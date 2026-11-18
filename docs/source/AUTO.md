# AUTO.md — Automatische Auflösung (Block 6)

Stand: 2026-11-18 · Build `0cf5fce` (APK `a1a72fc3…`) · Gerät SM-A525F

## Verhalten

Der Nutzer wählt nie eine Quelle.

1. **Suche** (`AggregatedIndexSource`) fragt alle aktiven Packs parallel an
   (4 s Timeout je Pack), mappt Treffer auf Katalogtitel (normalisierter
   Titel-Match) und dedupliziert. Quellenzeile im UI: `indizes, snapshots,
   catalog`. Fehler werden nur geloggt (`Frame61Search`).
2. **Wiedergabe** (`AutoPlaybackResolver`):
   - alle aktiven Packs parallel, 4 s je Pack;
   - jeder Stream durchläuft die Hoster-Kette (`HosterResolver`) mit 4 s;
   - Kandidaten werden sortiert: legal-frei, Sprache (de zuerst), Qualität,
     Health;
   - die erste brauchbare URL gewinnt, weitere sind Fallback-Mirrors;
   - Ergebnis wird **15 Minuten** gecacht (Schlüssel: Titel, Folge,
     deaktivierte Quellen).
3. **Ausfall** ist unsichtbar: nur `Log` (`Frame61Resolve`), kein Quellen- oder
   Fehlerlabel. Das Quellenlabel erscheint nur, wenn eine echte Quelle geliefert
   hat (z. B. „ARD Mediathek").

## Beleg: Erstausfall und erfolgreiche Quelle

Vollständiger Auszug: [autoresolve-log.txt](autoresolve-log.txt).

```
Frame61Resolve: resolve 'Babylon Berlin' ep=1 active=26 disabled=[]
Frame61Resolve: bs-to: 0 candidate(s) in 57 ms (no usable stream)
Frame61Resolve: s-to: 0 candidate(s) in 62 ms (no usable stream)
Frame61Resolve: movie4k: 0 candidate(s) in 68 ms (no usable stream)
…
Frame61Resolve: archive-org: 0 candidate(s) in 1134 ms (no usable stream)
Frame61Resolve: ard-mediathek: 15 candidate(s) in 911 ms
```

25 Packs liefern keinen brauchbaren Stream, `ard-mediathek` liefert 15
Kandidaten; der Player startet mit `ARD Mediathek` und
`18:16 / 44:55` ([auto-player.png](auto-player.png)).

## Cache-Beleg

Zweiter Start derselben Folge innerhalb von 30 Sekunden:

```
Frame61Resolve: cache hit 'Babylon Berlin' (15 mirrors)
```

Kein erneuter Netzaufruf, keine sichtbare Unterbrechung.

## Grenzen

- Die parallele Suche läuft real über alle 26 Packs; die meisten liefern
  wegen fehlender belegter Selektoren 0 Treffer (Block 8/9).
- Die Hoster-Kette ist implementiert, aber aktuell gewinnt fast immer der
  `direct`-Pfad (ARD). Die neun Hoster-Packs sind noch `verified:false`
  (Block 8).
- Die Suchaggregation mappt nur Treffer, die einem Katalogtitel entsprechen;
  fremde Titel werden verworfen, damit die Detailroute gültig bleibt.
- Der Demo-HLS bleibt als letzter Notnagel, wird aber **nicht** als Quelle
  gelabelt (Ausfall nur im Log).
- Kein Test mit einem echten Nicht-ARD-Treffer, weil die Indexseiten noch
  nicht belegt sind.
