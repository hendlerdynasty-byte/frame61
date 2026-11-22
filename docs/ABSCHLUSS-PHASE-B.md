# ABSCHLUSS-PHASE-B.md — Frame61, Phase B

Stand: 22.11.2026. Code-Stand `9353e5a` (Merge Phase B auf `main`), Release
**1.3.0 / VersionCode 4**, signierter Sideload-Release ohne Play-Store-Upload.

Ziel der Phase B: den Player-Fehler `Wiedergabe fehlgeschlagen` /
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (ExoPlayer 3003) auf dem Samsung A52
beseitigen, die Quellenabdeckung auf dem Gerät belegen, die TV-Navigation
ersetzen und einen signierten Release liefern.

**Definition „verified“ in diesem Dokument:** Ein Quelltext gilt nur dann als
verifiziert, wenn er auf dem Gerät real fortschreitet (Position läuft, Engine
meldet `playing=true`) oder als Indexseite live Treffer liefert. Ein grüner
Fixture-Test, ein grüner Build oder ein vorhandenes Sample belegen das nicht.
Blockierte Quellen werden mit Ursache, HTTP-Status und Versuchen benannt.

---

## 1. Ampel je Arbeitspaket

| # | Arbeitspaket | Status | Begründung und Beleg |
|---|---|---|---|
| WP1 | Player-Härtung: 3003, Container, Fallback, Header/Cookies, Varianten, Untertitel/Audio, Seek/Resume, Fehlerrotation | 🟡 **teilweise** | Selbsttest **6/6 PASS auf dem A52** (`docs/design/phase-b/selftest-a52-6of6.xml`): HLS, MP4, MKV, AVI und MPEG-TS laufen über Media3, der Fallback-Fall läuft nach echtem `ERROR_CODE_PARSING_MANIFEST_MALFORMED` über den Android-Systemplayer. Fehlerrotation ist im Code verdrahtet (`PlayerViewModel.recoverFromPlayback`, `app/src/main/java/de/hendler/frame61/ui/player/PlayerViewModel.kt:220`), ein **erzwungener Mid-Playback-3003-Wechsel auf einem realen Hoster** ist am Gerät jedoch **nicht** belegt. HDR-Wiedergabe ist **nicht** belegt (A52 ohne HDR-Display); die App wählt adaptiv über Media3, eine manuelle Qualitätswahl in der UI gibt es nicht. |
| WP2 | Quellenabdeckung: 14 Indexseiten, 10 Hosters | 🔴 **nicht erreicht** | Auf dem A52 verifiziert: **9/14 Indexseiten**, **1/14 Wiedergabe über den Indexpfad**, **2/11 Hoster** (`docs/design/phase-b/coverage-a52-run4-60512d4.txt`). 5 Indexseiten und 9 Hoster bleiben blockiert, jeweils mit Ursache, HTTP-Status und Versuchen dokumentiert (Abschnitt 3). |
| WP3 | Stabilität: 30 Minuten, 50 Titelwechsel, 20 Resume-Sprünge, 10 Profilwechsel | 🟢 **erfüllt** | `SESSION PASS titles=50 resumeJumps=20 profileChanges=10 errors=0 elapsedMs=1812788 maxHeap=39512624` (30,2 min, max. Heap 37,7 MiB) auf dem A52, `docs/design/phase-b/stress-a52-session-pass.xml`. |
| WP4 | TV-Navigation: untere Leiste durch echte TV-Navigation ersetzen | 🟢 **erfüllt (Emulator)** | Feste linke Seitenleiste, Fokusweg belegt: DOWN/UP wandern in der Leiste (x=106..262), RIGHT geht in den Inhalt (x≥278), LEFT zurück, CENTER wählt. `docs/design/phase-b/tv-dpad-trail-1.3.0.txt`. Echtes Fire TV: **NOT_RUN** (Owner-Policy, Gerät bleibt unangetastet). |
| WP5 | Performance ohne Regression, Debug-APK < 45 MiB | 🟢 **erfüllt** | Release-APK 1.3.0 auf dem A52: Kaltstart p95 **334 ms** (< 1200), Scroll p95 **22 ms** (< 40), Janky **3,01 %** (< 5). Debug-APK **36,7 MiB** (< 45 MiB). `docs/PERF2.md`, `docs/design/phase-b/perf-release-1.3.0.txt`. |
| WP6 | Signiertes Release 1.3.0 / VC4, `update.json` | 🟢 **erfüllt** | APK `0be6e691…` (20.016.408 B) und AAB `1044b319…` (24.693.080 B) lokal signiert, GitHub-Release `v1.3.0`, Manifest `versionCode 4` mit APK-Hash. Kein Play-Store-Upload (Auftrag). Details in Abschnitt 6. |
| WP7 | Doku und Übergabe | 🟢 **erfüllt** | `RELEASE.md`, `PERF2.md`, `PACKS.md`, `HOSTERS.md`, `INDEX.md`, `CHANGELOG.md`, `ENTSCHEIDUNGEN.md`, `STATE.md`, `ABSCHLUSS-TV.md`, dieses Dokument, Belege unter `docs/design/phase-b/`. |

Rote Ursachen im Klartext: **WP2 ist rot**, weil 5 Indexseiten extern blockiert
sind (HTTP 401/404 bzw. Platzhalter) und bei 6 weiteren die erreichbare
Indexseite keine Stream-Regel im Pack besitzt. **WP1 ist gelb**, weil der
Fehler-Recovery-Pfad im Code, aber nicht mit einem erzwungenen Gerätefehler
belegt ist und HDR mangels HDR-fähigem Testgerät offen bleibt.

---

## 2. Player: 3003 und Container

### 2.1 Geräteselbsttest (A52, 22.11.2026)

| Fall | Engine | Position / Dauer | Primärfehler | Ergebnis |
|---|---|---|---|---|
| HLS | Media3 | 2223 / 2000 ms | – | PASS |
| MP4 | Media3 | 2026 / 2000 ms | – | PASS |
| MKV | Media3 | 2047 / 2023 ms | – | PASS |
| AVI | Media3 | 2005 / 2000 ms | – | PASS |
| MP4-Fallback | Android MediaPlayer | 2014 / 2023 ms | `ERROR_CODE_PARSING_MANIFEST_MALFORMED` | PASS |
| MPEG-TS | Media3 | 2223 / 1900 ms | – | PASS |

Beleg: `docs/design/phase-b/selftest-a52-6of6.xml` (Job
`job-0ee3d0be1e3b4c42`, Gerät `be5281e`, APK `a9a412f8…`).

Der Primärplayer (Media3/ExoPlayer) ist die Hauptengine; nach einem echten
Wiedergabefehler versucht der Systemplayer dieselbe URL mit derselben
Position. HTML-, JSON- und leere Antworten werden als Resolverfehler
verworfen und **nicht** erneut als Video geöffnet — genau dort lag die alte
3003-Ursache.

### 2.2 Umsetzung (Quellstand `9353e5a`)

- `player/src/main/java/de/hendler/frame61/player/PlayerEngine.kt`: Media3 mit
  `ResolvingDataSource` (Header/Cookies auf jedem Hop), Sniffing über
  `DefaultExtractorsFactory`, MIME-Erkennung aus URL und Antwort, Ausgabe des
  tatsächlich selektierten Videoformats inklusive ColorTransfer.
- `player/src/main/java/de/hendler/frame61/player/SystemFallbackPlayer.kt`:
  Android-`MediaPlayer` mit eigener `SurfaceView`; der Fallback wird gegen
  nicht existierende Surfaces abgesichert (`be5281e`).
- `scraper/engine/.../PackHttpClient.kt`, `PackInterpreter.kt`:
  Request-Kontext (User-Agent, Referer, Cookies) und Fehlerrotation.
- `scraper/resolver/.../HosterResolver.kt`, `MirrorSelector.kt`:
  Auflösung mit Header, Auswahl des spielbaren Kandidaten.
- `core/net/.../HlsVariantParser.kt`: Master-Playlists werden bis 64 KiB
  gelesen und nach Höhe, dann Bandbreite sortiert (`VIDEO-RANGE` → `hdr`).
  Die Auswahl selbst bleibt bei Media3 (höchste vom Gerät spielbare
  Variante); die App erzwingt keine Variante und blockiert HDR nicht.

### 2.3 Nicht belegt

- Kein Gerätebeleg für einen erzwungenen Mid-Playback-3003 mit anschließender
  Rotation auf den nächsten Kandidaten (Code vorhanden, `PlayerViewModel.kt:220`).
- Kein HDR-Gerätenachweis (Testgerät ohne HDR-Panel).
- Keine manuelle Qualitäts-/Variantenwahl in der UI.

---

## 3. Quellenabdeckung auf dem Gerät (A52)

Abdeckungslauf 4, Quellstand `60512d4`, APK `308bdf4a…`, Job
`job-654784df242d4d93` PASS:
`COVERAGE SUMMARY index=9/14 playback=1/14 hoster=2/11`.
Volltext: `docs/design/phase-b/coverage-a52-run4-60512d4.txt`.

### 3.1 Indexseiten 9/14

| Index | Ergebnis | Detail |
|---|---|---|
| bs-to | ✅ Index | HTTP 200, 10.733 Treffer, erster „Boruto \| Naruto Next Generations“ |
| filmpalast | ✅ Index | HTTP 200, 1 Treffer „Inception“ |
| filmstarts | ✅ Index | HTTP 200, 3 Treffer „Inception“ |
| moflix | ✅ Index | HTTP 200, 5 Treffer „Inception“ |
| s-to | ✅ Index | HTTP 200, 1 Treffer „Black Doves“ |
| serienstream | ✅ Index | HTTP 200, 1 Treffer „Black Doves“ |
| streamcloud | ✅ Index | HTTP 200, 1 Treffer „Inception“ |
| filmo | ✅ Index | HTTP 200, 1 Treffer „Inception“ |
| cine | ✅ Index | HTTP 200, 1 Treffer „Inception“ |

### 3.2 Wiedergabe über den Indexpfad 1/14

| Quelle | Ergebnis | Detail |
|---|---|---|
| moflix | ✅ **VERIFIED_PLAYBACK** | lief 62 / 8.887.921 ms, `engine=Media3`, `playing=true`, kein Primärfehler; URL `https://s1-s1001604.97bf1.com/hls/…/master.m3u8?token=…` |

Die übrigen acht verifizierten Indexseiten erreichen den Player nicht:
bs-to, filmstarts, s-to, serienstream, filmo und cine haben im Pack **keine
Stream-Regel**; filmpalast und streamcloud liefern zwar einen Stream, aber
`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (3003) auf
`https://vidsonic.net/e/8zwnbxa44ivc` bzw.
`https://devideosrc.co/movie/tt1375666`.

### 3.3 Hoster 2/11

| Hoster | Ergebnis | Detail |
|---|---|---|
| direct | ✅ **VERIFIED_PLAYBACK** | lief 937 / 2.695.082 ms, `engine=Media3`, `playing=true`, kein Fehler; `https://ctv-videos.daserste.de/de/2026/05/07/409/409_16651_sendeton_960x540-50p-1600kbit.mp4` |
| vidmoly | ✅ **VERIFIED_PLAYBACK** | lief 182 / 1.371.240 ms, `engine=Media3`, `playing=true`, kein Fehler; `https://box-1583-q.vmeas.cloud/hls2/04/01431/…/master.m3u8?…` |

**Kein verifizierter Quelltext liefert 3003.**

### 3.4 Blockierte Quellen (Ursache, HTTP-Status, Versuche)

Versuche = zwei vollständige Abdeckungsläufe am Gerät (Lauf 2 `627ef8b`,
Lauf 4 `60512d4`) plus die dokumentierten Vorversuche in `docs/INDEX.md`
und `docs/HOSTERS.md`.

**Index (5 blockiert)**

| Index | HTTP-Status | Ursache |
|---|---|---|
| aniworld | 200 | Antwort leer (0 Bytes, `text/html`), 0 Treffer |
| kinokiste | **401** | JWT-/Bot-Gate auf `kinokiste.cloud/suche/inception` |
| kinox | **404** | 6 Rotationsdomains, `/suche` liefert 404 |
| megakino | 200 | Platzhalter-Seite (2.927 B), 0 Treffer |
| movie2k | — | kein Pack: `.to`/`.ch` sind Platzhalter, kein Server |

**Hoster (9 blockiert)**

| Hoster | HTTP-Status | Ursache |
|---|---|---|
| voe | — | Captcha erforderlich (WebView-Lösung nötig) |
| doodstream | — | Captcha erforderlich (WebView-Lösung nötig) |
| filemoon | **405** | `filemoon.to/api/videos/89crzins1ufc/playback` lehnt GET ab |
| streamtape | — | keine öffentliche Dateiliste, API nur mit Konto |
| vidoza | — | Landingpage ohne öffentliche Dateiliste |
| mixdrop | — | TLS-Verbindung fehlgeschlagen (18.11.2026) |
| luluvdo | — | `luluvdo.com`/`.net` nicht auflösbar (18.11.2026) |
| loadx | — | `loadx.ws` Timeout (18.11.2026) |
| streamhide | — | nur JS-Redirect ohne Dateilink |

### 3.5 Messfehler, die behoben wurden

Der erste Abdeckungslauf meldete für `direct` und `vidmoly` „bereit und
playing, aber Position 0“. Ursache war nicht die Quelle, sondern die
Messung: `Media3Frame61Player.publish()` wird ereignisgesteuert aktualisiert,
und die Diagnose-Activity hatte keinen Ticker wie die Produktions-App. Nach
dem Fix `60512d4` (`engine.publish()` in beiden Poll-Schleifen) zählen beide
Quellen als verifizierte Wiedergabe. Belege: Lauf 2 (`hoster=0/11`) gegen
Lauf 4 (`hoster=2/11`).

---

## 4. Stabilität

| Kennzahl | Ziel | gemessen | Ergebnis |
|---|---|---|---|
| Laufzeit | ≥ 30 min | 1.812.788 ms (30,2 min) | ✅ |
| Titelwechsel | 50 | 50 | ✅ |
| Resume-Sprünge | 20 | 20 | ✅ |
| Profilwechsel | 10 | 10 | ✅ |
| Fehler | 0 | 0 | ✅ |
| Heap | — | max. 39.512.624 B (37,7 MiB) | ✅ kein Wachstum im Messfenster |

Beleg: `docs/design/phase-b/stress-a52-session-pass.xml` (A52, Job
`job-0ee3d0be1e3b4c42`).

---

## 5. TV-Navigation

| Schritt | Fokussierter Knoten | Position |
|---|---|---|
| DOWN | „Serien“ | `[106,352][262,456]` — Leiste, zweiter Eintrag |
| UP | „Filme“ | `[106,208][262,312]` — Leiste, erster Eintrag |
| RIGHT | Inhaltsbereich | `[278,15][1834,481]` — x ≥ 278 |
| LEFT | „Filme“ | `[106,208][262,312]` — zurück in der Leiste |
| CENTER | „Start“ | `[106,64][262,168]` — Auswahl |

Es gibt keine untere Navigationsleiste mehr. Start des Release-APK:
`Status: ok`, `LaunchState: COLD`, `TotalTime 1114 ms`, 0 FATAL EXCEPTION,
0 ANR. Belege: `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` (Emulator
`frame61-androidtv-31`, `emulator-5554`, `uimode 0x24`, leanback vorhanden),
`docs/design/phase-b/tv-release-startup-1.3.0.png`.

---

## 6. Release und Artefakte

| Artefakt | SHA-256 | Größe |
|---|---|---|
| `frame61-1.3.0.apk` | `0be6e691c665f46a6e675b633dd62511d680ae88e2cc22160e8834fdb2bb0541` | 20.016.408 B |
| `frame61-1.3.0.aab` | `1044b3196d07f59c2b25831ea09269c82a0c9862a9ee7a6279b3e0d06c2995c6` | 24.693.080 B |
| Debug-APK (Gate) | siehe Abschnitt 7 | 38.530.293 B (36,7 MiB) |

- Quellstand `9353e5a`, VersionCode 4, versionName 1.3.0, Package
  `de.hendler.frame61`.
- Signatur `CN=Frame61, O=Hendler Dynasty, C=DE`, Zertifikat-SHA-256
  `1201f46d50d167ab10712dadbe64751af89bf9d61f1207672d1b0ce06dec4c52`
  (unverändert gegenüber 1.2.0, Updates ohne Datenverlust). `zipalign -c`,
  `apksigner verify` und `jarsigner -verify` bestätigt. Kein Signing-Secret
  verlässt den Mac; der Keystore liegt außerhalb des Repos.
- GitHub-Release: <https://github.com/hendlerdynasty-byte/frame61/releases/tag/v1.3.0>
  (Asset-Hash über HTTP nachgeladen und geprüft).
- Update-Manifest: <https://hendlerdynasty-byte.github.io/frame61/update.json>
  (`versionCode` 4, `versionName` 1.3.0, APK-URL und SHA-256 des APK).
- Geräte: Release-APK auf dem A52 installiert (Job `job-f147cb8b1d6848d4`
  PASS) und auf dem Android-TV-Emulator `frame61-androidtv-31`
  (`versionCode=4`, `versionName=1.3.0`).
- **Kein Play-Store-Upload, kein Rollout, keine Store-Freigabe.**

---

## 7. Tests, Lint, APK-Größe

| Kennzahl | Wert | Grenze | Ergebnis |
|---|---|---|---|
| Unit-Tests | **121/121**, 0 Failures, 0 Errors, 0 Skipped (25 Testklassen, 10 Module) | alle grün | ✅ |
| Lint (Debug) | **0 Errors**, 106 Warnings | 0 Errors | ✅ |
| Debug-APK | 36,7 MiB (38.530.293 B) | < 45 MiB | ✅ |
| Validator | 0 Fehler (Katalog, Meta-Snapshots, Index, Source- und Hoster-Fixtures, Packs) | 0 | ✅ |

Gate: Actions-Lauf `android-gate`, Job `gate`, Quellstand siehe
`docs/RELEASE.md`; Evidenz-Artefakt `frame61-gate-evidence-<sha>` enthält die
Test-XML und den Lint-Report. Fehlgeschlagener Vorlauf `44989416861` (Merge
`9353e5a`) scheiterte ausschließlich an `:packsReport` („docs/PACKS.md ist
veraltet"), weil der Merge die `last_check`-Zeitstempel verändert hatte;
`docs/PACKS.md` wurde daraufhin aus dem Live-Stand neu erzeugt.

---

## 8. Performance (Release-APK 1.3.0 auf dem A52)

| Kennzahl | Release 1.3.0 | Block 11 (Perf) | Grenze | Ergebnis |
|---|---|---|---|---|
| Kaltstart p95 | **334 ms** | 323 ms | < 1200 ms | PASS, keine Regression |
| Scroll p50 / p90 / p95 / p99 | **15 / 20 / 22 / 28 ms** | 16 / 19 / 24 / 28 ms | p95 < 40 ms | PASS, keine Regression |
| Janky | **19 von 631 = 3,01 %** | 3,71 % | < 5 % | PASS, keine Regression |

Belege: `docs/PERF2.md`, `docs/design/phase-b/perf-release-1.3.0.txt`
(Job `job-f147cb8b1d6848d4`). Einzelmessung auf einem Gerät; das Baseline
Profile liegt nicht im Repository (Block-11-Vergleich daher nicht
apples-to-apples, die Grenzwerte sind es).

---

## 9. Nicht behauptet / Grenzen

- **Fire TV: NOT_RUN.** Das laufende echte Fire TV wurde nicht angefasst
  (keine ADB-Befehle, keine Screenshots, keine Tests). TV-Nachweis ist der
  Android-TV-Emulator.
- **WP2 ist nicht erreicht:** 9/14 Index, 1/14 Index-Wiedergabe, 2/11 Hoster.
- **HDR** nicht belegt; **manuelle Qualitätswahl** nicht vorhanden.
- **Erzwungener Mid-Playback-3003 mit Rotation** nicht am Gerät belegt.
- Ein **grüner Build ist kein Geräte-Nachweis** und ein Sideload-Release ist
  **keine Store-Freigabe**. `verified` ist ein technischer Gerätebefund, kein
  Rechts- oder Lizenznachweis.
- Die Messungen sind Einzelmessungen auf einem Gerät, keine Studie über
  mehrere Geräte.

---

## 10. Reproduktion

```sh
# Abdeckungslauf auf dem A52 (Variante acceptance/perf/ci)
# Gerät über die zentrale Device-Bridge anmelden, Diagnostics-Activity starten,
# Statustext aus dem UI-Dump ziehen.

# Performance des signierten Release-APKs
~/hendler-dynasty/evidence/frame61/measure_release_perf.sh <zielordner>

# TV-Abnahme des Release-APKs
~/hendler-dynasty/evidence/frame61/tv_frame61_release_check.sh <apk> <zielordner>

# Gate (Tests, Lint, APK-Größe)
gh workflow run android-gate.yml --ref main
```

Belege im Repo: `docs/design/phase-b/` (Zuordnung und Hashes in
`docs/design/phase-b/README.md`). Rohdaten mit Gerätekennungen und
Systemlogs ausserhalb des Repos unter
`~/hendler-dynasty/evidence/frame61/`.
