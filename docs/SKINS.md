# SKINS.md — Frame61 Skin-System

Stand: 2026-11-16. Beschreibt den tatsächlichen Code (`SkinManager`,
`SkinJsonParser`, `DefaultAssetSkinManager`, `FileSkinManager`).

## Übersicht

Jeder Skin ist eine JSON-Datei `app/src/main/assets/skins/<id>.json` und wird
zur Laufzeit geparst. Kein Screen kennt einen Skin beim Namen; Screens lesen
Tokens über `LocalSkin` (`core/design`).

### Klassen

- `SkinManager` (Interface): `getSkin(id)`, `getAllSkins()`,
  `getAvailableSkins()`, `reload()`.
- `DefaultAssetSkinManager` — liest `assets/skins/*.json`, parst alle Dateien,
  fällt bei einer kaputten Datei pro Skin auf `DefaultFrame61Skin` zurück.
- `FileSkinManager` — dasselbe vom Dateisystem (Tests, Source-Lab-Vorschau).
- `SkinJsonParser` — `parse(content)` → `Frame61Skin`; `displayName(id)`,
  `parseMotionLevel(value)`, `color(hex)`.
- `SkinViewModel` (app) — hält den aktiven Skin; Auswahl landet in DataStore,
  `Frame61Theme(skin = …)` rendert die App.

### Skin-Tokens

| Token | Typ | Beschreibung |
|-------|-----|-------------|
| canvas | String (HEX) | Hintergrundfarbe |
| surfaces | String (HEX) | Oberflächenfarbe |
| accents | String (HEX) | Akzentfarbe |
| text | String (HEX) | Textfarbe |
| radius | Int | Border-Radius (dp) |
| cardAspect | Float | Seitenverhältnis der Karten |
| rowHeight | Int | Reihen-Höhe (dp) |
| gutter | Int | Abstand zwischen Reihen (dp) |
| navModel | String | Navigations-Modell (bottom-bar, left-bar, pill) |
| focusTreatment | String | Fokus-Behandlung (scale, lift, glow, ring) |
| motionLevel | String | Animations-Stufe (none, low, medium, high) |
| typeScale | Float | Typo-Skalierung |
| billboardHeight | Int | Billboard-Höhe (dp) |
| detailLayout | String | Detail-Layout (vertical, horizontal) |
| playerChrome | String | Player-Chrome (dark, light, minimal) |

Unbekannte Tokens werden ignoriert (`ignoreUnknownKeys = true`); ein
fehlendes Token behält seinen Default.

## Vordefinierte Skins (assets/skins/)

`netflix`, `disneyplus`, `appletv`, `primevideo`, `hbomax`, `paramount`,
`plex`, `crunchyroll`, `kodimodus`, `frame61`, `custom` — plus
`frame61-indigo` (interne Variante). `custom` ist die leere Vorlage für
eigene Anpassungen.

Werte bitte **den JSON-Dateien** entnehmen, nicht dieser Tabelle — die Dateien
sind die Quelle der Wahrheit (`SkinJsonParser` parst sie 1:1).

## Reihen-Layout (Layout-Builder)

| Reihen | Beschreibung |
|--------|-------------|
| Billboard | Haupt-Banner (immer erste Zeile) |
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

Der Layout-Builder (`LayoutBuilderScreen`) wählt die Reihen; gespeichert wird
die Auswahl **pro Profil** als `layoutId` (kommagetrennte `RailKind.key`-Liste,
siehe `LayoutPrefs`). Home liest das aktive Profil.

## Nutzung

```kotlin
// Theme folgt dem in DataStore gespeicherten Skin:
val skinViewModel: SkinViewModel = hiltViewModel()
Frame61Theme(skin = skin) { … }

// Skin direkt laden (Tests, Vorschau):
val skin = DefaultAssetSkinManager(context).getSkin("netflix")
```

Ein Profil hält `skinId` und `layoutId`:

```kotlin
Profile(id = 1, name = "Max", pinHash = "", allowedSources = "",
        skinId = "netflix", layoutId = "default")
```

---

*SKINS.md rewritten 2026-11-16 (actual class names, profile layout persistence).*