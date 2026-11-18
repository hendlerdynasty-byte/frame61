# Indexseiten — Stand und Nachweise

Stand: 18.11.2026. Ziel waren 14 Indexseiten mit echter Suche, getrimmter
Aufzeichnung (`responseSha256`) und `verified`. Erreicht sind **10 von 14**:
aniworld, bs-to, cine, filmpalast, filmstarts, filmo, moflix, s.to,
serienstream und streamcloud. Vier Seiten sind nach jeweils mindestens drei
Domain-/Pfadversuchen als `blocked_external` mit Beleg versehen: kinox,
kinokiste, megakino und movie2k.

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
| 2 | burningseries | `bs-to` | `verified` | POST `/search`, `option[value]` + `idFromTitle`; Fixture `sourceId`-Liste → `/serie/Naruto` |
| 3 | filmpalast.to | `filmpalast` | `verified` | GET `/search/title/Inception`; 1 Treffer; Fixture `8156913a…` |
| 4 | filmstarts.de | `filmstarts` | `verified` | GET `/_/autocomplete/Inception`; 3 Treffer; Fixture `b0a7a1c9…` |
| 5 | kinokiste.cloud | `kinokiste` | `blocked_external` | JWT-/Bot-Gate: 477/401, Chrome-Headless 40 B; WebView-Cookie-Pfad ausgefuehrt |
| 6 | kinox.to | `kinox` | `blocked_external` | 6 Rotationsdomains geprueft: kion.ru, JS-Redirects, DNS, Domain-for-sale, /lander |
| 7 | megakino.de | `megakino` | `blocked_external` | DNS nicht aufloesbar, frueher 436; .co/.to Platzhalter; Header/WebView erfolglos |
| 8 | moflix | `moflix` | `verified` | `moflix-stream.top/search/Inception`, eingebettetes Loader-JSON, 5 Treffer |
| 9 | movie2k | — | `blocked_external` | `.to` Parking, `.ch` 6-KB-Platzhalter, `movie2ktv.cyou` Vue-Suche ohne Serverantwort |
| 10 | s.to / SerienStream | `s-to` | `verified` | GET `/api/search/suggest?term=black%20doves`; Fixture `7c49b699…` |
| 11 | serienstream.to | `serienstream` | `verified` | gleiche API/Domain wie s.to; eigener Pack, gleiches Fixture |
| 12 | streamcloud | `streamcloud` | `verified` | `streamcloud.plus` → 302 `streamcloud.date`, `/suche?q=Inception`, 1 Treffer |
| 13 | filmo.to | `filmo` | `verified` | GET `/search/suggest?q=inception`; Fixture `b0bf2ef8…` |
| 14 | cine.to | `cine` | `verified` | POST `/request/search` (AJAX), 1 Treffer; Fixture im Repo |

## Verifizierte Suchpfade

| Pack | Suchregel | Detail | Fixture |
|---|---|---|---|
| `aniworld` | POST `/ajax/search`, `keyword={{query}}`, JSON-Array, `link`/`title` | HTML `/anime/stream/{titleId}`, `h1`; Folgen `table.seasonEpisodesList` | `search.body.json`, Hash `ae4d9d4c21e47c04…` |
| `bs-to` | POST `/search`, `search[text]={{query}}`, `option[value]`, `idFromTitle` `^(.+)$` | HTML `/serie/{titleId}`, `h2` | `search.body.html`, Hash im Fixture-meta |
| `cine` | POST `/request/search`, Formularfelder inkl. `rating=1`, JSON `entries[]`, `imdb`/`title`/`year` | – | `search.body.json`, Hash im Fixture-meta |
| `moflix` | GET `/search/{query}`, `jsonExtractRegex` auf `loaders.searchPage.results`, JSON `id`/`name`/`year` | – | `search.body.json`, Hash im Fixture-meta |
| `streamcloud` | GET `/suche?q=`, `a.cv`, `.cv-t`, `.cv-m` | HTML `/filme/{titleId}`, `.sc-h1` | `search.body.html`, Hash im Fixture-meta |
| `filmpalast` | GET `/search/title/{{query}}`, `article.liste`, `h2 a` | HTML `/stream/{titleId}`, `[itemprop="name"]`; Stream `ul.currentStreamLinks a.iconPlay` | `search.body.html`, Hash `8156913a3f3fc504…` |
| `filmstarts` | GET `/_/autocomplete/{query}`, `results[]`, `entity_id`/`label`/`facet` | – | `search.body.json`, Hash `b0a7a1c970898939…` |
| `filmo` | GET `/search/suggest?q=`, `movies[]`, `url`/`title` | HTML `/movies/{titleId}`, `h1` | `search.body.json`, Hash `b0bf2ef8532d524f…` |
| `s-to`, `serienstream` | GET `/api/search/suggest?term=`, `shows[]`, `url`/`name` | HTML `/serie/{titleId}`, `h1` | `search.body.json`, Hash `7c49b69909f88f35…` |

Die vollstaendigen Hashes stehen in den jeweiligen `meta.json`; die
Vollantwort-Hashes liegen dort als `fullResponseSha256`.

## Blockaden und naechster technischer Schritt

- **Burning Series**: mit `idFromTitle` gelöst: Die `<option>`-Liste liefert
  den Klartext `Naruto`, der als ID für `/serie/Naruto` genutzt wird.
- **Cine.to**: Der AJAX-Endpunkt `/request/search` wurde im Netzwerk-Log der
  Seite gefunden (`core.js`); mit `language=0`, `rating=1`, `year[]=1900`,
  `year[]=2026` liefert er echte Treffer.
- **Moflix**: `moflix-stream.top` (Board-Meldung zur Ersatzdomain) liefert
  `loaders.searchPage.results` im HTML; `jsonExtractRegex` hebt das JSON
  heraus. `.xyz` bleibt Cloudflare 403, `.com` parking.
- **Streamcloud**: `streamcloud.plus` leitet auf `streamcloud.date`; dort
  liegen echte Treffer unter `/suche?q=`. `.my` timeout, `.eu` geparkt.
- **Kinokiste**: 477/401, Chrome-Headless 40 B; Block-13-WebView-Cookie-Pfad
  ausgefuehrt, danach weiter keine Selektoren.
- **Kinox**: sechs Rotationsdomains geprueft (kion.ru, JS-Redirects, DNS,
  Domain-for-sale, /lander) — kein Index erreichbar.
- **MegaKino**: DNS nicht aufloesbar, frueher 436; .co/.to Platzhalter;
  Header-Profil und WebView-Pfad erfolglos.
- **Movie2k**: `.to` Parking, `.ch` 6-KB-Platzhalter,
  `movie2ktv.cyou` Vue-Suche ohne Serverantwort. Fuer diese vier Domains
  gibt es bewusst keinen Attrappen-Pack, sondern `blocked_external`.

## Offener Umfang

- `s-to`/`serienstream` liefern Suche und Detail; die Folgenliste nutzt
  `onclick` auf der Tabellenzeile und wird noch nicht geparst.
- `filmo` liefert Suche und Detail; die Provider-Chips haben nur ein
  `data-p`-Token, keinen Stream-Link im HTML.
- `filmstarts` ist eine Metadaten-Suche ohne Streams (kein Hoster).

## Gate-Nachweis (18.11.2026)

Aktuell: `frame61-f843b2abc544-1795022941`, Exit 0, **104/104 Tests**,
Debug-APK `68ffe83bed006247426e40b739be48c6f76a3262d8ebbef2cbfdf41db2691627`
(31,6 MiB). Damit sind 10 von 14 Packs live verifiziert; vier Reste haben
Domain-/Pfadbelege im Pack und in diesem Dokument.

Aelterer Beleg:

- Remote-Gate `frame61-0c7da0c0539d-1794987598`, Quellstand `0c7da0c`, Exit 0.
- Debug-APK `f8a9ac79d2a439aa8216ba3c91913252e8ab785230871ab0c71c556c6e5013f7`,
  24.434.378 Bytes (unter 45 MiB).
- `PackFixtureTest` 7 Tests, 0 Fehler; darin die Fixture-Kette fuer alle sechs
  verifizierten Suchantworten. `validatePacks`, `packsReport --check`,
  `testFetchTools` und `checkApkSize` liefen im Gate mit.
- Der Gate belegt Parser und Aufzeichnungen, nicht die Wiedergabe der
  blockierten Seiten.

