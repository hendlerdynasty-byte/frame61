package de.hendler.frame61.net

import java.util.concurrent.ConcurrentHashMap
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Process-local cookie continuity for resolver, WebView handoff and media hops. */
object Frame61CookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, Map<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val merged = store[url.host].orEmpty().toMutableMap()
        cookies.forEach { cookie -> merged[key(cookie)] = cookie }
        store[url.host] = merged.values.associateBy(::key)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.flatMap { it.values }
            .filter { it.expiresAt > now && it.matches(url) }
    }

    fun clear() = store.clear()

    private fun key(cookie: Cookie): String = "${cookie.domain}|${cookie.path}|${cookie.name}"
}
