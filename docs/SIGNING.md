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
- `frame61.jks` existiert nicht → `:app:assembleRelease` ist ohne Keystore
  nicht lauffähig. Das ist ein **BLOCKED_EXTERNAL**, kein Buildfehler.

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

## Release bauen

```sh
export FRAME61_STORE_PASS='…'
export FRAME61_KEY_ALIAS='frame61'
export FRAME61_KEY_PASS='…'
python3 ~/hendler-device-ops/remote_build.py \
  --repo "<frame61-Pfad>" --target aab \
  --tasks ":app:bundleRelease"
```

- Signing-Secrets dürfen **nur** nach ausdrücklicher Owner-Freigabe auf den
  Bauhost übertragen werden; der Remote-Build schließt Signing-Dateien beim
  Export bewusst aus.
- Der Debug-Gate bleibt davon unberührt: `./gradlew gate` baut und prüft nur
  Debug.
- Ein signiertes AAB ist kein Store-Nachweis; Upload/Rollout sind eigene
  Freigaben.

## Prüfen

```sh
jarsigner -verify -verbose -certs app-release.aab
apksigner verify --print-certs app-release.apk
```

Fingerprint und Artefakt-Hash in `docs/STATE.md`/`docs/ABSCHLUSS.md` eintragen.
