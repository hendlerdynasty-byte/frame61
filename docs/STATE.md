# STATE.md — Frame61 Status

Stand: 22.11.2026 (Phase B, Release 1.3.0 / VersionCode 4). Quellstand
`9353e5a` (Merge Phase B auf `main`). Verbindliche Abschlussberichte:
[ABSCHLUSS-PHASE-B.md](ABSCHLUSS-PHASE-B.md) (aktuell), [ABSCHLUSS-TV.md](ABSCHLUSS-TV.md)
(TV-Ziel), [ABSCHLUSS.md](ABSCHLUSS.md) (Historie Blöcke 1–8).

## Kurzfassung

- **Release:** signierter Sideload-Release **1.3.0 / VC4** (APK `0be6e691…`,
  AAB `1044b319…`), GitHub-Release `v1.3.0`, Update-Manifest auf Pages mit
  `versionCode` 4. Kein Play-Store-Upload, kein Rollout.
- **Gate:** 121/121 Unit-Tests grün, Lint 0 Errors / 106 Warnings,
  Debug-APK 36,7 MiB (< 45 MiB), Validator 0 Fehler.
- **Player:** 3003 (`ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED`) ist im
  Selbsttest auf dem A52 für HLS, MP4, MKV, AVI und MPEG-TS behoben
  (6/6 PASS), Fallback auf den Android-Systemplayer belegt.
- **Quellen (A52):** 9/14 Indexseiten verifiziert, 1/14 Wiedergabe über den
  Indexpfad (moflix), 2/11 Hoster verifiziert (direct, vidmoly). 5 Indexseiten
  und 9 Hoster blockiert, jeweils mit Ursache, HTTP-Status und Versuchen.
- **TV:** untere Navigationsleiste durch eine feste linke Seitenleiste
  ersetzt; D-Pad-Fokusweg auf dem Android-TV-Emulator belegt. Echtes Fire TV:
  **NOT_RUN** (Gerät bleibt unangetastet).
- **Performance (Release-APK, A52):** Kaltstart p95 334 ms, Scroll p95 22 ms,
  Janky 3,01 % — keine Regression gegenüber Block 11.
- **Offen:** 5 Indexseiten und 9 Hoster blockiert; HDR-Nachweis mangels
  HDR-fähigem Testgerät; erzwungener Mid-Playback-3003 mit Rotation nicht am
  Gerät belegt; Fire TV unangetastet; M6 bleibt deaktiviert.

## Belegte Änderungen der Phase B

| Bereich | Änderung | Beleg |
|---|---|---|
| Player | Media3 mit auflösender DataSource, Systemplayer-Fallback mit eigener Surface, MIME-/Header-Kontext | `docs/design/phase-b/selftest-a52-6of6.xml` (A52, 6/6 PASS) |
| Quellen | Abdeckung v2 im Diagnose-Activity, Live-Messung von Index und Hoster | `docs/design/phase-b/coverage-a52-run4-60512d4.txt` (index 9/14, playback 1/14, hoster 2/11) |
| Stabilität | 30-Minuten-Sessiontest mit 50/20/10-Wechseln | `docs/design/phase-b/stress-a52-session-pass.xml` (0 Fehler) |
| TV-UI | feste Seitenleiste statt unterer Leiste | `docs/design/phase-b/tv-dpad-trail-1.3.0.txt` |
| Release | Version 1.3.0/VC4, unsigned Bau im Gate, lokale Signatur, Pages-Manifest | `docs/RELEASE.md` |
| Performance | Nachmessung am signierten Release-APK | `docs/PERF2.md`, `docs/design/phase-b/perf-release-1.3.0.txt` |

## Artefakt

| Artefakt | SHA-256 | Größe |
|---|---|---|
| `frame61-1.3.0.apk` | `0be6e691c665f46a6e675b633dd62511d680ae88e2cc22160e8834fdb2bb0541` | 20.016.408 B |
| `frame61-1.3.0.aab` | `1044b3196d07f59c2b25831ea09269c82a0c9862a9ee7a6279b3e0d06c2995c6` | 24.693.080 B |

Ablage: `~/hendler-dynasty/releases/frame61/1.3.0/` und
<https://github.com/hendlerdynasty-byte/frame61/releases/tag/v1.3.0>.
Update-Manifest: <https://hendlerdynasty-byte.github.io/frame61/update.json>.

## Bauen

```sh
scripts/preflight.sh
# Linux-kompatible Builds laufen über GitHub Actions (android-gate.yml):
gh workflow run android-gate.yml --ref main
# Signierung bleibt lokal am Mac (kein Secret-Transfer):
scripts/sign_acceptance.sh <unsigned.apk> <signed.apk>
scripts/sign_release_aab.sh <unsigned.aab> <signed.aab>
```

Lokale Heavy-Builds bleiben per Owner-Policy gesperrt.
