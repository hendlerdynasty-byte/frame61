# CHANGELOG.md — Frame61

## [Unreleased]

### Schritt 2 — Persistente Stream-Nachweise (26.11.2026)

#### Hinzugefügt
- Room-Datenmodell für Titel-/Episoden-Stream-Nachweise mit Outcome
  `CORRECT`, `WRONG_CONTENT`, `TRAILER_ONLY` und `UNPLAYABLE`; stabile Upserts
  je Katalog, Staffel, Folge und Quelle. Gespeichert werden nur Metadaten sowie
  ein SHA-256-Hash der redigierten URL, keine URLs, Tokens, Cookies oder Header.
- Additive Room-Migration **2 → 3** ohne Eingriff in bestehende Tabellen sowie
  DAO, Hilt-Binding und daten-only `StreamEvidenceRepository`. Noch keine
  UI-/Resolver-Verdrahtung.
- Repository-Unit-Tests und ein ohne Gradle ausführbarer SQLite-Test für
  Migration, Datenverlusterhalt, Upsert, Episode-Trennung und Outcome-Filter.
- Reine `TitleVisibility`-Komponente filtert den Katalog ausschließlich über
  `CORRECT`-Stream-Evidenz (Film 0/0, Serie mit positiver Folgennummer,
  unbekannter Typ unsichtbar), fail-closed ohne Raw-Catalog-Fallback.
  Suchtreffer werden per exakter ID oder eindeutig normalisiertem Titel/Jahr/Typ
  kanonisiert; Unit-Tests decken Film, Serie, Outcome, Fehler und Kanonisierung ab.

### 1.5.0 — Blöcke 7–9, DEVIDEOSRC-Kette, Quoten-Messung (25.11.2026)

#### Hinzugefügt
- **`DeVideoSrcResolver`**: DEVIDEOSRC wird über die dokumentierte API
  (`POST /api/embed-links`, Token aus `/movie/{id}` bzw. `/serial/{id}`)
  aufgelöst. Die Antwort liefert die **Quellenliste** (Dropload, SuperVideo,
  Doodstream, Mixdrop) in Rangfolge, bei Serien je Staffel und Episode. Die
  Liste läuft durch die bestehende Kette; jeder Anbieter wird der Reihe nach
  versucht. Ohne API-Antwort bleibt der bisherige Pack-Pfad als Rückfallebene.
- **`MediaUrlMatcher`**: erkennt Doodstream-URLen **ohne Dateiendung**
  (`cloudatacdn.com` bzw. `~…?token=`-Form). Vorher galten sie weder als
  Medien-URL noch als Pack-Treffer.
- **WebView-Ziel-Liste (`HosterResolver.isWebFirst`)**: `uqload.vc` und
  `flyfile.app` tragen keine Embed-Form im Pfad und bekamen deshalb
  überhaupt keinen WebView-Versuch. Sie laufen jetzt über die stille WebView —
  erst die Medien-Interception, dann der DOM-Abruf.
- **DOM-Abruf erweitert**: Der generische Abgriff liest zusätzlich das
  jwplayer-Playlist-Element und die Liste der vom Browser abgerufenen
  Ressourcen. jwplayer-Seiten (uqload.vc, dr0pstream.com) setzen die Quelle
  gepackt in `setup({sources:[{file:…}]})`; der sichtbare `video.src` ist nur
  ein `blob:`.
- **`PlaybackPayloadCipher`** (`scraper/resolver`): liest den verschlüsselten
  `playback`-Block einer Videoseite (AES-256-GCM, Base64url, WebCrypto-Layout
  mit angehängtem 16-Byte-Tag) und gibt den Klartext zurück. Die Schlüssel­
  auswahl ist 1:1 aus dem Player-Bundle der Seite rekonstruiert: Version `v`
  → Teile `v` und `31-v` (1-basiert), sonst alle Teile.
- **Neue Pack-Aktion `aesgcm`**: ein Schritt in der Hoster-Schrittkette
  entschlüsselt einen solchen Block. `filemoon.json` (Version 4) nutzt sie
  zusammen mit `GET /api/videos/{code}`; das Ergebnis wird über
  `json:sources.0.url` gelesen, damit die escaped Trennzeichen (`\u0026`) der
  m3u8 als echte `&` ankommen. `tools/validate_packs.py` und
  `tools/fetch/hoster_worker.py` kennen die Aktion jetzt ebenfalls.
- **Block 7 — Vorwaermen auf Fokus:** Die Quelle des **fokussierten** Titels
  wird im Hintergrund aufgeloest, waehrend der Nutzer noch navigiert.
  `tvFocusable` meldet den Fokuswechsel, `PosterCard` gibt ihn als `onFocus`
  weiter, `PrefetchController` entprellt (400 ms), serialisiert die Laeufe
  (ein Vorwaermlauf zur Zeit) und haelt den Kandidaten 60 s bereit. Der Player
  uebernimmt den Kandidaten sofort, waehrend die volle Aufloesung parallel
  weiterlaeuft und nur die Fallback-Kette liefert.
  - Der Vorwaermlauf loest **nur** die Pack(s) der Kataloggruppe des Titels
    (`PrefetchScope`) — ein bis zwei HTTP-Anfragen statt 33 Pack-Suchen.
  - Er benutzt einen **eigenen Hoster-Resolver ohne WebView-Bestandteile**
    (kein `jsEvaluator`, kein Captcha-Loeser, kein Media-Interceptor). Der
    Vorwaermlauf kann den WebView-Slot des Players damit nicht wegnehmen.
  - `bs.to` ist bewusst **nicht** vorwaermbar (VOE laeuft ueber die stille
    WebView).
  - Der Player protokolliert die Zeit bis zum **ersten Bild** und ob der
    Start aus dem Vorwaermlauf kam — Messkriterium fuer die A52-Abnahme.
- **Block 8 — Staffel gehört zur Episodenadresse:** `EpisodeSelection` wählt
  die Folge in drei Stufen (Staffel+Nummer, dann nur Nummer, dann die erste
  der Quelle). Die Packs s.to und aniworld adressieren ihre Streams jetzt
  über `staffel-{season}` statt fest `staffel-1`; `PackInterpreter.streams`
  füllt den Platzhalter (Default 1 — bestehende Packs bleiben unverändert).
  ARD und bs.to wählen ebenfalls nach (Staffel, Nummer).
- **Block 9 — Serie ohne Episodennummer ist Folge 1:**
  `EpisodeSelection.effectiveNumber` bildet eine Serie ohne Nummer auf Folge 1
  ab (Filme bleiben bei 0). Vorher adressierten Vorwärmlauf, Normalauflösung
  und Alternative-Kette Serientitel mit `episode-0` — bei s.to/aniworld ein
  404, gemessen: `episode-0` → 404 (2516 Bytes, 0 Streams), `episode-1` → 200
  (4 Stream-Links). Folge: die Vorwärmung lief, lieferte aber nie einen
  Kandidaten, und „Abspielen" aus der Detailansicht blieb bei Serien leer.


#### Behoben
- **Vorwärmung lieferte bei Serien nie einen Kandidaten (Block 9, A52
  25.11.2026):** Serientitel wurden ohne Episodennummer aufgelöst — bei
  s.to/aniworld `episode-0`, und das ist ein 404. Gemessen:
  `serienstream.to/serie/mcgregor-forever/staffel-1/episode-0` → 404 (2516
  Bytes, 0 Stream-Links), `…/episode-1` → 200 (176859 Bytes, 4 Streams). Der
  Logcat zeigte `prefetch 'McGREGOR FOREVER' s1e0: kein Kandidat` — die
  Vorwärmung lief, wirkte aber nicht. Jetzt wird eine Serie ohne Nummer als
  Folge 1 aufgelöst (Vorwärmlauf, Normalauflösung, Alternative-Kette).
- **DEVIDEOSRC-API kam auf dem Gerät nie an (A52, Lauf 4):** Der Ausdruck für
  den `JSON.stringify`-Aufruf der Player-Seite war an der Zeilengrenze
  zerschnitten (nach dem Komma fehlte das `\s*`) und traf nie; die Rückfallebene
  griff stattdessen das erste `id=` des HTML (`_cloudToggle`). Gemessen: 16 GETs
  auf die Seite, **0** POSTs. Der Ausdruck ist korrigiert, die Rückfallebene
  sucht nur noch innerhalb des `JSON.stringify`-Aufrufs.
- **Serien kommen über `/api/resolve`:** Die Seite
  `/custom/serial/{id}/{staffel}/{folge}` ruft nicht `/api/embed-links`, sondern
  `/api/resolve` mit `{type, id, season, episode, mode, token}` — die Werte
  stehen als `data-*`-Attribute am `<main>`-Element. Beide Formen sind
  umgesetzt (Live-Abfrage 24.11.2026).
- **Eine HTML-Seite ist kein Kandidat mehr:** Liefert ein Pack die Seite selbst
  zurück (gleiches Pack, keine Medien-URL), ist das ein Fehlschlag statt eines
  Kandidaten. Vorher bekam der Player HTML und meldete 3003 — die
  Fehlerklasse war falsch und der Titel unbrauchbar.
- **jwplayer-Pack lieferte `blob:` statt der m3u8:** Gepackte Player
  (dr0pstream, superplayer) legen die Quelle nur im jwplayer-Playlist-Element
  und in der Ressourcenliste ab. Das Skript akzeptiert jetzt nur `http…`-URLs
  und fragt beide Quellen ab.
- **Filemoon war tot (HTTP 405, Block 4d-1):** Die Pack-Kette endete auf
  `GET /api/videos/{code}/playback`; die Seite antwortet dort inzwischen mit
  **405**. Der Block kommt unter `GET /api/videos/{code}` — verschlüsselt
  (AES-256-GCM). Das Pack liest jetzt diese API, entschlüsselt sie und liefert
  die m3u8. Live belegt am 24.11.2026: `hoster_worker` löst die Kette gegen
  die echte Seite auf (`verified`), die m3u8 antwortet mit HTTP 200
  (`application/vnd.apple.mpegurl`).
- **Staffel-2-Folgen wurden als Staffel 1 gespielt (Block 8):** Drei Stellen
  adressierten stillschweigend die falsche Folge:
  - bs.to suchte `season == 1 && number == n` — eine Staffel-2-Folge traf nie,
    der Nummer-Fallback spielte Staffel 1.
  - ARD suchte nur nach der Nummer und wählte bei Mehrstaffel-Serien den
    ersten Treffer aus Staffel 1.
  - die Packs s.to und aniworld standen mit fest `staffel-1` im Stream-Endpoint
    (404 oder eine fremde Folge).
  Der Resume-Punkt wanderte dabei in eine andere Zeitachse.
- **Resume-Schlüssel hing an der Quelle (Block 8):** Der Slot je Folge wird
  jetzt aus (Titel, Staffel, Nummer) abgeleitet und ist damit über den
  Quellenwechsel hinweg stabil. Titel ohne Metadaten-Snapshot teilten sich
  vorher alle Folgen einen Slot (`episodeId = 0`); der alte Schlüssel wird beim
  Lesen als Fallback mitgeprüft, damit bestehende Positionen nicht verloren
  gehen.

#### Geändert
- Die generische Embed-Stufe greift jetzt auch **innerhalb** einer Kette
  (`depth > 0`): Die von DEVIDEOSRC gelieferten Provider-Embeds sind erst
  dort spielbar. Das WebView-Budget (ein Versuch je Titel) bleibt.
- **Embed-Vertrag im Test festgeschrieben:** Die Embed-Form im Pfad ist
  **host-unabhängig** (seit Lauf 1 so gemessen: `odysseusa.cc` und
  `moflix-stream.click` lösen nur so auf); die Host-Liste (`uqload.vc`,
  `flyfile.app`) erweitert nur um Hosts **ohne** Embed-Form. Der Test des
  Merge-Branches behauptete das Gegenteil („fremde Hosts bleiben unangetastet")
  und fiel im CI durch — die Erwartung war falsch, nicht der Code.
- **Messbarkeit:** Der Messharness startete keine Diagnose-Sitzung — alle
  Resolver-Ereignisse wurden verworfen, die Fehlerklasse konnte nur
  `NO_CANDIDATE` heißen. Pro Titel läuft jetzt eine Sitzung; die QUOTA-Zeile
  führt den Fehlergrund mit, dazu eine `QUOTA CAUSE`-Zeile mit der
  Resolver-Kette.
- **DEVIDEOSRC protokolliert jeden Schritt** (Seite geladen, Token gelesen,
  POST, Anbieterliste) und nennt je Anbieter den Einzelgrund, wenn keiner
  spielbar war.

#### Belege
- Lauf 3 (Build `078fb4b`, A52): Quote 59 %, erste echte Medien-URL einer
  Index-Gruppe außerhalb des ARD-Sockels.
- Lauf 4 (Build `d678efb`, A52): gleiche Stichprobe (Seed 20261122), Quote
  60/100 = 60 %, Gruppen 65/84/27/36/50/50 %, 34 × `CONTAINER_UNSUPPORTED`,
  6 × `NO_CANDIDATE` — siehe [QUOTA.md](QUOTA.md). Live-Abfrage beider
  devideosrc-Endpunkte am 24.11.2026.
- Lauf 5 (Build `682683d`, A52): gleiche Stichprobe (Seed 20261122), Quote
  **64/100 = 64 %** (+4 pp), Gruppen 69/89/36/45/50/50 %; **12 POSTs** auf die
  devideosrc-API (10 × `/api/embed-links`, 2 × `/api/resolve`) statt 0 in Lauf 4,
  vier Titel mehr und **kein Verlust**. Die Fehlerklassen sind jetzt genauer
  (18 × `CONTAINER_UNSUPPORTED`, 9 × `GEO_BLOCKED`, 9 × `EMBED_UNRESOLVED`),
  weil der Harness je Titel eine Diagnose-Sitzung öffnet — siehe
  [QUOTA.md](QUOTA.md).
- Lauf 6 (Build `27afaeb`, A52): gleiche Stichprobe (Seed 20261122), Quote
  **62/100 = 62 %** (−2 pp), Gruppen 70/84/27/45/50/50 %. Die drei Verluste
  und der eine Gewinn sind **alle** Providerwechsel (Anbieterliste rotiert
  zwischen den Läufen). Block 4c-2 ist dabei **unmeasured**: `uqload.vc` hatte
  in Lauf 5 96 Erwähnungen, in Lauf 6 **0** — der Host wurde gar nicht mehr
  angeboten. Fehlziel-Masse: `dropcdn.io` 13, `moflix.rpmplay.xyz` 11,
  `moflix-stream.fans` 4. Filemoon stand in 1–2 Titeln der Kette und ist mit
  Block 4d-1 adressiert — siehe [QUOTA.md](QUOTA.md).
- Lauf 7 (Build `0ef5b44`, A52): gleiche Stichprobe (Seed 20261122), Quote
  **66/100 = 66 %** (+4 pp zu Lauf 6), Gruppen 72/95/27/55/50/50 %. Die vier
  Gewinne kommen aus aniworld (+2), filmpalast (+1) und serienstream (+1), zwei
  ARD-Titel verloren an einen GEO-Block — **nicht** aus Filemoon: der
  reparierte Pfad steht in keinem der 100 Titel als fehlender Hoster. Der
  wichtigste Befund: 12 der 34 Fehlschläge sterben an **einem** toten Hoster
  (`moflix.rpmplay.xyz`, aufgelöst aber unspielbar), obwohl dieselbe
  Watch-Seite 3–8 weitere Hoster anbietet — siehe [QUOTA.md](QUOTA.md).
- **Block 4e (Alternative-Kette, `c6465fa`):** Der Fast-Path löst je Quelle nur
  den **ersten** Hoster auf (`MAX_WEBVIEW_STREAMS = 1`). Ist der aufgelöst,
  aber nicht abspielbar, blieben die übrigen Hoster der Watch-Seite liegen.
  Neu: `MirrorCandidate.streamUrl` trägt die Watch-URL mit, `hostOf()` sperrt
  gescheiterte Hoster darüber (nicht über die Medien-URL — die sieht anders
  aus als die Hoster-Seite, die sie erzeugt hat), und
  `AutoPlaybackResolver.resolveAlternative` holt die nächsten Hoster
  **derselben** Quelle nach. Player und Mess-Harness bilden beide den
  Produktionspfad ab; der Harness zählt Fast-Path und Produktion getrennt
  (`QUOTA FAST SUMMARY` / `QUOTA FAST GROUPEN`), damit die Reihe Lauf 1–7
  vergleichbar bleibt.
- Lauf 8 (Build `c6465fa`, A52): gleiche Stichprobe (Seed 20261122), Quote
  **62/100 = 62 %** Produktion, **61/100 = 61 %** Fast-Path (−4/−5 pp zu Lauf 7),
  Gruppen 72/78/36/36/50/50 %. Der Rückgang ist **Provider-Rotation** (17 Titel
  neu an dropcdn-Knoten, aniworld 95→78 %, filmpalast 55→36 %), nicht der
  Block: 6 Titel änderten das Ergebnis, 5 davon an externe CDN-Wechsel.
  Block 4e rettet exakt **+1 Titel** (idx 49: `moflix.rpmplay.xyz` →
  `cdn.plyr.io`); 9 Titel öffneten die Kette, 8 starben an `js: kein
  Rückgabewert` — reine JS-Packs verdeckten den Interception-Weg, obwohl
  `INTERCEPT #79` dieselbe Host-Familie in 2,1 s auflöst. 10 Titel starben an
  `Hoster-Zeitüberschreitung`: parallele Alternativen reihen sich in die
  serialisierte WebView-Queue. Siehe [QUOTA.md](QUOTA.md).
- Lauf 9 (Build `7cac713`, A52): gleiche Stichprobe (Seed 20261122), Quote
  **64/100 = 64 %** Produktion, **63/100 = 63 %** Fast-Path (**+2/+2 pp** zu
  Lauf 8), Gruppen 72/89/27/45/50/50 %. Block 4f belegt: Interception 73→202
  Versuche, 3→11 Titel mit Medien-URL; `js: kein Rückgabewert` als Todesursache
  von 8 Titeln auf 2 gesunken. Die 6 Gewinne sitzen dort, wo Lauf 8 sie an
  Rotation verloren hatte (idx 34/35/37 über andere Hoster zurück). Kosten: 6
  moflix/bsto-Titel laufen die volle 60-s-Alternative-Kette leer. Siehe
  [QUOTA.md](QUOTA.md).
- **Block 6 (Single-Flight, `1c069ab`):** `resolveInternal` hielt Cache und
  Schlösser über verschachtelte Inline-Lambdas mit Labeled-Return — der
  Kotlin-Compiler verweigerte das („Missing return statement“, CI
  `45358019986`). Neu ohne Labeled-Return: Cache-Hit → `Mutex` pro
  Auflösungsschlüssel → erneuter Cache-Hit → `resolveNow`. Gleiche Semantik,
  kompilierbar.
- **Block 4f (`7cac713`):** (1) Reine JS-Packs auf Embed-Seiten laufen jetzt
  **zuerst** durch die Medien-Interception (`shouldInterceptRequest` gibt
  `null` zurück, Referer/Origin/Cookie gehen an den Player, die **echte**
  Interception-Zeit wird gemessen); (2) nach einem fehlgeschlagenen Pack gibt
  es eine generische Rückfallebene (`genericFallback(force = true)`); (3) die
  Alternative-Kette ist **sequenziell** mit eigener Frist je Kandidat
  (`ALTERNATIVE_DEADLINE_MS` 60 s, `ALTERNATIVE_STREAM_MS` 34 s,
  `MIN_ALTERNATIVE_MS` 8 s, 2 WebView-Versuche je Alternative) — parallele
  WebView-Zugriffe verhungern aneinander; (4) `needsWebView` erkennt
  JS-Schritt-Packs, damit solche Hosts nicht mehr mit `WebViewBudget.none()`
  im „plain"-Bucket landen. CI `45384683779` grün, 183/183 Tests
  (`HosterResolverTest` 27: Interception vor JS-Pack, Rückfall nach
  Fehlschlag, Watch-Seite behält den Pack-Pfad).
- **Block 7 + Block 8 (`ebec382`):** CI `45480868905` grün — Gate **199/199
  Tests**, Lint **0 Errors**, Release-Job baut APK + AAB. Acceptance-APK
  lokal signiert (sha256 `fac02c5901b4ed94c2e7e52fb0265d14fed8cfcc1cfee299ba74b980dcfac66b`,
  Signatur-Zertifikat `1201f46d…` — derselbe Release-Key wie Lauf 9) und per
  Device-Lab `QUICK_SMOKE` auf dem A52 installiert (Job `job-731d24e82a274223`,
  PASS). **Lauf 10** (gleiche Stichprobe wie Lauf 1–9: Training, Seed
  `20261122`, 100 Titel, Katalog 997) läuft auf dem A52 — Messwerte und Delta
  folgen in [QUOTA.md](QUOTA.md).
- **Block 9 (`9d14350`):** CI `45582003989` grün — Gate **203/203 Tests**,
  Lint **0 Errors**, Release-Job baut APK + AAB. Release-Artefakte aus diesem
  Lauf lokal signiert: APK 19 963 388 B, sha256
  `ee415ab9aa0eb09546c3dd8bf0fe40a7660c1c9b32ed5bfc311136055d45e720`; AAB
  24 667 262 B, sha256
  `39a36476edc075a7a2065c46d5650a5d195d1b5411d0234cfb19e87a8e2866bc`.
  Signatur-Zertifikat `1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52`
  (CN=Frame61, O=Hendler Dynasty, C=DE) — derselbe Release-Key wie Lauf 9/10.
- **Block 7 — Gerätebeleg A/B (A52, 25.11.2026, Build `9d14350`):** Der
  Vorwärmlauf ist auf dem Gerät **funktional** belegt: Fokus auf eine
  ARD-Karte löst `Frame61Prefetch` aus (4–9 Kandidaten in 181–1371 ms), der
  Player startet daraus (`RESOLVER: Start aus dem Vorwaermlauf`) und meldet
  `PLAYER: Erstes Bild nach … details={vorwaermlauf=ja}`.
  **Der Zeitgewinn ist nicht belegt:** In zwei unabhängigen Paaren (ARD-Titel
  #202 und #200, jeweils warm/kalt) war der Kaltpfad schneller
  (7551 ms → 1412 ms und 3953 ms → 2776 ms). Die Auflösung, die der
  Vorwärmlauf einspart, holt der Kaltpfad bei ARD in rund einer Sekunde nach
  (ARD-API: 22 ms + 15 ms); maßgeblich bleibt die Medienübertragung. Messwert,
  Rohlogcat und Nebenbefund (Fremdlast 92 % CPU eines anderen Projekts auf
  dem Gerät) in
  `~/hendler-dynasty/evidence/frame61/1.5.0/run10-ebec382/BLOCK7-AB-BELEG.md`.
- **Lauf 11 (Build `9d14350`, A52, 25.11.2026, Training Seed `20261122`):**
  Quote **67/100 (67 %)**, Fast-Path 66/100 (66 %), resolveMs p50/p95
  1959/27089 ms, firstFrameMs p50/p95 2907/28584 ms. Gruppen: aniworld 95 %,
  serienstream 70 %, filmpalast 73 %, ard 50 %, bsto 50 %, moflix 27 %.
  Delta zu Lauf 10 (69/100): **−2 Titel**, sechs gekippte Titel, alle an
  rotierenden Anbietern (`veevcdn.co`, `dropcdn.io`, `moflix.rpmplay.xyz`) —
  Provider-Rotation, keine Blockwirkung (Block 7/9 sind oberflächengebunden
  und greifen im Harness nicht). Rohbelege:
  `~/hendler-dynasty/evidence/frame61/1.5.0/run11-9d14350/polls/`.
- **Holdout (Build `9d14350`, A52, 25.11.2026, Seed `20261123`, disjunkt):**
  Quote **70/100 (70 %)**, Fast-Path 68/100 (68 %) — **über** der
  Trainings-Quote (67 %), also keine Überanpassung. Gruppen: aniworld 90 %,
  filmpalast 86 %, ard 83 %, serienstream 64 %, bsto 50 %, moflix 36 %.
  resolveMs p50/p95 1797/27107 ms, firstFrameMs p50/p95 3016/29422 ms,
  157 Interception-Versuche (13 mit Medien-URL). Rohbelege:
  `~/hendler-dynasty/evidence/frame61/1.5.0/holdout-9d14350/polls/`.
  **Damit ist die Veröffentlichungsregel erfüllt.**

- **Sessiontest 1.5.0 (A52, `9d14350`, 25.11.2026):** `SESSION PASS
  titles=50 resumeJumps=20 profileChanges=10 errors=0 elapsedMs=1811946
  maxHeap=55798224` — 30,2 Minuten, fünf Container (HLS, MP4, MKV, AVI,
  MPEG-TS), 20 Resume-Sprünge, 10 Profilwechsel, kein Crash/ANR. Grenze: das
  Harness spielt lokale Assets, nicht die Netzpfade. Rohbelege:
  `~/hendler-dynasty/evidence/frame61/1.5.0/stress-9d14350/`.

### 1.4.0 — Titel-Quote, schnelle Auflösung, Hoster-Ketten (2026-11-22)

#### Hinzugefügt
- **Messharness `PlaybackQuotaActivity`** (nur `ci`/`acceptance`): zieht 100
  Katalogtitel deterministisch (Seed 20261122), löst sie über die
  Produktionskette auf und spielt bis zu drei Kandidaten. Pro Titel werden
  Ergebnis, Zeit bis zum ersten Bild, Kandidatenzahl, versuchte Hosts und
  Fehlerklasse protokolliert — im Logcat, im Statusbereich und in einer Datei.
  Bericht: [QUOTA.md](QUOTA.md).
- **Captcha-Pipeline ohne sichtbaren Dialog**: `HeadlessCaptchaSolver` nutzt
  eine versteckte WebView (`WebViewJsEvaluator.cookiesFor`), cacht Cookies pro
  Host 15 Minuten und wärmt zwei Hoster beim App-Start vor. Der sichtbare
  `CaptchaBroker` bleibt Rückfallebene.
- **Hoster-Ketten**: Eine Resultat-URL, die zu einem anderen Hoster-Pack
  gehört, wird rekursiv aufgelöst (bis drei Stufen). Dazu Packs für
  `serienstream` (Redirect-Gate per JS) und `devideosrc`, eine generische
  Embed-Auflösung für Hosts ohne Pack sowie `vidsonic`.
- **Stream-Regeln** für `s.to` (Serien) und `aniworld` (Anime):
  Hoster-Links, Sprache und Hoster aus Attributen, relative Ziele absolut.

#### Geändert
- `resolveFast`: Der Resolver sammelt alle Quellen parallel ein und kehrt
  **600 ms nach dem ersten Kandidaten** zurück statt auf die langsamste
  Quelle zu warten. Der Player nutzt diesen Pfad; die vollständige Liste
  bleibt für die Fehlerrotation verfügbar.
- Hoster-Auflösung je Pack **parallel** statt sequentiell.
- Quellbudget 4 s → **9 s**, Obergrenze des Einsammelns **12 s** (Packs mit
  JS-Kette brauchen länger; im schnellen Pfad läuft die Captcha-Runde nicht).
- POST-Bodies tragen `application/x-www-form-urlencoded`, wenn die Regel
  keinen eigenen Content-Type nennt.
- Stream-Ziele werden absolut (`baseUrl`), `langAttr`/`hosterAttr` lesen
  Sprache und Hoster aus Attributen.
- `HEAD`-Ketten: `HosterResolver` folgt Weiterleitungen bis drei Stufen.

#### Behoben
- **aniworld-Regression (HTTP 200 / 0 Treffer):** POST ohne Content-Type —
  DDoS-Guard antwortet mit 200 und leerem Rumpf. Behoben, aniworld liefert
  wieder 74 Treffer.
- **Crash-Loop „Frame61 wird wiederholt beendet“:** `ClosedSendChannelException`
  aus `send()` beim Abbruch des Einsammelns. Jetzt `trySend` in eigenem Scope.
- **ANR durch parallele WebViews:** WebView-Zugriffe serialisiert, Vorwärmen
  verzögert und auf zwei Hosts begrenzt.

#### Gemessen (A52, 100-Titel-Stichprobe)
| Lauf | Quote | Startzeit p95 | Fehlerklassen |
|---|---|---|---|
| Baseline `243971d` | 6/100 | 5.615 ms | 55 NO_CANDIDATE, 38 × 3003 |
| `e242198` | 5/100 | **2.714 ms** | 34 NO_CANDIDATE, 61 × 3003 |
| `f673eb2` | 4/100 | 3.142 ms | 86 NO_CANDIDATE, 10 × 3003 |
| `4ebb58a` | läuft | — | — |

Die 95-%-Marke ist mit dem aktuellen Quellenbestand **nicht** erreicht; Gründe
und nächste Schritte in [QUOTA.md](QUOTA.md).

### Phase B — Player-Härtung, Quellen-Abdeckung, Release 1.3.0 (2026-11-22)

#### Hinzugefügt
- `SourceCoverageActivity` (nur `ci`/`acceptance`): Abdeckungslauf auf dem
  Geraet — je Indexseite der HTTP-Status, die Trefferzahl und der erste
  Treffer, danach die Produktionskette `streams → Hoster → Player`. Ergebnis
  auf dem Samsung A52: 9/14 Indexseiten, 1/14 Index-Wiedergabe, 2/11 Hoster
  (`direct`, `vidmoly`); neun Ziele blockiert mit HTTP-Status und Grund.
- `hoster_targets.json` (nur `ci`/`acceptance`) mit elf Hosterzielen,
  Herkunft und dokumentiertem Sperrgrund.
- `docs/design/phase-b/`: Selbsttest 6/6, 30-Minuten-Sessiontest, TV-Fokusweg,
  zwei Abdeckungslaeufe und der Nachweis einer laufenden Wiedergabe.
- `PackHttpClient.fetch()` liefert den HTTP-Status mit; `get()` wirft bei
  4xx/5xx `IOException("HTTP <code> für <url>")`. Blockierte Quellen tragen
  damit ihren Status im Protokoll statt nur einer Fehlermeldung.

#### Geändert
- `PackInterpreter.search` reicht die Query auch unter dem Parameternamen der
  Regel (`queryParam`) heraus. Ein Pack mit `term` (s.to, SerienStream) wurde
  vorher ohne Suchbegriff abgerufen und lieferte 0 Treffer.
- `PackHttpClient` kodiert Pfad-Platzhalter ausser `id` und haengt die Query
  nicht doppelt an, wenn der Pfad sie bereits enthaelt.
- Die Coverage-Activity liest die Playerposition live (`engine.publish()`)
  wie der App-Ticker; vorher meldete sie Position 0 fuer einen Player, der
  bereit war und lief.
- TV-Navigation als feste linke Seitenleiste statt unterer Leiste.
- Release-Version 1.3.0, `versionCode` 4.

#### Behoben
- `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (3003) bei zwei Quellen bleibt
  bestehen und ist jetzt mit der konkreten Stream-URL belegt:
  `vidsonic.net/e/8zwnbxa44ivc` (filmpalast) und `devideosrc.co/movie/tt1375666`
  (streamcloud). Beide liefern kein parsebares Mediencontainer; der
  Systemplayer uebernimmt, der Fortschritt bleibt 0.

#### Dokumentation
- `docs/ABSCHLUSS-PHASE-B.md`: Abschlussbericht der Phase B mit Ampel je
  Arbeitspaket, Quellzahlen, blockierten Quellen (Ursache, HTTP-Status,
  Versuche), Tests, Lint, Performance, APK-Groesse und Artefakt-Hashes.
- `docs/STATE.md` auf den Stand 22.11.2026 (Release 1.3.0/VC4) gebracht.
- `docs/ABSCHLUSS-TV.md`: Phase-B-Stand (Release auf dem TV-Emulator,
  D-Pad-Fokusweg der Seitenleiste) und DoD-Eintraege 23–27 ergaenzt.
- `docs/RELEASE.md` und `docs/PERF2.md`: Release 1.3.0 mit Hashes und
  Nachmessung am signierten Release-APK (Kaltstart p95 334 ms, Scroll p95
  22 ms, Janky 3,01 %).
- `README.md` an den tatsaechlichen Quellenstand angepasst: verifizierte
  Packs tragen `verified: true` mit technischem Beleg; die TV-Navigation
  sitzt als Seitenleiste; der Gate laeuft ueber GitHub Actions, signiert
  wird lokal. `docs/OWNER-ÜBERSICHT.md` traegt einen Hinweis auf den
  aktuellen Stand.
- `scripts/sign_release_aab.sh`: AAB-Signierung lokal mit `jarsigner`
  (`-storepass:env`/`-keypass:env`), damit kein Passwort in der
  Kommandozeile steht.

### Block 18 — Abschluss Release 1.2.0 (2026-11-19)

#### Hinzugefügt
- Signierter Release 1.2.0 (APK `da4e5011…`, AAB `74399d5c…`) und
  GitHub-Release `v1.2.0` mit `update.json`.
- `docs/ABSCHLUSS-TV.md` mit DoD-Abgleich aller Blöcke 0–18.
- Update-Livepfad 1.1.0 → 1.2.0 auf dem TV-Emulator (`versionCode=3`),
  direkte Installation aus dem Release-Asset.
- Dokumentierte REMOTE_BUSY-Umgehung mit identischem Export und
  frischen Jobordnern, keine fremden Prozesse beendet.

### Block 15 — Filme im Katalog (2026-11-18)

#### Hinzugefügt
- `tools/fetch/film_builder.py`: keyloser Wikidata-Export mit deutscher
  Beschreibung, Genres, Laufzeit und lokalen Wikimedia-Postern.
- 400 Filme (IDs `wiki-Q…`), damit **1000 Titel / 416 Filme** im Katalog.
- Laufzeit-Filter (`< 90`, `90–120`, `> 120 Min`) in `BrowseScreen`.

#### Geändert
- `CatalogRepositoryTest`: ≥ 1000 Titel und ≥ 400 Filme.
- `docs/CATALOG.md`: neue Quellen, Umfang und Filter.

### Block 14 — Indizes 10/14 (2026-11-18)

#### Hinzugefügt
- `jsonExtractRegex` fuer API-Regeln: eingebettetes Loader-JSON aus HTML
  loesen (Moflix `loaders.searchPage.results`).
- Neue verifizierte Packs: `moflix` (Ersatzdomain `.top`), `streamcloud`
  (`streamcloud.date`), `cine` (AJAX `/request/search`).
- `docs/INDEX.md`: 10/14 verifiziert, vier `blocked_external` mit Domain- und
  Pfadbelegen (kinox 6 Domains, kinokiste, megakino, movie2k).

#### Geändert
- `bs-to` per `idFromTitle` verifiziert, `cine`-Pack von DOM-Suche auf echten
  AJAX-Endpunkt umgestellt.
- Validator und `SOURCE_PACK.md` um `jsonExtractRegex` erweitert.

### Block 13 — Motor: Schema, WebView, Hoster-Ziele (2026-11-18)

#### Hinzugefügt
- Hoster-Aktionen `js` (WebView, 8 s) und `pow` (`filemoon-v1`, `sha256`),
  inklusive `PowSolver` mit echtem Filemoon-Vektor (Nonce → Lösung `3643`,
  live per `/captcha/verify` bestätigt).
- `idFromTitle`/`idRegex`/`idGroup` für Treffer ohne Link-Attribut;
  Burning Series ist damit verifiziert (7/14).
- `CaptchaBroker`, `CaptchaWebViewFrame` und `WebViewJsEvaluator`: Token,
  Cookies und Stream-URL aus der WebView in den Resolver; 20 s Timeout.
  Kinokiste/Megakino nutzen denselben Pfad auf Quellenebene.
- `direct` mit ARD-Fixture verifiziert; Hoster-Ziele nicht mehr nur aus
  aniworld, sondern auch aus filmpalast, filmo, s.to/serienstream.

#### Geändert
- `PackHttpClient` kodiert Pfad-Platzhalter und akzeptiert Cookies.
- Hoster-Worker beherrscht `pow` und Header-Templates; Source-Worker
  berücksichtigt `idFromTitle`.
- Validator und `SOURCE_PACK.md` um die neuen Felder erweitert.

#### Ergebnis
- Hoster 2/10 verifiziert; VOE, Doodstream und Filemoon mit konkreten live
  geprüften Restgründen. Kein "extern blockiert" ohne Codepfad und Versuch.

### Block 12 — Signierter Release und Update-Kanal (2026-11-18)

#### Hinzugefügt
- `version.properties` (1.1.0/versionCode 2) und versionsabhängiger
  Release-Build; `perf`-Variante bleibt separat.
- Signierter Release 1.1.0 (APK `1c902e37…`, AAB `f2a198df…`) und Altstand
  1.0.0 für einen echten Update-Durchlauf.
- `site/update.json` mit Release-URL, SHA-256 und Notes; Default-Update-URL
  auf den Pages-Kanal.
- GitHub-Release `v1.1.0` mit APK, AAB und Manifest; Pages-Lauf `44308280114`
  grün, Manifest HTTP 200.
- `docs/RELEASE.md` mit Artefakten, Signaturen, Update-Livepfad und Grenzen.

#### Geändert
- Release-Signatur nur mit lokalem Env; Remote-Build liefert unsigned,
  Signatur erfolgt lokal mit dem Keystore außerhalb des Repos.
- `ProfileDataStore.updateUrl` fällt auf den Pages-Kanal zurück.

#### Ergebnis
- Update-Pfad live: 1.0.0 → „Version 2 verfügbar“ → Download → SHA-256 →
  System-Installer. Play Protect blockiert die letzte Bestätigung extern;
  1.1.0 wurde aus dem Release-Asset installiert (`versionCode=2`).
- Kein Play-Store-Upload, kein Rollout.

### Block 11 — Performance (2026-11-18)

#### Hinzugefügt
- `app/src/main/baseline-prof.txt` aus einem echten A52-Profil-Dump
  (`cmd package dump-profiles`), 25.774 Methoden; `profileinstaller` 1.4.1 und
  `art-profile-r8-rewriting`.
- `tools/perf/profile_to_hrf.py`: reproduzierbare Umwandlung Dump → HRF.
- `perf`-Buildtyp: R8/Shrink wie Release, Debug-Key, eigene ID für Messungen.
- `docs/PERF2.md`: Messaufbau, Debug-/R8-Vergleich, p95 und Jank.

#### Geändert
- `HomeViewModel` lädt Katalog und Rails über `Dispatchers.IO`; Kaltstart
  Debug 2,8 s → 1,6 s.
- `BrowseViewModel` lädt 120 Titel pro Seite nach; Filter setzen die Seite
  zurück.
- `PlayerViewModel.onCleared` speichert den letzten Stand in einem
  IO-Scope statt im bereits gecancelten `viewModelScope`.
- `CatalogRepository.load` ist synchronisiert (paralleler Erstzugriff).

#### Messergebnis (A52, R8-Perf-APK `a52abc65…`)
- Kaltstart p95 **323 ms** (Ziel < 1200 ms).
- Scroll p95 **24 ms**, Jank **3,71 %** (Ziele < 40 ms / < 5 %).
- Debug-Vergleich: 1702 ms, 32 ms, 5,23 %.

### Block 10 — Katalog 600 (2026-11-18)

#### Hinzugefügt
- `tools/fetch/catalog_builder.py`: keyloser TVmaze-Katalogbau mit
  Allowlist/SSRF-Schutz, 2:3-Postern (180×270 JPEG) und Meta-Snapshots.
- 600 Katalogeinträge, 600 lokale Poster (`assets/posters`), 600
  Meta-Snapshots (`assets/meta`) und `index.json`.
- `BrowseViewModel`: Genre- und Jahrzehnt-Filter, Trefferzahl, Fortschritt.
- `ResumeRepository.progressByTitle` und `PosterCard(progress = …)`: 4-dp-Spur
  am unteren Posterrand, innerhalb des Clips.
- `docs/CATALOG.md`.

#### Geändert
- `catalog.json` auf Version 3, Posterpfade `file:///android_asset/posters/...`.
- `CatalogRepository` dedupliziert nach Titel/Jahr beim Laden.
- `CatalogRepositoryTest` verlangt ≥600 Titel, lokale Poster und
  Duplikatfreiheit.
- `validate_packs.py` prüft Titel/Jahr-Dedupe und die Existenz jeder
  Posterdatei.
- `BrowseScreen`/`WatchlistScreen` nutzen dieselbe Karte mit Fortschritt;
  „Meine Liste“ bleibt DataStore-persistent.

### Block 9 — Indexseiten-Nachweise (2026-11-18)

#### Hinzugefügt
- `format` pro Pack-Regel (`html`/`api`): gemischte Packs mit JSON-Suche und
  HTML-Detail/Folgen.
- `PackInterpreter` entfernt HTML aus API-Titeln (AniWorld `<em>…</em>`) und
  wählt den Parser pro Regel.
- `pack_worker.py`: POST-Ziele, detail-URLs mit `{titleId}`, Fixture-Aufzeichnung
  mit getrimmter Antwort, `search.expected.json`, `responseSha256` und
  `fullResponseSha256`.
- Sechs verifizierte Pack-Ziele mit Fixtures: `aniworld`, `filmpalast`,
  `filmstarts`, `filmo`, `s-to`, `serienstream`.
- `docs/INDEX.md`: Status aller 14 Seiten, Selektoren, Hashes und Blockaden.
- Test `api search strips html and mixed rules use their format`.

#### Geändert
- `aniworld.json`: POST `/ajax/search`, HTML-Detail und Folgenliste.
- `filmpalast.json`: echte HTML-Suche `/search/title/{query}`, Detail und VOE-
  Stream-Link.
- `s-to.json`/`serienstream.json`: JSON-Suggest `shows[]`, HTML-Detail.
- `filmo.json`: JSON-Suggest `movies[]`, HTML-Detail.
- `filmstarts.json`: JSON-Autocomplete `results[]`.
- `bs-to.json`: erreichbare Domain `burningseries.ac`, Status mit Grund.

#### Ergebnis
- 6/14 verifiziert, 5 erreichbar ohne auswertbaren Suchpfad, 3 Domains tot.
  Keine erfundenen Selektoren, keine Attrappen-Packs für tote Domains.

### Block 8 — Hoster-Nachweise (2026-11-18)

#### Hinzugefügt
- `tools/fetch/hoster_worker.py`: echter Durchlauf der Hoster-Schrittketten mit
  Browser-User-Agent, Referer, Cookie-Jar, drei Versuchen und Backoff.
- `tools/fetch/hoster_targets.json`: belegte Ziele aus `aniworld.to`-Redirects
  (Vidmoly, VOE, Doodstream, Filemoon) plus dokumentierte Gründe für neun
  nicht erreichbare Ziele.
- `scraper/resolver/src/test/resources/fixtures-hosters/vidmoly/`: echte
  Serverantwort, erwartete Stream-URL und `responseSha256` (`6db7c3c1…`).
- `HosterFixtureTest`: prüft Fixture-Hash und führt die Pack-Schrittkette gegen
  die Aufzeichnung aus.
- `docs/HOSTERS.md`: Status, Ziele, Blockaden und nächster technischer Schritt.

#### Geändert
- `vidmoly.json` auf Version 2: `vidmoly.net`, Regex für einfache und doppelte
  Anführungszeichen; `verified: true` mit Fixture.
- `doodstream.json`: Domains `do7go.com` und `playmogo.com`, generischer
  m3u8/mp4-Regex.
- `voe.json`: Domain `arianathankspecial.com`; `filemoon.json`: Domain
  `bysezejataos.com`.
- `tools/validate_packs.py`: `verified: true` nur mit Hoster-Fixture; jeder
  unverifizierte Pack braucht `check.reason`; Hoster-Fixtures werden geprüft.

#### Ergebnis
- Vidmoly `verified`; VOE/Doodstream/Filemoon `step_failed` mit konkretem
  Grund (JS-Redirect, Cloudflare, SPA/PoW); sechs Hoster `no_target` mit
  Erreichbarkeitsbefund. Keine erfundenen URLs oder Fixtures.

### P5 — Navigation, Suche, Skins, Profile/PIN, Player-Spuren, Source-Lab-Import, Layout-Builder, Updater (2026-11-16)

#### Hinzugefügt
- **Navigation**: `NavBar` mit Start, Suche, Skins, Quellen, Profile,
  Einstellungen — jede Route hat einen fokussierbaren Eintrag. Der Source Lab
  war vorher kompiliert, aber nicht erreichbar.
- **Suche**: `SearchScreen`/`SearchViewModel` über den `ChainedMetadataSource`
  (Katalog → TVmaze → Wikipedia), Dedupe nach Titel/Jahr, D-Pad-taugliche
  Trefferliste.
- **Skins**: `SkinsScreen`/`SkinsViewModel` — alle geparsten Skins als
  Vorschau; Auswahl landet in DataStore, das Theme folgt sofort.
- **Profile + PIN**: `ProfileScreen`/`ProfileViewModel` mit PIN setzen,
  ändern und entfernen (SHA-256, Salt = Profil-ID), Auswahl wird persistiert.
- **Player**: `TrackPanel` (Audio/Untertitel/Tempo), `ErrorPanel` mit Retry —
  der „kein Text"-Punkt aus dem Nutzerfeedback.
- **Layout-Builder**: `LayoutBuilderScreen`/`LayoutBuilderViewModel`; 12
  Reihen, Auswahl pro Profil als `layoutId` in DataStore, Home liest das
  aktive Profil.
- **Source Lab Import/Export**: SAF-Dateipick aus Downloads, Manifest-URL mit
  optionaler SHA-256-Prüfung, Ablage im internen `sources/`, Export als JSON.
  `PackLoader.loadAllPacks()` führt Assets und interne Packs zusammen;
  fehlerhafte Packs werden einzeln verworfen.
- **Sideload-Updater**: `UpdaterManager` (HttpURLConnection + FileProvider),
  Manifest-URL in den Einstellungen, SHA-256-geprüfter Download,
  Systeminstaller. `REQUEST_INSTALL_PACKAGES` + `file_paths.xml`.
- **Doku**: `README.md`, `docs/DEPS.md`, `docs/STATE_TEMPLATE.md` neu.
- **Episoden-Navigation**: `TvmazeEpisodeSource` (schlüsselfrei, legal) liefert
  Folgenlisten für Serien; `DetailScreen` zeigt Staffeln/Folgen, ein Tipp auf
  eine Folge startet den Player mit `episodeId` (Resume je Folge). Parse ist
  fixture-getestet (`TvmazeEpisodesTest`).

#### Behoben
- `HomeScreen`: doppelte, falsche `PlayerRouteFor`-Imports entfernt.
- `ProfileViewModel`: `PinHasher` statt nicht existierendem `ProfilePin`.
- `PlayerScreen`: `androidx.annotation.OptIn` statt Kotlin-`@OptIn`.
- `SearchScreen`: fehlender `width`-Import; Leersuche beim Start entfernt.
- `CatalogPlayback.stableId`: SHA-256/UTF-8 statt SHA-1 (Mimosa-High-Finding).
- `app/proguard-rules.pro` neu angelegt (Release-Referenz war leer).

#### Belege
- **Voller Gate grün:** `:app:assembleDebug` + `:app:lint` (**0 Errors,
  124 Warnings**) + `testDebugUnitTest` über alle Module (**81 Tests,
  0 Fehler, 0 übersprungen**) — Job `frame61-gate3`, exit 0,
  BUILD SUCCESSFUL in 7m 37s, HEAD `4e83c41`.
- APK `debug-app-debug.apk`, 24.487.318 B, SHA-256
  `e6945977ad73cf4caad2b8cc3ec927b7e5c5005d70a06157635dc711150e06f4`
  (lokal gegen Manifest verifiziert).
- Neue Tests: `LayoutPrefsTest`, `UpdateManifestTest`, `TvmazeEpisodesTest`.

### P4 — Detail, Player, Resume, Watchdog (2026-11-16)

#### Hinzugefügt
- `ResumeRepository` (Room) — 5-s-Drosselung, ungedrosselte Writes bei Pause/Stop,
  90-%-Schwelle, Episoden-Trennung: eine fremde `episodeId` erbt keine Position
- `Media3Frame61Player` — `PlayerUiState` als Flow, `publish()` als einzige
  Fortschrittsquelle, Spurwahl für Audio/Text über `TrackSelectionOverride` mit
  echtem Track-Index
- `NextEpisodeCountdown` — 8 s ab 90 %, Abbrechen bleibt für die Wiedergabe storniert
- `PlayerScreen` — Media3-`PlayerView` als AndroidView, Compose-Controls, 48-dp-Ziele,
  Countdown-Overlay
- `PlayerViewModel` — **verdrahtet den Watchdog**: `onFrame` → `PlaybackWatchdog`,
  8 s ohne Frame → `rotateMirror()` mit Positionsübernahme
- `DetailScreen` mit echten Metadaten und „Abspielen", Navigation
  `home → detail/{titleId} → player/{titleId}`
- `CatalogPlayback` — stabile numerische ID (SHA-1, gekürzt) statt `hashCode()`
- `PlaybackLogicTest` (8 Tests)

#### Behoben
- **Countdown bewaffnete sich nach „Abbrechen" neu**, weil der nächste Tick bei
  ≥ 90 % sofort wieder auslöste → `cancelled`-Flag, das bis zum Episodenwechsel hält
- Resume hätte mit den Mirrors nicht übereingestimmt: ViewModel nutzte
  `String.hashCode()`, `CatalogPlayback` aber `stableId()` → vereinheitlicht

#### Belege
- Gate `:app:assembleDebug :app:lint` + 7 Modul-Tests → BUILD SUCCESSFUL,
  **50 Tests, 0 Fehler, 0 übersprungen**
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `1344f3cb912cd61a281dabd28222556fb026678c3e39535a50200e56eb768174`
- Gerät: QUICK_SMOKE PASS, aber **BLOCKED** für Wiedergabe/Resume — A52 im
  gesicherten Keyguard (`mCurrentFocus=NotificationShade`)

#### Bekannte Grenzen
- Wiedergabe, Resume und Watchdog sind am Gerät **nicht** belegt.
- Die einzige Quelle ist ein öffentlicher HLS-Teststrom (Mux), nicht der
  Quelltext des Titels — der Katalog hat Rechte-Metadaten, keine Streams.


### P0–P3 — Fundament, Skins, Branding, TV-Home (2026-11-15)

#### Behoben (stumm kaputt, jetzt belegt)
- **`:oscardp` gelöscht** — `TvFoundationLayout` war ein leerer Composable; Home und Detail renderten eine leere Fläche. `Box` direkt in `:app` ersetzt das (der Kotlin-2.1.20-IR-Compiler konnte `Box` in jenem Modul nicht kompilieren).
- **6 tote Reflection-Dateien entfernt**: `TvFoundationBridge`, `TvLazyRowCompat`, `TvLazyGridCompat`, `TvRow`, `TvGrid`, `lazy/GridCells` — sie liefen in einen leeren Reflektions-Endpunkt.
- **`Modifier.tvFocusable` fokussiert jetzt wirklich**: `focusable()` + `collectIsFocusedAsState()`; der Ring wird mit `drawWithContent` **nach** dem Inhalt gezeichnet (ein `border` am Wrapper wurde von der Karte übermalt) und liegt innerhalb der Bounds.
- **Theme nutzt die JSON-Skins** (`SkinViewModel` → `Frame61Theme(skin = …)`). Am Gerät gemessen: Fokusring `#B5BCFF` (Default) 0,475 % → 0,001 %, Markenring `#FFBCA4` 0,556 %.
- **Fokus stahl nicht mehr die Karte**: die `Card`/`Surface` im Inneren war selbst fokussierbar; jetzt nicht-klickbare `Surface`, Fokus und Klick am Wrapper.

#### Hinzugefügt
- `SkinJson` + `SkinJsonParser`, `DefaultAssetSkinManager`, `FileSkinManager`, `DesignModule` — die 12 Skin-Dateien aus `assets/skins` werden tatsächlich gelesen
- `frame61`-Skin auf Marke umgestellt (`#060913` / `#FF6B35`), `frame61-indigo` als gleichwertiger zweiter Look
- Brand-Assets aus `Prime/FRAME61_*_Assets/`: Launcher-Icon (5 Dichten + adaptiv), TV-Banner, Logo, `colors_frame61.xml`, Markenfarben in `colors.xml`, dunkles `themes.xml` ohne API-27-Attribut
- `assets/catalog/catalog.json` — **46 echte Titel** (30 Serien von TVmaze, 16 Filme von Wikipedia), schlüsselfrei, Provenienz in der Datei, 8/8 Poster per HEAD verifiziert
- `CatalogRepository`, `MetadataSource` (Katalog offline, TVmaze, Wikipedia), `ChainedMetadataSource` mit Dubletten-Zusammenführung
- `TvHome` (Billboard + Rails), `HomeContentBuilder` als reine Funktion, `HomeViewModel`, `SkinViewModel`
- Tests: `SkinJsonParserTest` (5), `CatalogRepositoryTest` (6), `HomeFocusTargetTest` (6)

#### Belege
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :core:design:testDebugUnitTest :core:meta:testDebugUnitTest :scraper:engine:testDebugUnitTest :scraper:resolver:testDebugUnitTest` → BUILD SUCCESSFUL, **42 Tests, 0 Fehler**
- APK `debug-app-debug.apk`, 25 MB, SHA-256 `cc31aaaf168e655cf97a8ebac26c0ebff57a6b3e6c37ed7e80d9c571ccaf126e`
- A52 (SM A525F / Android 14): QUICK_SMOKE **PASS**, 5 Läufe; UI-Hierarchie mit echten Katalogtiteln („Stranger Things", „Top 10", „Neue Folgen", „Shōgun", „Fallout"), 88 158 Farben, Fokusring in Markenfarbe

#### Bekannte Grenzen
- `androidx.tv:foundation:1.0.0` enthält **keine** Lazy-Listen (AAR: 3 Klassen). Verwendet werden `androidx.compose.foundation.lazy.*` + `Modifier.focusGroup()`.
- Coil-Härtung (RGB_565, 512-MB-Cache) fehlt — die Builder-API von Coil 3.1 wurde nicht verifiziert, daher Default-Loader (im Code dokumentiert).
- Live-Suche ist nicht am Gerät getestet; das Home nutzt den gebündelten Katalog.
- `docs/AUDIT.md` ist veraltet und noch nicht nachgezogen.

### M3 — HosterResolver, Mirror-Failover, 8-s-Watchdog (2026-11-15)

#### Hinzugefügt
- `HosterPack`, `HosterStep`, `HosterPackValidator`, `HosterPackLoader` — Datenmodell und Validierung für `assets/hosters/<id>.json`
- `HosterResolver` — Domain-Match (längster Suffix, subdomain-bewusst, Lookalike-fest) und Step-Maschine `get|regex|json|post|sleep` über einem Variablen-Speicher
- `BodyFetcher` + `OkHttpBodyFetcher` — Netzwerkzugriff hinter austauschbarem Interface
- `MirrorSelector` — Reihenfolge healthScore → Sprache → Qualität (numerisch), `legalFree` als Tie-Break
- `PlaybackWatchdog` — 8 s ohne Frame = Stall; ein Frame setzt das Fenster zurück; Position bleibt erhalten
- `MirrorRotation` — rotiert in Reihenfolge, übergibt die Resume-Position, endet bei leerer Liste
- `CaptchaWebViewFrame` in `:app` — sichtbarer WebView-Frame mit genau einer `@JavascriptInterface`-Bridge
- 9 Hoster-Packs: voe, vidoza, streamtape, filemoon, vidmoly, luluvdo, mixdrop, streamhide, loadx
- 20 Unit-Tests in `:scraper:resolver`

#### Entfernt
- `Resolver.kt` mit 26 hartkodierten `SourceConfig`-Attrappen und einem `FailoverController`,
  dessen `withTimeoutOrNull(8000)` um einen **nicht** suspendierenden Block lief — der Watchdog
  hat nie gewartet. Ersetzt durch `MirrorSelector`, `PlaybackWatchdog` und `MirrorRotation`.

#### Geändert
- Platzhalter `{path}` ist jetzt eindeutig der vollständige Pfad **mit** führendem `/`;
  alle Hoster-Packs nutzen `https://{host}{path}`
- Ein deklariertes `result` muss erzeugt werden, sonst `HosterOutcome.Failed` (kein stiller
  Fallback auf die letzte Variable)

#### Belege
- `:scraper:resolver:testDebugUnitTest` → **20 Tests, 0 Fehler, 0 übersprungen**
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :scraper:engine:testDebugUnitTest :scraper:resolver:testDebugUnitTest`
  → BUILD SUCCESSFUL, `exit_code: 0`, **25 Tests, 0 Fehler**
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `d30604d7548177b78caea7673a454f503e58017ae7b692b30e527ba8d1b5e64c`

#### Bekannte Grenzen
- Alle 9 Hoster-Packs sind Attrappen (`verified: false`) — sie belegen die Maschine, nicht die Hoster.
- `CaptchaWebViewFrame` ist ohne Gerät nicht ausgeführt (`NOT_RUN`); die Hoster-Hosts fehlen in
  `network_security_config`.
- `PlaybackWatchdog` ist getestet, aber noch nicht an `PlayerEngine` angebunden.

### M2 — Legal-freie Quellen mit Fixture-Beweis (2026-11-15)

#### Hinzugefügt
- `PackHttpClient` — löst `{{q}}`/`{{page}}`/`{{titleId}}`-Platzhalter, GET/POST, Header
- `FilePackLoader` / `PackLoaderValidator` — Dateisystem-Lader plus Validierung ohne Android-Context, mit sichtbarem Ablehnungsgrund
- `FixtureRunner` — führt `<check>.body.*` gegen `<check>.expected.json` aus, struktureller Vergleich (Zahlen numerisch, Strings exakt, fehlendes Feld = Fehler), Herkunft (`recorded`/`synthetic`) aus `meta.json`
- `PackFixtureTest` (JUnit, offline) — 25/25 Packs valide, 9 Fixture-Packs grün, 0 Tests übersprungen
- Fixtures für 9 legal-freie Quellen: `scraper/engine/src/test/resources/fixtures/<id>/`
- `streamNamePath` + `streamUrlTemplate` im Pack-Schema (Download-URL aus Dateinamen)

#### Geändert
- `archive-org` von HTML-Attrappe auf die dokumentierte JSON-API umgestellt (`advancedsearch.php`, `/metadata/{id}`), Version 2, `verified: true` — mit echten HTTP-200-Antworten belegt
- Validator: eine Regel ohne `endpoint`/`path` ist parse-only erlaubt; das Pack braucht nur *eine* adressierbare Regel (vorher wurden alle 25 Packs verworfen)
- `._*`-AppleDouble-Seiten in `assets/sources` und `fixtures` werden übersprungen

#### Belege
- `./gradlew :scraper:engine:testDebugUnitTest` → `tests=5 failures=0 skipped=0`
- Gate `:app:assembleDebug :app:lint :app:testDebugUnitTest :scraper:engine:testDebugUnitTest` → BUILD SUCCESSFUL
- APK `debug-app-debug.apk`, 23 MB, SHA-256 `ded157620505f26e7892416b3761ebb56200b4e766b98533bdd18076d781bae2`

#### Bekannte Grenzen
- Nur `archive-org` ist mit echten Antworten belegt (`provenance: recorded`). ARD, ZDF, Arte, 3sat, KiKa, Pluto TV, Netzkino, Rakuten Free liefern ohne Auth 404/401; ihre Fixtures sind Strukturmodelle (`provenance: synthetic`) und beweisen den Parser, nicht den Live-Stand.
- Die Tier-A/Grauzone-Packs bleiben unveränderte Attrappen und `UNVERIFIED`.

### Geplant
- Sideload-Dokumentation
- Signiertes APK
- Updater mit WorkManager
- Trakt-Badges

## [1.0.0] — 2026-11-14

### Hinzugefügt

#### Module
- `:app` — Einstiegspunkt, Navigation, Profile
- `:core:design` — Composables, Skins, Fokus
- `:core:data` — Room Schemas, DataStore
- `:core:meta` — TMDB, TVDB, JustWatch
- `:core:net` — OkHttp, Jsoup, HttpClient
- `:scraper:engine` — Quellen-Scraping, Health-Scoring
- `:scraper:resolver` — Mirror-Auflösung, Failover
- `:player` — Media3-Wrapper, Resume
- `:recs` — Empfehlungen, Rec-Vektoren
- `:sources:free` — Legal-free Tier-C-Quellen
- `:work` — WorkManager Updater

#### Quellen (26 total)
- 8 Tier-A: streamkiste.sx, filmpalast.sx, cine.to, s.to, aniworld.to, kinokiste.cloud, megakino.de, dokujunkies.org
- 6 Tier-B: movie4k.to, hdfilme, kinox.to, filmfans.org, bs.to, anime-loads.org
- 11 Tier-C (legal-free): ARD, ZDF, Arte, 3sat, KiKa, Pluto TV DE, Netzkino, Rakuten Free, Plex Free, YouTube, Archive.org

#### Resolver (9)
- voe.sx (WebView-Captcha), vidoza.net, streamtape.com, filemoon.to, vidmoly.to, luluvdo.com, mixdrop.co, streamhide, loadx.ws

#### Skins (11)
- netflix, disneyplus, appletv, primevideo, hbomax, paramount, plex, crunchyroll, kodimodus, frame61, custom

#### Features
- Mirror-Auflösung: healthScore → Sprache → Qualität → legal-free priority
- Health-Scoring: healthScore * 0.5 + langScore * 0.25 + qualityScore * 0.25
- ResumeManager: speichert alle 5s, >90% = watched
- FailoverController: 8s Watchdog pro Mirror
- 48dp D-Pad-Fokusring
- PIN-geschützte Profile
- DataStore-Namespace pro Profil
- TMDB de-DE mit Badges
- Offline-Resume
- Leanback-Unterstützung

### Technisch
- Kotlin 2.1.20, Gradle 8.9, Compose 1.8.1, Media3 1.6.0
- Room 2.7.0 mit MIGRATION_1_2
- KSP 2.1.20-1.0.29
- minSdk 22, ABIs: arm64-v8a, armeabi-v7a
- Coil RGB_565 für Bildoptimierung
- Netzwerk-Security-Config mit 21 Video-Host-Domains

---

*CHANGELOG.md erstellt am 2026-11-14.*
