# GATE 1 — Inhaltsrichtigkeit (1.6.0)

Stand: 26.11.2026. Quellstand `2382520` (Gate `45769655972`: 224/224 Tests,
Lint 0 Errors / 109 Warnings). Gerät: Samsung SM-A525F (RZ8T703XJWF), Android 14,
Acceptance-Build, Device-Lab-Lease.

## Nordstern

**Richtigkeitsquote** = Anteil der Titel, bei denen nachweislich der
**angefragte** Inhalt läuft. Alles andere (Startzeit, Abdeckung, Tests) ist
dienend. Ziel: ≥ 95 % auf der Holdout-Stichprobe.

## Was vorher falsch war (belegt)

1. **Platzhalter statt Titel:** `CatalogPlayback.DEMO_URL` (Mux-Teststream)
   wurde als Fallback-Kandidat in den Player gereicht. Bei jedem
   Auflösungsfehler — in Lauf 11 rund ein Drittel der Titel — sah der Nutzer
   ein fremdes Beispielvideo. Das ist die wörtliche Meldung „Oft wird nur so
   ein Beispielvideo gezeigt."
2. **Keine Identitätsprüfung:** Ein Kandidat galt als Erfolg, sobald die
   Position lief (`positionMs > 0`). Ob das die angefragte Serie, ein Trailer
   oder ein anderer Film war, wurde nie geprüft. Die Quote 67/100 bzw. 70/100
   war deshalb eine **Spielbarkeits**-, keine **Richtigkeits**quote.
3. **Kein Laufzeit-/Episodenbezug:** Der Katalog führt 0 von 997 Laufzeiten;
   Staffel/Episode wurden nur beim Adressieren, nie beim Prüfen verwendet.

## Was jetzt gebaut ist

- **`ContentIdentity`** (`scraper/resolver`): Normalisierung (Umlaute,
  Diakritika, Artikel, Füllwörter, Jahreszahlen, Staffel-/Folgenanhänge),
  Token-Dice-Ähnlichkeit mit **≥ 0,75** als Schwelle, Fortsetzungsziffern
  („2" gegen „3") als hartes Ausschlussmerkmal, Trailer-Marker
  (`trailer`, `teaser`, `vorschau`, `preview`, `ausschnitt`, `making of`,
  `clip`/`sample` nur am Rand oder in Klammern), Episodenmarker-Prüfung
  (`staffel-N`, `sNEN`, `season/N/episode/N`) und Laufzeit-Toleranz
  (Filme 10 %, Serien 15 %).
- **`ContentIdentityGate`**: wendet die Prüfung auf eine Kandidatenliste an.
  Fehlt die Quellenangabe und ist die Watch-URL eine HTML-Seite, wird
  `og:title`/`<title>` einmalig geholt (Budget 2,5 s, Prozess-Cache); direkte
  Medien-URLs (`.mp4`, `.m3u8`, …) werden nie als Seite abgerufen.
- **`MirrorCandidate`** trägt `identityTitle`, `identityYear`,
  `identityRuntimeSec`, `identityKind`. Gefüllt von:
  - `AutoPlaybackResolver` — Titel/Jahr des **Suchtreffers** (`kind=source`),
  - `ArdPlaybackResolver` — Titel/Jahr des **ARD-API-Items** (`kind=api`),
  - `BsToPlaybackResolver` — `<title>` der bereits geladenen Serienseite
    (`kind=page`, kein Zusatzabruf).
- **Player** (`PlayerViewModel`): Vorwärmlauf, Startauflösung,
  Alternative-Kette und automatische Neuauflösung spielen nur noch
  `CORRECT`-Kandidaten. Ein `TRAILER_ONLY`-Kandidat ist nur letzter Ausweg
  und wird sichtbar als „Vorschau" markiert. Sonst: verständliche Meldung
  („Keine Quelle mit bestätigtem Inhalt gefunden") mit Wiederholen-Option —
  **kein Demo-Video mehr** (`CatalogPlayback` liefert `emptyList()`).
- **Overlay**: Titel (aus dem Katalog) und Laufzeit bleiben dauerhaft oben
  links eingeblendet — eine Fehlzuordnung fällt sofort auf.
- **Messung** (`PlaybackQuotaActivity`): je Titel `QUOTA IDENT … ident=…`
  plus `QUOTA RIGHT SUMMARY`/`QUOTA RIGHT GROUPEN`; die alte
  `QUOTA SUMMARY`/`GROUPEN` (spielbar) bleibt für die Reihe 1–11 erhalten.

Tests: 21 neue (`ContentIdentityTest` 15, `ContentIdentityGateTest` 6),
Gesamtstand **224/224**.

## Ergebnis der Baseline-Messung

Stichprobe: Training, Seed `20261122`, 100 Titel, Katalog 997, Build
`2382520`. Rohbelege: `~/hendler-dynasty/evidence/frame61/1.6.0/gate1-run/polls/`.

**Baseline: 2/100 richtig (2 %)** — das ist die schockierende Zahl, die die
bisherigen 67/100 nicht zeigten. **11 Titel spielten überhaupt; 9 dieser 11
Wiedergaben waren ein anderer Inhalt** (`WRONG_CONTENT`, 82 % der Plays).

| Kennzahl | Baseline (`2382520`, 26.11.2026) |
|---|---|
| Stichprobe | Training, Seed `20261122`, 100 Titel |
| Richtigkeitsquote | **2/100 = 2 %** |
| Gespielt | 11/100 |
| Davon falsch | 9 × `WRONG_CONTENT` |
| Trailer / unbekannt | 0 / 0 |
| Nicht gespielt | 89 (83 `NO_CANDIDATE`, 3 `CONTAINER_UNSUPPORTED`, 2 `EMBED_UNRESOLVED`, 1 `GEO_BLOCKED`) |

Rohbeleg: `QUOTA RIGHT SUMMARY mode=train richtig=2/100 quote=2
gespielt=11/100 falsch=[WRONG_CONTENT:9]` in
`~/hendler-dynasty/evidence/frame61/1.6.0/gate1-run/polls/latest.xml`.

**Ursache (nicht geraten, an echten Zeilen belegt):** Die played-WRONG-Fälle
hatten durchgehend `evidenz=quelle:api`, `score=0.50` und Hosts
`*.akamaihd.net`. Beispiele:

```
QUOTA IDENT train idx=2 id=ss:zeit-verbrechen group=serienstream played=ja
ident=WRONG_CONTENT score=0.50 evidenz=quelle:api grund=Titel passt nicht
QUOTA train idx=2 … RESULT=OK … host=wdradaptiv-vh.akamaihd.net

QUOTA IDENT train idx=3 id=ss:mcgregor-forever group=serienstream played=ja
ident=WRONG_CONTENT score=0.50 evidenz=quelle:api grund=Titel passt nicht
QUOTA train idx=3 … RESULT=OK … host=nrodlzdf-a.akamaihd.net
```

Mechanismus: `ArdPlaybackResolver.findShow()` nahm den ersten ARD-Treffer,
auch wenn der Titel nicht passte. `AutoPlaybackResolver.resolveNow()` fragte
alle Packs parallel ab; die schnelle ARD-API gewann die 600-ms-Frist gegen die
eigene Quellengruppe. Ergebnis: ein serienstream-Titel spielte ein fremdes
ARD/ZDF-Video — genau die Meldung „nicht der jeweilige Inhalt".

**Stopp-Regel und Umgang:** Die Baseline ist weit unter 60 %. Gebaut wird
trotzdem nicht auf einem anderen Gate, sondern ausschließlich der belegte
Gate-1-Fix (eigene Quellengruppe zuerst, kein ARD-Erst-Treffer-Fallback).
Danach wird **neu gemessen**. Steht die Quote dann weiter unter 60 %, gilt
Gate 1 als unerfüllt und die weiteren Gates bleiben geschlossen.

### Nach dem Fix: Messung blockiert durch DNS-Ausfall (kein App-Ergebnis)

Quellstand `c953d3f` (CI `45788635299`: gate + release grün, Acceptance-APK
`8aa1c909…` auf dem A52 installiert, Job `job-d980de1ac0b34b22` PASS).

Der erneute Trainingslauf lieferte zunächst `0/100` — **das darf nicht als
App-Ergebnis gewertet werden**. Der Logcat zeigt zur selben Zeit:

```
10:30:26.191 D NetworkMonitor/123: PROBE_HTTP … java.net.UnknownHostException:
  Unable to resolve host "connectivitycheck.gstatic.com"
10:30:26.192 D NetworkMonitor/123: PROBE_HTTPS … java.net.UnknownHostException:
  Unable to resolve host "www.google.com"
10:30:26.196 D NetworkMonitor/123: PROBE_FALLBACK … UnknownHostException
```

Damit ist **INFRA_FAIL belegt**: das Gerät hat keine Namensauflösung; alle
100 Titel scheitern in 100–190 ms mit `NO_CANDIDATE`. Die Fix-Messung wird
wiederholt, sobald DNS/WLAN auf dem A52 wieder funktioniert — nicht früher,
und ohne Änderung der Netzwerk-Einstellungen fremder Projekte.

Nächster Schritt: DNS-Probe frisch prüfen (Android-Netzwerkmonitor), dann
denselben Trainingslauf mit `c953d3f` fahren und die Quote gegen die Baseline
2/100 ausweisen. Erst diese Messung entscheidet, ob Gate 1 erfüllt ist.

### Schritt 0 — gültige Nachmessung nach DNS-Erholung (26.11.2026, 11:11–11:35)

Frische Netzprobe: A52 HTTP 200 von `voe.sx` in 134 ms; kein
`UnknownHostException` im Messfenster. Acceptance-Build `c953d3f` (APK
`8aa1c909…`) blieb installiert. Trainingslauf Seed `20261122`, 100 Titel:

| Kennzahl | Baseline `2382520` | Fix `c953d3f` | Delta |
|---|---:|---:|---:|
| Richtigkeitsquote | **2/100** | **20/100** | **+18 pp** |
| Gespielt | 11/100 | 26/100 | +15 |
| Falsch gespielt | 9 | 6 | −3 |
| Auflösung p50 / p95 | nicht berichtsfähig | 3287 / 27173 ms | — |
| Erstes Bild p50 / p95 | nicht berichtsfähig | 11581 / 29370 ms | — |

Rohbeleg: `~/hendler-dynasty/evidence/frame61/1.6.0/step0/measure-polls/latest.xml`
(`QUOTA RIGHT SUMMARY … richtig=20/100 … gespielt=26/100 falsch=[WRONG_CONTENT:6]`).

**Ergebnis je Quellengruppe:** aniworld 16/19 richtig (84 %), ard 2/10,
serienstream 1/43, bsto 1/6, filmpalast 0/11, moflix 0/11. Die verbleibenden
sechs Falschwiedergaben liegen in ard (3) und serienstream (3); aniworld hat
in dieser Stichprobe **keine** Falschwiedergabe. Die drei aniworld-Fehler sind
`EMBED_UNRESOLVED` bei Beautiful Bones, Watari-kun und Hal.

**Gate-Entscheidung:** Die Gesamtquote liegt unter 60 %, aber der belegte Fix
hat sie von 2 % auf 20 % angehoben und die Falschwiedergaben reduziert. Die
weitere Arbeit ist daher nicht „Breite reparieren", sondern der vom Owner
vorgegebene **Tiefenpfad aniworld**: 16/19 als Startbasis, Ziel 20/20 mit
lokalen Bildern, gefüllter Detailseite und Staffel/Folgenwahl. Die übrigen
Gruppen bleiben sichtbar unerfüllt; sie werden nicht als grün berichtet.

## Fehlerverteilung

### Verteilung laut Harness-Summary

| Befund | Anzahl | Bedeutung |
|---|---|---|
| `WRONG_CONTENT` | 9 | lief, aber anderes Video (ARD/ZDF-Fremdtreffer) |
| `CORRECT` | 2 | lief und Titel bestätigt (beide aniworld) |
| `NO_CANDIDATE` | 83 | aktuelle Quellenlage: keine Kandidaten (separates Gate-3-Thema) |
| `CONTAINER_UNSUPPORTED` | 3 | aufgelöster Hoster liefert kein abspielbares Medium |
| `EMBED_UNRESOLVED` | 2 | Kandidat, aber kein erstes Bild |
| `GEO_BLOCKED` | 1 | Quellenseite sperrt die Geräte-IP |

Wichtig: `NO_CANDIDATE` ist hier **nicht** durch die Identitätsprüfung
verursacht (der Harness prüft erst nach dem Spielen). Die 11 Plays und die
9 Fehlzuordnungen belegen den falschen Inhalt; die 83 NO_CANDIDATE zeigen
zusätzlich, dass die Quellenverfügbarkeit an diesem Tag schlecht ist.

## Entscheidungen zu Gate 1

1. **Demo-Stream entfernt statt umbenannt.** Ein klar beschrifteter
   Platzhalter wäre weiterhin falscher Inhalt; die Identitätsregel erlaubt ihn
   nicht. Der Player erklärt stattdessen den Fehlschlag.
2. **Quellenangabe vor Seitenabruf.** Der Suchtreffer der Quelle ist die
   billigste belastbare Identitätsangabe (kein Zusatz-Request). Der
   Seitentitel wird nur geholt, wenn sie fehlt.
3. **Streng bei Fortsetzungsziffern.** „Man-Eater" und „Man-Eater 2" sind
   verschiedene Filme; die Ähnlichkeit allein (0,8) hätte den falschen
   durchgelassen. Eine Ziffer, die nur auf einer Seite steht, kappt den Score
   auf 0,5.
