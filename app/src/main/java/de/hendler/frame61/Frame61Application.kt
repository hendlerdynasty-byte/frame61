package de.hendler.frame61

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
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
            .build()

    companion object {
        const val DISK_CACHE_BYTES = 512L * 1024 * 1024
    }
}