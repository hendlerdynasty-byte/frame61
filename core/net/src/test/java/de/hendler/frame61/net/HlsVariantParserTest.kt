package de.hendler.frame61.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsVariantParserTest {
    @Test
    fun `variants are ranked by playable resolution and bandwidth without urls`() {
        val body = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2",VIDEO-RANGE=SDR
            720p.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=6500000,AVERAGE-BANDWIDTH=6000000,RESOLUTION=1920x1080,CODECS="hvc1.1.6.L120.90",VIDEO-RANGE=HLG
            1080p-hdr.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",VIDEO-RANGE=SDR
            1080p.m3u8
        """.trimIndent()

        val variants = HlsVariantParser.parse(body)
        assertEquals(3, variants.size)
        assertEquals(6_500_000L, variants.first().bandwidth)
        assertEquals(1920, variants.first().width)
        assertEquals(1080, variants.first().height)
        assertTrue(variants.first().hdr)
        assertEquals("hvc1.1.6.L120.90", variants.first().codecs)

        val summary = HlsVariantParser.summarize(body)
        assertEquals("3", summary["hlsVariants"])
        assertEquals("1080", summary["hlsMaxHeight"])
        assertEquals("true", summary["hlsHdr"])
        assertTrue(summary.values.none { it.contains("1080p-hdr.m3u8") })
    }
}
