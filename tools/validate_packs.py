#!/usr/bin/env python3
"""Frame61 Pack-/Asset-Validator.

Prueft die JSON-Assets gegen den Vertrag in docs/SOURCE_PACK.md und
docs/SKINS.md. Keine externen Abhaengigkeiten, kein Netz.

Wahrheitsregel: `"verified": true` ist nur zulaessig, wenn ein Fixture mit
`"provenance": "recorded"` und `responseSha256` existiert. Ein synthetisches
Fixture belegt den Parser, nicht den Live-Stand einer Seite.

Exit 0 = keine Fehler, Exit 1 = mindestens ein Fehler.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

RULE_KEYS = ("search", "detail", "episodes", "streams")
SKIN_TOKENS = (
    "id",
    "canvas",
    "surfaces",
    "accents",
    "text",
    "radius",
    "cardAspect",
    "rowHeight",
    "gutter",
    "navModel",
    "focusTreatment",
    "motionLevel",
    "typeScale",
    "billboardHeight",
    "detailLayout",
    "playerChrome",
)
HOSTER_ACTIONS = {"get", "regex", "json", "post", "sleep"}
COLOR_RE = re.compile(r"^#[0-9a-fA-F]{6}$")


class Report:
    def __init__(self) -> None:
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.counts: dict[str, int] = {}

    def error(self, where: str, message: str) -> None:
        self.errors.append(f"{where}: {message}")

    def warn(self, where: str, message: str) -> None:
        self.warnings.append(f"{where}: {message}")

    def count(self, key: str) -> None:
        self.counts[key] = self.counts.get(key, 0) + 1


def load_json(path: Path, report: Report) -> object | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        report.error(str(path), "Datei fehlt")
    except json.JSONDecodeError as exc:
        report.error(str(path), f"kein gueltiges JSON: {exc}")
    return None


def non_empty(value: object) -> bool:
    return isinstance(value, str) and value.strip() != ""


def validate_rule(where: str, name: str, rule: object, kind: str, report: Report) -> None:
    if not isinstance(rule, dict):
        report.error(where, f"Regel '{name}' ist kein Objekt")
        return
    if rule.get("method") not in (None, "GET", "POST"):
        report.error(where, f"Regel '{name}': method={rule.get('method')!r}")
    if (
        name in ("search", "detail")
        and not non_empty(rule.get("endpoint"))
        and not non_empty(rule.get("path"))
    ):
        report.warn(where, f"Regel '{name}' hat kein endpoint/path")

    if kind == "html":
        if name == "search" and not non_empty(rule.get("resultSelector")):
            report.error(where, "search.resultSelector fehlt (html-Pack)")
        if name == "streams" and not non_empty(rule.get("selector")):
            report.error(where, "streams.selector fehlt (html-Pack)")
        if name == "episodes" and not non_empty(rule.get("episodeSelector")):
            report.error(where, "episodes.episodeSelector fehlt (html-Pack)")
    else:
        if name in ("search", "detail") and not (
            non_empty(rule.get("jsonPath")) or non_empty(rule.get("resultSelector"))
        ):
            report.error(where, f"{name}.jsonPath fehlt (api-Pack)")
        if name == "streams" and not (
            non_empty(rule.get("streamPath"))
            or non_empty(rule.get("jsonPath"))
            or non_empty(rule.get("streamUrlTemplate"))
        ):
            report.error(where, "streams.streamPath/jsonPath fehlt (api-Pack)")


def recorded_fixture(root: Path, pack_id: str) -> tuple[bool, str]:
    fixture_dir = root / "scraper/engine/src/test/resources/fixtures" / pack_id
    meta_path = fixture_dir / "meta.json"
    if not meta_path.is_file():
        return False, "kein Fixture-meta.json"
    meta = load_json(meta_path, Report())
    if not isinstance(meta, dict):
        return False, "Fixture-meta ist kein Objekt"
    if meta.get("provenance") != "recorded":
        return False, f"Fixture-Provenienz ist {meta.get('provenance')!r}, nicht 'recorded'"
    if not non_empty(meta.get("responseSha256")):
        return False, "Fixture-meta ohne responseSha256"
    return True, ""


def validate_source_pack(path: Path, root: Path, report: Report) -> None:
    data = load_json(path, report)
    if not isinstance(data, dict):
        if data is not None:
            report.error(str(path), "Wurzel ist kein Objekt")
        return
    where = path.name
    report.count("source_packs")

    for field in ("id", "name", "baseUrl"):
        if not non_empty(data.get(field)):
            report.error(where, f"{field} fehlt oder ist leer")
    pack_id = data.get("id")
    if non_empty(pack_id) and pack_id != path.stem:
        report.error(where, f"id={pack_id!r} != Dateiname {path.stem!r}")

    kind = data.get("kind", "html")
    if kind not in ("html", "api"):
        report.error(where, f"kind={kind!r} ist nicht html|api")

    rules = [name for name in RULE_KEYS if name in data]
    if not rules:
        report.error(where, "keine Regel (search/detail/episodes/streams)")
        return
    addressable = 0
    for name in rules:
        rule = data[name]
        if isinstance(rule, dict) and (
            non_empty(rule.get("endpoint")) or non_empty(rule.get("path"))
        ):
            addressable += 1
        validate_rule(where, name, rule, kind, report)
    if addressable == 0:
        report.error(where, "keine Regel mit endpoint/path")

    if data.get("verified") is True:
        if not non_empty(pack_id):
            report.error(where, "verified=true ohne id")
        else:
            ok, reason = recorded_fixture(root, pack_id)
            if not ok:
                report.error(where, f"verified=true nicht belegbar: {reason}")


def validate_hoster(path: Path, report: Report) -> None:
    data = load_json(path, report)
    if not isinstance(data, dict):
        if data is not None:
            report.error(str(path), "Wurzel ist kein Objekt")
        return
    where = path.name
    report.count("hosters")
    for field in ("id", "name"):
        if not non_empty(data.get(field)):
            report.error(where, f"{field} fehlt oder ist leer")
    if non_empty(data.get("id")) and data.get("id") != path.stem:
        report.error(where, f"id={data.get('id')!r} != Dateiname {path.stem!r}")
    domains = data.get("domains")
    if not isinstance(domains, list) or not domains:
        report.error(where, "domains fehlt oder ist leer")
    steps = data.get("steps")
    if not isinstance(steps, list) or not steps:
        report.error(where, "steps fehlt oder ist leer")
        return
    for index, step in enumerate(steps):
        if not isinstance(step, dict):
            report.error(where, f"steps[{index}] ist kein Objekt")
            continue
        if step.get("action") not in HOSTER_ACTIONS:
            report.error(where, f"steps[{index}].action={step.get('action')!r}")
        if step.get("action") in ("get", "post") and not non_empty(step.get("url")):
            report.error(where, f"steps[{index}] ohne url")
        if step.get("action") == "regex" and not non_empty(step.get("pattern")):
            report.error(where, f"steps[{index}] ohne pattern")
        if step.get("action") == "json" and not non_empty(step.get("path")):
            report.error(where, f"steps[{index}] ohne path")
    if data.get("verified") is True:
        report.warn(where, "verified=true, aber es gibt keinen Hoster-Live-Nachweis im Repo")


def validate_skin(path: Path, report: Report) -> None:
    data = load_json(path, report)
    if not isinstance(data, dict):
        if data is not None:
            report.error(str(path), "Wurzel ist kein Objekt")
        return
    where = path.name
    report.count("skins")
    for token in SKIN_TOKENS:
        if token not in data:
            report.error(where, f"Token '{token}' fehlt")
    if non_empty(data.get("id")) and data.get("id") != path.stem:
        report.error(where, f"id={data.get('id')!r} != Dateiname {path.stem!r}")
    for token in ("canvas", "surfaces", "accents", "text"):
        value = data.get(token)
        if isinstance(value, str) and not COLOR_RE.match(value):
            report.error(where, f"{token}={value!r} ist keine #RRGGBB-Farbe")


def validate_catalog(path: Path, report: Report) -> None:
    data = load_json(path, report)
    if not isinstance(data, dict):
        if data is not None:
            report.error(str(path), "Wurzel ist kein Objekt")
        return
    where = path.name
    report.count("catalogs")
    items = data.get("items")
    if not isinstance(items, list) or not items:
        report.error(where, "items fehlt oder ist leer")
        return
    seen: set[str] = set()
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            report.error(where, f"items[{index}] ist kein Objekt")
            continue
        item_id = item.get("id")
        if not non_empty(item_id):
            report.error(where, f"items[{index}].id fehlt")
            continue
        if item_id in seen:
            report.error(where, f"doppelte id {item_id!r}")
        seen.add(item_id)
        if not non_empty(item.get("title")):
            report.error(where, f"items[{index}] ({item_id}) ohne title")
        if item.get("type") not in ("movie", "series"):
            report.error(where, f"items[{index}] ({item_id}) type={item.get('type')!r}")
    report.counts["catalog_items"] = len(items)


def validate_fixtures(root: Path, report: Report) -> None:
    fixture_root = root / "scraper/engine/src/test/resources/fixtures"
    if not fixture_root.is_dir():
        report.error(str(fixture_root), "Fixture-Verzeichnis fehlt")
        return
    for directory in sorted(p for p in fixture_root.iterdir() if p.is_dir()):
        report.count("fixture_dirs")
        meta = load_json(directory / "meta.json", report)
        if not isinstance(meta, dict):
            if meta is not None:
                report.error(str(directory / "meta.json"), "kein Objekt")
            continue
        provenance = meta.get("provenance")
        if provenance not in ("recorded", "synthetic"):
            report.error(str(directory / "meta.json"), f"provenance={provenance!r}")
        if not non_empty(meta.get("capturedAt")):
            report.error(str(directory / "meta.json"), "capturedAt fehlt")
        if provenance == "recorded":
            if not non_empty(meta.get("sourceUrl")):
                report.error(str(directory / "meta.json"), "recorded ohne sourceUrl")
            if not non_empty(meta.get("responseSha256")):
                report.error(str(directory / "meta.json"), "recorded ohne responseSha256")
            bodies = list(directory.glob("*.body.*"))
            if not bodies:
                report.error(str(directory), "recorded ohne *.body.*")
        for expected in directory.glob("*.expected.json"):
            load_json(expected, report)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        default=str(Path(__file__).resolve().parent.parent),
        help="Frame61-Repo-Wurzel",
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    report = Report()

    for path in sorted((root / "app/src/main/assets/sources").glob("*.json")):
        validate_source_pack(path, root, report)
    for path in sorted((root / "app/src/main/assets/hosters").glob("*.json")):
        validate_hoster(path, report)
    for path in sorted((root / "app/src/main/assets/skins").glob("*.json")):
        validate_skin(path, report)
    catalog = root / "app/src/main/assets/catalog/catalog.json"
    if catalog.is_file():
        validate_catalog(catalog, report)
    else:
        report.error(str(catalog), "Katalog fehlt")
    validate_fixtures(root, report)

    print(
        "Geprueft: "
        + ", ".join(f"{value} {key}" for key, value in sorted(report.counts.items()))
    )
    for warning in report.warnings:
        print(f"WARN  {warning}")
    for error in report.errors:
        print(f"ERROR {error}")
    if report.errors:
        print(f"FAIL: {len(report.errors)} Fehler, {len(report.warnings)} Warnungen")
        return 1
    print(f"OK: 0 Fehler, {len(report.warnings)} Warnungen")
    return 0


if __name__ == "__main__":
    sys.exit(main())
