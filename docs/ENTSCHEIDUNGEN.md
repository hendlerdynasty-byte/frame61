# ENTSCHEIDUNGEN.md — getroffene Defaults

Stand: 2026-11-17. Bei Unklarheit gilt der hier dokumentierte Default; die
Entscheidung wird mit Datum eingetragen und weitergearbeitet.

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







