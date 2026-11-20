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
}