package de.hendler.frame61.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Visible WebView frame for hosters that require a captcha (`captcha: "webview"`).
 *
 * This is the *only* place in the app with an `addJavascriptInterface` call —
 * the bridge named in the hardening rules. It exposes exactly one method, which
 * reports the token the hoster page writes into `window.__frame61Captcha`.
 *
 * A captcha is never a hard failure: the resolver returns `CaptchaRequired`, the
 * UI shows this frame, and once the token arrives playback continues with it.
 */
@Composable
fun CaptchaWebViewFrame(
    url: String,
    modifier: Modifier = Modifier,
    onToken: (token: String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val bridge = remember(url, onToken) { Frame61Bridge(url, onToken) }

    DisposableEffect(bridge) {
        onDispose { bridge.dispose() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = Frame61WebViewClient()
                installCaptchaBridge(bridge, url)
            }
        },
        update = { webView ->
            if (webView.url != url) webView.loadUrl(url)
        }
    )
}

/**
 * Installs the one and only JS bridge. Lint cannot see the `@JavascriptInterface`
 * annotation through the `apply` block, so the call lives in a named function
 * where the annotation stays verifiable.
 */
@SuppressLint("JavascriptInterface")
private fun WebView.installCaptchaBridge(bridge: Frame61Bridge, url: String) {
    addJavascriptInterface(bridge, "Frame61Captcha")
    loadUrl(url)
}

/** The single allowed JS bridge: reports the captcha token, nothing else. */
class Frame61Bridge(
    private val url: String,
    private val onToken: (String) -> Unit
) {
    @JavascriptInterface
    fun submit(token: String?) {
        val value = token?.trim().orEmpty()
        if (value.isNotEmpty()) onToken(value)
    }

    fun dispose() { /* no resources to release yet */ }
}

private class Frame61WebViewClient : WebViewClient() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
        super.onPageFinished(view, finishedUrl)
        // Hosters publish the token at different places; ask the page for it.
        view?.evaluateJavascript(
            "(function(){try{return (window.__frame61Captcha||'') + ''}catch(e){return ''}})()",
            null
        )
    }
}