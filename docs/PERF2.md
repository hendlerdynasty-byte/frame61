# PERF2.md — Performance Block 11

Stand: 18.11.2026. Messung auf dem Samsung SM-A525F (A52), Android 14
(SDK 34), USB, 80 % Akku. Die Zahlen gelten für die genannten Builds, nicht
für einen Store-Release.

## Builds

| Build | Quellstand | APK | Groesse | Zweck |
|---|---|---|---|---|
| Debug | `0ca67d6` | `2743e5790acb79ae874b42b0d2ca9774f4f8fa8376f4efe53e209f2a010ff6ab` | 30,1 MiB | Vorher/Nachher, Tests |
| Perf (R8) | `0ca67d6` | `a52abc656a228ba6c693fccf8f4a87861caea35d929dd29cef682b2b3f79964d` | 13 MiB | Performance-Ziele |

Der Perf-Build ist `release`-konfiguriert (R8, Shrink, Baseline Profile), aber
mit Debug-Key und `applicationIdSuffix ".perf"` gebaut. Er ist **keine**
Release-/Store-Freigabe.

## Baseline Profile

- Aufnahme: installierter Debug-Build `657cf2cb…`, echte Bedienung
  (Start, Serien, Detail, Wiedergabe), danach
  `cmd package dump-profiles de.hendler.frame61`.
- Konvertierung: `tools/perf/profile_to_hrf.py` (ART-Dump → Baseline-HRF),
  25.774 Methoden.
- Im Perf-APK enthalten: `assets/dexopt/baseline.prof` (14.266 Bytes) und
  `baseline.profm` (1.382 Bytes), `profileinstaller` 1.4.1.
- AGP-Flag `android.experimental.art-profile-r8-rewriting=true` mappt die
  Profilnamen auf die R8-Ausgabe.

## Kaltstart

Methode je Lauf:

```
adb shell am force-stop <paket>
sleep 1
adb shell am start -W -n <paket>/de.hendler.frame61.ui.MainActivity
```

Zehn Laeufe, `TotalTime`:

| Build | Werte (ms) | p95 |
|---|---|---|
| Debug | 1574, 1574, 1584, 1584, 1594, 1597, 1610, 1618, 1634, 1702 | **1702 ms** |
| Perf (R8) | 294, 297, 300, 301, 303, 312, 313, 316, 320, 323 | **323 ms** |

Ziel: p95 < 1200 ms → **PASS** (Perf). Debug liegt darueber und ist nicht der
Zielbuild.

## Scrollen

Methode: Serien-Seite, `dumpsys gfxinfo <paket> reset`, zehn vertikale Swipes
(300 ms), danach `dumpsys gfxinfo <paket>`.

| Build | Frames | Janky | p50 | p90 | p95 | p99 |
|---|---|---|---|---|---|---|
| Debug | 555 | 29 (5,23 %) | 19 ms | 28 ms | 32 ms | 53 ms |
| Perf (R8) | 620 | 23 (3,71 %) | 16 ms | 19 ms | 24 ms | 28 ms |

Ziele: Scroll-p95 < 40 ms, Janky < 5 % → **PASS** (Perf: 24 ms, 3,71 %).

## Bild-Cache und Paging

- Coil: Memory-Cache 25 % des App-Heaps, Disk-Cache 512 MB unter
  `cacheDir/coil`; Poster sind lokal (kein Netz noetig).
- Katalog-Grid: 120 Titel pro Seite, Nachladen ab 24 Eintraegen vor dem Ende
  (`BrowseViewModel.loadMore`). Der Filter setzt die Seite zurueck.
- Home laedt den Katalog und baut die Rails ausserhalb des Main-Threads
  (`Dispatchers.IO`).

## Einschraenkungen

- Die Perf-APK ist debug-signiert und hat eine eigene ID; sie belegt R8,
  Baseline Profile und die Messziele, nicht die Release-Signatur.
- Der Baseline-Profile-Dump stammt vom Debug-Build vor dem IO-Umbau; die
  Startup-Methoden sind unveraendert, der Perf-Build wurde damit gemessen.
- `am start -W` misst den Activity-Start, nicht den vollstaendigen
  Prozess-Kaltstart inklusive aller ContentProvider; die gleiche Methode
  wurde fuer Debug und Perf verwendet.
- Die Zahlen sind Einzelmessungen auf einem Geraet, keine p95-Studie ueber
  viele Geraete.
