package de.hendler.frame61.resolver

import de.hendler.frame61.net.HttpClientFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Reads a page body. Interface so the resolver is testable without network. */
interface BodyFetcher {
    suspend fun get(url: String, headers: Map<String, String>): String
    suspend fun post(url: String, body: String, headers: Map<String, String>): String

    /**
     * Returns the final URL after following redirects, or null when the answer
     * is no redirect. Default keeps test doubles simple.
     */
    suspend fun resolveRedirect(url: String, headers: Map<String, String>): String? = null
}

sealed interface HosterOutcome {
    data class Resolved(
        val url: String,
        val usedSteps: Int,
        val requestHeaders: Map<String, String> = emptyMap(),
        val cookies: String? = null
    ) : HosterOutcome
    data class CaptchaRequired(val url: String, val kind: String) : HosterOutcome
    data class Unknown(
        val url: String,
        val requestHeaders: Map<String, String> = emptyMap(),
        val cookies: String? = null
    ) : HosterOutcome
    data class Failed(val url: String, val reason: String, val usedSteps: Int) : HosterOutcome
}

/**
 * Headless challenge/captcha solver.
 *
 * Returns the cookies that unlock [url] (DDoS-Guard, Cloudflare, hoster
 * captcha) or null. Implemented on the device with a hidden WebView, so the
 * user never sees a captcha.
 */
fun interface CaptchaSolver {
    suspend fun solve(url: String, timeoutMs: Long): String?
}

/**
 * Budget für die stille WebView-Interception **je Titel**.
 *
 * Die versteckte WebView ist app-weit serialisiert ([WebViewGate]). Ohne
 * Budget stellen die parallelen Hoster-Ketten eines Titels (bis zu neun
 * Streams) je einen eigenen WebView-Lauf in die Warteschlange — der Titel
 * wartet dann auf einen Hoster, den das Quellbudget längst abgeschnitten
 * hat. Messung 23.11. (A52, `be45079`): 7 von 14 protokollierten Versuchen
 * kamen **nie** in die WebView (0 Anfragen), der Rest lief in den 20-s-Deckel.
 *
 * Deshalb: wenige Versuche je Titel, mit Frist. Danach läuft der Rest über
 * den günstigen Weg (Pack-Schritte ohne WebView).
 */
class WebViewBudget private constructor(
    private val attempts: AtomicInteger,
    val deadlineMs: Long
) {
    /**
     * Nimmt einen Versuch heraus. `false` heißt: kein Versuch mehr frei oder
     * die Frist abgelaufen — der Aufrufer muss den günstigen Weg nehmen.
     */
    fun tryTake(): Boolean =
        attempts.getAndDecrement() > 0 && System.currentTimeMillis() < deadlineMs

    companion object {
        /** Kein WebView-Versuch — für Streams, die ohnehin ohne WebView gehen. */
        fun none(): WebViewBudget = WebViewBudget(AtomicInteger(0), 0L)

        /** Unbegrenzt — für Aufrufer außerhalb eines Titels (Tests, Diagnose). */
        fun unlimited(): WebViewBudget = WebViewBudget(AtomicInteger(Int.MAX_VALUE), Long.MAX_VALUE)

        fun of(attempts: Int, deadlineMs: Long): WebViewBudget =
            WebViewBudget(AtomicInteger(attempts), deadlineMs)
    }
}

/**
 * Turns a hoster page URL into a playable URL by running the pack's steps.
 *
 * Never throws: every failure is a value ([HosterOutcome.Failed]), and a pack
 * with `captcha` set yields [HosterOutcome.CaptchaRequired] so the UI can show a
 * WebView frame instead of failing the mirror.
 */
class HosterResolver(
    private val packs: List<HosterPack>,
    private val fetcher: BodyFetcher,
    private val jsEvaluator: JsEvaluator? = null,
    private val powSolver: PowSolver = PowSolver(),
    private val captchaSolver: CaptchaSolver? = null,
    private val mediaInterceptor: MediaInterceptor? = null,
    private val devideosrc: DeVideoSrcResolver? = null
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /** Longest matching domain suffix wins, so `a.b.EXAMPLE.com` hits `b.example.com`. */
    fun findPack(url: String): HosterPack? {
        val host = hostOf(url) ?: return null
        return packs
            .filter { pack -> pack.domains.any { host == it || host.endsWith(".$it") } }
            .maxByOrNull { pack -> pack.domains.filter { host == it || host.endsWith(".$it") }.maxOf { it.length } }
    }

    suspend fun resolve(
        url: String,
        cookies: String? = null,
        budget: WebViewBudget? = null,
        episodeNumber: Int = 0
    ): HosterOutcome =
        resolve(url, cookies, depth = 0, budget = budget ?: WebViewBudget.unlimited(), episodeNumber)

    private suspend fun resolve(
        url: String,
        cookies: String?,
        depth: Int,
        budget: WebViewBudget,
        episodeNumber: Int = 0
    ): HosterOutcome {
        var effectiveCookies = cookies
        // DEVIDEOSRC liefert über eine dokumentierte API eine **Quellenliste**
        // (Dropload/SuperVideo/Doodstream/Mixdrop) statt einer Player-Seite.
        // Ohne diese Stufe blieb die HTML-Seite als Kandidat stehen — der
        // Player meldete 3003. Gemessen 23.11.2026 auf dem A52: devideosrc.co
        // war der häufigste Zielhost der CONTAINER_UNSUPPORTED-Fehlschläge.
        val devideosrcResolver = devideosrc
        if (devideosrcResolver != null && DeVideoSrcResolver.matches(url)) {
            val providers = runCatching {
                devideosrcResolver.expand(url, episodeNumber)
            }.getOrDefault(emptyList())
            if (providers.isNotEmpty()) {
                for (provider in providers) {
                    if (depth >= MAX_REDIRECT_DEPTH) break
                    when (val outcome = resolve(provider.url, effectiveCookies, depth + 1, budget, episodeNumber)) {
                        is HosterOutcome.Resolved -> return outcome
                        is HosterOutcome.CaptchaRequired -> return outcome
                        else -> Unit // nächster Anbieter
                    }
                }
                return HosterOutcome.Failed(
                    url,
                    "devideosrc: keiner der ${providers.size} Anbieter spielbar",
                    1
                )
            }
            // Keine Liste erhalten: der bisherige Pack-Pfad bleibt in Kraft.
        }
        val pack = findPack(url)
        if (pack == null) {
            val headers = linkedMapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to url
            )
            if (!effectiveCookies.isNullOrBlank()) headers["Cookie"] = effectiveCookies
            // Kein Hoster-Pack: Ketten wie aniworld.to/redirect/<id> zeigen auf
            // den echten Hoster. Ohne das bleibt ein HTML-Ziel stehen und der
            // Player scheitert mit 3003. Direkte Medien-URLs bleiben unangetastet.
            if (depth < MAX_REDIRECT_DEPTH && !looksLikeMedia(url)) {
                val followed = runCatching { fetcher.resolveRedirect(url, headers) }.getOrNull()
                if (!followed.isNullOrBlank() && followed != url) {
                    return resolve(followed, effectiveCookies, depth + 1, budget)
                }
            }
            // Letzte Stufe: eine Embed-Seite ohne eigenes Pack. Zuerst die
            // versteckte WebView: sie baut die Seite (inkl. Cloudflare) und
            // zeichnet den **echten** Medienabruf samt Kopfzeilen auf. Erst
            // danach der DOM-Abgriff als Rückfallebene.
            //
            // Auch in einer Kette (depth > 0): devideosrc liefert Provider-
            // Embeds, die erst hier spielbar werden. Das WebView-Budget
            // begrenzt die Versuche je Titel — eine Schleife ist damit
            // ausgeschlossen.
            if (depth < MAX_REDIRECT_DEPTH && looksLikeEmbed(url)) {
                val interceptor = mediaInterceptor
                if (interceptor != null && budget.tryTake()) {
                    val intercepted = runCatching {
                        interceptor.intercept(url, MEDIA_INTERCEPT_MS)
                    }.getOrNull()
                    if (intercepted != null && intercepted.url.startsWith("https://")) {
                        val merged = linkedMapOf(
                            "User-Agent" to DEFAULT_USER_AGENT,
                            "Referer" to url
                        )
                        intercepted.headers.forEach { (key, value) -> merged[key] = value }
                        return HosterOutcome.Resolved(
                            intercepted.url,
                            1,
                            merged,
                            intercepted.cookies ?: effectiveCookies
                        )
                    }
                }
                val evaluator = jsEvaluator
                if (evaluator != null) {
                    val extracted = runCatching {
                        evaluator.evaluate(url, GENERIC_EMBED_SCRIPT, GENERIC_EMBED_MS)
                    }.getOrNull()
                    if (!extracted.isNullOrBlank() && extracted.startsWith("https://")) {
                        return HosterOutcome.Resolved(extracted, 1, headers, effectiveCookies)
                    }
                }
            }
            return HosterOutcome.Unknown(url, headers, effectiveCookies)
        }
        if (!pack.captcha.isNullOrBlank() && effectiveCookies.isNullOrBlank()) {
            // Erste Wahl: die Embed-Seite still in einer WebView laden und den
            // Medienabruf abfangen. Der Browser der Seite löst seine Challenge
            // selbst; sichtbar wird nichts. Das Budget begrenzt die Versuche:
            // die WebView ist app-weit serialisiert, mehr als ein Versuch je
            // Titel erreicht die Seite ohnehin nicht mehr.
            val intercepted = if (budget.tryTake()) {
                runCatching {
                    mediaInterceptor?.intercept(url, MEDIA_INTERCEPT_MS)
                }.getOrNull()
            } else {
                null
            }
            if (intercepted != null && intercepted.url.startsWith("https://")) {
                // Die Kopfzeilen der aufgezeichneten Anfrage gehen mit zum
                // Player (Referer/Origin/Cookie) — ohne sie verweigern die
                // Hoster den Abruf nach der Challenge-Seite.
                val headers = linkedMapOf(
                    "User-Agent" to DEFAULT_USER_AGENT,
                    "Referer" to url
                )
                intercepted.headers.forEach { (key, value) -> headers[key] = value }
                return HosterOutcome.Resolved(
                    intercepted.url,
                    1,
                    headers,
                    intercepted.cookies
                )
            }
            // Zweite Wahl: Cookie-basiertes Lösen über den Captcha-Broker.
            val solved = runCatching { captchaSolver?.solve(url, CAPTCHA_SOLVE_MS) }.getOrNull()
            if (solved.isNullOrBlank()) {
                return HosterOutcome.CaptchaRequired(url, pack.captcha)
            }
            effectiveCookies = solved
        }

        val defaultHeaders = linkedMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to url
        )
        if (!effectiveCookies.isNullOrBlank()) defaultHeaders["Cookie"] = effectiveCookies

        val vars = linkedMapOf(
            "url" to url,
            "host" to (hostOf(url).orEmpty()),
            "path" to pathOf(url)
        )
        var used = 0
        val requestHeaders = defaultHeaders
        for (step in pack.steps) {
            used++
            when (step.action) {
                "sleep" -> { /* no delay in the pure step machine */ }
                "get" -> {
                    val target = expand(step.url ?: return HosterOutcome.Failed(url, "get ohne url", used), url, vars)
                    val headers = step.headers.mapValues { (_, value) -> expand(value, url, vars) }
                    requestHeaders += headers
                    vars[step.saveAs ?: "body"] = runCatching {
                        fetcher.get(target, requestHeaders + headers)
                    }.getOrElse { return HosterOutcome.Failed(url, "get fehlgeschlagen: ${it.message}", used) }
                }
                "post" -> {
                    val target = expand(step.url ?: return HosterOutcome.Failed(url, "post ohne url", used), url, vars)
                    val body = expand(step.body.orEmpty(), url, vars)
                    val headers = step.headers.mapValues { (_, value) -> expand(value, url, vars) }
                    requestHeaders += headers
                    vars[step.saveAs ?: "body"] = runCatching {
                        fetcher.post(target, body, requestHeaders + headers)
                    }.getOrElse { return HosterOutcome.Failed(url, "post fehlgeschlagen: ${it.message}", used) }
                }
                "js" -> {
                    val script = step.script
                        ?: return HosterOutcome.Failed(url, "js ohne script", used)
                    val evaluator = jsEvaluator
                        ?: return HosterOutcome.Failed(url, "js: kein WebView verfügbar", used)
                    val target = expand(step.url ?: url, url, vars)
                    val value = runCatching { evaluator.evaluate(target, script) }.getOrNull()
                    if (value.isNullOrBlank()) {
                        return HosterOutcome.Failed(url, "js: kein Rückgabewert", used)
                    }
                    vars[step.saveAs ?: "js"] = value
                }
                "pow" -> {
                    val inputKey = step.input.orEmpty()
                    val input = vars[inputKey]
                        ?: return HosterOutcome.Failed(url, "pow: input '$inputKey' unbekannt", used)
                    val difficulty = step.difficultyInput
                        ?.let { vars[it]?.toIntOrNull() }
                        ?: step.difficulty
                    val solution = powSolver.solve(step, input, difficulty)
                        ?: return HosterOutcome.Failed(
                            url,
                            "pow: keine Lösung für ${step.algorithm}",
                            used
                        )
                    vars[step.saveAs ?: "solution"] = solution
                }
                "regex" -> {
                    val input = vars[step.input.orEmpty()]
                        ?: return HosterOutcome.Failed(url, "regex: input '${step.input}' unbekannt", used)
                    val match = runCatching { Regex(step.pattern.orEmpty()).find(input) }.getOrNull()
                        ?: return HosterOutcome.Failed(url, "regex ohne Treffer", used)
                    val value = (match.groupValues.getOrNull(step.group) ?: match.value)
                    if (value.isBlank()) return HosterOutcome.Failed(url, "regex traf leeren Wert", used)
                    vars[step.saveAs ?: "match"] = value
                }
                "json" -> {
                    val input = vars[step.input.orEmpty()]
                        ?: return HosterOutcome.Failed(url, "json: input '${step.input}' unbekannt", used)
                    val value = readPath(input, step.path.orEmpty())
                        ?: return HosterOutcome.Failed(url, "json: Pfad '${step.path}' ohne Wert", used)
                    vars[step.saveAs ?: "json"] = value
                }
                "copy" -> {
                    // Direct stream: no hoster page, just pass the URL through.
                    val value = expand(step.url.orEmpty(), url, vars)
                    if (value.isBlank()) return HosterOutcome.Failed(url, "copy ohne Wert", used)
                    vars[step.saveAs ?: "copy"] = value
                }
            }
        }

        // Fail closed: a declared result must actually be produced. No silent
        // fallback to "the last variable", which would hide pack mistakes.
        val finalValue = if (pack.result.isNullOrBlank()) {
            vars.values.lastOrNull()
        } else {
            vars[pack.result]?.takeIf { it.isNotBlank() }
                ?: return HosterOutcome.Failed(url, "result '${pack.result}' nicht erzeugt", used)
        }

        if (finalValue.isNullOrBlank()) {
            return HosterOutcome.Failed(url, "kein Ergebnis", used)
        }

        // Ketten ueber mehrere Hoster-Packs: eine Zwischenadresse (z. B. der
        // serienstream.to-Redirect-Gate) zeigt auf den echten Hoster. Ohne
        // diese Stufe bleibt die HTML-Seite stehen und der Player meldet 3003.
        if (depth < MAX_REDIRECT_DEPTH && finalValue.startsWith("https://")) {
            val nextPack = findPack(finalValue)
            if (nextPack != null && nextPack.id != pack.id) {
                val cookieHeader = requestHeaders["Cookie"]
                return when (val inner = resolve(finalValue, cookieHeader, depth + 1, budget)) {
                    is HosterOutcome.Resolved -> inner
                    is HosterOutcome.CaptchaRequired ->
                        HosterOutcome.CaptchaRequired(finalValue, inner.kind)
                    is HosterOutcome.Failed ->
                        HosterOutcome.Failed(finalValue, inner.reason, used)
                    is HosterOutcome.Unknown ->
                        HosterOutcome.Resolved(
                            finalValue,
                            used,
                            requestHeaders.toMap(),
                            effectiveCookies
                        )
                }
            }
        }

        return HosterOutcome.Resolved(finalValue, used, requestHeaders.toMap(), effectiveCookies)
    }

    /** `{host}`, `{path}` and stored variables are substituted. */
    private fun expand(template: String, sourceUrl: String, vars: Map<String, String>): String {
        var out = template
        hostOf(sourceUrl)?.let { out = out.replace("{host}", it) }
        pathOf(sourceUrl)?.let { out = out.replace("{path}", it) }
        out = out.replace("{{url}}", sourceUrl)
        out = out.replace("{url}", sourceUrl)
        for ((key, value) in vars) out = out.replace("{$key}", value)
        return out
    }

    private fun readPath(body: String, path: String): String? {
        val root: JsonElement = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        var current: JsonElement? = root
        for (segment in path.split('.')) {
            if (segment.isBlank()) continue
            current = when (current) {
                is JsonObject -> current[segment]
                is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: -1)
                else -> null
            } ?: return null
        }
        return when (current) {
            is JsonPrimitive -> current.contentOrNull
            else -> null
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = HttpClientFactory.DEFAULT_USER_AGENT

        /** Redirect hops before a chain URL is treated as unknown. */
        const val MAX_REDIRECT_DEPTH = 3

        /** Budget for a headless captcha solve before the UI fallback is used. */
        const val CAPTCHA_SOLVE_MS = 15_000L

        /** Budget für den stillen WebView-Interceptor (Kette inkl. Redirects). */
        const val MEDIA_INTERCEPT_MS = 20_000L

        /** Generische Embed-Aufloesung: kurz halten, sie ist die letzte Stufe. */
        const val GENERIC_EMBED_MS = 5_000L

        /**
         * DEVIDEOSRC-Domains (dokumentierte Embed-API, siehe
         * [DeVideoSrcResolver]). In `assets/hosters/devideosrc.json`
         * gespiegelt; dort steht der aeltere Pack-Pfad als Rueckfallebene.
         */
        val DEVIDEOSRC_DOMAINS = listOf("devideosrc.co", "devideosrc.com", "devideosrc.net")

        private val EMBED_PATH = Regex("""(?i)/(?:e|embed|video|stream|play)/[^/?#]+""")

        /** Nur Embed-Formen aufloesen, keine beliebigen fremden Hosts. */
        fun looksLikeEmbed(url: String): Boolean = EMBED_PATH.containsMatchIn(url)

        /**
         * Liest die erste echte Medien-URL aus einer Embed-Seite — unabhängig
         * vom konkreten Hoster (video/source/window.sources/getActiveLink).
         */
        const val GENERIC_EMBED_SCRIPT =
            "(function(){try{var v=document.querySelector('video');" +
                "if(v&&v.src&&v.src.indexOf('http')===0)return v.src;" +
                "var s=document.querySelectorAll('source');" +
                "for(var i=0;i<s.length;i++){if(s[i].src&&s[i].src.indexOf('http')===0)return s[i].src;}" +
                "if(window.sources&&sources.length){var f=sources[0].file||sources[0].src;" +
                "if(f&&f.indexOf('http')===0)return f;}" +
                "if(typeof getActiveLink==='function'){var l=getActiveLink();" +
                "if(l&&l.indexOf('http')===0)return l;}" +
                "var ifr=document.querySelector('iframe');" +
                "if(ifr&&ifr.src&&ifr.src.indexOf('http')===0)return ifr.src;}"+"catch(e){}return '';})()"

        private val MEDIA_PATH = Regex("""(?i)\.(?:mp4|m3u8|mkv|avi|ts|webm|mov|m4v|mpd)(?:[?#]|$)""")

        /** Direct media targets must not cost a redirect round-trip. */
        fun looksLikeMedia(url: String): Boolean = MEDIA_PATH.containsMatchIn(url)

        fun hostOf(url: String): String? =
            Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)""").find(url)
                ?.groupValues?.get(1)
                ?.substringBefore(':')
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }

        fun pathOf(url: String): String =
            Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]+([^?#]*)""").find(url)
                ?.groupValues?.get(1)
                ?: ""
    }
}