# NAVIGATION.md — Sechs Ziele und Einstellungen-Baum (Block 5)

Stand: 2026-11-18 · Build `6f6a175` (APK `078c48f6…`) · Gerät SM-A525F

## Navigation

| Ziel | Route | Inhalt |
|---|---|---|
| Start | `home` | Billboard + Regale |
| Filme | `films` | 2:3-Grid aller Katalogfilme |
| Serien | `series` | 2:3-Grid aller Serien |
| Suche | `search` | Snapshot-/Katalogsuche |
| Meine Liste | `watchlist` | persistente Merkliste |
| Einstellungen | `settings` | Baum (siehe unten) |

**Entfernt:** „Skins" und „Quellen" sind keine Menüpunkte mehr. Die Skin-Auswahl
liegt unter Einstellungen → Darstellung; das Source Lab ist über
Einstellungen → Erweitert → „Source Lab öffnen" erreichbar.

Die Leiste zeigt auf 1080 px alle sechs Labels (UI-Dump: Start 71–197, Filme
217–343, Serien 365–491, Suche 512–638, Meine Liste 658–817,
Einstellungen 838–1026). Auf schmalen Geräten ist die Leiste horizontal
scrollbar, ohne Umbrüche.

## Einstellungen-Baum

| Abschnitt | Inhalt | Persistenz |
|---|---|---|
| Darstellung | sechs Skins, Dichte (Kompakt/Standard/Geräumig), Untertitel-Optik | DataStore; Skin/Dichte sofort, Untertitelgröße im Player |
| Wiedergabe | Autoplay, Hoster-Präferenz, Qualität (Auto/1080/720/480), Gesehen-Schwelle | DataStore |
| Erweitert | Quellenliste mit Schaltern (Default alle an), Fetch-Status je Pack, Source Lab | `disabled_sources` in DataStore |
| Info | Info & Quellen (Version, Attribution, Lizenzen) | statisch |

Nichts Technisches steht außerhalb der Einstellungen; die NavBar enthält nur
die sechs Nutzerziele.

## Meine Liste

- Detailseite hat „In Meine Liste"/„Aus Meine Liste"; die Auswahl landet als
  String-Set in DataStore.
- Watchlist-Screen zeigt die Titel als 2:3-Grid; leerer Zustand wird erklärt.
- Nach `am force-stop` + Neustart ist der Eintrag weiterhin vorhanden
  ([meine-liste-nach-restart.png](meine-liste-nach-restart.png)).

## Belege

| Datei | Inhalt |
|---|---|
| [home-six-destinations.png](home-six-destinations.png) | Start mit sechs Nav-Zielen |
| [filme.png](filme.png) | Film-Grid, Poster geladen |
| [serien.png](serien.png) | Serien-Grid |
| [meine-liste-leer.png](meine-liste-leer.png) | Leerzustand |
| [detail-watchlist.png](detail-watchlist.png) | Detail mit „In Meine Liste" |
| [meine-liste-mit-titel.png](meine-liste-mit-titel.png) | Liste mit Interstellar |
| [meine-liste-nach-restart.png](meine-liste-nach-restart.png) | Persistenz nach Neustart |
| [einstellungen-top.png](einstellungen-top.png) | Darstellung + Wiedergabe |
| [einstellungen-erweitert.png](einstellungen-erweitert.png) | Quellen-Schalter + Fetch-Status |

## Gefundene und behobene Defekte

1. **Film-Poster leer:** Wikimedia liefert für OkHttp/leere User-Agents HTTP
   403 (gemessen: `""` → 403, `okhttp/4.12.0` → 403, `Frame61/1.0` → 200).
   Der Coil-ImageLoader nutzt jetzt einen eigenen OkHttp-Client mit
   `User-Agent: Frame61/1.0 (Android; poster cache)`. Danach laden die
   Film-Poster ([filme.png](filme.png)).
2. **„Einstellungen" außerhalb der Leiste:** Sechs Ziele passten nicht in die
   alte SpaceBetween-Leiste. Schrift 11 sp, Abstand 4 dp, horizontal
   scrollbar; alle sechs sichtbar.
3. **Watchlist-Compile-Fehler:** lokale Variable `items` verdeckte die
   Grid-`items`-Funktion; in `titles` umbenannt.

## Grenzen

- Filme/Serien sind Grids über den 46-Titel-Katalog; der Ausbau auf ≥ 600
  Titel ist Block 10.
- Quellen-Schalter wirken auf die Auto-Auflösung (Block 6); die meisten Packs
  sind noch `verified:false`.
- Screenshots auf dem A52 (Portrait); TV-Dichte 1,0 nicht separat geprüft.
