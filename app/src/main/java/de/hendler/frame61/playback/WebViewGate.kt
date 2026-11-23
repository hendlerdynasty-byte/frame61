package de.hendler.frame61.playback

import kotlinx.coroutines.sync.Mutex

/**
 * Gemeinsame Sperre für **alle** WebViews der App.
 *
 * Gemessene Ursache eines ANR („Frame61 wird wiederholt beendet“), 23.11.2026:
 * Das Vorwärmen erzeugte vier WebViews gleichzeitig, jede blockiert den
 * Hauptthread beim Laden. Seitdem führt genau **ein** Pfad gleichzeitig WebView:
 * JS-Auswertung, Cookie-Ernte und Medien-Interception teilen sich dieses Mutex.
 * Eine parallele Anfrage wartet also, statt den Hauptthread zu überlasten.
 */
object WebViewGate {
    val mutex = Mutex()
}