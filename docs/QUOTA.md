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