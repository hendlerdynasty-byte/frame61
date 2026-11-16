# STATE.md — Frame61 Status

## Stand: 2026-11-16 (Abend)

Kurzfassung: P5 ist umgesetzt und der **volle Gate grün** — 81 Tests, 0 Fehler,
Lint 0 Errors, signierter Debug-APK mit Hash. Am Gerät ist P3 belegt; P4/P5
(Wiedergabe, Resume, Watchdog, Episoden, Skins, Layout) sind durch den
gesperrten A52 **BLOCKED**. Die Grauzone bleibt Attrappen/UNVERIFIED.

---

## Was läuft (belegt)

| Schritt | Inhalt | Beleg |
|---------|--------|-------|
| M0 | 10 Module, Room (6 Entities, **Version 1**, exportSchema), Hilt, Theme, TV-Fokus, Manifest | Gate grün, `Frame61Database` v1 |
| M1 | PackLoader (Assets + internes `sources/`), PackInterpreter (html+api), Fixture-Runner, Source Lab mit Import/Export | `PackLoader.loadAllPacks`, `PackFixtureTest` 5/5 |
| M2 | 25 Source-Packs (davon 14 Grauzone = Attrappen, `verified:false`), 9 Fixture-Serien, Katalog 46 Titel | `assets/sources/*.json`, `fixtures/` |
| M3 | HosterResolver (9 Hoster-Packs), MirrorSelector, 8-s-Watchdog | `HosterResolverTest` 7/7, `PlaybackWatchdogTest` 5/5 |
| P0 | `:oscardp` entfernt, tote Reflection-Dateien gelöscht, Fokus repariert | `HomeFocusTargetTest` 6/6 |
| P1 | Skins aus JSON, vom Theme benutzt | `SkinJsonParserTest` 5/5, 12 Skin-Dateien |
| P2 | Brand-Assets (Icon, Banner, Logo, Farben) | am A52 belegt (P3-Lauf) |
| P3 | TV-Home mit Billboard + Regalen | A52 PASS (Bilder, 88 158 Farben, Marken-Canvas 20,7 %) |
| P4 | Detail, Player, Resume, Autoplay-Countdown, Watchdog | `PlaybackLogicTest` 8/8; Gerät BLOCKED |
| P5 | NavBar (6 Ziele), Suche, Skins, Profile/PIN, Player-Spuren, Layout-Builder, Episoden (TVmaze), Source-Lab-Import/Export, Updater, Coil-Cache | Gate grün; Gerät BLOCKED |

## Build-Gate (voll)

| Befehl | Status |
|--------|--------|
| `:app:assembleDebug` | ✅ |
| `:app:lint` | ✅ **0 Errors, 124 Warnings** |
| `testDebugUnitTest` (alle Module) | ✅ **81 Tests, 0 Fehler, 0 übersprungen** |

- **Job:** `frame61-gate3` · `exit_code: 0` · BUILD SUCCESSFUL in 7m 37s
- **Quellstand:** `4e83c41d39358a9d0c37b3530fa7651745fac577` (sauberer HEAD,
  isoliertes Snapshot-Repo `frame61/.git`, kein Monorepo-Commit)
- **Bauweg:** `python3 ~/hendler-device-ops/remote_build.py --repo <frame61>
  --tasks ":app:assembleDebug :app:lint testDebugUnitTest"` auf `contabo-build`
  (JDK17/SDK36, Serverlease, 2 Worker, 2 GiB Heap)

## APK-Artefakt

- **Datei:** `debug-app-debug.apk`
- **Größe:** 24.487.318 Bytes (23,4 MB) — Grenze 45 MB eingehalten
- **SHA-256:** `e6945977ad73cf4caad2b8cc3ec927b7e5c5005d70a06157635dc711150e06f4`
  (lokal gegen Manifest nachgehasht: identisch)
- **Pfad:** `~/.hendler-dynasty/device-ops/remote-builds/frame61-gate3/debug-app-debug.apk`
- **Grenze:** Debug-Bau, kein signiertes Release, keine Store-Aussage

## Geräte-Nachweise (Samsung A52 · SM A525F · Android 14)

| Lauf | Suite | Verdikt | Was es zeigt |
|------|-------|---------|--------------|
| `job-e400af84cac346ca` | QUICK_SMOKE | PASS | erste lauffähige Fassung |
| `job-f026bdd46c1d44a3` | QUICK_SMOKE | PASS | Skin verdrahtet (Ring `#FFBCA4` 0,556 %, Default 0,001 %) |
| `job-b919fa8047fc4753` | QUICK_SMOKE | PASS | P4 installiert + startet ohne Crash; Fokus auf NotificationShade |

**Am Gerät belegt (P3):** UI-Hierarchie mit echten Katalogtiteln, Marken-Canvas
`#060913` 20,7 %, Fokus auf dem Billboard, Ring in Markenfarbe.
**BLOCKED (P4/P5):** Wiedergabe, Resume, Watchdog, Episoden, Skins, Layout,
PIN — der A52 liegt im gesicherten Keyguard; Owner-Entsperrung nötig
(`OWNER_UNLOCK_REQUIRED`). Keine neuen Geräteläufe in dieser Runde.

## Offen

1. **Geräte-Abnahme P4/P5** — BLOCKED (A52-Keyguard): Play → Kill → Resume,
   Watchdog, Episodenliste, Skins/Layout/PIN am Gerät.
2. **RGB_565-Bitmap-Konfiguration** — BLOCKED belegt: Coil 3.1.0 hat **keine**
   öffentliche API (javap gegen die 3.1.0-Artefakte: kein `bitmapConfig` an
   `ImageLoader.Builder`/`ImageRequest.Builder`, `BitmapFactoryDecoder` final).
   Ein eigener `Decoder.Factory` wäre nötig; ohne Gerät nicht belegbar.
3. **Coil-Cache-Wirkung** — Memory 25 % + Disk 512 MB implementiert (API per
   javap verifiziert, `Frame61Application`), Wirkung nicht gemessen (kein Gerät).
4. **Live-Nachweis legal-freie Quellen** (ARD, ZDF, Arte, 3sat, KiKa, Pluto,
   Netzkino, Rakuten, Plex, YouTube, Archive.org) — NOT_RUN: APIs liefern ohne
   Auth 404/401; Fixtures sind Strukturmodelle.
5. **Live-Nachweis Hoster (9)** — NOT_RUN, alle `verified:false`.
6. **WebView-Captcha am Gerät** — NOT_RUN; Code vorhanden (`CaptchaWebViewFrame`).
7. **Grauzone Tier A/B** (streamkiste, s.to, cine.to, filmpalast, aniworld,
   megakino, kinokiste, dokujunkies, movie4k, hdfilme, kinox, bs.to, filmfans,
   anime-loads) — Attrappen mit synthetischen Selektoren, `UNVERIFIED`;
   keine Recherche, keine echten Selektoren (verbindliche Quellenpolitik).
8. **Signiertes Release-APK** — BLOCKED_EXTERNAL: Keystore/Secrets nicht
   übertragen (Owner-Policy); Signing-Config existiert.
9. **Room-Migration** — Schema ist Version 1 mit `exportSchema`; eine
   Migration 1→2 ist damit nicht nötig, aber ab jeder Schemaänderung Pflicht.
10. **Performance** (Kaltstart <2,5 s, Row <100 ms, RSS <350 MB) — NOT MEASURED,
    braucht Gerät.
11. **Updater-Livepfad** — Code + Parser-Tests grün; ein echtes `update.json`
    wurde nicht ausgeliefert (NOT_RUN).
12. **`docs/DEPS.md`-Abweichung** `minSdk` (gradle.properties 22 vs. Catalog 23)
    — dokumentiert, nicht angeglichen (eigene Aufgabe).

## Bau-Hinweise (Lehren aus dieser Runde)

- **`remote-build.sh` hat drei still Exit-75-Punkte:** `flock` (REMOTE_BUSY),
  `MemAvailable < 4 GiB`, `df < 10 GiB`. Vor jedem Lauf alle drei prüfen;
  Fremd-Locks nie killen, Retry-Schleife nutzen.
- **`--reuse-job`** hält den Gradle-Cache im Job-Ordner; nach Kompilierfehlern
  aus dem Vorgängerstand lieber frischen Job nehmen.
- **`remote_build.py` bettet bei REMOTE_BUSY den alten Host-Log ins Fehlerbild
  ein** — die Schleife muss auf `exit 75` prüfen, nicht auf Log-Inhalte.
- **Dagger + Kotlin-Generics:** `List<MetadataSource>` wird im Bytecode zu
  `List<? extends MetadataSource>`; `@JvmSuppressWildcards` hilft im
  Hilt-generierten Java-Code nicht. Einzelquellen injizieren und die Liste
  selbst bauen.
- **`HashMap.putIfAbsent` ist API 24** → Lint `NewApi` bei minSdk 23;
  `MutableMap.getOrPut` verwenden.
- **`androidx.annotation.OptIn` ist vararg** → positional übergeben
  (`@androidx.annotation.OptIn(UnstableApi::class)`).
- **`JsonElement.jsonObject`/`.jsonPrimitive` werfen bei `JsonNull`** → bei
  echten APIs (z. B. `"image": null`) sichere Casts verwenden.
- **Kanonische Enum-Reihenfolge** schlägt jede „logische" Reihenfolge:
  `BECAUSE_YOU_WATCHED` steht vor `TOP10` (LayoutPrefs/Tests beachten).
- **Mimosa-Pre-Commit-Scan** lief in dieser Sitzung nur partiell
  (library_source/callgraph unavailable) — keine Sicherheitsaussage ableitbar.

---

*Stand 2026-11-16. Voller Gate grün (81 Tests, 0 Fehler, Lint 0E/124W). P3 am
Gerät belegt, P4/P5 device-blocked. HEAD 4e83c41, APK e6945977…*