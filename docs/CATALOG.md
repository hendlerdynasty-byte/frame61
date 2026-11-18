# Katalog — 1000 Titel mit lokalen Postern

Stand: 18.11.2026. Der gebundelte Katalog umfasst **1000 Titel**: 584 Serien
aus TVmaze, 16 Seed-Filme und 400 Filme aus Wikidata/Wikimedia. Jeder Titel hat
einen lokalen Poster, einen Meta-Snapshot und eine stabile ID. Die App bleibt
ohne Netz benutzbar; Live-Quellen sind additiv.

## Erzeugung

`tools/fetch/catalog_builder.py` (Serien):

- liest `https://api.tvmaze.com/shows?page=0..7`,
- nimmt Scripted/Animation/Documentary/Mini-Series mit Poster und Datum,
- dedupliziert nach Titel/Jahr, sortiert nach Gewicht/Rating/ID,
- skaliert Poster auf 180 × 270 JPEG (Qualität 72).

`tools/fetch/film_builder.py` (Filme):

- SPARQL-Export aus `query.wikidata.org` (Filmklasse Q11424, Jahr 1930–2026,
  Poster vorhanden),
- `wbgetentities` in 50er-Batches für **deutsche** Beschreibung, Genres (P136)
  und Laufzeit (P2047),
- Poster über `Special:FilePath` → `thumb.wikimedia.org`, Host-Validierung
  und 180 × 270-Zuschnitt,
- nur neue Titel (Dedupe gegen Serien und untereinander), IDs `wiki-Q<id>`.

```
python3 -I tools/fetch/catalog_builder.py
python3 -I tools/fetch/film_builder.py --sparql-cache /tmp/... --meta-cache /tmp/... --count 400 --extra 10
```

Netzwerkzugriffe sind auf HTTPS und feste Hosts beschränkt. Cache-Dateien
liegen unter `/tmp`, nie im Repo.

## Umfang

| Artefakt | Anzahl |
|---|---|
| Katalogeinträge | **1000** (584 Serien, 416 Filme) |
| lokale Poster | 1009 |
| Meta-Snapshots | 1000 |

Der Validator prüft eindeutige IDs/Titel-Jahr-Schlüssel, Typ, Posterpfad und
Posterdatei. `CatalogRepositoryTest` verlangt ≥ 1000 Titel und ≥ 400 Filme.

## Oberfläche

- **Filme/Serien** (`BrowseScreen`): Genre-, Jahrzehnt- und (bei Filmen)
  Laufzeit-Filter als D-Pad-taugliche Pillen, Trefferzahl, 120er-Seiten,
  2:3-Grid mit `PosterCard`.
- **Fortschritt pro Titel**: `ResumeRepository.progressByTitle` liest den
  letzten Stand aus Room; die Karte zeigt eine 4-dp-Spur am unteren Rand.
- **Meine Liste**: DataStore-persistent, gleiche Karte und Fortschritt.

## Filter

- Genre: deutsche Labels aus TVmaze/Wikidata.
- Jahrzehnt: aufsteigend/absteigend je nach Quelle, „Alle Jahre" setzt zurück.
- Laufzeit: `< 90 Min`, `90–120 Min`, `> 120 Min` (nur Filme mit Laufzeit).

## Geraetenachweis (A52, vor Block 15)

| Bild | Inhalt |
|---|---|
| `docs/design/block10/series/06.png` | Serien: 584 Titel, Genre-/Jahrzehnt-Pillen |
| `docs/design/block10/filters/05.png` | Filme: 16 Seed-Titel |
| `docs/design/block10/filters2/07.png` | Genre Action: 162 Titel |
| `docs/design/block10/progress4/20.png` | Fortschrittsspur nach 60 s Wiedergabe |

Die Bilder stammen aus QUICK_SMOKE `job-114ada04f6c44d8b` und Bruecken-Sitzungen.

## Gate-Nachweis

Block 10: `frame61-5d75e47dbe09-1794995232`, Exit 0, APK `657cf2cb…` (600 Titel).
Block 15: wird nach dem Remote-Gate in `docs/ABSCHLUSS-TV.md` nachgefuehrt.
