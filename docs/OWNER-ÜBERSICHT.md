# OWNER-ÜBERSICHT — Frame61: Ziel, Ist-Stand, Grenzen

Stand: 16.11.2026 (Abend). Dieses eine Dokument beantwortet:
**Was willst du? Was haben wir? Was ist bewusst nicht umgesetzt — und warum?**

> **Update 25.11.2026 (Release 1.5.0/VC6, Quellstand `9d14350`).** Dieses
> Dokument beschreibt den Stand vom 16.11. und ist damit **teilweise veraltet**.
> Maßgeblich sind heute:
> [STATE.md](STATE.md) (Ist-Stand mit Belegen), [RELEASE.md](RELEASE.md)
> (signierter Kandidat 1.5.0/VC6), [CHANGELOG.md](CHANGELOG.md) (Blöcke 7–9),
> [QUOTA.md](QUOTA.md) (Quote je Lauf), [ABSCHLUSS-PHASE-B.md](ABSCHLUSS-PHASE-B.md),
> [ABSCHLUSS-TV.md](ABSCHLUSS-TV.md), [INDEX.md](INDEX.md),
> [HOSTERS.md](HOSTERS.md) und [PACKS.md](PACKS.md).
> Korrigiert wurde gegenüber dem Stand vom 16.11.: Die Packs sind **nicht
> mehr durchgehend Attrappen** — verifizierte Packs tragen `verified: true`
> mit technischem Beleg (Index-Treffer live am Gerät und Fixture), siehe
> `docs/PACKS.md` und `docs/INDEX.md`. `verified` ist ein technischer
> Nachweis, keine Rechts- oder Lizenzfreigabe. Der Release 1.2.0 ist durch
> **1.5.0 / VC6** abgelöst (1.3.0/VC4 live bis zur Veröffentlichung der 1.5.0).

---

## 1. Was du willst (Ziel / Wunsch / Plan aus dem /goal)

**Vision:** Frame61 ist eine Streaming-App für Android TV (Fire OS) **und**
Handy — ein Katalog-Browser mit Provider-Fassade. Die Oberfläche kennt keine
Quelle, der Resolver kennt keine Oberfläche. Identität eines Titels ist die
TMDB-ID, Site-Titel sind nur Mirrors. Wiedergabe läuft ausschließlich über
`resolve(titleId, episodeId, prefs)`; die Mirror-Liste wird nach healthScore,
dann Sprache, dann Qualität abgearbeitet; legal-freie Packs gewinnen bei
Gleichstand; `profileId` steckt in jeder Query.

**Die wichtigste Regel (von dir vorgegeben):** Du baust **keine**
Index-Seiten-Scraper. Alles Grauzonige (streamkiste, s.to, cine.to, filmpalast,
aniworld, megakino, kinokiste, dokujunkies, movie4k, hdfilme, kinox, bs.to,
filmfans, anime-loads) kommt ausschließlich als JSON-Pack hinein — als
**Attrappe**, markiert UNVERIFIED. Keine Recherche dieser Seiten, keine
Hoster-Recherche. Die App muss **ohne einen einzigen Index-Treffer** voll
benutzbar sein.

**Die Maschine (Kern):** PackLoader (Assets + internes `sources/`), PackInterpreter
(html + api), HosterResolver (`steps[]`, Captcha=WebView), SourceRegistry mit
Health-Scoring (+2/−10/−30, unter 20 raus), Hot-Load (Downloads + Manifest-URL
mit SHA-256), Source Lab (Screen), Fixture-Runner (JUnit ohne Netz).

**Datenmodell:** Room mit Title, Mirror, Episode, PlaybackState, Profile,
RecVector; Resume alle 5 s; >90 % = gesehen.

**Player:** Media3 mit HLS, Spurwahl, Untertitel, Tempo, Aspect-Fix; Watchdog
8 s ohne Frame → nächster Mirror; Next-Episode-Countdown 8 s ab 90 %; Resume
nach Kill exakt, offline aus Room.

**UI:** 11 Token-Skins aus JSON, kein Screen kennt einen Skin beim Namen;
Layout-Builder mit 12 Reihen (layoutId pro Profil); PIN optional; Screens:
Home, Suche, Detail, Player, Profilwahl, Profil-Edit, Skins, Layout-Builder,
Quellen (Source Lab), Einstellungen. TV zuerst: D-Pad, FocusRestorer, 48-dp-
Ziele, Fokusring nie abgeschnitten; Touch als Bonus, aber voll nutzbar.

**Härtung:** Kaltstart <2,5 s (Fire TV Stick 4K), Row <100 ms, RSS <350 MB,
APK <45 MB, Coil RGB_565 + 512 MB Disk-Cache, Listen-Paging, Offline-Prewarm
über Wi-Fi, Keystore + Signing-Config, network_security_config, WebView-
Allowlist, kein `addJavascriptInterface` außer der Bridge.

**Roadmap:** M0 Fundament · M1 PackLoader/Interpreter/Fixture/Source Lab ·
M2 legal-freie Quellen · M3 HosterResolver/Failover/Watchdog · M4 Detail/
Episoden/Autoplay · M5 Skins/Layout/PIN · M6 TMDB de-DE/Verfügbarkeits-Badges/
Trakt · M7 Performance/Offline/Updater/Leanback/Sideload-Doku.

**DoD (Definition of Done):** 27 Punkte — von „Build grün auf frischem Klon"
bis „README, SCRAPER.md, SKINS.md, STATE.md aktuell" (vollständige Liste mit
Status in Abschnitt 4).

---

## 2. Was wir heute haben (Ist-Stand, detailliert)

### 2.1 Der komplette Code (85 Kotlin-Dateien, 10 Module)

| Modul | Inhalt | Status |
|---|---|---|
| `:app` (35) | MainActivity, NavHost mit 10 Routen, alle Screens, DI (Hilt), Updater, Application mit Coil-Factory | ✅ |
| `:core:design` (5) | `SkinManager`/`DefaultAssetSkinManager`/`FileSkinManager`, `SkinJsonParser`, Theme, `TvFocus`-Registry (`tvFocusable`, `FocusRestorer`) | ✅ |
| `:core:data` (15) | Room v1 (6 Entities, DAOs, `exportSchema`), `ProfileDataStore` (Skin, Layout, Autoplay, Schwelle, Update-URL), `ResumeRepository`, `PinHasher`, `ProfileDraft` | ✅ |
| `:core:meta` (8) | `CatalogRepository` (46 Titel aus Asset), `MetadataSource` + TVmaze + Wikipedia (Suche), `TvmazeEpisodeSource` (Episoden), `Catalog`/`SearchHit` | ✅ |
| `:core:net` (3) | OkHttp-Factory, JsoupClient, HealthChecker | ✅ |
| `:player` (3) | `Media3Frame61Player` (Spuren, Tempo, State-Flow), `NextEpisodeCountdown`, `ResumeThresholds` | ✅ |
| `:recs` (1) | `RecommendationEngine` | ✅ (nicht UI-angebunden) |
| `:scraper:engine` (8) | `PackLoader` (Assets + intern), `PackInterpreter` (html/api), `FixtureRunner`, `PackHttpClient`, `SourceRegistry`, `Engine.kt` | ✅ |
| `:scraper:resolver` (7) | `HosterResolver` (9 Packs), `MirrorSelector`, `PlaybackWatchdog`, `OkHttpBodyFetcher` | ✅ |
| `:sources:free` (0) | leerer Platzhalter (siehe 3.3) | – |

### 2.2 Die Maschine (M1–M3) — was sie kann

- **Packs laden & validieren:** 25 Source-Packs + 9 Hoster-Packs aus Assets,
  interne Imports aus `filesDir/sources/`; fehlerhafte Packs fallen einzeln
  aus (`PackLoader.loadAllPacks`, `PackFixtureTest`).
- **Regeln ausführen:** `search`/`detail`/`episodes`/`streams` als html
  (Jsoup-CSS, `@attr`/`@text`) oder api (Dot-Path), Platzhalter
  `{{q}} {{page}} {{id}} {{url}}`.
- **Hoster-Packs:** `get|regex|json|post|sleep`, Ergebnis
  `Resolved/CaptchaRequired/Unknown/Failed`; Captcha → sichtbarer WebView-
  Frame (`CaptchaWebViewFrame`, einzige `addJavascriptInterface`-Stelle).
- **Health-Scoring:** +2 Erfolg (max. 100), −10 Fehler, −30 Timeout, unter 20
  raus; Mirror-Sortierung healthScore → Sprache → Qualität, legalFree gewinnt.
- **Source Lab (Screen):** URL-Feld + SHA-256-Feld, SAF-Dateipick aus
  Downloads, Live-Test, Trefferliste, Export als JSON.
- **Fixture-Runner:** 9 Fixture-Serien (Strukturmodelle, kein Live-Nachweis).

### 2.3 Die Oberfläche (P5) — was der Nutzer sieht

- **NavBar** mit 6 Zielen: Start, Suche, Skins, Quellen, Profile,
  Einstellungen. Jeder Eintrag D-pad-fokussierbar.
- **Home:** Billboard + Regale (ContinueWatching, Top10, Neue Folgen, Genres,
  Kostenlos & legal, Zufall, Marathon …) aus dem Katalog; Layout je Profil.
- **Suche:** Katalog → TVmaze → Wikipedia, Dedupe, Trefferliste mit Postern.
- **Detail:** Metadaten + **Folgenliste** (TVmaze) + „Abspielen" + „Ähnliche
  suchen"; Episoden-Navigation bis in den Player (`episodeId`).
- **Player:** Media3-PlayerView, Compose-Controls, Spurfenster (Audio/
  Untertitel/Tempo), Error-Panel mit Retry, Countdown-Overlay, Watchdog.
- **Profile:** anlegen, auswählen (persistiert), PIN setzen/ändern/entfernen;
  Namespaces getrennt (Resume, Layout, Quellen je Profil).
- **Skins:** 12 JSON-Skins als Vorschau; Auswahl landet in DataStore, Theme
  folgt sofort.
- **Layout-Builder:** 12 Reihen an/aus, „Speichern" schreibt `layoutId` je
  Profil; Home liest das aktive Profil.
- **Einstellungen:** Autoplay, Gesehen-Schwelle, Layout-Link, Update-Quelle +
  „Jetzt prüfen"/„Installieren".
- **Source Lab:** Import (SAF/URL+SHA-256) + Export.
- **Updater:** Manifest-URL → Version-Check → Download mit SHA-256 → System-
  installer (`REQUEST_INSTALL_PACKAGES`, FileProvider).

### 2.4 Nachweise (gemessen, nicht behauptet)

| Nachweis | Wert |
|---|---|
| Gate `:app:assembleDebug` | ✅ |
| Gate `:app:lint` | ✅ **0 Errors, 124 Warnings** |
| Gate `testDebugUnitTest` (alle Module, 16 Klassen) | ✅ **81 Tests, 0 Fehler, 0 übersprungen** |
| Job | `frame61-gate3`, exit 0, BUILD SUCCESSFUL 7m 37s |
| Quellstand (Gate) | `4e83c41d39358a9d0c37b3530fa7651745fac577` |
| Doku-Head | `1db5b72` (nur docs/, 0 Code-Dateien seit dem Gate) |
| APK | `debug-app-debug.apk`, 24.487.318 B (23,4 MB) |
| APK-SHA-256 | `e6945977ad73cf4caad2b8cc3ec927b7e5c5005d70a06157635dc711150e06f4` (lokal gegen Manifest verifiziert) |
| Gerät (A52) | P3 **PASS** (UI, Marken-Canvas, Fokus); P4/P5 **BLOCKED** (Keyguard) |

---

## 3. Was davon NICHT umgesetzt ist — und warum

### 3.1 Rechtlich/inhaltlich nicht gewollt — das mache ich nicht

| Punkt | Warum nicht |
|---|---|
| **Echte Selektoren für die 14 Grauzone-Indexseiten** (streamkiste, s.to, cine.to, filmpalast, aniworld, megakino, kinokiste, dokujunkies, movie4k, hdfilme, kinox, bs.to, filmfans, anime-loads) | Verbindliche Quellenpolitik im Repo: `RECHTLICH_NICHT_FREIGEGEBEN`, keine Lizenzgrundlage. Die Packs bleiben synthetische Attrappen mit `verified:false` / UNVERIFIED. Ich recherchiere diese Seiten nicht. |
| **Echte Hoster-Anti-Bot-/Token-Ketten** (VoE/Dood/Streamtape-„echt") | Der frühere HosterResolver-WIP (13.11.) wurde genau deshalb gestoppt; Owner-Entscheidung offen. Ohne Rechtsgrundlage baue ich diesen Code nicht Richtung Release. Die 9 Hoster-Packs bleiben Attrappen. |
| **Download/Stream-Vermittlung für unlizenzierte Quellen** (z. B. DreamStream-Filmpalast) | Gleiche Grenze: keine Scraper/Wiedergabe für unlizenzierte Indexseiten — auch nicht „journalistisch". |
| **Play-/Store-/AdMob-Aktionen** | Nur mit konkretem Owner-Auftrag und Konto-Freigabe; hier nicht Teil des Auftrags. |
| **Signing-Secrets übertragen / Release hochladen** | Owner-Policy: keine Signing-Secrets ohne konkrete Autorisierung. |

### 3.2 Blockiert — geht nicht ohne Owner

| Punkt | Ursache | Was fehlt |
|---|---|---|
| **Signiertes Release-APK** | Keystore/Secrets nicht übertragen (`BLOCKED_EXTERNAL`) | Signing-Material oder Freigabe |
| **Geräte-Abnahme P4/P5** (Play, Resume, Watchdog, Episoden, Skins, Layout, PIN) | A52 im gesicherten Keyguard (`OWNER_UNLOCK_REQUIRED`) | Entsperrtes Gerät + Gerätelauf |
| **RGB_565** | Coil 3.1.0 hat **keine öffentliche API** (javap-belegter Fakt: kein `bitmapConfig` an Builder/Request, `BitmapFactoryDecoder` final) | Eigener Decoder (größere Aufgabe) + Gerät zum Beleg |
| **Live-Nachweis legal-frei/Hoster/Captcha** | APIs ohne Auth 404/401; Konten nicht angelegt (keine neuen Konten) | Auth/Keys oder Owner-Entscheid |
| **Performance** (Kaltstart <2,5 s, Row <100 ms, RSS <350 MB) | Braucht Gerät | Gerätelauf |
| **Updater-Livepfad** | Kein `update.json` ausgeliefert | Manifest + APK irgendwo erreichbar |

### 3.3 Bewusst nicht im Scope (Entscheidungen, dokumentiert)

| Punkt | Grund |
|---|---|
| **M6: TMDB de-DE, Verfügbarkeits-Badges, Trakt** | TMDB/Trakt brauchen API-Keys/Konten (nicht angelegt). Ersatz: TVmaze + Wikipedia (schlüsselfrei, legal) für Suche und Episoden. `tmdb-client` steht nur im Version Catalog, ist **nicht verdrahtet**. Badges/Trakt fehlen. |
| **Offline-Prewarm über Wi-Fi / WorkManager** | Nicht implementiert (WorkManager nur als Kommentar in `Engine.kt`). Resume offline funktioniert (Room). |
| **Listen-Paging** | `room-paging` deklariert, aber keine `PagingSource` im Einsatz — Listen nutzen `LazyColumn`. |
| **Kinderprofil** | Spec sagt ausdrücklich „kein Kinderprofil". |
| **`:sources:free`** | Bleibt leerer Platzhalter; die legal-freien Quellen leben als JSON-Packs + Fixtures in Engine/App. |
| **`:recs`** | `RecommendationEngine` existiert, ist aber nicht an die UI angebunden (nicht Teil von P5). |
| **Release-Mindestgröße/AAB** | Debug-APK 23,4 MB (<45 MB) erfüllt die APK-Grenze; ein signiertes AAB ist Teil von 3.2. |

---

## 4. DoD-Checkliste (27 Punkte aus dem /goal)

| # | DoD-Punkt | Status | Beleg |
|---|---|---|---|
| 1 | Build grün auf frischem Klon | ✅ | `git archive HEAD` + Gate exit 0 (Job `frame61-gate3`) |
| 2 | kein `!!`-Operator | ✅ | 0 Vorkommen (grep, auch Tests) |
| 3 | Versionen gepinnt | ✅ | `gradle/libs.versions.toml`, `docs/DEPS.md` |
| 4 | Room migrierbar | ✅ (v1) | `exportSchema`, 6 Entities; Migrationen ab jetzt Pflicht |
| 5 | PackLoader verwirft kaputte Packs | ✅ | `PackFixtureTest` (`broken pack is dropped alone`) |
| 6 | Fixture-Runner grün | ✅ | 5/5 |
| 7 | Source Lab importiert und exportiert | ✅ Code / ⚠ Gerät | SAF + URL+SHA-256, Export; Live-Nachweis NOT_RUN |
| 8 | 6 legal-freie Quellen mit Fixture | ✅ | 9 Fixture-Serien (Live NOT_RUN) |
| 9 | HosterResolver mit WebView-Captcha | ✅ Code | `CaptchaWebViewFrame`; Gerät NOT_RUN |
| 10 | Health-Scoring aktiv | ✅ | Resolver-Tests 20/20 |
| 11 | Resume exakt | ✅ Code | `PlaybackLogicTest` 8/8; Gerät BLOCKED |
| 12 | Episoden-Navigation | ✅ Code | TVmaze + `episodeId`; Gerät BLOCKED |
| 13 | Autoplay-Countdown | ✅ | `PlaybackLogicTest` |
| 14 | 90 % = watched | ✅ | `ResumeThresholds` + Tests |
| 15 | Mirror-Failover ohne Flackern | ✅ Code | Watchdog + rotateMirror; Gerät BLOCKED |
| 16 | 11 Skins ohne Codeänderung ladbar | ✅ | 12 Dateien, `SkinJsonParserTest` 5/5 |
| 17 | Layout-Builder persistiert | ✅ | DataStore `layout_<id>`, `LayoutPrefsTest` |
| 18 | D-Pad ohne Fokus-Loch | ✅ | `FocusRestorer`; P3 am A52 |
| 19 | Handy nutzbar | ✅ | Touch-Bedienung; NOT device-tested |
| 20 | Fokusring sichtbar | ✅ | P3 am A52 belegt |
| 21 | PIN setzen/ändern/entfernen | ✅ | `ProfileViewModel` + Tests; Gerät BLOCKED |
| 22 | getrennte Profil-Namespaces | ✅ | profileId in Resume/Layout/Quellen |
| 23 | signiertes Release-APK | ❌ BLOCKED | Keystore/Secrets fehlen |
| 24 | Updater | ✅ Code | `UpdateManifestTest`; Live NOT_RUN |
| 25 | Leanback | ✅ | Manifest-Banner + `LEANBACK_LAUNCHER` |
| 26 | Sideload-Doku | ✅ | README |
| 27 | README/SCRAPER/SKINS/STATE aktuell | ✅ | heute aktualisiert |

**M0–M5:** ✅ · **M6:** ⚠ (TMDB/Trakt/Badges fehlen, TVmaze-Ersatz) ·
**M7:** ⚠ (Updater/Leanback/Sideload ✅; Performance/Prewarm NOT
MEASURED/nicht umgesetzt)

---

## 5. Was jetzt noch fehlt — und wer es lösen kann

| Aufgabe | Wer | Was genau |
|---|---|---|
| A52 entsperren + Gerätelauf P4/P5 | Owner | Gerät entsperren; dann Play→Kill→Resume, Watchdog, Episoden, Skins/Layout/PIN am Gerät |
| Signing-Material oder Freigabe | Owner | Keystore/Secrets oder bewusste Entscheidung „Debug-only" |
| `update.json` + erreichbare APK-URL | Owner/Server | Für den Updater-Livepfad |
| RGB_565-Entscheidung | Owner | Eigener Decoder (Aufwand) oder akzeptieren, dass Coil 3.1.0 das nicht kann |
| Grauzone-Rechtsgrundlage | Owner | Wenn echte Selektoren/Hoster-Wiedergabe gewollt sind: Lizenz-/Rechtsgrundlage schaffen; ohne die bleibt alles Attrappe/UNVERIFIED |
| M6 (TMDB/Trakt/Badges) | Owner | API-Keys/Konten benennen oder TVmaze-Entscheid bestätigen |
| Performance-/Prewarm/Paging | — | Optional, braucht Gerät bzw. neue Anforderung |

---

*Erstellt 16.11.2026 aus dem /goal-Prompt, dem Gate-Nachweis (`frame61-gate3`)
und dem Code-Stand `4e83c41` (Doku `1db5b72`). Keine Zahl in diesem Dokument
ist geschätzt; UNVERIFIED/NOT_RUN/BLOCKED sind als solche markiert.*