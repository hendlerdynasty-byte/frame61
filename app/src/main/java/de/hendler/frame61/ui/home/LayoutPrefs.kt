package de.hendler.frame61.ui.home

/**
 * Layout id <-> rail set.
 *
 * A layout id is the comma-joined list of [RailKind.key] in canonical order.
 * That keeps it readable in DataStore, stable across restarts and independent
 * of enum ordinal numbers. Unknown keys are ignored, an empty or "default"
 * value falls back to [HomeContentBuilder.DEFAULT_LAYOUT].
 *
 * Pure functions, so the persistence contract is unit-testable without Android.
 */
object LayoutPrefs {

    const val DEFAULT_ID = "default"

    /**
     * Renders the rail set as a layout id in canonical enum order, so the same
     * set always produces the same string.
     */
    fun encode(kinds: Set<RailKind>): String {
        val selected = RailKind.entries.filter { it != RailKind.BILLBOARD && it in kinds }
        return if (selected.isEmpty()) DEFAULT_ID else selected.joinToString(",") { it.key }
    }

    /** Parses a stored layout id; unknown or empty input yields the default. */
    fun decode(layoutId: String?): Set<RailKind> {
        val raw = layoutId?.trim().orEmpty()
        if (raw.isBlank() || raw == DEFAULT_ID) return HomeContentBuilder.DEFAULT_LAYOUT
        val keys = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val parsed = RailKind.entries.filter { it != RailKind.BILLBOARD && it.key in keys }
        return parsed.toSet().ifEmpty { HomeContentBuilder.DEFAULT_LAYOUT }
    }

    /**
     * The layoutId stored on a profile row. Profiles that never customised
     * their home keep "default".
     */
    fun profileLayoutId(kinds: Set<RailKind>): String {
        val encoded = encode(kinds)
        return if (encoded == encode(HomeContentBuilder.DEFAULT_LAYOUT)) DEFAULT_ID else encoded
    }
}