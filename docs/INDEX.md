# Indexseiten — Stand und Nachweise

Stand: 18.11.2026. Ziel waren 14 Indexseiten mit echter Suche, getrimmter
Aufzeichnung (`responseSha256`) und `verified`. Erreicht sind **6 von 14**:
aniworld, filmpalast, filmstarts, filmo, s.to und serienstream. Fuenf weitere
Seiten sind erreichbar, aber ohne auswertbaren Suchpfad; drei Domains sind tot.
Jeder nicht verifizierte Pack traegt `check.status` und `check.reason`.

## Methode

`tools/fetch/pack_worker.py` holt die Suchantwort mit dem Pack-Ziel aus
`tools/fetch/targets.json`, prueft die Selektoren, schreibt Status und bei
Erfolg ein Fixture unter `scraper/engine/src/test/resources/fixtures/<id>/`.
`format` pro Regel erlaubt gemischte Packs: JSON-Suche, HTML-Detail.

```
python3 -I tools/fetch/pack_worker.py --pack aniworld --pack filmpalast ...
python3 -I tools/validate_packs.py
```

Der Fixture-Test (`PackFixtureTest`) parst jede Aufzeichnung mit derselben
Engine wie die App. `provenance: recorded`, `responseSha256` und
`fullResponseSha256` binden die getrimmte Datei an die Vollantwort.

## Status der 14 Seiten

| # | Seite | Pack | Status | Beleg / Grund |
|---|---|---|---|---|
| 1 | aniworld.to | `aniworld` | `verified` | POST `/ajax/search` `keyword=naruto`; 2 Treffer; Fixture `ae4d9d4c…` |
| 2 | burningseries | `bs-to` | `selector_missing` | `/search` liefert nur `<option value="134">`-Liste, keinen Slug-Link |
| 3 | filmpalast.to | `filmpalast` | `verified` | GET `/search/title/Inception`; 1 Treffer; Fixture `8156913a…` |
| 4 | filmstarts.de | `filmstarts` | `verified` | GET `/_/autocomplete/Inception`; 3 Treffer; Fixture `b0a7a1c9…` |
| 5 | kinokiste.cloud | `kinokiste` | `selector_missing` | JWT-/Bot-Gate, Folgeabruf ohne Browser-Session HTTP 401 |
| 6 | kinox.to | `kinox` | `selector_missing` | nur Meta-Refresh auf `kion.ru`; kein Index mehr |
| 7 | megakino.de | `megakino` | `selector_missing` | Erstaufruf HTTP 436, aktuell keine Suchtreffer |
| 8 | moflix | — | `blocked_external` | `moflix-stream.com` parkt/leitet auf `yfdabv11.com`, `.xyz` Cloudflare 403 |
| 9 | movie2k | — | `blocked_external` | `movie2k.to` leitet auf Domain-Parking `yfdabv11.com` |
| 10 | s.to / SerienStream | `s-to` | `verified` | GET `/api/search/suggest?term=black%20doves`; Fixture `7c49b699…` |
| 11 | serienstream.to | `serienstream` | `verified` | gleiche API/Domain wie s.to; eigener Pack, gleiches Fixture |
| 12 | streamcloud | — | `blocked_external` | `streamcloud.eu` parkt (`ww38.streamcloud.eu`), `.sx` ohne Index |
| 13 | filmo.to | `filmo` | `verified` | GET `/search/suggest?q=inception`; Fixture `b0bf2ef8…` |
| 14 | cine.to | `cine` | `selector_missing` | Suche filtert clientseitig, kein servergerenderter Pfad |

## Verifizierte Suchpfade

| Pack | Suchregel | Detail | Fixture |
|---|---|---|---|
| `aniworld` | POST `/ajax/search`, `keyword={{query}}`, JSON-Array, `link`/`title` | HTML `/anime/stream/{titleId}`, `h1`; Folgen `table.seasonEpisodesList` | `search.body.json`, Hash `ae4d9d4c21e47c04…` |
| `filmpalast` | GET `/search/title/{{query}}`, `article.liste`, `h2 a` | HTML `/stream/{titleId}`, `[itemprop="name"]`; Stream `ul.currentStreamLinks a.iconPlay` | `search.body.html`, Hash `8156913a3f3fc504…` |
| `filmstarts` | GET `/_/autocomplete/{query}`, `results[]`, `entity_id`/`label`/`facet` | – | `search.body.json`, Hash `b0a7a1c970898939…` |
| `filmo` | GET `/search/suggest?q=`, `movies[]`, `url`/`title` | HTML `/movies/{titleId}`, `h1` | `search.body.json`, Hash `b0bf2ef8532d524f…` |
| `s-to`, `serienstream` | GET `/api/search/suggest?term=`, `shows[]`, `url`/`name` | HTML `/serie/{titleId}`, `h1` | `search.body.json`, Hash `7c49b69909f88f35…` |

Die vollstaendigen Hashes stehen in den jeweiligen `meta.json`; die
Vollantwort-Hashes liegen dort als `fullResponseSha256`.

## Blockaden und naechster technischer Schritt

- **Burning Series**: Die Suche liefert eine Auswahlliste
  (`<option value="134">Naruto</option>`). Der Serien-Slug steht nur im
  Klartext-Label. Ein `idFromTitle`/Slug-Feld im Schema wuerde das loesen.
- **Kinokiste**: JWT im Loader; ohne Browser-Session HTTP 401. Braucht
  Cookie-/Token-Handoff aus dem WebView.
- **Kinox**: Domain zeigt nur einen Meta-Refresh auf `kion.ru`. Kein Pack-Ziel.
- **Megakino**: HTTP 436 beim Erstaufruf, danach Bot-/JS-Seite ohne Treffer.
- **Cine.to**: Suche filtert die bereits geladene DOM-Liste; kein
  serverseitiger Suchpfad.
- **Moflix / Movie2k / Streamcloud**: Domains parken oder sind nicht
  aufloesbar. Fuer sie gibt es bewusst keinen Pack, statt Attrappen zu bauen.

## Offener Umfang

- `s-to`/`serienstream` liefern Suche und Detail; die Folgenliste nutzt
  `onclick` auf der Tabellenzeile und wird noch nicht geparst.
- `filmo` liefert Suche und Detail; die Provider-Chips haben nur ein
  `data-p`-Token, keinen Stream-Link im HTML.
- `filmstarts` ist eine Metadaten-Suche ohne Streams (kein Hoster).
