# Hoster — Stand und Nachweise

Stand: 18.11.2026. Ziel waren 10 Hoster mit echter Schrittkette, aufgezeichneter
Antwort (`responseSha256`) und Status `verified`. Erreicht ist ein belegter
Hoster (Vidmoly); drei weitere wurden mit echten Zielen durchlaufen und sind an
der Quelle blockiert, sechs haben kein erreichbares Dateiziel. Jeder nicht
verifizierte Pack traegt `check.status` und `check.reason`.

## Methode

`tools/fetch/hoster_worker.py` liest `tools/fetch/hoster_targets.json`, holt die
Seite mit Browser-User-Agent, Referer und Cookie-Jar, fuehrt die Schritte des
Packs aus (maximal 3 Versuche je Aufruf, Backoff) und schreibt:

- `app/src/main/assets/hosters/<id>.json`: `verified`, `last_check`, `check.status`, `check.reason`
- bei Erfolg `scraper/resolver/src/test/resources/fixtures-hosters/<id>/`:
  `page.body.html`, `result.expected.json`, `meta.json` mit `responseSha256`

Der Validator verlangt fuer `verified: true` ein echtes Fixture mit
`provenance: recorded`, `sourceUrl`, `responseSha256` und erwarteter URL.
`HosterFixtureTest` rechnet den SHA-256 der aufgezeichneten Antwort nach und
fuehrt dieselbe Schrittkette wie die App gegen die Aufzeichnung aus.

```
python3 -I tools/fetch/hoster_worker.py
python3 -I tools/validate_packs.py
```

## Status der 10 Hoster

| Hoster | Status | Ziel / Befund |
|---|---|---|
| Vidmoly | `verified` | `vidmoly.net/embed-8irnzzsknabi.html` liefert `sources: [{ file: '…master.m3u8…' }]`; Fixture mit Hash `6db7c3c1…` |
| VOE | `step_failed` | `voe.sx` leitet per JavaScript auf `arianathankspecial.com/e/…`; das HTML enthaelt kein `<source>` (verschleierte Quelle) |
| Doodstream | `step_failed` | `do7go.com` leitet auf `playmogo.com`; Antwort ist Cloudflare `Just a moment...` (HTTP 403) |
| Filemoon | `step_failed` | `filemoon.to/d/…` ist eine SPA ohne Player-HTML; Stream-URL nur ueber `/api/videos/…` mit PoW-Token |
| Streamtape | `no_target` | Homepage ohne oeffentliche Dateiliste; API nur mit Konto |
| Vidoza | `no_target` | Landingpage ohne oeffentliche Dateiliste |
| Mixdrop | `no_target` | `mixdrop.co` TLS-Verbindung fehlgeschlagen (18.11.2026) |
| Luluvdo | `no_target` | `luluvdo.com`/`.net` nicht aufloesbar (18.11.2026) |
| Loadx | `no_target` | `loadx.ws` Timeout (18.11.2026) |
| Streamhide | `no_target` | `streamhide.com` liefert nur einen JS-Redirect ohne Dateilink |
| `direct` (Zusatz) | `no_target` | Direktstream-Pack fuer ARD/MP4/HLS, kein Hoster-Ziel |

## Belegte Ziele

Alle vier Ziele stammen aus einer echten Episode (`aniworld.to`, Naruto S01E01)
und wurden ueber die 301-Redirects der Seite aufgeloest:

| Hoster | Redirect | Zielseite |
|---|---|---|
| Vidmoly | `/redirect/3049671` | `https://vidmoly.net/embed-8irnzzsknabi.html` |
| VOE | `/redirect/3523796` | `https://voe.sx/e/cvrluqyituus` -> `arianathankspecial.com/e/cvrluqyituus` |
| Doodstream | `/redirect/4059006` | `https://do7go.com/e/5hyj5kblvy1n` -> `playmogo.com` |
| Filemoon | `/redirect/2922355` | `https://filemoon.to/d/89crzins1ufc` |

`tools/fetch/hoster_targets.json` haelt Ziel, Referer und Herkunft fest.

## Blockaden und naechster technischer Schritt

- **VOE**: Der Loesungsweg ist ein sichtbarer WebView (Pack traegt
  `captcha: webview`). Ohne WebView-Token-Rueckgabe an den Resolver bleibt die
  URL unerreichbar; die Schrittkette endet korrekt mit `step_failed`.
- **Doodstream**: Cloudflare-Challenge. Ein reiner HTTP-Client kommt ohne
  Challenge-Token nicht durch; WebView oder ein zugelassener Cookie-Jar waere
  noetig.
- **Filemoon**: Die SPA verlangt einen Proof-of-Work-Token
  (`/api/videos/<code>/captcha/...`) vor `/api/videos/stream/<token>`. Das ist
  mit den vorhandenen Schritt-Aktionen (`get`, `regex`, `json`, `sleep`,
  `copy`) nicht darstellbar.
- **Streamtape/Vidoza**: Ohne Konto/API-Key kein oeffentlicher Dateilink.
  Zugangsdaten werden nicht beschafft und nicht im Repo abgelegt.
- **Mixdrop/Luluvdo/Loadx/Streamhide**: Domains sind am 18.11.2026 nicht
  erreichbar oder liefern keine Dateiseite.

Diese Punkte sind externe Blockaden, keine Parserfehler. Die Packs bleiben
geladen und liefern im Betrieb einen klaren `check.reason`; der Resolver faellt
auf den naechsten Hoster zurueck.
