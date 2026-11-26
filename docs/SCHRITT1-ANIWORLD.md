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
