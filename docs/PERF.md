# PERF.md — Messungen (Block 8)

Gerät: Samsung SM-A525F (A52), Android 14 (SDK 34), USB, 80 % Akku.
Build: Debug-APK `995528f86f49d760917d7f6f882b91d32d43fd04a4d50ca9b099ed664b2b0e40`
(Quellstand `ed30818`, Block 6 + Suchfix). Kein Release-Build, kein
Benchmark-Profil.

## Kaltstart

Methode: `am force-stop`, 1 s Pause, `am start -W`, `TotalTime` dreimal.

| Lauf | TotalTime |
|---|---|
| 1 | 2051 ms |
| 2 | 2018 ms |
| 3 | 2200 ms |
| **Median** | **2051 ms** |

Ziel: < 2500 ms → **PASS** (Debug-Build, ohne Baseline Profile).

## Speicher

Methode: App im Vordergrund, `dumpsys meminfo de.hendler.frame61`.

| Kennzahl | Wert |
|---|---|
| TOTAL PSS | 157.633 KB (~154 MiB) |
| TOTAL RSS | 227.832 KB (~222 MiB) |
| Java Heap | 34.736 KB |
| Native Heap | 11.912 KB |
| Graphics | 55.632 KB |

Ziel: RSS < 350 MB → **PASS** (PSS 154 MiB; RSS 222 MiB).

## Scrollen

Methode: `dumpsys gfxinfo … reset`, fünf vertikale Swipes auf dem Home-Screen
(300 ms), danach `dumpsys gfxinfo`.

| Kennzahl | Wert |
|---|---|
| Frames | 96 |
| Janky frames | 19 (19,79 %) |
| 50th percentile | 22 ms |
| 90th percentile | 65 ms |
| 95th percentile | 150 ms |
| 99th percentile | 200 ms |

Ziel „Row < 100 ms": der 90th-Percentile liegt mit 65 ms darunter; der
95th-Percentile (150 ms) und ~20 % Janky-Frames liegen darüber. **Kein PASS für
eine flüssige 60-fps-Darstellung**, nur für die 90-%-Zeile. Ursachen sind nicht
untersucht (Debug-Build, keine Baseline Profile, Poster-Decoding).

## Grenzen

- Ein Gerät, ein Lauf je Messung; keine Makrobenchmark-Suite.
- Debug-Build ist langsamer als Release (keine Minifizierung/Profile).
- Die Werte sind eine Baseline, keine Release-Garantie.
- Keine Messung auf Fire TV (Gerät bleibt unangetastet, Owner-Policy).
