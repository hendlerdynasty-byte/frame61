#!/usr/bin/env python3
"""Ursachen-Auswertung eines Titel-Quote-Laufs (A52).

Liest die `QUOTA CAUSE`-Zeilen (nur ab Lauf 5 vorhanden) aus den Poll-Dumps
und zeigt, welche Resolver-Stufe den Titel gekippt hat.

Aufruf:
    python3 -I analyse_ursachen.py <lauf-dir> [modus]

`modus` ist `train` oder `holdout` (Default: beide).
"""
import glob
import os
import re
import sys
from collections import Counter

TEXT = re.compile(r'text="([^"]*)"')
CAUSE = re.compile(r"QUOTA CAUSE (\w+) idx=(\d+) (.*)")
ROW = re.compile(r"QUOTA (train|holdout) idx=(\d+).*RESULT=(\w+) class=(\S+)(.*)")


def texts(path):
    raw = open(path, encoding="utf-8", errors="replace").read()
    for value in TEXT.findall(raw):
        for part in value.split("&#10;"):
            if part.startswith("QUOTA"):
                yield part.replace(chr(34), "'").replace("&", "&")


def main(run_dir, mode=None):
    causes = {}
    fails = {}
    for path in sorted(glob.glob(os.path.join(run_dir, "p*", "*.xml"))):
        for line in texts(path):
            m = CAUSE.match(line)
            if m:
                if mode and m.group(1) != mode:
                    continue
                causes[int(m.group(2))] = m.group(3)
                continue
            m = ROW.match(line)
            if m:
                if mode and m.group(1) != mode:
                    continue
                fails[int(m.group(2))] = (m.group(3), m.group(4), m.group(5))

    print(f"Titel mit Ursachenkette: {len(causes)}")
    print("\nUrsache im Klartext (cause= der FAIL-Zeile):")
    counter = Counter()
    for idx, (result, klass, rest) in sorted(fails.items()):
        if result == "OK":
            continue
        m = re.search(r"cause=(\S*)", rest)
        cause = m.group(1) if m else "(ohne cause)"
        counter[cause.split(":")[0]] += 1
    for cause, count in counter.most_common():
        print(f"  {count:>3} x {cause}")

    print("\nKette je fehlgeschlagenem Titel (letzte 2 Stufen):")
    for idx, (result, klass, rest) in sorted(fails.items()):
        if result == "OK":
            continue
        chain = causes.get(idx, "(keine Kette)")
        stages = [s for s in chain.split(" | ")]
        print(f"  idx={idx:<3} {klass:<22} {stages[-2:] if stages else []}")

    dev = [(idx, c) for idx, c in sorted(causes.items()) if "devideosrc" in c]
    print(f"\nTitel mit devideosrc-Stufe in der Kette: {len(dev)}")
    for idx, chain in dev[:12]:
        stages = [s for s in chain.split(" | ") if "devideosrc" in s]
        print(f"  idx={idx:<3} {stages}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
