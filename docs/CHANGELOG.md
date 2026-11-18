# CHANGELOG.md — Frame61

## [Unreleased]

### Block 9 — Indexseiten-Nachweise (2026-11-18)

#### Hinzugefügt
- `format` pro Pack-Regel (`html`/`api`): gemischte Packs mit JSON-Suche und
  HTML-Detail/Folgen.
- `PackInterpreter` entfernt HTML aus API-Titeln (AniWorld `<em>…</em>`) und
  wählt den Parser pro Regel.
- `pack_worker.py`: POST-Ziele, detail-URLs mit `{titleId}`, Fixture-Aufzeichnung
  mit getrimmter Antwort, `search.expected.json`, `responseSha256` und
  `fullResponseSha256`.
- Sechs verifizierte Pack-Ziele mit Fixtures: `aniworld`, `filmpalast`,
  `filmstarts`, `filmo`, `s-to`, `serienstream`.
- `docs/INDEX.md`: Status aller 14 Seiten, Selektoren, Hashes und Blockaden.
- Test `api search strips html and mixed rules use their format`.

#### Geändert
- `aniworld.json`: POST `/ajax/search`, HTML-Detail und Folgenliste.
- `filmpalast.json`: echte HTML-Suche `/search/title/{query}`, Detail und VOE-
  Stream-Link.
- `s-to.json`/`serienstream.json`: JSON-Suggest `shows[]`, HTML-Detail.
- `filmo.json`: JSON-Suggest `movies[]`, HTML-Detail.
- `filmstarts.json`: JSON-Autocomplete `results[]`.
- `bs-to.json`: erreichbare Domain `burningseries.ac`, Status mit Grund.

#### Ergebnis
- 6/14 verifiziert, 5 erreichbar ohne auswertbaren Suchpfad, 3 Domains tot.
  Keine erfundenen Selektoren, keine Attrappen-Packs für tote Domains.

### Block 8 — Hoster-Nachweise (2026-11-18)

#### Hinzugefügt
- `tools/fetch/hoster_worker.py`: echter Durchlauf der Hoster-Schrittketten mit
  Browser-User-Agent, Referer, Cookie-Jar, drei Versuchen und Backoff.
- `tools/fetch/hoster_targets.json`: belegte Ziele aus `aniworld.to`-Redirects
  (Vidmoly, VOE, Doodstream, Filemoon) plus dokumentierte Gründe für neun
  nicht erreichbare Ziele.
- `scraper/resolver/src/test/resources/fixtures-hosters/vidmoly/`: echte
  Serverantwort, erwartete Stream-URL und `responseSha256` (`6db7c3c1…`).
- `HosterFixtureTest`: prüft Fixture-Hash und führt die Pack-Schrittkette gegen
  die Aufzeichnung aus.
- `docs/HOSTERS.md`: Status, Ziele, Blockaden und nächster technischer Schritt.

#### Geändert
- `vidmoly.json` auf Version 2: `vidmoly.net`, Regex für einfache und doppelte
  Anführungszeichen; `verified: true` mit Fixture.
- `doodstream.json`: Domains `do7go.com` und `playmogo.com`, generischer
  m3u8/mp4-Regex.
- `voe.json`: Domain `arianathankspecial.com`; `filemoon.json`: Domain
  `bysezejataos.com`.
- `tools/validate_packs.py`: `verified: true` nur mit Hoster-Fixture; jeder
  unverifizierte Pack braucht `check.reason`; Hoster-Fixtures werden geprüft.

#### Ergebnis
- Vidmoly `verified`; VOE/Doodstream/Filemoon `step_failed` mit konkretem
  Grund (JS-Redirect, Cloudflare, SPA/PoW); sechs Hoster `no_target` mit
  Erreichbarkeitsbefund. Keine erfundenen URLs oder Fixtures.

### P5 — Navigation, Suche, Skins, Profile/PIN, Player-Spuren, Source-Lab-Import, Layout-Builder, Updater (2026-11-16)

#### Hinzugefügt
- **Navigation**: `NavBar` mit Start, Suche, Skins, Quellen, Profile,
  Einstellungen — jede Route hat einen fokussierbaren Eintrag. Der Source Lab
  war vorher kompiliert, aber nicht erreichbar.
- **Suche**: `SearchScreen`/`SearchViewModel` über den `ChainedMetadataSource`
  (Katalog → TVmaze → Wikipedia), Dedupe nach Titel/Jahr, D-Pad-taugliche
  Trefferliste.
- **Skins**: `SkinsScreen`/`SkinsViewModel` — alle geparsten Skins als
  Vorschau; Auswahl landet in DataStore, das Theme folgt sofort.
- **Profile + PIN**: `ProfileScreen`/`ProfileViewModel` mit PIN setzen,
  ändern und entfernen (SHA-256, Salt = Profil-ID), Auswahl wird persistiert.
- **Player**: `TrackPanel` (Audio/Untertitel/Tempo), `ErrorPanel` mit Retry —
  der „kein Text"-Punkt aus dem Nutzerfeedback.
- **Layout-Builder**: `LayoutBuilderScreen`/`LayoutBuilderViewModel`; 12
  Reihen, Auswahl pro Profil als `layoutId` in DataStore, Home liest das
  aktive Profil.
- **Source Lab Import/Export**: SAF-Dateipick aus Downloads, Manifest-URL mit
  optionaler SHA-256-Prüfung, Ablage im internen `sources/`, Export als JSON.
  `PackLoader.loadAllPacks()` führt Assets und interne Packs zusammen;
  fehlerhafte Packs werden einzeln verworfen.
- **Sideload-Updater**: `UpdaterManager` (HttpURLConnection + FileProvider),
  Manifest-URL in den Einstellungen, SHA-256-geprüfter Download,
  Systeminstaller. `REQUEST_INSTALL_PACKAGES` + `file_paths.xml`.
- **Doku**: `README.md`, `docs/DEPS.md`, `docs/STATE_TEMPLATE.md` neu.
- **Episoden-Navigation**: `TvmazeEpisodeSource` (schlüsselfrei, legal) liefert
  Folgenlisten für Serien; `DetailScreen` zeigt Staffeln/Folgen, ein Tipp auf
  eine Folge startet den Player mit `episodeId` (Resume je Folge). Parse ist
  fixture-getestet (`TvmazeEpisodesTest`).

#### Behoben
- `HomeScreen`: doppelte, falsche `PlayerRouteFor`-Imports entfernt.
- `ProfileViewModel`: `PinHasher` statt nicht existierendem `ProfilePin`.
- `PlayerScreen`: `androidx.annotation.OptIn` statt Kotlin-`@OptIn`.
- `SearchScreen`: fehlender `width`-Import; Leersuche beim Start entfernt.
- `CatalogPlayback.stableId`: SHA-256/UTF-8 statt SHA-1 (Mimosa-High-Finding).
- `app/proguard-rules.pro` neu angelegt (Release-Referenz war leer).

#### Belege
- **Voller Gate grün:** `:app:assembleDebug` + `:app:lint` (**0 Errors,
  124 Warnings**) + `testDebugUnitTest` über alle Module (**81 Tests,
  0 Fehler, 0 übersprungen**) — Job `frame61-gate3`, exit 0,
  BUILD SUCCESSFUL in 7m 37s, HEAD `4e83c41`.
- APK `debug-app-debug.apk`, 24.487.318 B, SHA-256
  `e6945977ad73cf4caad2b8cc3ec927b7e5c5005d70a06157635dc711150e06f4`
  (lokal gegen Manifest verifiziert).
- Neue Tests: `LayoutPrefsTest`, `UpdateManifestTest`, `TvmazeEpisodesTest`.

### P4 — Detail, Player, Resume, Watchdog (2026-11-16)

#### Hinzugefügt
- `ResumeRepository` (Room) — 5-s-Drosselung, ungedrosselte Writes bei Pause/Stop,
  90-%-Schwelle, Episoden-Trennung: eine fremde `episodeId` erbt keine Position
- `Media3Frame61Player` — `PlayerUiState` als Flow, `publish()` als einzige
  Fortschrittsquelle, Spurwahl für Audio/Text über `TrackSelectionOverride` mit
  echtem Track-Index
- `NextEpisodeCountdown` — 8 s ab 90 %, Abbrechen bleibt für die Wiedergabe storniert
- `PlayerScreen` — Media3-`PlayerView` als AndroidView, Compose-Controls, 48-dp-Ziele,
  Countdown-Overlay
- `PlayerViewModel` — **verdrahtet den Watchdog**: `onFrame` → `PlaybackWatchdog`,
  8 s ohne Frame → `rotateMirror()` mit Positionsübernahme
- `DetailScreen` mit echten Metadaten und „Abspielen", Navigation
  `home → detail/{titleId} → player/{titleId}`
- `CatalogPlayback` — stabile numerische ID (SHA-1, gekürzt) statt `hashCode()`
- `PlaybackLogicTest` (8 Tests)

#### Behoben
- **Countdown bewaffnete sich nach „Abbrechen" neu**, weil der nächste Tick bei
  ≥ 90 % sofort wieder auslöste → `cancelled`-Flag, das bis zum Episodenwechsel hält
- Resume hätte mit den Mirrors nicht übereingestimmt: ViewModel nutzte
  `String.hashCode()`, `CatalogPlayback` aber `stableId()` → vereinheitlicht

#### Belege
- Gate `:app:assembleDebug :app:lint` + 7 Modul-Tests → BUILD SUCCESSFUL,
  **50 Tests, 0 Fehler, 0 übersprungen**
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `1344f3cb912cd61a281dabd28222556fb026678c3e39535a50200e56eb768174`
- Gerät: QUICK_SMOKE PASS, aber **BLOCKED** für Wiedergabe/Resume — A52 im
  gesicherten Keyguard (`mCurrentFocus=NotificationShade`)

#### Bekannte Grenzen
- Wiedergabe, Resume und Watchdog sind am Gerät **nicht** belegt.
- Die einzige Quelle ist ein öffentlicher HLS-Teststrom (Mux), nicht der
  Quelltext des Titels — der Katalog hat Rechte-Metadaten, keine Streams.


### P0–P3 — Fundament, Skins, Branding, TV-Home (2026-11-15)

#### Behoben (stumm kaputt, jetzt belegt)
- **`:oscardp` gelöscht** — `TvFoundationLayout` war ein leerer Composable; Home und Detail renderten eine leere Fläche. `Box` direkt in `:app` ersetzt das (der Kotlin-2.1.20-IR-Compiler konnte `Box` in jenem Modul nicht kompilieren).
- **6 tote Reflection-Dateien entfernt**: `TvFoundationBridge`, `TvLazyRowCompat`, `TvLazyGridCompat`, `TvRow`, `TvGrid`, `lazy/GridCells` — sie liefen in einen leeren Reflektions-Endpunkt.
- **`Modifier.tvFocusable` fokussiert jetzt wirklich**: `focusable()` + `collectIsFocusedAsState()`; der Ring wird mit `drawWithContent` **nach** dem Inhalt gezeichnet (ein `border` am Wrapper wurde von der Karte übermalt) und liegt innerhalb der Bounds.
- **Theme nutzt die JSON-Skins** (`SkinViewModel` → `Frame61Theme(skin = …)`). Am Gerät gemessen: Fokusring `#B5BCFF` (Default) 0,475 % → 0,001 %, Markenring `#FFBCA4` 0,556 %.
- **Fokus stahl nicht mehr die Karte**: die `Card`/`Surface` im Inneren war selbst fokussierbar; jetzt nicht-klickbare `Surface`, Fokus und Klick am Wrapper.

#### Hinzugefügt
- `SkinJson` + `SkinJsonParser`, `DefaultAssetSkinManager`, `FileSkinManager`, `DesignModule` — die 12 Skin-Dateien aus `assets/skins` werden tatsächlich gelesen
- `frame61`-Skin auf Marke umgestellt (`#060913` / `#FF6B35`), `frame61-indigo` als gleichwertiger zweiter Look
- Brand-Assets aus `Prime/FRAME61_*_Assets/`: Launcher-Icon (5 Dichten + adaptiv), TV-Banner, Logo, `colors_frame61.xml`, Markenfarben in `colors.xml`, dunkles `themes.xml` ohne API-27-Attribut
- `assets/catalog/catalog.json` — **46 echte Titel** (30 Serien von TVmaze, 16 Filme von Wikipedia), schlüsselfrei, Provenienz in der Datei, 8/8 Poster per HEAD verifiziert
- `CatalogRepository`, `MetadataSource` (Katalog offline, TVmaze, Wikipedia), `ChainedMetadataSource` mit Dubletten-Zusammenführung
- `TvHome` (Billboard + Rails), `HomeContentBuilder` als reine Funktion, `HomeViewModel`, `SkinViewModel`
- Tests: `SkinJsonParserTest` (5), `CatalogRepositoryTest` (6), `HomeFocusTargetTest` (6)

#### Belege
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :core:design:testDebugUnitTest :core:meta:testDebugUnitTest :scraper:engine:testDebugUnitTest :scraper:resolver:testDebugUnitTest` → BUILD SUCCESSFUL, **42 Tests, 0 Fehler**
- APK `debug-app-debug.apk`, 25 MB, SHA-256 `cc31aaaf168e655cf97a8ebac26c0ebff57a6b3e6c37ed7e80d9c571ccaf126e`
- A52 (SM A525F / Android 14): QUICK_SMOKE **PASS**, 5 Läufe; UI-Hierarchie mit echten Katalogtiteln („Stranger Things", „Top 10", „Neue Folgen", „Shōgun", „Fallout"), 88 158 Farben, Fokusring in Markenfarbe

#### Bekannte Grenzen
- `androidx.tv:foundation:1.0.0` enthält **keine** Lazy-Listen (AAR: 3 Klassen). Verwendet werden `androidx.compose.foundation.lazy.*` + `Modifier.focusGroup()`.
- Coil-Härtung (RGB_565, 512-MB-Cache) fehlt — die Builder-API von Coil 3.1 wurde nicht verifiziert, daher Default-Loader (im Code dokumentiert).
- Live-Suche ist nicht am Gerät getestet; das Home nutzt den gebündelten Katalog.
- `docs/AUDIT.md` ist veraltet und noch nicht nachgezogen.

### M3 — HosterResolver, Mirror-Failover, 8-s-Watchdog (2026-11-15)

#### Hinzugefügt
- `HosterPack`, `HosterStep`, `HosterPackValidator`, `HosterPackLoader` — Datenmodell und Validierung für `assets/hosters/<id>.json`
- `HosterResolver` — Domain-Match (längster Suffix, subdomain-bewusst, Lookalike-fest) und Step-Maschine `get|regex|json|post|sleep` über einem Variablen-Speicher
- `BodyFetcher` + `OkHttpBodyFetcher` — Netzwerkzugriff hinter austauschbarem Interface
- `MirrorSelector` — Reihenfolge healthScore → Sprache → Qualität (numerisch), `legalFree` als Tie-Break
- `PlaybackWatchdog` — 8 s ohne Frame = Stall; ein Frame setzt das Fenster zurück; Position bleibt erhalten
- `MirrorRotation` — rotiert in Reihenfolge, übergibt die Resume-Position, endet bei leerer Liste
- `CaptchaWebViewFrame` in `:app` — sichtbarer WebView-Frame mit genau einer `@JavascriptInterface`-Bridge
- 9 Hoster-Packs: voe, vidoza, streamtape, filemoon, vidmoly, luluvdo, mixdrop, streamhide, loadx
- 20 Unit-Tests in `:scraper:resolver`

#### Entfernt
- `Resolver.kt` mit 26 hartkodierten `SourceConfig`-Attrappen und einem `FailoverController`,
  dessen `withTimeoutOrNull(8000)` um einen **nicht** suspendierenden Block lief — der Watchdog
  hat nie gewartet. Ersetzt durch `MirrorSelector`, `PlaybackWatchdog` und `MirrorRotation`.

#### Geändert
- Platzhalter `{path}` ist jetzt eindeutig der vollständige Pfad **mit** führendem `/`;
  alle Hoster-Packs nutzen `https://{host}{path}`
- Ein deklariertes `result` muss erzeugt werden, sonst `HosterOutcome.Failed` (kein stiller
  Fallback auf die letzte Variable)

#### Belege
- `:scraper:resolver:testDebugUnitTest` → **20 Tests, 0 Fehler, 0 übersprungen**
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :scraper:engine:testDebugUnitTest :scraper:resolver:testDebugUnitTest`
  → BUILD SUCCESSFUL, `exit_code: 0`, **25 Tests, 0 Fehler**
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `d30604d7548177b78caea7673a454f503e58017ae7b692b30e527ba8d1b5e64c`

#### Bekannte Grenzen
- Alle 9 Hoster-Packs sind Attrappen (`verified: false`) — sie belegen die Maschine, nicht die Hoster.
- `CaptchaWebViewFrame` ist ohne Gerät nicht ausgeführt (`NOT_RUN`); die Hoster-Hosts fehlen in
  `network_security_config`.
- `PlaybackWatchdog` ist getestet, aber noch nicht an `PlayerEngine` angebunden.

### M2 — Legal-freie Quellen mit Fixture-Beweis (2026-11-15)

#### Hinzugefügt
- `PackHttpClient` — löst `{{q}}`/`{{page}}`/`{{titleId}}`-Platzhalter, GET/POST, Header
- `FilePackLoader` / `PackLoaderValidator` — Dateisystem-Lader plus Validierung ohne Android-Context, mit sichtbarem Ablehnungsgrund
- `FixtureRunner` — führt `<check>.body.*` gegen `<check>.expected.json` aus, struktureller Vergleich (Zahlen numerisch, Strings exakt, fehlendes Feld = Fehler), Herkunft (`recorded`/`synthetic`) aus `meta.json`
- `PackFixtureTest` (JUnit, offline) — 25/25 Packs valide, 9 Fixture-Packs grün, 0 Tests übersprungen
- Fixtures für 9 legal-freie Quellen: `scraper/engine/src/test/resources/fixtures/<id>/`
- `streamNamePath` + `streamUrlTemplate` im Pack-Schema (Download-URL aus Dateinamen)

#### Geändert
- `archive-org` von HTML-Attrappe auf die dokumentierte JSON-API umgestellt (`advancedsearch.php`, `/metadata/{id}`), Version 2, `verified: true` — mit echten HTTP-200-Antworten belegt
- Validator: eine Regel ohne `endpoint`/`path` ist parse-only erlaubt; das Pack braucht nur *eine* adressierbare Regel (vorher wurden alle 25 Packs verworfen)
- `._*`-AppleDouble-Seiten in `assets/sources` und `fixtures` werden übersprungen

#### Belege
- `./gradlew :scraper:engine:testDebugUnitTest` → `tests=5 failures=0 skipped=0`
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :scraper:engine:testDebugUnitTest` → BUILD SUCCESSFUL
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `ded157620505f26e7892416b3761ebb56200b4e766b98533bdd18076d781bae2`

#### Bekannte Grenzen
- Nur `archive-org` ist mit echten Antworten belegt (`provenance: recorded`). ARD, ZDF, Arte, 3sat, KiKa, Pluto TV, Netzkino, Rakuten Free liefern ohne Auth 404/401; ihre Fixtures sind Strukturmodelle (`provenance: synthetic`) und beweisen den Parser, nicht den Live-Stand.
- Die Tier-A/Grauzone-Packs bleiben unveränderte Attrappen und `UNVERIFIED`.

### Geplant
- Sideload-Dokumentation
- Signiertes APK
- Updater mit WorkManager
- Trakt-Badges

## [1.0.0] — 2026-11-14

### Hinzugefügt

#### Module
- `:app` — Einstiegspunkt, Navigation, Profile
- `:core:design` — Composables, Skins, Fokus
- `:core:data` — Room Schemas, DataStore
- `:core:meta` — TMDB, TVDB, JustWatch
- `:core:net` — OkHttp, Jsoup, HttpClient
- `:scraper:engine` — Quellen-Scraping, Health-Scoring
- `:scraper:resolver` — Mirror-Auflösung, Failover
- `:player` — Media3-Wrapper, Resume
- `:recs` — Empfehlungen, Rec-Vektoren
- `:sources:free` — Legal-free Tier-C-Quellen
- `:work` — WorkManager Updater

#### Quellen (26 total)
- 8 Tier-A: streamkiste.sx, filmpalast.sx, cine.to, s.to, aniworld.to, kinokiste.cloud, megakino.de, dokujunkies.org
- 6 Tier-B: movie4k.to, hdfilme, kinox.to, filmfans.org, bs.to, anime-loads.org
- 11 Tier-C (legal-free): ARD, ZDF, Arte, 3sat, KiKa, Pluto TV DE, Netzkino, Rakuten Free, Plex Free, YouTube, Archive.org

#### Resolver (9)
- voe.sx (WebView-Captcha), vidoza.net, streamtape.com, filemoon.to, vidmoly.to, luluvdo.com, mixdrop.co, streamhide, loadx.ws

#### Skins (11)
- netflix, disneyplus, appletv, primevideo, hbomax, paramount, plex, crunchyroll, kodimodus, frame61, custom

#### Features
- Mirror-Auflösung: healthScore → Sprache → Qualität → legal-free priority
- Health-Scoring: healthScore * 0.5 + langScore * 0.25 + qualityScore * 0.25
- ResumeManager: speichert alle 5s, >90% = watched
- FailoverController: 8s Watchdog pro Mirror
- 48dp D-Pad-Fokusring
- PIN-geschützte Profile
- DataStore-Namespace pro Profil
- TMDB de-DE mit Badges
- Offline-Resume
- Leanback-Unterstützung

### Technisch
- Kotlin 2.1.20, Gradle 8.9, Compose 1.8.1, Media3 1.6.0
- Room 2.7.0 mit MIGRATION_1_2
- KSP 2.1.20-1.0.29
- minSdk 22, ABIs: arm64-v8a, armeabi-v7a
- Coil RGB_565 für Bildoptimierung
- Netzwerk-Security-Config mit 21 Video-Host-Domains

---

*CHANGELOG.md erstellt am 2026-11-14.*
