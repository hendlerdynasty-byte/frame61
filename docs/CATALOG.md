# Katalog — 600 Titel mit lokalen Postern

Stand: 18.11.2026. Der gebundelte Katalog umfasst **600 Serien** aus der
keylosen TVmaze-API. Jeder Titel hat einen lokalen Poster, einen
Meta-Snapshot und stabile IDs (`tvmaze-<id>`). Die App bleibt ohne Netz
benutzbar; Live-Quellen sind additiv.

## Erzeugung

`tools/fetch/catalog_builder.py`:

- liest `https://api.tvmaze.com/shows?page=0..7`,
- nimmt nur Scripted/Animation/Documentary/Mini-Series mit Poster und
  Premierendatum,
- dedupliziert nach normalisiertem Titel + Jahr,
- sortiert nach TVmaze-Gewicht, Rating und ID,
- laedt `image.medium`, skaliert auf **180 × 270** (2:3) und speichert JPEG
  (Qualitaet 72) unter `app/src/main/assets/posters/tvmaze-<id>.jpg`,
- schreibt `catalog/catalog.json` (Version 3) und je Titel
  `meta/tvmaze-<id>.json`; vorhandene Episodenlisten bleiben erhalten.

```
python3 -I tools/fetch/catalog_builder.py --dry-run
python3 -I tools/fetch/catalog_builder.py
```

Netzwerkzugriffe sind auf `https`, `api.tvmaze.com` und `*.tvmaze.com`
beschraenkt, loesen oeffentliche IPs auf und folgen keinen Redirects.

## Umfang

| Artefakt | Anzahl | Groesse |
|---|---|---|
| Katalogeintraege | 600 | ~464 KB |
| lokale Poster | 600 | ~7,3 MB |
| Meta-Snapshots | 600 | ~2,5 MB |

Der Validator prueft fuer jeden Eintrag: eindeutige ID, eindeutiger
Titel/Jahr-Schluessel, Typ `series`, nicht-leerer Posterpfad und existierende
Posterdatei. `CatalogRepositoryTest` verlangt mindestens 600 Titel, lokale
Poster und Duplikatfreiheit.

## Oberflaeche

- **Filme/Serien** (`BrowseScreen`): Genre- und Jahrzehnt-Filter als
  D-Pad-taugliche Pillen, Trefferzahl, 2:3-Grid mit `PosterCard`.
- **Fortschritt pro Titel**: `ResumeRepository.progressByTitle` liest den
  letzten Stand je Titel aus Room; die Karte zeigt eine 4-dp-Spur am unteren
  Rand, innerhalb des Poster-Clips.
- **Meine Liste**: DataStore-persistent; die Watchlist-Ansicht nutzt dieselbe
  Karte und denselben Fortschritt.

## Filter

- Genre: alle im Katalog vorkommenden Genres, deutsch gemappt
  (z. B. `Comedy` → `Komödie`, `Crime` → `Krimi`).
- Jahrzehnt: `1950er` bis `2020er`, absteigend; „Alle Jahre“ setzt zurueck.

## Geraetenachweis (A52)

| Bild | Inhalt |
|---|---|
| `docs/design/block10/series/06.png` | Serien: 584 Titel, Genre-/Jahrzehnt-Pillen, lokale Poster |
| `docs/design/block10/filters/05.png` | Filme: 16 Seed-Titel, gleiche Filter |
| `docs/design/block10/filters2/07.png` | Genre Action: 162 Titel |
| `docs/design/block10/filters2/10.png` | Action + 2020er: 7 Titel |
| `docs/design/block10/progress4/20.png` | Fortschrittsspur nach 60 s Wiedergabe (Stranger Things) |

Die Bilder stammen aus dem QUICK_SMOKE-Lauf `job-114ada04f6c44d8b`
(PASS, APK `1945345c…`) und interaktiven Bruecken-Sitzungen am A52.

## Gate-Nachweis (18.11.2026)

- Remote-Gate `frame61-5d75e47dbe09-1794995232`, Quellstand `5d75e47`, Exit 0.
- Debug-APK `657cf2cbc13c55647b83ddff3d571ea02f41cc4d6d2eeedda8f81b159760d944`,
  31.597.522 Bytes (30,1 MiB, unter 45 MiB).
- `CatalogRepositoryTest` 7 Tests, 0 Fehler; Validator 600 Katalogeintraege,
  600 Meta-Snapshots, 600 Posterdateien.
- QUICK_SMOKE `job-ec3bbb8bb0814803` PASS auf dem A52.

## Offen

- Neue Titel haben leere `episodes`-Listen im Snapshot; die Detailansicht
  nutzt fuer sie die bestehende Live-TVmaze-Quelle. Die 46 alten Snapshots
  behalten ihre Episoden.
- Der Katalog enthaelt Serien, keine Spielfilme. Filme kommen aus den
  Metadaten-Quellen bzw. den Index-Packs.
