# AUDIT — Frame61 Android TV

Stand: 2026-11-17 (Block 8). Ersetzt die Fassung vom 16.11. (dort: Room v1,
81 Tests, 85 Dateien). Maßgeblich sind die Gate-Artefakte.

## A. Build-Gate

| Task | Status | Beleg |
|---|---|---|
| `:app:assembleDebug` | GRÜN | Block-8-Gate, exit 0 |
| `:app:lint` | GRÜN | 0 Errors |
| Unit-Tests (10 Module) | GRÜN | **93/93**, 0 Failures/Errors/Skipped |
| Asset-Validator | GRÜN | 26 Packs, 10 Hoster, 12 Skins, 46 Meta, 2 Gold |
| Fetch-Tests | GRÜN | CSS-Matcher (4) |
| PACKS-Abgleich | GRÜN | `tools/packs_report.py --check` |
| APK-Größe | GRÜN | ≤ 45 MiB (Wert in ABSCHLUSS.md) |

## B. Module (91 Kotlin-Dateien)

| Modul | Inhalt | Tests |
|---|---|---|
| `:app` | Screens, Nav, DI, Updater, ARD-Resolver, Info | 25 |
| `:core:design` | Skins, Theme, Fokus | 5 |
| `:core:data` | Room **v2**, DataStore, Resume, PIN | 12 |
| `:core:meta` | Katalog, Snapshots, Suche, Episoden | 16 |
| `:core:net` | OkHttp, Jsoup, Health | 0 |
| `:player` | Media3, Countdown, Schwellen | 8 |
| `:recs` | RecommendationEngine | 0 |
| `:scraper:engine` | PackLoader/Interpreter/Fixture | 6 |
| `:scraper:resolver` | HosterResolver, MirrorSelector, Watchdog | 21 |
| `:sources:free` | Platzhalter | 0 |

## C. Quellen und Wahrheitsstatus

| Gruppe | Anzahl | Status |
|---|---|---|
| Live verifiziert | **2** | `ard-mediathek` (Suche/Detail/Folge/Stream + Gerät), `archive-org` (API) |
| Legal-frei ohne Live-Beleg | 10 | `verified:false`, Fixtures synthetisch oder kein Ziel |
| Grauzone Tier A/B | 14 | `verified:false`, Attrappen, kein HTML-Beleg |
| Hoster | 10 | 9 alte + `direct`; alle `verified:false` (Hoster-Live nicht geführt) |

Details und Begründung je Pack: `docs/PACKS.md`.

## D. Datenbank

Room **Version 2**, `exportSchema = true`, 6 Entities. Migration 1→2:
Resume-Index `(profileId,titleId,episodeId)` statt `(profileId,titleId)`.
Default-Profil wird lazy angelegt (FK-Fehler behoben). Migration lief real
beim Update über die v1-App.

## E. Player

Media3 1.6.0 inkl. `media3-exoplayer-hls`; HLS/MP4, Spurwahl, Tempo,
Watchdog (Fortschritt statt Ticker), Direktstream-Hoster (`copy`), sichtbares
Quellenlabel („ARD Mediathek"/„Demo-Stream"). ARD-Slice am A52 belegt.

## F. Metadaten

46 Offline-Snapshots + Index; Client ohne Metadaten-Live-Call; Attribution im
Info-Screen. Fetch-Werkzeug und Cron in `tools/fetch/`, `docs/FETCH.md`.

## G. Offene Punkte

1. Release-Signing (Keystore) — BLOCKED_EXTERNAL.
2. `update.json`-Livepfad — NOT_RUN.
3. 24 Packs ohne Live-Beleg — bewusst `verified:false`.
4. M6 (TMDB/Badges/Trakt) — Flag aus, Stubs.
5. RGB_565 — ohne eigenen Decoder nicht umgesetzt.
6. Fire TV — unangetastet (Owner-Policy).
7. Performance — Debug-Build; 95th-Percentile 150 ms, ~20 % Jank.
