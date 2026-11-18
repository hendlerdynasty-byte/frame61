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
HOSTER_ACTIONS = {"get", "regex", "json", "post", "sleep", "copy", "js", "pow"}
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
        if rule.get("idFromTitle") is True:
            pattern = rule.get("idRegex")
            if pattern:
                try:
                    re.compile(pattern)
                except re.error as exc:
                    report.error(where, f"search.idRegex ungültig: {exc}")
            group = rule.get("idGroup", 1)
            if not isinstance(group, int) or group < 1:
                report.error(where, f"search.idGroup={group!r} (>=1 erwartet)")
        if name == "streams" and not non_empty(rule.get("selector")):
            report.error(where, "streams.selector fehlt (html-Pack)")
        if name == "episodes" and not non_empty(rule.get("episodeSelector")):
            report.error(where, "episodes.episodeSelector fehlt (html-Pack)")
    else:
        if name in ("search", "detail") and not (
            non_empty(rule.get("jsonPath"))
            or non_empty(rule.get("resultSelector"))
            or non_empty(rule.get("jsonExtractRegex"))
        ):
            report.error(where, f"{name}.jsonPath fehlt (api-Pack)")
        pattern = rule.get("jsonExtractRegex")
        if pattern:
            try:
                re.compile(pattern)
            except re.error as exc:
                report.error(where, f"{name}.jsonExtractRegex ungültig: {exc}")
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


def hoster_fixture_dir(root: Path, pack_id: str) -> Path:
    return root / "scraper/resolver/src/test/resources/fixtures-hosters" / pack_id


def recorded_hoster_fixture(root: Path, pack_id: str) -> tuple[bool, str]:
    fixture_dir = hoster_fixture_dir(root, pack_id)
    meta_path = fixture_dir / "meta.json"
    if not meta_path.is_file():
        return False, "kein Hoster-Fixture-meta.json"
    meta = load_json(meta_path, Report())
    if not isinstance(meta, dict):
        return False, "Hoster-Fixture-meta ist kein Objekt"
    if meta.get("id") != pack_id:
        return False, f"Fixture-id {meta.get('id')!r} != {pack_id!r}"
    if meta.get("provenance") != "recorded":
        return False, f"Fixture-Provenienz ist {meta.get('provenance')!r}, nicht 'recorded'"
    if not non_empty(meta.get("responseSha256")):
        return False, "Hoster-Fixture ohne responseSha256"
    if not non_empty(meta.get("sourceUrl")):
        return False, "Hoster-Fixture ohne sourceUrl"
    if not list(fixture_dir.glob("*.body.*")):
        return False, "Hoster-Fixture ohne *.body.*"
    if not list(fixture_dir.glob("*.expected.json")):
        return False, "Hoster-Fixture ohne *.expected.json"
    return True, ""


def validate_source_pack(
    path: Path, root: Path, report: Report, check_filename: bool = True
) -> None:
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
    if check_filename and non_empty(pack_id) and pack_id != path.stem:
        report.error(where, f"id={pack_id!r} != Dateiname {path.stem!r}")

    kind = data.get("kind", "html")
    if kind not in ("html", "api"):
        report.error(where, f"kind={kind!r} ist nicht html|api")
    if data.get("captcha") not in (None, "webview"):
        report.error(where, f"captcha={data.get('captcha')!r} (nur 'webview')")

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
        effective_kind = kind
        if isinstance(rule, dict):
            fmt = rule.get("format")
            if fmt is not None and fmt not in ("html", "api"):
                report.error(where, f"Regel '{name}': format={fmt!r}")
            elif fmt is not None:
                effective_kind = fmt
        validate_rule(where, name, rule, effective_kind, report)
    if addressable == 0:
        report.error(where, "keine Regel mit endpoint/path")

    if data.get("verified") is True:
        if not non_empty(pack_id):
            report.error(where, "verified=true ohne id")
        else:
            ok, reason = recorded_fixture(root, pack_id)
            if not ok:
                report.error(where, f"verified=true nicht belegbar: {reason}")


def validate_hoster(path: Path, root: Path, report: Report) -> None:
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
        if step.get("action") == "copy" and not non_empty(step.get("url")):
            report.error(where, f"steps[{index}] copy ohne url")
        if step.get("action") == "js" and not non_empty(step.get("script")):
            report.error(where, f"steps[{index}] js ohne script")
        if step.get("action") == "pow":
            if not non_empty(step.get("input")):
                report.error(where, f"steps[{index}] pow ohne input")
            algorithm = step.get("algorithm")
            if algorithm not in ("filemoon-v1", "sha256"):
                report.error(where, f"steps[{index}] pow algorithm={algorithm!r}")
            elif algorithm == "filemoon-v1":
                if step.get("difficulty") is None and not non_empty(step.get("difficultyInput")):
                    report.error(where, f"steps[{index}] pow ohne difficulty/difficultyInput")
            elif not non_empty(step.get("targetPrefix")):
                report.error(where, f"steps[{index}] pow ohne targetPrefix")

    check = data.get("check")
    if not isinstance(check, dict) or not non_empty(check.get("status")):
        report.error(where, "check.status fehlt (Worker nicht gelaufen)")
    if data.get("verified") is True:
        pack_id = data.get("id")
        if not non_empty(pack_id):
            report.error(where, "verified=true ohne id")
        else:
            ok, reason = recorded_hoster_fixture(root, pack_id)
            if not ok:
                report.error(where, f"verified=true nicht belegbar: {reason}")
    elif isinstance(check, dict) and not non_empty(check.get("reason")):
        report.error(where, "verified=false ohne check.reason")


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


def validate_catalog(path: Path, root: Path, report: Report) -> None:
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
    seen_titles: set[str] = set()
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
        title = item.get("title")
        if not non_empty(title):
            report.error(where, f"items[{index}] ({item_id}) ohne title")
        else:
            key = f"{title.strip().lower()}|{item.get('year') or 0}"
            if key in seen_titles:
                report.error(where, f"doppelter Titel/Jahr {key!r}")
            seen_titles.add(key)
        if item.get("type") not in ("movie", "series"):
            report.error(where, f"items[{index}] ({item_id}) type={item.get('type')!r}")
        poster = item.get("poster")
        if not non_empty(poster):
            report.error(where, f"items[{index}] ({item_id}) ohne poster")
        elif poster.startswith("file:///android_asset/"):
            relative = poster.removeprefix("file:///android_asset/")
            if not (root / "app/src/main/assets" / relative).is_file():
                report.error(where, f"items[{index}] ({item_id}) Poster fehlt: {relative}")
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


def validate_hoster_fixtures(root: Path, report: Report) -> None:
    """Prueft die echten Hoster-Aufzeichnungen unter fixtures-hosters."""
    fixture_root = root / "scraper/resolver/src/test/resources/fixtures-hosters"
    if not fixture_root.is_dir():
        return
    for directory in sorted(p for p in fixture_root.iterdir() if p.is_dir()):
        report.count("hoster_fixtures")
        meta = load_json(directory / "meta.json", report)
        if not isinstance(meta, dict):
            if meta is not None:
                report.error(str(directory / "meta.json"), "kein Objekt")
            continue
        if meta.get("id") != directory.name:
            report.error(str(directory / "meta.json"), f"id={meta.get('id')!r} != {directory.name!r}")
        if meta.get("provenance") != "recorded":
            report.error(str(directory / "meta.json"), f"provenance={meta.get('provenance')!r}")
        if not non_empty(meta.get("capturedAt")):
            report.error(str(directory / "meta.json"), "capturedAt fehlt")
        if not non_empty(meta.get("sourceUrl")):
            report.error(str(directory / "meta.json"), "sourceUrl fehlt")
        if not non_empty(meta.get("responseSha256")):
            report.error(str(directory / "meta.json"), "responseSha256 fehlt")
        if not list(directory.glob("*.body.*")):
            report.error(str(directory), "recorded ohne *.body.*")
        for expected in directory.glob("*.expected.json"):
            data = load_json(expected, report)
            if isinstance(data, dict) and not non_empty(data.get("url")):
                report.error(str(expected), "url fehlt")


def validate_meta(root: Path, report: Report) -> None:
    """Prueft die Offline-Metadaten-Snapshots (assets/meta)."""
    meta_dir = root / "app/src/main/assets/meta"
    index_path = meta_dir / "index.json"
    if not meta_dir.is_dir() or not index_path.is_file():
        report.error(str(meta_dir), "Metadaten-Ordner oder index.json fehlt")
        return
    index = load_json(index_path, report)
    if not isinstance(index, dict):
        if index is not None:
            report.error(str(index_path), "Wurzel ist kein Objekt")
        return
    entries = index.get("entries")
    if not isinstance(entries, list) or not entries:
        report.error(str(index_path), "entries fehlt oder ist leer")
        return
    report.counts["meta_index_entries"] = len(entries)
    ids: set[str] = set()
    for position, entry in enumerate(entries):
        if not isinstance(entry, dict) or not non_empty(entry.get("id")):
            report.error(str(index_path), f"entries[{position}] ohne id")
            continue
        if entry["id"] in ids:
            report.error(str(index_path), f"doppelte id {entry['id']!r}")
        ids.add(entry["id"])

    files = sorted(p for p in meta_dir.glob("*.json") if p.name != "index.json")
    file_ids = {p.stem for p in files}
    for missing in sorted(ids - file_ids):
        report.error(str(meta_dir), f"index nennt {missing!r}, Datei fehlt")
    for extra in sorted(file_ids - ids):
        report.error(str(meta_dir), f"Datei {extra!r} fehlt im index")

    allowed_sources = {"tvmaze", "catalog", "wikipedia"}
    for path in files:
        report.count("meta_snapshots")
        data = load_json(path, report)
        if not isinstance(data, dict):
            if data is not None:
                report.error(str(path), "Wurzel ist kein Objekt")
            continue
        where = path.name
        if data.get("schema") != 1:
            report.error(where, f"schema={data.get('schema')!r} (erwartet 1)")
        if data.get("id") != path.stem:
            report.error(where, f"id={data.get('id')!r} != Dateiname {path.stem!r}")
        if not non_empty(data.get("title")):
            report.error(where, "title fehlt")
        if data.get("type") not in ("movie", "series"):
            report.error(where, f"type={data.get('type')!r}")
        if data.get("source") not in allowed_sources:
            report.error(where, f"source={data.get('source')!r}")
        if data.get("live") is True:
            if data.get("source") != "tvmaze":
                report.error(where, "live=true nur mit source=tvmaze")
            if not non_empty(data.get("sourceId")):
                report.error(where, "live=true ohne sourceId")
            if not non_empty(data.get("fetchedAt")):
                report.error(where, "live=true ohne fetchedAt")
        episodes = data.get("episodes")
        if not isinstance(episodes, list):
            report.error(where, "episodes ist keine Liste")
            continue
        seen_episode_ids: set[int] = set()
        for position, episode in enumerate(episodes):
            if not isinstance(episode, dict):
                report.error(where, f"episodes[{position}] ist kein Objekt")
                continue
            episode_id = episode.get("id")
            if not isinstance(episode_id, int):
                report.error(where, f"episodes[{position}].id ist keine Zahl")
            elif episode_id in seen_episode_ids:
                report.error(where, f"doppelte Episoden-id {episode_id}")
            else:
                seen_episode_ids.add(episode_id)
            if not isinstance(episode.get("season"), int) or not isinstance(
                episode.get("number"), int
            ):
                report.error(where, f"episodes[{position}] season/number fehlt")


def validate_gold(root: Path, report: Report) -> None:
    """Die zwei Gold-Fixtures muessen exakt dem eingefrorenen Schema folgen."""
    fixture_dir = root / "fixtures"
    gold_files = [fixture_dir / "gold-01.json", fixture_dir / "gold-02.json"]
    for path in gold_files:
        if not path.is_file():
            report.error(str(path), "Gold-Fixture fehlt")
            continue
        report.count("gold_fixtures")
        validate_source_pack(path, root, report, check_filename=False)


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
        validate_hoster(path, root, report)
    for path in sorted((root / "app/src/main/assets/skins").glob("*.json")):
        validate_skin(path, report)
    catalog = root / "app/src/main/assets/catalog/catalog.json"
    if catalog.is_file():
        validate_catalog(catalog, root, report)
    else:
        report.error(str(catalog), "Katalog fehlt")
    validate_fixtures(root, report)
    validate_hoster_fixtures(root, report)
    validate_meta(root, report)
    validate_gold(root, report)

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
