# SCRAPER.md — Frame61 Scraper-Engine

## Architektur

```
UI ──► Resolver ──► ScraperEngine ──► JsoupClient ──► HTTP
                     │
                     └──► MirrorResolver ──► MirrorResult
```

### ScraperEngine
- `scraper/engine/src/main/java/de/hendler/frame61/scraper/engine/Engine.kt`
- Scrape → parse → score Pipeline
- Parallel scraping pro Quelle
- HealthScoring: `healthScore * 0.5 + langScore * 0.25 + qualityScore * 0.25`

### MirrorResolver
- `scraper/resolver/src/main/java/de/hendler/frame61/resolver/Resolver.kt`
- Sortiert nach: healthScore (desc) → Sprache (de first) → Qualität (desc) → legal-free priority
- FailoverController mit 8s Watchdog
- DefaultSourceConfigRepository lädt 26 Quellen aus Memory (persistiert via assets/)

### SourceConfig
- `scraper/engine/src/main/java/de/hendler/frame61/scraper/engine/SourceConfig.kt`
- SourceConfig mit SearchConfig, DetailConfig, StreamsConfig
- SourceRegistry: 26 Quellen (8 Tier-A + 6 Tier-B + 11 Tier-C)
- SourceTier: A, B, C

### JsoupClient
- `core/net/src/main/java/de/hendler/frame61/net/JsoupClient.kt`
- fetch(query): Suche nach Titel
- fetchWithJsoup(url): HTML-Parsing
- parseStreamLinks(html): Extrahiert Stream-URLs
- parseVideoElements(html): Extrahiert Video-Elemente

### Network
- `core/net/src/main/java/de/hendler/frame61/net/Network.kt`
- HttpClientFactory: OkHttp mit 8s Timeouts
- ChainlinkLoggingInterceptor: Logging für Failover-Chain
- HealthChecker: checkSource + scoreMirror

## Quellen

### Tier A (8 Quellen)
| ID | Domain | legalFree |
|----|--------|-----------|
| streamkiste | streamkiste.sx | false |
| filmpalast | filmpalast.sx | false |
| cine | cine.to | false |
| s-to | s.to | false |
| aniworld | aniworld.to | false |
| kinokiste | kinokiste.cloud | false |
| megakino | megakino.de | false |
| dokujunkies | dokujunkies.org | false |

### Tier B (6 Quellen)
| ID | Domain | legalFree |
|----|--------|-----------|
| movie4k | movie4k.to | false |
| hdfilme | hdfilme.to | false |
| kinox | kinox.to | false |
| filmfans | filmfans.org | false |
| bs-to | bs.to | false |
| anime-loads | anime-loads.org | false |

### Tier C Legal-Free (11 Quellen)
| ID | Domain | legalFree |
|----|--------|-----------|
| ard | ard.de | true |
| zdf | zdf.de | true |
| arte | arte.tv | true |
| 3sat | 3sat.de | true |
| kika | kika.de | true |
| pluto-tv | pluto.tv | true |
| netzkino | netzkino.de | true |
| rakuten-free | rakuten.tv | true |
| plex-free | plex.tv | true |
| youtube | youtube.com | true |
| archive-org | archive.org | true |

## Resolver (9)
| Resolver | Domain | Captcha |
|----------|--------|---------|
| voe | voe.sx | WebView |
| vidoza | vidoza.net | — |
| streamtape | streamtape.com | — |
| filemoon | filemoon.to | — |
| vidmoly | vidmoly.to | — |
| luluvdo | luluvdo.com | — |
| mixdrop | mixdrop.co | — |
| streamhide | streamhide.to | — |
| loadx | loadx.ws | — |

## Health-Scoring

```kotlin
fun scoreMirror(source: Source, health: Double, lang: String, quality: String): Double {
    val healthScore = health
    val langScore = if (lang == "de") 1.0 else 0.7
    val qualityScore = when (quality) {
        "2160" -> 1.0
        "1080" -> 0.85
        "720" -> 0.7
        else -> 0.5
    }
    return healthScore * 0.5 + langScore * 0.25 + qualityScore * 0.25
}
```

## Failover

1. MirrorResolver sortiert Mirrors
2. FailoverController startet 8s Watchdog pro Mirror
3. Bei Timeout: nächster Mirror in Chain
4. Failover ohne UI-Flackern (asynchron)
5. Voe-Captcha über WebView (nicht WebView-Intent)

## Assets

- `assets/sources/<id>.json` — 26 Source-Konfigurationen
- Struktur: id, version, minEngine, tier, legalFree, search{}, detail{}, streams{}

---

*SCRAPER.md erstellt am 2026-11-14.*
