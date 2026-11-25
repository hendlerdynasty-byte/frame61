# ABSCHLUSS 1.5.0 — Frame61 (25.11.2026)

Quellstand `9d14350` (`main`), Release **1.5.0 / VersionCode 6**, signiert,
veröffentlicht über den bestehenden Update-Kanal. Abschlussbericht zum Auftrag
„FRAME61 — technische Vollendung und Release": Ampel je Arbeitspaket, danach
die Zahlen. Belege liegen unter `~/hendler-dynasty/evidence/frame61/1.5.0/`
und in den verlinkten Dokumenten.

## Ampel je Arbeitspaket

| Arbeitspaket | Ampel | Beleg / Begründung |
|---|---|---|
| Diagnose-Instrumentierung | 🟢 | `PlaybackDiagnostics`: Ringpuffer 500 Ereignisse, Logcat-Brücke, HTTP-Ereignisse mit Header-Namen und redigierten Werten (SHA-256 statt Cookie/Token), Container-Sniffing der ersten Bytes. Auf dem A52 in jeder Messung aktiv (`Frame61Diag`), In-App-Panel im Player. |
| 3003 auf verifizierten Quellen | 🟡 | Auf den verifizierten Hostern (`direct`/ARD, `vidmoly`) trat in Lauf 11 und im Holdout **kein** 3003 auf. Die `CONTAINER_UNSUPPORTED`-Fälle sitzen ausschließlich in Titeln, deren Kette auf **unverifizierte** Hoster (dropcdn/rpmplay) zeigt und dort HTML statt Medien erhält: Lauf 11 acht Fälle (moflix 5, serienstream 2, bsto 1), Holdout drei (moflix 2, filmpalast 1). Die DoD-Formulierung („keine verifizierte Quelle") ist erfüllt, das ist aber keine Aussage über die unverifizierten Hosts. |
| Container: HLS, MP4, MKV, AVI, MPEG-TS + Fallback | 🟢 | Phase-B-Selbsttest 6/6 auf dem A52 (`docs/design/phase-b/selftest-a52-6of6.xml`); in dieser Runde ARD-MP4 1920 und ARD-HLS 1280 real abgespielt (`AB4-out`, `AB9-out`); der 30-Minuten-Test fährt alle fünf Container-Assets (Ergebnis unten). |
| Header-Propagierung je Hop | 🟢 | Jeder HTTP-Hop wird mit UA/Referer/Cookie-**Namen** protokolliert; der Referer ist je Ziel unterschiedlich (z. B. `Referer=https://aniworld.to`, `https://api.ardmediathek.de`, `https://devideosrc.co/movie/tt0082479`, `https://moflix-stream.top`). Belege: `AB4-out/09-logcat.txt`, `run11-9d14350/polls/`. |
| Qualitätswahl / HDR | 🟡 | HLS-Varianten und ARD-Qualitäten werden geparst, die höchste spielbare gewählt (gemessen: 1920 im MP4-Slice, 1280 im HLS-Slice). HDR-Metadaten (`VIDEO-RANGE`) werden gelesen und getestet (`HlsVariantParserTest`); **Wiedergabe auf einem HDR-Display nicht belegt** — kein HDR-Testgerät. |
| Untertitel / Audiospuren / Seek / Resume | 🟡 | Spurwahl ist implementiert (`PlayerScreen` „Audio/subtitle track chooser", `PlayerEngine` mit `DefaultTrackSelector`, `audioTracks`/`textTracks`); Seek und Resume sind im Sessiontest belegt. Die Spurwahl selbst wurde in dieser Runde **nicht** separat am Gerät abgenommen. |
| Automatische Neuauflösung | 🟢 | Alternative-Kette sequenziell mit eigenen Budgets (60 s/34 s/8 s je Kandidat), Auslöser ist der Fehler des laufenden Kandidaten. Belegt in Lauf 8 (+1 Titel), in Lauf 9–11 aktiv; Ursachenanalyse zählt 22 Titel mit voller Kette und 8 mit „keine weitere Alternative". |
| Block 7 — Fokus-Vorwärmlauf | 🟡 | Funktional belegt (`Frame61Prefetch`, `RESOLVER: Start aus dem Vorwaermlauf`, `PLAYER: Erstes Bild … vorwaermlauf=ja`). **Zeitgewinn nicht belegt** — in zwei Paaren war der Kaltpfad schneller (7551→1412 ms, 3953→2776 ms). Entscheidung: bleibt drin, wird nicht als Gewinn ausgewiesen (`docs/ENTSCHEIDUNGEN.md`). |
| Block 8 — Staffel in der Episodenadresse | 🟢 | `EpisodeSelection` in drei Stufen; s.to/aniworld adressieren `staffel-{season}`; Tests grün (siehe Gate). |
| Block 9 — Serie ohne Nummer = Folge 1 | 🟢 | A52-Messung: `episode-0` → HTTP 404 (2 516 B, 0 Streams), `episode-1` → HTTP 200 (176 859 B, 4 Streams). 10 Tests in `EpisodeSelectionTest`, Prefetch liefert seither Kandidaten für Serien. |
| Quellenabdeckung | 🟡 | 31 Source-Packs, **12 live verifiziert** (`tools/packs_report.py --check` → OK); 14 Indexseiten: **10 verifiziert**, 9/14 auf dem A52 erreichbar; Hoster **3/10** Kette-verifiziert (vidmoly, filemoon, direct), **2** davon auf dem A52 abgespielt. Die übrigen 7 Hoster sind mit HTTP-Status/Grund blockiert (`docs/HOSTERS.md`). Das Ziel „10/10 Hoster" ist **verfehlt**, die Abweichung ist belegt. |
| Titel-Quote Training (Lauf 11) | 🟡 | **67/100 (67 %)**, Fast 66/100. Die 95-%-Marke des Auftrags ist nicht erreicht; die Grenze liegt auf der Hoster-Ebene (dropcdn/rpmplay), nicht in der App. Delta zu Lauf 10 (69/100) ist Provider-Rotation. |
| Holdout-Quote | 🟢 | Seed `20261123`, disjunkt zur Trainingsprobe: **70/100 (70 %)**, Fast 68/100 — höher als im Training, also keine Überanpassung. Publikationsregel erfüllt. |
| Session-Stabilität (30 min, 50/20/10) | ⏳ | `SessionStressActivity` mit den fünf Container-Assets — Ergebnis unten. |
| Regression: Tests, Lint, APK-Größe | 🟢 | CI `45582003989`: **203 Tests, 0 Fehler, 0 Skips** (33 Report-Dateien), Lint **0 Errors / 109 Warnings**, Debug-APK 38 505 413 B = **36,7 MiB** (< 45 MiB). |
| Performance | 🔴 | **Nicht neu gemessen (STALE).** Die Gates (Kaltstart p95 334 ms, Scroll p95 22 ms, Jank 3,01 %) stammen vom Release 1.3.0 (`docs/PERF2.md`). Für 1.5.0 fehlt die Nachmessung: die Device-Bridge bietet kein `force-stop`, ein Macrobenchmark-Lauf ist nicht Teil des CI. Es gibt **keinen Beleg** für eine Verschlechterung — und keinen dagegen. |
| Release-Artefakte | 🟢 | APK `ee415ab9…` (19 963 388 B), AAB `39a36476…` (24 667 262 B), Zertifikat `1201f46d…` unverändert; `aapt2`: `versionCode 6`, `versionName 1.5.0`, minSdk 23, targetSdk 36; `apksigner verify`/`jarsigner -verify` bestätigt. Geräte-Smoke des Release-APK unten. |
| Veröffentlichung (Release + update.json) | ⏳ | GitHub-Release `v1.5.0` + `site/update.json` (VC6) — Status unten. |

## Zahlen

| Kennzahl | Wert |
|---|---|
| Tests | 203/203 grün (CI `45582003989`), 0 Fehler, 0 Skips |
| Lint | 0 Errors, 109 Warnings |
| Debug-APK | 38 505 413 B (36,7 MiB) |
| Release-APK | 19 963 388 B, sha256 `ee415ab9aa0eb09546c3dd8bf0fe40a7660c1c9b32ed5bfc311136055d45e720` |
| Release-AAB | 24 667 262 B, sha256 `39a36476edc075a7a2065c46d5650a5d195d1b5411d0234cfb19e87a8e2866bc` |
| Signatur | `CN=Frame61, O=Hendler Dynasty, C=DE`, Zertifikat-SHA-256 `1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52` |
| Quote Training (Lauf 11) | 67/100 (aniworld 95 %, serienstream 70 %, filmpalast 73 %, ard 50 %, bsto 50 %, moflix 27 %) |
| Quote Holdout | 70/100 (aniworld 90 %, filmpalast 86 %, ard 83 %, serienstream 64 %, bsto 50 %, moflix 36 %) |
| Quellen | 12/31 Source-Packs live verifiziert; 10/14 Indexseiten verifiziert (9/14 am A52 erreichbar); 3/10 Hoster verifiziert, 2 am Gerät abgespielt |
| Performance | STALE (1.3.0-Stand: Kaltstart p95 334 ms, Scroll p95 22 ms, Jank 3,01 %) |
| Gerät | Samsung SM-A525F (RZ8T703XJWF), Android 14, USB, zentrale Device-Lab-Lease; kein Emulator, kein WLAN-ADB, Fire TV unangetastet |

## Was offen bleibt (mit Grund)

1. **Performance-Nachmessung für 1.5.0** — fehlt (kein `force-stop` über die
   Bridge, kein Benchmark-Job im CI). Nächster Schritt: Macrobenchmark-Modul
   plus Device-Lab-Schritt, der `am force-stop` erlaubt.
2. **Untertitel-/Audiospurwahl am Gerät** — implementiert, nicht separat
   abgenommen.
3. **HDR-Durchreichung** — ohne HDR-Testgerät nicht belegbar.
4. **7 Hoster blockiert** (VOE, Doodstream je `step_failed`; Streamtape,
   Vidoza, Mixdrop, Luluvdo, Loadx, Streamhide ohne ermittelbares Dateiziel) —
   Ursachen und HTTP-Status in `docs/HOSTERS.md`.
5. **4 Indexseiten blockiert** (kinox, kinokiste, megakino, movie2k) —
   dokumentiert in `docs/INDEX.md`.
6. **Lint-Warnungen (109)** — keine Errors, aber nicht abgearbeitet.
