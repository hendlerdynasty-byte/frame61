package de.hendler.frame61.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class CaptchaRequest(
    val url: String,
    val kind: String,
    val hoster: String?
)

data class CaptchaResult(
    val token: String? = null,
    val cookies: String? = null,
    val streamUrl: String? = null
)

/**
 * Bridge between the non-UI resolver and the visible WebView.
 *
 * The resolver suspends here while the UI shows [de.hendler.frame61.ui.CaptchaWebViewFrame].
 * Exactly one request is active at a time; parallel hoster chains queue on the
 * mutex. Timeout is 20 s, then the resolver gets null and tries the next mirror.
 */
@Singleton
class CaptchaBroker @Inject constructor() {

    private val mutex = Mutex()
    private val _request = MutableStateFlow<CaptchaRequest?>(null)
    val request: StateFlow<CaptchaRequest?> = _request.asStateFlow()

    @Volatile
    private var pending: CompletableDeferred<CaptchaResult?>? = null

    suspend fun await(
        url: String,
        kind: String,
        hoster: String? = null,
        timeoutMs: Long = 20_000L
    ): CaptchaResult? = mutex.withLock {
        val deferred = CompletableDeferred<CaptchaResult?>()
        pending = deferred
        _request.value = CaptchaRequest(url, kind, hoster)
        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
        _request.value = null
        pending = null
        result
    }

    fun submit(result: CaptchaResult) {
        pending?.complete(result)
    }

    fun cancel() {
        pending?.complete(null)
    }
}
