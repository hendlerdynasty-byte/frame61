# UPDATE.md — Sideload-Update (`update.json`)

Stand: 2026-11-17. Der Updater ist für Fire TV / Sideload gebaut: er liest ein
JSON-Manifest, vergleicht `versionCode`, lädt die APK, prüft SHA-256 und öffnet
den System-Installer. Kein Play, kein Dienstkonto.

## Schema

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `versionCode` | int | ja | > installierter Code = Update |
| `versionName` | string | nein | Anzeige; leer → `versionCode` |
| `apkUrl` | string | ja | `http(s)://…` der APK |
| `sha256` | string | nein* | Hex-Hash der APK; *empfohlen, sonst keine Integritätsprüfung |
| `notes` | string | nein | Was neu ist |

Unbekannte Felder werden ignoriert. Ungültiges JSON, `versionCode <= 0` oder
eine Nicht-http(s)-URL machen das Manifest ungültig; die App zeigt eine
Fehlermeldung, stürzt nicht.

## Beispiel

`docs/update.example.json`:

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://example.invalid/frame61/frame61-1.1.0.apk",
  "sha256": "…64 hex…",
  "notes": "Beispielmanifest."
}
```

## Ablauf in der App

1. Optionen → „Update-Quelle (Manifest-URL)" eintragen.
2. „Jetzt prüfen" → `UpdaterManager.check()`.
3. Bei neuer Version: „Installieren" → Download nach
   `filesDir/updates/frame61-<versionCode>.apk`, SHA-256-Vergleich, dann
   `FileProvider` + `ACTION_VIEW` (Systemdialog).
4. Der Nutzer bestätigt die Installation; die App installiert nicht selbst.

## Betrieb

- Manifest und APK müssen über HTTPS erreichbar sein; `sha256` aus
  `shasum -a 256` eintragen.
- Ein echtes Manifest wurde in dieser Umgebung **nicht ausgeliefert**
  (kein erreichbarer Host) → Livepfad **NOT_RUN**. Parser und
  Fehlerbehandlung sind durch `UpdateManifestTest` belegt.
- Signatur: Sideload-Updates brauchen denselben Signaturschlüssel wie die
  installierte App; siehe `docs/SIGNING.md`.
