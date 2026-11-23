# QUOTA.md — Titel-Erfolgsquote (Messharness und Baseline)

Stand: 23.11.2026. Methode und Zahlen für Version 1.5.0.

## Methode (1.5.0)

- **Zwei Stichproben, garantiert disjunkt:**
  - **Training** (`PlaybackQuotaActivity`, Seed `20261122`, 100 Titel),
  - **Holdout** (`PlaybackQuotaHoldoutActivity`, Seed `20261123`, 100 Titel aus
    dem Rest des Katalogs). Die Holdout-Stichprobe wird erst nach der
    Trainings-Stichprobe gefahren und nie zum Nachsteuern benutzt.
- **Katalog:** invertiert und nach Zielverteilung geerntet — 997 Titel aus den
  Quellen selbst: 35 % Serien von s.to/serienstream, 20 % Filme von
  filmpalast/moflix, 20 % Anime von aniworld, 15 % ARD als Sockel, 10 % bs.to
  (siehe `docs/ENTSCHEIDUNGEN.md`). Jeder Titel trägt `group` und `sourceKey`.
- **Quote je Quellengruppe:** Der Lauf weist die Quote **pro Gruppe** aus
  (`Gruppe | Titel | spielbar | Quote | häufigste Fehlerklasse`). Der
  Mischwert allein verdeckt, welche Gruppe trägt und welche nur den leichten
  Pfad zählt.
- **Kette:** derselbe Produktionsweg wie im Player —
  `AutoPlaybackResolver.resolveFast` → Kandidaten → bis zu drei mit
  `Media3Frame61Player`.
- **Zeiten getrennt:** `resolveMs` (Auflösung), `playMs` (Kandidat bis erstes
  Bild), `firstFrameMs` (Summe ab Tippen).
- **Fehlertaxonomie:** `NO_CANDIDATE`, `EMBED_UNRESOLVED`,
  `CONTAINER_UNSUPPORTED`, `GEO_BLOCKED`, `RATE_LIMITED`, `TIMEOUT` — die
  Klasse wird aus den Diagnose-Ereignissen des Resolvers abgeleitet, nicht
  geraten.
- **Auflösungsbudget:** 60 s je Titel. bs.to braucht bis zu ~28 s (Challenge +
  Klick in der versteckten WebView); mit 30 s wäre der Titel abgeschnitten.
- **Protokoll:** je Titel eine Zeile (idx, id, group, type, RESULT, class,
  resolveMs, playMs, candidates, host, intercept) im Logcat, im Statusbereich
  und in einer Datei; am Ende eine Zeile je Gruppe und die letzten
  Interception-Versuche mit Request-Kette.

### Belegregel

Nach **jedem** Block wird dieselbe Stichprobe mit demselben Seed gefahren und
das Delta festgehalten. Ein Block ohne Messung gilt nicht als abgeschlossen.

## Lauf 1 — Build `448109a`, 23.11.2026, A52 (nach Katalog-Neubau + bs.to-Regel)

Stichprobe: Training, Seed `20261122`, 100 Titel aus 997 Katalogeinträgen.
Dauer ~15 min. APK `84ebb997…` (signiert, `de.hendler.frame61.acceptance`).

| Kennzahl | Wert | Ziel 1.5.0 |
|---|---|---|
| Erfolgsquote (Mischwert) | **45 / 100 = 45 %** | ≥ 95 % |
| Auflösung p50 / p95 | 4.734 / **10.119** ms | p95 < 2.500 ms |
| Startzeit p50 / p95 | 3.367 / **4.172** ms | p95 < 3.000 ms |
| Fehlerklassen | 41 × `NO_CANDIDATE`, 14 × `CONTAINER_UNSUPPORTED` | 0 sichtbar |

Quote je Quellengruppe:

| Gruppe | Titel | spielbar | Quote | häufigste Fehlerklasse |
|---|---|---|---|---|
| serienstream | 43 | 24 | 55 % | `NO_CANDIDATE`: 16 |
| aniworld | 19 | 8 | 42 % | `NO_CANDIDATE`: 6 |
| ard | 10 | 5 | 50 % | `CONTAINER_UNSUPPORTED`: 3 |
| filmpalast | 11 | 3 | 27 % | `NO_CANDIDATE`: 6 |
| moflix | 11 | 3 | 27 % | `NO_CANDIDATE`: 7 |
| bsto | 6 | 2 | 33 % | `NO_CANDIDATE`: 4 |

**Befund — die Quote misst derzeit nur den Sockel.** Alle 45 Erfolge gingen
über ARD/ZDF/arte/kika/funk (`nrodlzdf-a.akamaihd.net`, `br-vod.ard-mcdn.de`,
`zdfvod.akamaized.net` …). **Kein einziger Index** (s.to/serienstream, bs.to,
aniworld, filmpalast, moflix) lieferte einen spielbaren Stream. Die Gruppen-
Quote ist damit keine Aussage über die Index-Quellen, sondern über die
Breite des ARD-Katalogs.

Ursachen aus dem Logcat desselben Laufs:

1. **34 × `hoster timeout`** (moflix 15, filmpalast 6, s-to 5, streamcloud 3,
   aniworld 3): Das Quellbudget (10 s) schnitt die Hoster-Kette ab, **bevor**
   die WebView-Interception (bis 20 s) fertig war. Das Budget umfasste nur
   den Quellenschritt, nicht die Hoster-Auflösung.
2. **bs.to:** 3 von 4 Titeln je 22–25 s ohne Kandidaten
   (`Frame61BsTo: keine Medien-URL für Surfside-Girls S1E1`). Die Kette
   hält an `ajax/embed.php` — die Seite verlangt ein reCAPTCHA-v2-Token
   (`6Ldd07og…`); ohne Token antwortet der Server `{"success":false}`,
   mit leerem Ticket `HTTP 400`.
3. **13 × `CONTAINER_UNSUPPORTED`** auf `devideosrc.co` — HTML-Ziel statt
   Medien-URL.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run1-448109a/`
(Rohzeilen `polls/final-lines.txt`, Logcat `polls/p*/01-logcat.txt`,
Endbild `A5-final/02.png`).

### Delta zu Lauf 0 (Baseline 1.4.0, `243971d`)

| | Lauf 0 | Lauf 1 | Delta |
|---|---|---|---|
| Quote | 6 % | 45 % | +39 pp |
| Startzeit p95 | 5.615 ms | 4.172 ms | −1.443 ms |
| Fehler 3003 | 38 | 14 | −24 |

Die Stichprobe ist ab hier **neu gezogen** (Katalog-Neubau mit 997 Titeln und
Zielverteilung). Ab Lauf 1 bleibt Sample + Seed unverändert, damit die
Folgeblöcke vergleichbar sind.

## Lauf 2 — Build `be45079`, 23.11.2026, A52 (Budget deckt Hoster + Interception)

Stichprobe: **identisch** zu Lauf 1 (Training, Seed `20261122`, 100 Titel).
Dauer 18:50–19:41 (~51 min). APK `77f679b5…` (signiert, `de.hendler.frame61.acceptance`).
Geänderte Basis gegenüber Lauf 1: Quellbudget 32 s (vorher 10 s), Hoster-Budget
26 s, **echte** Interception-Zeit und Request-Kette im Protokoll.

| Kennzahl | Wert | Lauf 1 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **53 / 100 = 53 %** | 45 % | +8 pp |
| Auflösung p50 / p95 | 4.585 / **27.186 ms** | 4.734 / 10.119 ms | p95 +17.067 ms |
| Startzeit p50 / p95 | 2.734 / **28.458 ms** | 3.367 / 4.172 ms | p95 +24.286 ms |
| Fehlerklassen | 35 × `CONTAINER_UNSUPPORTED`, 12 × `NO_CANDIDATE` | 14 / 41 | **+21 / −29** |

Quote je Quellengruppe:

| Gruppe | Titel | spielbar | Quote | Lauf 1 | häufigste Fehlerklasse |
|---|---|---|---|---|---|
| aniworld | 19 | 15 | **78 %** | 42 % | `CONTAINER_UNSUPPORTED`: 4 |
| serienstream | 43 | 25 | **58 %** | 55 % | `CONTAINER_UNSUPPORTED`: 12 |
| ard | 10 | 5 | 50 % | 50 % | `NO_CANDIDATE`: 3 |
| bsto | 6 | 3 | **50 %** | 33 % | `CONTAINER_UNSUPPORTED`: 2 |
| filmpalast | 11 | 3 | 27 % | 27 % | `CONTAINER_UNSUPPORTED`: 6 |
| moflix | 11 | 2 | **18 %** | 27 % | `CONTAINER_UNSUPPORTED`: 9 |

### Befund 1 — die Fehlerklasse ist gewandert, nicht verschwunden

Das Budget hat genau das bewirkt, wofür es gedacht war: Die 34 „hoster timeout"
aus Lauf 1 sind weg, `NO_CANDIDATE` fällt von 41 auf 12. **Aber** die 14
`CONTAINER_UNSUPPORTED` werden zu 35: Titel, die vorher gar keinen Kandidaten
lieferten, liefern jetzt einen — nur eben eine **HTML-Zielseite**, die der
Player nicht spielen kann. Zielhosts der Fehlschläge: `devideosrc.co` (20),
`moflix.rpmplay.xyz` (10), `uqload.vc` (5), `moflix-stream.fans` (4),
`click-v4.junimedclk.com` (2), `flyfile.app` (1).

### Befund 2 — alle 53 Erfolge kommen weiterhin aus dem legalen Sockel

Die OK-Hosts sind unverändert ARD/ZDF/BR/funk (`nrodlzdf-a.akamaihd.net` 13×,
`br-vod.ard-mcdn.de` 4×, `prx-1560-ant-t.vmpx.online` 2×, `wdradaptiv-vh…` 2× …).
**Kein Index** (s.to/serienstream, bs.to, aniworld, filmpalast, moflix) hat einen
spielbaren Stream geliefert. Der +8-pp-Sprung stammt aus mehr ARD-Treffern
(die längere Auflösung findet mehr Sockel-Kandidaten), nicht aus den Index-Quellen.
Das ist genau die Verzerrung, die die Nachtsteuerung verhindern will.

### Befund 3 — die WebView war die Engstelle, nicht die Zeit

88 Interception-Versuche auf 27 Titeln, **2** davon mit einer Medien-URL. Im
Logcat (14 Versuche im Fenster) gilt:

| | Anteil | Bedeutung |
|---|---|---|
| 0 Anfragen | **7 / 14** | Der Versuch kam **nie** in die WebView — er wartete auf die app-weite Sperre, bis das Quellbudget ihn abschnitt. |
| 30 Anfragen | 7 / 14 | Die Seite wurde geladen (VOE, burningseries), aber **kein Medienabruf** ausgelöst. |
| Medien-URL | **0 / 14** | Kein einziger Treffer. |

Drei Ursachen, jede einzeln belegt:

1. **Warteschlange statt Arbeit.** Die versteckte WebView ist app-weit
   serialisiert; bis zu neun Streams eines Titels stellten je einen eigenen
   Lauf hinein. Wer nicht mehr drankam, verbrauchte das Budget ohne Ergebnis.
   → Behoben in `WebViewBudget` (ein Versuch je Titel, mit Frist) und
   `QUEUE_WAIT_MS` (5 s) im Interceptor.
2. **VOE verlangt eine Session.** Die Embed-Seite lädt ihre 30 Requests, bleibt
   aber vor dem Player stehen. Der Gastweg öffnet ein Popup auf
   `https://voe.sx/api2/session/generate-token` — das antwortet mit
   **302 auf `/login`** (DDOS-Guard-Cookies, CSRF-Token). Ohne Konto gibt es
   diese Strecke nicht; laut Auftrag wird kein Konto geführt.
3. **Filemoon braucht PoW + Fingerabdruck.** Das Pack scheitert an
   `step 9 get: HTTP 405` (`/api/videos/{code}/playback` verlangt POST +
   `X-Captcha-Token`). Die Challenge nennt `algorithm: sha256-leading-zero-bits`,
   `difficulty: 12`; die Seite selbst nutzt zusätzlich einen signierten
   Fingerabdruck (publicJwk, Canvas-/Audio-/WebGL-Hashes).

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run2-be45079/`
(Titelzeilen im UI-Dump `polls/p*/02.xml`, Logcat `polls/p*/01-logcat.txt`,
Antwort der Challenge/Token-Abfrage separat protokolliert).

## Ältere Methode (1.4.0)

- **Stichprobe:** 100 deterministisch gezogene Katalogtitel aus
  `assets/catalog/catalog.json` (1.000 Titel), Seed `20261122`,
  proportional nach Typ (584 Serien / 416 Filme → 58/42), gemischt über
  Genres. Die Auswahl liegt im Harness (`PlaybackQuotaActivity.sample`) und
  ist damit reproduzierbar.
- **Kette:** derselbe Produktionsweg wie im Player —
  `AutoPlaybackResolver.resolveFast(title, episode)` → Kandidatenliste →
  bis zu drei Kandidaten mit `Media3Frame61Player` abspielen.
- **Erfolg:** Die Wiedergabe auf dem Gerät schreitet wirklich fort
  (`positionMs > 0`). Ein grüner Build oder Fixture zählt nicht.
- **Startzeit:** von Beginn der Auflösung bis zur ersten fortschreitenden
  Position (`firstFrameMs`), inklusive Hoster-Kette und Puffer.
- **Protokoll:** je Titel eine Zeile (Titel, Typ, Ergebnis, Startzeit,
  Kandidatenzahl, versuchter Kandidat, Host, Fehlerklasse), zusätzlich im
  Logcat und als Datei unter `Android/data/<paket>/files/quota/`.

## Baseline (Build `243971d`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **6 / 100 = 6 %** | ≥ 95 % |
| Startzeit p50 | 5.345 ms | — |
| Startzeit p95 | **5.615 ms** | < 3.000 ms |
| Ø Startzeit | 5.230 ms | — |
| Fehlerklassen | 55 × `NO_CANDIDATE`, 38 × `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (3003), 1 × `ERROR_CODE_IO_BAD_HTTP_STATUS` | 0 sichtbar |

Alle sechs Erfolge liefen über die legal-freien Quellen (ARD, ZDF, KiKa,
funk) mit 2–6 Kandidaten; je Titel spielte der erste Kandidat.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/baseline-243971d.txt`
(vollständige 100-Zeilen-Tabelle), UI-Dumps `12-poll3/`.

## Zweiter Lauf (Build `e242198`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **5 / 100 = 5 %** | ≥ 95 % |
| Startzeit p50 | **2.635 ms** | — |
| Startzeit p95 | **2.714 ms** | < 3.000 ms ✅ |
| Ø Startzeit | 2.662 ms | — |
| Fehlerklassen | 61 × 3003, 34 × `NO_CANDIDATE` | 0 sichtbar |

Was sich bewegt hat: Die Auflösung ist von ~11 s auf **~1,6–1,9 s** gefallen
(`resolveFast` mit 600-ms-Frist, parallele Hoster-Ketten). Die Kandidaten
kommen jetzt aus den großen Indizes — `serienstream.to` (56 Titel),
`devideosrc.co` (35), `vidaraa.cc` (3), `vixeo.io` (2), `flyfile.app` (1) —
aber sie sind **HTML-Zwischenseiten**, die der Player nicht abspielen kann
(3003). Genau dort setzt der nächste Schritt an: Ketten über mehrere
Hoster-Packs, ein Redirect-Gate-Pack für `serienstream.to`, ein JS-Pack für
`devideosrc` und eine generische Embed-Auflösung für Hosts ohne Pack.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/run5-final-75.txt`.

## Dritter Lauf (Build `f673eb2`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **4 / 100 = 4 %** | ≥ 95 % |
| Startzeit p50 / p95 | 3.142 / 3.142 ms | < 3.000 ms |
| Fehlerklassen | 86 × `NO_CANDIDATE`, 10 × 3003 | 0 sichtbar |

Wichtiger Befund: Die neuen JS-Ketten (serienstream-Redirect, devideosrc,
vidmoly) brauchen **deutlich länger als 4 s**. Da `SOURCE_TIMEOUT_MS` bei 4 s
lag, wurde jede Pack-Auflösung mit JS-Schritt nach 4 s abgebrochen und lieferte
**0 Kandidaten** — die Quote fiel von 5 auf 4 %, obwohl die Zahl der 3003-Fälle
von 61 auf 10 sank. Der nächste Lauf (`4ebb58a`) hebt das Quellbudget auf 9 s
und die Obergrenze des Einsammelns auf 12 s; `resolveFast` bleibt bei 600 ms
nach dem ersten Kandidaten, schnelle Quellen starten also unverändert in ~1,6 s.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/run6-final-84.txt`.

## Vierte Runde (Build `4ebb58a`, 22.11.2026, A52) — abgebrochen

Nach 25 Titeln: 2 OK, 22 × `NO_CANDIDATE`, 1 × 3003. Die Resolve-Zeit lag bei
**20–27 s**, weil die Captcha-Runde (mehrere Packs à 9 s, seriell) auch im
schnellen Pfad lief. Fix in `ef7516a`: Captcha-Runde nur noch im vollständigen
Lauf (`graceMs == 0`).

## Fünfte Runde (Build `ef7516a`) — BLOCKED

Der Lauf startete, danach **sperrte sich der A52** (Keyguard, Screenshot zu
87 % dunkel, UI-Dump nur SystemUI). Die Brücke darf eine sichere Sperre nicht
umgehen (Owner-Policy) — der Lauf ist damit **BLOCKED**, nicht etwa fehlgeschlagen.
Zum Fortsetzen: Gerät entsperren, App starten, „Titel-Quote" erneut auslösen.

## Fünfte Runde (Build `ef7516a`) — BLOCKED

Der Lauf startete, danach **sperrte sich der A52** (Keyguard, Screenshot zu
87 % dunkel, UI-Dump nur SystemUI). Die Brücke darf eine sichere Sperre nicht
umgehen (Owner-Policy) — der Lauf ist damit **BLOCKED**, nicht etwa fehlgeschlagen.

## Sechste Runde (Build `b0aaf4c` = 1.4.0, A52)

Nach 11 Titeln: **1 OK, 10 × `NO_CANDIDATE`, 0 × 3003**. Die 3003-Klasse ist
in diesem Lauf also **nicht mehr aufgetreten** — die Kandidaten, die früher
als HTML-Embed endeten, werden jetzt entweder aufgelöst oder als
„kein Kandidat" verworfen. Dafür kostet jeder Titel ~70 s (JS-Ketten:
serienstream 8 s, devideosrc 8 s, generischer Embed 5 s), weshalb der Lauf für
100 Titel ~2 h bräuchte und nach 11 Titeln zugunsten der Auswertung pausiert
wurde.

Wichtig: Die Quote von 1/11 ist **keine** belastbare Quote (zu kleine
Stichprobe), sie zeigt nur die Richtung.

## Zwischenfazit

- **Startzeit:** Die 3-s-Marke ist bei schnellen Quellen erreicht (p95 2,7 s in
  Lauf 2); mit Quellbudget 9 s und JS-Ketten verschieben sich langsame Titel auf
  5–12 s.
- **Quote:** 4–6 von 100. Der Engpass ist nicht der Player, sondern das
  **Quellenangebot**: Von 100 zufälligen Katalogtiteln führen nur ARD/ZDF/KiKa
  (legal-frei) zuverlässig zum Ziel; die großen Indizes sitzen hinter
  Captcha-/JS-Ketten (VOE, Doodstream, Filemoon) oder liefern Embeds ohne
  auflösbare URL. Die 95-%-Marke ist mit diesem Bestand nicht erreichbar.
- **Nächste Hebel:** (1) Ketten für VOE/Doodstream/Filemoon so bauen, dass sie
  ohne sichtbaren Dialog durchlaufen; (2) Ersatz-Indizes mit eigenem
  Hoster-Pfad; (5) Katalog und Quellen aufeinander abstimmen (Titel, die
  Quellen tatsächlich führen).

## Ursachen der Baseline

1. **`NO_CANDIDATE` (55)** — die großen Serien-/Film-Indizes haben in diesem
   Stand keine verwertbare Stream-Regel: `s.to`/`bs.to` liefern nur Suche und
   Detail, `cine`/`filmstarts` sind reine Metadaten-Packs. Der Resolver wartet
   trotzdem die vollen 4 s je Quelle ab → ~11 s Laufzeit ohne Ergebnis.
2. **`3003` (38)** — Kandidaten existieren, sind aber HTML-Embeds
   (`vidsonic.net`, `devideosrc.co`), die der Player nicht parsen kann. Es
   fehlt die Hoster-Kette für diese Embeds.
3. **Startzeit** — der Resolver wartete auf die langsamste Quelle, obwohl die
   ARD-Kette nach ~1 s liefert; Hoster-Ketten liefen pro Stream sequentiell.

## Gegenmaßnahmen (Stand nach Baseline)

| Maßnahme | Wirkung |
|---|---|
| `resolveFast` mit 600 ms Frist nach dem ersten Kandidaten | Startzeit p95 5,6 s → erwartet ~2 s |
| Hoster-Auflösung je Pack parallel | ~11 s ohne Kandidat → unter 5 s |
| Stream-Regel für `s.to` (Serien) | neue Kandidaten für Serien |
| Hoster-Pack `vidsonic` (JS-Kette) | 3003 bei filmpalast-Kandidaten |
| `HeadlessCaptchaSolver` (versteckte WebView, Cookie-Cache) | VOE/Doodstream ohne Dialog |
| Stream-Regel für `aniworld` (Anime) | neue Kandidaten für Anime |

## Offen

- `bs.to` (Serien) und `devideosrc` (Filme) brauchen eigene Ketten; dort
  antwortet die Seite ohne `data-play-url`, bzw. baut die URL per JS.
- Die 95-%-Marke ist mit dem aktuellen Quellenbestand **nicht** erreichbar;
  entscheidend ist, wie viele der 100 Titel eine Quelle überhaupt führt.