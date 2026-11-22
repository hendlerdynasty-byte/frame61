package de.hendler.frame61.playback

import android.util.Log
import de.hendler.frame61.resolver.CaptchaSolver
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Löst Hoster-Challenges still in einer versteckten WebView.
 *
 * Der Nutzer sieht keine Checkbox und keinen Dialog: [solve] liefert die
 * Cookies, die den Hoster durchlässig machen. Die Cookies werden pro Host
 * zwischengespeichert ([TTL_MS]), damit ein zweiter Titel desselben Hosters
 * ohne neuen Durchlauf startet. scheitert die stille Lösung, bleibt der
 * sichtbare [CaptchaBroker] als Rückfallebene erhalten.
 */
@Singleton
class HeadlessCaptchaSolver @Inject constructor(
    private val jsEvaluator: WebViewJsEvaluator
) : CaptchaSolver {

    private data class Entry(val cookies: String, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun solve(url: String, timeoutMs: Long): String? {
        val host = hostOf(url) ?: return null
        cache[host]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let {
            Log.i(TAG, "captcha-cache hit $host")
            return it.cookies
        }
        val cookies = jsEvaluator.cookiesFor(url, timeoutMs)
        if (cookies.isNullOrBlank()) {
            Log.w(TAG, "headless captcha ohne Cookies: $host")
            return null
        }
        cache[host] = Entry(cookies, System.currentTimeMillis())
        Log.i(TAG, "headless captcha geloest: $host (${cookies.length} Zeichen)")
        return cookies
    }

    /**
     * Vorwärmen beim App-Start: der erste Titel soll nicht auf eine
     * Challenge warten. Läuft still im Hintergrund, Fehler sind unkritisch.
     */
    fun warmUp(urls: List<String>) {
        urls.forEach { url ->
            val host = hostOf(url) ?: return@forEach
            if (cache[host]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS } != null) {
                return@forEach
            }
            scope.launch {
                runCatching { solve(url, WARMUP_TIMEOUT_MS) }
            }
        }
    }

    private fun hostOf(url: String): String? =
        Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)""").find(url)
            ?.groupValues?.get(1)
            ?.substringBefore(':')
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "Frame61Captcha"
        const val TTL_MS = 15L * 60L * 1000L
        const val WARMUP_TIMEOUT_MS = 12_000L
    }
}