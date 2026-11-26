package de.hendler.frame61.ui

/** Wo die dauerhafte Navigation liegen darf. Eine untere Leiste ist verboten. */
enum class NavigationChrome { SIDE, TOP, NONE }

/**
 * Reine Entscheidung für die App-Shell.
 *
 * TV nutzt immer die Seitenleiste, Telefone die kompakte obere Leiste. Nur der
 * Player ist vollflächig und damit ohne Navigation. Es gibt bewusst keinen
 * BOTTOM-Zustand, damit die sechs Punkte nie unten eingeblendet werden können.
 */
fun navigationChrome(isTv: Boolean, route: String?): NavigationChrome {
    val baseRoute = route?.substringBefore('?').orEmpty()
    val isPlayer = baseRoute == "player" || baseRoute.startsWith("player/")
    return when {
        isPlayer -> NavigationChrome.NONE
        isTv -> NavigationChrome.SIDE
        else -> NavigationChrome.TOP
    }
}
