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

**Nachtest 2 (Build `f43802d`):** „Start" führt von jeder Seite zurück auf Home;
die NavBar zeigt „Start · Suche · Skins · Quellen · Profile · Optionen" in einer
Zeile. Suche, Skins, Source Lab, Profile und Einstellungen wurden geöffnet
(UI-Dumps 25/29/33/37).

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
| Nachtest 2 | Build `f43802d`, Player geöffnet | **FAIL (kein Crash, aber kein Bild)** — nach 8 s „Alle Mirrors ausgefallen"; Zurück landet auf Home statt Detail |
| Nachtest 3 | Build `16dbb98`, Player 14 s geöffnet | **PASS** — Video sichtbar (Big-Buck-Bunny-Demo), Fortschritt `0:24 / 10:34`, Audiospur „Spur 1", Tempo 1,00x, Pause/±10 s; kein Fatal |
| Rückweg | `back` aus dem Player | **PASS** — Detailseite mit Folgenliste (kein Doppel-Pop mehr) |
| Ursache 3 | Code + Gerät | Watchdog wurde nur einmal beim Öffnen gefüttert und schlug nach 8 s zu, obwohl der Stream lief; `PlayerRoute` rief beim Dispose `popBackStack()` und nahm die Detailseite mit |
| Fixes 3 | – | Watchdog misst Fortschritt (`positionMs` ändert sich) statt Tickergeräusche; `DisposableEffect`/`onExit` entfernt, Zurück navigiert normal |
| Fixes 1+2 | – | `media3-exoplayer-hls`; `PlayerEngine.open` fängt Fehler; Default-Profil wird lazy angelegt; deterministische Resume-Row-ID; Index `(profileId,titleId,episodeId)` + Migration 1→2 |

## 5. Weitere Screens

| Screen | Ergebnis |
|---|---|
| Suche | **PASS (Render)** — Texteingabe über die Brücke nicht möglich (kein `type`-Op); Suchfunktion nur per Unit-Test, Geräte-Texteingabe **NOT_RUN** |
| Skins | **PASS** — Skin-Liste, „Aktiv: appletv", Auswahl sichtbar (Dump 25) |
| Quellen (Source Lab) | **PASS** — „Available Packs (25)", Importfelder, Pack-Liste (Dump 29) |
| Profile | **PASS** — „Profil wählen", Name/PIN, „Profil anlegen" (Dump 33) |
| Optionen | **PASS** — Autoplay, Gesehen-Schwelle 80/90/95 %, Layout, Update-URL (Dump 37) |

## 6. Gate und Abschluss Block 2

| Nachweis | Wert |
|---|---|
| Gate-Job | `frame61-16dbb98823b9-1794927880`, exit 0, BUILD SUCCESSFUL in 11m 53s, 451 Tasks |
| Quellstand | `16dbb98` (sauberer HEAD) |
| Tests | **86/86** grün (17 Klassen), 0 Failures, 0 Errors, 0 Skipped |
| Lint | `:app:lint` grün (0 Errors; Warnungszahl im Lint-Report) |
| APK | 24.208.533 B (23,1 MiB), SHA-256 `343680cb72a54ab74bfe1c49af5676e64f3ccdea574339d1353c02ea2dd4d6e9` |
| Installation | `device-lab` Job `job-405879e17c68402f`, `install_update rc=0`, kein Fatal |
| Player | Screenshot `frame61-block2-player2/12.png` (Video, 0:24/10:34) |
| Rückweg | UI-Dump `frame61-block2-player2/17.xml` = Detailseite |

**Grenzen:** Der Player spielt den öffentlichen Mux-Demo-HLS, nicht den Katalog-
Stream (der kommt in Block 6). Die Texteingabe im Suchfeld ist über die Brücke
nicht automatisierbar (kein `type`-Op) → **NOT_RUN**; die Suchlogik ist durch
Unit-Tests belegt. Der Upgrade-Pfad v1→v2 wurde durch die Installation über die
bestehende App real ausgeführt (App startete, DB öffnete, Resume schrieb).

**Bedienhinweis:** Nach einem App-Absturz landet der Tap auf der NavBar-Leiste
im Launcher bzw. Play Store. Vor jedem Tap muss per UI-Dump geprüft werden, dass
`de.hendler.frame61` im Vordergrund ist — sonst werden fremde Apps bedient
(am 17.11. einmal passiert, keine Käufe/Installationen ausgelöst).

---

*Block 2 abgeschlossen am 17.11.2026, 16:22. Gate grün, Gerät belegt.*
