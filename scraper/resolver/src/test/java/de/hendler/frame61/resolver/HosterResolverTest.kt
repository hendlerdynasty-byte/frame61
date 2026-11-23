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
    private val bodies: Map<String, String> = emptyMap(),
    private val redirects: Map<String, String> = emptyMap()
) : BodyFetcher {
    val calls = mutableListOf<String>()
    val headersByUrl = mutableMapOf<String, Map<String, String>>()
    override suspend fun get(url: String, headers: Map<String, String>): String {
        calls += "GET $url"
        headersByUrl[url] = headers
        return bodies[url] ?: ""
    }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String {
        calls += "POST $url"
        headersByUrl[url] = headers
        return bodies[url] ?: ""
    }

    override suspend fun resolveRedirect(url: String, headers: Map<String, String>): String? {
        calls += "HEAD $url"
        return redirects[url]
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
        assertTrue(ok is HosterOutcome.Resolved)
        assertEquals("/x/index.m3u8", (ok as HosterOutcome.Resolved).url)
        assertEquals("a=b", ok.cookies)
        assertEquals("a=b", ok.requestHeaders["Cookie"])
    }

    @Test
    fun `webview budget allows one attempt then refuses`() = runTest {
        val budget = WebViewBudget.of(1, System.currentTimeMillis() + 30_000L)
        assertTrue("der erste Versuch ist frei", budget.tryTake())
        assertFalse("der zweite Versuch muss abgelehnt werden", budget.tryTake())
    }

    @Test
    fun `webview budget refuses after its deadline`() = runTest {
        val budget = WebViewBudget.of(1, System.currentTimeMillis() - 1L)
        assertFalse("abgelaufene Frist sperrt den Versuch", budget.tryTake())
    }

    @Test
    fun `webview budget none never allows an attempt`() = runTest {
        val budget = WebViewBudget.none()
        assertFalse(budget.tryTake())
    }

    @Test
    fun `exhausted webview budget skips the interceptor and stays a soft result`() = runTest {
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
        val resolver = HosterResolver(
            packs = listOf(p),
            fetcher = fetcher,
            mediaInterceptor = object : MediaInterceptor {
                override suspend fun intercept(
                    url: String,
                    timeoutMs: Long,
                    script: String?
                ): InterceptedMedia? = throw AssertionError(
                    "das Budget war erschöpft, die WebView darf nicht starten"
                )
            }
        )
        val out = resolver.resolve("https://voe.sx/e/1", budget = WebViewBudget.none())
        assertTrue("ohne Budget-Los bleibt das Ergebnis weich", out is HosterOutcome.CaptchaRequired)
        assertTrue("kein Netzaufruf ohne WebView", fetcher.calls.isEmpty())
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
        assertEquals("https://cdn/x.m3u8", (out as HosterOutcome.Resolved).url)
        assertTrue(fetcher.calls.contains("POST https://h.io/api"))
        assertEquals("https://h.io/e/1", fetcher.headersByUrl["https://h.io/api"]?.get("Referer"))
        assertEquals(HosterResolver.DEFAULT_USER_AGENT, fetcher.headersByUrl["https://h.io/api"]?.get("User-Agent"))
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

    @Test
    fun `js step stores the WebView result`() = runTest {        val p = pack(
            id = "js",
            domains = listOf("host.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "js", script = "return window.token", saveAs = "token"),
                HosterStep(action = "copy", url = "https://cdn/x/{token}.m3u8", saveAs = "file")
            )
        )
        val evaluator = object : JsEvaluator {
            override suspend fun evaluate(url: String, script: String, timeoutMs: Long): String = "abc123"
        }
        val out = HosterResolver(listOf(p), FakeFetcher(), evaluator)
            .resolve("https://host.example/e/1")
        assertEquals("https://cdn/x/abc123.m3u8", (out as HosterOutcome.Resolved).url)
    }

    @Test
    fun `pow step solves a sha256 prefix`() = runTest {
        val p = pack(
            id = "pow",
            domains = listOf("host.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "copy", url = "frame61", saveAs = "challenge"),
                HosterStep(action = "pow", algorithm = "sha256", input = "challenge", targetPrefix = "0", saveAs = "solution"),
                HosterStep(action = "copy", url = "https://cdn/x/{solution}.m3u8", saveAs = "file")
            )
        )
        val out = HosterResolver(listOf(p), FakeFetcher()).resolve("https://host.example/e/1")
        assertTrue(out is HosterOutcome.Resolved)
        assertTrue((out as HosterOutcome.Resolved).url.startsWith("https://cdn/x/"))
    }

    @Test
    fun `cookie jar is forwarded as Cookie header`() = runTest {
        val fetcher = object : BodyFetcher {
            var cookie: String? = null
            override suspend fun get(url: String, headers: Map<String, String>): String {
                cookie = headers["Cookie"]
                return "<html></html>"
            }
            override suspend fun post(url: String, body: String, headers: Map<String, String>): String =
                get(url, headers)
        }
        val p = pack(
            id = "cookie",
            domains = listOf("host.example"),
            result = "file",
            steps = listOf(HosterStep(action = "get", url = "https://{host}{path}", saveAs = "file"))
        )
        val out = HosterResolver(listOf(p), fetcher).resolve("https://host.example/e/1", "cf_clearance=1")
        assertTrue(out is HosterOutcome.Resolved)
        assertEquals("cf_clearance=1", fetcher.cookie)
    }

    @Test
    fun `a redirect chain is followed to the real hoster`() = runTest {
        val fetcher = FakeFetcher(
            bodies = mapOf("https://vidmoly.example/embed-1.html" to "sources: [{ file: 'https://cdn.example/x.m3u8' }]"),
            redirects = mapOf("https://index.example/redirect/1" to "https://vidmoly.example/embed-1.html")
        )
        val vidmoly = pack(
            id = "vidmoly",
            domains = listOf("vidmoly.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = "file: '([^']+)'", group = 1, saveAs = "file")
            )
        )
        val out = HosterResolver(listOf(vidmoly), fetcher)
            .resolve("https://index.example/redirect/1")

        assertTrue(out is HosterOutcome.Resolved)
        assertEquals("https://cdn.example/x.m3u8", (out as HosterOutcome.Resolved).url)
    }

    @Test
    fun `a direct media url never costs a redirect lookup`() = runTest {
        val fetcher = FakeFetcher(
            bodies = mapOf("https://cdn.example/x.mp4" to "ignored"),
            redirects = mapOf("https://cdn.example/x.mp4" to "https://elsewhere.example/")
        )
        val out = HosterResolver(emptyList(), fetcher).resolve("https://cdn.example/x.mp4")

        assertTrue(out is HosterOutcome.Unknown)
        assertFalse(fetcher.calls.contains("HEAD https://cdn.example/x.mp4"))
    }

    @Test
    fun `a hoster pack still matches after a redirect`() = runTest {
        val fetcher = FakeFetcher(
            bodies = mapOf("https://voe.example/e/1" to "window.sources=[{file:'https://cdn.example/v.m3u8'}]"),
            redirects = mapOf("https://index.example/redirect/2" to "https://voe.example/e/1")
        )
        val voe = pack(
            id = "voe",
            domains = listOf("voe.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = "file:'([^']+)'", group = 1, saveAs = "file")
            )
        )
        val out = HosterResolver(listOf(voe), fetcher).resolve("https://index.example/redirect/2")

        assertTrue(out is HosterOutcome.Resolved)
        assertNotNull((out as HosterOutcome.Resolved).url)
    }

    @Test
    fun `a chain across two hoster packs resolves to the final url`() = runTest {
        val fetcher = FakeFetcher(
            bodies = mapOf(
                "https://gate.example/r?t=1" to "<html>gate</html>",
                "https://voe.example/e/1" to "window.sources=[{file:'https://cdn.example/v.m3u8'}]"
            )
        )
        val gate = pack(
            id = "gate",
            domains = listOf("gate.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "copy", url = "https://voe.example/e/1", saveAs = "file")
            )
        )
        val voe = pack(
            id = "voe",
            domains = listOf("voe.example"),
            result = "file",
            steps = listOf(
                HosterStep(action = "get", url = "https://{host}{path}", saveAs = "page"),
                HosterStep(action = "regex", input = "page", pattern = "file:'([^']+)'", group = 1, saveAs = "file")
            )
        )

        val out = HosterResolver(listOf(gate, voe), fetcher).resolve("https://gate.example/r?t=1")

        assertTrue(out is HosterOutcome.Resolved)
        assertEquals("https://cdn.example/v.m3u8", (out as HosterOutcome.Resolved).url)
    }
}