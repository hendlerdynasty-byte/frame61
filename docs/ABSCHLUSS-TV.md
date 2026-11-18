# ABSCHLUSS-TV.md — Frame61, Blöcke 0–12

Stand: 18.11.2026. Quellstand `24a1198` (Merge Block 12). Dieses Dokument
ersetzt für das 10-Foot-TV-Ziel die alte `ABSCHLUSS.md`-Liste; die alte Datei
bleibt als Historie des ersten Ziels liegen.

## Gate

| Kennzahl | Wert |
|---|---|
| Job | `frame61-3747730c4e0b-1795004986` |
| Quellstand | `3747730` (Block-12-Branch, identisch gemergt) |
| Exit | `0` |
| Tests | **95/95**, 0 Failures, 0 Errors, 0 Skipped |
| Lint | **0 Errors**, 128 Warnings |
| APK | `31.597.519` B (**30,1 MiB**, Grenze 45 MiB) |
| APK-SHA-256 | `263220c3b986c92cd46a17d1e289584bb2903674af6c945abc22fd7b40d5a4fe` |
| Validator | 600 Katalogeinträge, 600 Meta-Snapshots, 600 Poster, 16 Source-Fixtures, 11 Hoster, 18 Skins — 0 Fehler |

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
| 8 | Hoster-Nachweise | `docs/HOSTERS.md`: Vidmoly verified; VOE/Dood/Filemoon blockiert; sechs ohne Ziel |
| 9 | Index-Nachweise | `docs/INDEX.md`: 6/14 verified (aniworld, filmpalast, filmstarts, filmo, s.to, serienstream) |
| 10 | Katalog 600, lokale Poster, Filter, Fortschritt, Meine Liste | `docs/CATALOG.md`, `docs/design/block10/` |
| 11 | Baseline Profile, R8-Perf, Paging, Ziele | `docs/PERF2.md`: 323 ms / 24 ms / 3,71 % |
| 12 | Signierter Release, Pages-Manifest, Update-Pfad | `docs/RELEASE.md`, Release `v1.1.0` |

## DoD-Abgleich

| # | Kriterium | Status | Beleg |
|---|---|---|---|
| 1 | 10-Foot-Optik, sechs Skins | ✅ | Skin-Bilder, `docs/skins/SKINS2.md` |
| 2 | Handy identisch nutzbar | ✅ | A52-Screenshots Block 2–12 |
| 3 | Nutzer wählt nie eine Quelle | ✅ | Auto-Resolver, Quellen nur unter Einstellungen |
| 4 | Technisches nur in Einstellungen | ✅ | Navigation Block 5 |
| 5 | D-Pad-Vollnavigation, Fokus sichtbar | ✅ | A52-Fokusbelege |
| 6 | Suche aggregiert, dedupliziert, deutsch zuerst | ✅ | `Frame61Search`-Logs, Tests |
| 7 | Fallback ohne sichtbaren Quellenausfall | ✅ | `Frame61Resolve`-Log |
| 8 | Player ohne permanentes HUD | ✅ | A52-Player-Screenshots |
| 9 | 14 Indexseiten live | ⚠ 6/14 | 8 extern blockiert, `docs/INDEX.md` |
| 10 | 10 Hoster live | ⚠ 1/10 | 3 Quellblockaden, 6 ohne Ziel, `docs/HOSTERS.md` |
| 11 | Katalog ≥ 600, lokale Poster, Filter | ✅ | 600/600, Validator, A52 |
| 12 | Fortschritt und Meine Liste persistent | ✅ | Room-Resume, DataStore, A52-Screenshot |
| 13 | Kaltstart p95 < 1200 ms | ✅ | R8-Perf: **323 ms** |
| 14 | Scroll-p95 < 40 ms | ✅ | R8-Perf: **24 ms** |
| 15 | Jank < 5 % | ✅ | R8-Perf: **3,71 %** |
| 16 | Lint 0, Tests grün, APK ≤ 45 MB | ✅ | 0/128, 95/95, 30,1 MiB |
| 17 | Signiertes Release-APK | ✅ | `1c902e37…`, Zertifikat `12:01:F4:…` |
| 18 | `update.json` auf Pages | ✅ | HTTP 200, Lauf `44308280114` |
| 19 | Update-Pfad live | ✅/extern | 1.0.0 → „Version 2“ → Download → SHA → Installer; Play Protect blockt letzte Bestätigung |
| 20 | Installation aus Release | ✅ | `adb install -r` aus Release-Asset, `versionCode=2` |
| 21 | Fire TV unangetastet | ✅ | Kein Fire-TV-Zugriff |
| 22 | Keine Platzhalter/erfundenen Werte | ✅ | Validator, Fixtures, Messprotokolle |

## Bekannte Grenzen

- **Index 6/14 und Hoster 1/10**: Burning Series liefert nur `<option>`-IDs,
  Kinokiste hat ein JWT-Gate, Kinox/Megakino sind tot bzw. ohne Treffer,
  Cine.to sucht clientseitig; Moflix/Movie2k/Streamcloud sind geparkt.
  VOE, Doodstream und Filemoon blockieren per JS-Challenge/Cloudflare/PoW;
  Streamtape, Vidoza, Mixdrop, Luluvdo, Loadx und Streamhide haben kein
  erreichbares Dateiziel. Keine Attrappen.
- **Play Protect**: Die letzte Update-Bestätigung ist auf dem A52 extern
  blockiert. Kein APK an Google gesendet; Installation aus dem Release-Asset
  per ADB belegt.
- **Fire TV**: unangetastet, kein Fire-TV-Lauf.
- **Kein Store**: kein Play-Upload, kein Rollout, keine Store-Freigabe.
- **Performance**: Einzelmessungen auf einem A52; `am start -W` misst den
  Activity-Start, keine geräteübergreifende p95-Studie.
