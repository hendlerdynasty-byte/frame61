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
| **`coverage-a52-run4-60512d4.txt`** | **Abdeckungslauf 4 (massgeblich):** `COVERAGE SUMMARY index=9/14 playback=1/14 hoster=2/11`; `direct` 937/2.695.082 ms und `vidmoly` 182/1.371.240 ms laufen mit Media3 (`playing=true`); 5 Indexseiten und 9 Hoster blockiert mit HTTP-Status und Grund | Samsung A52, `60512d4`, APK `308bdf4a…`, Job `job-654784df242d4d93` PASS |
| **`playback-running-60512d4.png`** | Laufende Wiedergabe im Videofeld: 65.742 verschiedene Farben, Streuung 27,4 | Samsung A52, `60512d4` |
| **`coverage-summary-screen-60512d4.png`** | Abdeckungsuebersicht neben dem laufenden Bild: 53.848 Farben, Mittel 121,7, 96,7 % helle Pixel | Samsung A52, `60512d4` |
| `playback-screenshot-60512d4.txt` | Pixelauswertung der beiden Screenshots aus Lauf 4 | Samsung A52, `60512d4` |
| `coverage-ui-60512d4.xml` | UI-Dump aus Lauf 4 mit vollstaendigem Statustext und `exo_content_frame` | Samsung A52, `60512d4` |

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
67159e37c2acfd7567d441300e21ee891642846a09bbae9d8e67d2cb75312adb  coverage-a52-run4-60512d4.txt
809c87df6368e2138ab3ea5b269a87c004ea4bd3649331fbefd6d38370384648  playback-running-60512d4.png
42ccae54418629923c62e06afc32639c343e6122c952bcc18514e3d4945f228f  coverage-summary-screen-60512d4.png
b9e32fe887efb0e93feb68e23deae8897efb2af48bfd62b2abf9236f7a66be1d  coverage-ui-60512d4.xml
f321878f338a8592a50ccb4e84103eb904298aaa662f6b5c8943b80323e21413  playback-screenshot-60512d4.txt
```

Die TV-Trail-Datei entsteht aus `tv-be5281e/dpad-01/*.xml` (je Dump der
fokussierte Knoten mit Text und Bounds). Die Hashes der Abdeckungs- und
Wiedergabedateien stehen in `docs/ABSCHLUSS-PHASE-B.md`.
