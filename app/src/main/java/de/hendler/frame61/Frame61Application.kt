package de.hendler.frame61

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class Frame61Application : Application() {

    override fun onCreate() {
        super.onCreate()
    }
}

/*
 * Image loading note: Coil 3.1 resolves `AsyncImage` with its default
 * `SingletonImageLoader`. The hardening targets (RGB_565 bitmaps, 512 MB disk
 * cache) belong in a custom `ImageLoader.Factory` here — the builder API moved
 * between Coil versions, so it is added in the hardening pass with a verified
 * signature rather than guessed at now. See docs/STATE.md "Offen".
 */