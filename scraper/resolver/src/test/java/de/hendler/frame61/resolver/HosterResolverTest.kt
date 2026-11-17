package de.hendler.frame61.resolver

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline: a scripted fetcher replaces the network. */
private class FakeFetcher(
    private val bodies: Map<String, String> = emptyMap()
) : BodyFetcher {
    val calls = mutableListOf<String>()
    override suspend fun get(url: String, headers: Map<String, String>): String {
        calls += "GET $url"
        return bodies[url] ?: ""
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        calls += "POST $url"
        return bodies[url] ?: ""
    }
}

private fun pack(
    id: String = "p",
    domains: List<String> = listOf("example.com"),
    captcha: String? = null,
    result: String? = "file",
    steps: List<HosterStep> = emptyList()
) = HosterPack(
    id = id, name = id.uppercase(), domains = domains,
    captcha = captcha, result = result, steps = steps
)

class HosterResolverTest {

    @Test
    fun `domain match is subdomain aware and longest suffix wins`() {
        val a = pack(id = "a", domains = listOf("example.com"))
        val b = pack(id = "b", domains = listOf("cdn.example.com"))
        val resolver = HosterResolver(listOf(a, b), FakeFetcher())

        assertEquals("a", resolver.findPack("https://www.example.com/x")?.id)
        assertEquals("b", resolver.findPack("https://cdn.example.com/x")?.id)
        assertEquals("b", resolver.findPack("https://a.b.cdn.example.com/x")?.id)
        // lookalike must not match
        assertNull(resolver.findPack("https://notexample.com/x"))
        assertNull(resolver.findPack("https://example.com.evil.io/x"))
    }

    @Test
    fun `direct copy passes the stream url through`() = runTest {
        val p = pack(
            id = "direct",
            domains = listOf("akamaihd.net"),
            result = "stream",
            steps = listOf(
                HosterStep(action = "copy", url = "{{url}}", saveAs = "stream")
            )
        )
        val fetcher = FakeFetcher()
        val out = HosterResolver(listOf(p), fetcher)
            .resolve("https://wdradaptiv-vh.akamaihd.net/i/x/master.m3u8")

        assertTrue(out is HosterOutcome.Resolved)
        assertEquals(
            "https://wdradaptiv-vh.akamaihd.net/i/x/master.m3u8",
            (out as HosterOutcome.Resolved).url
        )
        assertTrue("copy darf keinen Netzaufruf machen", fetcher.calls.isEmpty())
    }

    @Test
    fun `unknown host returns Unknown and never throws`() = runTest {
        val resolver = HosterResolver(emptyList(), FakeFetcher())
        val out = resolver.resolve("https://nowhere.invalid/e/1")
        assertTrue(out is HosterOutcome.Unknown)
    }

    @Test
    fun `captcha is a soft result, not an exception`() = runTest {
        val fetcher = FakeFetcher(mapOf("https://voe.sx/e/1" to "<source src='/x/index.m3u8'>"))
        val p = pack(
            id = "voe",
            domains = listOf("voe.sx"),
            captcha = "webview",
            result = "src",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = """src='([^']+)'""", saveAs = "src")
            )
        )
        val resolver = HosterResolver(listOf(p), fetcher)

        val blocked = resolver.resolve("https://voe.sx/e/1")
        assertTrue(blocked is HosterOutcome.CaptchaRequired)
        assertEquals("webview", (blocked as HosterOutcome.CaptchaRequired).kind)
        assertTrue("ohne Cookies darf kein Netzaufruf passieren", fetcher.calls.isEmpty())

        // with cookies the same pack resolves
        val ok = resolver.resolve("https://voe.sx/e/1", cookies = "a=b")
        assertEquals(HosterOutcome.Resolved("/x/index.m3u8", 2), ok)
    }

    @Test
    fun `a declared result that is never produced fails closed`() = runTest {
        val fetcher = FakeFetcher(mapOf("https://h.io/x" to "irgendein text"))
        val p = pack(
            id = "r",
            domains = listOf("h.io"),
            result = "file",
            steps = listOf(HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"))
        )
        val out = HosterResolver(listOf(p), fetcher).resolve("https://h.io/x")
        assertEquals(HosterOutcome.Failed("https://h.io/x", "result 'file' nicht erzeugt", 1), out)
    }

    @Test
    fun `steps run in order and final variable is returned`() = runTest {
        val fetcher = FakeFetcher(
            mapOf(
                "https://cdn.example.com/e/1" to """<video><source src="/x/index.m3u8"></video>"""
            )
        )
        val p = pack(
            id = "v",
            domains = listOf("cdn.example.com"),
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = """src="([^"]+)\"""", group = 1, saveAs = "src"),
                HosterStep(action = "sleep", ms = 250),
                HosterStep(action = "json", input = "src", path = "0", saveAs = "file")
            )
        )
        val out = HosterResolver(listOf(p), fetcher).resolve("https://cdn.example.com/e/1")
        // regex yields /x/index.m3u8; json path "0" on a non-JSON string fails
        assertTrue("expected Failed on json step, got $out", out is HosterOutcome.Failed)
        assertEquals(listOf("GET https://cdn.example.com/e/1"), fetcher.calls)
    }

    @Test
    fun `regex json and post steps produce the final url`() = runTest {
        val fetcher = FakeFetcher(
            mapOf(
                "https://h.io/e/1" to """{"sources":[{"file":"https://cdn/x.m3u8"}]}""",
                "https://h.io/api" to """{"ok":true}"""
            )
        )
        val p = pack(
            id = "j",
            domains = listOf("h.io"),
            result = "file",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "json", input = "page", path = "sources.0.file", saveAs = "file"),
                HosterStep(action = "post", url = "https://h.io/api", body = "{\"t\":\"{path}\"}", saveAs = "ignored")
            )
        )
        val out = HosterResolver(listOf(p), fetcher).resolve("https://h.io/e/1")
        assertEquals(HosterOutcome.Resolved("https://cdn/x.m3u8", 3), out)
        assertTrue(fetcher.calls.contains("POST https://h.io/api"))
    }

    @Test
    fun `failing regex is a value`() = runTest {
        val fetcher = FakeFetcher(mapOf("https://h.io/e/1" to "<html>kein Treffer</html>"))
        val p = pack(
            id = "r",
            domains = listOf("h.io"),
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = """src="([^"]+)\"""", saveAs = "file")
            )
        )
        val out = HosterResolver(listOf(p), fetcher).resolve("https://h.io/e/1")
        assertTrue(out is HosterOutcome.Failed)
        assertEquals("regex ohne Treffer", (out as HosterOutcome.Failed).reason)
    }
}