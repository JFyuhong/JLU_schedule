package cn.jlu.schedule.remote

import android.content.Context
import android.webkit.CookieManager
import cn.jlu.schedule.auth.CampusCookieJar
import cn.jlu.schedule.auth.CasClient
import cn.jlu.schedule.auth.CasLoginResult
import cn.jlu.schedule.auth.JluCredentialStore
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AccountDataContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 教务平台原生 HTTP 客户端单例：持久化 CookieJar + 统一 UA。
 * 支持应用内账号密码登录；验证码等情况可由 WebView 登录后迁移 Cookie。
 */
object JwApiClient {

    enum class SessionStatus { VALID, EXPIRED, UNAVAILABLE, CERTIFICATE_ERROR }

    @Volatile
    private var cached: OkHttpClient? = null

    @Volatile
    private var cachedCookieJar: CampusCookieJar? = null

    fun get(context: Context): OkHttpClient {
        return cached ?: synchronized(this) {
            cached ?: OkHttpClient.Builder()
                .cookieJar(cookieJar(context))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", TpassConfig.USER_AGENT)
                            .build()
                    )
                }
                .build()
                .also { cached = it }
        }
    }

    fun cookieJar(context: Context): CampusCookieJar {
        return cachedCookieJar ?: synchronized(this) {
            cachedCookieJar ?: CampusCookieJar(File(context.applicationContext.filesDir, "auth/cookies.json"))
                .also { cachedCookieJar = it }
        }
    }

    /**
     * 把 WebView CookieManager 中目标域名的会话迁移进原生 CookieJar（首次登录后调用）。
     * @return 迁移的 Cookie 条数
     */
    fun importWebViewCookies(context: Context, targetUrl: String): Int {
        val manager = CookieManager.getInstance()
        val header = runCatching { manager.getCookie(targetUrl) }.getOrNull() ?: return 0
        val url: HttpUrl = targetUrl.toHttpUrl()
        return cookieJar(context).importFromCookieHeader(url, header)
    }

    // 各业务应用会话 Cookie 的 path 互不相同，需按 URL 逐个取齐（CookieManager 按 path 过滤）
    private val WEBVIEW_IMPORT_URLS = listOf(
        TpassConfig.IEDU_PORTAL_URL,
        "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do",
        "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/modules/xskcb/cxxszhxqkb.do",
        "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do",
        "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/modules/cjcx/xscjcx.do",
        TpassConfig.CAS_LOGIN_URL
    )

    /** 登录后/拉取数据前，把 WebView 各业务路径的会话全部迁入原生 CookieJar */
    fun importAllWebViewCookies(context: Context): Int {
        refreshAccountContext(context)
        val manager = CookieManager.getInstance()
        var imported = 0
        for (target in WEBVIEW_IMPORT_URLS) {
            val header = runCatching { manager.getCookie(target) }.getOrNull() ?: continue
            val url = runCatching { target.toHttpUrl() }.getOrNull() ?: continue
            imported += cookieJar(context).importFromCookieHeader(url, header)
        }
        return imported
    }

    /** Observe the actual CAS session, never the ID of credentials merely saved for autofill. */
    fun refreshAccountContext(context: Context) {
        val header = CookieManager.getInstance().getCookie(TpassConfig.CAS_LOGIN_URL).orEmpty()
        val token = header.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("CASTGC=") }?.substringAfter('=')
        AccountDataContext.get(context.filesDir).observeSession(token)
    }

    /** 用存储的加密凭据静默重登（会话失效时调用） */
    suspend fun silentLogin(context: Context): CasLoginResult {
        val credentials = JluCredentialStore.load(context)
        if (credentials == null) {
            android.util.Log.i("JwApiClient", "silentLogin skipped: no saved credentials")
            return CasLoginResult.NeedsManualLogin
        }
        val accounts = AccountDataContext.get(context.filesDir)
        val scope = accounts.capture()
        var authenticatedCredentials = false
        val result = CasClient(get(context)).login(credentials.studentId, credentials.password,
            onCredentialsAuthenticated = { authenticatedCredentials = true })
        currentCoroutineContext().ensureActive()
        if (!scope.isCurrent) return CasLoginResult.NeedsManualLogin
        if (result is CasLoginResult.Success) {
            syncJarToWebView(context)
            if (authenticatedCredentials) {
                accounts.authenticated(scope, credentials.studentId, cookieJar(context).getCastgc())
            }
        }
        return result
    }

    /** 校验业务会话；失效时只尝试一次已保存凭据的静默登录。 */
    suspend fun ensureSession(context: Context): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        when (checkIeduSession(context)) {
            SessionStatus.VALID -> return@withContext true
            SessionStatus.UNAVAILABLE, SessionStatus.CERTIFICATE_ERROR -> return@withContext false
            SessionStatus.EXPIRED -> Unit
        }
        if (silentLogin(context) !is CasLoginResult.Success) return@withContext false
        probeIeduSession(context)
    }

    /** 原生 CookieJar → WebView CookieManager（静默重登后 WebView 才能带上新会话） */
    fun syncJarToWebView(context: Context) {
        val manager = CookieManager.getInstance()
        val jar = cookieJar(context)
        listOf(
            TpassConfig.CAS_LOGIN_URL,
            TpassConfig.IEDU_PORTAL_URL,
            "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do",
            "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/modules/xskcb/cxxszhxqkb.do",
            "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do",
            "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/modules/cjcx/xscjcx.do",
            "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/modules/xskcb/cxxskcbbjfb.do"
        ).forEach { target ->
            val httpUrl = runCatching { target.toHttpUrl() }.getOrNull() ?: return@forEach
            runCatching {
                jar.loadForRequest(httpUrl).forEach { cookie ->
                    val domainClean = cookie.domain.removePrefix(".")
                    val base = "https://$domainClean"
                    manager.setCookie("$base${cookie.path}", cookie.toString())
                    if (domainClean == TpassConfig.IEDU_HOST) {
                        manager.setCookie("$base/", cookie.toString())
                        manager.setCookie("$base/jwapp/", cookie.toString())
                        manager.setCookie("$base/jwapp/sys/wdkb/", cookie.toString())
                        manager.setCookie("$base/jwapp/sys/wdkb/modules/xskcb/", cookie.toString())
                        manager.setCookie("$base/jwapp/sys/cjcx/", cookie.toString())
                        manager.setCookie("$base/jwapp/sys/cjcx/modules/cjcx/", cookie.toString())
                    }
                }
            }
        }
        manager.flush()
    }

    /** 清空会话（登出） */
    fun clearSession(context: Context, onComplete: (() -> Unit)? = null) {
        AccountDataContext.get(context.filesDir).clearSession()
        synchronized(this) {
            cached?.dispatcher?.cancelAll()
            cookieJar(context).retire()
            cached = null
            cachedCookieJar = null
        }
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            onComplete?.invoke()
        }
    }

    /** 本地是否还有可能使用的会话 Cookie；不能据此显示“已登录”。 */
    fun hasSession(context: Context): Boolean {
        val url = runCatching { TpassConfig.IEDU_PORTAL_URL.toHttpUrl() }.getOrNull() ?: return false
        return cookieJar(context).loadForRequest(url).isNotEmpty()
    }

    /** 教务操作可尝试已有会话，或用已保存账号在过期后重新登录。 */
    fun canRestoreSession(context: Context): Boolean = hasSession(context) || JluCredentialStore.hasSaved(context)

    /**
     * 原生会话能否真正访问教务应用：访问 wdkb 应用页并跟随重定向，
     * 最终仍落在 iedu 域（而非被踢到 CAS 登录页）才算有效。
     * 若 CAS 侧 TGT 仍有效，这条链会顺带把新业务会话 Cookie 写回 CookieJar。
     */
    fun probeIeduSession(context: Context): Boolean = checkIeduSession(context) == SessionStatus.VALID

    /** 实际请求教务应用。网络故障与认证过期分开呈现，避免误报。 */
    fun checkIeduSession(context: Context): SessionStatus {
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url("https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do")
                .build()
            get(context).newCall(request).execute().use { response ->
                val pageStart = if (response.isSuccessful &&
                    response.request.url.host == TpassConfig.IEDU_HOST
                ) response.peekBody(8192).string() else ""
                classifySessionResponse(response.request.url.host, response.code, pageStart)
            }
        }.getOrElse { error ->
            android.util.Log.w("JwApiClient", "session probe unavailable", error)
            val certificateRejected = generateSequence(error) { it.cause }.any {
                it is java.security.cert.CertificateException ||
                    it is java.security.cert.CertPathValidatorException
            }
            if (certificateRejected) SessionStatus.CERTIFICATE_ERROR else SessionStatus.UNAVAILABLE
        }
    }

    internal fun classifySessionResponse(host: String, code: Int, pageStart: String): SessionStatus {
        if (code >= 500) return SessionStatus.UNAVAILABLE
        if (host != TpassConfig.IEDU_HOST || code !in 200..299) return SessionStatus.EXPIRED
        val body = pageStart.lowercase()
        if (body.contains("id=\"loginform\"") || body.contains("id='loginform'") ||
            (body.contains("/tpass/login") && body.contains("password"))
        ) return SessionStatus.EXPIRED
        return SessionStatus.VALID
    }

    private fun getCompleteCookieHeader(context: Context): String {
        val cookieMap = LinkedHashMap<String, String>()

        // 1. 从原生持久化 CookieJar 读取已有 Cookie
        runCatching {
            cookieJar(context).getAllCookies().filter { it.domain.contains("jlu.edu.cn") }.forEach {
                if (it.name.isNotBlank() && it.value.isNotBlank()) {
                    cookieMap[it.name] = it.value
                }
            }
        }

        // 2. 从 WebView CookieManager 读取各层级 URL 的最新 Cookie（按名覆盖/补充）
        runCatching {
            val manager = android.webkit.CookieManager.getInstance()
            val probeUrls = listOf(
                "https://iedu.jlu.edu.cn/",
                "https://iedu.jlu.edu.cn/jwapp/",
                "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/",
                "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do",
                "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/modules/cjcx/xscjcx.do"
            )
            for (u in probeUrls) {
                val raw = manager.getCookie(u) ?: continue
                raw.split(";").forEach { part ->
                    val pair = part.trim()
                    val eq = pair.indexOf('=')
                    if (eq > 0) {
                        val k = pair.substring(0, eq).trim()
                        val v = pair.substring(eq + 1).trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            cookieMap[k] = v
                        }
                    }
                }
            }
        }

        return cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * 原生直接拉取全量学期成绩（不经过慢速 WebView 渲染，极速返回历年所有课程）
     */
    suspend fun fetchAllGrades(context: Context): List<cn.jlu.schedule.domain.ImportedGrade> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        importAllWebViewCookies(context)
        var grades = queryGradesInternal(context)
        if (grades.isEmpty()) {
            val relogin = silentLogin(context)
            if (relogin is CasLoginResult.Success) {
                syncJarToWebView(context)
                grades = queryGradesInternal(context)
            }
        }
        grades
    }

    private fun queryGradesInternal(context: Context): List<cn.jlu.schedule.domain.ImportedGrade> {
        val cookieHeader = getCompleteCookieHeader(context)
        android.util.Log.i("JwApiClient", "queryGradesInternal: cookieHeader size=${cookieHeader.length}")
        if (cookieHeader.isBlank()) return emptyList()

        // 使用无 CookieJar 拦截的 OkHttpClient，直接由 header 传递精确的 Cookie 集合，避免由于 path 匹配规则过滤掉重要子路径 Cookie
        val directClient = get(context).newBuilder()
            .cookieJar(okhttp3.CookieJar.NO_COOKIES)
            .followRedirects(false)
            .build()

        val defaultQuery = """[{"name":"SFYX","caption":"是否有效","linkOpt":"AND","builderList":"cbl_m_List","builder":"m_value_equal","value":"1","value_display":"是"},{"name":"SHOWMAXCJ","caption":"显示最高成绩","linkOpt":"AND","builderList":"cbl_m_List","builder":"m_value_equal","value":"0","value_display":"否"}]"""
        val formBody = okhttp3.FormBody.Builder()
            .add("querySetting", defaultQuery)
            .add("*order", "-XNXQDM,-KCH,-KXH")
            .add("pageSize", "1000")
            .add("pageNumber", "1")
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://iedu.jlu.edu.cn/jwapp/sys/cjcx/modules/cjcx/xscjcx.do")
            .header("Referer", "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("User-Agent", TpassConfig.USER_AGENT)
            .header("Cookie", cookieHeader)
            .post(formBody)
            .build()

        val res = runCatching {
            directClient.newCall(request).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                android.util.Log.i("JwApiClient", "queryGradesInternal code=$code, bodyLength=${body.length}")
                if (code in 200..299 && cn.jlu.schedule.parser.GradeTranscriptParser.isLikelyGradePayload(body)) {
                    val list = cn.jlu.schedule.parser.GradeTranscriptParser.parse(body)
                    android.util.Log.i("JwApiClient", "queryGradesInternal parsed ${list.size} courses")
                    list
                } else {
                    android.util.Log.w("JwApiClient", "queryGradesInternal not valid grade payload: ${body.take(200)}")
                    null
                }
            }
        }.onFailure {
            android.util.Log.e("JwApiClient", "queryGradesInternal execution error", it)
        }.getOrNull()

        if (!res.isNullOrEmpty()) {
            return res.distinctBy { "${it.semesterCode}_${it.courseCode}_${it.name}" }
                .sortedWith(compareByDescending<cn.jlu.schedule.domain.ImportedGrade> { it.semesterCode }.thenBy { it.courseCode })
        }

        // 兜底：若带条件查询返回空，尝试 querySetting = [] 无条件全量查询
        val fallbackBody = okhttp3.FormBody.Builder()
            .add("querySetting", "[]")
            .add("*order", "-XNXQDM,-KCH,-KXH")
            .add("pageSize", "1000")
            .add("pageNumber", "1")
            .build()

        val fallbackReq = request.newBuilder().post(fallbackBody).build()
        val fallbackRes = runCatching {
            directClient.newCall(fallbackReq).execute().use { response ->
                val code = response.code
                val body = response.body?.string().orEmpty()
                android.util.Log.i("JwApiClient", "queryGradesInternal fallback code=$code, bodyLength=${body.length}")
                if (code in 200..299 && cn.jlu.schedule.parser.GradeTranscriptParser.isLikelyGradePayload(body)) {
                    val list = cn.jlu.schedule.parser.GradeTranscriptParser.parse(body)
                    android.util.Log.i("JwApiClient", "queryGradesInternal fallback parsed ${list.size} courses")
                    list
                } else null
            }
        }.onFailure {
            android.util.Log.e("JwApiClient", "queryGradesInternal fallback error", it)
        }.getOrNull()

        return fallbackRes.orEmpty()
            .distinctBy { "${it.semesterCode}_${it.courseCode}_${it.name}" }
            .sortedWith(compareByDescending<cn.jlu.schedule.domain.ImportedGrade> { it.semesterCode }.thenBy { it.courseCode })
    }
}
