# STATE.md — Frame61 Status

## Stand: 2026-11-16

Kurzfassung: Fundament, Skins, Branding, TV-Home, Detail, Player und Watchdog
stehen. Gate grün mit **50 Tests**. Am Gerät nur P3 vollständig belegt — P4 ist
durch den gesperrten A52 blockiert.

---

## Was läuft (belegt)

| Schritt | Inhalt | Beleg |
|---------|--------|-------|
| M0 | 11 Module, Room (6 Entities, v2), Hilt, Theme, TV-Fokus, Manifest | Gate grün |
| M1 | PackLoader, PackInterpreter (html + api), Fixture-Runner, Source Lab | 5 Tests |
| M2 | 9 legal-freie Quellen mit Fixture, Katalog mit 46 echten Titeln | 6 + 6 Tests |
| M3 | HosterResolver, Mirror-Failover, 8-s-Watchdog, 9 Hoster-Packs | 20 Tests |
| P0 | `:oscardp` entfernt, tote Reflection-Dateien gelöscht, Fokus repariert | 6 Tests |
| P1 | Skins werden aus JSON gelesen und **vom Theme benutzt** | 5 Tests |
| P2 | Brand-Assets (Icon, Banner, Logo, Farben) eingebunden | am Gerät belegt |
| P3 | TV-Home mit Billboard + Rails aus dem Katalog | A52 PASS, Bilder |
| P4 | Detail, Player, Resume, Autoplay-Countdown, Watchdog | 8 Tests, Gerät BLOCKED |

## Build-Gate

| Befehl | Status |
|--------|--------|
| `:app:assembleDebug` | ✅ |
| `:app:lint` | ✅ |
| Unit-Tests (7 Module) | ✅ **50 Tests, 0 Fehler, 0 übersprungen** |

Letzter Lauf: contabo-Build `frame61-p4`, `exit_code: 0`, BUILD SUCCESSFUL.

## APK-Artefakt

- **Datei:** `debug-app-debug.apk`
- **Größe:** 23 MB
- **SHA-256:** `1344f3cb912cd61a281dabd28222556fb026678c3e39535a50200e56eb768174`
- **Pfad:** `~/hendler-build-lab/artifacts/frame61-p4/debug-app-debug.apk`
- **Grenze:** Debug-Bau, kein signiertes Release, keine Store-Aussage

## Geräte-Nachweise (Samsung A52 · SM A525F · Android 14)

| Lauf | Suite | Verdikt | Was es zeigt |
|------|-------|---------|--------------|
| `job-e400af84cac346ca` | QUICK_SMOKE | PASS | erste lauffähige Fassung |
| `job-f026bdd46c1d44a3` | QUICK_SMOKE | PASS | **Skin verdrahtet**: Ring `#FFBCA4` 0,556 %, Default-Indigo 0,475 % → 0,001 % |
| `job-b919fa8047fc4753` | QUICK_SMOKE | PASS | P4 installiert + startet ohne Crash; **aber** `mCurrentFocus=NotificationShade`, `resumed: (leer)` |

`evidence_scope` im Original: „Physical PASS applies only to SM A525F / Android 14
(SDK 34). It is not API-level, OEM or form-factor coverage."

**Am Gerät belegt (P3):** UI-Hierarchie mit echten Katalogtiteln („Stranger Things",
„Top 10", „Neue Folgen", „Shōgun", „Fallout"), 88 158 Farben, Marken-Canvas
`#060913` 20,7 %, Fokus auf dem Billboard, Ring in Markenfarbe.

**BLOCKED (P4):** Wiedergabe, Resume und Watchdog sind am Gerät **nicht** belegt.
Der A52 liegt im gesicherten Keyguard; Screenshots zeigen den Sperrbildschirm
(Marken-Canvas 0,08 %). Es fehlt die Owner-Entsperrung des Geräts.

## Offen

1. **P4-Gerätenachweis** — braucht entsperrten A52: Play → Kill → Resume, Watchdog.
2. **Coil-Härtung** (RGB_565, 512-MB-Disk-Cache) fehlt; die Builder-API von Coil 3.1
   wurde nicht verifiziert, daher Default-Loader (im Code dokumentiert).
3. **Live-Suche** (TVmaze/Wikipedia) hat Code, ist aber nicht am Gerät getestet.
4. **Live-Nachweis** für ARD, ZDF, Arte, 3sat, KiKa, Pluto TV, Netzkino, Rakuten Free
   (API ohne Auth 404/401; Fixtures sind Strukturmodelle).
5. **Live-Nachweis** für die 9 Hoster-Packs (alle `verified: false`).
6. **WebView-Captcha am Gerät**: Code vorhanden, `NOT_RUN`; Hoster-Hosts fehlen in
   `network_security_config`.
7. **Grauzone-Tier-A** (streamkiste, filmpalast, s.to, cine.to, aniworld, kinokiste,
   megakino, dokujunkies, movie4k, hdfilme, kinox, bs.to, filmfans, anime-loads)
   bleiben Attrappen und `UNVERIFIED` — keine echten Selektoren, keine Recherche.
8. **Signiertes Release-APK**: Signing-Config existiert, Keystore/Secrets nicht
   übertragen → `BLOCKED_EXTERNAL`.
9. **Room-Migration 1→2** im Code, aber nicht als Instrumentierungstest belegt.
10. **`docs/AUDIT.md`** ist veraltet (nennt eine gelöschte `Resolver.kt`, falsche
    Datei- und Testzahlen).
11. **Bauhost-Platte**: `contabo-build` lief mehrfach knapp unter 10 GiB (Schwelle im
    `remote-build.sh`), weil `hendler-build-lab/projects` 64 GB fremder Projekte
    belegt. Ich habe **nur eigene** `frame61-*`-Verzeichnisse geräumt und gewartet;
    fremde Jobs und Daten blieben unangetastet.

## Bau-Hinweise (Lehren aus dieser Runde)

- **Lokale Bauten sind per Owner-Policy gesperrt** → alles über
  `~/hendler-build-lab/remote-build.sh <job> "<tasks>"`.
- **`remote-build.sh` bricht still mit exit 75 ab**, wenn Platte < 10 GiB oder RAM
  < 4 GiB. Vor jedem Lauf `df -Pk` und `lsof build.lock` prüfen; bei Fremd-Lock
  warten, nie killen.
- **`androidx.tv:foundation:1.0.0` hat keine Lazy-Listen** (AAR: 3 Klassen).
  Verwendet: `androidx.compose.foundation.lazy.*` + `Modifier.focusGroup()`, Karten
  aus `androidx.tv:material3`.
- **Kotlin-`@OptIn` erfüllt den AndroidX-Lint-Check nicht**; er verlangt
  `androidx.annotation.OptIn`.
- **Lint-Reports können veraltet sein** — bei widersprüchlichen Meldungen immer den
  Task-Log (`logs/<job>.log`) lesen, nicht den Report.
- **A52-Fokusfalle**: `mCurrentFocus=NotificationShade` heißt, die App ist nicht
  vorn; Screenshots sind dann wertlos. Vor Captures `HOME`/`BACK`, sonst sieht man
  den Sperrbildschirm statt der App.

---

*Stand 2026-11-16. Gate grün (50 Tests). P3 am Gerät belegt, P4 device-blocked.*