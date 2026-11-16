# SKINS.md — Frame61 Skin-System

## Übersicht

Frame61 verwendet ein token-basiertes Skin-System. Jeder Skin wird als JSON-Datei in `assets/skins/<id>.json` definiert und zur Laufzeit geladen.

### SkinManager
- Interface: `SkinManager` (core/design)
- Implementation: `SkinManagerImpl` mit `SkinDefinitions` (11 vordefinierte Skins)
- `Frame61Skin` Data-Klasse mit allen Tokens
- Layout wird pro Profil gespeichert (DataStore)

### Skin-Tokens

| Token | Typ | Beschreibung |
|-------|-----|-------------|
| canvas | String | Hintergrundfarbe (HEX) |
| surfaces | String | Oberflächenfarbe (HEX) |
| accents | String | Akzentfarbe (HEX) |
| text | String | Textfarbe (HEX) |
| radius | Int | Border-Radius (dp) |
| cardAspect | Double | Seitenverhältnis der Karten |
| rowHeight | Int | Reihen-Höhe (dp) |
| gutter | Int | Abstand zwischen Reihen (dp) |
| navModel | String | Navigations-Modell (bottom-bar, left-bar, pill) |
| focusTreatment | String | Fokus-Behandlung (scale, lift, glow, ring) |
| motionLevel | String | Animations-Stufe (low, medium, high) |
| typeScale | Double | Typo-Skalierung |
| billboardHeight | Int | Billboard-Höhe (dp) |
| detailLayout | String | Detail-Layout (vertical, horizontal) |
| playerChrome | String | Player-Chrome (dark, light, minimal) |

## Vordefinierte Skins

### 1. Netflix
- canvas: #141414, accents: #e50914
- navModel: bottom-bar, focusTreatment: scale
- motionLevel: medium, typeScale: 1.0
- rowHeight: 180, cardAspect: 0.5625

### 2. Disney+
- canvas: #05060b, accents: #0063e5
- navModel: left-bar, focusTreatment: lift
- motionLevel: high, typeScale: 1.05
- rowHeight: 200, cardAspect: 0.75

### 3. Apple TV
- canvas: #000000, accents: #ffffff
- navModel: pill, focusTreatment: glow
- motionLevel: low, typeScale: 0.95
- rowHeight: 170, cardAspect: 0.6

### 4. Prime Video
- canvas: #0f1218, accents: #00a8e1
- navModel: bottom-bar, focusTreatment: scale
- motionLevel: medium, typeScale: 1.0
- rowHeight: 190, cardAspect: 0.5625

### 5. HBO Max
- canvas: #150524, accents: #9945ff
- navModel: left-bar, focusTreatment: lift
- motionLevel: high, typeScale: 1.02
- rowHeight: 185, cardAspect: 0.7

### 6. Paramount+
- canvas: #06080d, accents: #0064ff
- navModel: pill, focusTreatment: glow
- motionLevel: medium, typeScale: 0.98
- rowHeight: 175, cardAspect: 0.65

### 7. Plex
- canvas: #131721, accents: #e5a00d
- navModel: left-bar, focusTreatment: scale
- motionLevel: low, typeScale: 1.0
- rowHeight: 195, cardAspect: 0.7

### 8. Crunchyroll
- canvas: #120a3b, accents: #fa861b
- navModel: left-bar, focusTreatment: lift
- motionLevel: high, typeScale: 1.05
- rowHeight: 210, cardAspect: 0.75

### 9. Kodi Modus
- canvas: #000000, accents: #ff6600
- navModel: pill, focusTreatment: ring
- motionLevel: low, typeScale: 0.9
- rowHeight: 160, cardAspect: 0.5

### 10. Frame61 (Standard)
- canvas: #0d0e12, accents: #5b6aff
- navModel: bottom-bar, focusTreatment: scale
- motionLevel: medium, typeScale: 1.0
- rowHeight: 180, cardAspect: 0.6

### 11. Custom
- canvas: #000000, accents: #888888
- navModel: bottom-bar, focusTreatment: scale
- motionLevel: medium, typeScale: 1.0
- rowHeight: 180, cardAspect: 0.5625
- Leer-Skin für benutzerdefinierte Anpassung

## Reihen-Layout

| Reihen | Beschreibung |
|--------|-------------|
| Billboard | Haupt-Banner mit hero image |
| ContinueWatching | Fortsetzungs-Reihe |
| BecauseYouWatched | Empfehlungen |
| Top10 | Top-10-Rangliste |
| NewEpisodes | Neue Episoden |
| GenreRail | Genre-Reihe |
| Collection | Sammlung |
| FreeLegal | Legal-free Quellen |
| AiringToday | Heute verfügbar |
| Random | Zufällige Titel |
| SourceRail | Nach Quelle sortiert |
| Marathon | Marathon-Wahl |

## Nutzung

```kotlin
val skinManager = SkinManager.getInstance()
val skin = skinManager.getSkin("netflix")
// Oder benutzerdefiniert:
val customSkin = skinManager.getSkin("custom")
```

Profil speichert layoutId:
```kotlin
val profile = Profile(
    id = "user1",
    name = "Max",
    pinHash = "abc123",
    skinId = "netflix",
    layoutId = "default"
)
```

---

*SKINS.md erstellt am 2026-11-14.*
