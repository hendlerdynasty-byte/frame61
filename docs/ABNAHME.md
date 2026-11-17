# ABNAHME.md — Geräteabnahme Block 2

Stand: 2026-11-17 · Gerät: Samsung SM-A525F (A52), Android 14 (SDK 34),
Serie `RZ8T703XJWF`, USB · APK: `debug-app-debug.apk`,
SHA-256 `64398e0c08c7eb666c002de3491eaf823471fa2961a995ea85ebb70260c4d627`
(Quellstand `5be8129`).

Vorgehen: Installation über die zentrale Device-Lab-Queue (`device-lab submit`,
Suite `QUICK_SMOKE`), danach interaktive Bedienung über die Gerätebrücke
(`device-bridge`, Aktionen `launch/tap/wait/screenshot/ui/logcat`). Screenshots
und UI-Dumps liegen im jeweiligen Evidenzordner; der Crash-Stack wurde aus dem
Crash-Buffer gelesen.

---

## 1. Installation und Start

| Punkt | Vorgehen | Ergebnis |
|---|---|---|
| Installation | `device-lab submit … --suite QUICK_SMOKE` (Job `job-ee3adbfb4535470d`) | **PASS** — `install_update rc=0`, Paket installiert |
| Start | gleicher Job, `focus:startup` + `screenshot:startup` | **PASS** — `MainActivity` im Vordergrund, kein Fatal im Job-Fenster |
| Startbildschirm | Kaltstart + Screenshot (`ev5/04.png`) | **PASS** — Billboard (Stranger Things), Regale „Top 10" und „Neue Folgen", NavBar sichtbar |

Der QUICK_SMOKE-Job prüft **nur** Start und Crash-Fenster; er hat den
Player-Crash nicht gesehen (er tritt erst beim Öffnen des Players auf).

## 2. Navigation (Defekt gefunden, Fix gebaut, Nachtest offen)

| Punkt | Vorgehen | Ergebnis |
|---|---|---|
| Tab „Suche" | Tap auf NavBar „Suche" | **PASS** — Suchscreen rendert (Feld, Quellenzeile, NavBar) |
| Tab „Start" | Tap auf „Start" von der Suche | **FAIL** — No-op: `onNavigate` ließ die Home-Route aus, der Tab tat nichts |
| NavBar-Label | Sichtprüfung | **FAIL** — „Einstellungen" brach in zwei Zeilen um („Einstellunge / n") |
| Überschrift-Kontrast | Sichtprüfung Suche/Detail | **FAIL** — Überschrift und Fließtext schwarz auf dunklem Canvas (kein `LocalContentColor`) |

Fixes (Code, noch nicht neu gebaut): `NavBar` navigiert für `home` per
`popBackStack("home")`; Label „Optionen" statt „Einstellungen";
`Frame61Theme` liefert `LocalContentColor = skin.colors.textPrimary`.

## 3. Katalogliste und Detailseite

| Punkt | Vorgehen | Ergebnis |
|---|---|---|
| Katalogliste | Home-Screenshot, UI-Dump | **PASS** — echte Katalogtitel mit Postern, fokussierbare Karten |
| Detailseite | Tap auf „Breaking Bad" | **PASS** — Titel, Jahr, Genres, Beschreibung, „Abspielen", „Ähnliche suchen", Folgenliste |
| Folgen | TVmaze-Liveabruf im Detail | **PASS** — 62 Folgen (S01 E01 …), Live-Netz funktioniert |
| Beschreibung | Sichtprüfung | **FAIL** — rohes HTML (`<b>Breaking Bad</b> …`) wurde als Text gerendert |

Fix: `CatalogRepository` entfernt Markup beim Laden; neuer Test
`overviews are plain text`.

## 4. Player-Übergang (Crash gefunden, Fix gebaut, Nachtest offen)

| Punkt | Vorgehen | Ergebnis |
|---|---|---|
| „Abspielen" | Tap auf den Button | **FAIL (Crash 1, behoben)** — App wird geschlossen, Systemdialog „Fehler bei Frame61 aufgetreten" |
| Ursache 1 | Crash-Buffer (`adb logcat -d -b crash`, leerer Device-Lease) | `java.lang.IllegalStateException: ClassNotFoundException: androidx.media3.exoplayer.hls.HlsMediaSource$Factory` in `Media3Frame61Player.open` (PlayerEngine.kt:81) |
| Nachtest | neuer Build `7544ac2`, erneuter Tap | **FAIL (Crash 2, behoben)** — App schließt ohne Dialog |
| Ursache 2 | Crash-Buffer nach Nachtest | `SQLiteConstraintException: FOREIGN KEY constraint failed (787)` in `PlaybackStateDao.upsert` — `profileId=0` existiert nicht in `profiles` |
| Zusatzbefund | Code-Analyse | `PlaybackState.id` war immer 0 → REPLACE löschte den vorherigen Titel; Resume kannte nur einen Eintrag |
| Fixes | – | `media3-exoplayer-hls`; `PlayerEngine.open` fängt Fehler; Default-Profil wird lazy angelegt; deterministische Resume-Row-ID; Index `(profileId,titleId,episodeId)` + Migration 1→2 |

## 5. Weitere Screens

| Screen | Ergebnis |
|---|---|
| Suche | **PASS (Render)** — Texteingabe über die Brücke nicht möglich (kein `type`-Op); Suchfunktion nur per Unit-Test, Geräte-Texteingabe **NOT_RUN** |
| Skins / Quellen / Profile / Optionen | nach dem Rebuild nachzuziehen (Block 2 Nachtest) |

## 6. Offene Punkte bis Block-2-Abschluss

1. Zweiten Rebuild (`./gradlew gate` remote) mit HLS- und Room-Fix bauen,
   APK installieren (überschreibt die v1-Datenbank → Migration 1→2 wird real
   ausgeführt).
2. Start-Tab, Label, Kontrast, Detail-Markup und Player-Übergang erneut prüfen.
3. Skins-, Quellen-, Profil- und Options-Screen einmal öffnen.
4. Erst danach Block 2 als grün führen.

**Bedienhinweis:** Nach einem App-Absturz landet der Tap auf der NavBar-Leiste
im Launcher bzw. Play Store. Vor jedem Tap muss per UI-Dump geprüft werden, dass
`de.hendler.frame61` im Vordergrund ist — sonst werden fremde Apps bedient
(am 17.11. einmal passiert, keine Käufe/Installationen ausgelöst).

---

*Zwischenstand 17.11.2026, 15:20. Kein Block-2-PASS, solange der Nachtest
nicht gelaufen ist.*
