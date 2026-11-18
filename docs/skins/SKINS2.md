# SKINS2.md — Theme-Engine und sechs Skins (Block 4)

Stand: 2026-11-18 · Build `9afd07f` (APK `a16cf233…`) · Gerät SM-A525F

## Theme-Engine

`Frame61Theme` liefert jetzt:

| Baustein | Inhalt |
|---|---|
| `ColorScheme` | canvas, surface, accent, text, onPrimary, focus |
| `Typography` | `screenTitle` 32 sp, Body min. 16 sp, Skin-`typeScale` |
| `Shapes` | small/medium/large aus dem Skin-Radius |
| `DensityTokens` | Posterhöhe, Gutter, Content-Padding |
| `FocusTokens` | Skalierung und Ringbreite (1.06–1.12 / 3–4 dp) |
| `HeroTokens` | max. Höhenanteil (≤ 0,4), Gradient-Regel (bottom/top/none), Stärke |

Die Bedienlogik bleibt unverändert: Skins ändern nur Optik, Dichte und
Fokus-Feedback. Der Fokus-Ring und die Skalierung kommen aus dem Skin
(`tvFocusable` liest `LocalSkin.current.focus`).

## Die sechs Skins

| Skin | Canvas | Accent | Radius | Poster | Fokus | Hero |
|---|---|---|---|---|---|---|
| Cinema Red | `#0B0505` | `#E11D2E` | 10 | 190 dp | 1.08 / 3 dp | 40 %, bottom 0.88 |
| Magic Blue | `#050A1A` | `#3B82F6` | 14 | 200 dp | 1.08 / 3 dp | 38 %, bottom 0.82 |
| Midnight Gold | `#080704` | `#D4AF37` | 12 | 190 dp | 1.06 / 3 dp | 36 %, bottom 0.90 |
| Arctic Light | `#F4F7FB` | `#0284C7` | 16 | 190 dp | 1.08 / 3 dp | 32 %, bottom 0.92 (hell) |
| Neon Grid | `#05050A` | `#00E5FF` | 4 | 180 dp | 1.12 / 4 dp | 40 %, bottom 0.78 |
| Kino Noir | `#0A0A0A` | `#C9C9C9` | 2 | 190 dp | 1.06 / 4 dp | 34 %, bottom 0.95 |

Die bisherigen zwölf Skin-Dateien bleiben im Repo (Kompatibilität/Tests);
**angeboten** werden unter Einstellungen → Darstellung genau diese sechs.

## Auswahl und Live-Anwendung

- `SettingsScreen` zeigt die sechs Skins als fokussierbare Buttons mit
  Aktivmarkierung; ein Klick schreibt die ID in DataStore
  (`SkinsViewModel.select` → `ProfileDataStore.setActiveSkin`).
- Der Shell-`SkinViewModel` beobachtet DataStore und lädt den Skin sofort neu;
  kein Neustart nötig.
- Nach dem Wechsel auf Neon Grid wurde die App beendet und neu gestartet:
  [persistence-after-restart.png](persistence-after-restart.png) zeigt weiterhin
  Cyan-Fokus und Neon-Radius — Persistenz belegt.

## Screenshots je Skin

| Skin | Einstellungen | Start |
|---|---|---|
| Cinema Red | [cinema-red-settings.png](cinema-red-settings.png) | [cinema-red-home.png](cinema-red-home.png) |
| Magic Blue | [magic-blue-settings.png](magic-blue-settings.png) | [magic-blue-home.png](magic-blue-home.png) |
| Midnight Gold | [midnight-gold-settings.png](midnight-gold-settings.png) | [midnight-gold-home.png](midnight-gold-home.png) |
| Arctic Light | [arctic-light-settings.png](arctic-light-settings.png) | [arctic-light-home.png](arctic-light-home.png) |
| Neon Grid | [neon-grid-settings.png](neon-grid-settings.png) | [neon-grid-home.png](neon-grid-home.png) |
| Kino Noir | [kino-noir-settings.png](kino-noir-settings.png) | [kino-noir-home.png](kino-noir-home.png) |

## Gefundener und behobener Defekt

Arctic Light (heller Skin) zeigte Kartentitel ohne lesbaren Hintergrund: Der
alte Scrim war hart auf Schwarz gesetzt. Der Karten-Scrim nutzt jetzt
`skin.colors.canvas` mit 90 % Deckkraft — helle Skins bekommen einen hellen
Scrim mit dunkler Schrift, dunkle Skins dunklen Scrim mit heller Schrift.
Belegt in [arctic-light-home.png](arctic-light-home.png).

Zusätzlich behoben: `SkinsViewModel.select` schrieb die Auswahl vorher **nicht**
in DataStore; die Auswahl wäre nach Neustart verloren gewesen. Jetzt ist
DataStore die einzige Quelle.

## Grenzen

- Screenshots auf dem A52 (Portrait); TV-Dichte 1,0 nicht separat geprüft.
- Die sechs Skins nutzen dieselbe Bedienlogik; Automatismen (Auto-Play,
  Hoster-Präferenz) bleiben unabhängig.
- Der Darstellungs-Picker liegt vorläufig in den Einstellungen; Block 5 baut
  den vollständigen Einstellungen-Baum.
