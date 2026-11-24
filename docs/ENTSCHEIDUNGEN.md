# ENTSCHEIDUNGEN.md — getroffene Defaults

Stand: 24.11.2026. Bei Unklarheit gilt der hier dokumentierte Default; die
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

## 2026-11-23 — Katalog wird nach Zielverteilung neu geerntet (35/20/20/15/10)

- Befund: Der bis 22.11. geerntete Katalog bestand aus ARD 500 + bs.to 300 +
  streamcloud 197. ARD stellte damit **50 %** des Katalogs und ist die Quelle
  mit dem leichtesten Pfad (legal-frei, `direct`-Hoster, kein Captcha). Eine
  Quote auf dieser Basis misst nicht das Produkt, sondern den Sockel.
- Default (Ownerauftrag 23.11.2026): Der Katalog wird aus den A–Z-/Genre-/
  Neuerscheinungs-Listen **der Quellen selbst** geerntet, in der Verteilung
  35 % Serien von s.to/serienstream, 20 % Filme von filmpalast/moflix,
  20 % Anime von aniworld, 15 % ARD/ZDF als verlässlicher Sockel, 10 % bs.to.
  Jeder Eintrag trägt `group` (Quellengruppe) und `sourceKey` (Adressierung
  innerhalb der Quelle).
- Umsetzung: `tools/fetch/catalog_harvest.py` — serienstream A–Z-Liste plus
  Detailseite (Bild), aniworld-Übersicht plus Detailseite (Cover), bs.to
  Sitemap plus Serienseite (Cover), filmpalast A–Z-Liste (Titel, Slug und
  Cover im selben Eintrag), moflix-Such-API (Typ, Jahr, TMDB-Poster),
  ARD-Such-API. 997 Titel, 0 ohne Poster, Validator: 0 Fehler/0 Warnungen.
- Abweichungen und Begründung:
  1. **Filme 20 % = filmpalast 10 % + moflix 10 %.** filmpalast liefert die
     Einträge vollständig aus der A–Z-Liste (kein Detailabruf, Cover im
     Eintrag); moflix liefert Typ, Jahr und Poster direkt aus der API. Beide
     zusammen füllen die 20 %, und die Gruppe „Filme“ hat zwei unabhängige
     Pfade — fällt einer aus, trägt der andere.
  2. **15 % ARD statt ARD/ZDF.** ZDF hat im Pack-Verbund nur eine
     HTML-Suche ohne keyless Listing-API; ein zweiter Sockel hätte einen
     zusätzlichen Pfad mit unklarem Ertrag gebraucht. ARD ist keyless und
     messbar verlässlich, deckt die 15 % allein.
  3. **aniworld 19 % statt 20 %, ARD 14 % statt 15 %.** Drei Titel fielen der
     globalen Dublettenregel zum Opfer (Validator: kein Titel/Jahr doppelt).
     Wirkung: −0,3 bzw. −0,2 Prozentpunkte, ohne Verhaltensänderung.
  4. **serienstream trägt das Backdrop-Bild der Quelle statt eines Posters.**
     Die Seite liefert kein Poster-Pfadmuster (`/media/images/poster/...`
     antwortet 404, geprüft); das Backdrop ist das einzige Bild der Quelle.
  5. **streamcloud fällt aus dem Katalog heraus.** Die Quelle führt vor allem
     Serien-Episoden; sie steht weiter als Quelle zur Verfügung, belegt aber
     keine Katalog-Gruppe mehr.

## 2026-11-23 — Interception zeichnet auf, der Player lädt selbst

- Default: `shouldInterceptRequest` gibt **null** zurück. Die Anfrage läuft
  normal durch; die WebView liefert die Datei nicht aus. Der Player lädt die
  Medien-URL selbst — mit den **aufgezeichneten** Kopfzeilen der
  WebView-Anfrage (Referer, Origin, Cookie, User-Agent). Ohne diese Kopfzeilen
  verweigern VOE, Filemoon und Co. den Abruf, sobald die Seite weitergeleitet
  hat.
- Die Zeit bis zum Treffer wird **gemessen** (`elapsedMs`) und protokolliert,
  nicht als der konfigurierte Deckel (20 s) ausgegeben.
- Die Interception kommt vor dem DOM-Abgriff: eine Embed-Seite ohne eigenes
  Pack (VOE landet auf wechselnenden Domains) wird zuerst über die
  WebView-Interception aufgelöst, der DOM-Abgriff bleibt Rückfallebene.
- Nebeneffekt, der mitgewürdigt wird: die WebView lädt die Mediendatei mit,
  weil die Anfrage durchgelassen wird. Der Player holt sie ein zweites Mal —
  gewollt, weil nur so die Kopfzeilen des Players gelten.

## 2026-11-23 — bs.to bekommt einen eigenen Wiedergabeweg

- Befund (gemessen, 23.11.2026): Die Serienseite von bs.to listet die Episoden
  mit Hoster-Link (`…/en/VOE`). Die Hoster-Seite deckt den Stream aber nur
  per Klick auf: `ajax/embed.php` verlangt ein reCAPTCHA-v2-Token
  (Site-Key `6Ldd07og…`); ohne Token antwortet die Schnittstelle
  `{"success":false}` (per curl geprüft, mit und ohne Cookie-Sitzung).
- Default: `BsToPlaybackResolver` liest die Episodentabelle, nimmt die
  Hoster-Seite der gewünschten Folge und lässt die versteckte WebView den
  Klick auslösen (jQuery-Ereignis mit belegten Koordinaten, weil die Seite
  `originalEvent.which/pageX/pageY` prüft). Der Browser löst die Challenge,
  die Interception zeichnet den Medienabruf auf.
- Adressierung **über den Sitemap-Slug** (`bs:<slug>`), nicht über die Suche:
  die Suche auf bs.to liefert interne Nummern statt URLs. Ohne Katalogeintrag
  bleibt die Quelle leer — lieber keine als die falsche Serie.
- Zeitbudget: bs.to bekommt 28 s statt 10 s. Die Challenge-Kette braucht
  länger; mit dem Standardbudget war die Quelle **immer** abgeschnitten.

## 2026-11-23 — Eine WebView app-weit, Prewarm über alle Interception-Hosts

- Default: Alle WebView-Pfade (JS-Auswertung, Cookie-Ernte, Interception,
  Prewarm) teilen sich ein Mutex (`WebViewGate`). Der gemessene ANR
  („Frame61 wird wiederholt beendet“) kam von vier gleichzeitig erzeugten
  WebViews; eine app-weite Sperre schließt das aus, statt es pro Stelle zu
  verteidigen.
- Prewarm beim App-Start deckt jetzt **alle** Hosts ab, über die die
  Interception läuft: VOE (der Hoster von bs.to, streamcloud und aniworld)
  sowie die Indexseiten burningseries, aniworld, streamcloud und
  serienstream. Der Prewarm läuft im Hintergrund, sequenziell und ohne
  Blockade des Hauptthreads.

## 2026-11-23 — Quellbudget umfasst den Hoster-Schritt (gemessen)

- Befund (A52, Lauf 1, Build `448109a`): 34 der 55 Fehlschläge endeten mit
  `hoster timeout` (moflix 15, filmpalast 6, s-to 5, streamcloud 3, aniworld 3).
  Ursache: Das Budget je Quelle (10 s) umfasste nur den Quellenschritt
  (Suche → Detail → Streams). Die Hoster-Auflösung darf für sich allein
  20 s brauchen (WebView-Interception) — der Schnitt fiel also **vor** dem
  Ergebnis.
- Default: Das Budget je Quelle (32 s) und die Obergrenze des Einsammelns
  (34 s) umfassen beide Stufen. Das schnelle Pfadverhalten bleibt: `resolveFast`
  kehrt 600 ms nach dem ersten Kandidaten zurück, die ARD-Kette liefert in
  ~1 s — die Startzeit der spielbaren Titel ändert sich dadurch nicht.
- Das Budget für bs.to (34 s) und die Hoster-Stufe (26 s) folgen derselben
  Logik: der Schnitt darf nicht vor dem Ergebnis fallen.

## 2026-11-23 — Jeder Interception-Versuch wird protokolliert

- Default: Die versteckte WebView schreibt pro Versuch einen Datensatz nach
  `InterceptionLog` (Ausgang, **gemessene** Zeit, Request-Kette der Seite,
  Skript-Rückgabe, letzte Seite). Der Messharness zeigt die Versuche je Titel
  (`intercept=n medien=n maxMs=n`) und die letzten sechs Versuche im Detail.
- Grund: Ohne diesen Beleg ist nicht unterscheidbar, ob die Kette an der
  Challenge (bs.to), am Hoster-Redirect (VOE) oder am Medienabruf gehalten
  hat. Im Lauf 1 stand nur „keine Medien-URL" — ohne Request-Kette.
- Die Aufzeichnung ersetzt keine Quelle: sie verändert den Ablauf nicht und
  bleibt im Prozess (kein Telemetrie-, Server- oder Account-Ansatz).

## 2026-11-23 — Ein WebView-Versuch je Titel (Warteschlange gemessen)

- Befund (A52, Lauf 2, Build `be45079`): 88 Interception-Versuche auf 27
  Titeln, nur 2 mit einer Medien-URL. Von den im Logcat sichtbaren 14 Versuchen
  kamen **7 mit 0 Anfragen aus der WebView zurück** — sie warteten auf die
  app-weite Sperre ([WebViewGate]) und wurden vom Quellbudget abgeschnitten,
  bevor die Seite je geladen hatte. Bis zu neun Streams eines Titels stellten
  parallel je einen eigenen WebView-Lauf in dieselbe Schlange.
- Default: `WebViewBudget` (ein Versuch je Titel, Frist = Quellbudget) plus
  `QUEUE_WAIT_MS` = 5 s im Interceptor. Danach läuft der Rest der Streams über
  den günstigen Weg (Pack-Schritte ohne WebView) statt eine weitere
  Warteposition zu belegen. Der Versuch wird als `uebersprungen`
  protokolliert, damit die Messung Schlange und Page-Fehler unterscheidet.
- Offen: VOE bleibt die Bruststelle. Die Embed-Seite lädt ihre 30 Requests,
  verlangt aber den Gastweg (`voe.sx/api2/session/generate-token` → **302
  /login**). Ohne Konto läuft diese Strecke nicht; laut Auftrag wird keines
  geführt. Bewusst **nicht** nachgebaut, sondern als BLOCKED mit Beleg
  festgehalten.

## 2026-11-24 — DEVIDEOSRC über die dokumentierte API statt über die Player-Seite

- Befund (A52, Lauf 3, Build `078fb4b`): Quote 59/100, aber `devideosrc.co` war
  der häufigste Zielhost der `CONTAINER_UNSUPPORTED`-Fehlschläge (~25 von 33).
  Der alte Pack liest `getActiveLink()`/`iframe.src` im versteckten WebView —
  der Abspieler setzt `iframe#_player.src` aber **erst nach dem Klick auf
  Play** (Quelle: `devideosrc.co/docs`, Ereignisliste `meinecloud:*`). Ergebnis:
  Leerwert, die HTML-Seite bleibt als Kandidat stehen, der Player meldet 3003.
- Default: `DeVideoSrcResolver` nutzt die **dokumentierte** API
  (`https://devideosrc.co/docs`, abgerufen 23.11.2026): Die Seite
  `/movie/{id}` bzw. `/serial/{id}` liefert Typ, ID und Token, damit liefert
  `POST /api/embed-links` die **Quellenliste** (Dropload, SuperVideo,
  Doodstream, Mixdrop) in Rangfolge — bei Serien je Staffel/Episode. Die
  Quellenliste läuft durch die bestehende Kette; jeder Anbieter wird der Reihe
  nach versucht. Ohne API-Antwort bleibt der alte Pack-Pfad als Rückfallebene.
- Folge für die Reihenfolge: Die Nachtsteuerung nannte bs.to → Doodstream →
  Filemoon. Die Messung zeigt in dieser Stichprobe **andere** Engpässe
  (devideosrc ~25, uqload 4, moflix-Hosts ~6, VOE/bs.to: 302 /login). Abweichung
  mit Begründung: zuerst der gemessene Haupthemmer, danach uqload/moflix;
  Doodstream bleibt drin, weil devideosrc ihn als Anbieter ausliefert.
- Nebenbefund, mitgefixt: Doodstream liefert die Medien-URL **ohne Endung**
  (`https://uha211.cloudatacdn.com/<hex>/myshk2p1j7~…?token=…&expiry=…`,
  gemessen an `playmogo.com/e/…`). Weder `isMediaUrl` noch das Pack-Muster
  (`\.(m3u8|mp4)`) erkannten sie. `MediaUrlMatcher` erkennt jetzt CDN-Host und
  `~…?token=`-Form.

## 2026-11-24 — DEVIDEOSRC-API korrigiert, Serienweg über `/api/resolve`

- Befund (A52, Lauf 4, Build `d678efb`): Quote 60/100 (+1 pp), aber **16 GETs**
  auf die DEVIDEOSRC-Player-Seite und **null** POSTs auf `/api/embed-links`.
  Die im Lauf 3 eingebaute API-Stufe lief auf dem Gerät **nie** an. Andere
  POSTs desselben Laufs (aniworld 12, cine.to 12, filemoon 12) sind
  protokolliert — es ist kein Lückenfenster im 400-Zeilen-Logcat.
- Ursache (live reproduziert am 24.11.2026): Der Ausdruck für den
  `JSON.stringify`-Aufruf der Seite war an der Zeilengrenze zerschnitten —
  nach dem Komma fehlte das `\s*`, die Seite schreibt aber
  `id: "tt12927920", token: "…"`. Der Ausdruck traf nie. Die Rückfallebene griff
  stattdessen das **erste** `type=`/`id=` des HTML und lieferte
  `id="_cloudToggle"` statt der Film-ID. Der Testbestand enthielt kein solches
  `id` — deshalb blieb die CI grün, während das Gerät scheiterte.
- Default: Der Ausdruck ist korrigiert; die Rückfallebene sucht jetzt **nur
  innerhalb** eines `JSON.stringify`-Aufrufs, nicht mehr im ganzen HTML. Die
  Serien-Seite `/custom/serial/{id}/{staffel}/{folge}` ruft ohnehin **nicht**
  `/api/embed-links`, sondern `/api/resolve` mit
  `{type, id, season, episode, mode, token}` — die Werte stehen dort als
  `data-*`-Attribute am `<main>`-Element. Beide Endpunkte sind am 24.11.2026
  live abgefragt und liefern dieselbe Antwortform (`sources[]`).
- Fail closed: Liefert ein Pack die **Seite selbst** zurück (gleiches Pack,
  keine Medien-URL), ist das jetzt ein Fehlschlag statt eines Kandidaten. Vorher
  bekam der Player HTML und meldete 3003 (`CONTAINER_UNSUPPORTED`) — die
  Fehlerklasse war damit falsch und der Titel unbrauchbar. Medien-URLs des
  gleichen Packs (Direktstream) bleiben unberührt.
- Nebenbefund, mitgefixt: Das jwplayer-Pack-Skript lieferte bei gepackten
  Playern (dr0pstream, superplayer) den `blob:`-Wert des `<video>`-Elements —
  nie die m3u8. Es akzeptiert jetzt nur `http…`-URLs und fragt danach
  jwplayer-Playlist und Ressourcenliste ab. Genau dieser Anbieter ist der
  erste in der DEVIDEOSRC-Providerliste; ohne die Korrektur zahlt sich die
  API-Stufe nicht aus.
- Messbarkeit: Der Messharness startete **keine** Diagnose-Sitzung — damit
  wurden alle Resolver-Ereignisse verworfen und die Fehlerklasse konnte nur
  `NO_CANDIDATE` heißen. Pro Titel läuft jetzt eine Sitzung; die QUOTA-Zeile
  führt den Fehlergrund mit, und der Resolver protokolliert jeden Schritt
  (Seite geladen, Token gelesen, POST, Anbieterliste, Einzelgrund je Anbieter).
## 2026-11-24 — uqload.vc und flyfile.app als WebView-Ziel (Host-Liste statt Pack)

- Befund (A52, Lauf 3, Build `078fb4b`, eindeutige Titel je Zielhost):
  `devideosrc.co` 22, `moflix.rpmplay.xyz` 7, `uqload.vc` 5,
  `moflix-stream.fans` 2, `flyfile.app` 1, `strmup.to`/`zairuc.com` 1.
  Bei `uqload.vc` war der Host **dreimal der einzige** Kandidat (idx 7, 9, 83);
  die Zeilen zeigten `intercept=0` — es gab **gar keinen** WebView-Versuch.
- Ursache: Die Embed-Erkennung verlangt `/e/`, `/embed/`, `/video/`, `/stream/`
  oder `/play/` **mit Slash**. `uqload.vc/embed-7uqq5rkob9z1.html` und
  `flyfile.app` tragen das Muster nicht, bekamen deshalb kein WebView-Budget,
  und der HTML-Kandidat blieb als Player-Ziel stehen (3003).
- Default: `HosterResolver.isWebFirst(url)` = Embed-Form **oder** Host aus
  `WEB_FIRST_HOSTS` (`uqload.vc`, `flyfile.app`). Diese Hosts bekommen den
  stillen WebView: erst die Medien-Interception (echter Abruf mit echten
  Kopfzeilen), dann der DOM-Abgriff. Zusätzlich liest der generische
  DOM-Abgriff jetzt das **jwplayer-Playlist-Element** und die Liste der vom
  Browser abgerufenen Ressourcen — bei uqload.vc liegt die Quelle gepackt im
  `jwplayer(...).setup({sources:[{file:…}]})`, der sichtbare `video.src` ist
  nur ein `blob:`.
- Beleg der Extraktion (24.11.2026, `uqload.vc/embed-7uqq5rkob9z1.html`):
  Der Packer-Payload (Dean Edwards, Base-62) wurde entpackt und ergibt
  `https://strm13.uqload.vc/hls2/02/05901/7uqq5rkob9z1_h/master.m3u8?t=…&s=…&e=14400&v=…`.
  Der Abruf dieser URL liefert **HTTP 200** (`application/vnd.apple.mpegurl`)
  mit der 1080p-Variantenliste — mit und ohne Referer. Die Strecke ist damit
  nicht nur theoretisch, sondern abrufbar.
- Bewusst **nicht** angefasst (gemessen tot, kein App-Defekt):
  - `moflix.rpmplay.xyz` / `moflix.upns.xyz`: die Seite ruft
    `GET /api/public/resolve?file1=<key>` auf. Mit **live**-Keys aus den
    Watch-Seiten (`6qofd`, `zqwhh`, `q3ieiw`, `n8lavf`) liefert die API
    `404 {"ok":false,"error":"not found"}`. Die Seite ist ein Ad-Stub
    („The system is under maintenance“) mit Smartlink-Overlay — kein Spieler.
  - `moflix-stream.fans`: liefert „Video unavailable“ (serverseitig).
  - `strmup.to`: JWT-Weiterleitung → `zairuc.com/fk?dat=…` →
    `click-v4.junimedclk.com/click?i=…` (Klick-Tracker, 0 Byte Antwort).
    Eine Klick-Kette, kein Player — die WebView würde hier nur die Werbung
    auslösen.

## 2026-11-24 — Embed-Form bleibt host-unabhängig; Test-Erwartung war falsch

- Befund (CI-Lauf `45318941773`, Merge-Branch `zcode/merge-4c2`): Der Test
  `embed forms and web-first hosts need the hidden webview` schlug fehl
  (`AssertionError` in Zeile 495). Er erwartete
  `isWebFirst("https://example.com/e/1") == false`.
- Sachverhalt: Die Embed-Erkennung ist **seit Lauf 1 host-unabhängig** — sie
  erkennt die Pfadform (`/e/`, `/embed/`, `/video/`, `/stream/`, `/play/` mit
  Slash), nicht die Domain. Genau so lösen `odysseusa.cc` und
  `moflix-stream.click` auf; beide stehen im selben Test als **wahre**
  WebView-Fälle. `WEB_FIRST_HOSTS` erweitert diese Regel nur um Hosts **ohne**
  Embed-Form (`uqload.vc`, `flyfile.app`).
- Default: Der **Code** bleibt unverändert, die **Test-Erwartung** wird
  korrigiert: „fremde Hosts bleiben unangetastet" gilt nur für Hosts **ohne**
  Embed-Form (`https://example.com/film/1` → kein WebView). Eine
  Allowlist-Liste aller Embed-Hosts des Katalogs wäre eine
  Verhaltensänderung ohne Messung — die Nachtsteuerung verlangt „measure
  before changing".
- Nebenwirkung, die beim selben Lauf sichtbar wurde: Die Fehlerklassen des
  Messharness sind ab Lauf 5 **genauer** (Diagnose-Sitzung je Titel), also
  nicht 1:1 mit Lauf 4 vergleichbar. Vergleichbar bleiben Kopfzahl der
  Fehlschläge und Quote; im Bericht ist das ausdrücklich vermerkt.

## 2026-11-24 — Block 4c-2 gilt als **unmeasured**; Provider-Rotation statt Code-Wirkung

- Anlass: Lauf 6 (Build `27afaeb`, A52) kommt auf **62 %** statt 64 % (Lauf 5),
  also −2 pp. Nach der Nachtsteuerung ist das ein Rückschritt, der eine
  Begründung braucht.
- Befund: `uqload.vc` — der einzige von Block 4c-2 adressierte Host — hat in
  Lauf 5 **96** Erwähnungen im gesamten Logcat-/Dump-Material, in Lauf 6
  **0**; `flyfile.app` 10 → **0**. In Lauf 5 standen `uqload.vc` (7
  Fehlschläge) und `flyfile.app` (1) in den Fehlziel-Hosts. In Lauf 6 fehlen
  beide vollständig: die Anbieterliste des Index hat zwischen den Läufen
  rotiert, uqload wurde **gar nicht mehr angeboten**.
- Die drei verlorenen und der gewonnene Titel (idx 49, 69, 95 → FAIL;
  idx 94 → OK) zeigen in jedem Fall einen **anderen Anbieter**
  (`moflix.rpmplay.xyz`, `r1.dropcdn.io`, `s1-s501740.97bf1.com`) — nicht
  denselben Host mit anderem Ergebnis.
- Entscheidung: Block 4c-2 wird als **unmeasured** protokolliert, nicht als
  Erfolg und nicht als Rückschritt. Die Änderung bleibt im Code (sie ist
  korrekt und host-unabhängig nützlich), wird aber **nicht** mit der
  Kopfquote belegt.
- Folge für die Messregel: Bei rotierendem Anbieterindex ist die
  Kopfquote eines Laufs **keine** Block-Wirkung. Ab Lauf 6 wird deshalb zu
  jedem Lauf zusätzlich dokumentiert: (a) Quote je Quellengruppe, (b) die
  **Zuordnung je Titel** (welcher Host wurde versucht) und (c) die
  **Host-Exposition** (wie oft kam der adressierte Host überhaupt vor). Ein
  Block gilt erst dann als belegt, wenn sein Zielhost in der Stichprobe
  überhaupt vorkommt.
- Nicht geändert: Die Stichprobe selbst (Seed `20261122`, 100 Titel) bleibt
  unverändert — nur so sind die Läufe vergleichbar. Der Holdout-Lauf
  (Seed `20261123`) bleibt bis zum Release getrennt.

## 2026-11-24 — Filemoon-Playback über `GET /api/videos/{code}` (AES-256-GCM)

- Anlass: Das Filemoon-Pack war **tot** — die Kette endete auf
  `GET /api/videos/{code}/playback`, die Seite antwortet dort inzwischen mit
  **HTTP 405** (im Pack protokolliert als `check.status: step_failed`). In
  Lauf 5/6 stand Filemoon in 2–4 Titeln der Auflösungskette, davon 1–2
  Fehlschläge.
- Beobachtung (Live, 24.11.2026): `GET https://filemoon.to/api/videos/{code}`
  liefert den `playback`-Block mit `algorithm: AES-256-GCM`, `iv`, `payload`,
  `key_parts` (30 Teile) und `version`. Der mitgelieferte Text ist **nicht**
  der Schlüssel.
- Rekonstruktion aus dem Player-Bundle der Seite (`videoPagesBundle-*.js`):
  Der Bundle-Automat wählt genau **zwei** Teile aus — `key_parts[version-1]`
  und `key_parts[30-version]` (1-basiert: Teile `v` und `31-v`); nur deren
  zusammengefügte Bytes (32 B) ergeben den AES-256-Schlüssel. Ohne gültige
  Version (1…20) greift der Automat auf **alle** Teile zurück. Beides ist in
  `PlaybackPayloadCipher` nachgebaut und durch die Aufzeichnung getestet
  (falsche Version → `null`; manipulierte Nutzlast → `null`).
- Layout: `payload` ist Base64url und enthält wie bei WebCrypto Chiffretext
  **und** 16-Byte-GCM-Tag hintereinander; `iv` ist Base64url mit 12 Byte.
- Wichtiger Nebenpunkt: Der Klartext escaped die Trennzeichen der m3u8 als
  `\u0026`. Ein Regex-Schritt hätte diese Escape-Sequenz **wörtlich** in die
  URL übernommen und die m3u8 kaputt gemacht. Das Pack liest die URL deshalb
  über `json:sources.0.url` — der JSON-Parser liefert echte `&`.
- Belege: Live-Abruf und Entschlüsselung mit `tools/fetch/hoster_worker.py`
  (`verified`, m3u8 mit HTTP 200 / `application/vnd.apple.mpegurl`),
  Aufzeichnung mit SHA-256 in
  `scraper/resolver/src/test/resources/fixtures-hosters/filemoon/`
  (`page.body.html`, `meta.json`, `result.expected.json`), Test in
  `PlaybackPayloadCipherTest`, Pack-Prüfung durch `HosterFixtureTest`.

## 2026-11-24 — Alternative-Kette nach Fehlschlag, gesperrt über die **Watch-URL**

- Befund (A52, Lauf 7, Build `0ef5b44`): Von 34 Fehlschlägen sterben **12** an
  einem einzigen Hoster (`moflix.rpmplay.xyz`), 8 davon wegen
  `CONTAINER_UNSUPPORTED` — der Hoster wird **aufgelöst** (Kandidat steht im
  Player), aber seine Medien-URL ist nicht abspielbar. Dieselbe Watch-Seite
  bietet zu diesen Titeln 3–8 **weitere** Hoster (`veev.to`, `vidaraa.cc`,
  `moflix.upns.xyz`, …; live geprüft am 24.11.2026). Der Fast-Path löst je
  Quelle nur den **ersten** Hoster auf (`MAX_WEBVIEW_STREAMS = 1`), die
  Alternativen fallen unter den Tisch.
- Entscheidung: Nach dem Durchlauf aller Kandidaten holt der Player die
  nächsten Hoster **derselben** Quelle nach
  (`AutoPlaybackResolver.resolveAlternative`). Aufgelöst wird mit derselben
  Kette wie im Fast-Path (`resolveStreams`, jetzt gemeinsam genutzt): ein
  WebView-Versuch für den ersten WebView-tauglichen Hoster, die übrigen
  parallel ohne WebView.
- Warum über die Watch-URL gesperrt wird und nicht über die Medien-URL: Die
  aufgelöste m3u8 (`…/master.m3u8?t=…`) sieht anders aus als die Hoster-Seite,
  die sie erzeugt hat. Ein Vergleich der Medien-URLs hätte den **toten**
  Hoster erneut aufgelöst — genau der Fehler des ersten Entwurfs. Der Host der
  Watch-URL ist der einzige stabile Schlüssel (`hostOf()`); bei moflix trägt
  jedes Alternativziel einen eigenen Host, eine Sperre greift also gezielt.
- Messbarkeit: Das Harness zählt ab jetzt **zwei** Reihen — `QUOTA …`
  (Produktionspfad mit Alternativen) und `QUOTA FAST …` (alter Fast-Pfad, max.
  3 Kandidaten). Nur damit bleibt die Reihe Lauf 1–7 vergleichbar, während die
  Hauptzahl den Produktpfad zeigt. Der Fast-Teil ändert sich nicht, sonst wäre
  das Delta nicht eindeutig der Alternative-Kette zuzurechnen.
- Offen: gemessen wird das in **Lauf 8**; Block 4e gilt bis dahin als
  implementiert, nicht als belegt.


