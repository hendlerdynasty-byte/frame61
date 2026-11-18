#!/usr/bin/env python3
"""Baut den gebundelten Katalog aus der keylosen TVmaze-API.

Auswahl: Scripted/Animation/Documentary/Mini-Series mit Poster und Premierendatum,
sortiert nach TVmaze-Gewicht, Rating und ID. Jeder Titel bekommt einen lokalen
Poster (180x270 JPEG) und einen Meta-Snapshot. Vorhandene Episodenlisten werden
beim Neubau erhalten.
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

API_HOST = "api.tvmaze.com"
POSTER_SUFFIX = ".tvmaze.com"
UA = "frame61-catalog/1.0 (+https://github.com/hendlerdynasty-byte/frame61)"
ALLOWED_TYPES = {"Scripted", "Animation", "Documentary", "Mini-Series"}
GENRE_MAP = {
    "Action": "Action",
    "Adventure": "Abenteuer",
    "Anime": "Anime",
    "Children": "Kinder",
    "Comedy": "Komödie",
    "Crime": "Krimi",
    "Disaster": "Katastrophe",
    "Drama": "Drama",
    "Espionage": "Spionage",
    "Family": "Familie",
    "Fantasy": "Fantasy",
    "History": "Historie",
    "Horror": "Horror",
    "Legal": "Justiz",
    "Medical": "Medical",
    "Music": "Musik",
    "Mystery": "Mystery",
    "Romance": "Romanze",
    "Science-Fiction": "Science-Fiction",
    "Sports": "Sport",
    "Supernatural": "Übernatürlich",
    "Thriller": "Thriller",
    "War": "Krieg",
    "Western": "Western",
}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        raise urllib.error.HTTPError(req.full_url, code, "redirect blockiert", headers, fp)


_OPENER = urllib.request.build_opener(_NoRedirect)


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _assert_public_host(host: str) -> None:
    try:
        infos = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    except socket.gaierror as exc:
        raise ValueError(f"DNS-Aufloesung fehlgeschlagen: {host}") from exc
    if not infos:
        raise ValueError(f"keine Adresse fuer {host}")
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


def safe_url(raw: str, exact_host: str | None = None, suffix: str | None = None) -> str:
    """Nur https, nur erlaubter Host, nur oeffentlich aufloesbare Ziele."""
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme != "https" or parsed.port not in (None, 443):
        raise ValueError(f"unerlaubte URL: {raw}")
    host = (parsed.hostname or "").lower()
    if not host:
        raise ValueError("Host fehlt")
    allowed = host == exact_host if exact_host else False
    if not allowed and suffix:
        allowed = host.endswith(suffix)
    if not allowed:
        raise ValueError(f"Host nicht erlaubt: {host}")
    _assert_public_host(host)
    return urllib.parse.urlunparse(("https", host, parsed.path or "/", parsed.params, parsed.query, ""))


def fetch_bytes(raw: str, exact_host: str | None = None, suffix: str | None = None, attempts: int = 3) -> bytes:
    target = safe_url(raw, exact_host, suffix)
    host = (urllib.parse.urlparse(target).hostname or "").lower()
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        _assert_public_host(host)
        request = urllib.request.Request(target, headers={"User-Agent": UA, "Accept": "*/*"})
        try:
            with _OPENER.open(request, timeout=30) as response:
                return response.read()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt < attempts:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"Abruf fehlgeschlagen: {target}: {last}")


def fetch_json(pages_url: str) -> object:
    return json.loads(fetch_bytes(pages_url, exact_host=API_HOST).decode("utf-8"))


def fetch_poster(poster_url: str, target: Path) -> None:
    host = (urllib.parse.urlparse(poster_url).hostname or "").lower()
    if host == "upload.wikimedia.org":
        raw = fetch_bytes(poster_url, exact_host=host)
    elif host.endswith(POSTER_SUFFIX):
        raw = fetch_bytes(poster_url, suffix=POSTER_SUFFIX)
    else:
        raise ValueError(f"Poster-Host nicht erlaubt: {host}")
    tmp = target.with_suffix(".download")
    tmp.write_bytes(raw)
    try:
        with Image.open(tmp) as image:
            image = image.convert("RGB")
            width, height = image.size
            target_w, target_h = 180, 270
            scale = max(target_w / width, target_h / height)
            resized = image.resize(
                (max(target_w, round(width * scale)), max(target_h, round(height * scale))),
                Image.LANCZOS,
            )
            left = (resized.width - target_w) // 2
            top = (resized.height - target_h) // 2
            cropped = resized.crop((left, top, left + target_w, top + target_h))
            cropped.save(target, "JPEG", quality=72, optimize=True)
    finally:
        tmp.unlink(missing_ok=True)


def clean_text(value: str | None) -> str:
    if not value:
        return ""
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", value)).strip()


def dedupe_key(title: str, year: int | None) -> str:
    normalized = unicodedata.normalize("NFKD", title)
    normalized = "".join(c for c in normalized if not unicodedata.combining(c))
    slug = re.sub(r"[^a-z0-9]+", "", normalized.lower())
    return f"{slug}|{year or 0}"


def collect(pages: int, wanted: int) -> list[dict]:
    shows: list[dict] = []
    for page in range(pages):
        payload = fetch_json(f"https://{API_HOST}/shows?page={page}")
        if not isinstance(payload, list):
            continue
        shows.extend(payload)
    candidates = [
        show
        for show in shows
        if show.get("name")
        and show.get("premiered")
        and (show.get("image") or {}).get("medium")
        and show.get("type") in ALLOWED_TYPES
    ]
    candidates.sort(
        key=lambda show: (
            show.get("weight") or 0,
            (show.get("rating") or {}).get("average") or 0,
            show.get("id") or 0,
        ),
        reverse=True,
    )
    selected: list[dict] = []
    seen: set[str] = set()
    for show in candidates:
        year = int(show["premiered"][:4]) if show["premiered"][:4].isdigit() else None
        key = dedupe_key(show["name"], year)
        if key in seen:
            continue
        seen.add(key)
        selected.append(show)
        if len(selected) >= wanted:
            break
    return selected


def safe_stem(item_id: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "-", item_id).strip("-") or "titel"


def load_seed(root: Path) -> list[dict]:
    path = root / "tools/fetch/catalog_seed.json"
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    items = data.get("items")
    return [item for item in items if isinstance(item, dict)] if isinstance(items, list) else []


def write_outputs(
    root: Path, shows: list[dict], old_meta: dict[str, dict], seed_items: list[dict], wanted: int
) -> None:
    now = iso_now()
    catalog_dir = root / "app/src/main/assets/catalog"
    poster_dir = root / "app/src/main/assets/posters"
    meta_dir = root / "app/src/main/assets/meta"
    catalog_dir.mkdir(parents=True, exist_ok=True)
    poster_dir.mkdir(parents=True, exist_ok=True)
    meta_dir.mkdir(parents=True, exist_ok=True)

    items: list[dict] = []
    index_entries: list[dict] = []
    seen_keys: set[str] = set()
    keep_meta: set[str] = {"index.json"}
    keep_posters: set[str] = set()

    def add_item(
        item_id: str,
        title: str,
        item_type: str,
        year: int | None,
        genres: list[str],
        rating,
        runtime,
        overview: str,
        poster_source: str,
        source: str,
        source_id: str | None,
        live: bool,
    ) -> bool:
        key = dedupe_key(title, year)
        if not title or key in seen_keys:
            return False
        stem = safe_stem(item_id)
        poster_path = poster_dir / f"{stem}.jpg"
        if not poster_path.is_file():
            if not poster_source.startswith("https://"):
                return False
            fetch_poster(poster_source, poster_path)
        poster_asset = f"file:///android_asset/posters/{stem}.jpg"
        item = {
            "id": item_id,
            "title": title,
            "type": item_type,
            "year": year,
            "genres": genres,
            "rating": rating,
            "runtime": runtime,
            "overview": overview,
            "poster": poster_asset,
        }
        items.append(item)
        index_entries.append(
            {
                "id": item_id,
                "title": title,
                "year": year,
                "type": item_type,
                "poster": poster_asset,
            }
        )
        previous = old_meta.get(item_id) or {}
        snapshot = {
            "schema": 1,
            "id": item_id,
            "source": source,
            "sourceId": source_id,
            "fetchedAt": now,
            "live": live,
            "attribution": "TVmaze" if source == "tvmaze" else "Wikipedia",
            "title": title,
            "year": year,
            "type": item_type,
            "overview": overview,
            "poster": poster_asset,
            "genres": genres,
            "rating": rating,
            "runtime": runtime,
            "episodes": previous.get("episodes") or [],
        }
        (meta_dir / f"{item_id}.json").write_text(
            json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        seen_keys.add(key)
        keep_meta.add(f"{item_id}.json")
        keep_posters.add(stem)
        return True

    for seed in seed_items:
        add_item(
            item_id=str(seed.get("id") or ""),
            title=str(seed.get("title") or ""),
            item_type=str(seed.get("type") or "movie"),
            year=seed.get("year") if isinstance(seed.get("year"), int) else None,
            genres=[str(g) for g in seed.get("genres") or []],
            rating=seed.get("rating"),
            runtime=seed.get("runtime"),
            overview=clean_text(str(seed.get("overview") or "")),
            poster_source=str(seed.get("poster") or ""),
            source="wikipedia" if str(seed.get("id") or "").startswith("wiki-") else "catalog",
            source_id=None,
            live=False,
        )

    for show in shows:
        if len(items) >= wanted:
            break
        year = int(show["premiered"][:4]) if show["premiered"][:4].isdigit() else None
        add_item(
            item_id=f"tvmaze-{show['id']}",
            title=str(show["name"]),
            item_type="series",
            year=year,
            genres=[GENRE_MAP.get(genre, genre) for genre in show.get("genres") or []],
            rating=(show.get("rating") or {}).get("average"),
            runtime=show.get("runtime"),
            overview=clean_text(show.get("summary")),
            poster_source=show["image"]["medium"],
            source="tvmaze",
            source_id=str(show["id"]),
            live=True,
        )

    catalog = {
        "version": 3,
        "generatedAt": now,
        "provenance": {
            "source": "TVmaze + Wikipedia-Seed",
            "method": "Seed-Titel zuerst, dann TVmaze shows?page=0..N (Scripted/Animation/Documentary/Mini-Series), Dedupe nach Titel/Jahr",
            "count": str(len(items)),
        },
        "items": items,
    }
    (catalog_dir / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    index = {"schema": 1, "generatedAt": now, "entries": index_entries}
    (meta_dir / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    for path in meta_dir.glob("*.json"):
        if path.name not in keep_meta:
            path.unlink()
    for path in poster_dir.glob("*.jpg"):
        if path.stem not in keep_posters:
            path.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--count", type=int, default=600)
    parser.add_argument("--pages", type=int, default=8)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    seed_items = load_seed(root)
    remaining = max(args.count - len(seed_items), 0)
    if remaining <= 0:
        print(f"Seed deckt bereits {len(seed_items)} Titel", file=sys.stderr)
        return 1
    shows = collect(args.pages, remaining + 120)
    print(f"{len(shows)} TVmaze-Titel + {len(seed_items)} Seed-Titel (Ziel {args.count})")
    for show in shows[:5]:
        print(f"  {show['id']:>7}  {show['name']}  {show.get('premiered', '')[:4]}")
    if len(shows) < remaining:
        print(f"FEHLER: nur {len(shows)} TVmaze-Titel, mehr Seiten noetig", file=sys.stderr)
        return 1
    if args.dry_run:
        return 0

    meta_dir = root / "app/src/main/assets/meta"
    old_meta: dict[str, dict] = {}
    if meta_dir.is_dir():
        for path in meta_dir.glob("tvmaze-*.json"):
            try:
                old_meta[path.stem] = json.loads(path.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                continue
    write_outputs(root, shows, old_meta, seed_items, args.count)
    print(f"geschrieben: {args.count} Katalogeintraege, Poster und Meta-Snapshots")
    return 0


if __name__ == "__main__":
    sys.exit(main())
