package de.hendler.frame61

/**
 * M6 feature flag (TMDB de-DE, availability badges, Trakt).
 *
 * Default is off (`frame61.m6=false`). The TMDB interfaces in `:core:meta`
 * remain as optional stubs; no key is bundled and no live call is made. Turning
 * the flag on is a separate product task, not a build accident.
 */
object M6Features {
    val enabled: Boolean get() = BuildConfig.M6_ENABLED
    const val IMPLEMENTED = false
}
