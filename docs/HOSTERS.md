# Hoster — Stand und Nachweise

Stand: 18.11.2026. Ziel waren 10 Hoster mit echter Schrittkette, aufgezeichneter
Antwort (`responseSha256`) und Status `verified`. Erreicht sind **2 von 10**:
Vidmoly und `direct` (ARD-Slice). Die Kette ist seit Block 13 um `js`, `pow`
und Cookie-/WebView-Rueckgabe erweitert; die verbleibenden Hoster haben
benannte, live gepruefte Gruende.

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
| VOE | `step_failed` | Filmpalast-Ziel `voe.sx/pid7osbd3o2b`; JS-Redirect, kein `<source>` im HTML; `js`-Schritt + WebView-Kette vorhanden |
| Doodstream | `step_failed` | `do7go.com` leitet auf `playmogo.com`; Cloudflare `Just a moment...` (HTTP 403); WebView-Cookie-Kette vorhanden |
| Filemoon | `step_failed` | PoW live geloest und per `/captcha/verify` bestaetigt; `/api/videos/{code}/playback` liefert ohne Browser-Kontext HTTP 405 |
| Streamtape | `no_target` | Homepage ohne oeffentliche Dateiliste; API nur mit Konto |
| Vidoza | `no_target` | Landingpage ohne oeffentliche Dateiliste |
| Mixdrop | `no_target` | `mixdrop.co` TLS-Verbindung fehlgeschlagen (18.11.2026) |
| Luluvdo | `no_target` | `luluvdo.com`/`.net` nicht aufloesbar (18.11.2026) |
| Loadx | `no_target` | `loadx.ws` Timeout (18.11.2026) |
| Streamhide | `no_target` | `streamhide.com` liefert nur einen JS-Redirect ohne Dateilink |
| `direct` (Zusatz) | `verified` | ARD-page-gateway-Antwort mit echtem MP4, im ARD-Slice auf dem A52 abgespielt; Fixture `0c7f414f…` |

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

## Block 13 — Code statt Etikett

- **Schema:** `js`-Schritt (WebView, 8 s) und `pow`-Schritt (`filemoon-v1`,
  `sha256`) im Hoster-Pack; beide im Validator und in Unit-Tests abgesichert.
  `PowSolverTest` nutzt eine echte Filemoon-Challenge: Nonce
  `71354556…2adec`, difficulty 16, Lösung `3643` — von
  `/api/videos/<code>/captcha/verify` mit `status: ok` bestaetigt.
- **WebView:** `CaptchaBroker` verbindet `CaptchaWebViewFrame` mit dem
  Resolver. Token, Cookies und ggf. die Stream-URL aus `video/source`,
  `window.sources`, jwplayer und Performance-Eintragen werden an den
  HTTP-Kontext uebergeben; Timeout 20 s, danach naechster Kandidat.
  `WebViewJsEvaluator` fuehrt `js`-Schritte in einer kurzlebigen WebView aus.
- **Quellen-Captcha:** `kinokiste` und `megakino` tragen `captcha: "webview"`;
  ein fehlgeschlagener Suchlauf oeffnet den Frame, uebernimmt die Cookies und
  wiederholt die Suche.
- **Zielherkunft (nicht mehr nur aniworld):**

| Index | Gepruefte Seite | Hoster-Ziele |
|---|---|---|
| aniworld | Naruto S01E01 | VOE `voe.sx/e/cvrluqyituus`, Dood `do7go.com/e/5hyj5kblvy1n`, Filemoon `filemoon.to/d/89crzins1ufc`, Vidmoly `vidmoly.net/embed-8irnzzsknabi.html` |
| filmpalast | The Mummy — Bride of the Dead | VOE `voe.sx/pid7osbd3o2b`, Firestream `firestream.to/e/twtee08p`, Vidaraa `vidaraa.cc/e/zrCCKFq5bLSf5` |
| filmo | Inception | VOE-Provider-Chip (`data-p`-Token, kein direkter Link im HTML) |
| s.to / serienstream | Black Doves S01E01 | `/r?t=…` → 302 `https://voe.sx/e/hn4pl228sgzd` |

Firestream und Vidaraa stehen nicht in den 10 geforderten Ziel-Hostern und
haben daher keinen Pack. Streamtape, Vidoza, Mixdrop, Luluvdo, Loadx und
Streamhide tauchen auf keinem der verifizierten Indizes auf; ihre Gruende
bleiben in `hoster_targets.json` erhalten.

## Gate-Nachweis (18.11.2026)

- Remote-Gate `frame61-037fc63f6f75-1795015007`, Quellstand `037fc63`, Exit 0.
- Debug-APK `94b7d82fba7e94af903d02dc19a3851bfd7e2c3717e8ad39537e4c515328e98d`,
  31.630.631 Bytes (unter 45 MiB), **103/103 Tests**, 0 Fehler.
- Geraet: `docs/design/block13/webview/07.png` zeigt den sichtbaren
  WebView-Frame mit „Abbrechen“ waehrend der Auto-Auflösung auf dem A52;
  danach wurde die Release-APK 1.1.0 wieder installiert.
- `validatePacks`: 17 Source-Fixtures, 2 Hoster-Fixtures, 0 Fehler.

### Frueherer Block-8-Nachweis

- Remote-Gate `frame61-76f3432c4b50-1794985462`, Quellstand `76f3432`, Exit 0.
- Debug-APK `db2fe4c6c8c140c8291bbc8ede357c63dc4112f57a313eb98d9bcafd3b9374d1`,
  24.432.615 Bytes (unter 45 MiB).
- `HosterFixtureTest`: 1 Test, 0 Fehler, 0 uebersprungen; `:scraper:resolver`
  gesamt 22 Tests ohne Fehler. `validatePacks`, `testFetchTools`,
  `packsReport --check` und `checkApkSize` liefen im Gate mit.
- Einschraenkung: Der Gate belegt Build, Parser und Fixture-Kette, nicht die
  Wiedergabe der sechs blockierten Hoster.

