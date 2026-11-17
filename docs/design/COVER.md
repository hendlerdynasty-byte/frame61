# COVER.md — Ecken-Defekt und zentrale PosterCard (Block 2)

Stand: 2026-11-17 · Build `e1c91df` (APK `c9ce651a…`) · Gerät SM-A525F

## Reproduktion (vorher)

| Stelle | Befund | Bild |
|---|---|---|
| Start | Cover wurden in `Artwork` separat geclippt, der Fokus-/Scale-Wrapper lag **außerhalb** dieses Clips; zusätzlich eine tonale `Surface` ohne gemeinsamen Stack → Ecken konnten beim Fokus-Scale aufreißen | [before-home.png](before-home.png) |
| Suche | `AsyncImage` mit `ContentScale.Crop` **ohne jeden** `clip`/Shape in einer rechteckigen Surface-Zeile → harte, quadratische Bildecken | [before-search.png](before-search.png) |
| Detail | Es gab **gar kein** Cover; nur Text. Damit fehlte die dritte Stelle für den Fix vollständig | [before-detail.png](before-detail.png) |

## Geprüfte Ursachen in der vorgegebenen Reihenfolge

1. **`clip()` hinter `background()`/`border()`/`shadow()`** — im alten Code
   lag der Clip nur in `Artwork`; `Surface`/Fokus-Wrapper standen davor.
   Bestätigt im Code, kein sauberer gemeinsamer Stack.
2. **Fehlender `clip()` bei `ContentScale.Crop`** — in `SearchScreen`
   nachgewiesen: `AsyncImage` ohne Shape. Das ist der sichtbare Defekt.
3. **Zu kleiner `RoundedCornerShape`-Radius** — nicht die Ursache; Karten
   nutzten 12 dp, Suche gar keinen Radius. Wird jetzt auf min. 8 dp geklemmt.
4. **1-dp-Stroke über der Kante** — es gab keinen Border; kein Treffer.
5. **Elevation-Schatten als harte Kante** — die alte `Surface` konnte tonalen
   Elevation-Schatten zeichnen; im neuen Stack gibt es keine Elevation.
6. **`shadow()` vor `clip()`** — kein expliziter Schatten im Code; kein Treffer.

## Fix

Eine zentrale Komponente: `core/design/.../PosterCard.kt`.

Fester Modifier-Stack:

```
optional tvFocusable (Fokusring innen)
→ clip(RoundedCornerShape(max(radius, 8.dp)))
→ background(surface / Gradient-Platzhalter)
→ AsyncImage(ContentScale.Crop) oder Platzhalter
→ optionaler 1-dp-Border innen
→ optionales Overlay (Titel/Gradient)
```

- Kein `Surface`-Elevation, kein `shadow()`, kein doppelter Clip.
- `fillMaxSize()` des Bildes erbt den Clip des Parents; es gibt keinen
  eigenen Clip mehr in Screens.
- Verwendet in:
  - `TvHome` (Rail-Karten + Billboard),
  - `SearchScreen` (Treffer-Thumbnail),
  - `DetailScreen` (neu: Poster 120 × 180 dp).
- `grep -n "clip(" app/.../ui/home/TvHome.kt ui/search/SearchScreen.kt ui/HomeScreen.kt`
  → **keine Treffer**. Der einzige Cover-Clip liegt in `PosterCard.kt`.
  (Der Skin-Vorschau-Rahmen im Skins-Screen ist kein Cover und behält seinen
  eigenen dekorativen Clip.)

## Nachher (am Gerät)

| Stelle | Bild |
|---|---|
| Start | [after-home.png](after-home.png) |
| Suche | [after-search.png](after-search.png) |
| Detail | [after-detail.png](after-detail.png) |

Die Thumbnails in der Suche sind jetzt gerundet, das Detail-Cover sitzt in
einem sauberen 12-dp-Rahmen, und die Home-Karten zeigen keine harten Ecken.

## Grenzen

- Der Vorher-Nachher-Vergleich ist ein Screenshot-Vergleich; es gibt keinen
  automatisierten Pixel-Test für Ecken. Die Ursache ist im Code belegt.
- Radius/Dichte kommen weiterhin aus dem Skin; Block 3/4 schärfen die Tokens.
