#!/usr/bin/env python3
"""Erzeugt docs/PACKS.md aus den Assets — keine handgepflegten Statuszeilen.

Quellen: app/src/main/assets/sources/*.json, hosters/*.json und
scraper/engine/src/test/resources/fixtures/<id>/meta.json.

`--check` vergleicht die erzeugte Datei mit docs/PACKS.md und liefert Exit 1,
wenn sie veraltet ist. Der Gate-Task `packsReport` haengt daran.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def fixture_provenance(root: Path, pack_id: str) -> str:
    meta = root / "scraper/engine/src/test/resources/fixtures" / pack_id / "meta.json"
    if not meta.is_file():
        return "–"
    try:
        return load(meta).get("provenance", "unbekannt")
    except json.JSONDecodeError:
        return "kaputt"


def open_reason(pack: dict) -> str:
    check = pack.get("check") or {}
    status = check.get("status", "")
    misses = check.get("misses") or []
    if pack.get("verified") is True:
        return "—"
    if status == "selector_missing":
        return "Selektoren ohne Treffer: " + ", ".join(misses)
    if status == "no_target":
        return "kein belegtes Ziel (Worker)"
    if status in ("fetch_failed", "detail_failed"):
        return f"{status}: {check.get('reason', '')}".strip()
    if status:
        return status
    return "nicht live geprüft"


def render(root: Path) -> str:
    sources_dir = root / "app/src/main/assets/sources"
    hosters_dir = root / "app/src/main/assets/hosters"
    packs = [load(p) for p in sorted(sources_dir.glob("*.json"))]
    hosters = [load(p) for p in sorted(hosters_dir.glob("*.json"))]

    used_fixtures = [fixture_provenance(root, p.get("id", "")) for p in packs]
    verified = [p for p in packs if p.get("verified") is True]
    legal_free = [p for p in packs if p.get("legalFree") is True]
    gray = [p for p in packs if str(p.get("tier", "")).upper() in ("A", "B")]

    lines: list[str] = []
    lines.append("# PACKS.md — Status aller Quellen")
    lines.append("")
    lines.append("Automatisch erzeugt von `tools/packs_report.py`; nicht von Hand")
    lines.append("pflegen. Stand der Prüfung = `last_check` je Pack.")
    lines.append("")
    lines.append("## Übersicht")
    lines.append("")
    lines.append(f"- Source-Packs: **{len(packs)}**")
    lines.append(f"- legal-frei: **{len(legal_free)}**, Grauzone Tier A/B: **{len(gray)}**")
    lines.append(f"- live verifiziert: **{len(verified)}** ({', '.join(p.get('id','') for p in verified) or '–'})")
    lines.append(
        "- Fixtures: "
        + f"{used_fixtures.count('recorded')} recorded, "
        + f"{used_fixtures.count('synthetic')} synthetic, "
        + f"{used_fixtures.count('–')} ohne"
    )
    lines.append(f"- Hoster-Packs: **{len(hosters)}** (live verifiziert: "
                 f"{sum(1 for h in hosters if h.get('verified') is True)})")
    lines.append("")
    lines.append("## Source-Packs")
    lines.append("")
    lines.append("| ID | Tier | legal-frei | Kind | verified | last_check | Fixture | Offen / Grund |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for pack in packs:
        fixture = fixture_provenance(root, pack.get("id", ""))
        lines.append(
            "| `{id}` | {tier} | {lf} | {kind} | {verified} | {last} | {fixture} | {reason} |".format(
                id=pack.get("id", ""),
                tier=pack.get("tier", "–"),
                lf="ja" if pack.get("legalFree") else "nein",
                kind=pack.get("kind", "html"),
                verified="ja" if pack.get("verified") is True else "nein",
                last=pack.get("last_check") or "–",
                fixture=fixture,
                reason=open_reason(pack),
            )
        )

    lines.append("")
    lines.append("## Hoster-Packs")
    lines.append("")
    lines.append("| ID | Domains | Schritte | verified |")
    lines.append("|---|---|---|---|")
    for hoster in hosters:
        lines.append(
            "| `{id}` | {domains} | {steps} | {verified} |".format(
                id=hoster.get("id", ""),
                domains=", ".join(hoster.get("domains") or []),
                steps=len(hoster.get("steps") or []),
                verified="ja" if hoster.get("verified") is True else "nein",
            )
        )

    lines.append("")
    lines.append("## Regeln")
    lines.append("")
    lines.append("- `verified: true` nur mit recorded Fixture oder echtem Worker-Lauf.")
    lines.append("- Packs ohne belegtes Ziel bleiben `verified: false` und stehen hier offen.")
    lines.append("- Ein Pack mit Selektorverlust wird markiert, nicht gelöscht.")
    lines.append("- Grauzone-Packs ohne HTML-Beleg bleiben unverifiziert; es werden keine")
    lines.append("  Selektoren erfunden.")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parent.parent))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    target = root / "docs/PACKS.md"
    text = render(root)
    if args.check:
        if not target.is_file():
            print("FAIL: docs/PACKS.md fehlt")
            return 1
        if target.read_text(encoding="utf-8") != text:
            print("FAIL: docs/PACKS.md ist veraltet — tools/packs_report.py laufen lassen")
            return 1
        print("OK: docs/PACKS.md ist aktuell")
        return 0
    target.write_text(text, encoding="utf-8")
    print(f"geschrieben: {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
