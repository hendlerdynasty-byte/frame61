package de.hendler.frame61.playback

import android.util.Log
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.ui.player.CatalogPlayback
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Wiedergabeweg für bs.to / burningseries.
 *
 * Die Kette ist mehrstufig und lässt sich nicht als Pack-Regel ausdrücken:
 *
 *  1. `GET /serie/<slug>` liefert die Episodentabelle. Jede Zeile verweist auf
 *     die Episodenseite, die dritte Spalte auf die Hoster-Seite (z. B. `/VOE`).
 *  2. Die Hoster-Seite deckt den Stream **nur per Klick** auf: `ajax/embed.php`
 *     verlangt ein reCAPTCHA-v2-Token. Der Weg ist deshalb der Browser: die
 *     versteckte WebView löst die Challenge, klickt den Hoster an und zeichnet
 *     den Medienabruf auf (siehe [WebViewMediaInterceptor]).
 *
 * Die Adressierung erfolgt über den **Slug** aus dem Katalog (`bs:<slug>`), weil
 * die Suche auf bs.to nur interne Nummern liefert, keine URLs.
 */
@Singleton
class BsToPlaybackResolver @Inject constructor(
    private val mediaInterceptor: WebViewMediaInterceptor
) {

    /** Der Fetcher wird hier erzeugt (kein Hilt-Provider nötig). */
    private val fetcher = OkHttpBodyFetcher()

    /** Eine gefundene Episode mit ihrer Hoster-Seite. */
    private data class Episode(val season: Int, val number: Int, val title: String, val hosterPage: String)

    suspend fun resolve(
        slug: String,
        episodeNumber: Int,
        season: Int = 1,
        titleId: String
    ): List<MirrorCandidate> = withContext(Dispatchers.IO) {
        val slug = slug.trim().trim('/')
        if (slug.isBlank()) return@withContext emptyList()
        val html = runCatching {
            fetcher.get("$BASE/serie/$slug", mapOf("User-Agent" to HosterResolver.DEFAULT_USER_AGENT))
        }.getOrElse {
            Log.w(TAG, "bs.to: Serie $slug nicht lesbar: ${it.message}")
            return@withContext emptyList()
        }
        val episodes = parseEpisodes(html, slug)
        if (episodes.isEmpty()) {
            Log.w(TAG, "bs.to: keine Episoden für $slug")
            return@withContext emptyList()
        }
        // Block 8: zuerst (Staffel, Nummer) — der alte Abgleich war
        // `season == 1 && number == n`, eine Staffel-2-Folge traf deshalb nie
        // und spielte über den Nummer-Fallback die Falsche.
        val key = EpisodeSelection.select(
            episodes.map { EpisodeTarget(it.hosterPage, it.season, it.number) },
            season = season,
            number = episodeNumber
        )?.id ?: return@withContext emptyList()
        val wanted = episodes.firstOrNull { it.hosterPage == key } ?: episodes.first()
        Log.i(TAG, "bs.to: $slug -> S${wanted.season}E${wanted.number} (${wanted.hosterPage})")

        // Der Browser löst Challenge + Klick, die Interception liefert die
        // echte Medien-URL samt Kopfzeilen.
        val intercepted = runCatching {
            mediaInterceptor.intercept(wanted.hosterPage, INTERCEPT_MS, CLICK_SCRIPT)
        }.getOrNull()
        if (intercepted == null || !intercepted.url.startsWith("https://")) {
            Log.w(TAG, "bs.to: keine Medien-URL für $slug S${wanted.season}E${wanted.number}")
            return@withContext emptyList()
        }
        Log.i(
            TAG,
            "bs.to: Medien-URL nach ${intercepted.elapsedMs} ms -> ${intercepted.url.take(80)}"
        )
        listOf(
            MirrorCandidate(
                sourceId = "bs-to",
                titleId = CatalogPlayback.stableId(titleId),
                episodeId = episodeNumber.toLong(),
                url = intercepted.url,
                lang = "de",
                quality = "auto",
                hosterId = "voe",
                healthScore = 1.0,
                legalFree = false,
                requestHeaders = intercepted.headers,
                cookies = intercepted.cookies
            )
        )
    }

    /**
     * Liest die Episodentabelle der Serienseite.
     *
     * Aufbau je Zeile: Nummer, Titel, Hoster-Link. Der Hoster-Link trägt den
     * Staffel-/Folgen-Index im Pfad (`serie/<slug>/<staffel>/<staffel>-<folge>-…`).
     */
    private fun parseEpisodes(html: String, slug: String): List<Episode> {
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return emptyList()
        val rows = doc.select("table.episodes tr")
        val fallback = doc.select("table tr")
        val candidates = if (rows.isNotEmpty()) rows else fallback
        return candidates.mapNotNull { row ->
            val links = row.select("a[href]")
            val hosterLink = links.lastOrNull { href ->
                val parts = href.attr("href").trim('/').split('/')
                parts.size >= 5 && parts[0] == "serie"
            } ?: return@mapNotNull null
            val href = hosterLink.attr("href").trim()
            val parts = href.trim('/').split('/')
            val season = parts.getOrNull(2)?.toIntOrNull() ?: 1
            val number = parts.getOrNull(3)?.substringBefore('-')?.toIntOrNull()
                ?: row.select("td").firstOrNull()?.text()?.trim()?.toIntOrNull()
                ?: return@mapNotNull null
            val title = row.select("td").getOrNull(1)?.text()?.trim().orEmpty()
            Episode(
                season = season,
                number = number,
                title = title,
                hosterPage = "$BASE/${href.trimStart('/')}"
            )
        }.distinctBy { it.season to it.number }
    }

    private companion object {
        const val TAG = "Frame61BsTo"
        const val BASE = "https://burningseries.ac"

        /** Budget für Challenge + Klick + Medienabruf. */
        const val INTERCEPT_MS = 25_000L

        /**
         * Startet die Hoster-Aufdeckung auf der bs.to-Seite.
         *
         * bs.to bindet den Aufruf an einen Klick und prüft dabei
         * `navigator.webdriver` sowie `originalEvent.which/pageX/pageY`. Der
         * Klick wird deshalb als jQuery-Ereignis mit belegten Koordinaten
         * ausgelöst; die WebView läuft als normaler Browser (echte
         * User-Agent, keine Automatisierungserkennung).
         */
        const val CLICK_SCRIPT =
            "(function(){try{" +
                "if(window.__f61bs){return 'bereits';}" +
                "window.__f61bs=true;" +
                "try{Object.defineProperty(navigator,'webdriver',{get:function(){return false;}});}catch(e){}" +
                "var el=document.querySelector('section.serie .hoster-player')||" +
                "document.querySelector('.hoster-player');" +
                "if(!el){return 'kein-player';}" +
                "if(window.jQuery){" +
                "var ev=jQuery.Event('click');" +
                "ev.originalEvent={which:1,pageX:120,pageY:120};" +
                "jQuery(el).trigger(ev);" +
                "return 'geklickt-jquery';" +
                "}" +
                "el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));" +
                "return 'geklickt-dom';" +
                "}catch(e){return 'fehler:'+e.message;}})()"
    }
}
