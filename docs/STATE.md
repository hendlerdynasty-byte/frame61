# STATE.md — Frame61 Status

Stand: 26.11.2026 (Schritt 0 und Schritt 1 abgeschlossen, Schritt 2 als
nächstes). Quellstand `1fc5385` auf `main`; letzter grüner CI-Lauf
`45891187615` (280/280 Tests, Lint 0 Errors / 109 Warnings / 1 Information).
Aktueller Bericht: [SCHRITT1-ANIWORLD.md](SCHRITT1-ANIWORLD.md). Historie 1.5.0:
[ABSCHLUSS-1.5.0.md](ABSCHLUSS-1.5.0.md), [RELEASE.md](RELEASE.md).

## Kurzfassung — Gate 1 (1.6.0)

- **Live bleibt 1.5.0/VC6** (`ee415ab9…` APK, Update-Kanal versionCode 6).
  Kein neuer Release, kein Play-Store-Upload.
- **Identitätsprüfung gebaut:** `ContentIdentity` (Titel-Dice ≥0.75, Trailer,
  Episode, Laufzeit 10/15 %, Fortsetzungsziffern) + `ContentIdentityGate`
  (Seitentitel-Abruf gecacht); `MirrorCandidate` trägt Quellenangaben.
- **Beispielvideo entfernt:** `CatalogPlayback` liefert keinen Mux-Demo-Stream
  mehr; ohne bestätigte Quelle zeigt der Player eine Fehlermeldung.
- **Overlay:** Titel + Laufzeit dauerhaft im Player, Trailer-Hinweis sichtbar.
- **Messung erweitert:** Harness schreibt `QUOTA IDENT` je Titel und
  `QUOTA RIGHT SUMMARY/GROUPEN` (Richtigkeit getrennt von Spielbarkeit).
- **Baseline (A52, `2382520`, Training Seed 20261122): 2/100 richtig; 11/100
  gespielt, davon 9 falsch** (`WRONG_CONTENT`, 82 % der Plays). Ursache belegt:
  ARD-Erst-Treffer-Fallback + alle Packs parallel → fremde ARD/ZDF-Videos für
  serienstream/aniworld-Titel (`score=0.50`, `*.akamaihd.net`).
- **Fix (`c953d3f`):** eigene Quellengruppe wird zuerst abgefragt; ARD nimmt
  nur noch einen Treffer mit passendem Titel (≥0.75). CI grün, Acceptance-APK
  `8aa1c909…` auf dem A52 installiert (`job-d980de1ac0b34b22` PASS).
- **Schritt 0 gültig gemessen (26.11.2026):** Nach DNS-Erholung (HTTP 200
  vom A52) lief `c953d3f` auf derselben Trainingsstichprobe: **20/100 richtig**
  vs. Baseline 2/100 (**+18 pp**), 26/100 gespielt, 6 falsch. aniworld ist die
  stärkste Gruppe: **16/19 richtig (84 %)**, keine Falschwiedergabe; ard 2/10,
  serienstream 1/43, bsto 1/6, filmpalast/moflix 0. Rohbelege:
  `~/hendler-dynasty/evidence/frame61/1.6.0/step0/measure-polls/latest.xml`.
- **Schritt 1 abgeschlossen (26.11.2026):** aniworld-Tiefenpfad **20/20 PASS**
  auf dem A52 (Build `c70a9ac`, CI `45886687367`). Alle 20 Titel: lokale
  Poster/Backdrops, gefüllte Metadaten, Episoden, `sourceId=aniworld`,
  `identity=CORRECT` Score 1.00, erstes Bild, wachsende Position, Screenshot.
  Surface-Screenshots per D-Pad-Betrachter auf Mac übertragen und gesichtet
  (`step1/aniworld20/F6-shots/`, `F6-contact.jpg`). `aniworld.json` jetzt
  `verified:true` (2026-11-26T20:23:16Z), PACKS.md regeneriert, Validator
  0 Fehler / 0 Warnungen. Beleg: `step1/aniworld20/polls9/latest.xml`.

## Nächster Schritt — Schritt 2 (Katalog-Wahrheit)

- Nachweis-Tabelle für jeden sichtbaren Katalogeintrag anbinden; nur Titel mit
  belegtem Stream in Rails/Suche/Detail. Die 20 aniworld-Pfade sind der erste
  belegte Block. Danach weitere Quellengruppen nach demselben Muster.
- Bestehende offene Punkte: 7 Hoster + 4 Indexseiten, HDR, TV/Fire TV,
  Netzpfad-Sessiontest/Performance. Kein Release 1.6.0, bevor Holdout-Richtigkeit
  ≥95 % und Gate 6 erfüllt sind.

## Historie 1.5.0 / VC6 (veröffentlicht)

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
