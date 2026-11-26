package de.hendler.frame61.playback

import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.hostOf
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import java.net.URI

/** Reine, testbare Auswahlregeln für Stream-/Hoster-Alternativen. */
internal object StreamSelectionPolicy {

    /** Filemoon > Vidmoly > Doodstream > andere > VOE > DeVideoSrc/unbekannt. */
    fun rank(hosterId: String?): Int = when (hosterId?.trim()?.lowercase()) {
        "filemoon" -> 0
        "vidmoly" -> 1
        "doodstream", "dood" -> 2
        null, "" -> 5
        "voe", "voe.sx" -> 4
        "devideosrc", "de-video-src" -> 5
        else -> 3
    }

    /** Zweistufig: HTTP-Tier zuerst, innerhalb des Tiers nach Hosterpriorität. */
    fun order(streams: List<StreamResult>, needsWebView: (String) -> Boolean): List<StreamResult> =
        streams.sortedWith(
            compareBy<StreamResult> { if (needsWebView(it.url)) 1 else 0 }
                .thenBy { rank(it.hosterId) }
        )

    /**
     * Ein Redirect-Host einer Indexseite ist kein Hoster. Dort sperrt nur die
     * exakte Watch-URL; eine Host-Sperre darf nicht alle Redirects zugleich
     * entfernen. Fremdhosts bleiben über ihren Host sperrbar.
     */
    fun isExcluded(
        stream: StreamResult,
        pack: SourcePack,
        excludeUrls: Set<String>,
        excludeHosts: Set<String>
    ): Boolean {
        if (stream.url in excludeUrls) return true
        val host = hostOf(stream.url)
        val sourceHost = runCatching { URI(pack.baseUrl).host?.lowercase() }.getOrNull()
        return host.isNotBlank() && host in excludeHosts && host != sourceHost
    }

    /** Unknown ist eine unaufgelöste HTML-Seite, kein Spielkandidat. */
    fun playable(outcome: HosterOutcome?): Boolean = outcome is HosterOutcome.Resolved
}
