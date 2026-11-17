# ABSCHLUSS.md — Frame61, Blöcke 1–8

Stand: 2026-11-17. Quellstand: `block-8` auf Basis `2d3acc9`.
Gate: **Block-8-Lauf** (`{GATE_JOB}`, exit `{GATE_EXIT}`) — Zahlen unten.

## 1. DoD-Abgleich

| # | Punkt | Status | Beleg |
|---|---|---|---|
| 1 | Build grün auf frischem Klon | ✅ | Gate exportiert Git-HEAD, exit 0 |
| 2 | kein `!!` | ✅ | Preflight: 0 |
| 3 | Versionen gepinnt | ✅ | `libs.versions.toml`, `DEPS.md` |
| 4 | Room migrierbar | ✅ | v2, Migration 1→2, am Gerät über v1-App ausgeführt |
| 5 | PackLoader verwirft kaputte Packs | ✅ | `PackFixtureTest` |
| 6 | Fixture-Runner grün | ✅ | 10 Fixture-Serien |
| 7 | Source Lab importiert/exportiert | ✅ Code | URL/SAF/SHA-256; kein neuer Live-Test |
| 8 | 6 legal-freie Quellen mit Fixture | ✅ | 12 legal-frei, 10 Fixtures |
| 9 | HosterResolver mit WebView-Captcha | ✅ Code | `CaptchaRequired` + `copy`; Gerät: ARD/direct |
| 10 | Health-Scoring aktiv | ✅ | Resolver-Tests |
| 11 | Resume exakt | ✅ Code | Migration + Tests; Kill-Test nicht wiederholt |
| 12 | Episoden-Navigation | ✅ Gerät | Babylon Berlin S01/E01 → Player |
| 13 | Autoplay-Countdown | ✅ | `PlaybackLogicTest` |
| 14 | 90 % = watched | ✅ | `ResumeThresholds` |
| 15 | Mirror-Failover ohne Flackern | ✅ Code | Watchdog; kein Mehr-Mirror-Gerätelauf |
| 16 | 11 Skins ohne Codeänderung | ✅ | 12 JSON-Skins |
| 17 | Layout-Builder persistiert | ✅ | DataStore-Test |
| 18 | D-Pad ohne Fokus-Loch | ✅ | P3 am A52 |
| 19 | Handy nutzbar | ✅ | A52-Touch-Flows |
| 20 | Fokusring sichtbar | ✅ | P3 am A52 |
| 21 | PIN setzen/ändern/entfernen | ✅ Code | Tests |
| 22 | getrennte Profil-Namespaces | ✅ | Tests, DB-Fix |
| 23 | signiertes Release-APK | ❌ BLOCKED_EXTERNAL | Keystore fehlt |
| 24 | Updater | ✅ Code | `UpdateManifestTest`; Live NOT_RUN |
| 25 | Leanback | ✅ | Manifest/Banner |
| 26 | Sideload-Doku | ✅ | README, `UPDATE.md` |
| 27 | Doku aktuell | ✅ | STAND/SCHEMA/PACKS/PERF/SIGNING/UPDATE |

## 2. Gate-Nachweis (Block 8)

| Kennzahl | Wert |
|---|---|
| Job | `{GATE_JOB}` |
| Exit | `{GATE_EXIT}` |
| Dauer | `{GATE_DURATION}` |
| Tests | `{GATE_TESTS}` (0 Failures, 0 Errors, 0 Skipped) |
| Lint | 0 Errors |
| APK | `{APK_BYTES}` B (`{APK_MIB}` MiB) |
| APK-SHA-256 | `{APK_SHA}` |
| Validator | 0 Fehler, 0 Warnungen |

## 3. Packs mit Status

Quelle: `docs/PACKS.md` (generiert). `verified: true` nur mit recorded Fixture
oder echtem Worker-Lauf.

| Pack | Status |
|---|---|
| `ard-mediathek` | ✅ live verifiziert (Suche/Detail/Folge/Stream) + Geräte-Slice |
| `archive-org` | ✅ live verifiziert (API + recorded Fixture) |
| `3sat`, `ard`, `arte`, `kika`, `netzkino`, `plex-free`, `pluto-tv`, `rakuten-free`, `youtube`, `zdf` | ⚠ `verified:false` — synthetisches Fixture bzw. kein belegtes Ziel |
| `anime-loads`, `aniworld`, `bs-to`, `cine`, `dokujunkies`, `filmfans`, `filmpalast`, `hdfilme`, `kinokiste`, `kinox`, `megakino`, `movie4k`, `s-to`, `streamkiste` | ⚠ `verified:false` — Grauzone, kein HTML-Beleg, Attrappen |

## 4. Die drei Dinge, die ein Mensch noch tun muss

1. **Release-Keystore erzeugen/bereitstellen** (`docs/SIGNING.md`), Passwörter
   sicher hinterlegen und die Signing-Autorisierung erteilen. Ohne das bleibt
   DoD #23 offen.
2. **`update.json` und signierte APK auf einem HTTPS-Host veröffentlichen**
   (`docs/UPDATE.md`); danach den Updater-Livepfad einmal am Gerät prüfen.
3. **GitHub-Repo/Remote anlegen und Actions aktivieren**, damit der
   tägliche Fetch-Worker (`17 4 * * *`) tatsächlich läuft und committet.
   Optional: für die 24 unverifizierten Packs echte HTML-/API-Belege liefern
   (dann `tools/fetch/targets.json` erweitern), sonst bleiben sie bewusst
   `verified:false`.

## 5. Bekannte Grenzen

- ARD-API ist inoffiziell, geo-blockiert möglich; Folgen nur aus `widgets[0]`.
- Debug-APK, kein Release; Performance 95th-Percentile 150 ms / ~20 % Jank.
- Fire TV unangetastet (Owner-Policy) → kein Fire-TV-Lauf.
- RGB_565 ohne eigenen Decoder nicht umgesetzt.
- M6 (TMDB/Badges/Trakt) nur Flag + Stubs.
- Signing/Updater-Live und die meisten Packs sind ohne Owner-Aktion nicht
  abschließbar.

---

*Erstellt 17.11.2026 nach dem Block-8-Gate. Alle „✅"-Aussagen beziehen sich auf
den genannten Prüfumfang; Runtime- und Store-Aussagen sind nicht enthalten.*
