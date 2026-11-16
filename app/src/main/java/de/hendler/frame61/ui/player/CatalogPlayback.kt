package de.hendler.frame61.ui.player

import de.hendler.frame61.meta.CatalogItem
import de.hendler.frame61.resolver.MirrorCandidate
import java.security.MessageDigest

/**
 * Playback source for a catalog title.
 *
 * The catalog holds metadata, not stream rights. There is no resolver-backed
 * mirror for these titles yet, so playback uses a public HLS test stream. That
 * keeps the player (resume, watchdog, countdown) verifiable on a real device
 * without inventing a source for the title.
 */
object CatalogPlayback {

    /** Public, freely reachable HLS test stream (Mux demo asset). */
    const val DEMO_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    const val DEMO_SOURCE_ID = "demo"

    /**
     * Stable positive Long for a catalog id, so Room's `titleId` matches across
     * sessions. First 8 bytes of the SHA-256 of the id — not a hash of user
     * data, just a deterministic key. SHA-1 is deliberately not used here.
     */
    fun stableId(key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        return value and Long.MAX_VALUE
    }

    /**
     * Mirrors for a title. With no resolver-backed source yet, this returns the
     * single demo candidate.
     */
    fun candidates(item: CatalogItem): List<MirrorCandidate> = listOf(
        MirrorCandidate(
            sourceId = DEMO_SOURCE_ID,
            titleId = stableId(item.id),
            episodeId = null,
            url = DEMO_URL,
            lang = "de",
            quality = "1080p",
            hosterId = null,
            healthScore = 1.0,
            legalFree = true
        )
    )

    /** True when the only source is the demo stream — the UI must say so. */
    fun isDemoOnly(mirrors: List<MirrorCandidate>): Boolean =
        mirrors.isNotEmpty() && mirrors.all { it.sourceId == DEMO_SOURCE_ID }
}