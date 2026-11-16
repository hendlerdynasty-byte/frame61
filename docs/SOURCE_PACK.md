# SOURCE_PACK.md — Vertrag für Source Packs

Ein Source Pack ist die JSON-Datei `assets/sources/<id>.json`. Sie beschreibt
**wie** eine Quelle abgefragt und ausgewertet wird — nie, ob sie legal ist.

## Pflichtfelder

| Feld | Typ | Bedeutung |
|------|-----|-----------|
| `id` | string | Paket-ID, identisch mit dem Dateinamen |
| `name` | string | Anzeigename |
| `baseUrl` | string | Basis-URL, ohne abschließenden `/` |
| `search` \| `detail` \| `episodes` \| `streams` | object | mindestens eine Regel; das Pack braucht **eine** adressierbare Regel (mit `endpoint` oder `path`) |

## Optionale Felder

| Feld | Typ | Standard | Bedeutung |
|------|-----|----------|-----------|
| `version` | int | `1` | Schema-Version des Packs |
| `minEngine` | string | – | jüngste Engine-Version, die das Pack versteht |
| `tier` | string | – | `A` \| `B` \| `C` (Beschreibung, keine Steuerung) |
| `legalFree` | bool | `false` | kennzeichnet legal-freie Quellen; wird bei Gleichstand bevorzugt |
| `kind` | string | `html` | `html` (Jsoup-CSS) oder `api` (JSON-Dot-Path) |
| `verified` | bool | `false` | live nachgewiesen — **nur** mit echten Antworten setzen |

## Regeln (`search`, `detail`, `episodes`, `streams`)

### Request

| Feld | Typ | Bedeutung |
|------|-----|-----------|
| `endpoint` | string | Pfad auf `baseUrl`, z. B. `/metadata/{titleId}` |
| `path` | string | Alternative zu `endpoint` |
| `method` | string | `GET` (Standard) oder `POST` |
| `headers` | map | zusätzliche HTTP-Header |
| `body` | string | Request-Body bei `POST` |
| `queryParam` | string | Name des Query-Parameters für den Suchbegriff |
| `pageLimit` | int | Seitenlimit der Quelle |

Platzhalter in `endpoint`/`path`/`body`: `{{query}}`, `{{q}}`, `{{page}}`,
`{{titleId}}`, `{{id}}`, `{{episodeId}}`. Nicht ersetzte Platzhalter bleiben
stehen; unbekannte Parameter werden ignoriert.

### HTML-Dialekt (`kind = "html"`)

| Feld | Bedeutung |
|------|-----------|
| `resultSelector` | CSS-Selektor auf die Treffer-Knoten |
| `titleSelector`, `yearSelector`, `typeSelector` | Feldselektor **innerhalb** eines Treffers |
| `linkSelector` | Selektor auf das Link-Element (Standard: erstes `<a>`) |
| `idAttr` | Attribut mit der ID (Standard: `href`) |
| `episodeSelector`, `seasonSelector`, `episodeTitleSelector`, `episodeLinkSelector`, `episodeIndexSelector` | Episodenliste |
| `selector` | Stream-Knoten (z. B. `.player-source a`) |
| `urlAttr` | Attribut mit der Stream-URL (Standard: `href`) |
| `qualitySelector`, `langSelector`, `hosterSelector` | Feldselektoren im Stream-Knoten |
| `healthAttr`, `minHealth` | Health-Attribut und Mindestschwelle; Treffer darunter fallen weg |
| `qualityMap`, `langMap` | Rohwert → interner Wert (`{"HD": "1080"}`, `{"DE": "de"}`) |
| `referrers` | erlaubte Herkunftsdomains |

**Selektor-Syntax:** `h3 a@text` liefert den Text, `a@href` den Attributwert,
`h3 a` entspricht `@text`. Attribute mit `abs:`-Präfix (z. B. `abs:href`) liefert
Jsoup absolut aufgelöst.

### API-Dialekt (`kind = "api"`)

| Feld | Bedeutung |
|------|-----------|
| `jsonPath` | Dot-Path auf das Ergebnis-Array, z. B. `response.docs` |
| `idPath`, `titlePath`, `yearPath`, `typePath` | Feld-Paths **innerhalb** eines Treffers |
| `streamPath` | Dot-Path auf die Stream-Liste (Standard: `jsonPath`) |
| `streamNamePath` | Dot-Path auf den Dateinamen |
| `streamQualityPath`, `streamLangPath` | Feld-Paths im Stream-Eintrag |
| `streamUrlTemplate` | URL-Vorlage, z. B. `https://archive.org/download/{id}/{name}` |

**Dot-Path:** `a.b.0.c` durchläuft Objekte und Arrays; numerische Segmente
indexieren Arrays.

## Validierung

`PackLoaderValidator` verwirft ein Pack, wenn `id`, `name` oder `baseUrl` fehlen,
`kind` unbekannt ist, keine Regel vorhanden ist oder keine Regel ein
`endpoint`/`path` hat. Ein verworfener Pack beeinflusst andere Packs nicht.
Der Ablehnungsgrund ist über `FilePackLoader.loadWithReason` sichtbar.

## Fixture-Vertrag

```
scraper/engine/src/test/resources/fixtures/<packId>/
  meta.json                Provenienz: id, provenance, capturedAt, sourceUrl, responseSha256, note
  <check>.body.<ext>       Eingabe (aufgezeichnet oder modelliert)
  <check>.expected.json    erwartetes Ergebnis
```

`<check>` ist `search`, `detail`, `episodes` oder `streams`. Ein fehlendes
Fixture überspringt den Test (kein Fehler), ein vorhandenes, nicht passendes
Fixture lässt ihn fehlschlagen.

`meta.json` gibt mit `provenance` an, was das Fixture beweist:

- `recorded` — echte Antwort der Quelle, im Feld `responseSha256` nachprüfbar
- `synthetic` — Strukturmodell ohne Live-Mitschnitt; belegt den Parser, nicht den
  Live-Stand der Seite

Ein `synthetic`-Fixture ist **kein** Nachweis, dass die Selektoren auf der
heutigen Seite noch greifen.