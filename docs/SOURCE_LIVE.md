# SOURCE_LIVE.md — erster Ende-zu-Ende-Slice (Block 6)

Stand: 2026-11-17. Quelle: **ARD Mediathek** (legal-frei, keyless).

## Kette

```
Katalogtitel (z. B. „Babylon Berlin")
  → ARD-Suche      /page-gateway/pages/ard/search?searchString=…
  → Show-Grouping  /page-gateway/pages/ard/grouping/{id}?embedded=true&seasoned=true&single=false
  → Folgen         widgets[0].teasers  (S01 …)
  → Stream         /page-gateway/pages/ard/item/{episodeId}?embedded=true
                   widgets[0].mediaCollection.embedded.streams[0].media[]
  → Hoster         assets/hosters/direct.json  (copy: URL unverändert)
  → Player         Media3 HLS/MP4, Resume, Watchdog
```

Beteiligte Dateien:

| Datei | Rolle |
|---|---|
| `assets/sources/ard-mediathek.json` | API-Pack (search/detail/episodes/streams) |
| `assets/hosters/direct.json` | Direktstream-Hoster (`copy`) |
| `app/.../playback/ArdPlaybackResolver.kt` | Titel → Show → Folge → Stream → MirrorCandidate |
| `app/.../ui/player/PlayerViewModel.kt` | Quelle zuerst, Demo nur als sichtbarer Fallback |
| `app/.../ui/player/PlayerScreen.kt` | zeigt „ARD Mediathek" bzw. „Demo-Stream" |
| `scraper/engine/.../PackInterpreter.kt` | API-Detail und API-Folgen implementiert |
| `fixtures/ard-mediathek/` | recorded Fixtures (Suche, Detail, Folgen, Streams) |

## Fixtures (recorded)

`meta.json` nennt die vollständigen Antwort-Hashes; die Bodies sind auf die
relevanten Container getrimmt:

| Check | Inhalt |
|---|---|
| `search` | `showResults` mit 3 Treffern, erster „Babylon Berlin" |
| `detail` | `{title, widgets}` der Grouping-Seite |
| `episodes` | `widgets[0].teasers` (3 Folgen, S01/E01–E03) |
| `streams` | erste 2 `media`-Einträge der Episode S01/E01 |

Der Fixture-Lauf beweist den Parser gegen die echte Antwortform, nicht die
aktuelle Seite. Live-Nachweis: Geräte-Slice (siehe unten).

## Grenzen

- Die ARD-page-gateway-API ist **nicht offiziell dokumentiert** („internal use
  only" laut Developer-Portal); sie ist keyless, kann sich aber ohne Vorwarnung
  ändern. Der Worker prüft Suche/Detail täglich; die Stream-Ebene braucht eine
  Episoden-ID und wird über Fixture + Gerätelauf belegt.
- Es wird nur `widgets[0]` (erste Staffel) als Folgenliste gelesen; weitere
  Staffeln sind eine additive Erweiterung.
- Streams können geo-blockiert sein (`isGeoBlocked`); außerhalb Deutschlands
  fällt der Player auf den Demo-Stream zurück (sichtbar markiert).
- Der Direktstream-Hoster ist ein Pass-through, kein Hoster mit Token-/Captcha-
  Kette.
- Fallback: Wenn ARD nichts Passendes liefert, spielt der öffentliche
  Mux-Demo-HLS; die UI zeigt „Demo-Stream". Das ist kein Quellennachweis.

## Live-Nachweis (A52, 17.11.2026)

Build `ed30818`, APK `995528f86f49d760917d7f6f882b91d32d43fd04a4d50ca9b099ed664b2b0e40`,
Installationsjob `job-91dde4ed45554020` (PASS, 0 Fatal).

| Schritt | Ergebnis |
|---|---|
| Suche | „Babylon" eingegeben, „Suchen" gedrückt → Treffer „Babylon Berlin 2017 · series" |
| Detail | 48 Folgen aus dem Snapshot, „Abspielen" und Folgenliste sichtbar |
| Folge S01/E01 | Tap startet den Player; UI-Dump: Quelle `ARD Mediathek`, `0:12 / 44:55`, Audiospur „Spur 1" |
| Bild | Screenshot `frame61-b6-player/03.png`: ARD-Titelkarte „BERLIN 1929", Fortschritt `0:10 / 44:55` |

Damit ist die Kette Suche → Detail → Folgen → Stream → Direktstream-Hoster →
Player live belegt. Der Player-Fallback „Demo-Stream" wurde in diesem Lauf nicht
benötigt.

**Zusatzfund und Fix:** Das Suchfeld löste ohne IME-Event/Button nie eine Suche
aus (am Gerät reproduziert). `SearchScreen` hat jetzt `ImeAction.Search` und
einen fokussierbaren „Suchen"-Button.
