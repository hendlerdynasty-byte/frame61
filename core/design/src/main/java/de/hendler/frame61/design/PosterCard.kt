package de.hendler.frame61.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * The one and only cover component.
 *
 * Fixed modifier stack, in this order:
 *   1. optional focus/click wrapper (tvFocusable, ring drawn inside),
 *   2. clip(rounded shape) — before background/image/border,
 *   3. background (surface or gradient placeholder),
 *   4. image with ContentScale.Crop (inherits the clip),
 *   5. optional 1 dp border drawn *inside* the bounds,
 *   6. optional overlay content.
 *
 * No screen adds its own clip/shape around a cover. Radius is clamped to at
 * least 8 dp so a too-small skin radius cannot produce ragged corners.
 * Shadows/elevation are deliberately not used: they showed as a hard square
 * edge under the rounded poster on the A52.
 */
@Composable
fun PosterCard(
    posterUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LocalSkin.current.layout.cardRadius,
    contentDescription: String? = null,
    showBorder: Boolean = false,
    focusId: String? = null,
    onClick: (() -> Unit)? = null,
    /** Wird genau einmal aufgerufen, wenn die Karte den Fokus **erhält** (Block 7). */
    onFocus: (() -> Unit)? = null,
    progress: Float? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val skin = LocalSkin.current
    val radius = maxOf(cornerRadius, 8.dp)
    val shape = RoundedCornerShape(radius)

    val focusModifier = if (focusId != null) {
        Modifier.tvFocusable(
            id = focusId,
            focusedColor = skin.colors.focus,
            onClick = onClick,
            onFocusChanged = onFocus?.let { callback -> { focused: Boolean -> if (focused) callback() } }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(focusModifier)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            // Markanter Lade-/Fehlergrund statt grauer Kachel. Lokale Poster
            // liegen vollständig darüber; schlägt eine Bildquelle fehl, bleibt
            // dieser gestaltete Zustand sichtbar statt „Nichts".
            .background(
                Brush.linearGradient(
                    listOf(
                        skin.colors.primary.copy(alpha = 0.35f),
                        skin.colors.surface
                    )
                )
            )
    ) {
        val url = posterUrl.orEmpty()
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showBorder) {
            // Inside the clipped bounds: a 1 dp stroke never sits on the edge.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, skin.colors.textPrimary.copy(alpha = 0.35f), shape)
            )
        }

        if (progress != null && progress > 0f) {
            // Fortschritt pro Titel: duenne Spur am unteren Rand, innerhalb des Clips.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(skin.colors.textPrimary.copy(alpha = 0.25f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(skin.colors.focus)
            )
        }

        overlay()
    }
}
