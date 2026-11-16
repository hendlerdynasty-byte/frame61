# STATE_TEMPLATE.md — Vorlage für docs/STATE.md

`docs/STATE.md` ist der kanonische Status. Diese Vorlage legt fest, wie er
gepflegt wird. Jede Aussage braucht einen Beleg (Dateipfad, Testname,
Artefakt-Hash) oder eine Blockade mit Ursache.

```markdown
# STATE.md — <Produkt> Status

## Stand: <TT.MM.JJJJ>

Kurzfassung: <zwei bis drei Sätze — was läuft, was blockiert, was ist der
nächste Schritt>.

---

## Was läuft (belegt)

| Schritt | Inhalt | Beleg |
|---------|--------|-------|
| <Phase> | <Inhalt> | <Dateipfad / Testname / Artefakt-Hash> |

## Build-Gate

| Befehl | Status |
|--------|--------|
| `:app:assembleDebug` | <✅ / ❌ + Fehlerbild> |
| `:app:lint` | <✅ / ❌ + Zahlen> |
| Unit-Tests | <✅ n Tests, 0 Fehler / ❌> |

Letzter Lauf: <Bauhost/Job, exit_code, BUILD SUCCESSFUL | BLOCKED_*>

## APK-Artefakt

- **Datei:** <Name>
- **Größe:** <MB>
- **SHA-256:** <Hash>
- **Pfad:** <absoluter Pfad>
- **Grenze:** Debug-Bau, kein signiertes Release, keine Store-Aussage

## Geräte-Nachweise

| Lauf | Suite | Verdikt | Was es zeigt |
|------|-------|---------|--------------|

## Offen

1. <Offener Punkt> — <warum offen, was fehlt>

## Bau-Hinweise

- <Lehren, die den nächsten Lauf betreffen>
```

## Regeln

- Nur belegte Zeilen als „läuft"; UNVERIFIED/blockiert/getan trennen.
- Zahlen mit Zähler/Nenner (z. B. „50 Tests, 0 Fehler").
- Buildpass ist kein Release-Nachweis; Artefakt-Hash und Quellstand gehören
  zu jedem relevanten Beleg.
- Veraltete Punkte werden korrigiert, nicht still gelöscht.
- Stand-Datum oben ist das Datum der letzten echten Änderung, nicht der
  letzten Formatierung.