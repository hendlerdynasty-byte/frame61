# RELEASE.md — Frame61 1.5.0 (Kandidat, signiert) / 1.3.0 (live)

Stand: 25.11.2026. Signierter Sideload-Release, kein Play-Store-Release.

## Kandidat 1.5.0 / VC6 — signiert, Veröffentlichung nach Holdout

| Artefakt | SHA-256 | Groesse |
|---|---|---|
| `frame61-1.5.0.apk` | `ee415ab9aa0eb09546c3dd8bf0fe40a7660c1c9b32ed5bfc311136055d45e720` | 19.963.388 B |
| `frame61-1.5.0.aab` | `39a36476edc075a7a2065c46d5650a5d195d1b5411d0234cfb19e87a8e2866bc` | 24.667.262 B |

- Quellstand `9d14350` (`main`, Block 9), VersionCode 6, versionName 1.5.0,
  Package `de.hendler.frame61`.
- Unsigned gebaut von Actions-Lauf `45582003989` (Gate **203/203 Tests**,
  Lint **0 Errors**, Job `release`), Artefakte
  `~/hendler-dynasty/evidence/frame61/1.5.0/release-9d14350/`
  (APK `7984a714…` 19.684.083 B, AAB `a9d88504…` 24.367.453 B).
- Signiert am Mac mit `scripts/sign_acceptance.sh` bzw.
  `scripts/sign_release_aab.sh`; Keystore `~/hendler-dynasty/keys/frame61-release.jks`,
  Passwort aus der Keychain (`frame61-keystore-pass`), kein Secret verlässt den
  Mac. Zertifikat `CN=Frame61, O=Hendler Dynasty, C=DE`,
  SHA-256 `1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52`
  (unverändert seit 1.1.0). `apksigner verify --print-certs` und
  `zipalign -c` bestätigt, `jarsigner -verify` → „jar verified".
- Ablage: `~/hendler-dynasty/releases/frame61/1.5.0/9d14350/`.
- Inhalt: Block 7 (Fokus-Vorwärmlauf), Block 8 (Staffel in der
  Episodenadresse), Block 9 (Serie ohne Nummer = Folge 1), DEVIDEOSRC-Kette,
  `aesgcm`-Pack-Aktion für Filemoon, `MediaUrlMatcher` ohne Dateiendung.
  Nachweise: [CHANGELOG.md](CHANGELOG.md), Quote: [QUOTA.md](QUOTA.md).
- **Noch nicht veröffentlicht:** GitHub-Release `v1.5.0` und `site/update.json`
  (versionCode 6) erst nach dem Holdout-Lauf (Seed `20261123`, Build
  `9d14350`) und dem 30-Minuten-Sessiontest. Owner-Regel: keine
  Veröffentlichung ohne belegte Holdout-Quote.

## Nicht veröffentlichte Vorstufe 1.4.0 / VC5

| Artefakt | SHA-256 | Groesse |
|---|---|---|
| `frame61-1.4.0.apk` | `93bf140f5734b5adfe58f28fa0959cf68d30db0bcc1cefe293876b873c8d16b8` | 20.033.020 B |
| `frame61-1.4.0.aab` | `5fead8868e148508eb507528bf961798ab75a572cf74d3e48f8b9e9be23c943f` | 24.706.504 B |

- Quellstand `312347a`, Actions-Lauf `45060048093`, VersionCode 5. Wurde nie
  veröffentlicht (Geräteabnahme fehlte); durch 1.5.0 überholt.

## Live: Release 1.3.0

| Artefakt | SHA-256 | Groesse |
|---|---|---|
| `frame61-1.3.0.apk` | `0be6e691c665f46a6e675b633dd62511d680ae88e2cc22160e8834fdb2bb0541` | 20.016.408 B |
| `frame61-1.3.0.aab` | `1044b3196d07f59c2b25831ea09269c82a0c9862a9ee7a6279b3e0d06c2995c6` | 24.693.080 B |

- Quellstand `9353e5a` (Merge Phase B auf `main`), VersionCode 4,
  versionName 1.3.0, Package `de.hendler.frame61`.
- Bau: Actions-Lauf `44989416861` (`android-gate`, Job `release`) liefert das
  unsignierte APK/AAB; signiert wird lokal mit dem Keystore ausserhalb des
  Repos (`scripts/sign_acceptance.sh`, `scripts/sign_release_aab.sh`).
  Kein Signing-Secret verlässt den Mac.
- Signatur: `CN=Frame61, O=Hendler Dynasty, C=DE`, Zertifikat-SHA-256
  `1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52`
  (unveraendert gegenueber 1.2.0, damit Updates ohne Datenverlust
  installieren). `zipalign -c` und `apksigner verify` bestaetigt,
  `jarsigner -verify` bestaetigt das AAB.
- GitHub-Release: https://github.com/hendlerdynasty-byte/frame61/releases/tag/v1.3.0
  Beide Assets sind veroeffentlicht; der APK-Asset-Hash wurde ueber HTTP
  nachgeladen und stimmt mit dem Manifest ueberein
  (`0be6e691…`, 20.016.408 B).
- Update-Manifest: https://hendlerdynasty-byte.github.io/frame61/update.json
  (`versionCode` 4, `versionName` 1.3.0, `apkUrl` auf das Release-Asset,
  `sha256` des APK). Pages-Lauf `44994210706` (Quellstand `c303a69`),
  Manifest live geprüft: HTTP 200, `versionCode` 4, SHA-256 des APK.
- Geraete: Release-APK auf dem Samsung A52 installiert (Device-Lab-Job
  `job-f147cb8b1d6848d4` PASS) und auf dem Android-TV-Emulator
  `frame61-androidtv-31` (`versionCode=4`, `versionName=1.3.0`, 0 Crash,
  0 ANR, D-Pad-Fokusweg in der linken Leiste).
- Gate auf dem Endstand von `main`: Actions-Lauf `44996700256` (Quellstand
  `ef4dd78`, nur Doku-Ergänzungen gegenüber `9353e5a`) — beide Jobs grün,
  121/121 Tests, Lint 0 Errors / 106 Warnings, Debug-APK 36,7 MiB.
- Ablage lokal: `~/hendler-dynasty/releases/frame61/1.3.0/`.

## Vorgaenger 1.2.0

| Artefakt | SHA-256 | Groesse |
|---|---|---|
| `frame61-1.2.0.apk` | `da4e501109163ff33aa708f37d8bdbc2516660ba84db24d3ab158cabfa0b02a6` | 17.706.190 B |
| `frame61-1.2.0.aab` | `74399d5c8a968f39078922ec99d6a42765af7210d48fa168b7800edaf379f382` | 22.327.841 B |

- Quellstand `89d5fff`, VersionCode 3, unsigned Remote-Build
  `frame61-7edc58dce37b-1795045475-release-noblock`; Signatur lokal mit dem
  Keystore außerhalb des Repos.
- GitHub-Release: https://github.com/hendlerdynasty-byte/frame61/releases/tag/v1.2.0
- Update-Manifest: https://hendlerdynasty-byte.github.io/frame61/update.json
  (Pages-Lauf `44479057163`, HTTP 200).
- Update-Livepfad TV: 1.1.0 → „Version 3" → Download → SHA-256 →
  Installer → `versionCode=3`. Belege: `docs/design/block18/`.
- Direkte Installation aus dem Release-Asset auf dem TV-Emulator:
  `versionCode=3`, `versionName=1.2.0`.

## Vorgaenger 1.1.0

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

Update auf dem Android-TV-Emulator (Block 17): signiertes 1.0.0 installiert,
Manifest lieferte „Version 2 verfügbar", System-Installer bestätigt,
anschließend `versionCode=2`/`versionName=1.1.0` und `InstallSuccess`.
Belege: `docs/design/block17/update-available.png`, `installer-ready.png`,
`unknown-settings.png`, `update-done.png`, `update-opened.png`;
Details in `docs/PLAYPROTECT.md`.

## Grenzen

- Kein Play-Store-Upload, kein Rollout, keine Store-Freigabe.
- AAB ist jarsigner-signiert, aber nicht bei Google eingereicht.
- `perf`-APK (R8-Messvariante) ist debug-signiert und nicht Teil des Releases.
- Die Update-Bestaetigung ist auf Geraeten mit aktivem Play Protect manuell
  bzw. per ADB zu umgehen; das ist eine externe Google-Schranke.
