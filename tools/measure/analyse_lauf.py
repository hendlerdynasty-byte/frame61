#!/usr/bin/env python3
"""Auswertung eines Titel-Quote-Laufs (A52) aus den UI-Dumps.

Liest die `QUOTA train`- bzw. `QUOTA holdout`-Zeilen aus den Poll-Dumps eines
Laufs und liefert: Quote, Gruppenquote mit Delta zum Vergleichslauf,
Zeit-Perzentile, Fehlerklassen und die geänderten Titel.

Aufruf:
    python3 -I analyse_lauf.py <lauf-dir> [vergleichs-lauf-dir] [modus]

`modus` ist `train` oder `holdout`; ohne Angabe werden beide Modi gelesen
(die beiden Stichproben sind disjunkt, die Indexierung bleibt damit eindeutig).
"""
import glob
import os
import pathlib
import re
import sys
from collections import Counter, defaultdict

LINE = re.compile(
    r"QUOTA (train|holdout) idx=(\d+) id=(\S+) group=(\S+) type=(\S+)"
    r"(?: title=(.*?))? RESULT=(\w+) class=(\S+) resolveMs=(\d+)"
    r"(?: playMs=(\d+))?(?: firstFrameMs=(\d+))?"
    r" candidates=(\d+) tried=(\d+)"
    r"(?: hosts=\[([^\]]*)\])?(?: host=(\S+))?"
    r"(?: intercept=(\d+))?(?: medien=(\d+))?(?: maxMs=(\d+))?"
)


def checked_run_dir(raw: str) -> str:
    """Prueft den Lauf-Pfad aus der Kommandozeile.

    Der Pfad bestimmt, welche Dateien gelesen werden. Er kommt aus der
    Kommandozeile und wird deshalb **validiert**, bevor er irgendwo landet:
    keine `..`-Segmente, nur ein existierendes Verzeichnis, und Symlinks
    auf Dateiebene werden uebersprungen (siehe `read_dumps`).
    """
    if ".." in pathlib.PurePath(raw).parts:
        raise SystemExit(f"ungueltiger Lauf-Pfad (enthaelt '..'): {raw}")
    path = pathlib.Path(raw).expanduser()
    if not path.is_dir():
        raise SystemExit(f"kein Verzeichnis: {raw}")
    return str(path.resolve())


def read_dumps(run_dir: str):
    """Alle Poll-Dumps eines Laufs — ohne Symlinks."""
    for path in sorted(glob.glob(os.path.join(run_dir, "p*", "*.xml"))):
        if os.path.islink(path):
            continue
        yield path


def load(run_dir, mode=None):
    """Alle eindeutigen Titelzeilen eines Laufs (spaetester Dump gewinnt)."""
    run_dir = checked_run_dir(run_dir)
    rows = {}
    for path in read_dumps(run_dir):
        text = open(path, encoding="utf-8", errors="replace").read()
        for raw in text.split("&#10;"):
            m = LINE.match(raw)
            if not m:
                continue
            g = m.groups()
            if mode and g[0] != mode:
                continue
            rows[int(g[1])] = {
                "mode": g[0], "idx": int(g[1]), "id": g[2], "group": g[3], "type": g[4],
                "title": (g[5] or "").strip(), "result": g[6], "class": g[7],
                "resolve_ms": int(g[8]), "play_ms": int(g[9] or 0),
                "first_ms": int(g[10] or 0), "candidates": int(g[11]),
                "tried": int(g[12]),
                "hosts": [h.strip() for h in (g[13] or "").split(",") if h.strip()],
                "host": g[14] or "", "intercept": int(g[15] or 0),
                "medien": int(g[16] or 0), "max_ms": int(g[17] or 0),
            }
    return rows


def pct(values, p):
    if not values:
        return 0
    values = sorted(values)
    k = max(0, min(len(values) - 1, round((p / 100.0) * (len(values) - 1))))
    return values[k]


def report(run_dir, compare_dir=None, mode=None):
    rows = load(run_dir, mode)
    ok = [r for r in rows.values() if r["result"] == "OK"]
    fails = [r for r in rows.values() if r["result"] != "OK"]
    print(f"Lauf: {run_dir}" + (f"   Modus: {mode}" if mode else ""))
    print(f"Titel: {len(rows)}   OK: {len(ok)} ({len(ok)*100.0/max(len(rows),1):.0f} %)"
          f"   FAIL: {len(fails)}")

    groups = defaultdict(lambda: [0, 0])
    for r in rows.values():
        groups[r["group"]][1] += 1
        if r["result"] == "OK":
            groups[r["group"]][0] += 1

    compare = load(compare_dir, mode) if compare_dir else {}
    print("\n| Gruppe | Titel | spielbar | Quote | Vergleich | Delta | häufigste Fehlerklasse |")
    print("|---|---|---|---|---|---|---|")
    for group in sorted(groups, key=lambda g: -groups[g][1]):
        good, total = groups[group]
        quote = good * 100.0 / total
        old = ""
        delta = ""
        if compare:
            old_rows = [r for r in compare.values() if r["group"] == group]
            if old_rows:
                old_good = sum(1 for r in old_rows if r["result"] == "OK")
                old_quote = old_good * 100.0 / len(old_rows)
                old = f"{old_good*100//len(old_rows)} %"
                delta = f"{quote - old_quote:+.0f} pp"
        classes = Counter(r["class"] for r in rows.values()
                          if r["group"] == group and r["result"] != "OK")
        worst = classes.most_common(1)
        worst = f"`{worst[0][0]}`: {worst[0][1]}" if worst else "—"
        print(f"| {group} | {total} | {good} | **{quote:.0f} %** | {old or '—'} | {delta or '—'} | {worst} |")

    print("\nFehlerklassen:", dict(Counter(r["class"] for r in fails)))
    if ok:
        first = [r["first_ms"] for r in ok]
        resolve = [r["resolve_ms"] for r in ok]
        print(f"resolveMs p50/p95: {pct(resolve,50)} / {pct(resolve,95)}")
        print(f"firstFrameMs p50/p95: {pct(first,50)} / {pct(first,95)}")
    print(f"Interception-Versuche: {sum(r['intercept'] for r in rows.values())}   "
          f"mit Medien-URL: {sum(1 for r in rows.values() if r['medien'] > 0)}")

    hosts = Counter(h for r in fails for h in (r["hosts"] or ([r["host"]] if r["host"] else [])))
    print("\nFehlziel-Hosts (FAIL-Titel):", dict(hosts.most_common()))

    if compare:
        print("\nGeänderte Titel:")
        for idx in sorted(set(rows) & set(compare)):
            a, b = compare[idx], rows[idx]
            if a["result"] != b["result"]:
                print(f"  idx={idx} {a['result']} ({a['host'] or ','.join(a['hosts'])})"
                      f" -> {b['result']} ({b['host'] or ','.join(b['hosts'])})")
    return rows


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    report(sys.argv[1],
           sys.argv[2] if len(sys.argv) > 2 else None,
           sys.argv[3] if len(sys.argv) > 3 else None)
