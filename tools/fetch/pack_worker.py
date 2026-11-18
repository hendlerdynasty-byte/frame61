#!/usr/bin/env python3
"""Prueft Source-Packs gegen echte Antworten und schreibt den Status zurueck.

Ablauf je Pack:
  1. Ziel-URL aus tools/fetch/targets.json (kein Raten von Endpunkten).
  2. HTTP-Abruf mit Retry/Backoff; Host muss zur baseUrl des Packs passen.
  3. Optional auf einen Container trimmen.
  4. Alle im Pack vorhandenen Regeln pruefen (search/detail/episodes/streams).
  5. `verified`, `last_check` und `check` in die Pack-Datei schreiben.

Ein fehlgeschlagener Pack bricht den Lauf nicht ab: er wird als
`verified: false` mit Grund markiert. `--strict` liefert Exit 2, wenn ein
bisher verifizierter Pack die Verifikation verliert (GitHub-Action oeffnet dann
ein Issue).
"""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
import re
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import cssmini  # noqa: E402

RETRIES = 3
TIMEOUT = 20
USER_AGENT = "frame61-packcheck/1.0 (+https://github.com/; selector verification)"
ID_PLACEHOLDER = re.compile(r"\{id\}|{{id}}|\{titleId\}|{{titleId}}|\{episodeId\}|{{episodeId}}")


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def assert_host_allowed(url: str, base_url: str) -> None:
    target = urllib.parse.urlparse(url)
    base = urllib.parse.urlparse(base_url)
    if target.scheme != "https":
        raise ValueError(f"nur https erlaubt: {url}")
    if target.port not in (None, 443):
        raise ValueError(f"Port nicht erlaubt: {target.port}")
    host = (target.hostname or "").lower()
    base_host = (base.hostname or "").lower()
    if not host or not base_host:
        raise ValueError(f"Host fehlt: {url}")
    # Same host or a real subdomain of the pack's base host — nothing else.
    if host != base_host and not host.endswith("." + base_host):
        raise ValueError(f"Host {host!r} passt nicht zu baseUrl {base_host!r}")


def assert_public_resolution(host: str) -> None:
    """Loest den Host auf und blockt private/loopback/link-local-Ziele."""
    try:
        infos = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ValueError(f"DNS-Aufloesung fehlgeschlagen: {host}: {exc}") from exc
    if not infos:
        raise ValueError(f"DNS liefert keine Adresse: {host}")
    for info in infos:
        address = ipaddress.ip_address(info[4][0].split("%")[0])
        if (
            address.is_private
            or address.is_loopback
            or address.is_link_local
            or address.is_reserved
            or address.is_multicast
            or address.is_unspecified
        ):
            raise ValueError(f"private/reservierte IP blockiert: {host} -> {address}")


def safe_url(url: str, base_url: str) -> str:
    """Validiert und REKONSTRUIERT die URL — nie den Rohstring weiterreichen."""
    assert_host_allowed(url, base_url)
    parsed = urllib.parse.urlparse(url)
    host = (parsed.hostname or "").lower()
    if not host:
        raise ValueError(f"Host fehlt: {url}")
    assert_public_resolution(host)
    return urllib.parse.urlunparse(
        ("https", host, parsed.path or "/", parsed.params, parsed.query, "")
    )


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Redirects sind ein SSRF-Ausweg: der Worker folgt keinem."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        raise urllib.error.HTTPError(
            req.full_url, code, f"redirect blockiert: {newurl}", headers, fp
        )


_OPENER = urllib.request.build_opener(_NoRedirect)


def fetch(
    url: str,
    base_url: str,
    method: str = "GET",
    body: str | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, str]:
    target = safe_url(url, base_url)
    host = (urllib.parse.urlparse(target).hostname or "").lower()
    last: Exception | None = None
    for attempt in range(1, RETRIES + 1):
        # erneut aufloesen: verhindert, dass ein spaeterer DNS-Wechsel auf eine
        # interne Adresse zeigt (Rebinding-Fenster klein halten).
        assert_public_resolution(host)
        request_headers = {"User-Agent": USER_AGENT, "Accept": "*/*"}
        request_headers.update(headers or {})
        data = body.encode("utf-8") if body is not None else None
        if data is not None:
            request_headers.setdefault("Content-Type", "application/x-www-form-urlencoded")
        request = urllib.request.Request(
            target, data=data, headers=request_headers, method=method.upper()
        )
        try:
            with _OPENER.open(request, timeout=TIMEOUT) as response:
                charset = response.headers.get_content_charset() or "utf-8"
                return response.status, response.read().decode(charset, errors="replace")
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt < RETRIES:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"fetch failed: {last}")


def rule_is_api(pack: dict, rule: dict | None) -> bool:
    """Per-rule `format` overrides the pack `kind` (mixed packs)."""
    fmt = (rule or {}).get("format")
    if fmt in ("html", "api"):
        return fmt == "api"
    return (pack.get("kind") or "html") == "api"


def pick_first_result(pack: dict, body: str, is_json: bool) -> str | None:
    """Best effort: first id from the search rule, used for detail URLs."""
    rule = pack.get("search") or {}
    if rule_is_api(pack, rule):
        is_json = True
    if is_json:
        try:
            import json as _json

            root = _json.loads(body)
            value = walk(root, rule.get("jsonPath") or "")
            item = value[0] if isinstance(value, list) and value else value
            if isinstance(item, dict) and rule.get("idPath"):
                found = walk(item, rule["idPath"])
                if isinstance(found, (str, int)):
                    # Same normalisation as the Kotlin parser: last path segment.
                    return str(found).rstrip("/").rsplit("/", 1)[-1].split("?")[0] or None
        except Exception:  # noqa: BLE001 - best effort only
            return None
        return None

    doc = cssmini.parse(body)
    nodes = cssmini.select(doc, rule.get("resultSelector") or "")
    if not nodes:
        return None
    first = nodes[0]
    link_selector = rule.get("linkSelector") or "a"
    link = cssmini.select_first(first, link_selector) or cssmini.select_first(first, "a") or first
    if rule.get("idFromTitle"):
        title_node = cssmini.select_first(first, rule.get("titleSelector") or "") or link
        title = title_node.text().strip()
        pattern = rule.get("idRegex")
        if pattern:
            match = re.search(pattern, title)
            return match.group(rule.get("idGroup", 1)) if match else None
        return title or None
    href = link.attr(rule.get("idAttr") or "href")
    return href.rstrip("/").rsplit("/", 1)[-1].split("?")[0] or None


def walk(value, path: str):
    if not path:
        return value
    current = value
    for segment in path.split("."):
        if segment == "":
            continue
        if isinstance(current, dict):
            current = current.get(segment)
        elif isinstance(current, list):
            try:
                current = current[int(segment)]
            except (ValueError, IndexError):
                return None
        else:
            return None
        if current is None:
            return None
    return current


def check_selector(doc, selector: str, scope=None) -> bool:
    if not selector:
        return True
    return bool(cssmini.select(scope or doc, selector))


def check_html_pack(pack: dict, body: str, detail_body: str | None) -> tuple[bool, list[str], list[str]]:
    """Returns (ok, misses, checked)."""
    doc = cssmini.parse(body)
    misses: list[str] = []
    checked: list[str] = []

    search = pack.get("search")
    if search:
        checked.append("search.resultSelector")
        nodes = cssmini.select(doc, search.get("resultSelector") or "")
        if not nodes:
            misses.append("search.resultSelector")
        else:
            first = nodes[0]
            for field in ("titleSelector", "yearSelector", "typeSelector"):
                selector = search.get(field)
                if selector:
                    checked.append(f"search.{field}")
                    if not check_selector(doc, selector, first):
                        misses.append(f"search.{field}")
            link_selector = search.get("linkSelector")
            if link_selector:
                checked.append("search.linkSelector")
                if not check_selector(doc, link_selector, first):
                    misses.append("search.linkSelector")

    detail_doc = cssmini.parse(detail_body) if detail_body else None
    if detail_doc is not None:
        detail = pack.get("detail")
        if detail:
            for field in ("episodeSelector", "seasonSelector"):
                selector = detail.get(field)
                if selector:
                    checked.append(f"detail.{field}")
                    if not check_selector(detail_doc, selector):
                        misses.append(f"detail.{field}")
        streams = pack.get("streams")
        if streams and streams.get("selector"):
            streams_doc = detail_doc
            container = streams.get("container")
            if container:
                scoped = cssmini.select_first(streams_doc, container)
                if scoped is None:
                    misses.append("streams.container")
                    scoped = streams_doc
                streams_doc = scoped
            checked.append("streams.selector")
            if not check_selector(streams_doc, streams.get("selector") or ""):
                misses.append("streams.selector")
    elif pack.get("detail") or pack.get("streams"):
        checked.append("detail/streams (kein detail_url im Ziel)")

    return not misses, misses, checked


def first_episode_id(pack: dict, body: str) -> str | None:
    """First id produced by the pack's episodes rule — for the stream URL."""
    rule = pack.get("episodes") or {}
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return None
    items = walk(root, rule.get("jsonPath") or "")
    if isinstance(items, dict):
        items = [items]
    if not isinstance(items, list) or not items:
        return None
    value = walk(items[0], rule.get("idPath") or "")
    return str(value) if value is not None else None


def check_api_pack(
    pack: dict, body: str, detail_body: str | None, stream_body: str | None = None
) -> tuple[bool, list[str], list[str]]:
    misses: list[str] = []
    checked: list[str] = []
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return False, ["body: kein JSON"], checked

    search = pack.get("search")
    if search:
        checked.append("search.jsonPath")
        items = walk(root, search.get("jsonPath") or "")
        if isinstance(items, dict):
            items = [items]
        if not isinstance(items, list) or not items:
            misses.append("search.jsonPath")
        else:
            first = items[0]
            for field in ("idPath", "titlePath"):
                path = search.get(field)
                if path:
                    checked.append(f"search.{field}")
                    if walk(first, path) is None:
                        misses.append(f"search.{field}")

    if detail_body is not None:
        try:
            detail_root = json.loads(detail_body)
        except json.JSONDecodeError:
            return False, misses + ["detail: kein JSON"], checked
        detail = pack.get("detail")
        if detail:
            checked.append("detail.jsonPath")
            if walk(detail_root, detail.get("jsonPath") or "") is None:
                misses.append("detail.jsonPath")
        streams = pack.get("streams")
        if streams:
            path = streams.get("streamPath") or streams.get("jsonPath")
            if path:
                checked.append("streams.streamPath")
                source = detail_root
                if stream_body:
                    try:
                        source = json.loads(stream_body)
                    except json.JSONDecodeError:
                        misses.append("streams: kein JSON")
                        source = None
                if source is not None and walk(source, path) is None:
                    misses.append("streams.streamPath")
    elif pack.get("detail") or pack.get("streams"):
        checked.append("detail/streams (kein detail_url im Ziel)")

    return not misses, misses, checked


def _check_html_search(rule: dict, body: str) -> tuple[list[str], list[str]]:
    misses: list[str] = []
    checked: list[str] = []
    doc = cssmini.parse(body)
    nodes = cssmini.select(doc, rule.get("resultSelector") or "")
    checked.append("search.resultSelector")
    if not nodes:
        misses.append("search.resultSelector")
        return misses, checked
    first = nodes[0]
    for field in ("titleSelector", "yearSelector", "typeSelector", "linkSelector"):
        selector = rule.get(field)
        if selector:
            checked.append(f"search.{field}")
            if not cssmini.select(first, selector):
                misses.append(f"search.{field}")
    return misses, checked


def _check_api_search(rule: dict, body: str) -> tuple[list[str], list[str]]:
    misses: list[str] = []
    checked: list[str] = []
    try:
        root = json.loads(body)
    except json.JSONDecodeError:
        return ["search: kein JSON"], checked
    items = walk(root, rule.get("jsonPath") or "")
    if isinstance(items, dict):
        items = [items]
    checked.append("search.jsonPath")
    if not isinstance(items, list) or not items:
        return ["search.jsonPath"], checked
    first = items[0]
    for field in ("idPath", "titlePath", "yearPath", "typePath"):
        path = rule.get(field)
        if path:
            checked.append(f"search.{field}")
            if walk(first, path) is None:
                misses.append(f"search.{field}")
    return misses, checked


def _check_detail(pack: dict, body: str | None) -> tuple[list[str], list[str]]:
    rule = pack.get("detail")
    if not rule or body is None:
        return [], []
    misses: list[str] = []
    checked: list[str] = []
    if rule_is_api(pack, rule):
        try:
            root = json.loads(body)
        except json.JSONDecodeError:
            return ["detail: kein JSON"], checked
        if rule.get("jsonPath"):
            checked.append("detail.jsonPath")
            if walk(root, rule["jsonPath"]) is None:
                misses.append("detail.jsonPath")
        if rule.get("titlePath"):
            checked.append("detail.titlePath")
            if walk(root, rule["titlePath"]) is None:
                misses.append("detail.titlePath")
    else:
        doc = cssmini.parse(body)
        root = doc
        if rule.get("resultSelector"):
            checked.append("detail.resultSelector")
            root = cssmini.select_first(doc, rule["resultSelector"])
            if root is None:
                misses.append("detail.resultSelector")
                root = doc
        if rule.get("titleSelector"):
            checked.append("detail.titleSelector")
            if not cssmini.select(root, rule["titleSelector"]):
                misses.append("detail.titleSelector")
    return misses, checked


def _check_episodes(pack: dict, body: str | None) -> tuple[list[str], list[str]]:
    rule = pack.get("episodes")
    if not rule or body is None:
        return [], []
    misses: list[str] = []
    checked: list[str] = []
    if rule_is_api(pack, rule):
        try:
            root = json.loads(body)
        except json.JSONDecodeError:
            return ["episodes: kein JSON"], checked
        items = walk(root, rule.get("jsonPath") or "")
        if isinstance(items, dict):
            items = [items]
        checked.append("episodes.jsonPath")
        if not isinstance(items, list) or not items:
            return ["episodes.jsonPath"], checked
        if rule.get("idPath"):
            checked.append("episodes.idPath")
            if walk(items[0], rule["idPath"]) is None:
                misses.append("episodes.idPath")
    else:
        doc = cssmini.parse(body)
        checked.append("episodes.episodeSelector")
        nodes = cssmini.select(doc, rule.get("episodeSelector") or "")
        if not nodes:
            return ["episodes.episodeSelector"], checked
        first = nodes[0]
        for field in ("episodeTitleSelector", "episodeLinkSelector", "episodeIndexSelector"):
            selector = rule.get(field)
            if selector:
                checked.append(f"episodes.{field}")
                if not cssmini.select(first, selector):
                    misses.append(f"episodes.{field}")
    return misses, checked


def _check_streams(pack: dict, detail_body: str | None, stream_body: str | None) -> tuple[list[str], list[str]]:
    rule = pack.get("streams")
    if not rule:
        return [], []
    source = stream_body or detail_body
    if source is None:
        return [], []
    misses: list[str] = []
    checked: list[str] = []
    if rule_is_api(pack, rule):
        try:
            root = json.loads(source)
        except json.JSONDecodeError:
            return ["streams: kein JSON"], checked
        path = rule.get("streamPath") or rule.get("jsonPath")
        if path:
            checked.append("streams.streamPath")
            items = walk(root, path)
            if isinstance(items, dict):
                items = [items]
            if not isinstance(items, list) or not items:
                misses.append("streams.streamPath")
    else:
        doc = cssmini.parse(source)
        container = rule.get("container")
        scope = doc
        if container:
            scoped = cssmini.select_first(doc, container)
            if scoped is None:
                misses.append("streams.container")
            else:
                scope = scoped
        checked.append("streams.selector")
        nodes = cssmini.select(scope, rule.get("selector") or "")
        if not nodes:
            misses.append("streams.selector")
        elif rule.get("urlAttr"):
            checked.append("streams.urlAttr")
            if not any(node.attr(rule["urlAttr"]) for node in nodes):
                misses.append("streams.urlAttr")
    return misses, checked


def check_pack(
    pack: dict, search_body: str, detail_body: str | None, stream_body: str | None
) -> tuple[bool, list[str], list[str]]:
    search_rule = pack.get("search") or {}
    if rule_is_api(pack, search_rule):
        misses, checked = _check_api_search(search_rule, search_body)
    else:
        misses, checked = _check_html_search(search_rule, search_body)
    for part_misses, part_checked in (
        _check_detail(pack, detail_body),
        _check_episodes(pack, detail_body),
        _check_streams(pack, detail_body, stream_body),
    ):
        misses += part_misses
        checked += part_checked
    return not misses, misses, checked


def load_targets(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    return data.get("targets", {})


def _truncate_path(value, segments: list[str], limit: int):
    """Kopie des JSON-Baums, in der die Liste am Pfad auf `limit` gekuerzt ist."""
    segments = [segment for segment in segments if segment]
    if not segments:
        if isinstance(value, list):
            return value[:limit]
        return value
    if isinstance(value, dict):
        key = segments[0]
        if key in value:
            copy = dict(value)
            copy[key] = _truncate_path(value[key], segments[1:], limit)
            return copy
        return value
    if isinstance(value, list) and segments[0].isdigit():
        index = int(segments[0])
        if 0 <= index < len(value):
            copy = list(value)
            copy[index] = _truncate_path(value[index], segments[1:], limit)
            return copy
    return value


def write_fixture(
    root: Path,
    pack_id: str,
    body: str,
    target: dict,
    is_api: bool,
    now: str,
) -> None:
    """Schreibt die getrimmte echte Antwort plus erwartetes Parser-Ergebnis."""
    spec = target.get("fixture") or {}
    expected = spec.get("expected")
    if expected is None:
        return
    full_hash = hashlib.sha256(body.encode("utf-8")).hexdigest()
    recorded = body
    trim_note = ""
    if is_api and spec.get("trim_path") is not None:
        try:
            parsed = json.loads(body)
            trimmed = _truncate_path(parsed, str(spec["trim_path"]).split("."), int(spec.get("limit", 1)))
            recorded = json.dumps(trimmed, ensure_ascii=False, indent=2) + "\n"
            trim_note = f" JSON-Liste {spec['trim_path']!r} auf {spec.get('limit', 1)} Eintraege gekuerzt."
        except (json.JSONDecodeError, TypeError, ValueError):
            recorded = body
    if not is_api and spec.get("html_regex"):
        match = re.search(spec["html_regex"], body, re.S)
        if match:
            recorded = match.group(0) + "\n"
            trim_note = f" HTML auf {spec['html_regex']!r} gekuerzt."
    fixture_dir = root / "scraper/engine/src/test/resources/fixtures" / pack_id
    fixture_dir.mkdir(parents=True, exist_ok=True)
    extension = "json" if is_api else "html"
    (fixture_dir / f"search.body.{extension}").write_text(recorded, encoding="utf-8")
    (fixture_dir / "search.expected.json").write_text(
        json.dumps(expected, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (fixture_dir / "meta.json").write_text(
        json.dumps(
            {
                "id": pack_id,
                "provenance": "recorded",
                "capturedAt": now,
                "sourceStatus": target.get("status", 200),
                "sourceUrl": target["url"],
                "responseSha256": hashlib.sha256(recorded.encode("utf-8")).hexdigest(),
                "fullResponseSha256": full_hash,
                "note": (
                    "Echte Suchantwort, HTTP 200."
                    + trim_note
                    + " Vollantwort-Hash im Feld fullResponseSha256."
                ),
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def run_pack(pack_path: Path, target: dict | None, write: bool, root: Path | None = None) -> dict:
    pack = json.loads(pack_path.read_text(encoding="utf-8"))
    pack_id = pack.get("id") or pack_path.stem
    now = iso_now()
    result = {
        "id": pack_id,
        "verified": False,
        "status": "no_target",
        "misses": [],
        "checked": [],
        "detail": "kein Ziel in tools/fetch/targets.json",
    }
    if not target or not target.get("url"):
        result["last_check"] = now
        if write:
            write_status(pack_path, pack, result, now)
        return result

    search_rule = pack.get("search") or {}
    is_json = rule_is_api(pack, search_rule)
    try:
        status, body = fetch(
            target["url"],
            pack.get("baseUrl") or "",
            method=target.get("method", "GET"),
            body=target.get("body"),
            headers=target.get("headers"),
        )
        result["http_status"] = status
        if status != 200:
            result.update(status="fetch_failed", detail=f"HTTP {status}")
        else:
            detail_body = None
            detail_url = target.get("detail_url")
            if detail_url:
                first_id = pick_first_result(pack, body, is_json)
                if first_id:
                    url = ID_PLACEHOLDER.sub(first_id, detail_url)
                    try:
                        _, detail_body = fetch(
                            url,
                            pack.get("baseUrl") or "",
                            method=target.get("detail_method", "GET"),
                            body=target.get("detail_body"),
                            headers=target.get("detail_headers"),
                        )
                    except Exception as exc:  # noqa: BLE001
                        result.update(status="detail_failed", detail=str(exc))

            stream_body = None
            stream_url = target.get("stream_url")
            if stream_url and detail_body:
                episode_id = first_episode_id(pack, detail_body)
                if episode_id:
                    url = ID_PLACEHOLDER.sub(episode_id, stream_url)
                    try:
                        _, stream_body = fetch(url, pack.get("baseUrl") or "")
                    except Exception as exc:  # noqa: BLE001
                        result.update(status="stream_failed", detail=str(exc))

            if result["status"] == "no_target":
                ok, misses, checked = check_pack(pack, body, detail_body, stream_body)
                detail = "" if ok else "Selektoren ohne Treffer: " + ", ".join(misses)
                if not ok and target.get("note"):
                    detail += "; " + target["note"]
                result.update(
                    verified=ok,
                    status="verified" if ok else "selector_missing",
                    misses=misses,
                    checked=checked,
                    detail=detail,
                )
                if ok and write and root is not None:
                    write_fixture(root, pack_id, body, target, is_json, now)
    except Exception as exc:  # noqa: BLE001 - ein Pack darf den Lauf nie stoppen
        detail = str(exc)
        if target.get("note"):
            detail += "; " + target["note"]
        result.update(status="fetch_failed", detail=detail)

    result["last_check"] = now
    if write:
        write_status(pack_path, pack, result, now)
    return result


def write_status(pack_path: Path, pack: dict, result: dict, now: str) -> None:
    pack["verified"] = bool(result["verified"])
    pack["last_check"] = now
    pack["check"] = {
        "status": result["status"],
        "reason": result.get("detail", ""),
        "checked": result.get("checked", []),
        "misses": result.get("misses", []),
        "http_status": result.get("http_status"),
    }
    pack_path.write_text(
        json.dumps(pack, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--limit", type=int, default=10, help="Packs pro Lauf (0 = alle)")
    parser.add_argument(
        "--offset",
        type=int,
        default=0,
        help="Startindex fuer die Rotation (mehrere Laeufe decken alle Packs ab)",
    )
    parser.add_argument("--pack", action="append", default=[], help="nur diese Pack-ID(s)")
    parser.add_argument("--dry-run", action="store_true", help="nichts schreiben")
    parser.add_argument(
        "--strict",
        action="store_true",
        help="Exit 2, wenn ein bisher verifizierter Pack die Verifikation verliert",
    )
    args = parser.parse_args()

    root = Path(args.root).resolve()
    packs_dir = root / "app/src/main/assets/sources"
    targets = load_targets(root / "tools/fetch/targets.json")
    paths = sorted(packs_dir.glob("*.json"))
    if args.pack:
        wanted = set(args.pack)
        paths = [p for p in paths if p.stem in wanted]
    if args.offset:
        paths = paths[args.offset:] + paths[: args.offset]
    if args.limit:
        paths = paths[: args.limit]

    results = []
    lost = []
    for path in paths:
        previous = json.loads(path.read_text(encoding="utf-8")).get("verified") is True
        result = run_pack(path, targets.get(path.stem), write=not args.dry_run, root=root)
        results.append(result)
        if previous and not result["verified"]:
            lost.append(result["id"])
        print(f"{result['id']:<16} {result['status']:<18} {result.get('detail', '')}")

    verified = sum(1 for r in results if r["verified"])
    print(f"\n{len(results)} Packs geprueft, {verified} verifiziert, {len(lost)} verloren")
    if args.strict and lost:
        print("VERLOREN: " + ", ".join(lost))
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
