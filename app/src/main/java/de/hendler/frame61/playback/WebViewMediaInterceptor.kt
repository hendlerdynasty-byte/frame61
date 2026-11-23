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
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Löst Hoster-Embeds still über eine versteckte WebView.
 *
 * Statt das Redirect-JavaScript nachzubauen, lädt der Browser der Seite die
 * Embed-URL selbst (inklusive DDoS-Guard-/Cloudflare-Challenge) und wir
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
    override suspend fun intercept(url: String, timeoutMs: Long, script: String?): InterceptedMedia? =
        WebViewGate.mutex.withLock {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        val startedAt = System.currentTimeMillis()
                        var finished = false
                        lateinit var webView: WebView

                        fun finish(value: InterceptedMedia?) {
                            if (finished) return
                            finished = true
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
                                    finish(result(found, null))
                                } else if (!finished) {
                                    handler.postDelayed({ probeDom() }, POLL_MS)
                                }
                            }
                        }

                        /** Skript nach dem Laden ausführen (z. B. der bs.to-Klick). */
                        fun runScript() {
                            if (finished || script.isNullOrBlank()) return
                            webView.evaluateJavascript(script) { raw ->
                                Log.i(TAG, "Skript auf $url -> ${unquote(raw) ?: "-"}")
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
                                    if (isMediaUrl(target)) {
                                        // **Durchlassen**: null bedeutet, die WebView
                                        // holt die Datei selbst. Wir zeichnen nur auf.
                                        Log.i(TAG, "Medienabruf aufgezeichnet: $target")
                                        handler.post { finish(result(target, request)) }
                                    }
                                    return null
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
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
                }
            }
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

    private companion object {
        const val TAG = "Frame61Intercept"
        const val POLL_MS = 500L
        const val WARMUP_TIMEOUT_MS = 15_000L

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

        /** Nur echte Medienziele, keine HTML-/JS-/CSS-URLs. */
        fun isMediaUrl(url: String): Boolean {
            if (!url.startsWith("http")) return false
            if (MEDIA_PATH.containsMatchIn(url)) return true
            // Manche Hosts liefern HLS ohne Endung, aber mit Query.
            return url.contains("master.m3u8") || url.contains("/hls/") ||
                url.contains("playlist.m3u8")
        }
    }
}
