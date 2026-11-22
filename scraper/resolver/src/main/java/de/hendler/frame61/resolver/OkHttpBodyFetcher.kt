package de.hendler.frame61.resolver

import de.hendler.frame61.net.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Network [BodyFetcher]. All errors surface as exceptions to the caller. */
class OkHttpBodyFetcher(
    private val client: OkHttpClient = HttpClientFactory.create()
) : BodyFetcher {

    override suspend fun get(url: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code} für $url")
                body
            }
        }

    override suspend fun post(url: String, body: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url(url)
                .post(body.toRequestBody(null))
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw java.io.IOException("HTTP ${response.code} für $url")
                }
                responseBody
            }
        }

    /**
     * Letztziel nach allen Weiterleitungen, ohne den Rumpf zu lesen.
     *
     * Indexseiten wie aniworld.to liefern `/redirect/<id>`, das per 302 auf den
     * echten Hoster zeigt. Der Resolver braucht diese End-URL, sonst versucht
     * er, eine HTML-Seite abzuspielen.
     */
    override suspend fun resolveRedirect(url: String, headers: Map<String, String>): String? =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { response ->
                val finalUrl = response.request.url.toString()
                when {
                    finalUrl != url -> finalUrl
                    response.code in 300..399 ->
                        response.header("Location")?.let { location ->
                            if (location.startsWith("http")) location
                            else url.substringBeforeLast('/') + "/" + location
                        }
                    else -> null
                }
            }
        }
}