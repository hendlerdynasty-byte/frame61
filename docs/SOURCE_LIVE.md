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

## Live-Nachweis

Wird nach dem Gerätelauf ergänzt: Katalog → Babylon Berlin → Folge → Player,
Screenshot + Quellenlabel.
