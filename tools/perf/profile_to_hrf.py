#!/usr/bin/env python3
"""Konvertiert einen ART-Profil-Dump in ein Baseline-HRF.

Aufnahme auf dem Geraet:
    adb shell cmd package dump-profiles de.hendler.frame61
    adb pull /data/misc/profman/de.hendler.frame61-primary.prof.txt

Dann:
    python3 -I tools/perf/profile_to_hrf.py <dump.txt> app/src/main/baseline-prof.txt

Der Dump listet Methoden als "<return> <class>.<method>(<params>)". Die
Baseline-Datei erwartet "HSP<Lclass>;-><method>(<params>)<return>".
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

PRIMITIVES = {
    "void": "V",
    "boolean": "Z",
    "byte": "B",
    "char": "C",
    "short": "S",
    "int": "I",
    "long": "J",
    "float": "F",
    "double": "D",
}


def descriptor(type_name: str) -> str | None:
    type_name = type_name.strip()
    if not type_name:
        return None
    if type_name.endswith("[]"):
        inner = descriptor(type_name[:-2])
        return f"[{inner}" if inner else None
    if type_name in PRIMITIVES:
        return PRIMITIVES[type_name]
    if type_name.startswith("["):
        return type_name.replace(".", "/")
    if re.fullmatch(r"[A-Za-z0-9_.$]+", type_name):
        return "L" + type_name.replace(".", "/") + ";"
    return None


def convert_line(line: str) -> str | None:
    line = line.strip().rstrip(",")
    if "(" not in line or not (line.endswith(")") or line.endswith(")[]")):
        return None
    parts = line.split(" ", 1)
    if len(parts) != 2:
        return None
    return_type, signature = parts
    array_return = signature.endswith("[]")
    if array_return:
        signature = signature[:-2]
    if not signature.endswith(")"):
        return None
    open_paren = signature.find("(")
    if open_paren <= 0:
        return None
    member = signature[:open_paren]
    params = signature[open_paren + 1 : -1]
    if "." not in member:
        return None
    class_name, method = member.rsplit(".", 1)
    class_desc = descriptor(class_name)
    return_desc = descriptor(return_type + ("[]" if array_return else ""))
    if not class_desc or not return_desc:
        return None
    if params.strip():
        param_descs = []
        for param in params.split(","):
            param_desc = descriptor(param)
            if not param_desc:
                return None
            param_descs.append(param_desc)
        params_desc = "".join(param_descs)
    else:
        params_desc = ""
    return f"HSP{class_desc}->{method}({params_desc}){return_desc}"


def main() -> int:
    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    seen: set[str] = set()
    with source.open(encoding="utf-8", errors="replace") as handle:
        for line in handle:
            converted = convert_line(line)
            if converted:
                seen.add(converted)
    target.write_text("\n".join(sorted(seen)) + "\n", encoding="utf-8")
    print(f"{len(seen)} Methoden -> {target}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
