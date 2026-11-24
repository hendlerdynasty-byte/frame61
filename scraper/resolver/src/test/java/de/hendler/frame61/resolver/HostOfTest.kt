package de.hendler.frame61.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Host ist der Schlüssel der Alternative-Kette (Block 4e): die
 * aufgelöste Medien-URL sieht anders aus als die Hoster-Seite, die sie
 * erzeugt hat. Nur der Host der **Watch-URL** verrät, welcher Hoster
 * bereits gescheitert ist.
 */
class HostOfTest {

    @Test
    fun `holt den Host einer Watch-URL`() {
        assertEquals("moflix.rpmplay.xyz", hostOf("https://moflix.rpmplay.xyz/serie/abc/1/de"))
        assertEquals("veev.to", hostOf("https://veev.to/e/phGy6thMsf0tR"))
    }

    @Test
    fun `normalisiert auf kleinbuchstaben und ohne port`() {
        assertEquals("vidaraa.cc", hostOf("HTTPS://VIDARAA.CC/e/abc"))
        assertEquals("example.org", hostOf("https://example.org:8443/path"))
    }

    @Test
    fun `behandelt query und fragment als teil des pfads`() {
        // Die Query trägt bei manchen Hostern den Token — der Host bleibt
        // derselbe, sonst würde die Sperre den Hoster nicht greifen.
        assertEquals("cdn.example", hostOf("https://cdn.example/hls/master.m3u8?t=abc&k=1"))
    }

    @Test
    fun `liefert bei unparsbarer url einen leeren string`() {
        assertEquals("", hostOf("keine-url"))
        assertEquals("", hostOf(""))
        assertEquals("", hostOf("moflix.rpmplay.xyz/ohne-schema"))
    }

    @Test
    fun `erkennt verschiedene hoster derselben watch-seite`() {
        // Der Ausfall betraf moflix.rpmplay.xyz; die Alternativen tragen
        // jeweils eigene Hosts und dürfen deshalb nicht gesperrt werden.
        val hosts = listOf(
            "https://moflix.rpmplay.xyz/x",
            "https://veev.to/x",
            "https://vidaraa.cc/x"
        ).map { hostOf(it) }
        assertEquals(3, hosts.distinct().size)
        assertTrue(hosts.contains("moflix.rpmplay.xyz"))
    }
}
