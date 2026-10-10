package cn.jlu.schedule.auth

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CampusCookieJarTest {

    @Test
    fun `late response cannot restore a retired account session`() {
        val file = File(tmp.root, "retired-cookies.json")
        val old = CampusCookieJar(file)
        old.importFromCookieHeader(casUrl, "CASTGC=alice")
        old.retire()
        val current = CampusCookieJar(file)
        current.importFromCookieHeader(casUrl, "CASTGC=bob")
        old.saveFromResponse(casUrl, listOf(Cookie.Builder()
            .name("CASTGC").value("late-alice").domain("cas.jlu.edu.cn").build()))
        old.importFromCookieHeader(casUrl, "CASTGC=another-late-alice")
        assertTrue(old.loadForRequest(casUrl).isEmpty())
        assertEquals("bob", CampusCookieJar(file).getCastgc())
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val ieduUrl = "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/index".toHttpUrl()
    private val casUrl = "https://cas.jlu.edu.cn/tpass/login".toHttpUrl()
    private val foreignUrl = "https://example.com/index".toHttpUrl()

    @Test
    fun `saves and reloads cookies across instances`() {
        val file = File(tmp.root, "auth/cookies.json")
        val jar = CampusCookieJar(file)
        jar.saveFromResponse(
            ieduUrl,
            listOf(
                Cookie.Builder()
                    .name("JSESSIONID")
                    .value("abc123")
                    .domain("iedu.jlu.edu.cn")
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 60_000)
                    .build()
            )
        )

        val reloaded = CampusCookieJar(file)
        val cookies = reloaded.loadForRequest(ieduUrl)
        assertEquals(1, cookies.size)
        assertEquals("JSESSIONID", cookies[0].name)
        assertEquals("abc123", cookies[0].value)
    }

    @Test
    fun `ignores non jlu domains`() {
        val jar = CampusCookieJar(File(tmp.root, "cookies.json"))
        jar.saveFromResponse(
            foreignUrl,
            listOf(
                Cookie.Builder()
                    .name("track")
                    .value("x")
                    .domain("example.com")
                    .path("/")
                    .build()
            )
        )
        assertTrue(jar.loadForRequest(foreignUrl).isEmpty())
    }

    @Test
    fun `expired cookies are dropped`() {
        val file = File(tmp.root, "cookies.json")
        val jar = CampusCookieJar(file)
        jar.saveFromResponse(
            casUrl,
            listOf(
                Cookie.Builder()
                    .name("TGC")
                    .value("old")
                    .domain("cas.jlu.edu.cn")
                    .path("/tpass")
                    .expiresAt(System.currentTimeMillis() - 1000)
                    .build()
            )
        )
        assertTrue(jar.loadForRequest(casUrl).isEmpty())
    }

    @Test
    fun `imports webview cookie header`() {
        val jar = CampusCookieJar(File(tmp.root, "cookies.json"))
        val imported = jar.importFromCookieHeader(
            ieduUrl,
            "JSESSIONID=web-1; PATH=/; SESSION=web-2"
        )
        assertTrue(imported >= 2)
        val names = jar.loadForRequest(ieduUrl).map { it.name }
        assertTrue(names.contains("JSESSIONID"))
        assertTrue(names.contains("SESSION"))
    }

    @Test
    fun `clear removes everything`() {
        val file = File(tmp.root, "cookies.json")
        val jar = CampusCookieJar(file)
        jar.importFromCookieHeader(casUrl, "TGC=ticket-1")
        jar.clear()
        assertTrue(jar.loadForRequest(casUrl).isEmpty())
        assertFalse(CampusCookieJar(file).loadForRequest(casUrl).isNotEmpty())
    }
}
