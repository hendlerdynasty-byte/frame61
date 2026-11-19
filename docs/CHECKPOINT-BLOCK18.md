# CHECKPOINT — Block 18 / Endstand 19.11.2026

## Ziel

Frame61 als 10-Foot-TV-App (Fire TV/Android TV, Handy identisch), Blöcke 0–18.

## Quellstand

- `main` = `62e03ac` (Merge `block-18-docs`), davor `89d5fff` (Merge Block 18).
- Branches: `block-8` bis `block-18-docs` gepusht.
- Gate: `frame61-7edc58dce37b-1795044345-noblock`, Exit 0, **104/104 Tests**,
  Lint 0 Fehler/125 Warnungen, Debug-APK 34,1 MiB.
- Validator: 1000 Katalogeinträge, 1000 Meta-Snapshots, 33 Packs, 20 Fixtures,
  2 Hoster-Fixtures, 0 Fehler.

## Release 1.2.0

- APK: `~/hendler-dynasty/releases/frame61/1.2.0/frame61-1.2.0.apk`,
  SHA-256 `da4e501109163ff33aa708f37d8bdbc2516660ba84db24d3ab158cabfa0b02a6`.
- AAB: SHA-256 `74399d5c8a968f39078922ec99d6a42765af7210d48fa168b7800edaf379f382`.
- GitHub-Release `v1.2.0` mit APK/AAB/update.json.
- Pages-Manifest `https://hendlerdynasty-byte.github.io/frame61/update.json`
  HTTP 200 (Lauf `44479057163`).
- TV-Emulator: Update 1.1.0 → 1.2.0 durchlaufen, `versionCode=3`,
  `versionName=1.2.0`, `InstallSuccess`.

## Geprüfte Grenzen

- Index 10/14 verified, Hoster 2/10; Details in `docs/INDEX.md` und
  `docs/HOSTERS.md`. Keine Attrappen für tote Domains.
- Play Protect auf A52 extern blockiert; auf TV-Emulator komplett durchlaufen.
- Echtes Fire-TV-Gerät unangetastet. Kein Play-Store-Upload/Rollout.
- `build.lock` war stale: direkte Ausführung mit identischem Export in
  frischen Jobordnern, keine fremden Prozesse beendet (`docs/ENTSCHEIDUNGEN.md`).

## Nächster Befehl

```
ssh contabo-build 'uptime'
python3 ~/hendler-device-ops/remote_build.py \
  --repo "/Volumes/My Passport/Mac Download/HD | ZCode/frame61" --tasks "gate"
```

Voraussetzung: sauberer HEAD, kein laufender Fremdjob, Platz/Lease prüfen.
Für einen neuen Release `version.properties` erhöhen, Remote-Build ohne
Signing-Env, lokal mit `docs/SIGNING.md` signieren, Release/Pages
aktualisieren.
