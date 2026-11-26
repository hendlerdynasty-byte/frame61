package de.hendler.frame61.resolver

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gate 1 (1.6.0): wendet [ContentIdentity] auf eine Kandidatenliste an.
 *
 * Ein Kandidat bringt seine Identitätsangabe von der Quelle mit
 * ([MirrorCandidate.identityTitle]). Fehlt sie und ist die Watch-URL eine
 * HTML-Seite, wird der Seitentitel einmalig nachgeladen (`og:title`, dann
 * `<title>`) und im Prozess zwischengespeichert — kein zusätzlicher Abruf pro
 * Wiedergabe, kein Abruf für direkte Medien-URLs (`.mp4`, `.m3u8`, …).
 */
class ContentIdentityGate(
    private val fetcher: BodyFetcher = OkHttpBodyFetcher(),
    private val fetchBudgetMs: Long = 2_500
) {

    /** url → Seitentitel (nur Treffer werden behalten). */
    private val titleCache = ConcurrentHashMap<String, String>()

    /**
     * Bewertet jeden Kandidaten in der übergebenen Reihenfolge. Die Liste wird
     * nicht sortiert: die Auswahlreihenfolge bleibt Identität → Qualität →
     * Spielbarkeit (der Aufrufer filtert).
     */
    suspend fun verify(
        request: IdentityRequest,
        candidates: List<MirrorCandidate>,
        fetchPageTitles: Boolean = true
    ): List<VerifiedCandidate> = candidates.map { candidate ->
        val fromSource = candidate.identityTitle?.takeIf { it.isNotBlank() }
        val pageTitle = if (fromSource == null && candidate.sourceId != "aniworld" && fetchPageTitles) {
            candidate.streamUrl?.let { pageTitleFor(it, candidate.requestHeaders) }
        } else {
            null
        }
        val evidenceTitle = fromSource ?: pageTitle
        val sourceEvidenceError = if (candidate.sourceId == "aniworld") {
            aniworldEvidenceError(request, candidate)
        } else {
            null
        }
        val verdict = sourceEvidenceError ?: ContentIdentity.verdict(
            request = request,
            evidenceTitle = evidenceTitle,
            evidenceYear = candidate.identityYear,
            evidenceRuntimeSec = candidate.identityRuntimeSec,
            // `episode-<id>` enthält bei aniworld die interne ID, nicht die
            // Folgennummer. ContentIdentity prüft daran nur die Staffel; die
            // sichtbare Folge kommt aus sourceEpisode.
            evidenceUrl = if (candidate.sourceId == "aniworld") {
                candidate.sourceWatchUrl
            } else {
                candidate.streamUrl ?: candidate.url
            },
            sameSourceItem = candidate.identityKind == "source"
        )
        VerifiedCandidate(
            candidate = candidate,
            verdict = verdict,
            evidenceSource = when {
                fromSource != null -> "quelle:${candidate.identityKind ?: "?"}"
                pageTitle != null -> "seite"
                else -> "keine"
            }
        )
    }

    /**
     * aniworld darf nicht aus der bloß angeforderten Adresse abgeleitet werden.
     * Fehlt eine Zeilenangabe, ist die Identität UNKNOWN; weicht Staffel, Folge
     * oder Episodenlink ab, ist sie WRONG_CONTENT.
     */
    private fun aniworldEvidenceError(
        request: IdentityRequest,
        candidate: MirrorCandidate
    ): IdentityVerdict? {
        val season = candidate.sourceSeason
        val episode = candidate.sourceEpisode
        val watchUrl = candidate.sourceWatchUrl?.trim()
        val runtimeUnknown = request.runtimeMin == null || candidate.identityRuntimeSec == null
        if (season == null || episode == null || watchUrl.isNullOrBlank() ||
            season <= 0 || episode <= 0 || request.season <= 0 || request.episode <= 0
        ) {
            return IdentityVerdict(
                IdentityKind.UNKNOWN,
                0.0,
                matchedTitle = candidate.identityTitle,
                reason = "aniworld: Quellen-Evidenz season/episode/watchUrl fehlt",
                runtimeUnknown = runtimeUnknown,
                sameSourceItem = candidate.identityKind == "source"
            )
        }
        if (season != request.season || episode != request.episode) {
            return IdentityVerdict(
                IdentityKind.WRONG_CONTENT,
                0.0,
                matchedTitle = candidate.identityTitle,
                reason = "aniworld: Quellenfolge S${season}E$episode passt nicht zu S${request.season}E${request.episode}",
                runtimeUnknown = runtimeUnknown,
                sameSourceItem = candidate.identityKind == "source"
            )
        }
        val uri = runCatching { java.net.URI(watchUrl) }.getOrNull()
        val marker = Regex(
            """^/anime/stream/[^/]+/staffel-(\d{1,2})/episode-([^/]+)/?$""",
            RegexOption.IGNORE_CASE
        ).find(uri?.path.orEmpty())
        val urlSeason = marker?.groupValues?.getOrNull(1)?.toIntOrNull()
        val internalId = marker?.groupValues?.getOrNull(2)?.trim().orEmpty()
        val validHost = uri?.host?.let {
            it.equals("aniworld.to", ignoreCase = true) || it.endsWith(".aniworld.to", ignoreCase = true)
        } == true
        if (!validHost || !uri?.scheme.equals("https", ignoreCase = true) ||
            urlSeason != season || internalId.isBlank()
        ) {
            return IdentityVerdict(
                IdentityKind.WRONG_CONTENT,
                0.0,
                matchedTitle = candidate.identityTitle,
                reason = "aniworld: Watch-URL belegt die Quellenfolge nicht",
                runtimeUnknown = runtimeUnknown,
                sameSourceItem = candidate.identityKind == "source"
            )
        }
        return null
    }

    /** Lädt den Seitentitel einer HTML-URL (gecacht), `null` bei Fehlern. */
    suspend fun pageTitleFor(url: String, headers: Map<String, String>): String? {
        if (!looksLikeHtmlPage(url)) return null
        titleCache[url]?.let { return it }
        val title = withTimeoutOrNull(fetchBudgetMs) {
            withContext(Dispatchers.IO) {
                runCatching { fetcher.get(url, headers) }.getOrNull()
            }
        }?.let(::extractPageTitle)
        if (!title.isNullOrBlank()) titleCache[url] = title
        return title
    }

    companion object {
        private val MEDIA_EXT = Regex(
            "\\.(mp4|m4v|mkv|avi|ts|m3u8|mpd|webm|mov|flv|wmv)(\\?|$)",
            RegexOption.IGNORE_CASE
        )

        /** Nur echte HTML-Seiten werden abgerufen — keine Medien-URLs. */
        fun looksLikeHtmlPage(url: String): Boolean =
            url.startsWith("http") && !MEDIA_EXT.containsMatchIn(url)

        /**
         * Zieht den Titel aus dem HTML: `og:title` ist die Absicht der Seite,
         * `<title>` die Rückfallebene. Site-Anhänge ("… | Aniworld",
         * "… - Serienstream") werden abgeschnitten.
         */
        fun extractPageTitle(html: String): String? {
            val og = Regex(
                "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.get(1)
            val raw = og ?: Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)
            if (raw.isNullOrBlank()) return null
            return raw.trim()
                .split('|')
                .firstOrNull()
                ?.substringBefore(" – ")
                ?.substringBefore(" - ")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }
}

/** Kandidat mit Prüfergebnis. */
data class VerifiedCandidate(
    val candidate: MirrorCandidate,
    val verdict: IdentityVerdict,
    val evidenceSource: String
) {
    val playable: Boolean get() = verdict.playable
    val lastResort: Boolean get() = verdict.lastResort
}
