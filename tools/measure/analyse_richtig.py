#!/usr/bin/env python3
"""Auswertung der Richtigkeitsmessung (Gate 1, 1.6.0).

Liest die Poll-Dumps eines Laufs und zaehlt:
  - QUOTA IDENT  je Titel (gespielt ja/nein, ident=Klasse, score)
  - QUOTA RIGHT SUMMARY / GROUPEN als Gegenprobe zur eigenen Zaehlung

Aufruf:
    python3 -I analyse_richtig.py <lauf-dir> [modus]

Der Pfad kommt aus der Kommandozeile und wird vor jedem Zugriff geprueft
(keine `..`-Segmente, existierendes Verzeichnis, keine Datei-Symlinks).
"""
import glob
import html
import os
import pathlib
import re
import sys
from collections import Counter, defaultdict

IDENT = re.compile(
    r"QUOTA IDENT (train|holdout) idx=(\d+) id=(\S+) group=(\S+) "
    r"played=(ja|nein) ident=([A-Z_\-]+) score=([0-9.]+) evidenz=(\S+)"
    r"(?: grund=(.*))?"
)
RIGHT_SUMMARY = re.compile(r"QUOTA RIGHT SUMMARY mode=(\w+) richtig=(\d+)/(\d+) quote=(\d+) gespielt=(\d+)/(\d+)")
RIGHT_GROUP = re.compile(r"QUOTA RIGHT GROUPEN mode=(\w+) (.*)")


def checked_run_dir(raw: str) -> str:
    if ".." in pathlib.PurePath(raw).parts:
        raise SystemExit(f"ungueltiger Lauf-Pfad (enthaelt '..'): {raw}")
    path = pathlib.Path(raw).expanduser()
    if not path.is_dir():
        raise SystemExit(f"kein Verzeichnis: {raw}")
    return str(path.resolve())


def read_texts(run_dir: str):
    for path in sorted(glob.glob(os.path.join(run_dir, "**", "*.xml"), recursive=True)):
        if os.path.islink(path):
            continue
        try:
            yield pathlib.Path(path).read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue


def main(run_dir: str, mode: str = "") -> int:
    run_dir = checked_run_dir(run_dir)
    seen = {}
    summary = None
    groups = None
    for text in read_texts(run_dir):
        # UIAutomator steckt den ganzen ScrollView-Text in einzelne text-Attribute;
        # Entities einmalig auflösen und über den gesamten Text suchen.
        decoded = html.unescape(" ".join(re.findall(r"text=\"([^\"]*)\"", text)))
        m = RIGHT_SUMMARY.search(decoded)
        if m and (not mode or m.group(1) == mode):
            summary = m.groups()
        g = RIGHT_GROUP.search(decoded)
        if g and (not mode or g.group(1) == mode):
            groups = g.group(2)
        for m2 in IDENT.finditer(decoded):
            if mode and m2.group(1) != mode:
                continue
            idx = m2.group(2)
            current = seen.get(idx)
            # Spätere/vollständigere Dumps gewinnen: gespielt vor nein,
            # konkrete Identitätsklasse vor "-".
            if current is None or (m2.group(5), m2.group(6) != "-") >= (current[4], current[5] != "-"):
                seen[idx] = m2.groups()

    if not seen:
        print("keine QUOTA IDENT-Zeilen gefunden")
        return 1

    played = {i: v for i, v in seen.items() if v[4] == "ja"}
    richtig = {i: v for i, v in played.items() if v[5] == "CORRECT"}
    per_group = defaultdict(lambda: {"titel": 0, "gespielt": 0, "richtig": 0, "fehler": Counter()})
    for idx, v in seen.items():
        group = v[3]
        entry = per_group[group]
        entry["titel"] += 1
        if v[4] == "ja":
            entry["gespielt"] += 1
            if v[5] == "CORRECT":
                entry["richtig"] += 1
            else:
                entry["fehler"][v[5]] += 1

    total = len(seen)
    print(f"Titel mit IDENT-Zeile: {total}")
    print(f"gespielt: {len(played)}  richtig: {len(richtig)}  "
          f"Richtigkeitsquote gesamt: {len(richtig) * 100 // max(total, 1)} %")
    print(f"Richtigkeitsquote der gespielten: {len(richtig) * 100 // max(len(played), 1)} %")
    print()
    print("| Gruppe | Titel | gespielt | richtig | Quote | falsch |")
    print("|---|---|---|---|---|---|")
    for group in sorted(per_group):
        e = per_group[group]
        quote = e["richtig"] * 100 // e["titel"] if e["titel"] else 0
        err = ", ".join(f"{k}:{v}" for k, v in e["fehler"].most_common()) or "-"
        print(f"| {group} | {e['titel']} | {e['gespielt']} | {e['richtig']} | {quote} % | {err} |")
    print()
    not_played = {i: v for i, v in seen.items() if v[4] == "nein"}
    if not_played:
        print(f"nicht gespielt: {len(not_played)} (ident={Counter(v[5] for v in not_played.values())})")
    falsch = [(i, v) for i, v in played.items() if v[5] != "CORRECT"]
    if falsch:
        print("\nGespielt, aber nicht bestätigt:")
        for idx, v in sorted(falsch, key=lambda x: int(x[0])):
            print(f"  idx={idx:<3} {v[3]:<14} ident={v[5]:<14} score={v[6]} evidenz={v[7]} "
                  f"{(v[8] or '')[:60]}")
    if summary:
        print(f"\nHarness-Summary: richtig={summary[1]}/{summary[2]} quote={summary[3]} "
              f"gespielt={summary[4]}/{summary[5]}")
    if groups:
        print(f"Harness-Gruppen: {groups}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "")
