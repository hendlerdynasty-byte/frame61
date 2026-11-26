#!/usr/bin/env python3
"""Schreibt Metadaten-Snapshots nach app/src/main/assets/meta/.

Der Client macht keine Live-API-Calls mehr. Dieses Werkzeug laeuft vorab
(lokal oder per GitHub-Cron) und erzeugt pro Katalogtitel eine Datei
`<id>.json` plus `index.json`.

Quellen:
  * Serien: TVmaze `/shows/{id}?embed=episodes` (keyless, CC BY-SA).
  * Filme: der mitgelieferte Katalog (keine Live-Quelle noetig).

Regeln:
  * 20 Calls / 10 s (TVmaze-Limit), hier 0.6 s Abstand.
  * Retry mit Backoff (3 Versuche) bei Netz-/5xx-Fehlern.
  * Nie erfundene Daten: faellt TVmaze aus, wird der Katalogstand geschrieben
    und `live: false` gesetzt.
  * Deterministische Ausgabe (sortierte Schluessel, 2 Leerzeichen).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

TVMAZE = "https://api.tvmaze.com/shows/{id}?embed=episodes"
USER_AGENT = "frame61-fetch/1.0 (metadata snapshot; contact: local)"
THROTTLE_SECONDS = 0.6
RETRIES = 3

# SSRF hard stop: this tool may only talk to the fixed TVmaze host. The catalog
# id is validated as digits before it reaches the URL, so no user-controlled
# host/port/scheme can be injected.
ALLOWED_HOSTS = {"api.tvmaze.com"}


def assert_allowed_url(url: str) -> None:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https":
        raise ValueError(f"nur https erlaubt: {url}")
    if parsed.hostname not in ALLOWED_HOSTS:
        raise ValueError(f"Host nicht erlaubt: {parsed.hostname!r}")
    if parsed.port not in (None, 443):
        raise ValueError(f"Port nicht erlaubt: {parsed.port}")
    if not parsed.path.startswith("/shows/"):
        raise ValueError(f"Pfad nicht erlaubt: {parsed.path!r}")


def log(message: str) -> None:
    print(message, flush=True)


def fetch_json(url: str) -> object:
    assert_allowed_url(url)
    last: Exception | None = None
    for attempt in range(1, RETRIES + 1):
        request = urllib.request.Request(
            url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"}
        )
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                if response.status != 200:
                    raise urllib.error.HTTPError(
                        url, response.status, "HTTP", response.headers, None
                    )
                return json.loads(response.read().decode("utf-8"))
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError, json.JSONDecodeError) as exc:
            last = exc
            if attempt < RETRIES:
                wait = 2 ** attempt
                log(f"  retry {attempt}/{RETRIES - 1} in {wait}s: {exc}")
                time.sleep(wait)
    raise RuntimeError(f"{url}: {last}")


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def clean_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    import re

    return re.sub(r"\s+", " ", re.sub(r"<[^>]*>", "", value)).strip()


def catalog_snapshot(item: dict, generated_at: str) -> dict:
    return {
        "schema": 1,
        "id": item["id"],
        "source": "catalog",
        "sourceId": None,
        "fetchedAt": generated_at,
        "live": False,
        "attribution": "Katalog (Frame61)",
        "title": item.get("title", ""),
        "year": item.get("year"),
        "type": item.get("type", "movie"),
        "overview": clean_text(item.get("overview", "")),
        "poster": item.get("poster", ""),
        "genres": item.get("genres", []),
        "rating": item.get("rating"),
        "runtime": item.get("runtime"),
        "episodes": [],
    }


def tvmaze_snapshot(item: dict, generated_at: str) -> dict:
    source_id = str(item["id"]).split("-", 1)[1]
    if not source_id.isdigit():
        raise ValueError(f"TVmaze-ID ist nicht numerisch: {source_id!r}")
    show = fetch_json(TVMAZE.format(id=source_id))
    embedded = show.get("_embedded") if isinstance(show, dict) else None
    raw_episodes = embedded.get("episodes", []) if isinstance(embedded, dict) else []
    episodes = []
    for episode in raw_episodes:
        if not isinstance(episode, dict):
            continue
        episodes.append(
            {
                "id": episode.get("id"),
                "season": episode.get("season") or 0,
                "number": episode.get("number") or 0,
                "name": clean_text(episode.get("name")) or "Folge",
                "airdate": episode.get("airdate"),
                "runtime": episode.get("runtime"),
                "image": ((episode.get("image") or {}) or {}).get("medium"),
            }
        )
    image = show.get("image") if isinstance(show, dict) else None
    premiered = (show.get("premiered") or "")[:4]
    year = int(premiered) if premiered.isdigit() else item.get("year")
    return {
        "schema": 1,
        "id": item["id"],
        "source": "tvmaze",
        "sourceId": source_id,
        "fetchedAt": generated_at,
        "live": True,
        "attribution": "TVmaze (CC BY-SA 4.0)",
        "title": show.get("name") or item.get("title", ""),
        "year": year,
        "type": "series",
        "overview": clean_text(show.get("summary")) or clean_text(item.get("overview", "")),
        "poster": (image or {}).get("original") or item.get("poster", ""),
        "genres": show.get("genres") or item.get("genres", []),
        "rating": ((show.get("rating") or {}) or {}).get("average") or item.get("rating"),
        "runtime": show.get("averageRuntime") or item.get("runtime"),
        "episodes": episodes,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--limit", type=int, default=0, help="nur die ersten N Titel (0 = alle)")
    parser.add_argument("--offline", action="store_true", help="keine Netzabfrage, nur Katalog")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    catalog_path = root / "app/src/main/assets/catalog/catalog.json"
    out_dir = root / "app/src/main/assets/meta"
    out_dir.mkdir(parents=True, exist_ok=True)

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    items = catalog.get("items", [])
    if args.limit:
        items = items[: args.limit]
    generated_at = iso_now()

    index_path = out_dir / "index.json"
    existing_index: dict = {}
    if index_path.is_file():
        try:
            loaded_index = json.loads(index_path.read_text(encoding="utf-8"))
            if isinstance(loaded_index, dict):
                existing_index = loaded_index
        except json.JSONDecodeError:
            pass
    deep_path_ids: set[str] = set()
    targets_path = root / "tools/fetch/aniworld_targets.json"
    if targets_path.is_file():
        try:
            targets_payload = json.loads(targets_path.read_text(encoding="utf-8"))
            deep_path_ids = {
                str(target["id"])
                for target in targets_payload.get("targets", [])
                if isinstance(target, dict) and isinstance(target.get("id"), str)
            }
        except (json.JSONDecodeError, KeyError, TypeError):
            pass

    written = 0
    preserved = 0
    live = 0
    fallback = 0
    for index, item in enumerate(items, start=1):
        identifier = item.get("id", "")
        if not identifier:
            log(f"[{index}] SKIP ohne id")
            continue
        target = out_dir / f"{identifier}.json"
        if identifier in deep_path_ids and target.is_file():
            try:
                previous = json.loads(target.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                previous = {}
            if isinstance(previous, dict) and previous.get("source") in ("tvmaze", "anilist"):
                preserved += 1
                log(f"[{index}/{len(items)}] {identifier}: Tiefenpfad erhalten")
                continue
        snapshot: dict
        if item.get("type") == "series" and str(identifier).startswith("tvmaze-") and not args.offline:
            try:
                snapshot = tvmaze_snapshot(item, generated_at)
                live += 1
            except Exception as exc:  # noqa: BLE001 - Fallback ist gewollt
                log(f"[{index}] {identifier}: TVmaze fehlgeschlagen ({exc}) -> Katalog")
                snapshot = catalog_snapshot(item, generated_at)
                fallback += 1
        else:
            snapshot = catalog_snapshot(item, generated_at)

        target = out_dir / f"{identifier}.json"
        target.write_text(
            json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        written += 1
        log(f"[{index}/{len(items)}] {identifier}: {snapshot['source']} "
            f"({len(snapshot['episodes'])} Folgen)")
        if not args.offline and item.get("type") == "series":
            time.sleep(THROTTLE_SECONDS)

    generated_entries = [
        {
            "id": item.get("id"),
            "title": item.get("title", ""),
            "year": item.get("year"),
            "type": item.get("type", "movie"),
            "poster": item.get("poster", ""),
        }
        for item in items
        if item.get("id")
    ]
    previous_entries = existing_index.get("entries", [])
    merged_entries = [
        dict(entry) for entry in previous_entries if isinstance(entry, dict)
    ] if isinstance(previous_entries, list) else []
    positions = {entry.get("id"): position for position, entry in enumerate(merged_entries)}
    for entry in generated_entries:
        identifier = entry["id"]
        if identifier in positions:
            merged_entries[positions[identifier]] = entry
        else:
            positions[identifier] = len(merged_entries)
            merged_entries.append(entry)
    index_payload = {
        "schema": existing_index.get("schema", 1),
        "generatedAt": generated_at,
        "entries": merged_entries,
    }
    index_path.write_text(
        json.dumps(index_payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    log(
        f"geschrieben: {written} Snapshots, {preserved} Tiefenpfade erhalten, "
        f"{live} live, {fallback} Fallback -> {out_dir}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
