package de.hendler.frame61.playback

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.resolver.JsEvaluator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener

/**
 * Runs a pack's `js` step in a short-lived WebView.
 *
 * The WebView shares the app cookie jar with the visible captcha frame, so a
 * page whose Cloudflare/JS challenge was solved there can be evaluated again
 * here. No JavaScript bridge is installed: the script only returns a string.
 * Every WebView is destroyed after the result or the 8 s timeout.
 */
@Singleton
class WebViewJsEvaluator @Inject constructor(
    @ApplicationContext private val context: Context
) : JsEvaluator {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun evaluate(url: String, script: String, timeoutMs: Long): String? =
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                view?.evaluateJavascript(script) { raw ->
                                    val value = unquote(raw)
                                    view.destroy()
                                    continuation.resume(value)
                                }
                            }
                        }
                    }
                    continuation.invokeOnCancellation {
                        runCatching { webView.destroy() }
                    }
                    webView.loadUrl(url)
                }
            }
        }

    private fun unquote(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return runCatching { (JSONTokener(raw).nextValue() as? String)?.takeIf { it.isNotBlank() } }
            .getOrNull()
    }
}
