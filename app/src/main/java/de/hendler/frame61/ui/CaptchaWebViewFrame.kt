package de.hendler.frame61.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.hendler.frame61.playback.CaptchaResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Visible WebView for hosters with `captcha: webview`.
 *
 * It is the only place with `addJavascriptInterface`. The page gets a single
 * bridge method for a token; additionally a read-only JS probe extracts a
 * direct stream URL (`video/source`, `window.sources`, jwplayer, performance
 * entries) and the cookies of the final URL. The resolver receives that result
 * and continues its HTTP chain with the cookie jar.
 *
 * The frame closes after 20 s without a result; the resolver then tries the
 * next mirror.
 */
@Composable
fun CaptchaWebViewFrame(
    url: String,
    hoster: String?,
    modifier: Modifier = Modifier,
    onResult: (CaptchaResult) -> Unit,
    onCancel: () -> Unit
) {
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    var finalUrl by remember(url) { mutableStateOf(url) }
    var finished by remember(url) { mutableStateOf(false) }
    val bridge = remember(url) { Frame61Bridge(url) }

    DisposableEffect(bridge) {
        onDispose { bridge.dispose() }
    }
    DisposableEffect(webView) {
        onDispose { webView?.destroy() }
    }

    LaunchedEffect(webView, url) {
        val view = webView ?: return@LaunchedEffect
        withTimeoutOrNull(20_000L) {
            repeat(20) { second ->
                delay(1_000)
                if (finished) return@LaunchedEffect
                val state = readState(view, finalUrl) ?: return@repeat
                val cookies = CookieManager.getInstance().getCookie(finalUrl)
                    ?.takeIf { it.isNotBlank() }
                if (state.streamUrl != null || (cookies != null && second >= 3)) {
                    finished = true
                    onResult(
                        CaptchaResult(
                            token = state.token,
                            cookies = cookies,
                            streamUrl = state.streamUrl
                        )
                    )
                    return@LaunchedEffect
                }
            }
        }
        if (!finished) {
            finished = true
            onCancel()
        }
    }

    BackHandler(enabled = !finished) {
        finished = true
        onCancel()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (!url.isNullOrBlank()) finalUrl = url
                        }

                        override fun onPageFinished(view: WebView?, loaded: String?) {
                            if (!loaded.isNullOrBlank()) finalUrl = loaded
                            view?.evaluateJavascript(PROBE_SCRIPT) { raw ->
                                val value = unquote(raw)
                                if (value.isNotBlank()) bridge.submit(value)
                            }
                        }
                    }
                    installCaptchaBridge(bridge, url)
                    webView = this
                }
            },
            update = { view ->
                if (view.url != url) view.loadUrl(url)
            }
        )
        Button(
            onClick = {
                if (!finished) {
                    finished = true
                    onCancel()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Abbrechen")
        }
    }
}

private data class WebState(
    val token: String? = null,
    val streamUrl: String? = null
)

private suspend fun readState(view: WebView, url: String): WebState? =
    suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(PROBE_SCRIPT) { raw ->
            val text = unquote(raw)
            continuation.resume(parseState(text))
        }
    }

private fun parseState(text: String): WebState? {
    if (text.isBlank()) return null
    return runCatching {
        val json = org.json.JSONObject(text)
        WebState(
            token = json.optString("token").takeIf { it.isNotBlank() },
            streamUrl = json.optString("stream").takeIf { it.startsWith("https://") }
        )
    }.getOrNull()
}

private fun unquote(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        (JSONTokener(raw).nextValue() as? String).orEmpty()
    }.getOrDefault("")
}

/**
 * Installs the one and only JS bridge. Lint cannot see the
 * `@JavascriptInterface` annotation through the `apply` block, so the call
 * lives in a named function.
 */
@SuppressLint("JavascriptInterface")
private fun WebView.installCaptchaBridge(bridge: Frame61Bridge, url: String) {
    addJavascriptInterface(bridge, "Frame61Captcha")
    loadUrl(url)
}

/** The single allowed JS bridge: reports a token, nothing else. */
class Frame61Bridge(
    private val url: String,
    private val onToken: (String) -> Unit = {}
) {
    @JavascriptInterface
    fun submit(token: String?) {
        val value = token?.trim().orEmpty()
        if (value.isNotEmpty()) onToken(value)
    }

    fun dispose() { /* no resources to release yet */ }
}

private const val PROBE_SCRIPT = """
(function(){
  var r = {};
  try { r.token = (window.__frame61Captcha || '') + ''; } catch (e) {}
  try {
    var v = document.querySelector('video source, video');
    if (v && v.src) r.stream = v.src;
  } catch (e) {}
  try {
    var s = window.sources;
    if (s && s[0] && s[0].file) r.stream = s[0].file;
  } catch (e) {}
  try {
    if (window.jwplayer) {
      var p = window.jwplayer().getPlaylistItem();
      if (p && p.file) r.stream = p.file;
    }
  } catch (e) {}
  try {
    var entries = performance.getEntriesByType('resource');
    for (var i = entries.length - 1; i >= 0; i--) {
      if (/\.(m3u8|mp4)(\?|$)/.test(entries[i].name)) {
        r.stream = entries[i].name;
        break;
      }
    }
  } catch (e) {}
  r.href = location.href;
  return JSON.stringify(r);
})()
"""
