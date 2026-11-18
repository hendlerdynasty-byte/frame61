# CHECKPOINT — Block 12 / Endstand 18.11.2026

## Ziel

Frame61 als 10-Foot-TV-App (Fire TV/Android TV, Handy identisch), Blöcke 0–12.

## Quellstand

- `main` = `a0bfb3f` (Merge `block-12-fix`), davor `abe3982`/`24a1198`.
- Branches: `block-8`, `block-9`, `block-10`, `block-11`, `block-12`,
  `block-12-docs`, `block-12-fix` gepusht.
- Gate: `frame61-b3a91c5ff53d-1795008028`, Exit 0, 95/95 Tests,
  Lint 0 Fehler/128 Warnungen, APK 30,1 MiB.

## Release

- Version 1.1.0 / versionCode 2.
- Release-APK: `~/hendler-dynasty/releases/frame61/1.1.0/frame61-1.1.0.apk`,
  SHA-256 `1c902e3738d5a7c1667e09c4e238cddf88930d9eb065753896b523bd94503726`.
- Release-AAB: SHA-256 `f2a198df8ef66a94127345f53822113af69b974601ef01a2b69c4f0c2b8a1b86`.
- GitHub-Release: `v1.1.0` mit APK/AAB/update.json.
- Pages-Manifest:
  `https://hendlerdynasty-byte.github.io/frame61/update.json` (HTTP 200).
- A52: 1.1.0 installiert (`versionCode=2`), Startseite mit lokalen Postern.

## Geprüfte Grenzen

- Index 6/14 verified, Hoster 1/10; Details in `docs/INDEX.md`,
  `docs/HOSTERS.md`. Keine Attrappen für tote Domains.
- Play Protect blockiert die letzte Update-Bestätigung; Installation aus dem
  Release-Asset per ADB belegt.
- Fire TV unangetastet. Kein Play-Store-Upload/Rollout.
- Perf-Zahlen: Kaltstart p95 323 ms, Scroll-p95 24 ms, Jank 3,71 %
  (`docs/PERF2.md`).

## Nächster Befehl

```
ssh contabo-build 'uptime'
python3 ~/hendler-device-ops/remote_build.py \
  --repo "/Volumes/My Passport/Mac Download/HD | ZCode/frame61" --tasks "gate"
```

Voraussetzung: sauberer HEAD, kein laufender Fremdjob, genug Platz auf dem
Bauhost. Für einen neuen Release `version.properties` erhöhen, Remote-Build
ohne Signing-Env, lokal mit `docs/SIGNING.md` signieren, Release/Pages
aktualisieren.
