package de.hendler.frame61.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Registry of focusable ids so focus can be restored by name (FocusRestorer).
 *
 * Compose's [FocusRequester] is per call site; this maps the stable id used by
 * the layout builder to the requester that currently holds focus.
 */
interface TvFocusRegistry {
    fun register(id: String, requester: FocusRequester)
    fun unregister(id: String)
    fun request(id: String): Boolean
    fun clear()
    val currentId: String?
}

class DefaultTvFocusRegistry : TvFocusRegistry {

    private val requesters = mutableStateMapOf<String, FocusRequester>()
    private var focusedId by mutableStateOf<String?>(null)

    override val currentId: String? get() = focusedId

    override fun register(id: String, requester: FocusRequester) {
        requesters[id] = requester
    }

    override fun unregister(id: String) {
        requesters.remove(id)
        if (focusedId == id) focusedId = null
    }

    override fun request(id: String): Boolean {
        val requester = requesters[id] ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }

    override fun clear() {
        requesters.clear()
        focusedId = null
    }

    fun setFocused(id: String?) {
        focusedId = id
    }
}

val LocalTvFocusRegistry = compositionLocalOf<TvFocusRegistry> { DefaultTvFocusRegistry() }

@Composable
fun rememberTvFocusRegistry(): TvFocusRegistry = remember { DefaultTvFocusRegistry() }

/** Ring metrics, so no screen invents its own. */
object TvFocusStyle {
    val ringWidth: Dp = 3.dp
    /** 10-foot focus feedback: 8 % growth, still inside the layout bounds. */
    val scale: Float = 1.08f
    val cornerRadius: Dp = 12.dp
}

/**
 * Makes a component focusable and paints a ring **inside** its bounds, so a
 * scrolling parent can never clip it. Use [rememberTvFocus] when the caller also
 * needs the [FocusRequester] (initial focus or restore).
 */
@Composable
fun Modifier.tvFocusable(
    id: String,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    focusedColor: Color = LocalSkin.current.colors.focus,
    /** Player chrome wants 4 dp; other screens use the skin token. */
    ringWidth: Dp? = null,
    onClick: (() -> Unit)? = null
): Modifier {
    val registry = LocalTvFocusRegistry.current
    val requester = remember(id) { FocusRequester() }
    val interaction = remember(id) { MutableInteractionSource() }
    // The idiomatic read: focus state straight from the interaction source that
    // is handed to `focusable()`. `onFocusChanged` was unreliable here (measured
    // on the A52: uiautomator reported a focused node, the ring never drew).
    val focused by interaction.collectIsFocusedAsState()
    val focusTokens = LocalSkin.current.focus
    val action = onClick

    DisposableEffect(id, requester) {
        registry.register(id, requester)
        onDispose { registry.unregister(id) }
    }

    LaunchedEffect(focused, id) {
        if (focused && registry is DefaultTvFocusRegistry) registry.setFocused(id)
    }

    return this
        // Touch targets are at least 48 dp; TV cards are larger anyway.
        .minimumInteractiveComponentSize()
        .focusRequester(requester)
        .focusable(enabled = enabled, interactionSource = interaction)
        .then(
            if (focused) {
                Modifier
                    .graphicsLayer {
                        scaleX = focusTokens.scale
                        scaleY = focusTokens.scale
                    }
                    // Draw after the content: a `border` on a wrapper is painted
                    // over by the child, which made the ring invisible.
                    .drawWithContent {
                        drawContent()
                        val w = (ringWidth ?: focusTokens.ringWidth).toPx()
                        drawRect(
                            color = focusedColor,
                            topLeft = Offset(w / 2f, w / 2f),
                            size = Size(size.width - w, size.height - w),
                            style = Stroke(width = w)
                        )
                    }
            } else {
                Modifier
            }
        )
        .then(
            if (action != null && enabled) {
                Modifier
                    // D-pad OK/Enter must activate the focused target. Compose's
                    // clickable alone did move focus but did not fire on the A52
                    // with a hardware D-pad (measured: ring moved, detail did not
                    // open). Consume the key event here, centrally.
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter)
                        ) {
                            action()
                            true
                        } else {
                            false
                        }
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = action
                    )
            } else {
                Modifier
            }
        )
}

/**
 * Moves focus to [targetId] if nothing holds focus yet — prevents the focus hole
 * after navigating back to a screen.
 */
@Composable
fun FocusRestorer(
    targetId: String,
    registry: TvFocusRegistry = LocalTvFocusRegistry.current
) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(targetId) {
        if (registry.currentId == null) registry.request(targetId)
    }
    DisposableEffect(targetId) {
        onDispose { focusManager.clearFocus() }
    }
}

/** Restores focus to [targetId] whenever [trigger] changes (profile/rail switch). */
@Composable
fun RestoreFocusOn(trigger: Any?, targetId: String) {
    val registry = LocalTvFocusRegistry.current
    LaunchedEffect(trigger, targetId) { registry.request(targetId) }
}

/** Keeps Compose's focus system authoritative; returns a restore function by id. */
@Composable
fun rememberFocusRestorer(): (String) -> Unit {
    val registry = LocalTvFocusRegistry.current
    return remember(registry) { { id: String -> registry.request(id) } }
}

@Composable
fun ProvideTvFocusRegistry(
    registry: TvFocusRegistry = rememberTvFocusRegistry(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTvFocusRegistry provides registry, content = content)
}