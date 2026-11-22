# QUOTA.md — Titel-Erfolgsquote (Messharness und Baseline)

Stand: 22.11.2026. Methode und Zahlen für Version 1.4.0.

## Methode

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