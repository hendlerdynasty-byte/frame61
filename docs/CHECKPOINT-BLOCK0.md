# CHECKPOINT — Block 0 (Bootstrap)

Stand: 2026-11-17 19:52 · Branch `block-0` · `main` @ `b3f0b40`

## Status: BLOCKED_EXTERNAL (GitHub Actions Billing)

Zwei Bootstrap-Läufe wurden von GitHub **vor dem Jobstart** abgelehnt:

| Lauf | Ergebnis | Ursache |
|---|---|---|
| `44151864618` | failure (7 s) | „The job was not started because recent account payments have failed or your spending limit needs to be increased." |
| `44152085841` | failure (5 s) | identisch |

Das ist kein Workflow-Fehler: `actions/permissions` ist `enabled=true`,
`allowed_actions=all`; der Runner wird wegen des Abrechnungsstatus des Kontos
`hendlerdynasty-byte` nicht gestartet. Ein dritter Retry ist sinnlos, solange
der Kontostatus unverändert ist.

## Sicherheitsprüfung vor Public (17.11.2026)

| Prüfung | Ergebnis |
|---|---|
| Getrackte sensible Dateien | keine (`local.properties`, `*.jks`, `.env` nicht getrackt) |
| History-Pfade | keine |
| History-Inhaltsscan | 2 Treffer, beide der alte Platzhalter-Fallback `?: "frame61"` in `app/build.gradle.kts`; **kein echtes Passwort**, kein Schlüssel |
| Maßnahme | Fallbacks entfernt; Signing liest nur noch `FRAME61_*` aus der Umgebung |
| `.gitignore` | ergänzt: `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `.env*`, `signing.properties`, `keystore.properties`, `.idea/local.properties` |
| Repo-Größe | `.git` 1,5 MB, 315 getrackte Dateien, 35 Commits |
| Bewertung | sauber für Public; der alte Platzhalter ist kein Zugangsdatum |

## Was Block 0 bereits erledigt hat

| Schritt | Beleg |
|---|---|
| gh/git/keytool/openssl geprüft | gh 2.98.0, git 2.55.0, keytool/openssl vorhanden |
| Aktives Konto | `hendlerdynasty-byte`, Scopes `repo`, `workflow`, `read:org`, `gist` |
| Privates Repo | `https://github.com/hendlerdynasty-byte/frame61` (PRIVATE), Default `main` |
| Push | `main` @ `b3f0b40`, `block-0` gepusht |
| Actions-Permissions | `enabled=true`, `allowed_actions=all`, Workflow-Token `write` |
| Pages | **nicht verfügbar** für private Repos im aktuellen Plan (HTTP 422); `pages.yml` liegt dormant |
| Keystore | `~/hendler-dynasty/keys/frame61-release.jks` (RSA 4096, bis 2054), SHA-256 `31f5cd50…`, Zertifikat `12:01:F4:6D…4C:52` |
| Base64-Backup | `~/hendler-dynasty/keys/frame61-release.jks.b64` (außerhalb des Repos), SHA-256 `bec249b4…` |
| Secrets | `FRAME61_KEYSTORE_BASE64`, `FRAME61_STORE_PASS`, `FRAME61_KEY_PASS`, `FRAME61_KEY_ALIAS` gesetzt |
| Doku | `docs/SIGNING.md`, `docs/ENTSCHEIDUNGEN.md`, `README`/`STAND` unverändert gültig |

## Exakt fehlende Owner-Handlung

1. GitHub-Konto `hendlerdynasty-byte` → **Settings → Billing and plans**:
   fehlgeschlagene Zahlung klären oder Spending Limit so setzen, dass Actions
   für private Repos wieder startet.
2. Danach genau ein Befehl:

```sh
cd "/Volumes/My Passport/Mac Download/HD | ZCode/frame61"
gh workflow run bootstrap.yml --ref main
gh run watch
```

3. Grün = Block 0 abgeschlossen; erst dann Block 1 starten.

## Nicht getan (bewusst)

- Kein Public-Schalten des Repos (würde Pages/Actions „lösen", verletzt aber
  die Private-Vorgabe).
- Kein Wechsel auf ein anderes GitHub-Konto/Repo (nicht autorisiert).
- Kein Cloudflare-/Bezahl-Fallback für Actions.
- Keine Blöcke 1–12 begonnen (Regel: erst nach grünem Actions-Lauf).

## Nächster Befehl nach Freigabe

```sh
gh workflow run bootstrap.yml --ref main && gh run watch
```
