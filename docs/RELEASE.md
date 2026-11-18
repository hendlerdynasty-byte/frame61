# RELEASE.md — Frame61 1.1.0

Stand: 18.11.2026. Signierter Sideload-Release, kein Play-Store-Release.

## Artefakte

| Artefakt | Datei | SHA-256 | Groesse |
|---|---|---|---|
| Release-APK 1.1.0 | `frame61-1.1.0.apk` | `1c902e3738d5a7c1667e09c4e238cddf88930d9eb065753896b523bd94503726` | 13.528.252 B |
| Release-AAB 1.1.0 | `frame61-1.1.0.aab` | `f2a198df8ef66a94127345f53822113af69b974601ef01a2b69c4f0c2b8a1b86` | 18.134.266 B |
| Altstand 1.0.0 APK | `frame61-1.0.0.apk` | `a00727a23a969c378fafb33ee6d11a2101d721cd5f1e67b360ec4b5017e88d98` | 13.5 MB |
| Altstand 1.0.0 AAB | `frame61-1.0.0.aab` | `7cb29a1f727cb5f0c8027a4b18b73a69d391d99b3d72ebcbb9cbf04b55985734` | 17 MB |

Ablage lokal: `~/hendler-dynasty/releases/frame61/<version>/`.

- Quellstand 1.1.0: `24a1198` (Merge Block 12), Release-Build aus
  `f60ce80`/`9dd0752`-Linie; Remote-Job `frame61-9dd07526c9cb-1795003748`.
- Quellstand 1.0.0: Remote-Job `frame61-504afffeffc3-1795002501`.
- Signatur: `CN=Frame61, O=Hendler Dynasty, C=DE`, Zertifikat-SHA-256
  `12:01:F4:6D:50:D1:67:AB:10:71:2D:AD:BE:64:75:1A:F8:9B:F9:D6:1F:12:07:67:2D:1B:0C:E0:6D:EC:4C:52`.
- Keystore: `~/hendler-dynasty/keys/frame61-release.jks` (außerhalb des Repos).
- Bau: Remote ohne Signing-Env → unsigned Release; lokale Signatur mit
  `zipalign` + `apksigner` (APK) bzw. `jarsigner` (AAB). Keine
  Signing-Secrets auf dem Bauhost.

## Veroeffentlichung

- GitHub-Release: https://github.com/hendlerdynasty-byte/frame61/releases/tag/v1.1.0
- APK-Asset: https://github.com/hendlerdynasty-byte/frame61/releases/download/v1.1.0/frame61-1.1.0.apk
- Update-Manifest (Pages): https://hendlerdynasty-byte.github.io/frame61/update.json
- Pages-Lauf `44308280114`: completed/success; Manifest HTTP 200.
- Remote-Hash der Release-APK entspricht dem lokalen Hash
  (`1c902e37…`, per `curl | shasum -a 256` geprueft).

## Update-Livepfad (A52)

1. Signiertes 1.0.0 (versionCode 1) installiert, `versionName=1.0.0`,
   `versionCode=1` per `dumpsys package` belegt.
2. Einstellungen → Update-Quelle: Default
   `https://hendlerdynasty-byte.github.io/frame61/update.json`.
3. „Jetzt pruefen“ → **„Version 2 verfuegbar“**.
4. „Installieren“ → Download, SHA-256-Pruefung, System-Installer geoeffnet
   („Moechtest du diese App aktualisieren?“).
5. Google Play Protect blockierte die Bestaetigung mit „App-Scan empfohlen“
   („Play Protect kennt diese App noch nicht“). Kein Scan an Google gesendet,
   keine Google-Supportaktion.
6. Die Release-APK 1.1.0 wurde aus dem veroeffentlichten Release-Asset per
   `adb install -r` installiert; danach `versionCode=2`, `versionName=1.1.0`,
   App startet mit lokalen Postern.

Damit ist der Pfad Manifest → Pruefung → Download → SHA → Installer live
belegt. Die letzte Bestaetigung im System-Installer ist durch Play Protect
extern blockiert; kein automatischer Bypass, keine Google-Kontaktaufnahme.

## Startnachweis

`docs/design/block12/release-home.png`: Release 1.1.0 auf dem A52, Startseite
mit Stranger-Things-Hero, lokalen Postern und Sechs-Punkte-Navigation.

Update-Belege:
`docs/design/block12/check3.png` („Version 2 verfügbar“),
`docs/design/block12/installer2.png` (Systemdialog „Möchtest du diese App
aktualisieren?“), `docs/design/block12/installer.png` (Play-Protect-Gate),
`docs/design/block12/scroll/11.png` (Update-Abschnitt mit Default-URL).

## Grenzen

- Kein Play-Store-Upload, kein Rollout, keine Store-Freigabe.
- AAB ist jarsigner-signiert, aber nicht bei Google eingereicht.
- `perf`-APK (R8-Messvariante) ist debug-signiert und nicht Teil des Releases.
- Die Update-Bestaetigung ist auf Geraeten mit aktivem Play Protect manuell
  bzw. per ADB zu umgehen; das ist eine externe Google-Schranke.
