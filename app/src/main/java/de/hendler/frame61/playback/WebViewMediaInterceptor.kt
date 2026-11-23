package de.hendler.frame61.playback

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.resolver.InterceptedMedia
import de.hendler.frame61.resolver.MediaInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Löst Hoster-Embeds still über eine versteckte WebView.
 *
 * Statt das Redirect-JavaScript nachzubauen, lädt der Browser der Seite die
 * Embed-URL selbst (inklusive DDoS-Guard-/Cloudflare-Challenge) und wir fangen
 * den Medienabruf über [WebViewClient.shouldInterceptRequest] ab: `.m3u8`,
     * `.mp4`, `.mpd`, `.mkv` oder ein `video/*`-Antworttyp.
 *
 * Der Nutzer sieht nichts: Die WebView hängt nicht an der View-Hierarchie,
 * Cookies werden ausgelesen und an den Player weitergegeben.
 */
@Singleton
class WebViewMediaInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaInterceptor {

    /** Nur eine WebView gleichzeitig — mehrere blockieren den Hauptthread. */
    private val mutex = Mutex()

    override suspend fun intercept(url: String, timeoutMs: Long): InterceptedMedia? =
        mutex.withLock {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        var finished = false
                        lateinit var webView: WebView

                        fun finish(value: InterceptedMedia?) {
                            if (finished) return
                            finished = true
                            runCatching { webView.stopLoading() }
                            runCatching { webView.destroy() }
                            continuation.resume(value)
                        }

                        fun result(mediaUrl: String): InterceptedMedia {
                            val cookies = runCatching {
                                CookieManager.getInstance().getCookie(url)
                            }.getOrNull()?.takeIf { it.isNotBlank() }
                            return InterceptedMedia(mediaUrl, cookies)
                        }

                        fun probeDom() {
                            if (finished) return
                            webView.evaluateJavascript(DOM_PROBE) { raw ->
                                val found = unquote(raw)
                                if (!found.isNullOrBlank() && found.startsWith("http")) {
                                    finish(result(found))
                                } else if (!finished) {
                                    handler.postDelayed({ probeDom() }, POLL_MS)
                                }
                            }
                        }

                        webView = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest
                                ): WebResourceResponse? {
                                    val target = request.url.toString()
                                    if (isMediaUrl(target)) {
                                        handler.post { finish(result(target)) }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    probeDom()
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

    private fun unquote(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching {
            org.json.JSONTokener(raw).nextValue() as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val POLL_MS = 500L

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