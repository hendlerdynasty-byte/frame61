# PLAYER.md — Overlay und D-Pad (Block 7)

Stand: 2026-11-18 · Build `9d1acda` (APK `efd3226a…`) · Gerät SM-A525F

## Overlay

| Anforderung | Umsetzung |
|---|---|
| Auto-Hide nach 3 s | `LaunchedEffect(controlsVisible, interaction)` blendet Logo, Controls und Track-Panel nach 3000 ms aus |
| Einblenden bei jeder Eingabe | `PlayerKeyRouter` setzt `controlsVisible=true` bei jedem Key-Down/Up |
| Kein dauerhaftes HUD | Controls, Logo, Track-Panel und NavBar sind im Player ausgeblendet; nur Video + dezente Pufferlinie |
| Quellenlabel nur 3 s | eigener `sourceVisible`-State mit 3000-ms-Timer |
| Fokus-Ring 4 dp | `ControlButton` nutzt `tvFocusable(ringWidth = 4.dp)` |
| Sprung-Feedback | 600 ms Gradient am linken/rechten Rand nach −10/+10 |
| Pufferanzeige dezent | 2-dp-Linie mit 45 % Alpha, nur bei 1–99 % Puffer |

## Tasten

`MainActivity.dispatchKeyEvent` leitet jeden Key an `PlayerKeyRouter`. Grund:
der Compose-Root verlor den Fokus, sobald die Media3-`PlayerView` attached war
(gemessen: LEFT wirkte, OK/UP kamen nie an). Der Router ist fokusunabhängig.

| Taste | Aktion |
|---|---|
| DPAD links | −10 s, Gradient links |
| DPAD rechts | +10 s, Gradient rechts |
| OK / Enter | Play/Pause |
| DPAD hoch | Info-Panel (Audio/Untertitel/Tempo) ein/aus |
| Zurück (1×) | Overlay schließen |
| Zurück (2×) | Player verlassen (Detailseite) |

`input keyevent` liefert auf dem A52 nur `ACTION_UP`; der Handler wertet
Aktionen deshalb auf `ACTION_UP` aus (DOWN blendet nur ein).

## Belege

| Datei | Inhalt |
|---|---|
| [overlay-visible.png](overlay-visible.png) | Logo, Quelle „ARD Mediathek", Controls direkt nach Start |
| [overlay-hidden.png](overlay-hidden.png) | nach 4 s: nur Video, keine NavBar, kein Label |
| [pause-overlay.png](pause-overlay.png) | OK → pausiert, „Play"-Label, Fortschritt |
| [seek-feedback.png](seek-feedback.png) | +10 s: Gradient rechts, Fokusring 4 dp auf „−10" |
| [info-panel.png](info-panel.png) | DPAD hoch: Audio/Spur/Tempo |
| [mp4-playback.png](mp4-playback.png) | MP4-Stream läuft (MIME-Fix), HUD aus |

## Gefundene und behobene Defekte

1. **Tasten kamen nicht an** — Compose-Fokus ging an die `PlayerView`.
   Fix: `isFocusable=false` + Activity-Key-Routing.
2. **Nur ACTION_UP** — der Handler reagierte auf DOWN; OK/UP taten nichts.
3. **MP4 als HLS geöffnet** — ARD liefert teils MP4; der Player erzwang
   `application/x-mpegURL` → `ERROR_CODE_PARSING_MANIFEST_MALFORMED`.
   Fix: MIME aus der Endung (`.m3u8`/`.mp4`), sonst Inferenz durch Media3.
4. **NavBar im Player** — dauerhaftes HUD; jetzt nur außerhalb des Players.
5. **Countdown über Controls** — Countdown sitzt jetzt 160 dp über dem Boden.

## Grenzen

- Der Fokus-Ring wird an den Controls gezeigt; die D-Pad-Tasten wirken direkt,
  nicht über Fokus-Navigation.
- Screenshots auf dem A52; TV-Dichte 1,0 nicht separat geprüft.
- Der 2×-Zurück-Pfad ist im Code und in der Gerätesitzung beobachtet
  (erster Back → Overlay weg, zweiter → Detail); die finalen Screenshots
  liegen nicht in allen Zwischenständen vor.
