package de.hendler.frame61.playback

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.hendler.frame61.data.ProfileDataStore
import de.hendler.frame61.net.PlaybackDiagnostics
import de.hendler.frame61.resolver.HosterOutcome
import de.hendler.frame61.resolver.HosterPack
import de.hendler.frame61.resolver.HosterPackValidator
import de.hendler.frame61.resolver.HosterResolver
import de.hendler.frame61.resolver.MirrorCandidate
import de.hendler.frame61.resolver.OkHttpBodyFetcher
import de.hendler.frame61.scraper.engine.PackInterpreter
import de.hendler.frame61.scraper.engine.PackLoader
import de.hendler.frame61.scraper.engine.SourcePack
import de.hendler.frame61.scraper.engine.StreamResult
import de.hendler.frame61.ui.player.CatalogPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatic source resolution. The user never picks a source.
 *
 * 1. Every active source pack is searched in parallel (4 s per source).
 * 2. Stream URLs run through the hoster chain (4 s per candidate).
 * 3. The first usable candidate wins; failures are logged, never shown.
 * 4. The resolved list is cached for 15 minutes per (title, episode, active set).
 */
@Singleton
class AutoPlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packLoader: PackLoader,
    private val interpreter: PackInterpreter,
    private val ardResolver: ArdPlaybackResolver,
    private val captchaBroker: CaptchaBroker,
    private val jsEvaluator: WebViewJsEvaluator,
    private val prefs: ProfileDataStore
) {

    private data class CacheEntry(val at: Long, val mirrors: List<MirrorCandidate>)
    private data class ResolvedStream(
        val url: String,
        val headers: Map<String, String>,
        val cookies: String?
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private val hosterPacks: List<HosterPack> by lazy {
        val assets = context.assets
        (assets.list("hosters") ?: emptyArray())
            .filter { it.endsWith(".json") && !it.startsWith("._") }
            .mapNotNull { file ->
                runCatching {
                    assets.open("hosters/$file").bufferedReader().use { reader ->
                        HosterPackValidator.parse(reader.readText())
                    }
                }.getOrNull()
            }
    }

    private val hosterResolver: HosterResolver by lazy {
        HosterResolver(hosterPacks, OkHttpBodyFetcher(), jsEvaluator)
    }

    suspend fun resolve(
        title: String,
        episodeNumber: Int = 0,
        sessionId: Long = PlaybackDiagnostics.currentSessionId,
        fresh: Boolean = false
    ): List<MirrorCandidate> =
        withContext(Dispatchers.IO) {
            val disabled = runCatching { prefs.disabledSources.first() }.getOrDefault(emptySet())
            val key = "$title|$episodeNumber|${disabled.hashCode()}"
            val now = System.currentTimeMillis()
            if (!fresh) cache[key]?.takeIf { now - it.at < CACHE_MS }?.let {
                Log.i(TAG, "cache hit '$title' (${it.mirrors.size} mirrors)")
                trace(sessionId, "cache", "Treffer mit ${it.mirrors.size} Kandidaten")
                return@withContext it.mirrors
            }

            val allPacks = packLoader.loadAllPacks().filter { it.id !in disabled }
            val packs = allPacks.filter { it.captcha == null }
            Log.i(
                TAG,
                "resolve '$title' ep=$episodeNumber active=${allPacks.size} " +
                    "immediate=${packs.size} deferredCaptcha=${allPacks.size - packs.size} " +
                    "disabled=${disabled.sorted()}"
            )

            val candidates = coroutineScope {
                packs.map { pack ->
                    async { resolveWithTimeout(pack, title, episodeNumber, sessionId) }
                }.awaitAll().flatten()
            }

            val captchaCandidates = if (candidates.isEmpty()) {
                allPacks.filter { it.captcha != null }.flatMap { pack ->
                    resolveWithTimeout(pack, title, episodeNumber, sessionId)
                }
            } else {
                emptyList()
            }
            val ordered = order(candidates + captchaCandidates)
            if (ordered.isEmpty()) {
                Log.w(TAG, "all sources exhausted for '$title' ep=$episodeNumber")
            }
            cache[key] = CacheEntry(now, ordered)
            ordered
        }

    private suspend fun resolveWithTimeout(
        pack: SourcePack,
        title: String,
        episodeNumber: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        val started = System.currentTimeMillis()
        val result = runCatching {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                resolvePack(pack, title, episodeNumber, sessionId)
            } ?: run {
                trace(sessionId, pack.id, "Zeitüberschreitung nach $SOURCE_TIMEOUT_MS ms")
                emptyList()
            }
        }.getOrElse { error ->
            trace(sessionId, pack.id, "Resolverfehler: ${error.javaClass.simpleName}")
            emptyList()
        }
        trace(sessionId, pack.id, "${result.size} Kandidaten")
        Log.i(
            TAG,
            "${pack.id}: ${result.size} candidate(s) in " +
                "${System.currentTimeMillis() - started} ms" +
                if (result.isEmpty()) " (no usable stream)" else ""
        )
        return result
    }

    private suspend fun resolvePack(
        pack: SourcePack,
        title: String,
        episodeNumber: Int,
        sessionId: Long
    ): List<MirrorCandidate> {
        if (pack.id == ArdPlaybackResolver.PACK_ID) {
            return ardResolver.resolve(title, episodeNumber)
        }
        var cookies: String? = null
        var hits = runCatching { interpreter.search(pack, title) }.getOrDefault(emptyList())
        val sourceCaptcha = pack.captcha
        if (hits.isEmpty() && sourceCaptcha != null) {
            Log.i(TAG, "${pack.id}: source captcha $sourceCaptcha -> WebView")
            val solved = captchaBroker.await(pack.baseUrl, sourceCaptcha, pack.id)
            cookies = solved?.cookies
            if (!cookies.isNullOrBlank()) {
                hits = runCatching { interpreter.search(pack, title, cookies = cookies) }
                    .getOrDefault(emptyList())
            }
        }
        val hit = hits.firstOrNull { it.title.contains(title, ignoreCase = true) }
            ?: return emptyList()
        val streams = interpreter.streams(pack, hit.id, episodeNumber.toString(), cookies)
        return streams.mapNotNull { stream ->
            val resolved = resolveHoster(pack, stream, sessionId)
            resolved?.let {
                MirrorCandidate(
                    sourceId = pack.id,
                    titleId = CatalogPlayback.stableId(hit.id),
                    episodeId = if (episodeNumber > 0) episodeNumber.toLong() else null,
                    url = it.url,
                    lang = stream.lang?.take(2) ?: "de",
                    quality = stream.quality ?: "auto",
                    hosterId = stream.hosterId,
                    healthScore = 1.0,
                    legalFree = pack.legalFree,
                    requestHeaders = it.headers,
                    cookies = it.cookies
                )
            }
        }
    }

    private suspend fun resolveHoster(
        pack: SourcePack,
        stream: StreamResult,
        sessionId: Long
    ): ResolvedStream? {
        trace(sessionId, pack.id, "Hosterauflösung ${stream.hosterId ?: "unknown"}", stream.url)
        if (!stream.url.startsWith("https://")) {
            Log.w(TAG, "${pack.id}: non-https stream rejected")
            trace(sessionId, pack.id, "Nicht-HTTPS-Ziel verworfen", stream.url)
            return null
        }
        val hosterCaptcha = pack.captcha
        if (hosterCaptcha != null) {
            Log.i(TAG, "${pack.id}: captcha $hosterCaptcha -> WebView")
            val result = captchaBroker.await(stream.url, hosterCaptcha, pack.id)
            result?.streamUrl?.takeIf { it.startsWith("https://") }?.let {
                return ResolvedStream(
                    it,
                    mapOf(
                        "User-Agent" to HosterResolver.DEFAULT_USER_AGENT,
                        "Referer" to stream.url
                    ),
                    result.cookies
                )
            }
            val cookies = result?.cookies
            if (cookies.isNullOrBlank()) {
                Log.w(TAG, "${pack.id}: captcha ohne Cookie/Stream beendet")
                return null
            }
            val second = runCatching {
                withTimeoutOrNull(HOSTER_TIMEOUT_MS * 5) {
                    hosterResolver.resolve(stream.url, cookies)
                }
            }.getOrNull()
            return when (second) {
                is HosterOutcome.Resolved -> ResolvedStream(
                    second.url,
                    second.requestHeaders,
                    second.cookies
                )
                else -> {
                    Log.w(TAG, "${pack.id}: Kette nach Captcha ohne Ergebnis")
                    null
                }
            }
        }
        val outcome = runCatching {
            withTimeoutOrNull(HOSTER_TIMEOUT_MS) { hosterResolver.resolve(stream.url) }
        }.getOrNull()
        return when (outcome) {
            is HosterOutcome.Resolved -> ResolvedStream(
                outcome.url,
                outcome.requestHeaders,
                outcome.cookies
            ).also {
                trace(sessionId, pack.id, "Hoster aufgelöst", it.url)
            }
            is HosterOutcome.Unknown -> ResolvedStream(
                outcome.url,
                outcome.requestHeaders,
                outcome.cookies
            ).also {
                trace(sessionId, pack.id, "Unbekannter Hoster als Kandidat durchgereicht", it.url)
            }
            is HosterOutcome.CaptchaRequired -> {
                trace(sessionId, pack.id, "Captcha erforderlich", stream.url)
                null
            }
            is HosterOutcome.Failed -> {
                Log.w(TAG, "${pack.id}: hoster failed (${outcome.reason})")
                trace(sessionId, pack.id, "Hoster fehlgeschlagen: ${outcome.reason}", outcome.url)
                null
            }
            null -> {
                Log.w(TAG, "${pack.id}: hoster timeout")
                trace(sessionId, pack.id, "Hoster-Zeitüberschreitung", stream.url)
                null
            }
        }
    }

    private fun trace(sessionId: Long, sourceId: String, message: String, url: String? = null) {
        PlaybackDiagnostics.record(
            category = "RESOLVER",
            message = message,
            sessionId = sessionId,
            url = url,
            details = mapOf("sourceId" to sourceId)
        )
    }

    private fun order(candidates: List<MirrorCandidate>): List<MirrorCandidate> =
        candidates.distinctBy { it.url }.sortedWith(
            compareByDescending<MirrorCandidate> { it.legalFree }
                .thenByDescending { it.lang.startsWith("de", ignoreCase = true) }
                .thenByDescending {
                    it.quality.filter(Char::isDigit).toIntOrNull() ?: 0
                }
                .thenByDescending { it.healthScore }
        )

    companion object {
        private const val TAG = "Frame61Resolve"
        private const val SOURCE_TIMEOUT_MS = 4_000L
        private const val HOSTER_TIMEOUT_MS = 4_000L
        private const val CACHE_MS = 15L * 60L * 1000L
    }
}
