# Phase A — Diagnose- und Quellbefund, 20.11.2026

## Quellgebundener Prüfstand

- HEAD `805af37560d10dab171e30315044a234138c8a8f`.
- Remote-Job `frame61-805af37560d1-1795176615`, Exit 0.
- **112/112 Tests**, 0 Failures/Errors/Skipped; Lint **0 Errors/125 Warnings**.
- Perf-APK `465ba36db9946b2f357622eebb30ebd98d6cc65f6ea9b042a5192b2ce734ea48`, 20.024.529 B.
- Device-Lab `job-fb28f440f5d04f09`: Installation als `de.hendler.frame61.perf` auf SM-A525F/Android 14 PASS. Die öffentliche Release-App wurde nicht ersetzt.

## Befund 1 — 3003 und Kontrollpfad

Der zunächst installierte `de.hendler.frame61`-Stand zeigte bei „Stranger Things“ → „Abspielen“ reproduzierbar `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (`REPRO-3003.md`). In der quellgebundenen Diagnosevariante mit OkHttpDataSource erschien dieser Fehler beim Kontrolltitel nicht erneut:

- Alle 31 aktiven Source-Packs lieferten für Stranger Things 0 Kandidaten.
- Der einzige verbleibende Kandidat war der ausdrücklich gekennzeichnete Mux-Demo-Fallback.
- `Media3-Zustand READY` und `Erstes Frame gerendert` sind in `isolated-play/06-frame61.log` belegt.
- Die HTTP-Diagnose erkannte die Playlist als HLS und die Segmente als MPEG-TS. Die Wiedergabe endete an der bereits nahe am Ende gespeicherten Resume-Position.

Das ist ein erfolgreicher HLS-/TS-Kontrollpfad, **kein Nachweis einer realen Index-/Hosterquelle**.

## Befund 2 — Reale Kandidaten entstehen derzeit nicht

Für Inception lieferten ebenfalls alle aktiven Packs 0 Kandidaten (`inception-play/02-frame61.log`). Relevante Live-Unterschiede:

| Quelle | Beobachtung | Einordnung |
|---|---|---|
| Moflix | HTTP 200; `loaders.searchPage.results[0]` enthält Inception (`id=8972`) | Suchtreffer vorhanden, aber keine `streams`-Regel im Pack |
| StreamCloud | HTTP 200; Treffer `/filme/27205-inception-stream-deutsch` | Suchtreffer vorhanden, aber keine `streams`-Regel im Pack |
| Filmpalast | Suche findet `/stream/inception`; Detail liefert 49 B | `Verbindungsfehler. Bitte später erneut versuchen.` — App und unabhängiger Abruf, daher `BLOCKED` |
| StreamCloud-Embed | `devideosrc.co/movie/tt1375666` | Embed ist HTML, keine direkte Media-URL; unbekannte Hoster dürfen nicht ungeprüft an ExoPlayer durchgereicht werden |

Moflix `/watch/1620235` liefert `watchPage.alternative_videos`; StreamCloud `POST /api/embed-links` liefert vier weitere Embed-Hoster. Auch diese URLs sind zunächst Embed-Seiten und kein Abspielnachweis. Der erste Dropload-Embed enthält gepacktes JavaScript/JWPlayer; nach zwei technischen Prüfungen bleibt die direkte Mediendatei hier zunächst `BLOCKED`, statt einen Erfolg zu behaupten.

Private Rohbelege mit Token-/Signaturdaten bleiben außerhalb des Repos unter `~/hendler-dynasty/research/frame61/20261120/`.

SHA-256 der ausgewerteten Rohdaten:

- Filmpalast 49 B: `537365ffba16ee94dcc88a1ef4bff87fe52eb17d0d35c33985f7709201f22edc`
- Moflix Suche: `8ce27d8994b59695e567d511e5a69147151e6ded55d5c0ae2a63bd20ad5295c1`
- Moflix Watch: `89fd6b63613f938cb680f14a892cb61768863932c05dc36cb48b23c4d24f0c7f`
- StreamCloud Suche: `1493c6ba12e049ea62e1a3bc67e391a9edee29732f2c1b40625f71ed8a795285`
- StreamCloud Detail: `7d1ef69e0ce3d2f4423befb2e719a8525f09c8bb84e80e8f3cb75d46893171e8`
- StreamCloud Embed: `b9089ea87db6080d3de4e7cceb50951e2fe24e9f63adfdc99e95bab5ad2a3348`
- StreamCloud Embed-Links: `df83b3e0619dd12d4fde15fb322d8ec5b1ef1a03c1ef953f829cdb895880897c`
- Dropload Embed: `a71c4f3ca79a07ba26860629e9c42d6d484f4a375b0cf64248ba09255dc2f8a2`

## Befund 3 — Captcha blockiert sichtbar und nicht abbrechbar

Während der Demo-Fallback bereits lud, überdeckte ein Captcha-WebView die Wiedergabe. Nach einem Abbruch rückte der nächste Captcha-Request nach. Zwei Touch-Abbrüche und Android-Zurück beendeten den sichtbaren Dialog nicht (`cancel-captcha*/`, `back-captcha/`). Die interaktive Prüfung wurde danach gestoppt. Für die Isolation wurden Kinokiste und MegaKino in der Perf-Installation deaktiviert (`disabled-captcha-sources/05.xml`, beide `checked=false`).

Erforderliche Reparatur: Captcha-Quellen nicht parallel mit normalen Kandidaten starten, Dialoge seriell und regulär abbrechbar machen, kein Hintergrund-Playback hinter einem Dialog.

## Befund 4 — Player-Back und Header

- Zwei Back-Aktionen verließen den beendeten Player nicht (`player-back*/`). Der Key-Router setzt bei Back-Down erneut `controlsVisible=true`, sodass der BackHandler jedes Back abfängt.
- Media3 erhielt bisher nur eine URL. Referer/User-Agent/Cookies sind im `MirrorCandidate` nicht enthalten und können folglich nicht durch Playlist-/Segment-Hops propagiert werden.
- `onPlayerError` erreichte den ViewModel nicht; es gab keine automatische Neuauflösung mit einem anderen Kandidaten.

Diese Punkte werden im Reparaturpaket behandelt. Keine Source wird ab diesem Auftrag allein wegen Fixture oder HTTP 200 als `verified` geführt; erforderlich ist ein realer Abspielnachweis auf dem A52.
