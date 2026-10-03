package cn.jlu.schedule.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** 统一认证登录结果 */
sealed class CasLoginResult {
    /** 登录成功，会话 Cookie 已写入 CookieJar（含 ticket 回跳建立的 iedu 会话） */
    data object Success : CasLoginResult()

    /** 账号或密码错误（msg 为服务端提示） */
    data class InvalidCredentials(val message: String) : CasLoginResult()

    /** 页面需要图形验证码/手机验证码，无法静默登录 */
    data object NeedsManualLogin : CasLoginResult()

    /** 网络不通或其他异常 */
    data class Error(val message: String) : CasLoginResult()
}

/**
 * 吉大 TPASS 统一认证客户端（原生 HTTP）。
 *
 * 登录流程：GET 登录页取 lt/execution → rsa = TpassDes(用户名+密码+lt) →
 * POST 表单（无明文账号字段，凭据全在 rsa 密文中）→ 跟随 302 ticket 回跳建立业务会话。
 */
class CasClient(private val client: OkHttpClient) {

    suspend fun login(
        username: String,
        password: String,
        serviceUrl: String = TpassConfig.IEDU_PORTAL_URL,
        onCredentialsAuthenticated: (() -> Unit)? = null
    ): CasLoginResult = withContext(Dispatchers.IO) {
        val pageUrl = "${TpassConfig.CAS_LOGIN_URL}?service=${urlencode(serviceUrl)}"
        try {
            val pageRequest = Request.Builder()
                .url(pageUrl)
                .header("User-Agent", TpassConfig.USER_AGENT)
                .build()
            client.newCall(pageRequest).execute().use { pageResponse ->
                val html = pageResponse.body?.string().orEmpty()
                val form = TpassFormParser.parse(html)
                    ?: return@withContext if (pageResponse.request.url.host == TpassConfig.CAS_HOST) {
                        CasLoginResult.NeedsManualLogin
                    } else {
                        // 未跳转到登录页说明已有会话，视为成功
                        CasLoginResult.Success
                    }

                val rsa = TpassDes.encrypt(username + password + form.lt, "1", "2", "3")
                val bodyBuilder = FormBody.Builder()
                    .add("rsa", rsa)
                    .add("ul", username.length.toString())
                    .add("pl", password.length.toString())
                    .add("sl", "0")
                    .add("lt", form.lt)
                    .add("_eventId", form.eventId ?: "submit")
                form.execution?.let { bodyBuilder.add("execution", it) }

                val loginRequest = Request.Builder()
                    .url(pageUrl)
                    .header("User-Agent", TpassConfig.USER_AGENT)
                    .header("Referer", pageUrl)
                    .post(bodyBuilder.build())
                    .build()
                client.newCall(loginRequest).execute().use { loginResponse ->
                    val finalUrl = loginResponse.request.url
                    val finalBody = runCatching { loginResponse.body?.string().orEmpty() }.getOrDefault("")
                    val stillOnLogin = finalUrl.host == TpassConfig.CAS_HOST &&
                        finalBody.contains("id=\"loginForm\"")
                    if (!stillOnLogin) {
                        if (loginResponse.isSuccessful && finalUrl.host == TpassConfig.IEDU_HOST) {
                            onCredentialsAuthenticated?.invoke()
                        }
                        android.util.Log.i("CasClient", "tpass login ok, final=${finalUrl.host}${finalUrl.encodedPath}")
                        return@withContext CasLoginResult.Success
                    }
                    val errorText = TpassFormParser.extractError(finalBody)
                    android.util.Log.w("CasClient", "tpass login rejected: $errorText, bodyLen=${finalBody.length}")
                    if (finalBody.contains("验证码")) {
                        CasLoginResult.NeedsManualLogin
                    } else {
                        CasLoginResult.InvalidCredentials(errorText ?: "账号或密码错误")
                    }
                }
            }
        } catch (error: Exception) {
            android.util.Log.w("CasClient", "tpass login error", error)
            CasLoginResult.Error(error.message ?: "网络异常")
        }
    }

    private fun urlencode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
