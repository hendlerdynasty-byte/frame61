package de.hendler.frame61.design

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Raw shape of `assets/skins/<id>.json`. Keys match the files exactly; the
 * parser maps them onto [Frame61Skin].
 */
@Serializable
data class SkinJson(
    val id: String,
    val canvas: String = "#0d0e12",
    val surfaces: String = "#17181f",
    val accents: String = "#5b6aff",
    val text: String = "#f0f0f5",
    val radius: Int = 8,
    val cardAspect: Float = 0.6f,
    val rowHeight: Int = 180,
    val gutter: Int = 12,
    val navModel: String = "bottom-bar",
    val focusTreatment: String = "scale",
    val motionLevel: String = "medium",
    val typeScale: Float = 1.0f,
    val billboardHeight: Int = 380,
    val detailLayout: String = "vertical",
    val playerChrome: String = "dark"
)

/**
 * Reads the 11 skin files from assets. One parser, no second token path.
 *
 * A broken file is skipped on its own; the caller always has a fallback skin.
 */
class SkinJsonParser(private val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}) {

    fun parse(content: String): Frame61Skin? {
        val raw = runCatching { json.decodeFromString<SkinJson>(content) }.getOrNull() ?: return null
        if (raw.id.isBlank()) return null
        return raw.toSkin()
    }

    fun SkinJson.toSkin(): Frame61Skin = Frame61Skin(
        id = id,
        name = displayName(id),
        colors = Frame61Colors(
            canvas = color(canvas),
            surface = color(surfaces),
            primary = color(accents),
            focus = focusColor(accents),
            textPrimary = color(text),
            onSurface = color(text),
            onCanvas = color(text),
            onPrimary = onAccentColor(accents)
        ),
        typography = Frame61Typography().scaled(typeScale),
        layout = Frame61Layout(
            radius = radius,
            cardAspectRatio = cardAspect,
            rowHeight = rowHeight,
            gutter = gutter,
            billboardHeight = billboardHeight,
            detailLayout = detailLayout,
            playerChrome = playerChrome
        ),
        navModel = navModel,
        focusTreatment = focusTreatment,
        motionLevel = parseMotionLevel(motionLevel),
        darkMode = true
    )

    companion object {
        /** `netflix` → `Netflix`, `primevideo` → `Prime Video`, `hbomax` → `HBO Max`. */
        fun displayName(id: String): String = when (id) {
            "netflix" -> "Netflix"
            "disneyplus" -> "Disney+"
            "appletv" -> "Apple TV"
            "primevideo" -> "Prime Video"
            "hbomax" -> "HBO Max"
            "paramount" -> "Paramount+"
            "plex" -> "Plex"
            "crunchyroll" -> "Crunchyroll"
            "kodimodus" -> "Kodi Modus"
            "frame61" -> "Frame61"
            "frame61-indigo" -> "Frame61 Indigo"
            "custom" -> "Custom"
            else -> id
        }

        /** `"medium"` → [MotionLevel.MEDIUM]; unknown values fall back to [MotionLevel.MEDIUM]. */
        fun parseMotionLevel(value: String): MotionLevel = when (value.lowercase().trim()) {
            "none", "off", "0" -> MotionLevel.NONE
            "low", "1" -> MotionLevel.LOW
            "high", "2" -> MotionLevel.HIGH
            else -> MotionLevel.MEDIUM
        }

        /** Focus ring is a lightened accent, so it stays visible on every canvas. */
        fun focusColor(accents: String): androidx.compose.ui.graphics.Color =
            lighten(color(accents), 0.55f)

        /** Readable text on top of the accent colour. */
        fun onAccentColor(accents: String): androidx.compose.ui.graphics.Color =
            if (luminance(color(accents)) < 0.55f) androidx.compose.ui.graphics.Color.White
            else androidx.compose.ui.graphics.Color(0xFF0B0B0F)

        fun color(hex: String): androidx.compose.ui.graphics.Color {
            val cleaned = hex.removePrefix("#").trim()
            return when (cleaned.length) {
                6 -> runCatching { androidx.compose.ui.graphics.Color(("FF$cleaned").toLong(16)) }
                    .getOrDefault(androidx.compose.ui.graphics.Color.Black)
                8 -> runCatching { androidx.compose.ui.graphics.Color(cleaned.toLong(16)) }
                    .getOrDefault(androidx.compose.ui.graphics.Color.Black)
                else -> androidx.compose.ui.graphics.Color.Black
            }
        }

        private fun luminance(c: androidx.compose.ui.graphics.Color): Float =
            0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

        private fun lighten(
            c: androidx.compose.ui.graphics.Color,
            amount: Float
        ): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
            red = (c.red + (1f - c.red) * amount).coerceIn(0f, 1f),
            green = (c.green + (1f - c.green) * amount).coerceIn(0f, 1f),
            blue = (c.blue + (1f - c.blue) * amount).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}

/** Motion budget derived from the skin, so screens never guess. */
enum class MotionLevel { NONE, LOW, MEDIUM, HIGH }