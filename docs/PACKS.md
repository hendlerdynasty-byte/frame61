# PACKS.md — Status aller Quellen

Automatisch erzeugt von `tools/packs_report.py`; nicht von Hand
pflegen. Stand der Prüfung = `last_check` je Pack.

## Übersicht

- Source-Packs: **29**
- legal-frei: **13**, Grauzone Tier A/B: **17**
- live verifiziert: **8** (aniworld, archive-org, ard-mediathek, filmo, filmpalast, filmstarts, s-to, serienstream)
- Fixtures: 8 recorded, 8 synthetic, 13 ohne
- Hoster-Packs: **11** (live verifiziert: 1)

## Source-Packs

| ID | Tier | legal-frei | Kind | verified | last_check | Fixture | Offen / Grund |
|---|---|---|---|---|---|---|---|
| `3sat` | C | ja | html | nein | 2026-11-17T17:35:14Z | synthetic | kein belegtes Ziel (Worker) |
| `anime-loads` | B | nein | html | nein | 2026-11-17T17:35:14Z | – | kein belegtes Ziel (Worker) |
| `aniworld` | A | nein | html | ja | 2026-11-18T07:37:11Z | recorded | — |
| `archive-org` | C | ja | api | ja | 2026-11-17T17:35:14Z | recorded | — |
| `ard-mediathek` | C | ja | api | ja | 2026-11-17T17:35:16Z | recorded | — |
| `ard` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `arte` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `bs-to` | B | nein | html | nein | 2026-11-18T07:37:12Z | – | Selektoren ohne Treffer: search.resultSelector |
| `cine` | A | nein | html | nein | 2026-11-18T07:37:13Z | – | Selektoren ohne Treffer: search.resultSelector |
| `dokujunkies` | A | nein | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `filmfans` | B | nein | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `filmo` | A | nein | html | ja | 2026-11-18T07:37:14Z | recorded | — |
| `filmpalast` | A | nein | html | ja | 2026-11-18T07:37:14Z | recorded | — |
| `filmstarts` | B | ja | api | ja | 2026-11-18T07:37:14Z | recorded | — |
| `hdfilme` | B | nein | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `kika` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `kinokiste` | A | nein | html | nein | 2026-11-18T07:37:15Z | – | Selektoren ohne Treffer: search.resultSelector |
| `kinox` | B | nein | html | nein | 2026-11-18T07:37:15Z | – | Selektoren ohne Treffer: search.resultSelector |
| `megakino` | A | nein | html | nein | 2026-11-18T07:37:16Z | – | Selektoren ohne Treffer: search.resultSelector |
| `movie4k` | B | nein | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `netzkino` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `plex-free` | C | ja | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `pluto-tv` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `rakuten-free` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |
| `s-to` | A | nein | html | ja | 2026-11-18T07:37:16Z | recorded | — |
| `serienstream` | A | nein | html | ja | 2026-11-18T07:37:25Z | recorded | — |
| `streamkiste` | A | nein | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `youtube` | C | ja | html | nein | 2026-11-17T17:35:17Z | – | kein belegtes Ziel (Worker) |
| `zdf` | C | ja | html | nein | 2026-11-17T17:35:17Z | synthetic | kein belegtes Ziel (Worker) |

## Hoster-Packs

| ID | Domains | Schritte | verified |
|---|---|---|---|
| `direct` | akamaihd.net, daserste.de, ardmediathek.de, ard.de | 1 | nein |
| `doodstream` | doodstream.com, dood.watch, dood.re, do7go.com, playmogo.com | 2 | nein |
| `filemoon` | filemoon.to, filemoon.sx, bysezejataos.com | 4 | nein |
| `loadx` | loadx.ws | 2 | nein |
| `luluvdo` | luluvdo.com, luluvdo.net | 2 | nein |
| `mixdrop` | mixdrop.co, mixdrop.to, mixdrop.ch | 2 | nein |
| `streamhide` | streamhide.to, streamhide.com | 2 | nein |
| `streamtape` | streamtape.com, streamtape.to | 4 | nein |
| `vidmoly` | vidmoly.to, vidmoly.me, vidmoly.net | 2 | ja |
| `vidoza` | vidoza.net | 2 | nein |
| `voe` | voe.sx, voe-unblock.com, voeunblock.com, arianathankspecial.com | 3 | nein |

## Regeln

- `verified: true` nur mit recorded Fixture oder echtem Worker-Lauf.
- Packs ohne belegtes Ziel bleiben `verified: false` und stehen hier offen.
- Ein Pack mit Selektorverlust wird markiert, nicht gelöscht.
- Grauzone-Packs ohne HTML-Beleg bleiben unverifiziert; es werden keine
  Selektoren erfunden.
