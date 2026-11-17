# FETCH.md — Worker und Cron (Block 4)

Stand: 2026-11-17. Der Worker laeuft ohne Fremdbibliotheken (Python-Stdlib) und
ohne Zugangsdaten.

## Bausteine

| Datei | Aufgabe |
|---|---|
| `tools/fetch/pack_worker.py` | prueft Source-Packs gegen echte Antworten, schreibt `verified` / `last_check` / `check` |
| `tools/fetch/cssmini.py` | HTML-Baum + CSS-Teilmenge (Tag, `.class`, `#id`, `[attr]`, Nachfahren) |
| `tools/fetch/test_cssmini.py` | Unit-Tests des Matchers (laufen im Gate als `testFetchTools`) |
| `tools/fetch/tvmaze_snapshots.py` | erzeugt `assets/meta/*` (20 Calls/10 s, Retry/Backoff) |
| `tools/fetch/targets.json` | **nur belegte** Ziel-URLs je Pack |
| `.github/workflows/fetch.yml` | Cron `17 4 * * *`, Rotation, Commit, Issue bei Bruch |

## Ablauf je Pack

1. Ziel aus `targets.json`; fehlt es, bleibt der Pack `verified: false` mit
   `check.status = "no_target"` — es wird nichts geraten.
2. HTTPS-Abruf mit 3 Versuchen (Backoff 2 s/4 s) und Host-Allowlist: der
   Zielhost muss zur `baseUrl` des Packs gehoeren (SSRF-Schutz).
3. Optionaler Container-Trim; danach Pruefung aller vorhandenen Regeln
   (`search`, `detail`, `episodes`, `streams`) — HTML per CSS-Matcher, API per
   Dot-Path.
4. Ergebnis in den Pack schreiben:

```json
"verified": true,
"last_check": "2026-11-17T15:59:23Z",
"check": {
  "status": "verified | selector_missing | no_target | fetch_failed | ...",
  "reason": "",
  "checked": ["search.jsonPath", "search.idPath", "detail.jsonPath"],
  "misses": [],
  "http_status": 200
}
```

Ein Pack mit Selektorverlust wird **markiert, nicht geloescht**; der Lauf
endet fuer die anderen Packs normal. `--strict` liefert Exit 2, wenn ein
bisher verifizierter Pack die Verifikation verliert — die Action oeffnet dann
ein Issue.

## Cron

```yaml
schedule: "17 4 * * *"   # ungerade Minute
```

- Rotation: `--limit 10 --offset <(Tag im Jahr % 3) * 10>`; drei Laeufe decken
  alle 25 Packs ab.
- Der Job committet `app/src/main/assets` selbst (verhindert die
  60-Tage-Deaktivierung).
- Bei Fehlschlag: `gh issue create` mit Link auf den Actions-Lauf.
- Rechte: `contents: write`, `issues: write`; kein `pull_request_target`,
  keine Secrets ausser `GITHUB_TOKEN`.

## Belegter Lauf (lokal, 17.11.2026)

```
25 Packs geprueft, 1 verifiziert, 0 verloren
archive-org      verified
```

`archive-org` wurde live gegen
`https://archive.org/advancedsearch.php?...` plus
`https://archive.org/metadata/{id}` geprueft: `search.jsonPath`, `idPath`,
`titlePath`, `detail.jsonPath`, `streams.streamPath` — HTTP 200, keine Misses.
Alle anderen Packs haben noch kein belegtes Ziel (`no_target`) und bleiben
`verified: false`; HTML-Ziele kommen erst mit echtem Seitenbeleg dazu
(Block 7).

**Grenze:** Der Cron selbst ist im Repo definiert, aber noch nicht auf GitHub
ausgefuehrt (kein Remote/Workflow-Lauf in dieser Umgebung). Der Nachweis oben
ist ein lokaler Worker-Lauf.
