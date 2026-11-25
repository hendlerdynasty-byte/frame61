package de.hendler.frame61.resolver

/**
 * Inhalts-Identität eines Wiedergabe-Kandidaten (Gate 1, 1.6.0).
 *
 * Ein Kandidat darf nur abgespielt werden, wenn die Quelle selbst belegt, dass
 * sie den **angefragten** Titel führt. Diese Datei ist die reine Bewertung:
 * Normalisierung, Ähnlichkeit, Trailer-Erkennung, Laufzeit- und
 * Episodenprüfung. Sie hat keine Netz- oder Android-Abhängigkeit und ist damit
 * vollständig testbar.
 *
 * Reihenfolge der Prüfungen (Auftrag 1.6.0): Trailer → Titel → Episode →
 * Laufzeit. Ein Trailer-Titel ist nie „richtig", auch wenn der Titel passt.
 */
enum class IdentityKind {
    /** Titel (und ggf. Laufzeit/Episode) passen. */
    CORRECT,

    /** Seitentitel enthält Trailer-/Vorschau-Marker. Nur letzter Ausweg. */
    TRAILER_ONLY,

    /** Quelle liefert einen anderen Inhalt (Titel/Episode/Laufzeit passen nicht). */
    WRONG_CONTENT,

    /** Keine verwertbare Identitätsangabe — darf nicht abgespielt werden. */
    UNKNOWN
}

/**
 * Was der Nutzer angefordert hat. `runtimeMin` kommt aus dem Katalog und ist
 * derzeit fast immer `null` (997 Titel, 0 Laufzeiten) — dann entfällt die
 * Laufzeitprüfung und wird als `runtimeUnknown` gezählt (Auftrag: „Fehlt die
 * Laufzeit, entfällt die Prüfung").
 */
data class IdentityRequest(
    val title: String,
    val year: Int? = null,
    val type: String = "movie",
    val season: Int = 1,
    val episode: Int = 0,
    val runtimeMin: Int? = null
) {
    val isSeries: Boolean get() = type == "series"
}

/**
 * Ergebnis der Prüfung. `score` ist die Titelsimilarität nach Normalisierung
 * (0..1), `matchedTitle` die Quelle, die tatsächlich geprüft wurde.
 */
data class IdentityVerdict(
    val kind: IdentityKind,
    val score: Double,
    val matchedTitle: String? = null,
    val reason: String = "",
    val runtimeUnknown: Boolean = false,
    /** Der Kandidat kommt aus derselben Quellen-Id, aus der der Titel stammt. */
    val sameSourceItem: Boolean = false
) {
    val playable: Boolean get() = kind == IdentityKind.CORRECT
    /** Nur wenn nichts anderes übrig bleibt — mit sichtbarem Hinweis „Vorschau". */
    val lastResort: Boolean get() = kind == IdentityKind.TRAILER_ONLY

    fun asLogLine(): String = buildString {
        append("ident=").append(kind.name)
        append(" score=").append(String.format(java.util.Locale.ROOT, "%.2f", score))
        if (sameSourceItem) append(" sameItem=ja")
        if (runtimeUnknown) append(" runtime_unknown")
        if (reason.isNotBlank()) append(" grund=").append(reason)
    }
}

object ContentIdentity {

    /** Ab dieser Ähnlichkeit gilt der Titel als derselbe (Auftrag: ≥ 0,75). */
    const val TITLE_THRESHOLD = 0.75

    /** Laufzeit-Toleranz: Filme 10 %, Serien 15 % (Auftrag). */
    private const val RUNTIME_TOLERANCE_MOVIE = 0.10
    private const val RUNTIME_TOLERANCE_SERIES = 0.15

    /**
     * Trailer-Marker. Bewusst klein und deutsch/englisch: ein Titelanhang wie
     * „(1960)" darf nicht als Trailer gelten, „Trailer", "Teaser" schon.
     */
    private val TRAILER_MARKERS = listOf(
        "trailer", "vorschau", "teaser", "preview", "ausschnitt",
        "clip", "sample", "making of", "featurette"
    )

    private val LEADING_ARTICLES = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer",
        "the", "a", "an", "le", "la", "les", "el", "los", "las"
    )

    /** Füllwörter, die den Inhalt nicht unterscheiden. */
    private val STOP_WORDS = setOf(
        "und", "and", "mit", "with", "von", "of", "im", "in", "am", "on",
        "zu", "to", "für", "for", "aus", "from"
    )

    /** Rauschtokens, die im Quellentitel stehen dürfen, ohne den Inhalt zu ändern. */
    private val NOISE_TOKENS = setOf(
        "ganzer", "ganze", "komplett", "komplette", "full", "movie", "film",
        "stream", "online", "kostenlos", "kostenlose", "hd", "fhd", "uhd",
        "1080p", "720p", "480p", "4k", "german", "deutsch", "ger", "dublado"
    )

    /**
     * Normalisiert einen Titel für den Vergleich: Kleinschreibung, Umlaute
     * ausgeschrieben, Diakritika entfernt, Staffel-/Folgenanhänge und
     * Jahreszahlen weg, Satzzeichen zu Trenner, Artikel/Füllwörter/Rauschtokens
     * entfernt.
     *
     * Beispiele: „Die Schnetts und die Schmoos (2023)" → `schnetts schmoos`;
     * „Andor – Staffel 1" → `andor`; „Man-Eater 2" → `man eater 2`.
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        var s = raw.lowercase()
        s = s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        // Weitere Diakritika (é, à, ñ …) über NFD + Combining-Marks entfernen.
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        // Staffel-/Folgenanhänge als Paar entfernen, sonst bleibt die Zahl allein.
        s = s.replace(Regex("\\b(staffel|season|folge|episode)\\s*\\d{1,3}\\b"), " ")
        s = s.replace(Regex("\\bs\\d{1,2}\\s*e\\d{1,3}\\b"), " ")
        s = s.replace(Regex("\\b\\d{1,2}x\\d{1,3}\\b"), " ")
        // Jahreszahlen (vierstellig) sind kein Inhalt.
        s = s.replace(Regex("\\b(19|20)\\d{2}\\b"), " ")
        // Alles außer Buchstaben/Ziffern wird Trenner.
        s = s.replace(Regex("[^a-z0-9]+"), " ").trim()
        return s.split(' ').filter { it.isNotBlank() }
            .filter { it !in LEADING_ARTICLES && it !in STOP_WORDS && it !in NOISE_TOKENS }
            .joinToString(" ")
    }

    /**
     * Token-Dice-Ähnlichkeit (0..1) über die normalisierten Tokens.
     *
     * Gleiche Tokenmenge → 1,0. „Man-Eater" gegen „Man-Eater 2" bleibt unter
     * der Schwelle, weil das Sequenz-„2" ein eigenes Token ist. Ein Anhang wie
     * „– Staffel 1" wird vorher entfernt und ändert die Menge nicht.
     */
    fun similarity(a: String, b: String): Double {
        val ta = normalize(a).split(' ').filter { it.isNotBlank() }.toSet()
        val tb = normalize(b).split(' ').filter { it.isNotBlank() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        if (ta == tb) return 1.0
        val inter = ta.intersect(tb).size
        return 2.0 * inter / (ta.size + tb.size)
    }

    /** Enthält der Rohtitel einen Trailer-Marker? */
    fun looksLikeTrailer(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val s = raw.lowercase()
        return TRAILER_MARKERS.any { marker ->
            s.contains(marker) && !s.contains("trailerpark")
        }
    }

    /**
     * Prüft einen Kandidaten.
     *
     * @param evidenceTitle Titel, den die Quelle selbst für diesen Kandidaten
     *   angibt (Suchtreffer, API-Item oder Seitentitel). `null` = keine Angabe.
     * @param evidenceYear Jahr aus derselben Angabe, wenn vorhanden.
     * @param evidenceRuntimeSec Laufzeit aus derselben Angabe, wenn vorhanden.
     * @param evidenceUrl Adresse der Seite, aus der der Kandidat stammt (für
     *   die Episodenprüfung bei Serien).
     * @param sameSourceItem true, wenn der Kandidat aus genau dem Suchtreffer
     *   stammt, der für den angefragten Titel ausgewählt wurde.
     */
    fun verdict(
        request: IdentityRequest,
        evidenceTitle: String?,
        evidenceYear: Int? = null,
        evidenceRuntimeSec: Int? = null,
        evidenceUrl: String? = null,
        sameSourceItem: Boolean = false
    ): IdentityVerdict {
        if (evidenceTitle.isNullOrBlank()) {
            return IdentityVerdict(
                kind = IdentityKind.UNKNOWN,
                score = 0.0,
                reason = "keine Titelangabe der Quelle",
                runtimeUnknown = request.runtimeMin == null
            )
        }
        if (looksLikeTrailer(evidenceTitle)) {
            return IdentityVerdict(
                kind = IdentityKind.TRAILER_ONLY,
                score = similarity(request.title, evidenceTitle),
                matchedTitle = evidenceTitle,
                reason = "Trailer-Marker im Quellentitel",
                runtimeUnknown = request.runtimeMin == null,
                sameSourceItem = sameSourceItem
            )
        }
        val score = similarity(request.title, evidenceTitle)
        if (score < TITLE_THRESHOLD) {
            return IdentityVerdict(
                kind = IdentityKind.WRONG_CONTENT,
                score = score,
                matchedTitle = evidenceTitle,
                reason = "Titel passt nicht",
                runtimeUnknown = request.runtimeMin == null,
                sameSourceItem = sameSourceItem
            )
        }
        // Serie: Staffel/Folge muss in der Adresse stehen, wenn sie dort steht.
        if (request.isSeries && request.episode > 0) {
            val marker = episodeMarkerMismatch(evidenceUrl, request.season, request.episode)
            if (marker != null) {
                return IdentityVerdict(
                    kind = IdentityKind.WRONG_CONTENT,
                    score = score,
                    matchedTitle = evidenceTitle,
                    reason = marker,
                    runtimeUnknown = request.runtimeMin == null,
                    sameSourceItem = sameSourceItem
                )
            }
        }
        // Laufzeit: nur wenn beide Seiten sie kennen.
        val catalogRuntimeSec = request.runtimeMin?.times(60L)
        if (catalogRuntimeSec != null && evidenceRuntimeSec != null && evidenceRuntimeSec > 0) {
            val tolerance = if (request.isSeries) RUNTIME_TOLERANCE_SERIES else RUNTIME_TOLERANCE_MOVIE
            val delta = kotlin.math.abs(evidenceRuntimeSec - catalogRuntimeSec).toDouble() / catalogRuntimeSec
            if (delta > tolerance) {
                return IdentityVerdict(
                    kind = IdentityKind.WRONG_CONTENT,
                    score = score,
                    matchedTitle = evidenceTitle,
                    reason = "Laufzeit weicht ab",
                    runtimeUnknown = false,
                    sameSourceItem = sameSourceItem
                )
            }
        }
        return IdentityVerdict(
            kind = IdentityKind.CORRECT,
            score = score,
            matchedTitle = evidenceTitle,
            reason = "",
            runtimeUnknown = catalogRuntimeSec == null || evidenceRuntimeSec == null,
            sameSourceItem = sameSourceItem
        )
    }

    /**
     * Prüft die Episodenmarker in einer URL (`staffel-2/folge-3`,
     * `-s02e03`, `season/2/episode/3`). Steht dort nichts, ist die Prüfung
     * nicht möglich und blockiert nicht.
     */
    fun episodeMarkerMismatch(url: String?, season: Int, episode: Int): String? {
        if (url.isNullOrBlank()) return null
        val s = url.lowercase()
        val seasonMatch = Regex("staffel[-_/]?(\\d{1,2})|season[-_/]?(\\d{1,2})")
            .find(s)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        val episodeMatch = Regex("folge[-_/]?(\\d{1,3})|episode[-_/]?(\\d{1,3})|/(\\d{1,3})(?:/|$)")
            .find(s)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()
        val sxe = Regex("s(\\d{1,2})e(\\d{1,3})").find(s)?.groupValues?.drop(1)
        val urlSeason = seasonMatch ?: sxe?.getOrNull(0)?.toIntOrNull()
        val urlEpisode = episodeMatch ?: sxe?.getOrNull(1)?.toIntOrNull()
        if (urlSeason != null && urlSeason != season) {
            return "Staffel in der Adresse ist $urlSeason, angefragt $season"
        }
        if (urlEpisode != null && urlEpisode != episode) {
            return "Folge in der Adresse ist $urlEpisode, angefragt $episode"
        }
        return null
    }
}
