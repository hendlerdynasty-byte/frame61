# FIRETV.md — 10-Foot-Abnahme

Stand: 18.11.2026. Ergebnis: **Code und Oberfläche auf Android-TV-Emulator
abgenommen; echtes Fire-TV-Gerät unangetastet.** Hardware-Startzeit ist
daher `NOT_RUN` mit konkreter Ursache, kein erfundener Messwert.

## Umgebung

- AVD `frame61-tv-1080p` (neu angelegt), Device-Profil `tv_1080p`,
  Systemimage `android-35/google_apis/arm64-v8a` (das Verzeichnis
  `system-images/android-36/android-tv` war leer, daher 35).
- Emulator `emulator-5556`, 1920 × 1080, 320 dpi, SwiftShader/ANGLE.
- APK: Debug-Build aus Block 15,
  SHA-256 `a4e7529a0d22c2781fa1855c10a0b7d34d10038b2c87ded234c270d3d4ea55c5`.
- Install per `adb install -r`, Start per
  `am start -n de.hendler.frame61/de.hendler.frame61.ui.MainActivity`.

## Geprüft

| Prüfung | Beleg | Ergebnis |
|---|---|---|
| Leanback-Intent / Launcher | Activity `de.hendler.frame61/.ui.MainActivity` topResumed | PASS |
| Startseite, Banner, Hero, lokale Poster | `docs/design/block16/start.png` | PASS |
| D-Pad Fokus (Fernbedienung) | `keyevent 20/22` → Fokusring auf Skin-Pille, `docs/design/block16/dpad-card.png` | PASS |
| Overscan 1080p | 5 % horizontal / 3 % vertikal sichtbar, Navigation vollständig | PASS |
| Overscan 720p | `wm size 1280x720`, `docs/design/block16/720p.png`, Nav weiter sichtbar | PASS |
| Filme / Serien / Suche / Meine Liste / Einstellungen | `filme.png`, `serien.png`, `suche.png`, `liste.png`, `einstellungen.png` | PASS |
| Startzeit Emulator | `am start -W`: 2426, 3390, 6239, 9107, 10127 ms (Median 6239 ms) | INFO — Software-Rendering, kein Fire-TV-Hardwarewert |
| Play Protect | auf Fire TV/Android TV nicht im Play-Sideload-Pfad | siehe Block 17 |

## Screenshots

Alle Bilder liegen unter `docs/design/block16/` und sind echte
`adb screencap`-Aufnahmen des TV-Emulators (1920 × 1080, außer `720p.png`).

## Grenzen

- Kein echtes Fire-TV-Gerät verbunden oder angesprochen: Die Owner-Regel
  „laufendes echtes Fire TV unangetastet" bleibt in Kraft. Hardware-Startzeit,
  echte Fernbedienung und Fire-OS-spezifische Codec-/HDR-Fälle sind
  **NOT_RUN**, nicht PASS.
- Der Emulator nutzt Android 35 mit Software-GPU; die Startzeiten sind nicht
  mit dem A52- oder Fire-TV-Hardwarewert vergleichbar.
- Kein Store-Upload, kein Rollout.

## Resume-Befehl

```
~/Library/Android/sdk/emulator/emulator -avd frame61-tv-1080p -no-snapshot -no-audio -no-window -port 5556
~/Library/Android/sdk/platform-tools/adb -s emulator-5556 install -r <apk>
~/Library/Android/sdk/platform-tools/adb -s emulator-5556 shell am start -n de.hendler.frame61/de.hendler.frame61.ui.MainActivity
~/Library/Android/sdk/platform-tools/adb -s emulator-5556 shell screencap -p /sdcard/cap.png
```
