# Frame61

Streaming-App für Android TV (Fire OS) und Handy. Katalog-Browser mit
Provider-Fassade: die Oberfläche kennt keine Quelle, der Resolver kennt keine
Oberfläche. Identität eines Titels ist die TMDB-ID; Site-Titel sind Mirrors.

## Rechtsstand — die wichtigste Regel

Frame61 baut **keine** Index-Seiten-Scraper. Alles Grauzonige (streamkiste,
s.to, cine.to, filmpalast, aniworld, megakino, kinokiste, dokujunkies, movie4k,
hdfilme, kinox, bs.to, filmfans, anime-loads) kommt ausschließlich als
JSON-Pack aus `assets/sources/` hinein. Diese Packs sind **Attrappen**:
keine echten Selektoren, `verified: false`, `UNVERIFIED`. Es wird nichts
recherchiert. Die App ist ohne einen einzigen Index-Treffer voll benutzbar —
über die legal-freien Quellen und den mitgelieferten Katalog.

## Architektur

```
:app ──► :core:design  (Skins, Fokus, Theme)
   │ ──► :core:meta     (Katalog, TVmaze, Wikipedia)
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

Media3 mit HLS, Spurwahl (Audio/Untertitel), Tempo, Aspect-Fix.
Watchdog: 8 s ohne Frame → nächster Mirror, ohne UI-Flackern.
Next-Episode-Countdown: 8 s ab 90 %, abbrechbar.
Resume nach Kill exakt, offline aus Room.

## Episoden

Für Serien zeigt die Detailseite die Folgenliste von **TVmaze**
(schlüsselfrei, legal, keine API-Autorisierung nötig). Ein Tipp auf eine Folge
startet den Player mit `episodeId`; Resume wird je Folge gespeichert. Die
Folgennamen kommen per Name-Suche (`singlesearch/shows?embed=episodes`); bei
mehrdeutigen Titeln zeigt die Detailseite den tatsächlich geladenen Seriennamen.

## UI

Elf Token-Skins aus `assets/skins/<id>.json` (canvas, surfaces, accents, text,
radius, cardAspect, rowHeight, gutter, navModel, focusTreatment, motionLevel,
typeScale, billboardHeight, detailLayout, playerChrome). Kein Screen kennt
einen Skin beim Namen; Screens lesen Tokens über `LocalSkin`.

Screens: Home (Billboard + Regale), Suche, Detail, Player, Profilwahl,
Profil-Edit, Skins, Layout-Builder, Quellen (Source Lab), Einstellungen.
TV zuerst: D-Pad, FocusRestorer, 48-dp-Ziele, Fokusring nie abgeschnitten;
Touch als Bonus.

Layout-Builder: 12 Reihen (Billboard, ContinueWatching, BecauseYouWatched,
Top10, NewEpisodes, GenreRail, Collection, FreeLegal, AiringToday, Random,
SourceRail, Marathon). Die Auswahl wird pro Profil als `layoutId`
gespeichert und von der Startseite gelesen.

Profile: optional PIN (SHA-256 mit Profil-ID als Salt), setzen/ändern/
entfernen. Getrennte Namespaces: Resume, Empfehlungen, Layout und erlaubte
Quellen sind je Profil keyed.

## Bauen und Prüfen

Lokale Gradle-Builds sind per Owner-Policy gesperrt. Der Gate-Build läuft
remote:

```sh
python3 ~/hendler-device-ops/remote_build.py --repo "<absoluter Pfad zu frame61>"
```

Das Skript exportiert einen sauberen Git-HEAD (signing-/env-Dateien
ausgeschlossen), baut auf `contabo-build` (JDK17/SDK36), prüft Hashes und
lädt die Artefakte zurück. Voraussetzung: sauberer HEAD; bei WIP einen
isolierten Snapshot anlegen, niemals fremden WIP committen.

Gate: `:app:assembleDebug`, `:app:lint`, `:app:testDebugUnitTest` → grün,
sonst reparieren. Details: `docs/STATE.md`, `docs/AUDIT.md`.

## Sideload

1. APK auf das Gerät bringen (USB, `adb install`, Downloads-Ordner).
2. Installation erlauben (Unbekannte Quellen), App starten.
3. Updater optional: in den Einstellungen eine Manifest-URL setzen
   (`update.json` mit `versionCode`, `versionName`, `apkUrl`, optional
   `sha256`). „Jetzt prüfen" vergleicht die Version; „Installieren" lädt
   die APK, prüft die SHA-256 und öffnet den Systeminstaller.

## Dokumentation

- `docs/OWNER-ÜBERSICHT.md` — **ein Dokument für alles**: Ziel/Wunsch, Ist-Stand,
  was nicht umgesetzt ist und warum, DoD-Checkliste, offene Punkte
- `docs/STATE.md` — was läuft, was offen ist, mit Belegen
- `docs/SOURCE_PACK.md` — Vertrag für Source Packs (Pflichtfelder, Dialekte)
- `docs/SCRAPER.md` — Scraper-Engine und Resolver
- `docs/SKINS.md` — Skin-System und Tokens
- `docs/DEPS.md` — gepinnte Versionen
- `docs/AUDIT.md` — Prüfstand (Build, Module, Screens)
- `docs/CHANGELOG.md` — Änderungen je Phase