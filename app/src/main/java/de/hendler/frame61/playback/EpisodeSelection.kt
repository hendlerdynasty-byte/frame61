package de.hendler.frame61.playback

/**
 * Eine Folge im Adressierungsschlüssel einer **einzelnen** Quelle.
 *
 * `id` ist die interne Id der Folge innerhalb der Quelle (bei ARD die
 * Page-Gateway-Id, bei aniworld die Episodenzeile), nicht die Nummer. Genau
 * diese Id braucht der Stream-Endpoint der Quelle — die Nummer allein
 * adressiert bei den meisten Packs gar nichts.
 */
data class EpisodeTarget(
    val id: String,
    val season: Int,
    val number: Int
)

/**
 * Auswahl der Folge, die zur angeforderten **Adresse** passt.
 *
 * Warum das existiert (Block 8, A52 24.11.2026): Der Player bekommt aus dem
 * Episoden-Metadatensnapshot Staffel **und** Folgennummer, die Auflösung
 * reichte bisher nur die Nummer weiter. Drei Stellen haben deshalb stillschweigend
 * die falsche Folge adressiert:
 *
 *  - bs.to suchte `season == 1 && number == n` — eine Staffel-2-Folge traf nie,
 *    anschliessend griff der Nummer-Fallback und spielte Staffel 1.
 *  - ARD suchte nur nach der Nummer und waehlte bei Mehrstaffel-Serien den
 *    ersten Treffer aus Staffel 1.
 *  - die Packs s.to und aniworld standen mit hart codiertem `staffel-1` im
 *    Stream-Endpoint — eine Staffel-2-Folge wurde als Staffel 1 abgerufen
 *    (404 oder fremde Folge).
 *
 * Der Abgleich läuft deshalb in drei Stufen: erst (Staffel, Nummer) exakt,
 * dann nur die Nummer (Quellen, die ihre Staffel nicht sauber ausweisen —
 * z. B. ARD-Grouping liefert Teaser der ersten Staffel), sonst die erste
 * Folge der Quelle. Ohne den zweiten Schritt würde eine Quelle, die
 * `season = 0` meldet, die angefragte Folge nie mehr treffen.
 *
 * Für quellenbelegte Adressen (aniworld) gilt stattdessen [selectExact]: kein
 * Nummer-, Staffel- oder Erstfolge-Fallback.
 */
internal object EpisodeSelection {

    /** Katalogtyp einer Serie. */
    private const val TYPE_SERIES = "series"

    /** Erste Folge einer Serie. */
    private const val FIRST_EPISODE = 1

    /**
     * Ohne Episodennummer ist eine **Serie** die erste Folge.
     *
     * Gemessen am 25.11.2026 auf dem A52 (Block 9): der Vorwaermlauf und der
     * Start aus der Detailansicht addressierten Serientitel mit `episode-0`.
     * s.to antwortet darauf mit **404** (2516 Bytes, keine Stream-Links),
     * `episode-1` dagegen mit **200** und vier Streams. Damit lieferte die
     * Vorwaermung fuer jede Serie "kein Kandidat" — sie lief, aber ohne
     * Wirkung (Logcat: `prefetch 'McGREGOR FOREVER' s1e0: kein Kandidat`).
     *
     * Filme bleiben bei 0: dort ist die Nummer bedeutungslos.
     */
    fun effectiveNumber(type: String?, number: Int): Int = when {
        number > 0 -> number
        type == TYPE_SERIES -> FIRST_EPISODE
        else -> number
    }

    fun select(
        episodes: List<EpisodeTarget>,
        season: Int,
        number: Int
    ): EpisodeTarget? {
        if (episodes.isEmpty()) return null
        if (number <= 0) return episodes.firstOrNull()
        episodes.firstOrNull { it.season == season && it.number == number }
            ?.let { return it }
        episodes.firstOrNull { it.number == number }?.let { return it }
        return episodes.firstOrNull()
    }

    /**
     * Strenger Pfad für Quellen mit belegter Staffel- und Folgenadresse
     * (aniworld). Nur ein eindeutiger exakter Treffer zählt; weder eine andere
     * Staffel noch die erste Folge dürfen als Ersatz dienen.
     */
    fun selectExact(
        episodes: List<EpisodeTarget>,
        season: Int,
        number: Int
    ): EpisodeTarget? {
        if (season <= 0 || number <= 0) return null
        return episodes.singleOrNull { it.season == season && it.number == number }
    }
}
