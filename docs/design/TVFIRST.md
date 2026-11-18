# TVFIRST.md — TV-Grundlagen (Block 3)

Stand: 2026-11-18 · Build `4c2223e` (APK `1e26022c…`) · Gerät SM-A525F
(1080 × 2400, Density 2,625)

## Umsetzung

| Anforderung | Umsetzung | Beleg |
|---|---|---|
| Overscan 5 % horizontal / 3 % vertikal | `Frame61` misst per `BoxWithConstraints`; NavHost und NavBar erhalten `maxWidth*0.05` / `maxHeight*0.03` | UI-Dump: Inhalt beginnt bei x=54 px, y=156 px (Statusbar 84 + 72) |
| Fokus-Skalierung 1.08 + 3-dp-Ring | `TvFocusStyle.scale = 1.08f`, `ringWidth = 3.dp`; Ring wird innen gezeichnet | [block3-dpad-fresh-down.png](block3-dpad-fresh-down.png) |
| Touch-Targets ≥ 48 dp | `tvFocusable` beginnt mit `minimumInteractiveComponentSize()` | Code; Navigation/Buttons |
| Body ≥ 16 sp, Titel ≥ 32 sp | `Frame61Typography.screenTitle=32.sp`, `toMaterial()` klemmt `headlineSmall` auf 32 sp und `bodyLarge/Medium` auf 16 sp | Detail-Screenshot: Titel zweizeilig 32 sp, Fließtext 16 sp |
| Poster 2:3, 190 dp | `CatalogPoster` nutzt 190 dp Höhe und exakt `2f/3f` Breite; alle Cover über `PosterCard` | Portrait: 333 × 499 px = 2:3; Landscape 1080p: 190 dp |
| Hero ≤ 40 % Höhe | `heroHeight = min(billboardHeight, screenHeight*0.4)` | Portrait: 945 px = 39,4 %; Landscape: 432 px = 40 % |
| WindowSizeClass | App liefert `LocalWindowSizeClass`; kompakt = 12 dp Außenabstand, expanded = 24 dp; Poster bleibt 190 dp → kompakt weniger Spalten | Portrait 411 dp = Compact, Landscape 914 dp = Expanded |
| D-Pad-Vollnavigation | `tvFocusable` behandelt `DPAD_CENTER`/`Enter` zentral; Fokus-Ring + Scrollen belegt | [block3-dpad-fresh-down.png](block3-dpad-fresh-down.png), [block3-dpad-enter-fixed.png](block3-dpad-enter-fixed.png) |
| Fokus-Persistenz | `FocusRestorer` zusätzlich in Suche (`search-input`) und Einstellungen (`settings-autoplay`) | Code |

## Screenshots

| Datei | Inhalt |
|---|---|
| [block3-compact-portrait.png](block3-compact-portrait.png) | Kompakt/Portrait: Safe-Area, Hero 39,4 %, 2:3-Poster, Fokus |
| [block3-1080p-landscape.png](block3-1080p-landscape.png) | Landscape 2400 × 1080: Hero 40 %, 5 Poster-Spalten sichtbar |
| [block3-dpad-fresh.png](block3-dpad-fresh.png) | Frischer Start, Fokus auf Billboard |
| [block3-dpad-fresh-down.png](block3-dpad-fresh-down.png) | DPAD_DOWN: Ring auf Rail-Karte, 1.08-Skalierung |
| [block3-dpad-enter-fixed.png](block3-dpad-enter-fixed.png) | DPAD_CENTER: Detailseite „Band of Brothers", Fokus auf „Abspielen" |

## Gefundener und behobener Defekt

Der erste D-Pad-Test zeigte: Fokus bewegt sich, aber **ENTER löst keinen Klick
aus**. Ursache: `Modifier.clickable` allein reagierte auf dem A52 nicht auf
`DPAD_CENTER`. Fix: zentrales `onKeyEvent` in `tvFocusable` für
`DirectionCenter`/`Enter`/`NumPadEnter` (KeyUp, Event konsumiert). Danach öffnet
ENTER die Detailseite.

## Grenzen

- Gemessen auf einem Handy (1080 × 2400), nicht auf einem Fire-TV-Stick;
  Fire TV bleibt per Owner-Policy unangetastet.
- „1080p" ist hier Landscape mit 1080 px Höhe; TV-Dichte 1,0 wurde nicht
  separat gemessen.
- Die 48-dp-Garantie gilt für `tvFocusable`-Ziele; reine Textzeilen ohne
  `tvFocusable` sind nicht klickbar und brauchen sie nicht.
