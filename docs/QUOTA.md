# QUOTA.md — Titel-Erfolgsquote (Messharness und Baseline)

Stand: 23.11.2026. Methode und Zahlen für Version 1.5.0.

## Methode (1.5.0)

- **Zwei Stichproben, garantiert disjunkt:**
  - **Training** (`PlaybackQuotaActivity`, Seed `20261122`, 100 Titel),
  - **Holdout** (`PlaybackQuotaHoldoutActivity`, Seed `20261123`, 100 Titel aus
    dem Rest des Katalogs). Die Holdout-Stichprobe wird erst nach der
    Trainings-Stichprobe gefahren und nie zum Nachsteuern benutzt.
- **Katalog:** invertiert — die Bibliothek kommt aus den Quellen selbst
  (ARD 500, bs.to 300, streamcloud 197), siehe `docs/ENTSCHEIDUNGEN.md`.
- **Kette:** derselbe Produktionsweg wie im Player —
  `AutoPlaybackResolver.resolveFast` → Kandidaten → bis zu drei mit
  `Media3Frame61Player`.
- **Zeiten getrennt:** `resolveMs` (Auflösung), `playMs` (Kandidat bis erstes
  Bild), `firstFrameMs` (Summe ab Tippen).
- **Fehlertaxonomie:** `NO_CANDIDATE`, `EMBED_UNRESOLVED`,
  `CONTAINER_UNSUPPORTED`, `GEO_BLOCKED`, `RATE_LIMITED`, `TIMEOUT` — die
  Klasse wird aus den Diagnose-Ereignissen des Resolvers abgeleitet, nicht
  geraten.
- **Protokoll:** je Titel eine Zeile (idx, id, type, RESULT, class, resolveMs,
  playMs, candidates, host) im Logcat, im Statusbereich und in einer Datei.

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