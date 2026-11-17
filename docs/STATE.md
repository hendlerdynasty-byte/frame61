# STATE.md — Frame61 Status

Stand: 2026-11-17 (Block 8). Endstand der Blöcke 1–8; Details in
[STAND.md](STAND.md) und [ABSCHLUSS.md](ABSCHLUSS.md).

## Kurzfassung

- **Gate:** Debug-Build, Lint, **93 Tests**, Asset-Validator, Fetch-Tests,
  PACKS-Abgleich, APK-Größe — siehe ABSCHLUSS für den letzten Lauf.
- **Gerät (A52):** Start, Navigation, Suche, Detail, Folgen, Player, Skins,
  Source Lab, Profile, Optionen, Info belegt; **ARD-Live-Slice** (Babylon
  Berlin S01/E01, Label „ARD Mediathek") läuft.
- **Offen:** Release-Signing (Keystore), `update.json`-Host, GitHub-Actions-
  Remote, 24 nicht live verifizierte Packs, M6-Stubs, RGB_565, Fire TV.

## Belegte Änderungen dieser Runde

| Bereich | Änderung | Beleg |
|---|---|---|
| Player | HLS-Abhängigkeit, Watchdog-Fix, kein Doppel-Pop | Gerät |
| Room | v2, Migration 1→2, Default-Profil, Resume-ID je Folge | Gerät + Tests |
| Metadaten | 46 Offline-Snapshots, lokaler Suchindex, Info-Attribution | Gerät + Tests |
| Quelle | ARD-Pack + `direct`-Hoster + Resolver + recorded Fixtures | Gerät + Worker |
| Automatisierung | Pack-Worker, Cron, TVmaze-Job, PACKS.md im Gate | Worker-Lauf |
| UI | Kontrast, Markup, NavLabel, Suchauslösung, Logo | Gerät |

## Artefakt

Der letzte Block-8-Gate liefert APK-Hash und Größe; siehe
[ABSCHLUSS.md](ABSCHLUSS.md). Kein Release-/Store-Nachweis.

## Bauen

```sh
scripts/preflight.sh
python3 ~/hendler-device-ops/remote_build.py --repo "<frame61>" --tasks "gate"
```

Lokale Heavy-Builds bleiben per Owner-Policy gesperrt.
