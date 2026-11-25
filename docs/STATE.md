# STATE.md — Frame61 Status

Stand: 25.11.2026 (Release 1.5.0 / VersionCode 6, signiert, Veröffentlichung
nach Holdout). Quellstand `9d14350` auf `main`. Berichte:
[CHANGELOG.md](CHANGELOG.md) (Blöcke 7–9 + Messläufe),
[QUOTA.md](QUOTA.md) (Quote je Lauf), [RELEASE.md](RELEASE.md) (Artefakte),
[ABSCHLUSS-PHASE-B.md](ABSCHLUSS-PHASE-B.md) / [ABSCHLUSS-TV.md](ABSCHLUSS-TV.md)
(Historie), [ABSCHLUSS.md](ABSCHLUSS.md).

## Kurzfassung

- **Release:** signierter Sideload-Release **1.5.0 / VC6** aus Quellstand
  `9d14350` — APK sha256 `ee415ab9aa0eb09546c3dd8bf0fe40a7660c1c9b32ed5bfc311136055d45e720`
  (19.963.388 B), AAB sha256
  `39a36476edc075a7a2065c46d5650a5d195d1b5411d0234cfb19e87a8e2866bc`
  (24.667.262 B), Zertifikat `1201f46d…` (unverändert). Kein Play-Store-Upload.
- **Gate:** Actions-Lauf `45582003989` auf `9d14350` grün — **203/203 Tests**,
  Lint **0 Errors / 109 Warnings**, Release-Job baut APK + AAB, Device-Lab
  `QUICK_SMOKE` der Acceptance-APK PASS (`job-d850fe42ccf74c6b`,
  APK sha256 `6500069c…`, Quellstand `9d143509…`).
- **Quote (A52, Build `9d14350`):** Lauf 11 **Training** (Seed `20261122`)
  **67/100 (67 %)**, Fast 66/100; **Holdout** (Seed `20261123`, disjunkt)
  **70/100 (70 %)**, Fast 68/100. Die Holdout-Quote liegt über der
  Trainings-Quote — keine Überanpassung. Damit ist die
  Veröffentlichungsregel erfüllt ([QUOTA.md](QUOTA.md)).
- **Veröffentlicht:** GitHub-Release `v1.5.0` (APK `ee415ab9…`, AAB
  `39a36476…`, beide Assets `uploaded`, APK per HTTP nachgeladen und
  hash-identisch) und Update-Manifest live (`versionCode` 6, HTTP 200,
  Pages-Lauf `45676076132`). Release-APK am A52 installiert und gestartet
  (Device-Lab `QUICK_SMOKE` `job-d9ec706e61d74b14` PASS).
- **Sessiontest:** `SESSION PASS titles=50 resumeJumps=20 profileChanges=10
  errors=0` (30,2 min, fünf Container, lokale Assets) — kein Crash, kein ANR.
- **Block 9 (A52-belegt):** Serie ohne Episodennummer wird als Folge 1
  aufgelöst — gemessen: `episode-0` → HTTP 404 (2516 Bytes, 0 Streams),
  `episode-1` → HTTP 200 (176859 Bytes, 4 Streams).
- **Block 7 (A52-belegt, A/B):** Fokus-Vorwärmlauf liefert Kandidaten und der
  Player startet daraus (`vorwaermlauf=ja`), aber die Zeit bis zum ersten Bild
  war in zwei Paaren **nicht kürzer** als der Kaltstart (7551→1412 ms,
  3953→2776 ms). Details und Messbedingungen:
  [QUOTA.md](QUOTA.md) → „Block 7 — Gerätemessung Vorwaermlauf".
- **Quellen:** 31 Source-Packs, **12 live verifiziert** ([PACKS.md](PACKS.md),
  per `tools/packs_report.py --check` aktuell); 14 Indexseiten: 10 verifiziert,
  **9/14 auf dem A52 erreichbar** ([INDEX.md](INDEX.md)); Hoster: **3/10**
  Kette-verifiziert (vidmoly, filemoon, direct), davon **2 auf dem A52
  abgespielt** ([HOSTERS.md](HOSTERS.md)).
- **TV:** D-Pad-Pfad belegt (Phase B); echtes Fire TV bleibt **NOT_RUN**
  (Gerät unangetastet).
- **Performance:** letzte Messung am signierten Release 1.3.0 (Kaltstart p95
  334 ms, Scroll p95 22 ms, Janky 3,01 %) — für 1.5.0 **nicht erneut gemessen**
  (STALE, kein neuer Anlass).
- **Offen:** Lauf 11-Auswertung, Holdout-Lauf (Seed 20261123), 30-Minuten-
  Sessiontest mit `9d14350`, Veröffentlichung 1.5.0/VC6, Fire TV, HDR-Nachweis
  mangels HDR-Gerät.

## Artefakt 1.5.0 / VC6

| Artefakt | SHA-256 | Größe |
|---|---|---|
| `frame61-1.5.0.apk` | `ee415ab9aa0eb09546c3dd8bf0fe40a7660c1c9b32ed5bfc311136055d45e720` | 19.963.388 B |
| `frame61-1.5.0.aab` | `39a36476edc075a7a2065c46d5650a5d195d1b5411d0234cfb19e87a8e2866bc` | 24.667.262 B |

Ablage: `~/hendler-dynasty/releases/frame61/1.5.0/9d14350/` (APK + AAB),
CI-Evidenz (unsigned) `~/hendler-dynasty/evidence/frame61/1.5.0/release-9d14350/`.
Signatur lokal am Mac (`scripts/sign_acceptance.sh` / `sign_release_aab.sh`,
Keystore außerhalb des Repos, kein Secret-Transfer): `apksigner verify
--print-certs` → `CN=Frame61, O=Hendler Dynasty, C=DE`, Zertifikat-SHA-256
`1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52`;
`jarsigner -verify` → „jar verified".

## Bauen

```sh
scripts/preflight.sh
# Linux-kompatible Builds laufen über GitHub Actions (android-gate.yml):
gh workflow run android-gate.yml --ref main
# Signierung bleibt lokal am Mac (kein Secret-Transfer):
scripts/sign_acceptance.sh <unsigned.apk> <signed.apk>
scripts/sign_release_aab.sh <unsigned.aab> <signed.aab>
```

Lokale Heavy-Builds bleiben per Owner-Policy gesperrt. Gerätetests nur über
die zentrale Device-Lab-Lease (A52 `RZ8T703XJWF`), kein Emulator, kein
WLAN-ADB, kein Fire TV.
