# Phase B — Geraetenachweise

Kuratsierte Nachweise aus der Phase B (22.11.2026). Die Dateien hier sind
klein und textlich auswertbar; die vollstaendigen Aufnahmen (Screenshots,
Logcats, XML-Dumps) liegen ausserhalb des Repos unter
`~/hendler-dynasty/evidence/frame61/`, weil sie Geraetekennungen und
Systemlogs enthalten.

## Zuordnung

| Datei | Belegt | Geraet / Stand |
|---|---|---|
| `selftest-a52-6of6.xml` | Player-Selbsttest 6/6 PASS: HLS, MP4, MKV, AVI, MPEG-TS mit Media3, Fallback-Fall auf dem Android Systemplayer nach echtem `ERROR_CODE_PARSING_MANIFEST_MALFORMED` | Samsung A52, `be5281e`, Job `job-0ee3d0be1e3b4c42` PASS, APK `a9a412f8…` |
| `stress-a52-session-pass.xml` | 30-Minuten-Sessiontest: `SESSION PASS titles=50 resumeJumps=20 profileChanges=10 errors=0 elapsedMs=1812788 maxHeap=39512624` | Samsung A52, `be5281e` |
| `tv-dpad-focus-trail.txt` | TV-Navigation: Fokus wandert in der linken Seitenleiste (x=106..262), RIGHT geht in den Inhalt (x>=278), LEFT zurueck in die Leiste, CENTER waehlt — keine untere Leiste | Android-TV-Emulator `frame61-androidtv-31`, `be5281e` |
| `coverage-a52-run2-627ef8b.txt` | Quellen-Abdeckung Lauf 2: 9/14 Indexseiten, 1/14 Wiedergabe, 0/11 Hoster — mit HTTP-Status und Grund je Quelle | Samsung A52, `627ef8b`, APK `8c23da2e…` |
| `coverage-a52-run3-18ad7eb.txt` | Quellen-Abdeckung Lauf 3 mit echter Playerflaeche: Status ScrollView [0,88..845], Videofeld [0,845..2361]; `exo_content_frame` [0,1378..1827]; moflix laeuft (Media3, playing=true); `direct` und `vidmoly` ready+playing, aber Position 0 (Harness las den Ereignisstand) | Samsung A52, `18ad7eb`, APK `b2a2026e…`, Job `job-9b2520cd888944bd` PASS |
| `playback-running-18ad7eb.png` | **Laufende Wiedergabe** im Videofeld (31.311 verschiedene Farben, Streuung 21,3) | Samsung A52, `18ad7eb` |
| `coverage-summary-screen-18ad7eb.png` | Abdeckungsuebersicht mit Videofeld waehrend des Wiedergabefensters (59.348 Farben, Streuung 19,6) | Samsung A52, `18ad7eb` |
| `playback-screenshot-18ad7eb.txt` | Pixelauswertung der beiden Screenshots (Videofeld sichtbar / frueher schwarz) | Samsung A52, `18ad7eb` |
| `coverage-ui-18ad7eb.xml` | UI-Dump: Layoutflaechen, `exo_content_frame` und vollstaendiger Statustext | Samsung A52, `18ad7eb` |

## Was „verified" heisst

Nur ein Quelltext, der auf dem Geraet real fortschreitet, gilt als verifiziert.
Ein gruener Fixture-Test oder ein gruener Build belegt das nicht. Blockierte
Quellen werden mit Ursache, HTTP-Status und Versuchen benannt — siehe
`docs/ABSCHLUSS-PHASE-B.md`.

## Rohdaten

| Lauf | Verzeichnis |
|---|---|
| Selbsttest | `~/hendler-dynasty/evidence/frame61/phase-b-selftest-be5281e/` |
| Sessiontest | `~/hendler-dynasty/evidence/frame61/phase-b-stress-be5281e/` |
| TV-Navigation | `~/hendler-dynasty/evidence/frame61/tv-be5281e/` |
| Quellen-Abdeckung | `~/hendler-dynasty/evidence/frame61/phase-b-coverage-*/` |
| Zwischenlaeufe (nicht im Repo) | `~/hendler-dynasty/evidence/frame61/phase-b-intermediates-20261121/` |

## Hashes der kuratierten Dateien

```
4f32b6f149c62deee047d98d65251c27a5611b994cb84880aaac61a6d1971942  selftest-a52-6of6.xml
b36bc9ce8709885a3033eb8dc861f0d6081756954fc1bfe1e79c4c923fe17d96  stress-a52-session-pass.xml
```

Die TV-Trail-Datei entsteht aus `tv-be5281e/dpad-01/*.xml` (je Dump der
fokussierte Knoten mit Text und Bounds). Die Hashes der Abdeckungs- und
Wiedergabedateien stehen in `docs/ABSCHLUSS-PHASE-B.md`.
