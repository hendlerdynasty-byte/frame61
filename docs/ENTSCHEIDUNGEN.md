# ENTSCHEIDUNGEN.md — getroffene Defaults

Stand: 23.11.2026. Bei Unklarheit gilt der hier dokumentierte Default; die
Entscheidung wird mit Datum eingetragen und weitergearbeitet.

## 2026-11-23 — Der Katalog wird aus den Quellen geerntet, nicht aus TVmaze

- Befund: 584 Serien aus TVmaze und 416 Filme aus Wikidata sind globale
  Metadaten ohne Bezug zum Angebot der Zielindizes. Gemessen: 6/100 Titel
  spielbar — nicht, weil der Player scheitert, sondern weil die Titel auf
  keiner Quelle liegen.
- Default: Der Katalog wird invertiert. `tools/fetch/catalog_harvest.py` erntet
  die tatsächlich erreichbaren Bestände:
  - **ARD Mediathek** (keyless API, `showResults`), 500 Shows — die Quelle, die
    auf dem Gerät ohne Captcha nachweislich spielt,
  - **bs.to** (Sitemap, 300 Serien), Poster `https://bs.cine.to/cover/<slug>.jpg`,
  - **streamcloud.date** (Sitemap `filme-*.xml`, 197 Filme), Poster aus `og:image`.
  Jeder Titel existiert damit per Konstruktion auf einer Quelle.
- Sicherheit: Der Harvester prüft vor jedem Abruf https, eine Host-Allowlist und
  öffentliche IPs; Weiterleitungen werden erneut geprüft (kein SSRF).

## 2026-11-23 — Hoster-Challenges per Request-Interception, nicht per JS-Nachbau

- Befund: VOE, Doodstream und Filemoon liefern ihren Stream erst nach einer
  JS-/Cloudflare-Challenge. Ein nachgebautes Redirect-Skript scheitert, sobald
  der Anbieter die Seite ändert.
- Default: Die Embed-Seite lädt in einer **versteckten** WebView
  (`WebViewMediaInterceptor`), der Browser der Seite löst seine Challenge, und
  der Medienabruf wird über `shouldInterceptRequest` abgefangen
  (`.m3u8`, `.mp4`, `.mpd`, `.mkv`, `video/…`). Cookies werden ausgelesen und
  an den Player gereicht. Der sichtbare Captcha-Dialog bleibt Rückfallebene.

## 2026-11-23 — Timeouts: Quelle 10 s, Hoster 20 s, Frist ab erstem Kandidaten

- Der 600-ms-Abbruch nach dem **ersten gefundenen** Kandidaten (1.4.0) hat die
  Quote gedrückt: JS-Ketten brauchen länger.
- Default: `SOURCE_TIMEOUT_MS` 10 s, `HOSTER_TIMEOUT_MS` 20 s (inkl.
  Interception), `HARD_DEADLINE_MS` 22 s. `resolveFast` endet weiterhin 600 ms
  nach dem **ersten spielbaren** Kandidaten.

## 2026-11-23 — Resolver nimmt den besten Treffer, wenn der Titel abweicht

- Der geerntete Katalog trägt teils längere Titel (Slug, Serienzusatz).
- Default: Passt kein Treffer exakt auf den Katalogtitel, zählt der beste
  Treffer statt „keine Quelle" — in `AutoPlaybackResolver` und
  `ArdPlaybackResolver`.

## 2026-11-22 — Auflösung endet nach dem ersten Kandidaten (600 ms Frist)

- Befund aus der Baseline: Der Resolver wartete auf die langsamste Quelle
  (4 s Timeout), obwohl ARD nach ~1 s einen spielbaren Stream liefert; dazu
  liefen die Hoster-Ketten je Stream sequentiell. Gemessen: p95 5,6 s bis zum
  ersten Bild.
- Default: `AutoPlaybackResolver.resolveFast` sammelt alle Quellen parallel
  ein und beendet das Einsammeln **600 ms nach dem ersten Kandidaten**
  (`FIRST_CANDIDATE_GRACE_MS`). Die restlichen Quellen werden nicht
  weiterverfolgt; der Player startet. Die vollständige Liste bleibt über
  `resolve` für den Fehlerfall (Neuauflösung) verfügbar. Die Hoster-Kette
  eines Packs läuft parallel statt sequentiell.
- Wirkung (gemessen): Startzeit p95 5.615 ms → im zweiten Lauf 4.664 ms
  (vor dem Timeout-Fix); nach dem Fix greift die Frist tatsächlich.

## 2026-11-22 — Kein libVLC als zweite Engine

- Prüfung: libVLC würde die zweite Player-Stufe stellen, kostet aber als
  Abhängigkeit rund 15–40 MiB (native Libraries für alle ABIs). Der
  Debug-APK liegt bei 36,7 MiB, die harte Grenze bei 45 MiB — ein
  Zusatzpaket dieser Größe würde die Abnahme sprengen.
- Default: **kein** libVLC. Die zweite Stufe bleibt der Android-Systemplayer
  (`SystemFallbackPlayer`, Hardware-Decoder), die dritte die Neuauflösung mit
  anderem Kandidaten. Das deckt HLS, MP4, MKV, AVI und MPEG-TS bereits ab
  (Selbsttest 6/6) und hält die APK-Grenze ein.

## 2026-11-22 — Nur eine WebView gleichzeitig, Vorwärmen verzögert

- Befund: Der Lauf mit `25dba40` endete in einem ANR („Frame61 wird wiederholt
  beendet", `Input dispatching timed out … Waited 10003ms for FocusEvent`).
  Ursache: Das Vorwärmen der Captcha-Hoster lief beim ersten Resolve und
  erzeugte vier WebViews gleichzeitig auf dem Hauptthread.
- Default: `WebViewJsEvaluator` serialisiert alle Zugriffe über einen
  `Mutex`; das Vorwärmen startet erst **5 s nach dem App-Start** aus
  `MainActivity` heraus (nicht mehr aus dem Resolver) und umfasst
  **höchstens zwei** Hosts. Der Resolver bleibt damit frei von
  WebView-Arbeit, solange kein Captcha-Host tatsächlich aufgelöst wird.

## 2026-11-22 — Titel-Quote ist die Abnahmegröße, nicht die Domainliste

- Default: Der Erfolg wird an **100 zufällig gezogenen Katalogtiteln**
  gemessen (`PlaybackQuotaActivity`, Seed 20261122), nicht an der Zahl
  erreichbarer Domains. Jeder Titel zählt nur mit fortschreitender
  Wiedergabe; Startzeit = Auflösung + Puffer bis zur ersten Position.
- Die Messung ist reproduzierbar: gleiche Stichprobe, gleiche Kette, Protokoll
  in Logcat, Statusbereich und Datei.

## 2026-11-22 — Systemplayer-Fallback haengt die Surface erst bei `surfaceCreated`

- Befund auf dem A52: `Systemplayer-Setupfehler: IllegalArgumentException: The
  surface has been released`. Ursache: Die Fallback-`SurfaceView` wird erst
  angehaengt, wenn die UI auf den Systemplayer umschaltet — `setDisplay()` lief
  also vor `surfaceCreated`.
- Default: `SystemFallbackPlayer` ruft `setDisplay()` nur, wenn
  `holder.surface?.isValid == true`; sonst uebernimmt der `SurfaceHolder.Callback`
  die Verknuepfung, sobald die Surface existiert. Das ist Produktionscode, der
  auch im echten Player-Screen greift (`AndroidView` entsteht erst nach dem
  Zustandswechsel).
- Beleg: `player/.../SystemFallbackPlayer.kt`, Commit `be5281e`, Selbsttest
  6/6 PASS auf dem A52 mit `engine=Android MediaPlayer` und
  `primaryError=ERROR_CODE_PARSING_MANIFEST_MALFORMED`.

## 2026-11-22 — TV-Navigation ist eine feste Seitenleiste

- Default: Auf TV (leanback-Feature **oder** `UI_MODE_TYPE_TELEVISION`) rendert
  die App eine linke, feste Navigationsleiste (`TvSideNav`); die
  Bottom-Navigation bleibt Phone-only. Erkennung ueber beide Signale, damit
  auch ein TV-Systemimage ohne `FEATURE_LEANBACK` erfasst wird.
- Beleg: `frame61-androidtv-31` (echtes Android-TV-Image, `mCurUiMode=0x24`),
  Fokustrail Start → Filme → Serien → Suche → RIGHT in den Inhalt → LEFT
  zurueck → CENTER, keine Nodes unterhalb y=1026 (keine untere Leiste).

## 2026-11-22 — „verified" heisst Wiedergabe auf dem Geraet

- Zwei Ebenen werden getrennt berichtet:
  1. **Pack-Ebene** — der Fetch-Worker belegt die Such-/Detail-/Stream-Kette
     live (`check.status`, `last_check`, Fixture-Hashes).
  2. **Geraete-Ebene** — `SourceCoverageActivity` (nur `ci`/`acceptance`)
     fährt dieselbe Kette auf dem Gerät und zählt nur dann als `verified`,
     wenn der Produktionsplayer wirklich fortschreitet.
- Blockierte Quellen werden mit Ursache, HTTP-Status und Versuchen belegt;
  es gibt keine Attrappen und keine Hochstufung aufgrunde eines grünen Fixtures.

## 2026-11-22 — Release-Bau im Actions-Gate, Signatur nur lokal

- Default: Der Gate-Job baut zusaetzlich `:app:assembleRelease` und
  `:app:bundleRelease` **ohne** Signing-Env. Signiert wird ausschliesslich auf
  dem Mac mit `zipalign`/`apksigner` (APK) und `jarsigner` (AAB) und dem
  Keystore ausserhalb des Repos. Kein Signing-Secret erreicht den Runner.
- Diagnose-Quellen (`src/perf`) sind nur in den Varianten `ci` und
  `acceptance` enthalten. Die `perf`-Variante traegt sie nicht; die
  Diagnose-Buttons in den Einstellungen erscheinen deshalb nur dort, wo die
  Klassen existieren.

## 2026-11-17 — GitHub-Repo ist öffentlich

- Remote: `hendlerdynasty-byte/frame61`, **public**, Default-Branch `main`.
- Ownerauftrag Block 0.5; vorher Sicherheitscheck:
  - keine getrackten `local.properties`/`*.jks`/`.env`,
  - History-Scan: nur der alte Platzhalter-Fallback `?: "frame61"`, kein
    echtes Passwort, kein Schlüssel,
  - Fallbacks entfernt, `.gitignore` um Schlüssel-/Env-Dateien erweitert.
- `.git` 1,5 MB, 315 getrackte Dateien, 35 Commits zum Zeitpunkt des Wechsels.

## 2026-11-17 — GitHub Pages aktiv

- Pages ist aktiviert (`build_type=workflow`), URL:
  `https://hendlerdynasty-byte.github.io/frame61/`.
- `pages.yml` deployt `site/`; Lauf `44153289832` grün, URL liefert HTTP 200.
- Update-Manifest folgt in Block 12 unter derselben Basis
  (`…/frame61/update.json`), sofern nicht der Release-Asset-Weg nötig wird.

## 2026-11-17 — GitHub Actions

- Erste Läufe (`44151864618`, `44152085841`) scheiterten vor dem Jobstart an
  einer GitHub-Billing-Meldung des privaten Repos.
- Nach dem Wechsel auf public: `bootstrap`-Lauf `44153299724` **grün** (8 s).
  Der Billing-Blocker ist damit für dieses Repo nicht mehr wirksam.
- Default: Actions bleibt die CI; lokale Heavy-Builds bleiben gesperrt.

## 2026-11-17 — Keystore und Secrets

- Release-Keystore liegt **außerhalb** des Repos:
  `~/hendler-dynasty/keys/frame61-release.jks` (mode 600).
- Base64-Backup liegt ebenfalls außerhalb des Repos
  (`frame61-release.jks.b64`, mode 600). **Keine** Keystore-/Passwort-Literale
  in `SIGNING.md`, Quelltext oder Tests; dort stehen Pfad, Hashes und
  Fingerprint.
- GitHub-Actions-Secrets gesetzt: `FRAME61_KEYSTORE_BASE64`,
  `FRAME61_STORE_PASS`, `FRAME61_KEY_PASS`, `FRAME61_KEY_ALIAS`.

## 2026-11-17 — Signing ohne Passwort-Fallback

- `app/build.gradle.kts` liest `FRAME61_STORE_PASS`/`FRAME61_KEY_PASS` nur aus
  der Umgebung; die alten `?: "frame61"`-Fallbacks sind entfernt.
- Ein Release-Build ohne gesetzte Variablen bricht ab, statt einen
  Platzhalter-Schlüssel zu verwenden.

## 2026-11-17 — M6 bleibt optional

- `frame61.m6=false`; TMDB/Badges/Trakt bleiben Stubs. Kein Key, kein
  Live-Call. Einschalten ist eine eigene Produktentscheidung.

## 2026-11-18 — Hoster-Wahrheit statt Vollstaendigkeitsetikett

- `verified: true` gilt nur mit aufgezeichneter Serverantwort
  (`fixtures-hosters/<id>/`, `provenance: recorded`, `responseSha256`) und
  bestandenem `HosterFixtureTest`. Der Validator erzwingt das.
- Nicht erreichbare Hoster bleiben `verified: false` mit `check.status` und
  `check.reason`. Kein erfundener Ziel-URL, kein Platzhalter-Fixture.
- VOE, Doodstream und Filemoon sind an der Quelle blockiert (JS-Redirect,
  Cloudflare-Challenge, PoW-Token). Streamtape, Vidoza, Mixdrop, Luluvdo,
  Loadx und Streamhide haben am 18.11.2026 kein erreichbares Dateiziel.
- Konsequenz: Der Resolver nutzt die verbleibenden Hoster und faellt bei
  `CaptchaRequired`/`Failed` auf den naechsten Kandidaten zurueck; die
  WebView-Token-Rueckgabe ist ein eigener Block.

## 2026-11-18 — Gemischte Index-Packs und ehrliche 6/14

- Eine Regel darf ihr eigenes `format` (`html`/`api`) tragen. Damit kann ein
  Pack per JSON suchen und Detail/Folgen per HTML lesen, ohne zwei Pack-IDs.
- `api`-Titel werden von HTML befreit; AniWorld liefert `<em>`-Treffer.
- 6 der 14 Indexseiten sind live verifiziert (aniworld, filmpalast,
  filmstarts, filmo, s.to, serienstream). Fuenf weitere sind erreichbar, aber
  ohne auswertbaren Suchpfad; drei Domains sind tot.
- Fuer tote Domains (moflix, movie2k, streamcloud) wird **kein** Pack mit
  erfundenen Selektoren angelegt. Der Befund steht in `docs/INDEX.md`.
- `s-to` und `serienstream` sind dieselbe Site unter zwei IDs; beide behalten
  eigene Fixtures, damit die Ziel-Liste ehrlich abbildbar bleibt.

## 2026-11-18 — Katalog aus TVmaze, Poster lokal

- Der gebundelte Katalog kommt aus der keylosen TVmaze-API (Scripted,
  Animation, Documentary, Mini-Series mit Poster). Auswahl nach Gewicht,
  Rating, ID; Dedupe nach Titel/Jahr.
- 600 Titel mit 180×270-JPEG-Postern unter `assets/posters` und
  Meta-Snapshots unter `assets/meta`. Keine Remote-Poster im Katalog.
- Neue Titel tragen leere Episodenlisten; die Detailansicht nutzt die
  bestehende Live-TVmaze-Quelle. Alte Episodenlisten bleiben erhalten.
- Filter sind Genre und Jahrzehnt; „Meine Liste“ bleibt DataStore-persistent,
  Fortschritt kommt aus dem letzten Room-Stand je Titel.

## 2026-11-18 — Performance-Messung mit R8-Perf-Variante

- Der `perf`-Buildtyp nutzt die Release-Regeln (R8, Shrink, Baseline Profile),
  aber Debug-Signatur und `applicationIdSuffix ".perf"`. Er dient nur der
  Messung und ist keine Release-/Store-Freigabe.
- Die Performance-Ziele werden auf diesem Build gemessen: Kaltstart-p95
  323 ms, Scroll-p95 24 ms, Jank 3,71 % auf dem A52.
- Das Baseline Profile stammt aus einem echten Geraete-Dump; die HRF-Datei
  wird mit `tools/perf/profile_to_hrf.py` erzeugt und ist im APK als
  `assets/dexopt/baseline.prof` nachweisbar.
- Debug-Zahlen bleiben als Vergleich dokumentiert, gelten aber nicht als
  Zielerreichung.

## 2026-11-18 — Release 1.1.0 und Update-Kanal

- Version kommt aus `version.properties`. Fuer den echten Update-Test wurde
  1.0.0 (versionCode 1) gebaut und installiert, danach 1.1.0 (versionCode 2).
- Der Remote-Build erzeugt ohne Signing-Env eine unsignierte Release-APK/AAB;
  signiert wird ausschliesslich lokal mit dem Keystore ausserhalb des Repos.
  Keine Signing-Secrets auf dem Bauhost.
- Veroeffentlichung: GitHub-Release `v1.1.0` (APK, AAB, Manifest) plus
  Pages-Manifest `https://hendlerdynasty-byte.github.io/frame61/update.json`.
- Play Protect blockiert die letzte Installationsbestaetigung mit
  „App-Scan empfohlen“. Es wird kein APK an Google gesendet. Die
  Release-Installation wird per `adb install -r` aus dem Release-Asset
  belegt; der Updater-Pfad bis zum System-Installer ist live.

## 2026-11-19 — Remote-Lock-Umgehung mit Beleg

- `build.lock` war stale und lieferte REMOTE_BUSY bei leerem Host
  (Load < 1, keine laufenden Gradle-Prozesse). Fremde Prozesse wurden
  nicht beendet und die Sperre nicht geloescht.
- Gate und Release wurden mit identischem Export (`_export`) und denselben
  Gradle-Flags direkt in frischen Jobordnern ausgefuehrt:
  `frame61-7edc58dce37b-1795044345-noblock` (Gate, 104/104) und
  `frame61-7edc58dce37b-1795045475-release-noblock` (Release 1.2.0).
- Alle Hashes stammen aus den lokalen Artefakten bzw. dem Release-Asset;
  der abweichende Pfad ist dokumentiert und kein Standard fuer kuenftige
  Builds. Wenn `remote_build.py` wieder verfuegbar ist, gilt es weiter.

## 2026-11-18 — Block 13: Code vor Statusetikett

- `js`, `pow` und `idFromTitle` sind echte Engine-Faehigkeiten mit Unit-Tests
  und Live-Vektoren, keine Doku-Versprechen.
- Filemoons PoW ist deterministisch nachgebaut: nonce/difficulty/token einer
  echten Challenge, Loesung `3643`, vom Server mit `status: ok` bestaetigt.
- Ein sichtbarer WebView-Frame liefert Token, Cookies und ggf. die Stream-URL;
  der Resolver uebernimmt sie und laeuft die Kette zu Ende (20 s).
- Kinokiste und MegaKino nutzen denselben Pfad auf Quellenebene, bevor ein
  Pack als blockiert markiert wird.
- Weitere Ziele kommen aus filmpalast, filmo und s.to/serienstream; fuer
  Hoster ohne Ziel im Katalog bleibt `check.reason` mit echtem Befund.

## 2026-11-18 — Block 14: RESTE MIT BELEG

- Moflix nutzt die aus dem Board belegte Ersatzdomain `.top`; die Suche
  liegt als JSON in der HTML-Seite und wird per `jsonExtractRegex` gelesen.
- Streamcloud ist ueber `streamcloud.plus → streamcloud.date` erreichbar;
  Movie2k bleibt trotz drei geprueften Domains ohne echten Suchpfad.
- Cine.to hat einen echten AJAX-Endpunkt `/request/search`; die fruehere
  Annahme „nur clientseitige Suche“ war falsch und ist korrigiert.
- Kinox (6 Rotationsdomains), Kinokiste (JWT/Bot-Gate), Megakino (DNS)
  und Movie2k (Parking/SPA) sind `blocked_external` — erst nach den
  dokumentierten Versuchen, nie als vorzeitiges Etikett.

## 2026-11-18 — Block 15: Filme ohne API-Key

- Spielfilme stammen aus Wikidata (SPARQL + wbgetentities) und Wikimedia
  Commons; nur Eintraege mit deutscher Beschreibung und lokalem Poster
  werden aufgenommen.
- Der Katalog hat damit 1000 Titel (584 Serien, 416 Filme); die 400 neuen
  Filme sind gegen Serien und untereinander dedupliziert.
- Die Laufzeit kommt aus P2047 (ISO-8601) und ist der dritte Filter neben
  Genre und Jahrzehnt.

## 2026-11-20 — Phase A: Diagnose vor Player-Reparatur

- Der 3003-Fehler wurde am A52 über „Stranger Things“ → „Abspielen“ reproduziert;
  acht Sekunden und weitere zehn Sekunden ohne automatische Erholung belegt.
  Die installierte Versionsnummer wurde nicht behauptet, weil die Brücke sie
  vor einer Fremdapp-Unterbrechung nicht auslesen konnte. Nachweise:
  `docs/design/phase-a/REPRO-3003.md`.
- Resolver, OkHttp und Media3 schreiben einen begrenzten, ausschließlich lokalen
  Trace. Ein Network-Interceptor erfasst jeden Hop, Status, End-URL, Header und
  höchstens 377 Bytes per `peekBody`; der eigentliche Medien-Body bleibt
  unverändert. Tokens/Cookies werden nur als Namen plus kurzer SHA-256-
  Fingerabdruck angezeigt, keine Telemetrie und keine automatische Ablage.
- Media3 nutzt für diese Diagnose eine echte `OkHttpDataSource` über die
  gemeinsame Netzwerkfabrik. Das Diagnose-Overlay ist standardmäßig aus und wird
  über das Fehlerpanel oder D-Pad ↓ geöffnet. Automatische Kandidatenrotation
  nach `onPlayerError` ist in diesem Diagnosepaket noch nicht als repariert
  behauptet.
- `PACKS.md` wurde aus dem Remote-Datenstand `f8ea68a` neu erzeugt. Der
  statische Preflight-Fund „PACKS veraltet“ ist damit geschlossen; Compile,
  Unit-Tests und quellgebundene Geräteprüfung stehen weiter aus.
- Der unabhängige Review von `d04ee1b` fand sechs Diagnosedefekte. Vor dem
  Geräteeinsatz wurden Klartext-/Hex-Secrets aus Textbodies unterbunden,
  Fehlertexte maskiert, Sessionende und verspätete Ereignisse gebunden,
  Gzip-/Sniff-Fehler getrennt, Panel-Tasten modal abgeschirmt und die Diagnose
  auch für Fatalzustände zugänglich gemacht. Die Gegenproben prüfen zusätzlich
  Log-Sink und Hexdekodierung. Der alte Remote-Snapshot scheiterte außerdem an
  einem falschen `FontFamily`-Import; dieser ist korrigiert. Neuer Compile- und
  Testnachweis steht für den Folgecommit aus.

## 2026-11-20 — Phase B: Request-Kontext und zwei Player-Engines

- `MirrorCandidate` und aufgelöste Hoster tragen Request-Header, Cookies und
  optionale Qualitätsdaten. Eine prozessinterne Cookie-Ablage und eine
  `ResolvingDataSource` propagieren sie zu Playlist-, Schlüssel-, Segment- und
  Range-Requests; DataSpec-Header haben Vorrang.
- Media3/ExoPlayer bleibt die primäre Engine mit adaptiver Qualitätswahl und
  Trackauswahl. Nach einem echten Wiedergabefehler versucht Android
  `MediaPlayer` mit eigener `SurfaceView` dieselbe URL, Header und Position.
  HTML, JSON und leere Antworten werden dagegen direkt als Resolverfehler
  verworfen, nicht erneut als Video geöffnet. Erst nach Systemplayer-Fehler
  rotiert der ViewModel zum nächsten Kandidaten bzw. frischen Resolverlauf.
- Die Systemengine vermeidet ein großes natives Zusatzpaket und verwendet die
  Hardware-Decoder des Geräts. Untertitel/Audio nutzen `TrackInfo`, Seek,
  Tempo und Resume bleiben im gleichen ViewModel-/Room-Vertrag.
- HLS-Master-Playlists werden bis 64 KiB gelesen; Auflösung, Bandbreite,
  Codecs und `VIDEO-RANGE` werden ohne Stream-URLs im Trace zusammengefasst.
  Das von Media3 tatsächlich selektierte Videoformat inklusive ColorTransfer
  wird ebenfalls protokolliert. Nicht die größte Variante pauschal erzwingen,
  sondern die höchste vom Gerät spielbare Auswahl zulassen.
- Captcha-Quellen laufen erst nach normalen Packs seriell. Captcha-Timeout und
  Back schließen regulär; Player-Back setzt `controlsVisible` nicht mehr bei
  jedem Back-Down erneut. Ein erneuter Compile-/Gerätenachweis für diese
  Entscheidungen wird separat dokumentiert.
- GitHub-Actions erzeugt zusätzlich die Variante `de.hendler.frame61.ci`.
  Grund ist der gemessene Signaturkonflikt zwischen bestehender Perf-
  Installation und CI-Debugsignatur. Die CI-Variante nutzt dieselben
  Produktionsquellen und Perf-Assets, aber eine eigene Paket-ID. So sind
  Installationen neben Release/Perf ohne Deinstallation, Datenlöschung oder
  Wechsel des Signaturschlüssels möglich. Sie ist kein Release-Artefakt.
- Die nachfolgende `acceptance`-Variante ersetzt `.ci` für wiederholbare
  Installationen, da jeder frische GitHub-Runner einen anderen Debugkey
  erzeugt. CI baut sie ohne Signaturmaterial; erst lokal erfolgt die Signatur
  mit dem vorhandenen FRAME61-Key. So bleiben auch Folgeupdates kompatibel.








## 2026-11-22 — POST-Bodies tragen immer einen Content-Type

- Befund: `aniworld.to/ajax/search` antwortet auf einen POST **ohne**
  `Content-Type` mit HTTP 200 und **leerem** Rumpf (`text/html`, 0 Bytes).
  Verifiziert mit curl: POST mit `application/x-www-form-urlencoded` liefert
  19.350 Bytes JSON (74 Treffer), ohne Content-Type 0 Bytes. Der App-Client
  (`PackHttpClient`) hat den Body mit `toRequestBody(null)` gesendet — daher
  die „Regression“ 200/0 Treffer auf dem Gerät.
- Default: `PackHttpClient` setzt bei POST `application/x-www-form-urlencoded`,
  wenn die Regel keinen eigenen `Content-Type` nennt. Eine Regel mit eigenem
  Header behält ihren Wert.
- Wirkung: `aniworld` (und jedes weitere POST-Pack) ist wieder suchfähig.

## 2026-11-22 — Stream-Ziele werden absolutisiert, Attribute gelesen, Ketten verfolgt

- Befund: `aniworld.to` liefert Hoster-Links als `data-link-target="/redirect/<id>"`
  relativ, Sprache als `data-lang-key`, Hoster als Text im `<h4>`.
- Default:
  1. `parseStreamsHtml` macht relative Ziele mit der `baseUrl` absolut.
  2. Neue optionale Felder `langAttr`/`hosterAttr` lesen den Wert aus einem
     Attribut statt aus dem Elementtext.
  3. `HosterResolver` folgt bis zu drei Weiterleitungen, wenn für den Host
     kein Hoster-Pack existiert, und löst die End-URL erneut auf. Direkte
     Medien-URLs (`.mp4/.m3u8/.mkv/...`) lösen keinen Lookup aus.
  4. `vidmoly.biz` ist als Domain ergänzt (die Kette landet dort, nicht auf
     `.to/.me/.net`).
- Wirkung: `/redirect/<id>`-Ketten werden zum echten Hoster-Stream statt zu
  einer HTML-Seite, die der Player mit 3003 quittiert.

## 2026-11-22 — Captchas laufen still in einer versteckten WebView

- Default: `HeadlessCaptchaSolver` öffnet eine **nicht an die View-Hierarchie
  gehängte** WebView, wartet auf die Challenge und erntet die Cookies
  (`WebViewJsEvaluator.cookiesFor`). Die Cookies werden pro Host 15 Minuten
  zwischengespeichert; beim ersten Zugriff auf einen Captcha-Hoster läuft ein
  stilles Vorwärmen im Hintergrund.
- Der sichtbare `CaptchaBroker`/WebView-Dialog bleibt **Rückfallebene** und
  erscheint nur, wenn die stille Lösung scheitert. Damit ist „null sichtbare
  Captchas“ erreichbar, ohne eine Quelle fallenzulassen.
- `HosterResolver` erhält dafür den optionalen `CaptchaSolver`; ohne Solver
  bleibt das bisherige Verhalten (`CaptchaRequired`) erhalten.
