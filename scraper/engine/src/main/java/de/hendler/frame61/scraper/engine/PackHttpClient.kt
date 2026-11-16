package de.hendler.frame61.scraper.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Minimal HTTP front-end for [PackRule]s. Bodies are returned as text; parsing
 * is [PackInterpreter]'s job, so this class stays trivially replaceable in
 * tests.
 */
class PackHttpClient(
    private val client: OkHttpClient = OkHttpClient()
) {

    @Throws(IOException::class)
    fun get(pack: SourcePack, rule: PackRule, params: Map<String, String>): String {
        val url = buildUrl(pack, rule, params)
        val builder = Request.Builder().url(url)
        rule.headers.forEach { (key, value) -> builder.header(key, value) }

        if (rule.method.equals("POST", ignoreCase = true)) {
            val body = render(rule.body.orEmpty(), params).toRequestBody(null)
            builder.post(body)
        } else {
            builder.get()
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return body
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
            val rendered = render(path, params)
            val base = pack.baseUrl.trimEnd('/')
            val url = when {
                rendered.startsWith("http") -> rendered
                rendered.startsWith("/") -> base + rendered
                else -> "$base/$rendered"
            }

            // `{query}`-style placeholders that survived templating become query params
            val http = url.toHttpUrlOrNull() ?: return url
            if (rule.queryParam != null && params.isNotEmpty()) {
                val builder = http.newBuilder()
                params[rule.queryParam]?.let { builder.addQueryParameter(rule.queryParam, it) }
                return builder.build().toString()
            }
            return url
        }

        private fun String.toHttpUrlOrNull() = runCatching { toHttpUrl() }.getOrNull()
    }
}