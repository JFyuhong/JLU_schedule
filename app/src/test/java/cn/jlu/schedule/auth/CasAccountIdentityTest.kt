package cn.jlu.schedule.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class CasAccountIdentityTest {
    @Test fun reusedSessionDoesNotAuthenticateTheSavedUsername() = runBlocking {
        var authenticated = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request().newBuilder().url(TpassConfig.IEDU_PORTAL_URL).build())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("<html>Already signed in as someone else</html>".toResponseBody()).build()
        }.build()
        val result = CasClient(client).login("saved-account", "password",
            onCredentialsAuthenticated = { authenticated = true })
        assertEquals(CasLoginResult.Success, result)
        assertFalse(authenticated)
    }

    @Test fun acceptedCredentialSubmissionCanBindTheAccount() = runBlocking {
        var authenticated = false
        val methods = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            methods.add(request.method)
            val form = """<form id="loginForm"><input id="lt" name="lt" value="test-ticket" /></form>"""
            Response.Builder()
                .request(if (request.method == "POST") request.newBuilder().url(TpassConfig.IEDU_PORTAL_URL).build() else request)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((if (request.method == "GET") form else "<html>Portal</html>").toResponseBody()).build()
        }.build()
        assertEquals(CasLoginResult.Success, CasClient(client).login("alice", "password",
            onCredentialsAuthenticated = { authenticated = true }))
        assertEquals(listOf("GET", "POST"), methods)
        assertTrue(authenticated)
    }
}
