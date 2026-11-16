# SCRAPER.md — Frame61 Scraper-Engine

Stand: 2026-11-16. Diese Datei beschreibt den **tatsächlichen** Code; ältere
Fassungen nannten Klassen, die es nicht gibt (`Resolver.kt`,
`MirrorResolver`, `SkinManagerImpl`).

## Architektur

```
UI ──► PackInterpreter ──► PackHttpClient ──► HTTP
        │  (Regeln aus assets/sources/*.json)
        └──► HosterResolver ──► MirrorSelector ──► PlaybackWatchdog
```

Die Engine ist datengetrieben: Sie führt **JSON-Packs** aus, sie kennt keine
Seite im Code. Ein Pack beschreibt, *wie* gesucht, detailliert und aufgelöst
wird — nie, ob eine Quelle legal ist.

## Module und Klassen

### :scraper:engine

- `PackLoader` — lädt `assets/sources/*.json` **und** das interne
  `sources/`-Verzeichnis (`loadAllPacks()`); validiert jedes Pack einzeln.
  `FilePackLoader` ist die dateibasierte Variante für Fixtures/Tests.
  `PackLoaderValidator` validiert ohne Android-Context (in Tests wiederverwendet).
- `PackInterpreter` — führt `search(pack, query, page)`, `detail(...)`,
  `episodes(...)`, `streams(pack, id, episodeId)` aus. `kind=api` liest
  JSON per Dot-Path, `kind=html` per Jsoup-CSS mit `@attr`/`@text`.
  Platzhalter: `{{q}} {{page}} {{id}} {{url}}`.
- `PackHttpClient` — OkHttp-Front-end für Pack-Regeln (`get`/`post`),
  rendert Platzhalter, baut Query-Parameter.
- `FixtureRunner` — spielt `search.body.*` / `streams.body.*` gegen die
  erwarteten JSONs ein, **ohne Netz**. Fehlendes Fixture = skipped, nicht rot.
- `SourceConfig.kt` — `SourceRegistry` mit den Tier-Listen (A/B/C) als
  **Klassifikation**, keine Steuerung. `SourceConfig` ist ein älteres,
  separates Schema (urlTemplate/selector) und wird von der Pack-Engine nicht
  mehr ausgeführt.
- `Engine.kt` — `ScraperEngine`, `HealthScoring`, `ScrapingJob` (ältere
  Pipeline, von den Packs nicht mehr genutzt).

### :scraper:resolver

- `HosterResolver` — Domain-Match gegen `assets/hosters/*.json`, führt
  `steps[]` aus (`get|regex|json|post|sleep`). Ergebnis:
  `Resolved | CaptchaRequired | Unknown | Failed`.
- `MirrorSelector` — `MirrorCandidate` sortieren: healthScore → Sprache
  (`de` zuerst) → Qualität → legal-free gewinnt Gleichstand.
- `PlaybackWatchdog` — 8 s ohne Frame → `isStalled()`; `reportFrame()`
  hält Position und Zeitstempel.
- `OkHttpBodyFetcher` — `BodyFetcher`-Implementierung für den Resolver.

### :core:net

- `HttpClientFactory` — OkHttp mit 8-s-Timeouts (12 s für WebView).
- `JsoupClient` — `fetch`, `fetchWithJsoup`, `parseStreamLinks`,
  `parseVideoElements` (ältere Helfer, von den Packs nicht mehr genutzt).
- `HealthChecker` — `checkSource(url)`, `scoreMirror(mirror)`.

## Quellen und Wahrheitsstatus

**Verbindliche Regel:** Grauzonen-Indexseiten (streamkiste, s.to, cine.to,
filmpalast, aniworld, megakino, kinokiste, dokujunkies, movie4k, hdfilme,
kinox, bs.to, filmfans, anime-loads) sind **Attrappen**. Ihre Packs enthalten
synthetische Platzhalter-Selektoren, keine recherchierten echten Selektoren.
Sie sind `verified: false` (Default) und damit **UNVERIFIED**. Es wird nichts
recherchiert und nichts automatiert.

Eine Quelle ist erst GRÜN, wenn ihr Fixture-Test grün ist. Ohne Fixture ist
sie UNVERIFIED, egal wie plausibel die Selektoren aussehen.

### Tier A — Grauzone (Attrappen, UNVERIFIED)

`streamkiste`, `filmpalast`, `cine`, `s-to`, `aniworld`, `kinokiste`,
`megakino`, `dokujunkies`

### Tier B — Grauzone (Attrappen, UNVERIFIED)

`movie4k`, `hdfilme`, `kinox`, `filmfans`, `bs-to`, `anime-loads`

### Tier C — legal-frei (Fixtures vorhanden, Live-Nachweis offen)

`ard`, `zdf`, `arte`, `3sat`, `kika`, `pluto-tv`, `netzkino`, `rakuten-free`,
`plex-free`, `youtube`, `archive-org`

Fixtures liegen in `scraper/engine/src/test/resources/fixtures/<id>/`
(`meta.json`, `search.body.*`, `search.expected.json`, `streams.body.*`,
`streams.expected.json`). Sie sind **Strukturmodelle**, kein Live-Nachweis:
Die APIs liefern ohne Auth 404/401 oder ändern ihre Form.

### Hoster (9)

`voe`, `vidoza`, `streamtape`, `filemoon`, `vidmoly`, `luluvdo`, `mixdrop`,
`streamhide`, `loadx` — `assets/hosters/*.json`, `verified: false`,
Live-Nachweis NOT_RUN.

## Health-Scoring

`SourceRegistry` führt Packs mit `healthScore`: **+2** bei Erfolg (max. 100),
**−10** bei Fehler, **−30** bei Timeout. Unter 20 fällt das Pack aus der
Rotation. `HealthScoring` in `Engine.kt` ist die ältere Mirror-Formel
(`health*0.5 + lang*0.25 + quality*0.25`).

## Failover

1. `MirrorSelector` sortiert Mirrors (healthScore, Sprache, Qualität,
   legal-free).
2. `PlayerViewModel` füttert den `PlaybackWatchdog` pro Frame.
3. 8 s ohne Frame → `rotateMirror()` mit Positionsübernahme, ohne UI-Flackern.
4. `CaptchaRequired` → sichtbarer WebView-Frame (`CaptchaWebViewFrame`),
   kein Hard-Fail.

## Assets

- `app/src/main/assets/sources/<id>.json` — 25 Source-Packs
- `app/src/main/assets/hosters/<id>.json` — 9 Hoster-Packs
- `app/src/main/assets/catalog/catalog.json` — mitgelieferter Katalog
- `scraper/engine/src/test/resources/fixtures/<id>/` — Fixtures

---

*SCRAPER.md rewritten 2026-11-16 (code-named classes, legal status explicit).*