# Frame61 — Agent Instructions

## Build System
- Gradle 8.x, Kotlin 2.x
- Compose + tv-material3 for UI
- Media3 ExoPlayer for playback
- Room + DataStore for persistence
- Hilt for DI

## Module Layout
```
frame61/
├── app/                    # :app — entry point, navigation, profiles
├── core/
│   ├── design/            # :core:design — composables, skins, focus
│   ├── data/              # :core:data — Room schemas, DataStore
│   ├── meta/              # :core:meta — TMDB, TVDB, JustWatch
│   └── net/               # :core:net — OkHttp, Jsoup, HttpClient
├── scraper/
│   ├── engine/            # :scraper:engine — site scraping, health scoring
│   └── resolver/          # :scraper:resolver — mirror resolution, failover
├── player/                # :player — Media3 wrapper, resume
├── recs/                  # :recs — recommendations, rec vectors
└── sources/
    └── free/              # :sources:free — legal-free tier-C sources
```

## Architecture Rules
- UI knows no source types; Resolver knows no UI
- Identity = TMDB ID; Site titles are mirrors
- Playback via resolve(titleId, episodeId, prefs)
- Mirror selection: healthScore → lang → quality
- Legal-free sources prioritized before index sites

## Source Configs
- `assets/sources/<id>.json` per source with:
  - id, version, minEngine
  - legalFree (boolean)
  - search{}, detail{}, streams{}
- Tier-A: streamkiste.sx, filmpalast.sx, cine.to, s.to, aniworld.to, kinokiste.cloud, megakino.de, dokujunkies.org
- Tier-B: movie4k.to, hdfilme, kinox.to, filmfans.org, bs.to, anime-loads.org
- Tier-C: ARD, ZDF, Arte, 3sat, KiKa, Pluto TV DE, Netzkino, Rakuten Free, Plex Free, YouTube, Archive.org

## Skin Config
- `assets/skins/<id>.json` with:
  - canvas, surfaces, accents, text, radius, cardAspect, rowHeight, gutter
  - navModel, focusTreatment, motionLevel, typeScale
  - billboardHeight, detailLayout, playerChrome

## Performance Targets
- Cold start < 2.5s
- Row render < 100ms
- RSS < 350 MB
- APK < 45 MB
- Coil RGB_565
- Watchdog 8s → mirror failover without flicker

## Build Gate
- assembleDebug + lint + testDebugUnitTest must pass
- Verify each milestone with evidence
- Document causes on 3 failed attempts
