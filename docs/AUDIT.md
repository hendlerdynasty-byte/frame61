# AUDIT — Frame61 Android TV

Stand: 2026-11-16 (Abend). Diese Fassung ersetzt die veraltete 20.10.-Version
(sie nannte u. a. ein gelöschtes `:oscardp`-Modul, eine nicht existierende
`Resolver.kt` und „ECHT"-Scraper für Grauzone-Packs — das war falsch).

## A. Build-Gate (voll)

| Task | Status | Details | Beleg |
|------|--------|---------|-------|
| `:app:assembleDebug` | GRÜN | 24.487.318 B APK | Job `frame61-gate3`, exit 0 |
| `:app:lint` | GRÜN | **0 Errors, 124 Warnings** | `lint-results-debug.txt` |
| `testDebugUnitTest` (alle Module) | GRÜN | **81 Tests, 0 Fehler, 0 übersprungen** | 16 Testklassen, XMLs |
| Quellstand | `4e83c41` | sauberer HEAD, isoliertes Snapshot-Repo | Manifest `source.sha` |
| Artefakt-Hash | `e6945977…06f4` | lokal gegen Manifest verifiziert | `shasum -a 256` |

## B. Module (85 .kt-Dateien)

| Modul | Zweck | .kt | Tests |
|-------|-------|-----|-------|
| :app | MainActivity, Navigation, Screens, DI, Updater | 35 | 25 (5 Klassen) |
| :core:design | Skins, Fokus-Registry, Theme | 5 | 5 |
| :core:data | Room (6 Entities, v1), DataStore, Resume, PIN | 15 | 8 (2 Klassen) |
| :core:meta | Katalog, TVmaze (Suche + Episoden), Wikipedia | 8 | 10 (2 Klassen) |
| :core:net | OkHttp, Jsoup, HealthChecker | 3 | — |
| :player | Media3-Wrapper, Countdown | 3 | 8 |
| :recs | Empfehlungen | 1 | — |
| :scraper:engine | PackLoader/Interpreter/FixtureRunner | 8 | 5 |
| :scraper:resolver | HosterResolver, MirrorSelector, Watchdog | 7 | 20 (4 Klassen) |
| :sources:free | leerer Platzhalter (0 Dateien) | 0 | — |

## C. Screens (10)

Home (Billboard + Regale), Suche, Detail (inkl. Folgenliste), Player,
Profilwahl/-Edit (PIN setzen/ändern/entfernen), Skins, Layout-Builder,
Quellen (Source Lab), Einstellungen (Autoplay, Schwelle, Layout, Update),
Captcha-WebView-Frame. Jede Route ist über die NavBar oder einen Button
erreichbar; TV-Fokus über `FocusRestorer`/`tvFocusable`.

## D. Quellen und Wahrheitsstatus

| Gruppe | Anzahl | Status |
|--------|--------|--------|
| Grauzone Tier A/B (streamkiste, s.to, cine.to, filmpalast, aniworld, megakino, kinokiste, dokujunkies, movie4k, hdfilme, kinox, bs.to, filmfans, anime-loads) | 14 | **Attrappen, UNVERIFIED** — synthetische Selektoren, `verified:false`, keine Recherche |
| Legal-frei Tier C (ard, zdf, arte, 3sat, kika, pluto-tv, netzkino, rakuten-free, plex-free, youtube, archive-org) | 11 | Fixtures vorhanden (Strukturmodelle), **Live-Nachweis NOT_RUN** |
| Hoster (voe, vidoza, streamtape, filemoon, vidmoly, luluvdo, mixdrop, streamhide, loadx) | 9 | `verified:false`, Live-Nachweis NOT_RUN |
| Katalog (TMDB/TVmaze/Wikipedia-Metadaten, offline) | 46 Titel | im APK, Poster HEAD-geprüft (8/8 beim Build) |

## E. Datenbank

Room **Version 1**, `exportSchema = true`, 6 Entities (Title, Mirror, Episode,
PlaybackState, Profile, RecVector), DAOs vollständig, `DatabaseProvider`
(Hilt). Migrationen: keine nötig (v1); ab jeder Schemaänderung Pflicht.

## F. Player

Media3 (HLS), Spurwahl Audio/Untertitel, Tempo, Watchdog 8 s (`PlaybackWatchdog`,
`PlaybackWatchdogTest` 5/5), Mirror-Failover mit Positionsübernahme,
Autoplay-Countdown 8 s ab 90 %, Resume alle 5 s (Room, `ResumeRepository`),
`>90 % = gesehen` (`ResumeThresholds`). Gerät: BLOCKED (Keyguard).

## G. Skins

12 JSON-Dateien (`netflix`, `disneyplus`, `appletv`, `primevideo`, `hbomax`,
`paramount`, `plex`, `crunchyroll`, `kodimodus`, `frame61`, `frame61-indigo`,
`custom`), 15 Tokens je Datei, Parser tolerant (`SkinJsonParserTest` 5/5:
alle 12 parsen, kaputte Datei fällt einzeln aus). Aktiver Skin in DataStore,
`Frame61Theme(skin=…)` rendert; Auswahl in den Skins-Screen.

## H. Rechtliches / Quellenpolitik

- Grauzone-Indexseiten: ausschließlich JSON-Packs, Attrappen, UNVERIFIED.
- Keine echten Selektoren, keine Recherche, keine Hoster-Recherche.
- Legal-freie Quellen + TVmaze (Metadaten/Episoden) + mitgelieferter Katalog
  sind die einzigen echten Datenwege.
- `network_security_config` enthält weiter die (historisch gewachsenen)
  Domain-Einträge der Grauzone; sie sind kein Scraper und wurden nicht erweitert.

## I. Offene Punkte (Details in STATE.md)

1. Geräte-Abnahme P4/P5 — BLOCKED (A52-Keyguard, Owner-Entsperrung).
2. RGB_565 — BLOCKED belegt (keine öffentliche Coil-3.1.0-API).
3. Live-Nachweise legal-frei/Hoster/Captcha — NOT_RUN.
4. Signiertes Release-APK — BLOCKED_EXTERNAL (keine Signing-Secrets).
5. Performance-Messungen — NOT MEASURED (kein Gerät).
6. Updater-Livepfad — NOT_RUN (kein `update.json` ausgeliefert).
7. Mimosa-Pre-Commit-Scan in dieser Sitzung nur partiell — keine
   Sicherheitsaussage.

## J. Gerät

Samsung A52 (SM A525F, Android 14): P3 am Gerät belegt (UI, Marken-Canvas,
Fokus). P4/P5 BLOCKED durch gesicherten Keyguard — keine Installation, keine
Interaktion in dieser Runde. Fire TV: unangetastet (Owner-Policy).