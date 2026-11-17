# SCHEMA.md — eingefrorenes Datenformat (Version 1)

Stand: 2026-11-17 · Status: **eingefroren für M1–M7**. Änderungen nur additiv;
ein Bruch braucht `version`-Bump, `minEngine` und einen neuen Gold-Fixture-Satz.

Gültige Referenzen: `fixtures/gold-01.json` (HTML-Pack) und
`fixtures/gold-02.json` (API-Pack). Beide bestehen den Validator.

---

## 1. Source Pack (`assets/sources/<id>.json`)

### 1.1 Kopffelder

| Feld | Typ | Pflicht | Default | Bedeutung |
|---|---|---|---|---|
| `id` | string | ja | – | Pack-ID, identisch mit Dateiname (außer Gold-Fixtures) |
| `name` | string | ja | – | Anzeigename |
| `baseUrl` | string | ja | – | Basis-URL ohne abschließenden `/`; Host für die Worker-Allowlist |
| `version` | int | nein | `1` | Schema-Version des Packs |
| `minEngine` | string | nein | – | jüngste Engine-Version, die das Pack versteht |
| `tier` | `"A"\|"B"\|"C"` | nein | – | Klassifikation, keine Steuerung |
| `legalFree` | bool | nein | `false` | wird bei Mirror-Gleichstand bevorzugt |
| `kind` | `"html"\|"api"` | nein | `"html"` | Auswertungsdialekt |
| `verified` | bool | nein | `false` | **nur** `true` mit echtem Nachweis (Fixture `recorded` oder Worker-Lauf) |
| `last_check` | string | nein | – | ISO-8601 des letzten Worker-Laufs |
| `check` | object | nein | – | `status`, `reason`, `checked[]`, `misses[]`, `http_status` |

### 1.2 Regeln

Erlaubt sind `search`, `detail`, `episodes`, `streams`. Mindestens eine Regel
muss vorhanden sein; mindestens eine braucht `endpoint` oder `path`.

#### Request-Felder (alle Regeln)

| Feld | Typ | Default | Bedeutung |
|---|---|---|---|
| `endpoint` | string | – | Pfad auf `baseUrl` |
| `path` | string | – | Alternative zu `endpoint` |
| `method` | `"GET"\|"POST"` | `"GET"` | HTTP-Methode |
| `headers` | map<string,string> | `{}` | zusätzliche Header |
| `body` | string | – | POST-Body |
| `queryParam` | string | – | Name des Suchparameters |
| `pageLimit` | int | `20` | Seitenlimit |

Platzhalter: `{{q}}`, `{{query}}`, `{{page}}`, `{{titleId}}`, `{{id}}`,
`{{episodeId}}`, `{{name}}`, `{{url}}`. Unbekannte Platzhalter bleiben stehen.

#### HTML-Felder (`kind = "html"`)

| Feld | Regel | Bedeutung |
|---|---|---|
| `resultSelector` | search/detail | Treffer- bzw. Wurzelknoten |
| `titleSelector`, `yearSelector`, `typeSelector` | search/detail | Feld **innerhalb** des Treffers |
| `linkSelector`, `idAttr` | search | Link-Element und ID-Attribut (Default `href`) |
| `episodeSelector` | detail/episodes | Folgenknoten |
| `seasonSelector`, `episodeTitleSelector`, `episodeLinkSelector`, `episodeIndexSelector` | detail/episodes | Folgenfelder |
| `selector` | streams | Stream-Knoten |
| `urlAttr` | streams | Attribut der Stream-URL (Default `href`) |
| `qualitySelector`, `langSelector`, `hosterSelector` | streams | Feldselektoren im Stream-Knoten |
| `healthAttr`, `minHealth` | streams | Health-Attribut und Mindestwert |
| `qualityMap`, `langMap` | streams | Rohwert → interner Wert |
| `referrers` | streams | `[{domain, accept}]` |

Selektor-Syntax: `h3 a@text` (Text), `a@href` (Attribut), `a` = `@text`,
`abs:href` = absolut aufgelöste URL. Erlaubt sind Tag, `.class`, `#id`,
`[attr]`, `[attr=value]` und Nachfahren (`a b`).

#### API-Felder (`kind = "api"`)

| Feld | Regel | Bedeutung |
|---|---|---|
| `jsonPath` | search/detail/episodes/streams | Dot-Path auf Array/Objekt |
| `idPath`, `titlePath`, `yearPath`, `typePath` | search/detail | Feld-Paths im Treffer |
| `streamPath`, `streamNamePath`, `streamQualityPath`, `streamLangPath` | streams | Pfade in der Stream-Liste |
| `episodeSeasonPath`, `episodeNumberPath` | episodes | Staffel-/Folgennummer im API-Eintrag; sonst aus dem Titel (`(S01/E02)`) oder Index |
| `streamUrlTemplate` | streams | URL-Vorlage, z. B. `https://cdn/{{id}}/{{name}}` |

Dot-Path: `a.b.0.c`; numerische Segmente indexieren Arrays.

### 1.3 Beispiel

Siehe `fixtures/gold-01.json` (HTML) und `fixtures/gold-02.json` (API).

---

## 2. Hoster Pack (`assets/hosters/<id>.json`)

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | string | ja | identisch mit Dateiname |
| `name` | string | ja | Anzeigename |
| `domains` | string[] | ja | Domain-Match |
| `verified` | bool | nein | Live-Nachweis |
| `captcha` | `"webview"` | nein | erfordert sichtbaren WebView |
| `result` | string | nein | erwarteter Ergebnistyp (`file`, `url`, …) |
| `steps` | object[] | ja | Aktionskette |

Schritte: `{action: "get"|"post"|"regex"|"json"|"sleep"|"copy", ...}` mit
`url`, `headers`, `body`, `input`, `pattern`, `group`, `path`, `saveAs`,
`seconds`. Jeder Schritt speichert unter `saveAs`; `regex` braucht `pattern`,
`json` braucht `path`, `get`/`post`/`copy` brauchen `url`.
`copy` reicht `{{url}}` unverändert durch (Direktstream ohne Hoster-Seite).

---

## 3. Metadaten-Snapshot (`assets/meta/<id>.json`)

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `schema` | int | ja | `1` |
| `id` | string | ja | identisch mit Katalog-ID/Dateiname |
| `source` | `"tvmaze"\|"catalog"\|"wikipedia"` | ja | Herkunft |
| `sourceId` | string | bei `live` | ID bei der Quelle |
| `fetchedAt` | string | bei `live` | ISO-8601 |
| `live` | bool | ja | `true` nur bei echtem Abruf |
| `attribution` | string | ja | z. B. `TVmaze (CC BY-SA 4.0)` |
| `title` | string | ja | Anzeigetitel |
| `year` | int | nein | Jahr |
| `type` | `"movie"\|"series"` | ja | Typ |
| `overview` | string | nein | Klartext ohne HTML |
| `poster` | string | nein | Bild-URL |
| `genres` | string[] | nein | Genres |
| `rating` | number | nein | Bewertung |
| `runtime` | int | nein | Minuten |
| `episodes` | object[] | ja | `{id, season, number, name, airdate?, runtime?, image?}` |

`index.json`: `{schema, generatedAt, entries:[{id,title,year,type,poster}]}`.
Jeder Index-Eintrag braucht eine Datei; jede Datei braucht einen Index-Eintrag.

---

## 4. Fixture-Vertrag

```
scraper/engine/src/test/resources/fixtures/<packId>/
  meta.json                provenance: "recorded" | "synthetic", capturedAt,
                           sourceUrl (recorded), responseSha256 (recorded)
  <check>.body.<ext>       Eingabe
  <check>.expected.json    erwartetes Ergebnis
```

`<check>` ∈ `search`, `detail`, `episodes`, `streams`. Fehlendes Fixture =
Test skipped; vorhandenes, nicht passendes Fixture = Test rot.
`recorded` beweist eine echte Antwort; `synthetic` nur den Parser.

---

## 5. Validierungsregeln (maschinell)

`tools/validate_packs.py` (läuft in `scripts/preflight.sh` und `./gradlew gate`):

- Source-Pack: `id`/`name`/`baseUrl` nicht leer, `id` == Dateiname,
  `kind` ∈ {html, api}, mindestens eine Regel, mindestens eine mit
  `endpoint`/`path`, HTML-Pflichtselektoren (`search.resultSelector`,
  `streams.selector`, `episodes.episodeSelector`), API-Pflichtpfade
  (`search/detail.jsonPath`, `streams.streamPath|jsonPath|streamUrlTemplate`).
- `verified: true` ist nur mit Fixture `provenance: recorded` **und**
  `responseSha256` zulässig.
- Hoster: `domains`/`steps` nicht leer, gültige `action`, Pflichtfelder je Aktion.
- Skin: alle 16 Tokens, `#RRGGBB`-Farben, `id` == Dateiname.
- Katalog: `items` nicht leer, eindeutige IDs, `type` ∈ {movie, series}.
- Meta: `schema == 1`, `id` == Dateiname, Index/Dateien konsistent,
  `live ⇒ source=tvmaze + sourceId + fetchedAt`, Episoden-IDs eindeutig.
- Gold-Fixtures: `fixtures/gold-01.json`, `gold-02.json` müssen bestehen.

Zusätzlich: `tools/fetch/test_cssmini.py` (Gate-Task `testFetchTools`).

---

## 6. Versionierung

- Additive Felder sind erlaubt, ohne `version` zu erhöhen (Leser ignorieren
  Unbekanntes).
- Entfernen/Umbenennen/Typwechsel: `version` erhöhen, `minEngine` setzen,
  Migration dokumentieren, neuen Gold-Fixture-Satz anlegen.
- `verified` wird nie „aus Gewohnheit" gesetzt; ein abgelaufener Nachweis bleibt
  mit `last_check` und `check.status` sichtbar.
