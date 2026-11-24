# QUOTA.md — Titel-Erfolgsquote (Messharness und Baseline)

Stand: 23.11.2026. Methode und Zahlen für Version 1.5.0.

## Methode (1.5.0)

- **Zwei Stichproben, garantiert disjunkt:**
  - **Training** (`PlaybackQuotaActivity`, Seed `20261122`, 100 Titel),
  - **Holdout** (`PlaybackQuotaHoldoutActivity`, Seed `20261123`, 100 Titel aus
    dem Rest des Katalogs). Die Holdout-Stichprobe wird erst nach der
    Trainings-Stichprobe gefahren und nie zum Nachsteuern benutzt.
- **Katalog:** invertiert und nach Zielverteilung geerntet — 997 Titel aus den
  Quellen selbst: 35 % Serien von s.to/serienstream, 20 % Filme von
  filmpalast/moflix, 20 % Anime von aniworld, 15 % ARD als Sockel, 10 % bs.to
  (siehe `docs/ENTSCHEIDUNGEN.md`). Jeder Titel trägt `group` und `sourceKey`.
- **Quote je Quellengruppe:** Der Lauf weist die Quote **pro Gruppe** aus
  (`Gruppe | Titel | spielbar | Quote | häufigste Fehlerklasse`). Der
  Mischwert allein verdeckt, welche Gruppe trägt und welche nur den leichten
  Pfad zählt.
- **Kette:** derselbe Produktionsweg wie im Player —
  `AutoPlaybackResolver.resolveFast` → Kandidaten → bis zu drei mit
  `Media3Frame61Player`.
- **Zeiten getrennt:** `resolveMs` (Auflösung), `playMs` (Kandidat bis erstes
  Bild), `firstFrameMs` (Summe ab Tippen).
- **Fehlertaxonomie:** `NO_CANDIDATE`, `EMBED_UNRESOLVED`,
  `CONTAINER_UNSUPPORTED`, `GEO_BLOCKED`, `RATE_LIMITED`, `TIMEOUT` — die
  Klasse wird aus den Diagnose-Ereignissen des Resolvers abgeleitet, nicht
  geraten.
- **Auflösungsbudget:** 60 s je Titel. bs.to braucht bis zu ~28 s (Challenge +
  Klick in der versteckten WebView); mit 30 s wäre der Titel abgeschnitten.
- **Protokoll:** je Titel eine Zeile (idx, id, group, type, RESULT, class,
  resolveMs, playMs, candidates, host, intercept) im Logcat, im Statusbereich
  und in einer Datei; am Ende eine Zeile je Gruppe und die letzten
  Interception-Versuche mit Request-Kette.

### Belegregel

Nach **jedem** Block wird dieselbe Stichprobe mit demselben Seed gefahren und
das Delta festgehalten. Ein Block ohne Messung gilt nicht als abgeschlossen.

## Lauf 1 — Build `448109a`, 23.11.2026, A52 (nach Katalog-Neubau + bs.to-Regel)

Stichprobe: Training, Seed `20261122`, 100 Titel aus 997 Katalogeinträgen.
Dauer ~15 min. APK `84ebb997…` (signiert, `de.hendler.frame61.acceptance`).

| Kennzahl | Wert | Ziel 1.5.0 |
|---|---|---|
| Erfolgsquote (Mischwert) | **45 / 100 = 45 %** | ≥ 95 % |
| Auflösung p50 / p95 | 4.734 / **10.119** ms | p95 < 2.500 ms |
| Startzeit p50 / p95 | 3.367 / **4.172** ms | p95 < 3.000 ms |
| Fehlerklassen | 41 × `NO_CANDIDATE`, 14 × `CONTAINER_UNSUPPORTED` | 0 sichtbar |

Quote je Quellengruppe:

| Gruppe | Titel | spielbar | Quote | häufigste Fehlerklasse |
|---|---|---|---|---|
| serienstream | 43 | 24 | 55 % | `NO_CANDIDATE`: 16 |
| aniworld | 19 | 8 | 42 % | `NO_CANDIDATE`: 6 |
| ard | 10 | 5 | 50 % | `CONTAINER_UNSUPPORTED`: 3 |
| filmpalast | 11 | 3 | 27 % | `NO_CANDIDATE`: 6 |
| moflix | 11 | 3 | 27 % | `NO_CANDIDATE`: 7 |
| bsto | 6 | 2 | 33 % | `NO_CANDIDATE`: 4 |

**Befund — die Quote misst derzeit nur den Sockel.** Alle 45 Erfolge gingen
über ARD/ZDF/arte/kika/funk (`nrodlzdf-a.akamaihd.net`, `br-vod.ard-mcdn.de`,
`zdfvod.akamaized.net` …). **Kein einziger Index** (s.to/serienstream, bs.to,
aniworld, filmpalast, moflix) lieferte einen spielbaren Stream. Die Gruppen-
Quote ist damit keine Aussage über die Index-Quellen, sondern über die
Breite des ARD-Katalogs.

Ursachen aus dem Logcat desselben Laufs:

1. **34 × `hoster timeout`** (moflix 15, filmpalast 6, s-to 5, streamcloud 3,
   aniworld 3): Das Quellbudget (10 s) schnitt die Hoster-Kette ab, **bevor**
   die WebView-Interception (bis 20 s) fertig war. Das Budget umfasste nur
   den Quellenschritt, nicht die Hoster-Auflösung.
2. **bs.to:** 3 von 4 Titeln je 22–25 s ohne Kandidaten
   (`Frame61BsTo: keine Medien-URL für Surfside-Girls S1E1`). Die Kette
   hält an `ajax/embed.php` — die Seite verlangt ein reCAPTCHA-v2-Token
   (`6Ldd07og…`); ohne Token antwortet der Server `{"success":false}`,
   mit leerem Ticket `HTTP 400`.
3. **13 × `CONTAINER_UNSUPPORTED`** auf `devideosrc.co` — HTML-Ziel statt
   Medien-URL.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run1-448109a/`
(Rohzeilen `polls/final-lines.txt`, Logcat `polls/p*/01-logcat.txt`,
Endbild `A5-final/02.png`).

### Delta zu Lauf 0 (Baseline 1.4.0, `243971d`)

| | Lauf 0 | Lauf 1 | Delta |
|---|---|---|---|
| Quote | 6 % | 45 % | +39 pp |
| Startzeit p95 | 5.615 ms | 4.172 ms | −1.443 ms |
| Fehler 3003 | 38 | 14 | −24 |

Die Stichprobe ist ab hier **neu gezogen** (Katalog-Neubau mit 997 Titeln und
Zielverteilung). Ab Lauf 1 bleibt Sample + Seed unverändert, damit die
Folgeblöcke vergleichbar sind.

## Lauf 2 — Build `be45079`, 23.11.2026, A52 (Budget deckt Hoster + Interception)

Stichprobe: **identisch** zu Lauf 1 (Training, Seed `20261122`, 100 Titel).
Dauer 18:50–19:41 (~51 min). APK `77f679b5…` (signiert, `de.hendler.frame61.acceptance`).
Geänderte Basis gegenüber Lauf 1: Quellbudget 32 s (vorher 10 s), Hoster-Budget
26 s, **echte** Interception-Zeit und Request-Kette im Protokoll.

| Kennzahl | Wert | Lauf 1 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **53 / 100 = 53 %** | 45 % | +8 pp |
| Auflösung p50 / p95 | 4.585 / **27.186 ms** | 4.734 / 10.119 ms | p95 +17.067 ms |
| Startzeit p50 / p95 | 2.734 / **28.458 ms** | 3.367 / 4.172 ms | p95 +24.286 ms |
| Fehlerklassen | 35 × `CONTAINER_UNSUPPORTED`, 12 × `NO_CANDIDATE` | 14 / 41 | **+21 / −29** |

Quote je Quellengruppe:

| Gruppe | Titel | spielbar | Quote | Lauf 1 | häufigste Fehlerklasse |
|---|---|---|---|---|---|
| aniworld | 19 | 15 | **78 %** | 42 % | `CONTAINER_UNSUPPORTED`: 4 |
| serienstream | 43 | 25 | **58 %** | 55 % | `CONTAINER_UNSUPPORTED`: 12 |
| ard | 10 | 5 | 50 % | 50 % | `NO_CANDIDATE`: 3 |
| bsto | 6 | 3 | **50 %** | 33 % | `CONTAINER_UNSUPPORTED`: 2 |
| filmpalast | 11 | 3 | 27 % | 27 % | `CONTAINER_UNSUPPORTED`: 6 |
| moflix | 11 | 2 | **18 %** | 27 % | `CONTAINER_UNSUPPORTED`: 9 |

### Befund 1 — die Fehlerklasse ist gewandert, nicht verschwunden

Das Budget hat genau das bewirkt, wofür es gedacht war: Die 34 „hoster timeout"
aus Lauf 1 sind weg, `NO_CANDIDATE` fällt von 41 auf 12. **Aber** die 14
`CONTAINER_UNSUPPORTED` werden zu 35: Titel, die vorher gar keinen Kandidaten
lieferten, liefern jetzt einen — nur eben eine **HTML-Zielseite**, die der
Player nicht spielen kann. Zielhosts der Fehlschläge: `devideosrc.co` (20),
`moflix.rpmplay.xyz` (10), `uqload.vc` (5), `moflix-stream.fans` (4),
`click-v4.junimedclk.com` (2), `flyfile.app` (1).

### Befund 2 — alle 53 Erfolge kommen weiterhin aus dem legalen Sockel

Die OK-Hosts sind unverändert ARD/ZDF/BR/funk (`nrodlzdf-a.akamaihd.net` 13×,
`br-vod.ard-mcdn.de` 4×, `prx-1560-ant-t.vmpx.online` 2×, `wdradaptiv-vh…` 2× …).
**Kein Index** (s.to/serienstream, bs.to, aniworld, filmpalast, moflix) hat einen
spielbaren Stream geliefert. Der +8-pp-Sprung stammt aus mehr ARD-Treffern
(die längere Auflösung findet mehr Sockel-Kandidaten), nicht aus den Index-Quellen.
Das ist genau die Verzerrung, die die Nachtsteuerung verhindern will.

### Befund 3 — die WebView war die Engstelle, nicht die Zeit

88 Interception-Versuche auf 27 Titeln, **2** davon mit einer Medien-URL. Im
Logcat (14 Versuche im Fenster) gilt:

| | Anteil | Bedeutung |
|---|---|---|
| 0 Anfragen | **7 / 14** | Der Versuch kam **nie** in die WebView — er wartete auf die app-weite Sperre, bis das Quellbudget ihn abschnitt. |
| 30 Anfragen | 7 / 14 | Die Seite wurde geladen (VOE, burningseries), aber **kein Medienabruf** ausgelöst. |
| Medien-URL | **0 / 14** | Kein einziger Treffer. |

Drei Ursachen, jede einzeln belegt:

1. **Warteschlange statt Arbeit.** Die versteckte WebView ist app-weit
   serialisiert; bis zu neun Streams eines Titels stellten je einen eigenen
   Lauf hinein. Wer nicht mehr drankam, verbrauchte das Budget ohne Ergebnis.
   → Behoben in `WebViewBudget` (ein Versuch je Titel, mit Frist) und
   `QUEUE_WAIT_MS` (5 s) im Interceptor.
2. **VOE verlangt eine Session.** Die Embed-Seite lädt ihre 30 Requests, bleibt
   aber vor dem Player stehen. Der Gastweg öffnet ein Popup auf
   `https://voe.sx/api2/session/generate-token` — das antwortet mit
   **302 auf `/login`** (DDOS-Guard-Cookies, CSRF-Token). Ohne Konto gibt es
   diese Strecke nicht; laut Auftrag wird kein Konto geführt.
3. **Filemoon braucht PoW + Fingerabdruck.** Das Pack scheitert an
   `step 9 get: HTTP 405` (`/api/videos/{code}/playback` verlangt POST +
   `X-Captcha-Token`). Die Challenge nennt `algorithm: sha256-leading-zero-bits`,
   `difficulty: 12`; die Seite selbst nutzt zusätzlich einen signierten
   Fingerabdruck (publicJwk, Canvas-/Audio-/WebGL-Hashes).

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run2-be45079/`
(Titelzeilen im UI-Dump `polls/p*/02.xml`, Logcat `polls/p*/01-logcat.txt`,
Antwort der Challenge/Token-Abfrage separat protokolliert).

## Lauf 3 — Build `078fb4b`, 23.11.2026, A52 (WebView-Budget: ein Versuch je Titel)

Stichprobe: **identisch** zu Lauf 1/2 (Training, Seed `20261122`, 100 Titel,
Katalog 997). Dauer 21:57–22:22 (~26 min, Lauf 2 brauchte ~51 min).
APK `6402b567…` (signiert, `de.hendler.frame61.acceptance`), Zustandsha `078fb4b0…`.
Geänderte Basis gegenüber Lauf 2: `WebViewBudget` (ein WebView-Versuch je Titel,
mit Frist), `QUEUE_WAIT_MS` 5 s statt unbegrenzt auf die app-weite WebView-Sperre,
VOE-Pack `guest: false` (Log inside bei Gastsperre 302 → nach hinten sortiert).

| Kennzahl | Wert | Lauf 2 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **59 / 100 = 59 %** | 53 % | +6 pp |
| Auflösung p50 / p95 | 2.130 / 27.049 ms | 4.585 / 27.186 ms | p50 **−2,5 s**, p95 −137 ms |
| Startzeit p50 / p95 | 2.765 / 28.428 ms | 2.734 / 28.458 ms | p50 +31 ms, p95 −30 ms |
| Fehlerklassen | 33 × `CONTAINER_UNSUPPORTED`, 8 × `NO_CANDIDATE` | 35 / 12 | −2 / **−4** |

Quote je Quellengruppe (gemessene Zeile `QUOTA GROUPSEN`):

| Gruppe | Titel | spielbar | Quote | Lauf 2 | Delta | häufigste Fehlerklasse |
|---|---|---|---|---|---|---|
| aniworld | 19 | 16 | **84 %** | 78 % | +6 pp | `CONTAINER_UNSUPPORTED`: 3 |
| serienstream | 43 | 28 | **65 %** | 58 % | +7 pp | `CONTAINER_UNSUPPORTED`: 11 |
| filmpalast | 11 | 4 | **36 %** | 27 % | +9 pp | `CONTAINER_UNSUPPORTED`: 6 |
| moflix | 11 | 3 | **27 %** | 18 % | +9 pp | `CONTAINER_UNSUPPORTED`: 8 |
| ard | 10 | 5 | 50 % | 50 % | 0 | `CONTAINER_UNSUPPORTED`: 3 |
| bsto | 6 | 3 | 50 % | 50 % | 0 | `CONTAINER_UNSUPPORTED`: 2 |

### Befund 1 — die Warteschlange ist weg, die Interception trifft jetzt

Der Kern des Blocks ist belegt: von den letzten 7 Interception-Versuchen des
Fensters hatten **5** Requests (in Lauf 2 waren es 7 von 14 mit **null**
Anfragen), **4 davon eine echte Medien-URL**:

| Interception | Seite | gefundene Medien-URL | Dauer |
|---|---|---|---|
| #19 | `odysseusa.cc/e/DooiGuaaRJ73B` | `s6-s501740.97bf1.com/hls/aP7Rx…/master.m3u8?token=…` | 3.687 ms |
| #23 | `vidaraa.cc/e/phGy6thMsf0tR` | `s7-5042yyg.s1q2105.com/hls/OD6Y…/master.m3u8?token=…` | 1.878 ms |
| #25 | `vidaraa.cc/e/G6pMqQ070yPla` | `s7-5042yyg.s1q2105.com/hls/xcxb…/master.m3u8?token=…` | 2.121 ms |
| #29 | `vidaraa.cc/dgBRg3f5Mg5zC` | `s1-s501740.97bf1.com/hls/HOhI…/master.m3u8?token=…` | 2.472 ms |

Request-Kette vor dem Treffer (nachgewiesen im `INTERCEPT #REQ`-Fenster):
jwplayer 8.36.2 → jQuery 3.3.1 → pako → crypto-js → acscdn `aclib.js` →
`assets/js/fsorient.js` → `master.m3u8?token=…`. Das ist die **wirkliche**
Interception, nicht der Deckel. Und sie trägt: `fp:by-any-means-gegen-jede-regel`
spielt jetzt ab — **erster Treffer einer Index-Gruppe (filmpalast), der nicht aus
dem ARD/ZDF-Sockel stammt** (`host=s7-5042yyg.s1q2105.com`, `intercept=1
medien=1`, RESULT=OK).

### Befund 2 — was jetzt noch scheitert: eine HTML-Seite als einziger Kandidat

Die 33 `CONTAINER_UNSUPPORTED` haben das Muster `candidates=1 tried=1`: der
Index liefert genau **einen** Hosterlink, und der ist eine HTML-Zielseite, die
der Player nicht spielen kann. Gemessene Zielhosts der Fehlschläge dieser Runde:

| Host | Fehlschläge | Lage |
|---|---|---|
| `devideosrc.co` | ~25 | alle Gruppen — der Pack liefert kein Ergebnis |
| `uqload.vc` | 4 | kein Pack vorhanden |
| `moflix.rpmplay.xyz`, `moflix-stream.fans`, `flyfile.app`, `strmup.to` | ~6 | kein Pack vorhanden |

Damit ist die Abweichung von der Nachtsteuerung-Reihenfolge begründet (Regel 1
der Nachtsteuerung: „Begründe jede Abweichung"): Doodstream/Filemoon spielen in
**dieser Stichprobe praktisch keine Rolle** — gemessene Zielhosts sind
devideosrc/uqload/moflix. `devideosrc.co` ist zuerst zu bedienen, danach
uqload und die moflix-Hosts; bs.to bleibt in der Kette über VOE (BLOCKED) und
verweist bei 6 von 6 Titeln auf devideosrc/uqload.

### Befund 3 — warum „medien=1" trotzdem `CONTAINER_UNSUPPORTED` ergab

Vier Titel fangen eine Medien-URL und fallen trotzdem in
`CONTAINER_UNSUPPORTED` (z. B. `fp:couscous-und-geheimnisse`, `fp:coraline`,
`fp:catweazle`, `aw:afro-samurai`). Ursache: diese Titel haben nur den
devideosrc-Kandidaten; devideosrc hat **kein** `captcha`, läuft also über den
Pack-Pfad statt über den Interceptor. Der Pack-Schritt `js` fragt im
versteckten WebView `getActiveLink()/iframe.src/video.src` ab — der
devideosrc-Abspieler setzt `iframe#_player.src` aber **erst nach dem Klick auf
Play** (Quelle: `devideosrc.co/docs`, `meinecloudPlayer`-Ereignisse). Leerwert
→ `Failed` → der HTML-Kandidat bleibt stehen.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run3-078fb4b/`
(UI-Dumps `polls/p*/02.xml`, Raummeldung `jobs/smoke.log`,
`QUICK_SMOKE` PASS `job-ea000e3e05ec443f`).

## Lauf 4 — Build `d678efb`, 24.11.2026, A52 (DEVIDEOSRC-API + Doodstream-URL-Form)

Stichprobe: **identisch** zu Lauf 1–3 (Training, Seed `20261122`, 100 Titel,
Katalog 997). Dauer 01:44–02:09 (~25 min). APK `e6f74676…` (signiert,
`de.hendler.frame61.acceptance`, 20.430.866 B), CI-Lauf `45292120284` grün,
Smoke `job-e4d3d04d63874d9d` PASS.
Geänderte Basis gegenüber Lauf 3: `DeVideoSrcResolver` (dokumentierte Embed-API),
Doodstream-URL-Form (`/pass_md5/<hash>/<token>`), MediaUrlMatcher für diese Form.

| Kennzahl | Wert | Lauf 3 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **60 / 100 = 60 %** | 59 % | +1 pp |
| Auflösung p50 / p95 | 1.600 / 27.007 ms | 2.130 / 27.049 ms | p50 −530 ms, p95 −42 ms |
| Startzeit p50 / p95 | 2.624 / 28.088 ms | 2.765 / 28.428 ms | p50 −141 ms, p95 −340 ms |
| Fehlerklassen | 34 × `CONTAINER_UNSUPPORTED`, 6 × `NO_CANDIDATE` | 33 / 8 | +1 / −2 |
| Interception | 20 Versuche, 2 mit Medien-URL | 20 / 4 | 0 / −2 |

Quote je Quellengruppe (gemessene Zeile `QUOTA GROUPEN`):

| Gruppe | Titel | spielbar | Quote | Lauf 3 | Delta | häufigste Fehlerklasse |
|---|---|---|---|---|---|---|
| serienstream | 43 | 30 | **70 %** | 65 % | +5 pp | `CONTAINER_UNSUPPORTED`: 10 |
| aniworld | 19 | 16 | **84 %** | 84 % | 0 pp | `CONTAINER_UNSUPPORTED`: 3 |
| moflix | 11 | 3 | **27 %** | 27 % | 0 pp | `CONTAINER_UNSUPPORTED`: 8 |
| filmpalast | 11 | 3 | **27 %** | 36 % | **−9 pp** | `CONTAINER_UNSUPPORTED`: 8 |
| ard | 10 | 5 | 50 % | 50 % | 0 pp | `CONTAINER_UNSUPPORTED`: 3 |
| bsto | 6 | 3 | 50 % | 50 % | 0 pp | `CONTAINER_UNSUPPORTED`: 2 |

### Befund 1 — die DEVIDEOSRC-API lief auf dem Gerät **nie** an

Der Block 4c-1 hat die dokumentierte Embed-API eingebaut — auf dem Gerät ist sie
**nie** gelaufen. Beleg aus dem Logcat des Laufs (16 betroffene Titel):

| Anfrage | Anzahl |
|---|---|
| `GET https://devideosrc.co/movie/tt…` bzw. `/custom/serial/tt…/1/1` | **16** |
| `POST https://devideosrc.co/api/embed-links` | **0** |

Alle anderen POSTs des Laufs sind protokolliert (aniworld 12, cine.to 12,
filemoon 12) — es ist also kein Lückenfenster im 400-Zeilen-Logcat.

**Ursache, live reproduziert (24.11.2026):** Der Ausdruck für den
`JSON.stringify`-Aufruf der Seite war an der Zeilengrenze zerschnitten:

```
"""…id:\s*"([^"]+)"\s*,""" + """token:\s*"([^"]+)"\s*\}"""
```

Nach dem Komma fehlt das `\s*` — die Seite schreibt aber `id: "tt12927920",
token: "…"`. Der Ausdruck traf also **nie**. Die Rückfallebene griff dann das
**erste** `type=`/`id=` des HTML: gemessen `kind=movie` (zufällig richtig),
`id="_cloudToggle"` (das erste `id=` der Seite — falsch). Live-Beleg:

| Variante | Ergebnis |
|---|---|
| `STRINGIFY` (Ist-Zustand) | **KEIN TREFFER** |
| `STRINGIFY` (mit `\s*` nach dem Komma) | `('movie', 'tt12927920', 'bW92aWV8…')` |
| Rückfall-`ID` auf der echten Seite | `_cloudToggle` statt `tt12927920` |

Der Testbestand war zu sauber: die Fixture enthielt kein `id="_cloudToggle"`,
deshalb blieb die CI grün, während das Gerät scheiterte. Der neue Test
`live player page yields the film id and not the first html id` benutzt die
echte Seitenstruktur.

Zusätzlich belegt: die **Serien**-Seite (`/custom/serial/{id}/{s}/{e}`) ruft
**gar nicht** `/api/embed-links`, sondern `/api/resolve` mit
`{type, id, season, episode, mode, token}` — die Werte stehen dort als
`data-*`-Attribute am `<main>`-Element. Beide Endpunkte sind am 24.11.2026
live abgefragt und liefern dieselbe Antwortform (`sources[]` mit
`{name, url, rank}`).

### Befund 2 — devideosrc bleibt der häufigste Fehlziel-Host (16 Fehlschläge)

| Host | Fehlschläge | Lage |
|---|---|---|
| `devideosrc.co` | **16** | API-Stufe lief nicht (Befund 1); die Player-Seite selbst blieb als HTML-Kandidat stehen |
| `moflix.rpmplay.xyz` | 12 | `/api/public/resolve` antwortet 404 `not found` (live, 24.11.2026) |
| `uqload.vc` | 6 | gepackter jwplayer, `embed-<id>.html` — vom Embed-Muster nicht erfasst |
| `moflix-stream.fans` | 4 | „Video unavailable" |
| `aniworld.to` | 3 | Kette ohne WebView-Versuch |
| `prd.jwpltx.com` | 2 | JWPlayer-Kette |
| `zairuc.com`, `strmup.to` | 1 + 1 | Werbe-Klick-Kette (`junimedclk.com`), 0-Byte-Antwort |

**Abweichung von der Nachtsteuerung (Regel 1, „Begründe jede Abweichung"):**
Die Reihenfolge bs.to → Doodstream → Filemoon ist umgesetzt, aber in dieser
Stichprobe tragen die drei Stufen **nichts** bei: die Fehlziel-Hosts sind
devideosrc (16), moflix (16) und uqload (6). Deshalb wurde zuerst devideosrc
bedient (Block 4c-1) — dieser Block hat wegen des Regex-Fehlers **nicht
gegriffen** (Befund 1). Nach der Korrektur ist devideosrc erneut der erste
Kandidat, danach uqload (Block 4c-2).

### Befund 3 — Zuordnung der fünf geänderten Titel

| idx | Lauf 3 | Lauf 4 | Ursache |
|---|---|---|---|
| 1 | FAIL (`devideosrc.co`) | OK (`prx-1350-ant-d.vmpx.online`) | Gewinn über die DEVIDEOSRC-Providerkette |
| 37 | FAIL (`devideosrc.co`) | OK (`box-c-o-l-10.vmcld.space`) | Gewinn über die DEVIDEOSRC-Providerkette |
| 72 | FAIL (`devideosrc.co`) | OK (`cdn.plyr.io`) | Gewinn über die DEVIDEOSRC-Providerkette |
| 34 | OK (`box-1097-y.vmbox.space`) | FAIL (`aniworld.to` ×3) | **Index-/Hoster-Wechsel**, kein Regression |
| 86 | OK (`s7-5042yyg.s1q2105.com`) | FAIL (`devideosrc.co`) | **Index-/Hoster-Wechsel** (Lauf 3: Interception #19 auf `odysseusa.cc/e/DooiGuaaRJ73B`) |

Netto +1 Titel. Die drei Gewinne zeigen: **wenn** die Providerkette greift,
trägt sie sofort; die zwei Verluste sind Hoster-Wechsel der Indexseiten
zwischen den Läufen (gleiche Stichprobe, aber wechselnde Ziel-Hoster) und
keine Code-Regression. Deshalb ist der Mischwert nur eine grobe Größe — die
Gruppenquote ist die belastbare.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run4-d678efb/`
(UI-Dumps `polls/p*/02.xml`, Logcat `polls/p*/01-logcat.txt`), Auswertung mit
`~/hendler-dynasty/evidence/frame61/1.5.0/analyse_lauf.py`.

## Lauf 5 — Build `682683d`, 24.11.2026, A52 (DEVIDEOSRC-API greift + Diagnose)

Stichprobe: **identisch** zu Lauf 1–4 (Training, Seed `20261122`, 100 Titel,
Katalog 997). Dauer 04:47–05:12 (~25 min). APK `b6e165df…` (signiert,
`de.hendler.frame61.acceptance`, 20.430.866 B), CI-Lauf `45317857036` grün,
Smoke `job-54dcdea590ce4451` PASS.
Geänderte Basis gegenüber Lauf 4: DEVIDEOSRC-Ausdruck korrigiert (mit
`\s*` nach dem Komma), Serienweg über `/api/resolve`, HTML-Seite ist kein
Kandidat mehr (fail closed), Diagnose-Sitzung je Titel, jwplayer-Abruf
(Playlist + Ressourcenliste).

| Kennzahl | Wert | Lauf 4 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **64 / 100 = 64 %** | 60 % | **+4 pp** |
| Auflösung p50 / p95 (nur OK-Titel) | 1.616 / 26.958 ms | 1.600 / 27.007 ms | p50 +16 ms, p95 −49 ms |
| Startzeit p50 / p95 (nur OK-Titel) | 2.663 / 28.230 ms | 2.624 / 28.088 ms | p50 +39 ms, p95 +142 ms |
| Fehlerklassen | 18 × `CONTAINER_UNSUPPORTED`, 9 × `GEO_BLOCKED`, 9 × `EMBED_UNRESOLVED` | 34 / 6 | siehe Befund 2 |
| Interception | **82** Versuche, **6** mit Medien-URL | 20 / 2 | +62 / +4 |

Die Bildschirm-Auswertung des Laufs meldet `resolveP50Ms=2426`,
`resolveP95Ms=27113`, `frameP50Ms=2645`, `frameP95Ms=28181` — sie rechnet über
alle Titel, die Auswertung oben nur über die spielbaren.

Quote je Quellengruppe (gemessene Zeile `QUOTA GROUPEN`):

| Gruppe | Titel | spielbar | Quote | Lauf 4 | Delta | häufigste Fehlerklasse |
|---|---|---|---|---|---|---|
| serienstream | 43 | 30 | **69 %** | 70 % | −1 pp | `CONTAINER_UNSUPPORTED`: 6 |
| aniworld | 19 | 17 | **89 %** | 84 % | **+5 pp** | `EMBED_UNRESOLVED`: 2 |
| moflix | 11 | 4 | **36 %** | 27 % | **+9 pp** | `CONTAINER_UNSUPPORTED`: 7 |
| filmpalast | 11 | 5 | **45 %** | 27 % | **+18 pp** | `EMBED_UNRESOLVED`: 3 |
| ard | 10 | 5 | 50 % | 50 % | 0 pp | `GEO_BLOCKED`: 3 |
| bsto | 6 | 3 | 50 % | 50 % | 0 pp | `CONTAINER_UNSUPPORTED`: 2 |

### Befund 1 — die DEVIDEOSRC-API läuft jetzt (12 POSTs statt 0)

Lauf 4 hatte 16 GETs auf die Player-Seite und **null** POSTs. Lauf 5 hat im
Logcat-Fenster:

| Anfrage | Anzahl |
|---|---|
| `GET https://devideosrc.co/movie/tt…` | 10 |
| `GET https://devideosrc.co/custom/serial/tt…/1/1` | 2 |
| `POST https://devideosrc.co/api/embed-links` | **10** |
| `POST https://devideosrc.co/api/resolve` | **2** |

Beide Endpunkte sind damit **live auf dem Gerät** — Film über
`/api/embed-links`, Serie über `/api/resolve`. Die Auswertung der
`QUOTA CAUSE`-Ketten bestätigt es unabhängig davon: 5 Titel zeigen die Stufe
`devideosrc: Antwort 984 B: 4 Quellen` (bzw. 1206 B / 5 Quellen). Die Kette
zeigt nur die **letzten 6** Resolver-Ereignisse, die Zahl 5 ist deshalb eine
Untergrenze.

### Befund 2 — die Fehlerklassen sind jetzt **genauer**, nicht nur anders

In Lauf 4 standen 34 × `CONTAINER_UNSUPPORTED` und 6 × `NO_CANDIDATE`. In Lauf 5
gibt es **kein** `NO_CANDIDATE` mehr, dafür `GEO_BLOCKED` (9) und
`EMBED_UNRESOLVED` (9). Grund: Der Messharness öffnet jetzt je Titel eine
Diagnose-Sitzung. Vorher wurden alle Resolver-Ereignisse verworfen, die
Klassifikation konnte nur `NO_CANDIDATE` heißen; jetzt sieht sie 403/451
(`GEO_BLOCKED`), Captcha/Timeout/Embed (`EMBED_UNRESOLVED`, `TIMEOUT`) und den
HTML-Schutz (`EMBED_UNRESOLVED`). **Die Fehlerklassen von Lauf 4 und Lauf 5
sind deshalb nicht 1:1 vergleichbar** — vergleichbar ist die Kopfzahl der
Fehlschläge (40 in Lauf 4, 36 in Lauf 5) und die Quote.

Die Ursache im Klartext (`cause=` der FAIL-Zeile, 36 Titel):

| Ursache | Titel |
|---|---|
| `Hoster-Zeitüberschreitung` | 17 |
| `Hoster fehlgeschlagen` | 14 |
| `Hoster aufgelöst` (Kandidat da, Player scheitert) | 5 |

### Befund 3 — vier Titel mehr, **kein Verlust**

| idx | Lauf 4 | Lauf 5 | Ursache |
|---|---|---|---|
| 34 | FAIL (`aniworld.to` ×3) | OK (`box-1097-y.vmbox.space`) | Gewinn über die DEVIDEOSRC-Providerkette |
| 36 | FAIL (`devideosrc.co`) | OK (`s8-5042yyg.s1q2105.com`) | Gewinn über die DEVIDEOSRC-Providerkette |
| 49 | FAIL (`moflix.rpmplay.xyz`) | OK (`cdn.plyr.io`) | Gewinn über die Providerkette |
| 69 | FAIL (`devideosrc.co`) | OK (`s9-s1001604.97bf1.com`) | Gewinn über die DEVIDEOSRC-Providerkette |

64 = 60 + 4: In dieser Stichprobe hat **kein** Titel verloren. Die Gruppenquote
verschiebt sich entsprechend: filmpalast +18 pp, moflix +9 pp, aniworld +5 pp,
serienstream −1 pp (Rundung 30/43 = 69,8 %).

### Befund 4 — die Interception läuft viermal so oft

82 statt 20 Versuche, 6 statt 2 mit echter Medien-URL. Grund: Die
DEVIDEOSRC-API liefert je Titel mehrere Anbieter-Embeds; jeder davon bekommt
im Rahmen des Titelbudgets einen WebView-Versuch. Der WebView-Budget (ein
Versuch je Titel) bleibt unverändert — die Versuche stecken in der
Anbieterkette, nicht in einer Schleife.

**Fehlziel-Hosts der 36 Fehlschläge:** `moflix.rpmplay.xyz` 11,
`uqload.vc` 7, `moflix-stream.fans` 4, `dropcdn.io` 7 (auf 4 Subdomains),
`junimedclk.com` 1, `strmup.to` 1, `srv2.supercdn.ws` 1, `flyfile.app` 1.

**Abweichung von der Nachtsteuerung (Regel 1, „Begründe jede Abweichung"):**
Die Reihenfolge bs.to → Doodstream → Filemoon bleibt umgesetzt, trägt in dieser
Stichprobe aber weiterhin nichts bei — die Fehlziel-Hosts sind moflix (15) und
uqload (7). Block 4c-2 (`uqload.vc`, `flyfile.app` als WebView-Ziel) ist deshalb
als nächstes dran; danach moflix.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run5-e2ec45d/`
(UI-Dumps `polls/p*/02.xml`, Logcat `polls/p*/01-logcat.txt`, Startbild
`A4-start/03.png`), Auswertung mit `analyse_lauf.py` und `analyse_ursachen.py`
(beide unter `~/hendler-dynasty/evidence/frame61/1.5.0/`).

## Lauf 6 — Build `27afaeb`, 24.11.2026, A52 (uqload.vc/flyfile.app als WebView-Ziel)

Stichprobe: **identisch** zu Lauf 1–5 (Training, Seed `20261122`, 100 Titel,
Katalog 997). Dauer 05:30–05:55 (~25 min). APK `0f7b1b40…` (signiert,
`de.hendler.frame61.acceptance`, 20.430.866 B), CI-Lauf `45324713475` grün.
Geänderte Basis gegenüber Lauf 5: `uqload.vc` und `flyfile.app` als
WebView-Ziel (Embed-Form `/e/…`), dazu ein **korrigierter Test**, nicht
korrigierter Code (siehe ENTSCHEIDUNGEN 24.11.2026).

| Kennzahl | Wert | Lauf 5 | Delta |
|---|---|---|---|
| Erfolgsquote (Mischwert) | **62 / 100 = 62 %** | 64 % | **−2 pp** |
| Auflösung p50 / p95 (nur OK-Titel) | 1.592 / 26.957 ms | 1.616 / 26.958 ms | p50 −24 ms, p95 −1 ms |
| Startzeit p50 / p95 (nur OK-Titel) | 2.609 / 27.972 ms | 2.663 / 28.230 ms | p50 −54 ms, p95 −258 ms |
| Fehlerklassen | 11 × `CONTAINER_UNSUPPORTED`, 14 × `GEO_BLOCKED`, 13 × `EMBED_UNRESOLVED` | 18 / 9 / 9 | siehe Befund 2 |
| Interception | **69** Versuche, **2** mit Medien-URL | 82 / 6 | −13 / −4 |

Quote je Quellengruppe:

| Gruppe | Titel | spielbar | Quote | Lauf 5 | Delta | häufigste Fehlerklasse |
|---|---|---|---|---|---|---|
| serienstream | 43 | 30 | **70 %** | 69 % | +1 pp | `GEO_BLOCKED`: 6 |
| aniworld | 19 | 16 | **84 %** | 89 % | −5 pp | `EMBED_UNRESOLVED`: 3 |
| moflix | 11 | 3 | **27 %** | 36 % | −9 pp | `CONTAINER_UNSUPPORTED`: 8 |
| filmpalast | 11 | 5 | **45 %** | 45 % | 0 pp | `EMBED_UNRESOLVED`: 4 |
| ard | 10 | 5 | 50 % | 50 % | 0 pp | `GEO_BLOCKED`: 4 |
| bsto | 6 | 3 | 50 % | 50 % | 0 pp | `GEO_BLOCKED`: 2 |

### Befund 1 — Block 4c-2 ist **unmeasured**: der Zielhost kam nicht mehr vor

Die beiden von Block 4c-2 adressierten Hosts kommen im Logcat-/Dump-Material
dieses Laufs **überhaupt nicht** mehr vor:

| Host | Lauf 5 (Erwähnungen) | Lauf 6 (Erwähnungen) |
|---|---|---|
| `uqload.vc` | 96 | **0** |
| `flyfile.app` | 10 | **0** |

In Lauf 5 standen `uqload.vc` (7 Fehlschläge) und `flyfile.app` (1) in den
Fehlziel-Hosts — in Lauf 6 fehlt jeder von beiden. Der Index hat die
Anbieterliste zwischen den Läufen rotiert; uqload wurde gar nicht mehr
**angeboten**. Damit ist Block 4c-2 in dieser Stichprobe **weder ein Gewinn
noch eine Regression**: die Änderung hatte keinen Anlass, zu wirken.

Konsequenz für die Messung: die Kopfquote eines Laufs ist bei rotierendem
Anbieterindex keine Block-Wirkung. Belastbar ist nur die Zuordnung **je
Titel** (welcher Host wurde versucht) — deshalb wird ab hier beides
dokumentiert: Quote je Gruppe **und** Zuordnung der geänderten Titel.

### Befund 2 — drei Verluste, ein Gewinn, alle vier durch Providerwechsel

| idx | Lauf 5 | Lauf 6 | Ursache |
|---|---|---|---|
| 49 | OK (`cdn.plyr.io`) | FAIL (`moflix.rpmplay.xyz`) | anderer Anbieter im Index |
| 69 | OK (`s9-s1001604.97bf1.com`) | FAIL (`r1.dropcdn.io`) | anderer Anbieter im Index |
| 95 | OK (`wdr-progressive.ard-mcdn.de`) | FAIL (`r1.dropcdn.io`) | anderer Anbieter im Index |
| 94 | FAIL (`ds1.dropcdn.io`) | OK (`s1-s501740.97bf1.com`) | anderer Anbieter im Index |

62 = 64 − 3 + 1. Kein Verlust geht auf den geänderten Code zurück: in allen
vier Fällen ist der **Anbieter** ein anderer. Die Fehlziel-Masse liegt danach
bei `dropcdn.io` (13 Titel auf 5 Subdomains), `moflix.rpmplay.xyz` (11) und
`moflix-stream.fans` (4) — Provider, die aus dem Index kommen und nicht aus
dem Auflöser.

Die Fehlerklassen sind mit Lauf 5 vergleichbar (gleiche Taxonomie, gleiche
Diagnose-Sitzung je Titel): `CONTAINER_UNSUPPORTED` 18 → 11,
`GEO_BLOCKED` 9 → 14, `EMBED_UNRESOLVED` 9 → 13. Die Verschiebung ist eine
Verschiebung **innerhalb** der Fehlschläge (andere Hosts), kein neue Fehlerart.

### Befund 3 — die Interception läuft seltener, weil uqload nicht mehr vorkam

69 statt 82 Versuche, 2 statt 6 mit echter Medien-URL. Der Rückgang ist
derselbe Effekt wie in Befund 1: uqload war in Lauf 5 einer der wichtigsten
WebView-Treiber (Block 4c-2 hatte es zum WebView-Ziel gemacht). Der
Messharness misst weiterhin die **echte** Interception-Zeit, nicht den
20-Sekunden-Deckel.

### Befund 4 — Filemoon ist der letzte reparierbare Rest (Block 4d-1)

| Lauf | Titel mit Filemoon in der Kette | davon fehlgeschlagen |
|---|---|---|
| Lauf 5 | 4 | 2 (`ss:andor`, `ss:gekkan-mousou-kagaku`) |
| Lauf 6 | 2 | 1 (`ss:andor`) |

Das Pack selbst war tot: die alte Kette endete auf
`GET /api/videos/{code}/playback` → **HTTP 405** (im Pack als `check.status:
step_failed` protokolliert). Die Seite liefert den Block inzwischen unter
`GET /api/videos/{code}` — verschlüsselt (AES-256-GCM). Block 4d-1 liest
diese API und entschlüsselt sie; der Live-Nachweis steht in ENTSCHEIDUNGEN
24.11.2026. Erwarteter Zugewinn: **+1 bis +2 Titel** (1–2 pp).

**Abweichung von der Nachtsteuerung (Regel 1, „Begründe jede Abweichung"):**
Die geforderte Reihenfolge bs.to → Doodstream → Filemoon ist umgesetzt
(Block 4b-1 bs.to, Block 4c-1 Doodstream/DEVIDEOSRC, Block 4d-1 Filemoon).
Trägt hat in dieser Stichprobe vor allem DEVIDEOSRC (Lauf 5: +4 Titel);
bs.to bringt in beiden Läufen 3 von 6 Titeln (50 %) und war nie der größte
Posten. Die Gruppenverteilung der Stichprobe selbst ist unverändert
(43/19/11/11/10/6), ARD ist mit 10 Titeln **nicht** die Mehrheit.

Belege: `~/hendler-dynasty/evidence/frame61/1.5.0/run6-27afaeb/`
(UI-Dumps `polls/p*/02.xml`, Logcat `polls/p*/01-logcat.txt`), Auswertung mit
`analyse_lauf.py` und `analyse_ursachen.py`, Host-Zählung und
Filemoon-Zuordnung über die Skripte unter `~/hendler-dynasty/evidence/frame61/1.5.0/`.

## Ältere Methode (1.4.0)

- **Stichprobe:** 100 deterministisch gezogene Katalogtitel aus
  `assets/catalog/catalog.json` (1.000 Titel), Seed `20261122`,
  proportional nach Typ (584 Serien / 416 Filme → 58/42), gemischt über
  Genres. Die Auswahl liegt im Harness (`PlaybackQuotaActivity.sample`) und
  ist damit reproduzierbar.
- **Kette:** derselbe Produktionsweg wie im Player —
  `AutoPlaybackResolver.resolveFast(title, episode)` → Kandidatenliste →
  bis zu drei Kandidaten mit `Media3Frame61Player` abspielen.
- **Erfolg:** Die Wiedergabe auf dem Gerät schreitet wirklich fort
  (`positionMs > 0`). Ein grüner Build oder Fixture zählt nicht.
- **Startzeit:** von Beginn der Auflösung bis zur ersten fortschreitenden
  Position (`firstFrameMs`), inklusive Hoster-Kette und Puffer.
- **Protokoll:** je Titel eine Zeile (Titel, Typ, Ergebnis, Startzeit,
  Kandidatenzahl, versuchter Kandidat, Host, Fehlerklasse), zusätzlich im
  Logcat und als Datei unter `Android/data/<paket>/files/quota/`.

## Baseline (Build `243971d`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **6 / 100 = 6 %** | ≥ 95 % |
| Startzeit p50 | 5.345 ms | — |
| Startzeit p95 | **5.615 ms** | < 3.000 ms |
| Ø Startzeit | 5.230 ms | — |
| Fehlerklassen | 55 × `NO_CANDIDATE`, 38 × `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (3003), 1 × `ERROR_CODE_IO_BAD_HTTP_STATUS` | 0 sichtbar |

Alle sechs Erfolge liefen über die legal-freien Quellen (ARD, ZDF, KiKa,
funk) mit 2–6 Kandidaten; je Titel spielte der erste Kandidat.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/baseline-243971d.txt`
(vollständige 100-Zeilen-Tabelle), UI-Dumps `12-poll3/`.

## Zweiter Lauf (Build `e242198`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **5 / 100 = 5 %** | ≥ 95 % |
| Startzeit p50 | **2.635 ms** | — |
| Startzeit p95 | **2.714 ms** | < 3.000 ms ✅ |
| Ø Startzeit | 2.662 ms | — |
| Fehlerklassen | 61 × 3003, 34 × `NO_CANDIDATE` | 0 sichtbar |

Was sich bewegt hat: Die Auflösung ist von ~11 s auf **~1,6–1,9 s** gefallen
(`resolveFast` mit 600-ms-Frist, parallele Hoster-Ketten). Die Kandidaten
kommen jetzt aus den großen Indizes — `serienstream.to` (56 Titel),
`devideosrc.co` (35), `vidaraa.cc` (3), `vixeo.io` (2), `flyfile.app` (1) —
aber sie sind **HTML-Zwischenseiten**, die der Player nicht abspielen kann
(3003). Genau dort setzt der nächste Schritt an: Ketten über mehrere
Hoster-Packs, ein Redirect-Gate-Pack für `serienstream.to`, ein JS-Pack für
`devideosrc` und eine generische Embed-Auflösung für Hosts ohne Pack.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/run5-final-75.txt`.

## Dritter Lauf (Build `f673eb2`, 22.11.2026, A52)

| Kennzahl | Wert | Ziel 1.4.0 |
|---|---|---|
| Erfolgsquote | **4 / 100 = 4 %** | ≥ 95 % |
| Startzeit p50 / p95 | 3.142 / 3.142 ms | < 3.000 ms |
| Fehlerklassen | 86 × `NO_CANDIDATE`, 10 × 3003 | 0 sichtbar |

Wichtiger Befund: Die neuen JS-Ketten (serienstream-Redirect, devideosrc,
vidmoly) brauchen **deutlich länger als 4 s**. Da `SOURCE_TIMEOUT_MS` bei 4 s
lag, wurde jede Pack-Auflösung mit JS-Schritt nach 4 s abgebrochen und lieferte
**0 Kandidaten** — die Quote fiel von 5 auf 4 %, obwohl die Zahl der 3003-Fälle
von 61 auf 10 sank. Der nächste Lauf (`4ebb58a`) hebt das Quellbudget auf 9 s
und die Obergrenze des Einsammelns auf 12 s; `resolveFast` bleibt bei 600 ms
nach dem ersten Kandidaten, schnelle Quellen starten also unverändert in ~1,6 s.

Rohdaten: `~/hendler-dynasty/evidence/frame61/quota/run6-final-84.txt`.

## Vierte Runde (Build `4ebb58a`, 22.11.2026, A52) — abgebrochen

Nach 25 Titeln: 2 OK, 22 × `NO_CANDIDATE`, 1 × 3003. Die Resolve-Zeit lag bei
**20–27 s**, weil die Captcha-Runde (mehrere Packs à 9 s, seriell) auch im
schnellen Pfad lief. Fix in `ef7516a`: Captcha-Runde nur noch im vollständigen
Lauf (`graceMs == 0`).

## Fünfte Runde (Build `ef7516a`) — BLOCKED

Der Lauf startete, danach **sperrte sich der A52** (Keyguard, Screenshot zu
87 % dunkel, UI-Dump nur SystemUI). Die Brücke darf eine sichere Sperre nicht
umgehen (Owner-Policy) — der Lauf ist damit **BLOCKED**, nicht etwa fehlgeschlagen.
Zum Fortsetzen: Gerät entsperren, App starten, „Titel-Quote" erneut auslösen.

## Fünfte Runde (Build `ef7516a`) — BLOCKED

Der Lauf startete, danach **sperrte sich der A52** (Keyguard, Screenshot zu
87 % dunkel, UI-Dump nur SystemUI). Die Brücke darf eine sichere Sperre nicht
umgehen (Owner-Policy) — der Lauf ist damit **BLOCKED**, nicht etwa fehlgeschlagen.

## Sechste Runde (Build `b0aaf4c` = 1.4.0, A52)

Nach 11 Titeln: **1 OK, 10 × `NO_CANDIDATE`, 0 × 3003**. Die 3003-Klasse ist
in diesem Lauf also **nicht mehr aufgetreten** — die Kandidaten, die früher
als HTML-Embed endeten, werden jetzt entweder aufgelöst oder als
„kein Kandidat" verworfen. Dafür kostet jeder Titel ~70 s (JS-Ketten:
serienstream 8 s, devideosrc 8 s, generischer Embed 5 s), weshalb der Lauf für
100 Titel ~2 h bräuchte und nach 11 Titeln zugunsten der Auswertung pausiert
wurde.

Wichtig: Die Quote von 1/11 ist **keine** belastbare Quote (zu kleine
Stichprobe), sie zeigt nur die Richtung.

## Zwischenfazit

- **Startzeit:** Die 3-s-Marke ist bei schnellen Quellen erreicht (p95 2,7 s in
  Lauf 2); mit Quellbudget 9 s und JS-Ketten verschieben sich langsame Titel auf
  5–12 s.
- **Quote:** 4–6 von 100. Der Engpass ist nicht der Player, sondern das
  **Quellenangebot**: Von 100 zufälligen Katalogtiteln führen nur ARD/ZDF/KiKa
  (legal-frei) zuverlässig zum Ziel; die großen Indizes sitzen hinter
  Captcha-/JS-Ketten (VOE, Doodstream, Filemoon) oder liefern Embeds ohne
  auflösbare URL. Die 95-%-Marke ist mit diesem Bestand nicht erreichbar.
- **Nächste Hebel:** (1) Ketten für VOE/Doodstream/Filemoon so bauen, dass sie
  ohne sichtbaren Dialog durchlaufen; (2) Ersatz-Indizes mit eigenem
  Hoster-Pfad; (5) Katalog und Quellen aufeinander abstimmen (Titel, die
  Quellen tatsächlich führen).

## Ursachen der Baseline

1. **`NO_CANDIDATE` (55)** — die großen Serien-/Film-Indizes haben in diesem
   Stand keine verwertbare Stream-Regel: `s.to`/`bs.to` liefern nur Suche und
   Detail, `cine`/`filmstarts` sind reine Metadaten-Packs. Der Resolver wartet
   trotzdem die vollen 4 s je Quelle ab → ~11 s Laufzeit ohne Ergebnis.
2. **`3003` (38)** — Kandidaten existieren, sind aber HTML-Embeds
   (`vidsonic.net`, `devideosrc.co`), die der Player nicht parsen kann. Es
   fehlt die Hoster-Kette für diese Embeds.
3. **Startzeit** — der Resolver wartete auf die langsamste Quelle, obwohl die
   ARD-Kette nach ~1 s liefert; Hoster-Ketten liefen pro Stream sequentiell.

## Gegenmaßnahmen (Stand nach Baseline)

| Maßnahme | Wirkung |
|---|---|
| `resolveFast` mit 600 ms Frist nach dem ersten Kandidaten | Startzeit p95 5,6 s → erwartet ~2 s |
| Hoster-Auflösung je Pack parallel | ~11 s ohne Kandidat → unter 5 s |
| Stream-Regel für `s.to` (Serien) | neue Kandidaten für Serien |
| Hoster-Pack `vidsonic` (JS-Kette) | 3003 bei filmpalast-Kandidaten |
| `HeadlessCaptchaSolver` (versteckte WebView, Cookie-Cache) | VOE/Doodstream ohne Dialog |
| Stream-Regel für `aniworld` (Anime) | neue Kandidaten für Anime |

## Offen

- `bs.to` (Serien) und `devideosrc` (Filme) brauchen eigene Ketten; dort
  antwortet die Seite ohne `data-play-url`, bzw. baut die URL per JS.
- Die 95-%-Marke ist mit dem aktuellen Quellenbestand **nicht** erreichbar;
  entscheidend ist, wie viele der 100 Titel eine Quelle überhaupt führt.