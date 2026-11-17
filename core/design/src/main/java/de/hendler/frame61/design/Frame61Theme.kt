package de.hendler.frame61.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Color tokens from a skin.
 */
data class Frame61Colors(
    val canvas: Color = Color(0xFF0D0E12),
    val surface: Color = Color(0xFF17181F),
    val primary: Color = Color(0xFF5B6AFF),
    val onPrimary: Color = Color.White,
    val onSurface: Color = Color(0xFFF0F0F5),
    val onCanvas: Color = Color(0xFFF0F0F5),
    val focus: Color = Color(0xFFAEB4FF),
    val textPrimary: Color = Color(0xFFF0F0F5),
    val textSecondary: Color = Color(0xFF9A9AA8)
)

/**
 * Typography tokens. [typeScale] multiplies every size, so a skin can enlarge or
 * shrink the whole UI without touching screens.
 */
data class Frame61Typography(
    val billboardTitle: TextUnit = 36.sp,
    val screenTitle: TextUnit = 32.sp,
    val rowTitle: TextUnit = 20.sp,
    val cardTitle: TextUnit = 14.sp,
    val body: TextUnit = 16.sp,
    val caption: TextUnit = 12.sp,
    val typeScale: Float = 1.0f
) {
    fun scaled(factor: Float): Frame61Typography = copy(
        billboardTitle = billboardTitle * factor,
        screenTitle = screenTitle * factor,
        rowTitle = rowTitle * factor,
        cardTitle = cardTitle * factor,
        body = body * factor,
        caption = caption * factor,
        typeScale = factor
    )

    /** Material [Typography] derived from the tokens, with TV minimums. */
    fun toMaterial(): Typography {
        val base = Typography()
        return base.copy(
            displaySmall = base.displaySmall.copy(
                fontSize = billboardTitle.atLeast(32.sp)
            ),
            // Screen headings (Detail, Settings, Suche …) are never below 32 sp.
            headlineSmall = base.headlineSmall.copy(
                fontSize = screenTitle.atLeast(32.sp)
            ),
            titleMedium = base.titleMedium.copy(fontSize = cardTitle),
            // Body copy is never below 16 sp — TV viewing distance.
            bodyLarge = base.bodyLarge.copy(fontSize = body.atLeast(16.sp)),
            bodyMedium = base.bodyMedium.copy(fontSize = body.atLeast(16.sp)),
            labelSmall = base.labelSmall.copy(fontSize = caption)
        )
    }
}

/** Minimum font size in sp, independent of the skin's type scale. */
private fun TextUnit.atLeast(minimum: TextUnit): TextUnit =
    if (this.value < minimum.value) minimum else this

/**
 * Layout tokens from a skin.
 */
data class Frame61Layout(
    val radius: Int = 8,
    /** 2:3 cover, the TV poster standard. */
    val cardAspectRatio: Float = 2f / 3f,
    val rowHeight: Int = 190,
    val gutter: Int = 12,
    val billboardHeight: Int = 420,
    val detailLayout: String = "vertical",
    val playerChrome: String = "dark"
) {
    val cardRadius: androidx.compose.ui.unit.Dp get() = radius.dp
    val rowHeightDp: androidx.compose.ui.unit.Dp get() = rowHeight.dp
    val gutterDp: androidx.compose.ui.unit.Dp get() = gutter.dp
    val billboardHeightDp: androidx.compose.ui.unit.Dp get() = billboardHeight.dp
}

data class Frame61Skin(
    val id: String,
    val name: String,
    val colors: Frame61Colors = Frame61Colors(),
    val typography: Frame61Typography = Frame61Typography(),
    val layout: Frame61Layout = Frame61Layout(),
    val navModel: String = "bottom-bar",
    val focusTreatment: String = "scale",
    val motionLevel: MotionLevel = MotionLevel.MEDIUM,
    val darkMode: Boolean = true
)

@Composable
fun Frame61Theme(
    skin: Frame61Skin = LocalSkin.current,
    darkTheme: Boolean = isSystemInDarkTheme() || skin.darkMode,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = skin.colors.primary,
            onPrimary = skin.colors.onPrimary,
            surface = skin.colors.surface,
            onSurface = skin.colors.onSurface,
            background = skin.colors.canvas,
            onBackground = skin.colors.onCanvas
        )
    } else {
        lightColorScheme(
            primary = skin.colors.primary,
            onPrimary = skin.colors.onPrimary,
            surface = skin.colors.surface,
            onSurface = skin.colors.onSurface,
            background = skin.colors.canvas,
            onBackground = skin.colors.onCanvas
        )
    }

    CompositionLocalProvider(
        LocalSkin provides skin,
        LocalFrame61Typography provides skin.typography,
        // Screens that are a plain Column (Detail, Search, Settings …) have no
        // Material Surface above them. Without this, Text falls back to black
        // on the dark canvas — measured on the A52 (unreadable body text).
        LocalContentColor provides skin.colors.textPrimary
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = skin.typography.toMaterial(),
            content = content
        )
    }
}

val LocalSkin = compositionLocalOf<Frame61Skin> {
    Frame61Skin(id = "frame61", name = "Frame61")
}

/** Typography tokens as a composition local, so screens read sizes directly. */
val LocalFrame61Typography = compositionLocalOf { Frame61Typography() }

/** Default skin used before any skin file is loaded. */
val DefaultFrame61Skin = Frame61Skin(id = "frame61", name = "Frame61")

@Composable
fun rememberSkin(skinId: String? = null): Frame61Skin {
    val fallback = LocalSkin.current
    return remember(skinId) { if (skinId == null) fallback else fallback.copy(id = skinId) }
}