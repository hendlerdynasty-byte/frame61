package de.hendler.frame61.playback

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.net.HttpClientFactory
import de.hendler.frame61.resolver.InterceptedMedia
import de.hendler.frame61.resolver.MediaInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Löst Hoster-Embeds still über eine versteckte WebView.
 *
 * Statt das Redirect-JavaScript nachzubauen, lädt der Browser der Seite die
 * Embed-URL selbst (inkl. DDoS-Guard-/Cloudflare-Challenge) und wir
 * **zeichnen** den Medienabruf auf: `.m3u8`, `.mp4`, `.mpd`, `.mkv` oder ein
 * `video/…`-Antworttyp.
 *
 * Zwei Punkte sind bewusst so gewählt:
 *
 *  - `shouldInterceptRequest` gibt **null** zurück. Die Anfrage läuft normal
 *    weiter; der WebView liefert die Datei nicht aus. Der Player lädt sie
 *    selbst — mit denselben Kopfzeilen, die die Seite benutzt hat.
 *  - Die Kopfzeilen der aufgezeichneten Anfrage (Referer, Origin, Cookie)
 *    wandern mit zum Player. Ohne sie verweigern VOE, Filemoon und Co. den
 *    Abruf, sobald die Seite weitergeleitet hat.
 *
 * Jeder Versuch wird in [InterceptionLog] protokolliert: Ausgang, **gemessene**
 * Zeit und die Request-Kette der Seite. Ohne diesen Beleg ist nicht
 * unterscheidbar, ob die Kette an der Challenge, am Hoster-Redirect oder am
 * Medienabruf gehalten hat.
 *
 * Der Nutzer sieht nichts: Die WebView hängt nicht an der View-Hierarchie.
 */
@Singleton
class WebViewMediaInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaInterceptor {

    /**
     * Nur eine WebView gleichzeitig — mehrere blockieren den Hauptthread.
     * Die Sperre ist app-weit (siehe [WebViewGate]), damit Auswertung, Cookie-
     * Ernte und Interception nie parallel laufen.
     */
    override suspend fun intercept(url: String, timeoutMs: Long, script: String?): InterceptedMedia? {
        val seq = InterceptionLog.nextSeq()
        val runStartedAt = System.currentTimeMillis()
        // Die Request-Kette wird mitgeschrieben: sie zeigt, welche Station der
        // Kette erreicht wurde und wo sie stehen blieb (Embed-Seite, Challenge,
        // Hoster-Redirect, Medienabruf).
        val requests = ConcurrentLinkedQueue<String>()
        val scriptResult = AtomicReference<String?>(null)
        val lastPage = AtomicReference<String?>(null)
        val media = AtomicReference<InterceptedMedia?>(null)
        val resolved = AtomicBoolean(false)
        // Die versteckte WebView ist app-weit serialisiert. Wartet der Versuch
        // zu lange auf [WebViewGate], hat er das Quellbudget ohnehin aufgebraucht
        // und wäre wertlos — Messung 23.11. (A52, be45079): 7 von 14 Versuchen
        // endeten so, **ohne** eine einzige Anfrage in der WebView.
        var gateHeld = false
        try {
            if (!acquireGate(QUEUE_WAIT_MS)) return null
            gateHeld = true
            return withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    runWebView(url, script, requests, scriptResult, lastPage, media, resolved)
                }
            }
        } finally {
            if (gateHeld) WebViewGate.mutex.unlock()
            // Echte Zeit, nicht der Deckel: der Versuch wird protokolliert,
            // egal ob er mit einer Medien-URL endet, ins Leere läuft oder von
            // außen abgebrochen wird.
            val elapsed = System.currentTimeMillis() - runStartedAt
            val found = media.get()
            val outcome = when {
                found != null -> "medien"
                resolved.get() -> "kein-medienabruf"
                !gateHeld -> "uebersprungen"
                else -> "abbruch"
            }
            InterceptionLog.add(
                InterceptionLog.Record(
                    seq = seq,
                    url = url,
                    outcome = outcome,
                    elapsedMs = elapsed,
                    mediaUrl = found?.url,
                    scriptResult = scriptResult.get(),
                    lastPage = lastPage.get(),
                    requests = requests.toList()
                )
            )
            Log.i(
                TAG,
                "Interception $url -> $outcome nach ${elapsed} ms, ${requests.size} Anfragen"
            )
        }
    }

    /**
     * Holt die app-weite WebView-Sperre, aber höchstens [waitMs] lang. Danach
     * kehrt der Versuch mit `false` zurück, statt das Quellbudget zu fressen.
     */
    private suspend fun acquireGate(waitMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + waitMs
        while (!WebViewGate.mutex.tryLock()) {
            if (System.currentTimeMillis() >= deadline) return false
            delay(GATE_POLL_MS)
        }
        return true
    }

    /**
     * Der eigentliche WebView-Lauf. Läuft auf dem Hauptthread (die WebView
     * braucht ihn), hängt aber nicht in der View-Hierarchie.
     */
    private suspend fun runWebView(
        url: String,
        script: String?,
        requests: ConcurrentLinkedQueue<String>,
        scriptResult: AtomicReference<String?>,
        lastPage: AtomicReference<String?>,
        media: AtomicReference<InterceptedMedia?>,
        resolved: AtomicBoolean
    ): InterceptedMedia? = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val startedAt = System.currentTimeMillis()
        var finished = false
        lateinit var webView: WebView

        fun finish(value: InterceptedMedia?) {
            if (finished) return
            finished = true
            resolved.set(true)
            runCatching { webView.stopLoading() }
            runCatching { webView.destroy() }
            continuation.resume(value)
        }

        /** Medien-URL plus die Kopfzeilen, mit denen sie abgerufen wurde. */
        fun result(mediaUrl: String, request: WebResourceRequest?): InterceptedMedia {
            val requestHeaders = request?.requestHeaders.orEmpty()
            val pageCookies = runCatching {
                CookieManager.getInstance().getCookie(mediaUrl)
            }.getOrNull()
            val cookies = listOfNotNull(
                requestHeaders["Cookie"]?.takeIf { it.isNotBlank() },
                pageCookies?.takeIf { it.isNotBlank() }
            ).distinct().joinToString("; ").takeIf { it.isNotBlank() }
            val headers = linkedMapOf<String, String>()
            requestHeaders["Referer"]?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
            requestHeaders["Origin"]?.takeIf { it.isNotBlank() }?.let { headers["Origin"] = it }
            requestHeaders["User-Agent"]?.takeIf { it.isNotBlank() }
                ?.let { headers["User-Agent"] = it }
            headers["Referer"] ?: url.let { headers["Referer"] = it }
            headers["User-Agent"] ?: HttpClientFactory.DEFAULT_USER_AGENT
                .let { headers["User-Agent"] = it }
            return InterceptedMedia(
                url = mediaUrl,
                cookies = cookies,
                headers = headers,
                elapsedMs = System.currentTimeMillis() - startedAt
            )
        }

        fun probeDom() {
            if (finished) return
            webView.evaluateJavascript(DOM_PROBE) { raw ->
                val found = unquote(raw)
                if (!found.isNullOrBlank() && found.startsWith("http")) {
                    val value = result(found, null)
                    media.set(value)
                    finish(value)
                } else if (!finished) {
                    handler.postDelayed({ probeDom() }, POLL_MS)
                }
            }
        }

        /** Skript nach dem Laden ausführen (z. B. der bs.to-Klick). */
        fun runScript() {
            if (finished || script.isNullOrBlank()) return
            webView.evaluateJavascript(script) { raw ->
                val value = unquote(raw)
                scriptResult.set(value)
                Log.i(TAG, "Skript auf $url -> ${value ?: "-"}")
                probeDom()
            }
        }

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = HttpClientFactory.DEFAULT_USER_AGENT
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    val target = request.url.toString()
                    if (requests.size < MAX_RECORDED_REQUESTS) {
                        requests.add("${request.method} ${shortUrl(target)}")
                    }
                    if (MediaUrlMatcher.isMediaUrl(target)) {
                        // **Durchlassen**: null bedeutet, die WebView
                        // holt die Datei selbst. Wir zeichnen nur auf.
                        Log.i(TAG, "Medienabruf aufgezeichnet: $target")
                        handler.post {
                            val found = result(target, request)
                            media.set(found)
                            finish(found)
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                    lastPage.set(finishedUrl)
                    if (script.isNullOrBlank()) probeDom() else runScript()
                }
            }
        }
        continuation.invokeOnCancellation {
            if (!finished) {
                finished = true
                runCatching { webView.destroy() }
            }
        }
        webView.loadUrl(url)
    }

    /**
     * Vorwärmen beim App-Start: einmal pro Host die Seite laden, damit Cookies
     * (Cloudflare/DDoS-Guard) und der JS-Cache bereit sind, bevor der Nutzer
     * einen Titel antippt. Läuft still, verkettet über [WebViewGate] und
     * blockiert den Hauptthread nicht.
     */
    suspend fun warmUp(urls: List<String>, dwellMs: Long = 2_000L) {
        for (url in urls) {
            val ok = runCatching {
                WebViewGate.mutex.withLock {
                    withContext(Dispatchers.Main) {
                        withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
                            suspendCancellableCoroutine { continuation ->
                                val handler = Handler(Looper.getMainLooper())
                                var done = false
                                lateinit var webView: WebView
                                fun finish() {
                                    if (done) return
                                    done = true
                                    runCatching { webView.stopLoading() }
                                    runCatching { webView.destroy() }
                                    continuation.resume(Unit)
                                }
                                webView = WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.userAgentString = HttpClientFactory.DEFAULT_USER_AGENT
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                            handler.postDelayed({ finish() }, dwellMs)
                                        }
                                    }
                                }
                                continuation.invokeOnCancellation {
                                    if (!done) {
                                        done = true
                                        runCatching { webView.destroy() }
                                    }
                                }
                                webView.loadUrl(url)
                            }
                        }
                    }
                }
            }.isSuccess
            Log.i(TAG, "Prewarm $url -> ${if (ok) "ok" else "fehlgeschlagen"}")
        }
    }

    private fun unquote(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            org.json.JSONTokener(raw).nextValue() as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Host + Pfad ohne Query — die Request-Kette bleibt lesbar. */
    private fun shortUrl(url: String): String {
        val host = url.substringAfter("://", "").substringBefore('/')
        val path = url.substringAfter('/', "")
        return "$host/${path.substringBefore('?').take(70)}"
    }

    private companion object {
        const val TAG = "Frame61Intercept"
        const val POLL_MS = 500L
        const val WARMUP_TIMEOUT_MS = 15_000L

        /**
         * Längste Wartezeit auf die app-weite WebView-Sperre. Die Interception
         * ist die letzte Stufe einer langen Kette; wer hier zu lange steht,
         * hat das Budget des Titels aufgebraucht.
         */
        const val QUEUE_WAIT_MS = 5_000L

        /** Takt, in dem die Sperre erneut versucht wird. */
        const val GATE_POLL_MS = 60L

        /** Mehr Requests werden nicht protokolliert — die Kette bleibt lesbar. */
        const val MAX_RECORDED_REQUESTS = 30

        /** Sucht im DOM nach einer echten Medien-URL, falls kein Request abgefangen wird. */
        const val DOM_PROBE =
            "(function(){try{" +
                "var v=document.querySelector('video');" +
                "if(v&&v.src&&v.src.indexOf('http')===0)return v.src;" +
                "var s=document.querySelectorAll('source');" +
                "for(var i=0;i<s.length;i++){if(s[i].src&&s[i].src.indexOf('http')===0)return s[i].src;}" +
                "if(window.sources&&sources.length){var f=sources[0].file||sources[0].src;" +
                "if(f&&f.indexOf('http')===0)return f;}" +
                "if(typeof getActiveLink==='function'){var l=getActiveLink();" +
                "if(l&&l.indexOf('http')===0)return l;}" +
                "}catch(e){}return '';})()"

        private val MEDIA_PATH = Regex(
            """(?i)\.(?:m3u8|mp4|mpd|mkv|webm|mov|m4v)(?:[?#]|$)"""
        )
    }
}

/**
 * Erkennt echte Medienziele — getrennt vom Interceptor, damit die Regel ohne
 * Geraet geprueft werden kann (app/src/test/.../WebViewMediaInterceptorTest).
 */
internal object MediaUrlMatcher {

    private val MEDIA_PATH = Regex(
        """(?i)\.(?:m3u8|mp4|mpd|mkv|webm|mov|m4v)(?:[?#]|$)"""
    )

    /**
     * Doodstream liefert die Medien-URL **ohne Dateiendung**:
     * `/pass_md5/…` antwortet mit `https://<cdn>/<pfad>~` und `makePlay()`
     * hängt `<10 Zufallszeichen>?token=…&expiry=…` an. Gemessen 23.11.2026
     * an `playmogo.com/e/…`:
     * `https://uha211.cloudatacdn.com/<hex>/myshk2p1j7~…`. Weder CDN-Host
     * noch `~` tragen eine Endung — deshalb explizit erkennen.
     */
    private val DOOD_CDN = Regex("""(?i)cloudatacdn\.com""")

    /** `<pfad>~<zeichenkette>?token=…` bzw. `…&expiry=…` */
    private val DOOD_PLAY = Regex("""(?i)~[a-z0-9]{6,40}\?(?:token|expiry|download)=""")

    /** Nur echte Medienziele, keine HTML-/JS-/CSS-URLs. */
    fun isMediaUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        if (MEDIA_PATH.containsMatchIn(url)) return true
        // Manche Hosts liefern HLS ohne Endung, aber mit Query.
        if (url.contains("master.m3u8") || url.contains("/hls/") ||
            url.contains("playlist.m3u8")
        ) {
            return true
        }
        if (DOOD_CDN.containsMatchIn(url)) return true
        return DOOD_PLAY.containsMatchIn(url)
    }
}
