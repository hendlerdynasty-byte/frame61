package de.hendler.frame61.ui.player

import android.view.KeyEvent

/**
 * Focus-independent key routing for the full-screen player.
 *
 * Compose's focus system did not keep the root focused once the Media3
 * `PlayerView` was attached (measured on the A52: LEFT seek worked, OK/UP were
 * lost). The Activity dispatches every key here first; the player consumes only
 * the keys it maps and lets everything else through.
 */
object PlayerKeyRouter {

    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}
