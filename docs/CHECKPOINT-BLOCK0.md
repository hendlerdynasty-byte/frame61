# CHECKPOINT — Block 0 / 0.5 (Bootstrap)

Stand: 2026-11-17 20:05 · Branch `block-0.5` · Basis `57d1367`

## Status: DONE (Actions + Pages grün)

| Nachweis | Wert |
|---|---|
| Repo | `hendlerdynasty-byte/frame61`, **public**, Default `main` |
| Sicherheitscheck | keine getrackten Schlüssel/Env-Dateien; History nur alter Platzhalter `?: "frame61"` (kein echtes Passwort); Fallback entfernt |
| `.git` | 1,5 MB, 315 Dateien, 35 Commits beim Wechsel |
| Actions-Permissions | `enabled=true`, `allowed_actions=all`, Workflow-Token `write` |
| Bootstrap-Lauf | `44153299724` — **success** (8 s) |
| Pages | aktiv (`build_type=workflow`), Lauf `44153289832` **success** |
| Pages-URL | `https://hendlerdynasty-byte.github.io/frame61/` (HTTP 200) |
| Keystore | `~/hendler-dynasty/keys/frame61-release.jks`, SHA-256 `31f5cd50…`, Zertifikat `12:01:F4:6D…4C:52` |
| Base64-Backup | `…/frame61-release.jks.b64`, SHA-256 `bec249b4…` (außerhalb des Repos) |
| Secrets | 4 gesetzt (`FRAME61_KEYSTORE_BASE64`, `STORE_PASS`, `KEY_PASS`, `KEY_ALIAS`) |
| Signing-Fallback | entfernt; nur `System.getenv(...)` |

## Billing-Historie

Die ersten beiden Läufe (`44151864618`, `44152085841`) scheiterten vor dem
Jobstart an einer GitHub-Billing-Meldung des **privaten** Repos. Nach dem
öffentlichen Wechsel lief `bootstrap` sofort grün; kein Bezahl-Fallback nötig.

## Gate

- Lokal: `./gradlew gate` (Block-0.5-Vorgabe), Ergebnis wird nach dem Lauf
  eingetragen.
- Remote-Gate bleibt der verbindliche CI-Weg (`remote_build.py --tasks gate`).

## Nächster Schritt

Block 1 — Inventur (`docs/STAND2.md`), danach Block 2 (Cover-Defekt).
