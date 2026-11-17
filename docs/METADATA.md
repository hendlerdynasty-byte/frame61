# METADATA.md — Offline-Snapshots (Block 3)

Stand: 2026-11-17. Der Client ruft **keine** Metadaten-API auf. Alle Such- und
Folgendaten kommen aus `app/src/main/assets/meta/`.

## Dateien

| Datei | Inhalt |
|---|---|
| `assets/meta/index.json` | `schema`, `generatedAt`, `entries[]` (id, title, year, type, poster) |
| `assets/meta/<id>.json` | ein Snapshot je Katalogtitel |

Erzeugt mit:

```sh
python3 tools/fetch/tvmaze_snapshots.py --root .
```

- Serien (`id` = `tvmaze-<n>`): `https://api.tvmaze.com/shows/<n>?embed=episodes`
  (keyless, CC BY-SA 4.0), 0,6 s Abstand (Limit 20 Calls/10 s), 3 Versuche mit
  Backoff, Host-Allowlist gegen SSRF.
- Filme (`id` = `wiki-…`): Katalogstand, `source: "catalog"`, `live: false`.
- Fällt TVmaze aus, wird der Katalogstand geschrieben und `live: false` gesetzt —
  keine erfundenen Daten.

## Snapshot-Schema (Kurzform)

```
schema:int  id:string  source:tvmaze|catalog|wikipedia  sourceId:string?
fetchedAt:string?  live:bool  attribution:string
title:string  year:int?  type:movie|series  overview:string  poster:string
genres:string[]  rating:double?  runtime:int?
episodes: [{ id:long, season:int, number:int, name:string,
             airdate:string?, runtime:int?, image:string? }]
```

Validierung: `tools/validate_packs.py` prüft id==Dateiname, Schema 1, Typen,
Episoden-IDs, `live ⇒ source=tvmaze + sourceId + fetchedAt` sowie die
Konsistenz zwischen `index.json` und den Dateien.

## Client

- `SnapshotRepository` (core:meta) liest Index + Dateien, cached, sucht lokal
  über Titel/Genres/Overview; leerer Suchbegriff → bestbewertet.
- `SnapshotMetadataSource` ist die einzige verdrahtete Suchquelle; der
  Katalog dient als Fallback für fehlende Dateien.
- `SnapshotEpisodeSource` liefert die Folgen per Katalog-ID — kein
  Namens-Matching, kein Netz.
- `MetaModule` bietet **keine** `TvmazeMetadataSource`/`WikipediaMetadataSource`/
  `TvmazeEpisodeSource` mehr an. Die Klassen bleiben für Werkzeuge/Tests
  erhalten; die TMDB-Interfaces in `MetaRepository.kt` bleiben unverdrahtet.
- Info-Screen (`Optionen → Info & Quellen`) nennt TVmaze/Wikipedia-Attribution,
  Snapshot-Zahl (46), live-Zahl (30) und den Snapshot-Stand.

## Geräte-Nachweis (A52, Build `2f23196`, 17.11.2026)

| Prüfung | Ergebnis |
|---|---|
| Detail „Breaking Bad" | 62 Folgen aus dem Snapshot (UI-Dump, `07.xml`) |
| Info & Quellen | „46 Titel, davon 30 live", Stand `2026-11-17T15:25:42Z`, Attribution sichtbar |
| Suche | Quellenzeile „snapshots, catalog" — keine Live-Quelle mehr |
| Crash | 0 Fatal im Installationsjob `job-6924ad4aad2e49c4` |

**Grenze:** Der Nachweis „kein Netzwerk" wurde über die Verdrahtung und die
Quellenzeile geführt, nicht mit abgeschaltetem Netz (kein Flugmodus-Eingriff).
