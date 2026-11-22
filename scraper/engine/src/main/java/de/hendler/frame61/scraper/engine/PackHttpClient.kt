package de.hendler.frame61.scraper.engine

import de.hendler.frame61.net.HttpClientFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Full answer of a pack rule, including the status code.
 *
 * The status is needed for the evidence: a source that answers with 401 or 404
 * is blocked for a documented reason, not silently "without results".
 */
data class PackResponse(
    val status: Int,
    val body: String,
    val contentType: String?,
    val url: String
)

/**
 * Minimal HTTP front-end for [PackRule]s. Bodies are returned as text; parsing
 * is [PackInterpreter]'s job, so this class stays trivially replaceable in
 * tests.
 */
class PackHttpClient(
    private val client: OkHttpClient = HttpClientFactory.create()
) {

    @Throws(IOException::class)
    fun get(
        pack: SourcePack,
        rule: PackRule,
        params: Map<String, String>,
        cookies: String? = null
    ): String {
        val response = fetch(pack, rule, params, cookies)
        if (response.status !in 200..299) {
            throw IOException("HTTP ${response.status} for ${response.url}")
        }
        return response.body
    }

    /**
     * Like [get], but a 4xx/5xx answer comes back as a value with its status
     * instead of an exception. Only transport failures throw.
     */
    @Throws(IOException::class)
    fun fetch(
        pack: SourcePack,
        rule: PackRule,
        params: Map<String, String>,
        cookies: String? = null
    ): PackResponse {
        val url = buildUrl(pack, rule, params)
        val builder = Request.Builder().url(url)
            .header("User-Agent", HttpClientFactory.DEFAULT_USER_AGENT)
            .header("Referer", pack.baseUrl)
        rule.headers.forEach { (key, value) -> builder.header(key, value) }
        if (!cookies.isNullOrBlank()) builder.header("Cookie", cookies)

        if (rule.method.equals("POST", ignoreCase = true)) {
            val body = render(rule.body.orEmpty(), params).toRequestBody(null)
            builder.post(body)
        } else {
            builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            return PackResponse(
                status = response.code,
                body = response.body?.string().orEmpty(),
                contentType = response.header("Content-Type"),
                url = url
            )
        }
    }

    companion object {
        /** Replaces `{{name}}` placeholders; unknown names stay untouched. */
        fun render(template: String, params: Map<String, String>): String {
            var out = template
            for ((key, value) in params) {
                out = out.replace("{{$key}}", value)
                out = out.replace("{$key}", value)
            }
            return out
        }

        fun buildUrl(pack: SourcePack, rule: PackRule, params: Map<String, String>): String {
            val path = (rule.endpoint ?: rule.path).orEmpty()
            val rendered = renderPath(path, params)
            val base = pack.baseUrl.trimEnd('/')
            val url = when {
                rendered.startsWith("http") -> rendered
                rendered.startsWith("/") -> base + rendered
                else -> "$base/$rendered"
            }

            // `{query}`-style placeholders that survived templating become query params
            val http = url.toHttpUrlOrNull() ?: return url
            val queryName = rule.queryParam
            if (queryName != null && params.isNotEmpty()) {
                // A path that already carries the placeholder must not get the
                // query a second time as a URL parameter.
                val inPath = path.contains("{$queryName}") || path.contains("{{$queryName}}")
                if (!inPath) {
                    val builder = http.newBuilder()
                    params[queryName]?.let { builder.addQueryParameter(queryName, it) }
                    return builder.build().toString()
                }
            }
            return url
        }

        /**
         * Path placeholders are percent-encoded: a title-derived id such as
         * "Two and a Half Men" must become a valid path segment. `id` stays raw
         * because some packs use it as an already-complete URL.
         */
        fun renderPath(template: String, params: Map<String, String>): String {
            val encoded = params.mapValues { (key, value) ->
                if (key in PATH_RAW) value
                else java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            }
            return render(template, encoded)
        }

        /** Only `id` stays raw; every other placeholder is path-safe. */
        private val PATH_RAW = setOf("id")

        private fun String.toHttpUrlOrNull() = runCatching { toHttpUrl() }.getOrNull()
    }
}