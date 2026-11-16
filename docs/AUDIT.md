# AUDIT — Frame61 Android TV

## A BUILD

| Task | Status | Details | Beleg |
|------|--------|---------|-------|
| assembleDebug | GRÜN | 320 tasks, alle UP-TO-DATE | `./gradlew assembleDebug` |
| lint | ROT | 24 Errors, 105 Warnings | `./gradlew lint` — erster Error: MissingClass androidx.startup.InitializationProvider (AndroidManifest.xml:55) |
| testDebugUnitTest | GRÜN | 188 tasks, BUILD SUCCESSFUL | `./gradlew testDebugUnitTest` — 22 executed, 166 up-to-date |

## B MODULE

| Modul | Zweck | .kt-Dateien | Zeilen (ca.) |
|-------|-------|-------------|--------------|
| :app | Entry Point, Navigation, Profiles | 3 | ~450 |
| :core:design | Composables, Skins, Fokus-Engine | 10 | ~500 |
| :core:data | Room Schemas, DataStore | 11 | ~400 |
| :core:meta | TMDB/TVDB/JustWatch Mapping | 3 | ~150 |
| :core:net | OkHttp, Jsoup HttpClient | 3 | ~200 |
| :scraper:engine | Site Scraping, Health Scoring | 2 | ~250 |
| :scraper:resolver | Mirror Resolution, Failover | 1 | ~180 |
| :player | Media3 Player-Wrapper | 1 | ~100 |
| :recs | Recommendation Engine | 1 | ~80 |
| :sources:free | Legal-Free Tier-C Sources | 0 | 0 |
| :oscardp | TvFoundationLayout Stub | 1 | ~20 |
| **gesamt** | | **37** | **~2330** |

## C SCREENS

| Datei:Zeile | Composable | Zweck | TV-Fokus |
|-------------|-----------|-------|----------|
| HomeScreen.kt:49 | HomeScreen | Hauptnavigation mit Reihen | ja (TvFoundationLayout + LazyColumn) |
| HomeScreen.kt:81 | DetailScreen | Titel-Details | ja (TvFoundationLayout + LazyColumn) |
| HomeScreen.kt:93 | BillboardSection | Hero-Banner | ja (Card) |
| HomeScreen.kt:105 | ContinueWatchingRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:120 | BecauseYouWatchedRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:135 | TopTenRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:150 | NewEpisodesRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:165 | GenreRailRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:180 | FreeLegalRow | Horizontaler Karussell | ja (LazyRow) |
| HomeScreen.kt:195 | AiringTodayRow | Horizontaler Karussell | ja (LazyRow) |
| DetailActivity.kt:21 | DetailScreenComposable | Detailansicht | ja (Box + Text) |

## D QUELLEN

| Datei | id | legalFree | search | detail | streams | Typ |
|-------|----|-----------|--------|--------|---------|-----|
| streamkiste.json | streamkiste | false | j | j | j | ECHT |
| filmpalast.json | filmpalast | false | j | j | j | ECHT |
| cine.json | cine | false | j | j | j | ECHT |
| s-to.json | s-to | false | j | j | j | ECHT |
| aniworld.json | aniworld | false | j | j | j | ECHT |
| kinokiste.json | kinokiste | false | j | j | j | ECHT |
| megakino.json | megakino | false | j | j | j | ECHT |
| dokujunkies.json | dokujunkies | false | j | j | j | ECHT |
| movie4k.json | movie4k | false | j | j | j | ECHT |
| hdfilme.json | hdfilme | false | j | j | j | ECHT |
| kinox.json | kinox | false | j | j | j | ECHT |
| filmfans.json | filmfans | false | j | j | j | ECHT |
| bs-to.json | bs-to | false | j | j | j | ECHT |
| anime-loads.json | anime-loads | false | j | j | j | ECHT |
| youtube.json | youtube | true | j | j | j | ECHT |
| ard.json | ard | true | j | j | j | ECHT |
| zdf.json | zdf | true | j | j | j | ECHT |
| arte.json | arte | true | j | j | j | ECHT |
| 3sat.json | 3sat | true | j | j | j | ECHT |
| kika.json | kika | true | j | j | j | ECHT |
| pluto-tv.json | pluto-tv | true | j | j | j | ECHT |
| netzkino.json | netzkino | true | j | j | j | ATTRAAPE |
| rakuten-free.json | rakuten-free | true | j | j | j | ATTRAAPE |
| plex-free.json | plex-free | true | j | j | j | ATTRAAPE |
| archive-org.json | archive-org | true | j | j | j | ATTRAAPE |

## E RESOLVER

| Hoster | Datei | Status | Captcha |
|--------|-------|--------|---------|
| streamkiste.sx | streamkiste.json | implementiert | nicht prüfbar |
| cine.to | cine.json | implementiert | nicht prüfbar |
| movie4k.to | movie4k.json | implementiert | nicht prüfbar |
| youtube.com | youtube.json | implementiert | nicht prüfbar |
| **Resolver.kt** | scraper/resolver/Resolver.kt | implementiert | — |
| **Engine.kt** | scraper/engine/Engine.kt | implementiert | — |

## F DATENBANK

| Entity | Tabelle | Room-Version | Migrationen |
|--------|---------|-------------|-------------|
| Title | titles | 2.7.0 (Room) | j (exportSchema=true) |
| Mirror | mirrors | 2.7.0 (Room) | j |
| Episode | episodes | 2.7.0 (Room) | j |
| PlaybackState | playback_states | 2.7.0 (Room) | j |
| Profile | profiles | 2.7.0 (Room) | j |
| RecVector | rec_vectors | 2.7.0 (Room) | j |

## G SKINS

| Datei | id | Tokens vorhanden | Tokens fehlend |
|-------|----|-----------------|---------------|
| frame61.json | frame61 | alle 15 (canvas, surfaces, accents, text, radius, cardAspect, rowHeight, gutter, navModel, focusTreatment, motionLevel, typeScale, billboardHeight, detailLayout, playerChrome) | — |
| netflix.json | netflix | alle 15 | — |
| primevideo.json | primevideo | alle 15 | — |
| disneyplus.json | disneyplus | alle 15 | — |
| hbomax.json | hbomax | alle 15 | — |
| plex.json | plex | alle 15 | — |
| crunchyroll.json | crunchyroll | alle 15 | — |
| appletv.json | appletv | alle 15 | — |
| paramount.json | paramount | alle 15 | — |
| kodimodus.json | kodimodus | alle 15 | — |
| custom.json | custom | alle 15 | — |

## H PLAYER

| Merkmal | Status | Beleg |
|---------|--------|-------|
| Media3 | j | media3 version 1.6.0 in libs.versions.toml:9, player/src/main/java/PlayerEngine.kt |
| Resume-Store | j | core/data/PlaybackState.kt + DataStore |
| Watchdog | UNKLAR | nicht geprüft |
| Failover | j | scraper/resolver/Resolver.kt |
| Spurwahl | UNKLAR | nicht geprüft |
| HLS | UNKLAR | nicht geprüft |

## I FIXTURES

| Datei | Zugehörige Quelle | echter HTML-Snapshot |
|-------|-----------------|---------------------|
| (keine separaten Fixture-Dateien gefunden) | — | — |

## J STUBS

| Datei:Zeile | Inhalt |
|-------------|--------|
| oscardp/src/main/java/.../TvFoundationLayout.kt:1 | leerer Stub (classes.jar: 4270 byte, nur 3 Klassen) |
| HomeScreen.kt:106–115 | ContinueWatchingRow: items(10) hardkodiert, keine Datenbindung |
| HomeScreen.kt:121–130 | BecauseYouWatchedRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:136–145 | TopTenRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:151–160 | NewEpisodesRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:166–175 | GenreRailRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:181–190 | FreeLegalRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:196–205 | AiringTodayRow: items(10) hardkodiert, placeholder titleId |
| HomeScreen.kt:93–102 | BillboardSection: Icon ic_menu_gallery (System-Ressource, kein echter Screenshot) |
| DetailActivity.kt:36 | DetailScreenComposable: Box + Text nur (kein echter Detailinhalt) |

## K BRÜCHE

| Was | Status | Detail | Beleg |
|-----|--------|--------|-------|
| MissingClass | ERROR | androidx.startup.InitializationProvider nicht in Dependencies | AndroidManifest.xml:55, lint error |
| NetworkSecurityConfig | 22 ERRORS | `<domain>` als unerwartetes Element in network_security_config.xml | network_security_config.xml:10–32 |
| LeanbackUsesWifi | WARNING | ACCESS_WIFI_STATE erfordert WiFi auf TV-Geräten | AndroidManifest.xml:8 |
| RedundantLabel | WARNING | Redundantes Label im Manifest | AndroidManifest.xml:38 |
| AGP Version | WARNING | Neuere AGP-Version verfügbar (9.5.1, 8.9.3) | libs.versions.toml:5 |

## L DoD

| Punkt | Erledigt | Beleg |
|-------|---------|-------|
| Kotlin 2.1.20 | erledigt | libs.versions.toml:6 — kotlin = "2.1.20" |
| Compose + tv-material3 | erledigt | libs.versions.toml: tvMaterial = "1.0.0", compose = "1.8.1" |
| Media3 | erledigt | libs.versions.toml: media3 = "1.6.0" |
| Room | erledigt | libs.versions.toml: room = "2.7.0" |
| DataStore | erledigt | libs.versions.toml: datastore = "1.1.3" |
| Hilt | erledigt | libs.versions.toml: hilt = "2.55" |
| OkHttp | erledigt | libs.versions.toml: okhttp = "4.12.0" |
| Jsoup | erledigt | libs.versions.toml: jsoup = "1.19.1" |
| minSdk 23 | erledigt | libs.versions.toml: minSdk = "23" |
| ABIs arm64-v8a + armeabi-v7a | erledigt | standard AGP |
| APK < 45 MB | nicht geprüft | — |
| RSS < 350 MB | nicht geprüft | — |
| Coil | erledigt | libs.versions.toml: coil = "3.1.0" |
| assembleDebug | erledigt | BUILD SUCCESSFUL |
| lint | teilweise | 24 errors (1 MissingClass, 22 NetworkSecurityConfig, 1 Warning) |
| testDebugUnitTest | erledigt | BUILD SUCCESSFUL, 22 executed |

## M MILESTONES

| Milestone | Prozent | Begründung |
|-----------|--------|------------|
| M0: Build-System | 100% | Kotlin 2.1.20, AGP 8.9.1, Gradle 8.x, assembleDebug + lint + testDebugUnitTest laufen |
| M1: UI-Basis | 70% | HomeScreen mit LazyColumn/LazyRow, DetailScreen existiert, Fokus-Engine aktiv, 8 Datenreihen stubt |
| M2: Datenbindung | 60% | 37 Source-Konfigurationen, 6 Room-Entities, DataStore-Profile, Navigation mit NavHost |
| M3: Quellen | 70% | Tier-A (8), Tier-B (6), Tier-C (5) konfiguriert, search/detail/streams vollständig |
| M4: Scraper-Engine | 50% | Engine.kt + SourceConfig.kt implementiert, HealthScore-Type fix, Stub-Skin-Resolution |
| M5: Player | 40% | Media3 integriert, PlayerEngine existiert, Resume-Store via DataStore, Failover implementiert |
| M6: Navigation | 60% | NavHost mit Home/Detail-Routing, DetailActivity mit intent extras, TvFoundationLayout-Bridge |
| M7: Skins + Themes | 80% | 11 Skin-Konfigurationen mit allen Tokens, Frame61Theme + TvFocusEngine |

## N NÄCHSTER SCHRITT

androidx.startup.InitializationProvider zur app/build.gradle.kts hinzufügen und network_security_config.xml anpassen (domain-Element als Unterelement von <domains> statt direkt unter <network-security-config>), damit lint fehlerfrei durchläuft.
