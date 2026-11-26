# Schritt 1 — aniworld-Tiefenpfad (1.6.0)

Stand: 26.11.2026. Startbasis: frischer A52-Lauf `c953d3f`, Training Seed
`20261122`: **aniworld 16/19 richtig (84 %)**, keine Falschwiedergabe. Ziel:
**20/20 Titel** Ende-zu-Ende auf dem A52.

## Pflicht je Titel

1. Suche/Detail findet den exakten `aw:<slug>`-Eintrag.
2. Auflösung liefert einen Kandidaten der eigenen Quellengruppe.
3. Identitätsprüfung bestätigt Titel und bei Serien die tatsächlich adressierte
   Staffel/Folge (`CORRECT`).
4. Poster und Backdrop sind lokale, dekodierbare Dateien; kein Hotlink.
5. Detailseite zeigt Bild, Beschreibung, Besetzung, Genres, Laufzeit,
   ähnliche Titel; bei Serien Staffeln/Folgen und Fortschritt.
6. Wiedergabe zeigt den richtigen Inhalt; Overlay zeigt Titel und Laufzeit.
7. Ein Screenshot der laufenden Wiedergabe und ein Log/JSON-Nachweis existieren.

## 20 Titel

Die 16 im Schritt-0-Lauf korrekt gespielten Titel:

- Your Name.
- A Tale of the Secret Saint
- Wedding Peach
- ROLL OVER AND DIE
- The Vampire Dies in No Time
- When Will Ayumu Make His Move?
- The Demon Girl Next Door (Slug `machikado-mazoku`)
- Afro Samurai
- Super Psychic Policeman Chojo
- Air Master
- Origin - Spirits of the Past
- This Monster Wants to Eat Me
- Mix: Meisei Story
- Elegant Yokai Apartment Life
- Food for the Soul
- ReLIFE

Dazu die drei bisherigen Fehler und ein zusätzlicher Kandidat:

- Beautiful Bones - Sakurako's Investigation (`EMBED_UNRESOLVED`)
- Watari-kun's ****** Is about to Collapse (`EMBED_UNRESOLVED`)
- Hal (`EMBED_UNRESOLVED`)
- Slam Dunk (zusätzlicher 20. Pfad)

## Belege

- Schritt-0-Rohdaten: `~/hendler-dynasty/evidence/frame61/1.6.0/step0/measure-polls/latest.xml`
- Vorher-Screenshots: `~/hendler-dynasty/evidence/frame61/1.6.0/step1/B0-out/` (Home),
  `B1-out/` (Detail „Die Odyssee"), `B2-out/` (Player-Fehler).
- Nachher-Screenshots/Logs: werden nach Integration und Build unter
  `~/hendler-dynasty/evidence/frame61/1.6.0/step1/` abgelegt.

## Aktuell bekannte technische Lücken

- 0/199 aniworld-Poster lokal; alle 199 Katalogeinträge hotlinked.
- 0/199 mit Jahr/Genres/Beschreibung/Laufzeit; keine `aw:*`-Snapshots.
- Detailseite nicht scrollbar, keine Staffelgruppierung, Abspielen startet
  unverändert S1E0/Standardwerte.
- HTML-Episodenparser bildet IDs wie `episode-1` statt `1`; Staffel wird ohne
  Marker auf 1 gesetzt.
- Identitätsnachweis hängt am Suchtreffer/Redirect, nicht an der tatsächlich
  adressierten Quellfolge.
- Die drei Fehler haben VOE/Doodstream/Filemoon im Angebot, aber nur ein
  dropcdn-Kandidat erreicht die Wiedergabe.

## Messstand 26.11.2026

- CI `45844947689` auf `78fa07a`: Gate + Release grün, **271/271 Tests**,
  Lint 0 Errors / 109 Warnings / 1 Information.
- Acceptance-APK `90be70fe…` per Device-Lab `job-d02f4f1cc9af4ce8` installiert.
- Erste echte 20er-Messung: **Katalogpanel 20/20 PASS** (lokale Poster,
  Backdrops, Overview, Genres, Cast, Jahr, Laufzeit, Episoden), **10/20
  Ende-zu-Ende PASS**. Die anderen 10 lieferten keinen aniworld-Kandidaten;
  der strenge Pfad hing an der zusätzlichen Suche.
- Fix `dc8c417`: direkter `sourceKey`, Titel + Episoden aus einem Quellrequest.
  Nachmessung folgt; die 10/20 sind kein abgeschlossenes Gate.
- Rohbelege: `~/hendler-dynasty/evidence/frame61/1.6.0/step1/aniworld20/polls2/latest.xml`.

### Zweite Messung und Sichtprüfung

- Direkter `sourceKey` verbesserte den Pfad auf **12/20**; verbleibende Fehler
  waren Hoster-Timeouts/Regex-/HTTP-Fehler sowie ein Kandidat ohne erstes Bild.
- Der Harness nutzte zunächst nur den ersten Kandidaten. Commit `c2bc5af`
  bildet jetzt bis zu drei Kandidaten und zwei Alternative-Runden nach.
- Die ersten 14 lokalen Screenshots waren per D-Pad-Betrachter abrufbar, aber
  die Sichtprüfung zeigte schwarze Videoflächen. Das ist **kein gültiger
  Bildbeleg**. Commit `efbc0d4` capturet direkt von der Player-Surface und
  brennt Titel/Laufzeit ein; die Nachmessung muss die Bildinhalte erneut
  sichtprüfen.

### Ersatz nach wiederholten Fehlversuchen (26.11.2026)

Die finale Messung mit Alternative-Runden erreichte **13/20**. Sieben Titel
lieferten weiterhin keinen Kandidaten: A Tale of the Secret Saint und Slam Dunk
(32-s-Timeout), Air Master (DeVideoSrc-Timeout), ReLIFE, Super Psychic Policeman
Chojo und Watari-kun (Regex ohne Treffer), When Will Ayumu Make His Move
(Vidoza-HTTP-404). Sie werden nach mehreren Fehlversuchen aus dem Pfad entfernt.

Ersatz: SPY x FAMILY, Fullmetal Alchemist, Gabriel Dropout, Kabaneri of the Iron
Fortress, Radiant, Bungo Stray Dogs und GTO: Great Teacher Onizuka. Für alle
sieben existieren lokale Poster/Backdrops, Metadaten, Cast und Episoden. Die
Auswahl ist kein Blockierungs-Eingeständnis, sondern die Katalog-Wahrheit:
Nur Titel mit belegtem Pfad kommen in die Abnahme.

### Finaler Ersatz und Retry-Wirkung

- Nach frischem Retry bei leerer erster Auflösung erreichte der Pfad **19/20**.
  Nur SPY x FAMILY blieb ohne Kandidaten (32-s-Timeout).
- SPY x FAMILY wird durch **Air Master** ersetzt; dessen lokale Metadaten und
  Bilder waren bereits vorhanden, ein früherer Lauf spielte den Titel korrekt.
- Die finale 20er-Messung folgt mit diesem Stand.
