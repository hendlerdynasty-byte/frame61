# STAND2.md — Inventur gegen das neue Ziel (TV-App, Blöcke 0–12)

Stand: 2026-11-17 · Branch `block-1` · Basis `78a6a4c`
· Ersetzt fachlich `docs/STAND.md` für den Umbau zur 10-Foot-TV-App. Gate
und CI sind belegt; die folgende Liste trennt **vorhanden** von **fehlt**.

## 1. Infrastruktur (Block 0 / 0.5 — erledigt)

| Punkt | Stand |
|---|---|
| Repo | `hendlerdynasty-byte/frame61`, **public**, `main` |
| CI | `bootstrap` grün (`44153299724`), `pages` grün (`44153289832`) |
| Pages | `https://hendlerdynasty-byte.github.io/frame61/` (HTTP 200) |
| Keystore | außerhalb des Repos, SHA-256 `31f5cd50…`, Zertifikat `12:01:F4:6D…4C:52` |
| Secrets | 4 Signing-Secrets gesetzt; kein Passwort-Fallback im Code |
| Gate | Remote-Gate `frame61-3f720511d452-1794932134`: exit 0, 453 Tasks, APK `67c1ca71…` |
| Preflight | 27 OK, 0 FAIL, 0 WARN |

## 2. Code-Inventur

| Kennzahl | Wert |
|---|---|
| Kotlin-Dateien | 91 |
| `@Test`-Methoden | 93 |
| Module | 10 |
| Source-Packs | 26 (14 Grauzone, 12 legal-frei) |
| Hoster-Packs | 10 (9 Hoster + `direct`) |
| Skins | 12 JSON-Dateien |
| Katalog | 46 Titel |
| Snapshots | 46 + `index.json` |
| Fixtures | 10 (2 recorded, 8 synthetic) |

Vorhandene Kernbausteine: PackLoader/Interpreter (html+api), FixtureRunner,
HosterResolver (`get|post|regex|json|sleep|copy`), MirrorSelector,
PlaybackWatchdog, Source Lab, Offline-Snapshots, Fetch-Worker, ARD-Resolver,
Media3-Player, Room v2, DataStore, Source Lab, Info-Screen.

## 3. Lücken je Block

### Block 2 — Cover-Defekt
- Es gibt **keine** zentrale PosterCard. Cover werden an drei Stellen
  unterschiedlich gerendert:
  - `TvHome.Artwork`: `AsyncImage.clip(shape)`, zusätzlich Surface/Box;
  - `SearchScreen`: `AsyncImage` **ohne** `clip`/Shape in einer Surface-Zeile;
  - `DetailScreen`: gar kein Poster, nur Text (Cover fehlt dort komplett).
- `PosterCard` in `TvHome` hat den Fokus-/Klick-Wrapper außen, die Surface
  innen ohne gemeinsamen Clip → Ecken können beim Fokus-Scale aufreißen.
- Kein gemeinsamer Modifier-Stack, keine Innen-Border-Regel.
- **Fehlt:** eine `PosterCard`-Komponente, drei Verwendungen, Vorher/Nachher-
  Screenshots in `docs/design/`.

### Block 3 — TV-First
- Keine Overscan-Safe-Area; Inhalte beginnen bei 12–24 dp vom Rand.
- Fokus-Skalierung `1.04` (`TvFocusStyle.scale`), Ring `3.dp`; Ziel `1.08`.
- Typo: `body=16.sp`, `rowTitle=20.sp`, `billboardTitle=36.sp`; Ziel
  Titel 32 sp+ ist erfüllt, Body 16 sp erfüllt, aber keine zentrale Skala
  mit Mindestwerten/WindowSizeClass.
- Poster-Höhe 180 dp, Aspect 0.6; Ziel 190 dp / 2:3 (0.667).
- Hero/Billboard 420 dp; Ziel ≤ 40 % der Höhe (bei 1080p ≈ 432 px – knapp,
  aber nicht relativ berechnet).
- Kein `WindowSizeClass`-Verhalten; kein Nachweis D-Pad-only/Fokus-
  Persistenz pro Screen über die vorhandenen `FocusRestorer` hinaus.
- **Fehlt:** Safe-Area-Wrapper, Token-Anpassung, responsive Spalten,
  Screenshots 1080p/kompakt.

### Block 4 — Skins
- 12 bestehende Skins (netflix, disneyplus, appletv, primevideo, hbomax,
  paramount, plex, crunchyroll, kodimodus, frame61, frame61-indigo, custom).
- Die sechs Ziel-Skins **fehlen komplett**: Cinema Red, Magic Blue,
  Midnight Gold, Arctic Light, Neon Grid, Kino Noir.
- Theme hat ColorScheme/Typography/Layout, aber keine `DensityTokens`,
  `FocusTokens`, Gradient-Regeln oder Hero-Verhalten pro Skin.
- Skin-Auswahl existiert (Skins-Screen, DataStore, sofortige Anwendung),
  liegt aber als eigener Menüpunkt vor → Block 5 verschiebt sie.
- **Fehlt:** Theme-Engine-Erweiterung, sechs Skin-Dateien, Live-Vorschau,
  Screenshot je Skin.

### Block 5 — Navigation/Einstellungen
- NavBar: Start, Suche, Skins, Quellen, Profile, Optionen.
- Ziel: Start, Filme, Serien, Suche, Meine Liste, Einstellungen.
- „Filme", „Serien", „Meine Liste" existieren nicht als Routen.
- Einstellungen sind eine flache Liste (Autoplay, Schwelle, Layout,
  Update-URL, Info); kein Baum Darstellung/Wiedergabe/Erweitert/Info.
- Quellen-Schalter, Default alle an, Fetch-Status/Logs fehlen.
- **Fehlt:** neue Routen, Settings-Baum, Source-Toggles, Entfernen von
  Skins/Quellen aus der NavBar.

### Block 6 — Auto-Auflösung
- `ArdPlaybackResolver` löst genau eine Quelle (ARD) auf; danach
  `direct`-Hoster; sonst Demo.
- Keine parallele Suche über alle aktiven Indizes, keine Dedupe über
  Titel/Jahr/Länge, keine Hoster-Kette mit 4-s-Timeout, kein 15-min-Cache.
- Quellenausfall ist im Player als „Demo-Stream" sichtbar (soll nur Log sein).
- **Fehlt:** Aggregator, parallele Hoster-Auflösung, Cache, Log-Beleg.

### Block 7 — Player
- Controls sind permanent sichtbar (Surface unten), kein Auto-Hide.
- D-Pad: keine Links/Rechts-10-s-Bindung an KeyEvents, OK nicht belegt,
  Zurück schließt sofort (kein 2×).
- Fokusring 3 dp; Ziel 4 dp. Kein Sprung-Gradient, keine dezente
  Pufferanzeige, Quellenlabel dauerhaft sichtbar.
- **Fehlt:** Overlay-Zustand, KeyEvent-Handling, Auto-Hide, 2×-Zurück,
  Screenshots.

### Block 8 — Hoster
- Vorhanden: voe, streamtape, vidmoly, vidoza, mixdrop, luluvdo, loadx,
  streamhide, filemoon + `direct`.
- **Fehlt:** `doodstream`. Alle 9 Hoster sind `verified:false`; nur `direct`
  wird über ARD genutzt. Keine recorded Hoster-Fixtures, kein Cookie-Jar.
- **Fehlt:** 10 Schrittketten live, Fixtures, Worker-Verifikation.

### Block 9 — Indexseiten
- Ziel-14: aniworld, burningseries, filmpalast, filmstarts, kinokiste,
  kinox, megakino, moflix, movie2k, s.to, serienstream, streamcloud, filmo,
  cine.to.
- Vorhanden: aniworld, filmpalast, kinokiste, kinox, megakino, s.to, cine.
- **Fehlt:** burningseries, filmstarts, moflix, movie2k, serienstream,
  streamcloud, filmo (7 neue Packs).
- Zusätzlich vorhanden, aber nicht im Ziel: anime-loads, bs-to,
  dokujunkies, filmfans, hdfilme, movie4k, streamkiste (7).
- Alle 14 Grauzone-Packs sind `verified:false`, Fixtures fehlen; kein
  Cloudflare-/Cookie-Handling.
- **Fehlt:** 14 echte Selektoren, recorded Fixtures, Worker-Verifikation.

### Block 10 — Katalog
- 46 Titel, Poster als Remote-URL (Coil), kein lokaler Posterbestand.
- Keine Genre-/Jahresfilter-UI, keine „Meine Liste", kein Fortschritt pro
  Titel außerhalb von Resume.
- **Fehlt:** ≥ 600 Titel, lokale Poster, Dedupe, Filter, persistente Liste,
  Fortschrittsanzeige.

### Block 11 — Performance
- Debug-Baseline (Block 8 alt): Kaltstart-Median 2051 ms, PSS 154 MiB,
  Scroll-p90 65 ms, p95 150 ms, ~20 % Jank.
- Kein Baseline Profile, kein R8-Release im Gate, kein Paging, kein
  Bild-Cache-Tuning über Coil-Disk 512 MB hinaus.
- Ziel: p95 Kaltstart < 1200 ms, Scroll-p95 < 40 ms, Jank < 5 %.
- **Fehlt:** Baseline Profile, Release-Messung, Paging, PERF2.

### Block 12 — Release
- Keystore/Secrets vorhanden, Pages aktiv, aber kein signiertes Release,
  kein `update.json` auf Pages, kein GitHub-Release, kein Update-Live-Test.
- **Fehlt:** signierte APK/AAB, Manifest, Release, Installation, DoD-Abschluss.

## 4. Dateien, die fast jeder Block berührt

| Bereich | Dateien |
|---|---|
| Cover/UI | `app/.../ui/home/TvHome.kt`, `ui/search/SearchScreen.kt`, `ui/HomeScreen.kt`, `core/design/**` |
| Theme/Skins | `core/design/Frame61Theme.kt`, `SkinJson.kt`, `SkinManager.kt`, `assets/skins/*.json` |
| Navigation | `ui/NavBar.kt`, `ui/HomeScreen.kt`, `ui/settings/**` |
| Auflösung | `playback/ArdPlaybackResolver.kt`, `scraper/resolver/**`, `scraper/engine/**` |
| Player | `ui/player/**`, `player/**` |
| Packs | `assets/sources/*.json`, `assets/hosters/*.json`, `fixtures/**`, `tools/fetch/**` |
| Katalog | `assets/catalog/catalog.json`, `assets/meta/**`, `core/meta/**` |

## 5. Bekannte Defekte aus der letzten Runde (nicht erneut suchen)

Behoben und belegt: HLS-ClassNotFound, Profil-FK-Crash, Watchdog-
Fehlauslösung, Doppel-Pop beim Zurück, „Start"-No-op, schwarzer Text,
HTML-Markup im Detail, NavLabel-Umbruch, fehlende Suchauslösung.
Diese Fixes bleiben gültig; die neuen Blöcke bauen darauf auf.

## 6. Preflight

`scripts/preflight.sh`: **27 OK, 0 FAIL, 0 WARN**. Keine Reparatur nötig.
Der Remote-Gate ist der verbindliche Build-Weg; lokale Heavy-Builds bleiben
wegen der Ressourcengates gesperrt (22 GiB intern < 25 GiB, 8 GB RAM).

## 7. Reihenfolge

Block 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12.
Block 2 (zentrale PosterCard) ist Voraussetzung für Block 3 (Poster-Maße)
und Block 4 (Skin-Radien). Block 5 (Navigation) ist Voraussetzung für
Block 10 (Meine Liste/Filter). Block 8/9 liefern die Quellen, die Block 6
parallel auflöst.

---

*Ende Block 1. Nächster Schritt: Block 2, Cover-Defekt reproduzieren.*
