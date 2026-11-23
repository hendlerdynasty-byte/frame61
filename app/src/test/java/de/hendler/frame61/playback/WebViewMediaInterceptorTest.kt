package de.hendler.frame61.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Medien-Treffer im verdeckten WebView. Reine Funktion, ohne Android —
 * die Unit-Tests des App-Moduls laufen ohne Geraet.
 */
class WebViewMediaInterceptorTest {

    @Test
    fun `normal media targets are recognized`() {
        assertTrue(
            MediaUrlMatcher.isMediaUrl(
                "https://r1.dropcdn.io/hls2/03/00000/abc_n/master.m3u8?t=x&s=1"
            )
        )
        assertTrue(
            MediaUrlMatcher.isMediaUrl("https://cdn.example/video/12345/file.mp4")
        )
        assertTrue(MediaUrlMatcher.isMediaUrl("https://cdn.example/x/master.m3u8"))
    }

    @Test
    fun `doodstream media url without file extension is recognized`() {
        // Gemessen 23.11.2026 an playmogo.com/e/…: /pass_md5 liefert die
        // Basis ohne Endung, makePlay() haengt Token und Ablauf an.
        assertTrue(
            MediaUrlMatcher.isMediaUrl(
                "https://uha211.cloudatacdn.com/u5kjwjuviha3sdgge5qkwmyxd4mz2gn6ak6czr7s75spo7ksmdte6fu2eciq/myshk2p1j7~a1b2c3d4e5?token=nc1f14p40nmaewy7y4z8jkem&expiry=1795471671000"
            )
        )
        assertTrue(
            MediaUrlMatcher.isMediaUrl(
                "https://uha211.cloudatacdn.com/abc/def~x1y2z3?expiry=1795471671000"
            )
        )
    }

    @Test
    fun `pages and scripts are not media`() {
        assertFalse(MediaUrlMatcher.isMediaUrl("https://doodstream.com/e/pa8mnbfskecj"))
        assertFalse(MediaUrlMatcher.isMediaUrl("https://cdn.example/assets/app.js"))
        assertFalse(MediaUrlMatcher.isMediaUrl("https://cdn.example/img/poster.jpg"))
        assertFalse(MediaUrlMatcher.isMediaUrl("https://imgds.net/img/poster.jpg"))
        assertFalse(MediaUrlMatcher.isMediaUrl("ftp://cdn.example/v.m3u8"))
    }
}