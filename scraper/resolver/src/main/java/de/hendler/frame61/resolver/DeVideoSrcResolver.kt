package de.hendler.frame61.resolver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Eine Quelle aus der DEVIDEOSRC-Quellenliste. */
data class DeVideoSrcSource(
    val url: String,
    val name: String = "",
    val rank: Int = 0
)

/**
 * DEVIDEOSRC (`devideosrc.co`) ist eine dokumentierte Embed-API. Die
 * Player-Seite (`/movie/{id}`, `/serial/{id}`) holt ihre Quellenliste mit
 * `POST /api/embed-links` und dem Token aus eben dieser Seite. Antwort:
 * mehrere ** Provider-Embed-URLs** (Dropload, SuperVideo, Doodstream,
 * Mixdrop …) in Rangfolge — bei Serien je Episode.
 *
 * Ohne diese Stufe blieb die HTML-Player-Seite als Kandidat stehen. Gemessen
 * 23.11.2026 auf dem A52 (Lauf 2 und 3): `devideosrc.co` war der häufigste
 * Zielhost der `CONTAINER_UNSUPPORTED`-Fehlschläge (Index liefert die Seite,
 * der Player bekommt 3003). Mit der Liste kann die bestehende Kette
 * (Hoster-Pack, sonst verdeckter WebView-Abruf) arbeiten.
 *
 * Quelle: https://devideosrc.co/docs (abgerufen 23.11.2026), Antwortform
 * `/api/embed-links` sowie `embed-links`-Aufruf der Player-Seite selbst.
 */
class DeVideoSrcResolver(private val fetcher: BodyFetcher) {

    /**
     * Holt die Quellenliste einer DEVIDEOSRC-Seite. Leere Liste heißt: die
     * API hat nichts geliefert — der Aufrufer fällt dann auf den bisherigen
     * Pfad zurück. Nie eine Ausnahme nach außen.
     */
    suspend fun expand(url: String, episodeNumber: Int = 0): List<DeVideoSrcSource> {
        if (!matches(url)) return emptyList()
        val host = HosterResolver.hostOf(url) ?: return emptyList()
        val page = runCatching { fetcher.get(url, headersFor(url)) }.getOrNull() ?: return emptyList()
        val request = parsePageRequest(page)
        if (request == null || request.kind !in KINDS || request.id.isBlank()) {
            return emptyList()
        }
        val body = """{"type":"${request.kind}","id":"${request.id}","token":"${request.token}"}"""
        val answer = runCatching {
            fetcher.post(
                "https://$host/api/embed-links",
                body,
                headersFor(url) + ("Content-Type" to "application/json")
            )
        }.getOrNull() ?: return emptyList()
        return parseSources(answer, episodeNumber)
    }

    private fun headersFor(url: String): Map<String, String> = linkedMapOf(
        "User-Agent" to HosterResolver.DEFAULT_USER_AGENT,
        "Referer" to url
    )

    companion object {
        private val KINDS = setOf("movie", "tv")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /** Der Aufruf der Player-Seite selbst — Reihenfolge stets type,id,token. */
        private val STRINGIFY = Regex(
            """JSON\.stringify\(\{\s*type:\s*['"]([a-z]+)['"]\s*,\s*id:\s*"([^"]+)"\s*,""" +
                """token:\s*"([^"]+)"\s*\}"""
        )

        private val TOKEN = Regex("""token:\s*["']([^"']+)["']""")
        private val KIND = Regex("""['"]?type['"]?\s*[:=]\s*['"]([a-z]+)['"]""")
        private val ID = Regex("""['"]?id['"]?\s*[:=]\s*"([^"]+)"""")

        fun matches(url: String): Boolean {
            val host = HosterResolver.hostOf(url) ?: return false
            return HosterResolver.DEVIDEOSRC_DOMAINS.any { host == it || host.endsWith(".$it") }
        }

        /** Typ, ID und Token einer Player-Seite — alles, was die API verlangt. */
        fun parsePageRequest(page: String): DeVideoSrcRequest? {
            STRINGIFY.find(page)?.let { m ->
                return DeVideoSrcRequest(
                    kind = m.groupValues[1],
                    id = m.groupValues[2],
                    token = m.groupValues[3]
                )
            }
            // Rückfallebene: einzeln lesen, falls die Seite das Snippet
            // auseinanderzieht (Zeilenumbruch, andere Klammern).
            val token = TOKEN.find(page)?.groupValues?.get(1) ?: return null
            val kind = KIND.find(page)?.groupValues?.get(1) ?: return null
            val id = ID.find(page)?.groupValues?.get(1) ?: return null
            if (id.isBlank() || id.startsWith("http")) return null
            return DeVideoSrcRequest(kind, id, token)
        }

        /**
         * Film: `sources` direkt. Serie: `tv.seasons[].episodes[]`.
         *
         * Die Staffel ist für die App nicht bekannt — der Spieler bietet nur die
         * Episodennummer. Deshalb wird die **erste Staffel** verwendet (so auch
         * der Abspieler selbst: `currentKey = "1-1"`) und darin die passende
         * Folge gewählt; fehlt sie, die erste Folge der Staffel.
         */
        fun parseSources(body: String, episodeNumber: Int = 0): List<DeVideoSrcSource> {
            val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
                ?: return emptyList()
            (root["sources"] as? JsonArray)?.let { return sourcesOf(it) }
            val tv = root["tv"] as? JsonObject ?: return emptyList()
            val seasons = tv["seasons"] as? JsonArray ?: return emptyList()
            val season = seasons.filterIsInstance<JsonObject>().firstOrNull() ?: return emptyList()
            val episodes = (season["episodes"] as? JsonArray)?.filterIsInstance<JsonObject>()
                ?: return emptyList()
            val wanted = if (episodeNumber > 0) {
                episodes.firstOrNull { it["episode_number"]?.asIntOrNull() == episodeNumber }
                    ?: episodes.firstOrNull()
            } else {
                episodes.firstOrNull()
            } ?: return emptyList()
            val sources = wanted["sources"] as? JsonArray ?: return emptyList()
            return sourcesOf(sources)
        }

        private fun sourcesOf(array: JsonArray): List<DeVideoSrcSource> =
            array.filterIsInstance<JsonObject>()
                .mapNotNull { item ->
                    val url = (item["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                    if (!url.startsWith("https://")) return@mapNotNull null
                    DeVideoSrcSource(
                        url = url,
                        name = (item["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        rank = (item["rank"] as? JsonPrimitive)?.asIntOrNull() ?: 0
                    )
                }
                .sortedBy { it.rank }
                .distinctBy { it.url }

        private fun JsonElement?.asIntOrNull(): Int? =
            (this as? JsonPrimitive)?.intOrNull
    }
}

data class DeVideoSrcRequest(
    val kind: String,
    val id: String,
    val token: String
)