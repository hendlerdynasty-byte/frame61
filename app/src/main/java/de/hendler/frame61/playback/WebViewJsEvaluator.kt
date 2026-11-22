package de.hendler.frame61.playback

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.resolver.JsEvaluator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.json.JSONTokener

/** Evaluates a pack script until a real HTTPS media URL appears or time runs out. */
@Singleton
class WebViewJsEvaluator @Inject constructor(
    @ApplicationContext private val context: Context
) : JsEvaluator {

    /**
     * Nur eine WebView gleichzeitig.
     *
     * Mehrere parallele Erreaufbauten auf dem Hauptthread blockieren die
     * Nachrichten-Schleife (gemessen: ANR „Frame61 wird wiederholt beendet“,
     * weil das Vorwaermen vier WebViews gleichzeitig erzeugte).
     */
    private val webViewMutex = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun evaluate(url: String, script: String, timeoutMs: Long): String? =
        webViewMutex.withLock {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        var completed = false
                        lateinit var webView: WebView
                        fun finish(value: String?) {
                            if (completed) return
                            completed = true
                            runCatching { webView.destroy() }
                            continuation.resume(value)
                        }
                        fun probe() {
                            if (completed) return
                            webView.evaluateJavascript(script) { raw ->
                                val value = unquote(raw)
                                if (hasPlaybackResult(value)) {
                                    finish(value)
                                } else if (!completed) {
                                    handler.postDelayed({ probe() }, POLL_MS)
                                }
                            }
                        }
                        webView = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    probe()
                                }
                            }
                        }
                        continuation.invokeOnCancellation {
                            if (!completed) {
                                completed = true
                                runCatching { webView.destroy() }
                            }
                        }
                        webView.loadUrl(url)
                    }
                }
            }
        }

    private fun hasPlaybackResult(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        if (value.startsWith("https://")) return true
        return runCatching {
            JSONObject(value).optString("stream").startsWith("https://")
        }.getOrDefault(false)
    }

    /**
     * Lädt [url] in einer versteckten WebView und gibt die Cookies zurück, die
     * die Seite setzt (DDoS-Guard, Cloudflare, Hoster-Challenge).
     *
     * Die WebView wird nicht an die View-Hierarchie gehängt — der Nutzer sieht
     * keine Checkbox und keinen Dialog. Ein Cookie gilt als Ertrag, sobald es
     * nach dem Laden für [settleMs] stabil bleibt.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun cookiesFor(url: String, timeoutMs: Long = 15_000L, settleMs: Long = 1_200L): String? =
        webViewMutex.withLock {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine { continuation ->
                        val handler = Handler(Looper.getMainLooper())
                        var completed = false
                        lateinit var webView: WebView
                        fun finish(value: String?) {
                            if (completed) return
                            completed = true
                            runCatching { webView.destroy() }
                            continuation.resume(value)
                        }
                        fun harvest(): String? =
                            runCatching { CookieManager.getInstance().getCookie(url) }
                                .getOrNull()
                                ?.takeIf { it.isNotBlank() }
                        fun poll() {
                            if (completed) return
                            val cookies = harvest()
                            if (cookies != null) {
                                handler.postDelayed({ finish(cookies) }, settleMs)
                            } else {
                                handler.postDelayed({ poll() }, POLL_MS)
                            }
                        }
                        webView = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                    poll()
                                }
                            }
                        }
                        continuation.invokeOnCancellation {
                            if (!completed) {
                                completed = true
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
        return runCatching { (JSONTokener(raw).nextValue() as? String)?.takeIf { it.isNotBlank() } }
            .getOrNull()
    }

    private companion object {
        const val POLL_MS = 500L
    }
}
