package de.hendler.frame61.net

import java.io.IOException
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSource
import okio.GzipSink
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        PlaybackDiagnostics.endSession(PlaybackDiagnostics.currentSessionId)
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
    fun `resolver messages and sink are redacted too`() {
        val lines = mutableListOf<String>()
        PlaybackDiagnostics.sink = { lines += it.asLogLine() }
        PlaybackDiagnostics.record(
            "RESOLVER",
            "HTTP 403 für https://media.invalid/v?token=urlSecret session=assignmentSecret Bearer bearerSecret"
        )
        val output = lines.joinToString("\n") + PlaybackDiagnostics.snapshotText()
        assertFalse(output.contains("urlSecret"))
        assertFalse(output.contains("assignmentSecret"))
        assertFalse(output.contains("bearerSecret"))
        assertTrue(output.contains("sha256:"))
    }

    @Test
    fun `text response secrets are neither preview nor hex encoded`() {
        val body = "{\"token\":\"jsonSecret\"}".toByteArray()
        PlaybackDiagnostics.record("HTTP", "JSON", firstBytes = body)
        val event = PlaybackDiagnostics.events.value.single { it.category == "HTTP" }
        assertNull(event.bytesHex)
        assertNull(event.bytesPreview)
        val hex = body.joinToString("") { "%02X".format(it) }
        assertFalse(PlaybackDiagnostics.snapshotText().contains("jsonSecret"))
        assertFalse(PlaybackDiagnostics.snapshotText().contains(hex))

        PlaybackDiagnostics.record(
            "HTTP",
            "MP4",
            firstBytes = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
        )
        assertNotNull(PlaybackDiagnostics.events.value.last().bytesHex)
    }

    @Test
    fun `late and post release events never join another session`() {
        val first = PlaybackDiagnostics.currentSessionId
        val second = PlaybackDiagnostics.beginSession("second", 0)
        val before = PlaybackDiagnostics.events.value.size
        PlaybackDiagnostics.record("PLAYER", "late", sessionId = first)
        assertEquals(before, PlaybackDiagnostics.events.value.size)

        val secondSink: (PlaybackTraceEvent) -> Unit = {}
        PlaybackDiagnostics.sink = secondSink
        PlaybackDiagnostics.endSession(first)
        assertTrue(PlaybackDiagnostics.sink === secondSink)

        PlaybackDiagnostics.endSession(second)
        assertFalse(PlaybackDiagnostics.isActive(second))
        assertNull(PlaybackDiagnostics.sink)
        val ended = PlaybackDiagnostics.events.value.size
        PlaybackDiagnostics.record("HTTP", "post-release", sessionId = second)
        assertEquals(ended, PlaybackDiagnostics.events.value.size)
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
            assertTrue(responses.last().asLogLine().contains("#EXTM3U"))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `gzip hls is sniffed after decompression and still plays unchanged`() {
        val server = MockWebServer()
        server.start()
        try {
            val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n"
            val compressed = Buffer()
            GzipSink(compressed).buffer().use { it.writeUtf8(playlist) }
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .addHeader("Content-Encoding", "gzip")
                    .setBody(compressed)
            )
            PlaybackDiagnostics.clear()
            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(PlaybackDiagnosticsInterceptor())
                .build()
            client.newCall(Request.Builder().url(server.url("/video.m3u8")).build()).execute().use { response ->
                assertEquals(playlist, response.body?.string())
            }
            val event = PlaybackDiagnostics.events.value.single { it.status == 200 }
            assertEquals("HLS", event.details["container"])
            assertEquals("gzip", event.details["contentEncoding"])
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `sniff read failure is an exception not an empty body`() {
        val failingSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("synthetic")
            override fun timeout(): Timeout = Timeout.NONE
            override fun close() = Unit
        }
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 1L
            override fun source(): BufferedSource = failingSource.buffer()
        }
        val response = Response.Builder()
            .request(Request.Builder().url("https://media.invalid/video").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()

        assertThrows(IOException::class.java) { sniffForDiagnostics(response) }
    }
}
