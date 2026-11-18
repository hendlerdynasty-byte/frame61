package de.hendler.frame61

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Application entry point.
 *
 * Also the Coil [SingletonImageLoader.Factory]: one image loader for the whole
 * app with a bounded memory cache (25 %) and a 512 MB disk cache, so a long
 * browsing session cannot grow the cache without limit.
 *
 * RGB_565 note (evidence-based): Coil 3.1.0 exposes no public bitmap
 * configuration — verified with javap against the 3.1.0 artifacts: no
 * `bitmapConfig` on `ImageLoader.Builder` or `ImageRequest.Builder`, and
 * `BitmapFactoryDecoder` is final. Forcing RGB_565 would need a custom
 * `Decoder.Factory`; that is documented in docs/STATE.md instead of guessed.
 */
@HiltAndroidApp
class Frame61Application : Application(), SingletonImageLoader.Factory {

    /**
     * Wikimedia (movie posters) answers 403 for OkHttp/empty User-Agents.
     * Measured: UA "" → 403, "okhttp/4.12.0" → 403, "Frame61/1.0" → 200.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("coil").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
            }
            .build()

    companion object {
        const val DISK_CACHE_BYTES = 512L * 1024 * 1024
        const val USER_AGENT = "Frame61/1.0 (Android; poster cache)"
    }
}