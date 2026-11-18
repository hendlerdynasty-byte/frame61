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

