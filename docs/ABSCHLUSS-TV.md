# ABSCHLUSS-TV.md — Frame61, Blöcke 0–18 + Phase B

> **Stand-Hinweis (25.11.2026):** Dieses Dokument beschreibt den TV-Stand des
> Releases **1.3.0 / VC4** (Quellstand `9353e5a`). Die Blöcke 7–9 der 1.5.0
> ändern die TV-Oberfläche **nicht** (Player-, Resolver- und
> Episodenadressierung); die hier belegte Navigation und der TV-Start gelten
> unverändert. Aktueller Release-Stand: [STATE.md](STATE.md),
> [RELEASE.md](RELEASE.md).

Stand: 22.11.2026. Quellstand `9353e5a` (Merge Phase B auf `main`), Release
**1.3.0 / VersionCode 4**. Dieses Dokument ersetzt für das 10-Foot-TV-Ziel die
alte `ABSCHLUSS.md`-Liste; die alte Datei bleibt als Historie des ersten
Ziels liegen. Der vollständige Phasen-B-Bericht steht in
[ABSCHLUSS-PHASE-B.md](ABSCHLUSS-PHASE-B.md).

## Phase B (22.11.2026) — TV-Stand

| Gegenstand | Wert | Beleg |
|---|---|---|
| Release auf dem TV | signiertes Release-APK 1.3.0 / VC4, `versionCode=4`, `versionName=1.3.0` | `docs/RELEASE.md` |
| Start | `Status: ok`, `LaunchState: COLD`, `TotalTime 1114 ms` | `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` |
| Stabilität | 0 FATAL EXCEPTION, 0 ANR | `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` |
| Navigation | feste linke Seitenleiste (x=106..262), RIGHT in den Inhalt (x≥278), LEFT zurück, CENTER wählt; **keine untere Leiste** | `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` |
| Startbild | `docs/design/phase-b/tv-release-startup-1.3.0.png` (1.486.092 B) | Emulator `frame61-androidtv-31`, `emulator-5554`, `uimode 0x24`, leanback vorhanden |
| Playback auf TV | **NOT_RUN** — auf dem Emulator wurde Navigation und Start belegt, keine Stream-Wiedergabe | — |
| Echtes Fire TV | **NOT_RUN**, Gerät unangetastet (Owner-Policy: keine ADB-Befehle, keine Screenshots, keine Tests) | — |

## Gate (Blöcke 0–18)

| Kennzahl | Wert |
|---|---|
| Job | `frame61-7edc58dce37b-1795044345-noblock` |
| Quellstand | `7edc58d` (Block 18, nach Merge `89d5fff`) |
| Exit | `0` |
| Tests | **104/104**, 0 Failures, 0 Errors, 0 Skipped |
| Lint | **0 Errors**, 125 Warnings |
| Debug-APK | `35.801.833` B (**34,1 MiB**, Grenze 45 MiB) |
| APK-SHA-256 | `e64780a8a081df5ee777dd5a8567206106a551d1e23c282aada3296a147f4ffa` |
| Validator | 1000 Katalogeinträge, 1000 Meta-Snapshots, 1000 Index-Einträge, 20 Source-Fixtures, 2 Hoster-Fixtures, 33 Packs — 0 Fehler |

## Blöcke

| Block | Ergebnis | Beleg |
|---|---|---|
| 0/0.5 | Repo public, Pages, Actions, Keystore/Secrets | `docs/CHECKPOINT-BLOCK0.md`, Actions `44153299724`, Pages `44153289832` |
| 1 | Inventur, Preflight | `docs/STAND2.md`, `scripts/preflight.sh` |
| 2 | Eine zentrale `PosterCard`, Ecken sauber | `docs/design/COVER.md`, Vorher/Nachher am A52 |
| 3 | Safe-Area 5 %/3 %, Fokus 1.08 + Ring, 2:3/190 dp, D-Pad | `docs/design/TVFIRST.md`, A52-Screenshots |
| 4 | Theme-Engine, sechs Skins, Live-Vorschau, DataStore | `docs/skins/SKINS2.md`, sechs Skin-Bilder |
| 5 | Sechs Navigationsziele, Einstellungen-Baum, kein Technik-Menü | `docs/navigation/NAVIGATION.md` |
| 6 | Parallele Auto-Auflösung, 4 s Timeout, 15-min-Cache, Fallback-Log | `docs/source/AUTO.md`, `Frame61Resolve`-Log |
| 7 | Overlay 3 s, D-Pad, Seek-Gradient, Quelle 3 s, kein HUD | `docs/player/PLAYER.md`, A52-Screenshots |
| 8 | Hoster-Nachweise | `docs/HOSTERS.md`: Vidmoly + direct verified; VOE/Dood/Filemoon mit Grund; sechs ohne Ziel |
| 9 | Index-Nachweise | `docs/INDEX.md`: 10/14 verified (aniworld, bs-to, cine, filmpalast, filmstarts, filmo, moflix, s.to, serienstream, streamcloud) |
| 10 | Katalog 600, lokale Poster, Filter, Fortschritt, Meine Liste | `docs/CATALOG.md`, `docs/design/block10/` |
| 11 | Baseline Profile, R8-Perf, Paging, Ziele | `docs/PERF2.md`: 323 ms / 24 ms / 3,71 % |
| 12 | Signierter Release, Pages-Manifest, Update-Pfad | `docs/RELEASE.md`, Release `v1.1.0` |
| 13 | `js`, `pow`, `idFromTitle`, WebView-Broker, Hoster-Ziele | `docs/HOSTERS.md`, `docs/SOURCE_PACK.md`, 103/103 Tests |
| 14 | Indizes 10/14 | `docs/INDEX.md`, `jsonExtractRegex`, 104/104 Tests |
| 15 | Filme in den Katalog, 1000 Titel, Laufzeitfilter | `docs/CATALOG.md`, 416 Filme, 1009 Poster |
| 16 | Android-TV-Emulator, Leanback, D-Pad, 720p/1080p | `docs/FIRETV.md`, `docs/design/block16/` |
| 17 | Play-Protect-Hinweis mit Bild, Update auf TV | `docs/PLAYPROTECT.md`, `docs/design/block17/` |
| 18 | Release 1.2.0, Pages, Update 1.1.0 → 1.2.0 | `docs/RELEASE.md`, `docs/design/block18/` |

## DoD-Abgleich

| # | Kriterium | Status | Beleg |
|---|---|---|---|
| 1 | 10-Foot-Optik, sechs Skins | ✅ | Skin-Bilder, `docs/skins/SKINS2.md` |
| 2 | Handy identisch nutzbar | ✅ | A52-Screenshots Block 2–17 |
| 3 | Nutzer wählt nie eine Quelle | ✅ | Auto-Resolver, Quellen nur unter Einstellungen |
| 4 | Technisches nur in Einstellungen | ✅ | Navigation Block 5 |
| 5 | D-Pad-Vollnavigation, Fokus sichtbar | ✅ | A52- und TV-Fokusbelege |
| 6 | Suche aggregiert, dedupliziert, deutsch zuerst | ✅ | `Frame61Search`-Logs, Tests |
| 7 | Fallback ohne sichtbaren Quellenausfall | ✅ | `Frame61Resolve`-Log |
| 8 | Player ohne permanentes HUD | ✅ | A52-Player-Screenshots |
| 9 | 14 Indexseiten live | ⚠ 10/14 (Block 18) | 4 extern blockiert mit Domain-/Pfadbeleg, `docs/INDEX.md` |
| 10 | 10 Hoster live | ⚠ 2/10 (Block 18) | 3 Quellblockaden, 5 ohne Ziel, `docs/HOSTERS.md` |
| 11 | Katalog ≥ 600, lokale Poster, Filter | ✅ | **1000/1000**, 416 Filme, Validator, A52 |
| 12 | Fortschritt und Meine Liste persistent | ✅ | Room-Resume, DataStore, A52-Screenshot |
| 13 | Kaltstart p95 < 1200 ms | ✅ | R8-Perf: **323 ms** |
| 14 | Scroll-p95 < 40 ms | ✅ | R8-Perf: **24 ms** |
| 15 | Jank < 5 % | ✅ | R8-Perf: **3,71 %** |
| 16 | Lint 0, Tests grün, APK ≤ 45 MB | ✅ | 0/125, 104/104, 34,1 MiB |
| 17 | Signiertes Release-APK | ✅ | `da4e5011…`, Zertifikat `12:01:F4:…` |
| 18 | `update.json` auf Pages | ✅ | HTTP 200, Lauf `44479057163` |
| 19 | Update-Pfad live | ✅ | 1.1.0 → „Version 3“ → Download → SHA → Installer → 1.2.0 (`versionCode=3`) |
| 20 | Installation aus Release | ✅ | `adb install -r` aus Release-Asset auf TV-Emulator, `versionCode=3` |
| 21 | Fire TV unangetastet | ✅ | Kein echtes Fire-TV-Gerät benutzt |
| 22 | Keine Platzhalter/erfundenen Werte | ✅ | Validator, Fixtures, Messprotokolle |
| 23 | TV-Navigation: untere Leiste durch Seitenleiste ersetzt | ✅ (Phase B) | Fokusweg DOWN/UP/RIGHT/LEFT/CENTER belegt, `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` |
| 24 | TV trägt den Release-Stand 1.3.0 / VC4 | ✅ | Release-APK installiert, `versionCode=4`, 0 Crash, 0 ANR |
| 25 | 14 Indexseiten live (Messung am Gerät, Phase B) | ⚠ 9/14 | 5 blockiert mit HTTP-Status und Grund, `docs/design/phase-b/coverage-a52-run4-60512d4.txt` |
| 26 | 10 Hoster live (Messung am Gerät, Phase B) | ⚠ 2/11 | 9 blockiert mit HTTP-Status und Grund, `docs/HOSTERS.md` |
| 27 | TV-Wiedergabe einer Streamquelle | ⚠ NOT_RUN | Auf dem Emulator belegt sind Start und Navigation, keine Stream-Wiedergabe; Fire TV unangetastet |

## Bekannte Grenzen

- **Index und Hoster (Messung am Gerät, Phase B):** 9/14 Indexseiten, 1/14
  Wiedergabe über den Indexpfad, 2/11 Hoster. Aniworld (HTTP 200, 0 Treffer),
  Kinokiste (HTTP 401), Kinox (HTTP 404), Megakino (HTTP 200, Platzhalter) und
  Movie2k (kein Server) bleiben extern blockiert; VOE, Doodstream (Captcha) und
  Filemoon (HTTP 405) haben konkrete Quellblockaden; Streamtape, Vidoza,
  Mixdrop, Luluvdo, Loadx und Streamhide haben kein erreichbares Dateiziel.
  Keine Attrappen. Details: `docs/ABSCHLUSS-PHASE-B.md`, `docs/HOSTERS.md`.
- **Play Protect**: Auf dem A52 ist die letzte Bestätigung extern blockiert;
  kein APK an Google gesendet. Auf dem TV-Emulator lief der Pfad durch
  „Install unknown apps" komplett bis `versionCode=3`.
- **Fire-TV-Hardware**: unangetastet; Emulator-Startzeiten sind keine
  Hardware-Messwerte. Auf dem TV-Emulator ist die Phase-B-Navigation belegt,
  die Stream-Wiedergabe dort nicht (NOT_RUN).
- **Kein Store**: kein Play-Upload, kein Rollout, keine Store-Freigabe.
- **Performance**: Einzelmessungen auf einem A52; `am start -W` misst den
  Activity-Start, keine geräteübergreifende p95-Studie.
- **Remote-Build-Lock**: `build.lock` war stale und lieferte REMOTE_BUSY bei
  leerem Host. Gate und Release wurden mit identischem Export und denselben
  Gradle-Flags direkt in frischen Jobordnern ausgeführt (keine fremden
  Prozesse beendet). Jobs und Hashes sind oben dokumentiert.
- **Release-Bau in Phase B:** GitHub Actions statt Remote-Build; das Signing
  bleibt lokal am Mac, kein Secret-Transfer.
