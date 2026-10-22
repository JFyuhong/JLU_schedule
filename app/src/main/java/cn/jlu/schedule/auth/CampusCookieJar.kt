package cn.jlu.schedule.auth

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

/**
 * 吉大域名的持久化 CookieJar：仅保留 *.jlu.edu.cn 下的会话 Cookie，
 * 落盘到 filesDir/auth/cookies.json（该目录已在备份规则中排除），
 * 冷启动时恢复，最大限度延续登录态；失效后由上层走静默重登。
 */
class CampusCookieJar(private val storeFile: File) : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    )

    private val lock = Any()
    private val cookies = LinkedHashMap<String, Cookie>()
    private var loaded = false
    private var retired = false

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            if (retired) return
            ensureLoaded()
            val now = System.currentTimeMillis()
            cookies.forEach { cookie ->
                if (!isAllowed(cookie)) return@forEach
                val key = cookieKey(cookie)
                if (cookie.expiresAt <= now) {
                    this.cookies.remove(key)
                } else {
                    // 如果是 iedu.jlu.edu.cn 下的核心会话 Cookie，清理同名旧 Cookie，防止不同 path 导致旧 Cookie 遮蔽新 Cookie
                    if (cookie.domain.contains("iedu.jlu.edu.cn") &&
                        (cookie.name.equals("JSESSIONID", true) ||
                         cookie.name.equals("GS_SESSIONID", true) ||
                         cookie.name.equals("_WEU", true) ||
                         cookie.name.equals("route", true))
                    ) {
                        val staleKeys = this.cookies.keys.filter {
                            it.startsWith(cookie.domain) && it.endsWith("|${cookie.name}")
                        }
                        staleKeys.forEach { this.cookies.remove(it) }
                    }
                    this.cookies[key] = cookie
                }
            }
            trimToLimit()
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            if (retired) return emptyList()
            ensureLoaded()
            val now = System.currentTimeMillis()
            val matched = mutableListOf<Cookie>()
            val expired = mutableListOf<String>()
            cookies.forEach { (key, cookie) ->
                if (cookie.expiresAt <= now) {
                    expired += key
                } else if (cookie.matches(url)) {
                    matched += cookie
                }
            }
            if (expired.isNotEmpty()) {
                expired.forEach { cookies.remove(it) }
                persist()
            }
            // 按照路径深度降序排列（越具体的路径排在越前面），优先保留匹配度最高的 Cookie
            // 例如 /jwapp/sys/cjcx 的 JSESSIONID 优先于根路径 / 的 JSESSIONID
            val sortedMatched = matched.sortedByDescending { it.path.length }
            val deduplicated = LinkedHashMap<String, Cookie>()
            for (cookie in sortedMatched) {
                if (!deduplicated.containsKey(cookie.name)) {
                    deduplicated[cookie.name] = cookie
                }
            }
            return deduplicated.values.toList()
        }
    }

    /** 把 WebView CookieManager 里的会话迁移进原生 CookieJar，返回迁移条数 */
    fun importFromCookieHeader(url: HttpUrl, header: String?): Int {
        if (header.isNullOrBlank()) return 0
        var imported = 0
        synchronized(lock) {
            if (retired) return 0
            ensureLoaded()
            val now = System.currentTimeMillis()
            header.split(";").forEach { part ->
                val cookie = Cookie.parse(url, part.trim()) ?: return@forEach
                if (isAllowed(cookie) && cookie.expiresAt > now) {
                    val isIeduSession = cookie.domain.contains("iedu.jlu.edu.cn") &&
                        (cookie.name.equals("JSESSIONID", true) ||
                         cookie.name.equals("GS_SESSIONID", true) ||
                         cookie.name.equals("_WEU", true) ||
                         cookie.name.equals("route", true))

                    if (isIeduSession) {
                        val staleKeys = this.cookies.keys.filter {
                            it.startsWith(cookie.domain) && it.endsWith("|${cookie.name}")
                        }
                        staleKeys.forEach { this.cookies.remove(it) }

                        val normalizedPath = if (cookie.name.equals("route", true)) "/" else "/jwapp"
                        val normalizedCookie = cloneWithNewPath(cookie, normalizedPath)
                        cookies[cookieKey(normalizedCookie)] = normalizedCookie
                    } else {
                        cookies[cookieKey(cookie)] = cookie
                    }
                    imported++
                }
            }
            if (imported > 0) {
                trimToLimit()
                persist()
            }
        }
        return imported
    }

    fun getCastgc(): String? {
        synchronized(lock) {
            ensureLoaded()
            val now = System.currentTimeMillis()
            return cookies.values.firstOrNull {
                it.name == "CASTGC" && it.value.isNotBlank() && it.expiresAt > now
            }?.value
        }
    }

    fun clearIeduSession() {
        synchronized(lock) {
            ensureLoaded()
            val toRemove = cookies.keys.filter { key ->
                val lower = key.lowercase()
                lower.contains("iedu.jlu.edu.cn")
            }
            toRemove.forEach { cookies.remove(it) }
            persist()
        }
    }

    fun getAllCookies(): List<Cookie> {
        synchronized(lock) {
            ensureLoaded()
            return cookies.values.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            persist()
        }
    }

    /** Logout retires this jar so responses already in flight cannot restore the old session. */
    fun retire() = synchronized(lock) {
        cookies.clear()
        loaded = true
        persist()
        retired = true
    }

    private fun isAllowed(cookie: Cookie): Boolean =
        cookie.domain.endsWith(ALLOWED_DOMAIN_SUFFIX, ignoreCase = true)

    private fun cookieKey(cookie: Cookie): String =
        "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun cloneWithNewPath(cookie: Cookie, newPath: String): Cookie {
        val builder = Cookie.Builder()
            .name(cookie.name)
            .value(cookie.value)
            .path(newPath)
            .expiresAt(cookie.expiresAt)
        if (cookie.hostOnly) builder.hostOnlyDomain(cookie.domain) else builder.domain(cookie.domain)
        if (cookie.secure) builder.secure()
        if (cookie.httpOnly) builder.httpOnly()
        return builder.build()
    }

    private fun trimToLimit() {
        while (cookies.size > MAX_COOKIES) {
            val eldest = cookies.keys.firstOrNull() ?: break
            cookies.remove(eldest)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!storeFile.exists()) return
        runCatching {
            val stored = json.decodeFromString<List<StoredCookie>>(storeFile.readText())
            stored.forEach { entry ->
                val builder = Cookie.Builder()
                    .name(entry.name)
                    .value(entry.value)
                    .path(entry.path)
                    .expiresAt(entry.expiresAt)
                if (entry.hostOnly) builder.hostOnlyDomain(entry.domain) else builder.domain(entry.domain)
                if (entry.secure) builder.secure()
                if (entry.httpOnly) builder.httpOnly()
                val cookie = builder.build()
                cookies[cookieKey(cookie)] = cookie
            }
        }.onFailure { Log.w(TAG, "恢复会话 Cookie 失败，忽略旧会话", it) }
    }

    private fun persist() {
        if (retired) return
        runCatching {
            storeFile.parentFile?.mkdirs()
            val tmp = File(storeFile.parentFile, storeFile.name + ".${System.nanoTime()}.tmp")
            val payload = cookies.values.map { cookie ->
                StoredCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly
                )
            }
            tmp.writeText(json.encodeToString(payload))
            if (!tmp.renameTo(storeFile)) {
                storeFile.delete()
                tmp.renameTo(storeFile)
            }
        }.onFailure { Log.w(TAG, "会话 Cookie 落盘失败", it) }
    }

    companion object {
        private const val TAG = "CampusCookieJar"
        const val ALLOWED_DOMAIN_SUFFIX = "jlu.edu.cn"
        const val MAX_COOKIES = 300
    }
}
