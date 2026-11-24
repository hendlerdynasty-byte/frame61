package de.hendler.frame61.resolver

import de.hendler.frame61.net.PlaybackDiagnostics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Eine Quelle aus der DEVIDEOSRC-Quellenliste. */
data class DeVideoSrcSource(
    val url: String,
    val name: String = "",
    val rank: Int = 0
)

/**
 * Der Aufruf, den die Player-Seite selbst macht — in der Form, in der die
 * Seite ihn sendet.
 *
 * Zwei Formen kommen vor:
 *  - **Film** (`/movie/{id}`): `POST /api/embed-links` mit
 *    `{type, id, token}` (der Aufruf steht als `JSON.stringify(...)` im HTML).
 *  - **Serie** (`/custom/serial/{id}/{staffel}/{folge}`): `POST /api/resolve`
 *    mit `{type, id, season, episode, mode, token}`; Typ, ID und Token stehen
 *    als `data-*`-Attribute am `<main>`-Element.
 *
 * Beleg 24.11.2026: Live-Abfrage beider Endpunkte, Antwortform `sources[]`
 * mit `{name, url, rank}`.
 */
data class DeVideoSrcRequest(
    val kind: String,
    val id: String,
    val token: String,
    /** Relativer Endpunkt: `/api/embed-links` oder `/api/resolve`. */
    val endpoint: String = ENDPOINT_EMBED_LINKS,
    val season: String? = null,
    val episode: String? = null,
    val mode: String? = null
) {
    /** Der JSON-Körper des Aufrufs. */
    fun body(): String = buildJsonObject {
        put("type", kind)
        put("id", id)
        season?.takeIf { it.isNotBlank() }?.let { put("season", it) }
        episode?.takeIf { it.isNotBlank() }?.let { put("episode", it) }
        mode?.takeIf { it.isNotBlank() }?.let { put("mode", it) }
        put("token", token)
    }.toString()

    companion object {
        const val ENDPOINT_EMBED_LINKS = "/api/embed-links"
        const val ENDPOINT_RESOLVE = "/api/resolve"
    }
}

/**
 * DEVIDEOSRC (`devideosrc.co`) ist eine dokumentierte Embed-API. Die
 * Player-Seite (`/movie/{id}`, `/custom/serial/{id}/{s}/{e}`) holt ihre
 * Quellenliste über `POST /api/embed-links` bzw. `POST /api/resolve` mit dem
 * Token aus eben dieser Seite. Antwort: mehrere **Provider-Embed-URLs**
 * (Dropload, SuperVideo, Doodstream, Mixdrop …) in Rangfolge.
 *
 * Ohne diese Stufe blieb die HTML-Player-Seite als Kandidat stehen. Gemessen
 * 23.11.2026 auf dem A52 (Lauf 2 und 3): `devideosrc.co` war der häufigste
 * Zielhost der `CONTAINER_UNSUPPORTED`-Fehlschläge (Index liefert die Seite,
 * der Player bekommt 3003). Mit der Liste kann die bestehende Kette
 * (Hoster-Pack, sonst verdeckter WebView-Abruf) arbeiten.
 *
 * **Befund 24.11.2026 (Lauf 4, A52):** Die API-Stufe lief auf dem Gerät nie
 * an — 16 GETs auf die Player-Seite, **null** POSTs. Ursache: der Regex für
 * den `JSON.stringify`-Aufruf war an der Zeilengrenze zerschnitten
 * (`...id:\s*"([^"]+)"\s*,` + `token:` ⇒ das Leerzeichen nach dem Komma
 * fehlte) und traf deshalb nie; die Rückfallebene griff dann das **erste**
 * `type=`/`id=` im HTML (`id="_cloudToggle"`) statt der Film-ID. Belegt in
 * `DeVideoSrcResolverTest` mit der echten Seitenstruktur.
 *
 * Quelle: https://devideosrc.co/docs (abgerufen 23.11.2026), die
 * Endpunkt- und Körperform zusätzlich aus `/assets/player.js` belegt
 * (24.11.2026).
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
        val page = runCatching { fetcher.get(url, headersFor(url)) }
            .onFailure { note("GET der Player-Seite fehlgeschlagen: ${it.message}", url) }
            .getOrNull() ?: return emptyList()
        note("Player-Seite geladen (${page.length} B)", url)
        val request = parsePageRequest(page)
        if (request == null) {
            note("keine Typ/ID/Token-Angabe in der Seite gefunden", url)
            return emptyList()
        }
        if (request.kind !in KINDS) {
            note("Typ '${request.kind}' wird nicht unterstuetzt", url)
            return emptyList()
        }
        val endpoint = "https://$host${request.endpoint}"
        val answer = runCatching {
            fetcher.post(
                endpoint,
                request.body(),
                headersFor(url) + ("Content-Type" to "application/json")
            )
        }.onFailure { note("POST $request.endpoint fehlgeschlagen: ${it.message}", endpoint) }
            .getOrNull() ?: return emptyList()
        val sources = parseSources(answer, episodeNumber)
        note("Antwort ${answer.length} B: ${sources.size} Quellen", endpoint)
        return sources
    }

    private fun headersFor(url: String): Map<String, String> = linkedMapOf(
        "User-Agent" to HosterResolver.DEFAULT_USER_AGENT,
        "Referer" to url
    )

    /**
     * Jeder Schritt wird protokolliert. Ohne aktive Diagnose-Sitzung sind die
     * Ereignisse ein No-op (der Resolver läuft auch im Normalbetrieb).
     */
    private fun note(message: String, url: String? = null) {
        PlaybackDiagnostics.record(
            category = "RESOLVER",
            message = "devideosrc: $message",
            url = url
        )
    }

    companion object {
        private val KINDS = setOf("movie", "tv")

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        /**
         * Der Aufruf der Player-Seite selbst — Reihenfolge stets type,id,token.
         *
         * Wichtig: nach dem Komma muss `\s*` stehen. Ohne das matcht der
         * Ausdruck die Seite **nicht** (`id: "tt1", token: "…"` hat ein
         * Leerzeichen nach dem Komma) — genau der Fehler, der auf dem Gerät
         * jede devideosrc-Auflösung verhindert hat (Lauf 4, 24.11.2026).
         */
        private val STRINGIFY = Regex(
            """JSON\.stringify\(\{\s*type:\s*['"]([a-z]+)['"]\s*,\s*id:\s*"([^"]+)"\s*,""" +
                """\s*token:\s*"([^"]+)"\s*\}"""
        )

        /** Attribute der Serien-Seite (`<main class="player-shell …">`). */
        private val ATTRIBUTE = Regex("""data-([a-z-]+)="([^"]*)"""")

        private val TOKEN = Regex("""token:\s*["']([^"']+)["']""")
        private val KIND = Regex("""['"]?type['"]?\s*[:=]\s*['"]([a-z]+)['"]""")
        private val ID = Regex("""['"]?id['"]?\s*[:=]\s*"([^"]+)""")

        fun matches(url: String): Boolean {
            val host = HosterResolver.hostOf(url) ?: return false
            return HosterResolver.DEVIDEOSRC_DOMAINS.any { host == it || host.endsWith(".$it") }
        }

        /** Typ, ID und Token einer Player-Seite — alles, was die API verlangt. */
        fun parsePageRequest(page: String): DeVideoSrcRequest? {
            // 1) Serien-Seite: Typ, ID und Token stehen als data-Attribute.
            attributeRequest(page)?.let { return it }
            // 2) Standard-Player-Seite: der JSON.stringify-Aufruf im HTML.
            STRINGIFY.find(page)?.let { match ->
                return DeVideoSrcRequest(
                    kind = match.groupValues[1],
                    id = match.groupValues[2],
                    token = match.groupValues[3]
                )
            }
            // 3) Rueckfallebene: nur **innerhalb** eines JSON.stringify-Aufrufs
            //    suchen. Sonst greift das erste `type=`/`id=` des HTML — auf der
            //    echten Seite ist das `id="_cloudToggle"` (Befund 24.11.2026).
            var index = page.indexOf("JSON.stringify(")
            while (index >= 0) {
                val slice = page.substring(index + "JSON.stringify(".length, minOf(page.length, index + 600))
                val request = looseRequest(slice)
                if (request != null) return request
                index = page.indexOf("JSON.stringify(", index + 1)
            }
            // 4) Ganz umgeschriebene Seite ohne JSON.stringify (selten).
            return looseRequest(page)
        }

        /** Die drei Felder ohne JSON.stringify-Rahmen suchen. */
        private fun looseRequest(text: String): DeVideoSrcRequest? {
            val token = TOKEN.find(text)?.groupValues?.get(1) ?: return null
            val kind = KIND.find(text)?.groupValues?.get(1) ?: return null
            val id = ID.find(text)?.groupValues?.get(1) ?: return null
            if (id.isBlank() || id.startsWith("http")) return null
            return DeVideoSrcRequest(kind, id, token)
        }

        /** Die Serien-Seite: `<main … data-type="tv" data-id="tt…" …>`. */
        private fun attributeRequest(page: String): DeVideoSrcRequest? {
            val attributes = ATTRIBUTE.findAll(page)
                .associate { it.groupValues[1] to it.groupValues[2] }
            val kind = attributes["type"]?.takeIf { it.isNotBlank() } ?: return null
            val id = attributes["id"]?.takeIf { it.isNotBlank() } ?: return null
            val token = attributes["resolve-token"]?.takeIf { it.isNotBlank() } ?: return null
            return DeVideoSrcRequest(
                kind = kind,
                id = id,
                token = token,
                endpoint = attributes["resolve-endpoint"]
                    ?.takeIf { it.startsWith("/") }
                    ?: DeVideoSrcRequest.ENDPOINT_RESOLVE,
                season = attributes["season"],
                episode = attributes["episode"],
                mode = attributes["mode"]
            )
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
