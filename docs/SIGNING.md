# SIGNING.md — Release-Signierung

Stand: 2026-11-17. **Es liegt kein Release-Keystore im Repo.** Das ist Absicht:
keine Secrets im Repository, keine Übertragung ohne konkrete Autorisierung.

## Ist-Stand

- Debug-Builds nutzen den Standard-Debug-Keystore (`signingConfig = debug`).
- `app/build.gradle.kts` hat eine `release`-Signing-Config, die auf
  `../frame61.jks` zeigt und Passwörter aus Umgebungsvariablen liest:
  - `FRAME61_STORE_PASS`
  - `FRAME61_KEY_ALIAS` (Default `frame61`)
  - `FRAME61_KEY_PASS`
- `frame61.jks` existiert nicht im Repo → `:app:assembleRelease` ist ohne
  Keystore nicht lauffähig. Der Keystore liegt **außerhalb** des Repos; siehe
  „Erzeugter Keystore".

## Erzeugter Keystore (17.11.2026)

| Feld | Wert |
|---|---|
| Datei | `~/hendler-dynasty/keys/frame61-release.jks` (mode 600) |
| Alias | `frame61` |
| Algorithmus | RSA 4096, Gültigkeit 10000 Tage (bis 2054-04-04) |
| SHA-256 (Datei) | `31f5cd50bf6074e4e74de57793f20b97e4ccafb1bd96366086f6090e20ca3b08` |
| Zertifikat-SHA-256 | `12:01:F4:6D:50:D1:67:AB:10:71:2D:AD:BE:64:75:1A:F8:9B:F9:D6:1F:12:07:67:2D:1B:0C:E0:6D:EC:4C:52` |
| Base64-Backup | `~/hendler-dynasty/keys/frame61-release.jks.b64` (mode 600), SHA-256 `bec249b421241c8570f103a64cca883c68cf167202c4385f24be1e6b0b7c775e` |
| Passwort | macOS Keychain, Service `frame61-keystore-pass`, Account `frame61` (Fallback-Datei mode 600 außerhalb des Repos) |

Das Base64-Backup steht **nicht** in dieser Datei: Keystore und Passwort sind
Zugangsdaten und gehören nicht in ein Repository (auch kein privates). Pfad,
Hashes und Fingerprint oben sind die überprüfbaren Belege.

GitHub-Actions-Secrets sind gesetzt: `FRAME61_KEYSTORE_BASE64`,
`FRAME61_STORE_PASS`, `FRAME61_KEY_PASS`, `FRAME61_KEY_ALIAS`.

## Keystore erzeugen (einmalig, offline, nicht committen)

```sh
keytool -genkeypair -v \
  -keystore frame61.jks \
  -alias frame61 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Frame61, O=Hendler Dynasty, C=DE"
```

- Datei **außerhalb** des Repos oder in einem ignorierten Pfad ablegen.
- Passwörter in einem Passwortmanager, nie in Git, Chat oder Logs.
- Backup an zwei getrennten Orten; Verlust = keine Updates mehr möglich.
- `keytool -list -v -keystore frame61.jks` liefert den SHA-256-Fingerprint für
  die Dokumentation.

## Release bauen (seit 18.11.2026)

- Version aus `version.properties` (`versionCode`, `versionName`).
- Remote-Build ohne Signing-Env: `:app:assembleRelease :app:bundleRelease`
  erzeugt `release-app-release-unsigned.apk` und `release-app-release.aab`.
- Lokal signieren (Secrets bleiben auf dem Mac):

```sh
zipalign -p 4 <unsigned.apk> <aligned.apk>
apksigner sign --ks ~/hendler-dynasty/keys/frame61-release.jks \
  --ks-key-alias frame61 --ks-pass env:KS_PASS --key-pass env:KS_PASS \
  --out frame61-1.1.0.apk <aligned.apk>
jarsigner -keystore ~/hendler-dynasty/keys/frame61-release.jks \
  -storepass:env KS_PASS -keypass:env KS_PASS \
  -sigalg SHA256withRSA -digestalg SHA-256 <unsigned.aab> frame61
```

- 1.1.0: APK `1c902e37…`, AAB `f2a198df…`, siehe `docs/RELEASE.md`.
- `apksigner verify --print-certs` muss den Fingerprint
  `12:01:F4:…:4C:52` zeigen.
- Signing-Secrets werden **nicht** auf den Bauhost übertragen.
- Ein signiertes AAB ist kein Store-Nachweis; Upload/Rollout sind eigene
  Freigaben.

## Prüfen

```sh
jarsigner -verify -verbose -certs app-release.aab
apksigner verify --print-certs app-release.apk
```

Fingerprint und Artefakt-Hash in `docs/STATE.md`/`docs/ABSCHLUSS.md` eintragen.
