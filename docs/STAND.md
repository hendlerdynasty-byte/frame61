# STAND.md — Frame61 Endstand

Stand: 2026-11-17 · Branch `block-8` · Basis-HEAD `2d3acc9`
· Diese Datei ist der Endstand nach den Blöcken 1–8. Gate-Zahlen und
Artefakt-Hashes stehen in [ABSCHLUSS.md](ABSCHLUSS.md); die Block-1-Fassung
dieser Datei ist damit überholt.

---

## 1. Inventur (gemessen 17.11.2026)

| Kennzahl | Wert |
|---|---|
| Kotlin-Dateien | **91** |
| `@Test`-Methoden | **93** |
| Gradle-Module | **10** |
| Source-Packs | **26** (14 Grauzone A/B, 12 legal-frei) |
| Hoster-Packs | **10** (9 Hoster + `direct`) |
| Skins | **12** |
| Katalogtitel | **46** |
| Metadaten-Snapshots | **46** + `index.json` |
| Fixture-Serien | **10** (2 recorded: archive-org, ard-mediathek; 8 synthetic) |
| `!!`-Operator | **0** |

### Module

| Modul | Inhalt | Tests |
|---|---|---|
| `:app` | Screens, Navigation, DI, Updater, ARD-Resolver, Info | 25 |
| `:core:design` | Skins, Theme, Fokus | 5 |
| `:core:data` | Room **v2**, DataStore, Resume, PIN | 12 |
| `:core:meta` | Katalog, Offline-Snapshots, Suche, Episoden | 16 |
| `:core:net` | OkHttp, Jsoup, Health | 0 |
| `:player` | Media3, Countdown, Resume-Schwellen | 8 |
| `:recs` | RecommendationEngine (nicht angebunden) | 0 |
| `:scraper:engine` | PackLoader/Interpreter/Fixture/SourceRegistry | 6 |
| `:scraper:resolver` | HosterResolver (inkl. `copy`), MirrorSelector, Watchdog | 21 |
| `:sources:free` | Platzhalter | 0 |

Testzahlen je Modul sind aus den letzten Gate-Läufen; die Gesamtsumme 93 ist
der letzte gemessene Stand (Block 6). Der Block-8-Gate bestätigt sie.

---

## 2. Gate

`scripts/preflight.sh` (schnell, offline) prüft JDK/SDK, Assets, Doku,
`!!`-Verbot, API-Key-Verdacht, Pack-Validator, Fetch-Tests und PACKS.md.

`./gradlew gate` bündelt:

- `:app:assembleDebug`
- `:app:lint`
- `testDebugUnitTest` aller 10 Module
- `validatePacks` (Source/Hoster/Skin/Katalog/Meta/Gold-Fixtures)
- `testFetchTools` (CSS-Matcher)
- `packsReport --check` (docs/PACKS.md aktuell)
- `checkApkSize` (≤ 45 MiB)

Lokale Heavy-Builds sind per Owner-Policy gesperrt; der Gate-Lauf läuft über
`remote_build.py` auf `contabo-build`.

---

## 3. Was belegt ist

| Bereich | Beleg |
|---|---|
| Build | Block-8-Gate: siehe ABSCHLUSS (exit 0) |
| Gerät | A52: Start, Navigation, Detail, Folgen, Player, Suche, Skins, Source Lab, Profile, Optionen, Info (Screenshots/UI-Dumps) |
| ARD live | Babylon Berlin S01/E01, Label „ARD Mediathek", `0:10 / 44:55`, Video |
| Suche | Suchfeld + IME/Button; Treffer „Babylon Berlin" |
| Metadaten offline | 46 Snapshots, Info-Screen: „46 Titel, davon 30 live" (Stand 17.11.) |
| Archiv.org | Pack live verifiziert, recorded Fixture |
| Room | Migration 1→2 real beim Update über die v1-App ausgeführt; 93 Tests |
| Performance | Kaltstart-Median 2051 ms, PSS 154 MiB, Scroll-p90 65 ms (Debug) |
| Signing | Anleitung + Config; Keystore fehlt (BLOCKED_EXTERNAL) |
| Updater | Parser-Tests; kein echtes `update.json` ausgeliefert (NOT_RUN) |

---

## 4. DoD-Liste (27 Punkte)

| # | Punkt | Status |
|---|---|---|
| 1 | Build grün auf frischem Klon | ✅ Block-8-Gate |
| 2 | kein `!!` | ✅ 0 Vorkommen, Preflight prüft |
| 3 | Versionen gepinnt | ✅ Version Catalog + DEPS |
| 4 | Room migrierbar | ✅ v2 + Migration 1→2, real ausgeführt |
| 5 | PackLoader verwirft kaputte Packs | ✅ Tests |
| 6 | Fixture-Runner grün | ✅ 10 Serien |
| 7 | Source Lab importiert/exportiert | ✅ Code; URL-/Dateiimport; Live nicht erneut getestet |
| 8 | 6 legal-freie Quellen mit Fixture | ✅ 12 legal-frei, 10 Fixtures |
| 9 | HosterResolver mit WebView-Captcha | ✅ Code; `copy` für Direktstreams; Gerät nur ARD/direkt |
| 10 | Health-Scoring aktiv | ✅ Tests |
| 11 | Resume exakt | ✅ Code + Migration; Gerät: Position sichtbar, Kill-Test nicht wiederholt |
| 12 | Episoden-Navigation | ✅ am A52 (Babylon Berlin S01/E01 → Player) |
| 13 | Autoplay-Countdown | ✅ Logik-Test |
| 14 | 90 % = watched | ✅ Schwellen-Test |
| 15 | Mirror-Failover ohne Flackern | ✅ Code/Test; kein Mehr-Mirror-Gerätelauf |
| 16 | 11 Skins ohne Codeänderung | ✅ 12 Dateien |
| 17 | Layout-Builder persistiert | ✅ DataStore-Test |
| 18 | D-Pad ohne Fokus-Loch | ✅ P3 am A52; Touch-Flows ebenfalls |
| 19 | Handy nutzbar | ✅ A52-Touch-Flows |
| 20 | Fokusring sichtbar | ✅ P3 am A52 |
| 21 | PIN setzen/ändern/entfernen | ✅ Code/Tests; Gerät nicht erneut |
| 22 | getrennte Profil-Namespaces | ✅ Tests + DB-Fix |
| 23 | signiertes Release-APK | ❌ BLOCKED_EXTERNAL (Keystore) |
| 24 | Updater | ✅ Parser; Livepfad NOT_RUN |
| 25 | Leanback | ✅ Manifest |
| 26 | Sideload-Doku | ✅ README + UPDATE.md |
| 27 | README/SCRAPER/SKINS/STATE aktuell | ✅ diese Runde |

**M0–M5:** ✅ · **M6:** Flag `frame61.m6=false`, Funktionen Stubs · **M7:**
Updater/Leanback/Sideload ✅, Performance gemessen, Offline-Prewarm/Paging
nicht umgesetzt.

---

## 5. Offen (ehrlich)

1. **Release-Signing:** Keystore fehlt → `docs/SIGNING.md`; Owner muss Schlüssel
   erzeugen/bereitstellen.
2. **Update-Livepfad:** kein echtes Manifest/APK-Host → `docs/UPDATE.md`.
3. **Packs ohne Beleg:** 24 von 26 `verified:false` (14 Grauzone, 10 legal-frei
   mit synthetischen Fixtures bzw. ohne Ziel). Status in `docs/PACKS.md`.
4. **ARD-API:** inoffiziell, geo-blockiert möglich; nur erste Staffel als
   Folgen (widgets[0]); keine offizielle Zusage.
5. **M6:** TMDB/Badges/Trakt nur Stubs, Flag aus.
6. **Performance:** Debug-Build; 95th-Percentile 150 ms / ~20 % Janky; keine
   Release-/Fire-TV-Messung.
7. **RGB_565:** Coil 3.1.0 bietet keine öffentliche Bitmap-Config; eigener
   Decoder nicht gebaut.
8. **Fire TV:** unangetastet (Owner-Policy); kein Fire-TV-Lauf.
9. **Rechtliches:** Entscheidungslage des aktuellen Owner-Auftrags gilt; keine
   erfundenen Selektoren, Grauzone bleibt unverifiziert.

---

## 6. Blöcke

| Block | Ergebnis |
|---|---|
| 1 | Inventur, Preflight, Gate-Task, STAND.md |
| 2 | Geräteabnahme; HLS-, FK-, Watchdog-, Nav-, Kontrast-, Markup-Fixes |
| 3 | Offline-Snapshots, Suchindex, Info-Screen, Live-Calls entkoppelt |
| 4 | Pack-Worker mit CSS-Matcher, GitHub-Cron, TVmaze-Job |
| 5 | Schema eingefroren, Gold-Fixtures, Validator im Gate |
| 6 | ARD Ende-zu-Ende live (Suche → Folgen → Stream → Player) |
| 7 | PACKS.md generiert, ARD zusätzlich live verifiziert |
| 8 | Signing/Update/PERF, M6-Flag, Logo im Player/Info, Abschlussdoku |

---

*Endstand 17.11.2026. Details: [ABSCHLUSS.md](ABSCHLUSS.md),
[SCHEMA.md](SCHEMA.md), [PACKS.md](PACKS.md), [PERF.md](PERF.md),
[SIGNING.md](SIGNING.md), [UPDATE.md](UPDATE.md), [METADATA.md](METADATA.md),
[FETCH.md](FETCH.md), [SOURCE_LIVE.md](SOURCE_LIVE.md).*
