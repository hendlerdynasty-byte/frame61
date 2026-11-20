package de.hendler.frame61.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackDiagnosticsTest {

    @Before
    fun setUp() {
        PlaybackDiagnostics.sink = null
        PlaybackDiagnostics.beginSession("test", 0)
    }

    @After
    fun tearDown() {
        PlaybackDiagnostics.clear()
        PlaybackDiagnostics.sink = null
    }

    @Test
    fun `container sniffer distinguishes required containers and error pages`() {
        assertEquals("HLS", ContainerSniffer.detect("#EXTM3U\n#EXT-X-VERSION:3".toByteArray()))
        assertEquals("MP4", ContainerSniffer.detect(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)))
        assertEquals("MKV", ContainerSniffer.detect(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())))
        assertEquals("AVI", ContainerSniffer.detect("RIFF1234AVI LIST".toByteArray()))
        assertEquals("MPEG-PS", ContainerSniffer.detect(byteArrayOf(0, 0, 1, 0xBA.toByte())))
        val ts = ByteArray(377)
        ts[0] = 0x47
        ts[188] = 0x47
        assertEquals("MPEG-TS", ContainerSniffer.detect(ts))
        assertEquals("HTML", ContainerSniffer.detect("\n<!doctype html><html>".toByteArray()))
    }

    @Test
    fun `urls and headers stay comparable without exposing credentials`() {
        val url = PlaybackDiagnostics.safeUrl("https://media.invalid/video.m3u8?token=secret&quality=1080&sig=abc")
        assertFalse(url.contains("secret"))
        assertFalse(url.contains("abc"))
        assertTrue(url.contains("quality=1080"))
        assertTrue(url.contains("token=sha256:"))

        val headers = PlaybackDiagnostics.safeHeaders(
            mapOf("Cookie" to "session=secret; theme=dark", "User-Agent" to "Frame61")
        )
        assertFalse(headers.getValue("Cookie").contains("secret"))
        assertTrue(headers.getValue("Cookie").contains("session"))
        assertTrue(headers.getValue("Cookie").contains("theme"))
        assertEquals("Frame61", headers.getValue("User-Agent"))
    }

    @Test
    fun `interceptor records every hop and leaves the response untouched`() {
        val server = MockWebServer()
        server.start()
        try {
            val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n"
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/video.m3u8")))
            server.enqueue(MockResponse().setResponseCode(200).setBody(playlist).addHeader("Content-Type", "application/vnd.apple.mpegurl"))
            PlaybackDiagnostics.clear()
            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(PlaybackDiagnosticsInterceptor())
                .build()
            val request = Request.Builder().url(server.url("/start")).header("Cookie", "session=secret").build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals(playlist, response.body?.string())
            }

            val responses = PlaybackDiagnostics.events.value.filter { it.category == "HTTP" && it.status != null }
            assertEquals(listOf(302, 200), responses.map { it.status })
            assertEquals("HLS", responses.last().details["container"])
            assertFalse(responses.last().asLogLine().contains("secret"))
            assertTrue(responses.last().asLogLine().contains("firstBytes="))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
