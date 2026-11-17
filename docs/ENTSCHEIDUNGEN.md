# ENTSCHEIDUNGEN.md — getroffene Defaults

Stand: 2026-11-17. Bei Unklarheit gilt der hier dokumentierte Default; die
Entscheidung wird mit Datum eingetragen und weitergearbeitet.

## 2026-11-17 — GitHub-Repo

- Remote: `hendlerdynasty-byte/frame61`, **privat**, Default-Branch `main`.
- Begründung: Ownerauftrag Block 0; aktives `gh`-Konto ist
  `hendlerdynasty-byte` (workflow-Scope vorhanden).

## 2026-11-17 — GitHub Pages

- **BLOCKED_EXTERNAL:** GitHub Pages ist für dieses private Repo im aktuellen
  Plan nicht verfügbar (API `POST /pages` → HTTP 422 „plan does not support").
- Default: Repo bleibt privat; keine stille Umstellung auf public.
  `pages.yml` ist korrekt angelegt, aber dormant (nur `workflow_dispatch`).
- Fallback für Block 12: Cloudflare Pages/Worker der Firmeninfrastruktur oder
  Owner-Plan-Upgrade; Entscheidung fällt, sobald der Update-Host gebraucht wird.

## 2026-11-17 — Keystore und Secrets

- Release-Keystore liegt **außerhalb** des Repos:
  `~/hendler-dynasty/keys/frame61-release.jks` (mode 600).
- Base64-Backup liegt ebenfalls außerhalb des Repos
  (`frame61-release.jks.b64`, mode 600). **Keine** Keystore-/Passwort-Literale
  in `SIGNING.md`, Quelltext oder Tests — Sicherheitsvorgabe hat Vorrang vor
  der Formulierung „Backup in SIGNING.md"; dort stehen Pfad, Hashes und
  Fingerprint.
- GitHub-Actions-Secrets gesetzt: `FRAME61_KEYSTORE_BASE64`,
  `FRAME61_STORE_PASS`, `FRAME61_KEY_PASS`, `FRAME61_KEY_ALIAS`.
