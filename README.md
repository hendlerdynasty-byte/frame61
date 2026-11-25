# Frame61

Streaming-App für Android TV (Fire OS) und Handy. Katalog-Browser mit
Provider-Fassade: die Oberfläche kennt keine Quelle, der Resolver kennt keine
Oberfläche. Identität eines Titels ist die TMDB-ID; Site-Titel sind Mirrors.

## Quellenstand — die wichtigste Regel

Frame61 baut **keine** Index-Seiten-Scraper. Jede Quelle kommt ausschließlich
als JSON-Pack aus `app/src/main/assets/sources/` (plus Hot-Load aus dem
internen `sources/`-Verzeichnis) hinein; Selektoren, Pfade und HTTP-Regeln
stehen im Pack, nicht im Code.

Ein Pack trägt `verified: true`, wenn sein Suchpfad technisch belegt ist:
live am Gerät geprüft (Indexseite liefert Treffer) und durch ein Fixture
abgesichert. Der Stand je Pack steht in [docs/PACKS.md](docs/PACKS.md), die
Index-Nachweise in [docs/INDEX.md](docs/INDEX.md), die Hoster-Nachweise in
[docs/HOSTERS.md](docs/HOSTERS.md). Nicht verifizierte Packs behalten
`verified: false` und einen dokumentierten Sperrgrund — es werden keine
Selektoren ohne Beleg erfunden.

`verified` ist ein **technischer** Nachweis (Antwort, Treffer, Wiedergabe),
keine Rechts- oder Lizenzfreigabe. Die App ist ohne einen einzigen
Index-Treffer voll benutzbar — über die legal-freien Quellen (ARD Mediathek,
Archive.org, ZDF, arte, 3sat, …) und den mitgelieferten Katalog.

## Architektur

```
:app ──► :core:design  (Skins, Fokus, Theme)
   │ ──► :core:meta     (Katalog, Offline-Snapshots, Suche)
   │ ──► :core:data     (Room, DataStore, Resume, PIN)
   │ ──► :core:net      (OkHttp, Jsoup)
   │ ──► :scraper:engine (Packs laden, interpretieren, Fixtures)
   │ ──► :scraper:resolver (Hoster-Packs, Mirror-Failover, Watchdog)
   │ ──► :player        (Media3-Wrapper, Countdown)
   │ ──► :recs          (Empfehlungen)
   └──► :sources:free   (Platzhalter-Modul, legal-freie Quellen leben in Engine+Assets)
```

Module: `:app :player :recs :core:design :core:data :core:meta :core:net
:scraper:engine :scraper:resolver :sources:free` (10 Module; `:oscardp` wurde
in M0 entfernt).

## Die Maschine (Kern)

1. **PackLoader** — liest `assets/sources/*.json` **und** das interne
   `sources/`-Verzeichnis (Hot-Load), validiert gegen `SOURCE_PACK.md`,
   verwirft fehlerhafte Packs einzeln.
2. **PackInterpreter** — führt `search{}`, `detail{}`, `episodes{}`,
   `streams{}` aus: `kind=api` → Dot-Path, `kind=html` → Jsoup-CSS mit
   `@attr`/`@text`. Platzhalter `{{q}} {{page}} {{id}} {{url}}`.
3. **HosterResolver** — Domain-Match gegen `assets/hosters/*.json`, führt
   `steps[]` aus (`get|regex|json|post|sleep`). `captcha=webview` liefert
   einen sichtbaren WebView-Frame mit Bridge, kein Hard-Fail.
4. **SourceRegistry** — hält Packs, Version, `healthScore`: +2 bei Erfolg
   (max. 100), −10 bei Fehler, −30 bei Timeout; unter 20 fällt das Pack aus
   der Rotation.
5. **Hot-Load** — Source Lab importiert Packs aus Downloads (SAF) oder von
   einer Manifest-URL (mit SHA-256-Prüfung) in das interne `sources/`.
   Ein kaputter Import scheitert still, die App läuft weiter.
6. **Source Lab** (Screen) — URL-Feld, Pack-Liste, Live-Test, Treffer-Vorschau,
   Export. Erreichbar über die Navigationsleiste („Quellen").
7. **Fixture-Runner** — JUnit-Test je Pack gegen
   `scraper/engine/src/test/resources/fixtures/<id>/*`, ohne Netz.
   Fehlendes Fixture = Test skipped, nicht rot.

## Datenmodell (Room, Version 1)

`Title`, `Mirror`, `Episode`, `PlaybackState` (mit `updatedAt`),
`Profile`, `RecVector`. Migrationen ab Version 1 sind Pflicht.
Resume schreibt alle 5 s, >90 % = gesehen.

## Player

Media3 (ExoPlayer) als Hauptengine: HLS, progressives MP4, MKV, AVI,
MPEG-TS, Spurwahl (Audio/Untertitel), Tempo, Aspect-Fix. Nach einem echten
Wiedergabefehler übernimmt der Android-Systemplayer mit eigener Surface
dieselbe URL mit derselben Position; HTML-, JSON- und leere Antworten werden
als Resolverfehler verworfen statt erneut als Video geöffnet zu werden.
Request-Kontext (User-Agent, Referer, Cookies) läuft über eine auflösende
DataSource auf jedem Hop.
Watchdog: 8 s ohne Frame → nächster Mirror, ohne UI-Flackern.
Next-Episode-Countdown: 8 s ab 90 %, abbrechbar.
Resume nach Kill exakt, offline aus Room.
Gerätebeleg: [docs/ABSCHLUSS-PHASE-B.md](docs/ABSCHLUSS-PHASE-B.md).

## Episoden und Metadaten

Der Client liest ausschließlich lokale Snapshots aus `assets/meta/<id>.json`
(plus `index.json` für den Suchindex). Es gibt **keinen Live-API-Call** in der
App. Die Snapshots werden vorab mit `tools/fetch/tvmaze_snapshots.py` erzeugt
(TVmaze, CC BY-SA 4.0; Filme aus dem Katalog). Ein Tipp auf eine Folge startet
den Player mit `episodeId`; Resume wird je Folge gespeichert. Fehlt eine
Snapshot-Datei, fällt die Suche auf den mitgelieferten Katalog zurück.
Attribution und Snapshot-Stand stehen unter **Optionen → Info & Quellen**.

## UI

Elf Token-Skins aus `assets/skins/<id>.json` (canvas, surfaces, accents, text,
radius, cardAspect, rowHeight, gutter, navModel, focusTreatment, motionLevel,
typeScale, billboardHeight, detailLayout, playerChrome). Kein Screen kennt
einen Skin beim Namen; Screens lesen Tokens über `LocalSkin`.

Screens: Home (Billboard + Regale), Suche, Detail, Player, Profilwahl,
Profil-Edit, Skins, Layout-Builder, Quellen (Source Lab), Einstellungen.
TV zuerst: D-Pad, FocusRestorer, 48-dp-Ziele, Fokusring nie abgeschnitten;
Touch als Bonus. Die Navigation sitzt auf dem TV als feste linke
Seitenleiste (nicht als untere Leiste); Fokusweg DOWN/UP/RIGHT/LEFT/CENTER ist
auf dem Android-TV-Emulator belegt
([docs/design/phase-b/tv-dpad-trail-1.3.0.txt](docs/design/phase-b/tv-dpad-trail-1.3.0.txt)).

Layout-Builder: 12 Reihen (Billboard, ContinueWatching, BecauseYouWatched,
Top10, NewEpisodes, GenreRail, Collection, FreeLegal, AiringToday, Random,
SourceRail, Marathon). Die Auswahl wird pro Profil als `layoutId`
gespeichert und von der Startseite gelesen.

Profile: optional PIN (SHA-256 mit Profil-ID als Salt), setzen/ändern/
entfernen. Getrennte Namespaces: Resume, Empfehlungen, Layout und erlaubte
Quellen sind je Profil keyed.

## Quellen

- **ARD Mediathek** (legal-frei, keyless): Suche → Show → Folgen → HLS/MP4 →
  Player. Der Direktstream läuft über den `direct`-Hoster (Pass-through).
  Live am A52 belegt (Babylon Berlin, S01/E01).
- **Archive.org** (legal-frei, keyless): API-Pack mit recorded Fixture, vom
  Worker live verifiziert.
- **Index-Packs** (aniworld, bs-to, cine, filmo, filmpalast, filmstarts,
  moflix, s-to, serienstream, streamcloud u. a.): Suchpfad technisch belegt.
  Stand 25.11.2026: 10/14 Indexseiten `verified` ([docs/INDEX.md](docs/INDEX.md)),
  am A52 liefern 9/14 Treffer; Hoster 3/10 Kette-verifiziert (`direct`,
  `vidmoly`, `filemoon`), davon 2 auf dem A52 abgespielt (`direct`, `vidmoly`).
  Blockierte Quellen stehen mit HTTP-Status und Grund in
  [docs/INDEX.md](docs/INDEX.md), [docs/HOSTERS.md](docs/HOSTERS.md) und
  [docs/ABSCHLUSS-PHASE-B.md](docs/ABSCHLUSS-PHASE-B.md).
- Nicht verifizierte Packs behalten `verified: false` und einen dokumentierten
  Sperrgrund; Status und Grund je Pack stehen in
  [docs/PACKS.md](docs/PACKS.md). Es werden keine Selektoren ohne Beleg
  erfunden.
- Der Player zeigt die Quelle an („ARD Mediathek" oder „Demo-Stream"). Der
  Demo-HLS ist nur ein sichtbarer Fallback.

## Bauen und Prüfen

Lokale Gradle-Builds sind per Owner-Policy gesperrt. Der Gate-Build läuft
remote:

```sh
# schnell, lokal, ohne Gradle
scripts/preflight.sh

# voller Gate über GitHub Actions (kostenlos, bereits konfiguriert)
gh workflow run android-gate.yml --ref main

# Alternative: Remote-Build auf dem Firmenserver
python3 ~/hendler-device-ops/remote_build.py \
  --repo "<absoluter Pfad zu frame61>" --tasks "gate"
```

Das Actions-Gate baut `debug`, `perf`, `acceptance` und ein **unsigned**
Release-APK/AAB und lädt sie als Artefakte hoch. Signiert wird **lokal am
Mac** (Keystore ausserhalb des Repos, Passwort aus dem macOS-Keychain):

```sh
scripts/sign_acceptance.sh <unsigned.apk> <signed.apk>   # zipalign + apksigner
scripts/sign_release_aab.sh <unsigned.aab> <signed.aab>   # jarsigner
```

Kein Signing-Secret verlässt den Mac. Voraussetzung für jeden Bau: sauberer
HEAD; bei WIP einen isolierten Snapshot anlegen, niemals fremden WIP
committen.

`./gradlew gate` bündelt `:app:assembleDebug`, `:app:lint`, alle
Unit-Tests, den Pack-/Meta-Validator, die Fetch-Werkzeug-Tests, den
PACKS.md-Abgleich und die APK-Größenprüfung (≤ 45 MiB). Details:
[STAND.md](docs/STAND.md), [SCHEMA.md](docs/SCHEMA.md),
[PACKS.md](docs/PACKS.md), [PERF2.md](docs/PERF2.md),
[SIGNING.md](docs/SIGNING.md), [UPDATE.md](docs/UPDATE.md),
[RELEASE.md](docs/RELEASE.md).

## Sideload

1. APK auf das Gerät bringen (USB, `adb install`, Downloads-Ordner).
2. Installation erlauben (Unbekannte Quellen), App starten.
3. Updates über `update.json` (siehe [docs/UPDATE.md](docs/UPDATE.md)).
3. Updater optional: in den Einstellungen eine Manifest-URL setzen
   (`update.json` mit `versionCode`, `versionName`, `apkUrl`, optional
   `sha256`). „Jetzt prüfen" vergleicht die Version; „Installieren" lädt
   die APK, prüft die SHA-256 und öffnet den Systeminstaller.

## Dokumentation

- `docs/ABSCHLUSS-PHASE-B.md` — **aktueller Abschlussbericht**: Ampel je
  Arbeitspaket, Quellzahlen, Tests, Lint, Performance, Artefakt-Hashes,
  blockierte Quellen mit HTTP-Status und Grund
- `docs/STATE.md` — was läuft, was offen ist, mit Belegen
- `docs/RELEASE.md` — Release 1.5.0/VC6 (signierter Kandidat, APK/AAB, Signatur,
  Hashes) und der live 1.3.0-Stand mit Update-Pfad
- `docs/ABSCHLUSS-TV.md` — TV-Ziel (Blöcke 0–18 + Phase B, DoD-Tabelle)
- `docs/OWNER-ÜBERSICHT.md` — **ein Dokument für alles**: Ziel/Wunsch, Ist-Stand,
  was nicht umgesetzt ist und warum, DoD-Checkliste, offene Punkte
- `docs/INDEX.md` / `docs/HOSTERS.md` / `docs/PACKS.md` — Quell-, Hoster- und
  Pack-Stand mit Belegen
- `docs/SOURCE_PACK.md` — Vertrag für Source Packs (Pflichtfelder, Dialekte)
- `docs/SCRAPER.md` — Scraper-Engine und Resolver
- `docs/SKINS.md` — Skin-System und Tokens
- `docs/DEPS.md` — gepinnte Versionen
- `docs/AUDIT.md` — Prüfstand (Build, Module, Screens)
- `docs/CHANGELOG.md` — Änderungen je Phase