package de.hendler.frame61.playback

/**
 * Welche Quelle gehoert zu welcher Kataloggruppe (Block 7).
 *
 * Der Katalog wurde aus genau diesen Quellen geerntet; die Zuordnung ist damit
 * belegt statt geraten. Der Vorwaermlauf loest **nur** die Quelle der
 * Kataloggruppe auf — ein bis zwei HTTP-Anfragen statt der 33 Pack-Suchen des
 * vollen Laufs.
 *
 * **bs.to fehlt bewusst.** bs.to laeuft ueber VOE und damit ueber die stille
 * WebView. Die WebView ist app-weit serialisiert ([WebViewGate]); ein
 * Vorwaermlauf, der dort mitfaehrt, wuerde dem echten Start den Slot
 * wegnehmen. Das Vorwaermen bleibt deshalb fuer die Quellen, die **ohne**
 * Browser auskommen.
 */
internal object PrefetchScope {

    /** Kataloggruppe → Pack(s), die diese Gruppe beliefert. */
    val PACKS_BY_GROUP: Map<String, Set<String>> = mapOf(
        "serienstream" to setOf("s-to"),
        "aniworld" to setOf("aniworld"),
        "filmpalast" to setOf("filmpalast"),
        "moflix" to setOf("moflix"),
        "ard" to setOf(ArdPlaybackResolver.PACK_ID)
    )

    /** Die Pack(s) der Gruppe — leer, wenn die Gruppe nicht vorwaermbar ist. */
    fun packsFor(group: String?): Set<String> = PACKS_BY_GROUP[group].orEmpty()

    /** Darf diese Gruppe vorwaermbar sein? */
    fun isPrefetchable(group: String?): Boolean = packsFor(group).isNotEmpty()
}
