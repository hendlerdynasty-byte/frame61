# PLAYPROTECT.md — Sideload-Hürden

Stand: 18.11.2026. Der Updater lädt die signierte APK, prüft SHA-256 und
öffnet den System-Installer. Die letzte Bestätigung hängt vom Gerät ab; das
ist keine App-Abnahme, sondern eine externe Sicherheitsmeldung.

## Hinweis in der App

`Einstellungen → Update-Quelle` enthält unter „Sideload-Hinweis (Android)"
den Text „Trotzdem installieren" und das echte Dialogbild
`app/src/main/res/drawable-nodpi/play_protect_hint.png`
(aus `docs/design/block12/installer.png`, auf den Dialog zugeschnitten).
Sichtbar auf dem A52: `docs/design/block17/a52/12.png`.

## A52 (Android 14, Google Play Protect)

- Beleg: `docs/design/block12/installer.png`, `check2.png`, `check3.png`.
- Meldung: „App-Scan empfohlen … Play Protect kennt diese App noch nicht".
- Korrekte Reaktion: **Abbrechen** (kein APK an Google senden), dann in
  Android „Installieren unbekannter Apps" für Frame61 erlauben und erneut
  installieren. Auf dem A52 wurde die Installation aus dem Release-Asset per
  `adb install -r` belegt (`versionCode=2`).

## Android-TV-Emulator (kein Play Protect)

- Ausgangslage: signiertes Release 1.0.0 (`versionCode=1`) auf dem TV-AVD.
- Manifest-Check: „Version 2 verfügbar" —
  `docs/design/block17/update-available.png`.
- Systemdialog „Do you want to update this app?" —
  `docs/design/block17/installer-ready.png`.
- Einmalige Hürde „Install unknown apps" —
  `docs/design/block17/unknown-settings.png`; danach Update bestätigt.
- Ergebnis: `versionCode=2`, `versionName=1.1.0`, `InstallSuccess`,
  App danach geöffnet — `docs/design/block17/update-done.png`,
  `update-opened.png`.

Der TV-Pfad ist damit **live durchlaufen**; Play Protect ist auf Fire TV /
Android TV nicht zwischengeschaltet.

## Grenzen

- Kein echtes Fire-TV-Gerät berührt (Owner-Regel).
- Kein Google-Support kontaktiert, kein APK an Google gesendet.
- Keine automatische Umgehung von Sicherheitsgates.
