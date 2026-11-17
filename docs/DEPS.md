# DEPS.md — gepinnte Versionen

Stand: 2026-11-16. Verbindliche Quelle ist `gradle/libs.versions.toml`;
diese Datei dokumentiert den eingefrorenen Stand. Keine Version wird
„nach Gefühl" angefasst — ein Versionswechsel ist eine eigene Aufgabe mit
eigenem Gate-Lauf.

| Baustein | Version | Notiz |
|----------|---------|-------|
| Android Gradle Plugin | 8.9.1 | AGP |
| Kotlin | 2.1.20 | KSP `2.1.20-1.0.31` |
| compileSdk / targetSdk | 36 / 36 | minSdk 23 (gradle.properties: `frame61.minSdk=22`, toml: 23 — **Abweichung offen**) |
| Compose BOM | 2025.03.00 | UI, Material3, Tooling |
| Compose Compiler | 2.1.20 | `kotlinCompilerExtensionVersion` |
| androidx.tv | 1.0.0 | tv-material + tv-foundation; **keine Lazy-Listen** in 1.0.0 |
| Media3 | 1.6.0 | exoplayer, **exoplayer-hls** (HLS-Quelle; fehlte und crashte am A52), ui, common |
| Room | 2.7.0 | Schema v1, `exportSchema = true` |
| Hilt | 2.55 | KSP-Compiler |
| Coil | 3.1.0 | coil-compose + coil-network-okhttp |
| OkHttp | 4.12.0 | + logging-interceptor, okio 3.10.2 |
| Jsoup | 1.19.1 | HTML-Regeln |
| DataStore | 1.1.3 | preferences |
| WorkManager | 2.10.0 | vorgesehen (Prewarm) |
| kotlinx-serialization | 1.8.0 | JSON |
| Navigation Compose | 2.8.9 | |
| Lifecycle | 2.8.7 | runtime/viewmodel-compose |
| Activity Compose | 1.10.1 | |
| Material Components | 1.12.0 | XML-Theme |
| leanback | 1.0.0 | Leanback-Banner/Launcher |

## Regeln

- Abhängigkeiten **nur** über `libs.versions.toml`, keine hartkodierten
  Koordinaten in Modul-Build-Dateien.
- Keine neuen Abhängigkeiten ohne Eintrag hier und im Gate-Nachweis.
- Bekannte Fallen: `androidx.tv:foundation:1.0.0` hat keine Lazy-Listen
  (`androidx.compose.foundation.lazy.*` verwenden); Kotlin-`@OptIn` erfüllt
  den AndroidX-Lint nicht (`androidx.annotation.OptIn` benutzen); veraltete
  Lint-Reports können alte Fehler zeigen — Task-Log lesen.

## Offene Abweichungen

- `gradle.properties` nennt `frame61.minSdk=22`, der Version Catalog 23.
  Der Build nutzt den Catalog (23); die Properties sind Doku, keine
  Build-Vorgabe. Eine Angleichung ist eine eigene Aufgabe.
- `:sources:free` ist ein leeres Modul (nur `build.gradle.kts`). Die
  legal-freien Quellen leben als JSON-Packs + Fixtures in Engine/App; das
  Modul ist ein Platzhalter und könnte entfernt werden.