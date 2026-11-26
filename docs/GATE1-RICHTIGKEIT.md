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

<!-- ZAHLEN -->

## Fehlerverteilung

<!-- VERTEILUNG -->

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
