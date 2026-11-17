# STAND.md — Bestandsaufnahme Frame61

Stand: 2026-11-17 · Branch `block-1` · Basis-HEAD `db8e05dc14da61b1a8a64c5b791a81d7a7e2b03b`
· Diese Datei beschreibt den **vorgefundenen** Stand plus die in Block 1
angelegte Gate-Infrastruktur. Sie ersetzt keine Gate-Aussage: „grün" steht nur
dort, wo ein Lauf mit Quellstand und Ergebnis belegt ist.

---

## 1. Repo-Inventur (gemessen am 17.11.2026)

| Kennzahl | Wert |
|---|---|
| Kotlin-Dateien (ohne `build/`) | **85** |
| Kotlin-Zeilen | **8.268** |
| Testklassen / `@Test`-Methoden | **13 / 81** |
| Gradle-Module | **10** |
| Source-Packs (`assets/sources/`) | **25** (14 Grauzone A/B, 11 legal-frei C) |
| Hoster-Packs (`assets/hosters/`) | **9** |
| Skin-Dateien (`assets/skins/`) | **12** |
| Katalogtitel (`assets/catalog/`) | **46** |
| Fixture-Serien | **9** (1 `recorded`: archive-org; 8 `synthetic`) |
| `!!`-Operator in Kotlin | **0** |

### Module

| Modul | Inhalt | .kt | Tests |
|---|---|---|---|
| `:app` | MainActivity (in `ui/HomeScreen.kt`), NavHost mit 8 Routen, Screens, DI, Updater, Application | 30 | 25 (4 Klassen) |
| `:core:design` | Skins, Theme, Fokus-Registry | 5 | 5 |
| `:core:data` | Room v1 (6 Entities), DataStore, Resume, PIN | 15 | 8 (2 Klassen) |
| `:core:meta` | Katalog, TVmaze/Wikipedia (live), TVmaze-Episoden | 8 | 10 (2 Klassen) |
| `:core:net` | OkHttp-Factory, Jsoup-Client, HealthChecker | 3 | 0 |
| `:player` | Media3-Wrapper, Countdown, Resume-Schwellen | 3 | 8 |
| `:recs` | RecommendationEngine (nicht UI-angebunden) | 1 | 0 |
| `:scraper:engine` | PackLoader/Interpreter/FixtureRunner/SourceRegistry | 8 | 5 |
| `:scraper:resolver` | HosterResolver, MirrorSelector, Watchdog | 7 | 20 (4 Klassen) |
| `:sources:free` | leerer Platzhalter | 0 | 0 |

### Screens und Routen (Ist)

`home` · `detail/{titleId}` · `source_lab` · `player/{titleId}?episodeId=` ·
`search` · `skins` · `layout_builder` · `settings` · `profiles`
(dazu `DetailActivity` im Manifest, aber die Navigation läuft über Compose).

---

## 2. Gate-Infrastruktur — in Block 1 neu angelegt

Vorgefunden: **kein** `scripts/preflight.sh`, **kein** Gradle-Task `gate`.
Damit war die im Auftrag verlangte Prüfform nicht existent. Angelegt:

| Datei | Zweck |
|---|---|
| `scripts/preflight.sh` | schnelle, offline Prüfung: JDK 17, SDK 36, `gradlew`, Assets, Doku, Pack-Validator, `!!`-Verbot, Gate-Registrierung, Key-Verdacht, Git-Zustand |
| `tools/validate_packs.py` | Schema-Validator für Source-Packs, Hoster, Skins, Katalog, Fixtures; Wahrheitsregel `verified:true` ⇒ `recorded`-Fixture mit `responseSha256` |
| `build.gradle.kts` | Tasks `validatePacks`, `checkApkSize`, `gate` |

`./gradlew gate` hängt zusammen: `:app:assembleDebug`, `:app:lint`, alle
`testDebugUnitTest`-Tasks der 10 Module, `validatePacks`, `checkApkSize`
(≤ 45 MiB). Lokale Heavy-Builds sind per Owner-Policy gesperrt; der Gate-Lauf
erfolgt über `python3 ~/hendler-device-ops/remote_build.py` auf
`contabo-build` gegen einen sauberen Git-HEAD.

Validator-Erstlauf: **25 Source-Packs, 9 Hoster, 12 Skins, 46 Katalogtitel,
9 Fixtures — 0 Fehler, 0 Warnungen.**

---

## 3. Was belegt ist — und was nur Code ist

| Bereich | Belegter Stand | Nicht belegt |
|---|---|---|
| Build | Letzter fremder Lauf: `frame61-gate3`, 81 Tests, Lint 0E/124W, APK 24.487.318 B (`e6945977…`), Quellstand `4e83c41` | Block-1-Gate auf `db8e05d` + Gate-Task (läuft in dieser Runde) |
| Gerät A52 | P3 (UI, Marken-Canvas, Fokus) am Gerät belegt | P4/P5; Gerät ist aktuell als `device` verbunden, Keyguard-Zustand unbestätigt |
| Packs | Parser-Fixtures; archive-org mit echter Antwort (recorded) | Live-Nachweis aller anderen Packs; alle 14 Grauzone-Packs `verified:false` |
| Hoster | Resolver-Tests 20/20 | Live-Nachweis 9/9 NOT_RUN, alle `verified:false` |
| Player | Logik-Tests 8/8 | Wiedergabe am Gerät; aktuell **nur Demo-HLS** (`CatalogPlayback`, Mux-Teststream) |
| Metadaten | Katalog offline (46 Titel); TVmaze/Wikipedia live im Client | Kein Snapshot-Loader, keine Attribution im UI, keine `assets/meta/` |
| Signing | Release-Config vorhanden | `frame61.jks` fehlt; Release-Signing BLOCKED |
| Updater | Parser-Tests | Kein `update.json` ausgeliefert, Livepfad NOT_RUN |
| Performance | Targets dokumentiert | Keine Messung (Kaltstart/Row/RSS) |
| CI/Cron | – | Kein `.github/`, kein Fetch-Worker |

---

## 4. DoD-Liste (27 Punkte)

Status aus dem vorhandenen Audit übernommen; „Gate" heißt: in dieser Runde
maschinell zu bestätigen.

| # | Punkt | Status | Anmerkung |
|---|---|---|---|
| 1 | Build grün auf frischem Klon | ✅ berichtet | Block-1-Gate bindet HEAD `db8e05d` |
| 2 | kein `!!` | ✅ gemessen | 0 Vorkommen, Preflight prüft weiter |
| 3 | Versionen gepinnt | ✅ | `gradle/libs.versions.toml`, `docs/DEPS.md` |
| 4 | Room migrierbar | ✅ (v1) | `exportSchema`; Migration ab Schemaänderung Pflicht |
| 5 | PackLoader verwirft kaputte Packs | ✅ | `PackFixtureTest` |
| 6 | Fixture-Runner grün | ✅ | 5/5 |
| 7 | Source Lab importiert/exportiert | ✅ Code, ⚠ Gerät | SAF + URL+SHA-256; Live NOT_RUN |
| 8 | 6 legal-freie Quellen mit Fixture | ✅ | 9 Fixture-Serien (8 synthetic, 1 recorded) |
| 9 | HosterResolver mit WebView-Captcha | ✅ Code, ⚠ Gerät | `CaptchaWebViewFrame` |
| 10 | Health-Scoring aktiv | ✅ | Resolver-Tests 20/20 |
| 11 | Resume exakt | ✅ Code, ⚠ Gerät | `PlaybackLogicTest` 8/8 |
| 12 | Episoden-Navigation | ✅ Code, ⚠ Gerät | TVmaze live; Block 3 stellt auf Snapshot um |
| 13 | Autoplay-Countdown | ✅ | Logik-Test |
| 14 | 90 % = watched | ✅ | `ResumeThresholds` |
| 15 | Mirror-Failover ohne Flackern | ✅ Code, ⚠ Gerät | Watchdog + `rotateMirror` |
| 16 | 11 Skins ohne Codeänderung | ✅ | 12 Dateien, Parser-Test 5/5 |
| 17 | Layout-Builder persistiert | ✅ | DataStore `layout_<id>` |
| 18 | D-Pad ohne Fokus-Loch | ✅ (P3) | `FocusRestorer` |
| 19 | Handy nutzbar | ✅ Code, ⚠ Gerät | Touch vorhanden, nicht gerät-getestet |
| 20 | Fokusring sichtbar | ✅ (P3) | am A52 belegt |
| 21 | PIN setzen/ändern/entfernen | ✅ Code, ⚠ Gerät | `ProfileViewModel` |
| 22 | getrennte Profil-Namespaces | ✅ | `profileId` in Resume/Layout/Quellen |
| 23 | signiertes Release-APK | ❌ BLOCKED | Keystore fehlt → Block 8 liefert Anleitung |
| 24 | Updater | ✅ Code, ⚠ Live | `update.json` fehlt → Block 8 |
| 25 | Leanback | ✅ | Manifest-Banner + `LEANBACK_LAUNCHER` |
| 26 | Sideload-Doku | ✅ | README |
| 27 | README/SCRAPER/SKINS/STATE aktuell | ⚠ | wird in Block 8 auf Endstand gebracht |

**Offen im Sinne des Auftrags:** Signing (#23), Geräteabnahme P4/P5,
RGB_565, `update.json` (#24), Performance, M6-Metadaten, Grauzone-Freigabe.

---

## 5. Konkrete Lücken und Defekte, die die Blöcke betreffen

1. **Player ist Demo-only.** `CatalogPlayback.candidates()` liefert immer den
   Mux-Teststream; `MirrorSelector`/`HosterResolver` sind nicht angebunden.
   → Block 6 muss einen echten Pfad Suche → Detail → Streams → Player bauen.
2. **API-Detailpfad ist falsch.** `PackInterpreter.parseDetail()` ruft bei
   `kind=api` `parseSearchApi()` mit der `search`-Regel auf; eine
   `detail`-Regel (z. B. archive-org `metadata`) wird nie gelesen. → Block 6.
3. **Live-API-Calls im Client.** `TvmazeMetadataSource`, `WikipediaMetadataSource`,
   `TvmazeEpisodeSource` rufen im UI-Pfad `api.tvmaze.com` und
   `en.wikipedia.org` auf. → Block 3 entfernt das zugunsten von
   `assets/meta/<id>.json`.
4. **Kein Info-Screen, keine TVmaze-Attribution.** → Block 3.
5. **Kein Fetch-Worker, kein Cron, kein `verified`-Update.** →
   Block 4.
6. **Kein `docs/SCHEMA.md`, keine Gold-Fixtures.** → Block 5.
7. **Kein `docs/PACKS.md`, keine per-Pack-Statusliste.** → Block 7.
8. **Kein `update.json`, keine Release-Signing-Anleitung, keine PERF-Messung.** →
   Block 8.
9. **`minSdk`-Abweichung:** `gradle.properties` sagt 22, Version Catalog 23;
   der Build nutzt 23. Dokumentiert, nicht angeglichen.
10. **RGB_565:** Coil 3.1.0 hat keine öffentliche Bitmap-Config-API (javap-Beleg
    im Code-Kommentar). Bleibt ohne eigenen Decoder offen; Entscheidung nötig.
11. **`:sources:free` ist leer** (nur Build-Datei) — Platzhalter, keine Funktion.
12. **`network_security_config`** listet weiter Grauzone-/Hoster-Domains; das ist
    Altbestand und kein Scraper. Wird in Block 7/8 bereinigt oder begründet.
13. **Doku-Widerspruch:** README/SCRAPER bezeichnen Grauzone-Packs als Attrappen
    und untersagen Recherche. Der aktuelle Owner-Auftrag entscheidet Scraping
    anders. Operativ gilt für die Umsetzung: **kein Selektor ohne HTML-Beleg**;
    Packs ohne Beleg bleiben `verified:false`. Die Doku wird in Block 7/8 an die
    tatsächliche Entscheidungslage angepasst, nicht vorher.

---

## 6. Block → Dateien (Arbeitskarte)

| Block | Hauptdateien |
|---|---|
| 1 | `scripts/preflight.sh`, `tools/validate_packs.py`, `build.gradle.kts`, `docs/STAND.md` |
| 2 | `docs/ABNAHME.md`; Fixes in `app/src/main/java/**`, `app/build.gradle.kts` |
| 3 | `core/meta/**`, `app/src/main/assets/meta/**`, `app/src/main/java/.../di/MetaModule.kt`, Info-Screen, `AndroidManifest.xml` (Attribution) |
| 4 | `tools/fetch/**` (neu), `.github/workflows/fetch.yml` (neu), Pack-Dateien (`verified`, `last_check`) |
| 5 | `docs/SCHEMA.md`, `fixtures/gold-01.json`, `fixtures/gold-02.json`, Validator |
| 6 | `scraper/engine/**` (Detail-API-Fix), `app/.../ui/player/**`, `scraper/resolver/**`, Pack + Fixture der gewählten Quelle |
| 7 | `app/src/main/assets/sources/*.json`, `fixtures/<id>/**`, `docs/PACKS.md` |
| 8 | `app/build.gradle.kts` (Signing), `update.json`, `docs/PERF.md`, `docs/ABSCHLUSS.md`, `docs/STAND.md`, README |

---

## 7. Gate-Befehle

```sh
# vor jedem Block (lokal, schnell)
scripts/preflight.sh

# voller Gate (remote, sauberer HEAD)
python3 ~/hendler-device-ops/remote_build.py --repo "<frame61-Pfad>" \
  --tasks "gate"
```

Der Remote-Lauf exportiert den Git-HEAD; nicht committete Änderungen sind
**nicht** Teil des Gates. Deshalb: pro Block committen, dann bauen.

---

## 8. Entscheidungslage (für die Umsetzung)

- Signing: Debug-Keystore jetzt, Release-Signing in Block 8.
- RGB_565: aktiviert als Ziel; ohne öffentliche Coil-API bleibt der Nachweis
  offen (eigener Decoder wäre eine eigene Aufgabe).
- Metadaten: TVmaze + Wikidata/Wikipedia, keyless; Snapshots statt Live-Calls.
- Scraping: durch den aktuellen Owner-Auftrag freigegeben. Unabhängig davon
  gilt Regel 5: **keine erfundenen Selektoren**; ohne HTML-Beleg
  `verified:false`.
- Automatisierung: GitHub Actions, täglicher Cron auf ungerader Minute.

---

*Ende Block 1. Nächster Schritt: Gate-Lauf auf diesem Stand, dann Block 2.*
