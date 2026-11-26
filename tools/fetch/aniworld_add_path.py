#!/usr/bin/env python3
"""Ergänzt lokale Metadaten/Bilder für Ersatz-Titel des Aniworld-Tiefenpfads.

Nutzt denselben sicheren TVmaze-/AniList-Generator wie aniworld_tvmaze.py.
Keine neuen Provider, keine Laufzeit-Abhängigkeit der App.
"""
from __future__ import annotations

import importlib.util
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "aniworld_tvmaze", ROOT / "tools/fetch/aniworld_tvmaze.py"
)
if SPEC is None or SPEC.loader is None:
    raise SystemExit("Generator nicht ladbar")
TOOL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(TOOL)

TARGETS = [
    {"id": "aw:spy-x-family", "query": "SPY x FAMILY", "aliases": ["SPY x FAMILY"], "type": "series", "yearHint": 2019},
    {"id": "aw:fullmetal-alchemist", "query": "Fullmetal Alchemist", "aliases": ["Fullmetal Alchemist"], "type": "series", "yearHint": 2003},
    {"id": "aw:gabriel-dropout", "query": "Gabriel Dropout", "aliases": ["Gabriel Dropout"], "type": "series", "yearHint": 2017},
    {"id": "aw:kabaneri-of-the-iron-fortress", "query": "Kabaneri of the Iron Fortress", "aliases": ["Kabaneri of the Iron Fortress"], "type": "series", "yearHint": 2016},
    {"id": "aw:radiant", "query": "Radiant", "aliases": ["Radiant"], "type": "series", "yearHint": 2018},
    {"id": "aw:bungo-stray-dogs", "query": "Bungo Stray Dogs", "aliases": ["Bungo Stray Dogs"], "type": "series", "yearHint": 2016},
    {"id": "aw:gto-great-teacher-onizuka", "query": "GTO Great Teacher Onizuka", "aliases": ["GTO: Great Teacher Onizuka"], "type": "series", "yearHint": 1999},
]


def main() -> int:
    assets = ROOT / "app/src/main/assets"
    catalog_path = assets / "catalog/catalog.json"
    index_path = assets / "meta/index.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    items = catalog["items"]
    by_id = {item["id"]: item for item in items}
    index = json.loads(index_path.read_text(encoding="utf-8"))
    updates = []
    for target in TARGETS:
        item = by_id[target["id"]]
        snapshot, update, report = TOOL.build_target(ROOT, target, item)
        TOOL.write_json(assets / "meta" / f"{target['id']}.json", snapshot, sort_keys=True)
        updates.append({"id": target["id"], **update})
        for field, value in update.items():
            item[field] = value
        print(target["id"], report["source"], report["episodes"], report["cast"], flush=True)
    generated_at = TOOL.iso_now()
    catalog["generatedAt"] = generated_at
    catalog_path.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    TOOL.write_json(index_path, TOOL.merge_index(index, updates, generated_at), sort_keys=True)
    print("OK: 7 Ersatz-Titel ergänzt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
