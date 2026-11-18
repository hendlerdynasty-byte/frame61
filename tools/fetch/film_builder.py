#!/usr/bin/env python3
"""Ergaenzt den Katalog um deutsch beschriftete Spielfilme aus Wikidata.

Keylos: SPARQL fuer Filme mit Jahr/Poster, wbgetentities fuer deutsche
Beschreibung, Genres und Laufzeit, Wikimedia Special:FilePath fuer Poster.
Nur HTTPS, feste Host-Allowlist, keine Redirects auf fremde Hosts.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import io
import json
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

SPARQL = "https://query.wikidata.org/sparql"
ENTITIES = "https://www.wikidata.org/w/api.php"
POSTER_HOST = "commons.wikimedia.org"
ALLOWED_HOSTS = {
    "query.wikidata.org",
    "www.wikidata.org",
    POSTER_HOST,
    "upload.wikimedia.org",
    "thumb.wikimedia.org",
}
UA = "Frame61CatalogBuilder/1.0 (keyloser Offline-Katalog)"


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        raise urllib.error.HTTPError(req.full_url, code, "redirect blockiert", headers, fp)


_OPENER = urllib.request.build_opener(_NoRedirect)


def fetch(url: str, attempts: int = 3) -> bytes:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or (parsed.hostname or "") not in ALLOWED_HOSTS:
        raise ValueError(f"unerlaubte URL: {url}")
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
        try:
            with _OPENER.open(request, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as exc:
            last = exc
            if exc.code == 429 and attempt < attempts:
                time.sleep(61)
                continue
            if attempt < attempts:
                time.sleep(2 ** attempt)
        except (urllib.error.URLError, TimeoutError) as exc:
            last = exc
            if attempt < attempts:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"Abruf fehlgeschlagen: {url}: {last}")


def sparql_films(limit: int = 800, cache_path: Path | None = None) -> list[dict]:
    if cache_path is not None:
        resolved = cache_path.resolve()
        base = Path("/tmp").resolve()
        if not resolved.is_relative_to(base) or not resolved.is_file():
            raise ValueError(f"unerlaubter SPARQL-Cache: {cache_path}")
        data = json.loads(resolved.read_text(encoding="utf-8"))
    else:
        query = """SELECT ?film ?filmLabel ?year ?image WHERE {
      ?film wdt:P31 wd:Q11424; wdt:P577 ?date; wdt:P18 ?image.
      BIND(YEAR(?date) AS ?year)
      FILTER(YEAR(?date) >= 1930 && YEAR(?date) <= 2026)
      SERVICE wikibase:label { bd:serviceParam wikibase:language "de,en". }
    } LIMIT %d""" % limit
        url = SPARQL + "?" + urllib.parse.urlencode(
            {"query": query, "format": "json"}
        )
        data = json.loads(fetch(url).decode("utf-8"))
    rows = []
    for item in data["results"]["bindings"]:
        qid = item["film"]["value"].rsplit("/", 1)[-1]
        rows.append(
            {
                "qid": qid,
                "title": item.get("filmLabel", {}).get("value", ""),
                "year": int(item["year"]["value"]),
                "image": item["image"]["value"].replace("http://", "https://"),
            }
        )
    return rows


def entity_batch(qids: list[str]) -> dict:
    url = ENTITIES + "?" + urllib.parse.urlencode(
        {
            "action": "wbgetentities",
            "ids": "|".join(qids),
            "props": "labels|descriptions|claims",
            "languages": "de|en",
            "format": "json",
        }
    )
    return json.loads(fetch(url).decode("utf-8")).get("entities", {})


def label_of(entity: dict, key: str = "labels") -> str:
    values = entity.get(key, {})
    return (
        values.get("de", {}).get("value")
        or values.get("en", {}).get("value")
        or ""
    ).strip()


def duration_minutes(entity: dict) -> int | None:
    for claim in entity.get("claims", {}).get("P2047", []):
        data = claim.get("mainsnak", {}).get("datavalue", {}).get("value")
        if not isinstance(data, str):
            continue
        match = re.match(r"^PT(?:(\d+)H)?(?:(\d+)M)?", data)
        if match:
            hours = int(match.group(1) or 0)
            minutes = int(match.group(2) or 0)
            if hours or minutes:
                return hours * 60 + minutes
    return None


def genre_ids(entity: dict) -> list[str]:
    ids = []
    for claim in entity.get("claims", {}).get("P136", []):
        value = claim.get("mainsnak", {}).get("datavalue", {}).get("value", {})
        qid = value.get("id") if isinstance(value, dict) else None
        if qid and qid not in ids:
            ids.append(qid)
    return ids


def dedupe_key(title: str, year: int | None) -> str:
    normalized = unicodedata.normalize("NFKD", title)
    normalized = "".join(c for c in normalized if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", "", normalized.lower()) + "|" + str(year or 0)


def poster_path_for(poster_dir: Path, qid: str) -> Path:
    path = poster_dir / f"wiki-{qid}.jpg"
    if not path.resolve().is_relative_to(poster_dir.resolve()):
        raise ValueError("Posterpfad ausserhalb des Zielordners")
    return path


def poster_bytes(url: str, attempts: int = 4) -> bytes:
    """Special:FilePath redirects to upload.wikimedia.org; validate the final host."""
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != POSTER_HOST:
        raise ValueError(f"unerlaubte Poster-URL: {url}")
    request = urllib.request.Request(
        url, headers={"User-Agent": UA, "Accept": "image/avif,image/webp,image/jpeg,image/png,*/*"}
    )
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                final = urllib.parse.urlparse(response.geturl())
                if final.scheme != "https" or final.hostname not in ALLOWED_HOSTS:
                    raise ValueError(f"unerlaubtes Poster-Ziel: {response.geturl()}")
                return response.read()
        except Exception as exc:  # noqa: BLE001 - Poster sind optional, Retry aber noetig
            last = exc
            if attempt < attempts:
                time.sleep(3 * attempt)
    raise RuntimeError(f"Poster-Abruf fehlgeschlagen: {url}: {last}")


def download_poster(url: str, path: Path) -> bool:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != POSTER_HOST:
        return False
    try:
        raw = poster_bytes(url)
        image = Image.open(io.BytesIO(raw)).convert("RGB")
        width, height = image.size
        scale = max(180 / width, 270 / height)
        resized = image.resize(
            (max(180, round(width * scale)), max(270, round(height * scale))), Image.LANCZOS
        )
        left = (resized.width - 180) // 2
        top = (resized.height - 270) // 2
        resized.crop((left, top, left + 180, top + 270)).save(
            path, "JPEG", quality=72, optimize=True
        )
        return True
    except Exception:  # noqa: BLE001 - ein fehlendes Poster skippt den Film
        return False


def build(
    root: Path,
    wanted: int,
    sparql_limit: int,
    cache_path: Path | None = None,
    meta_cache: Path | None = None,
    extra: int = 0,
) -> None:
    catalog_path = root / "app/src/main/assets/catalog/catalog.json"
    meta_dir = root / "app/src/main/assets/meta"
    poster_dir = root / "app/src/main/assets/posters"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    existing = catalog["items"]
    seen = {
        dedupe_key(item.get("title", ""), item.get("year"))
        for item in existing
    }

    rows = sparql_films(sparql_limit, cache_path)
    candidates = []
    for row in rows:
        if row["title"]:
            key = dedupe_key(row["title"], row["year"])
            if key in seen:
                continue
            seen.add(key)
        candidates.append(row)
    if len(candidates) < wanted:
        raise RuntimeError(f"nur {len(candidates)} neue Filme verfuegbar")

    cached: dict = {}
    if meta_cache is not None and meta_cache.is_file():
        resolved_cache = meta_cache.resolve()
        if not resolved_cache.is_relative_to(Path("/tmp").resolve()):
            raise ValueError("unerlaubter Metadaten-Cache")
        cached = json.loads(resolved_cache.read_text(encoding="utf-8"))
    films: list[dict] = cached.get("films", [])
    genre_labels: dict[str, str] = cached.get("genre_labels", {})

    if not films:
        for offset in range(0, len(candidates), 50):
            batch = candidates[offset : offset + 50]
            entities = entity_batch([row["qid"] for row in batch])
            for row in batch:
                entity = entities.get(row["qid"], {})
                description = label_of(entity, "descriptions")
                title = row["title"] or label_of(entity)
                if not description or not title:
                    continue
                key = dedupe_key(title, row["year"])
                if key in seen:
                    continue
                seen.add(key)
                films.append(
                    {
                        **row,
                        "title": title,
                        "description": description,
                        "runtime": duration_minutes(entity),
                        "genreIds": genre_ids(entity),
                    }
                )
            print(
                f"Metadaten {min(offset + 50, len(candidates))}/{len(candidates)} "
                f"({len(films)} Filme)",
                flush=True,
            )
            if len(films) >= wanted:
                break
    unique_films: list[dict] = []
    seen_qids: set[str] = set()
    for film in films:
        if film["qid"] in seen_qids:
            continue
        seen_qids.add(film["qid"])
        unique_films.append(film)
    films = unique_films
    if len(films) < wanted:
        raise RuntimeError(f"nur {len(films)} Filme mit deutscher Beschreibung")

    if not genre_labels:
        all_genre_ids = sorted({gid for film in films for gid in film["genreIds"]})
        for offset in range(0, len(all_genre_ids), 50):
            batch = all_genre_ids[offset : offset + 50]
            entities = entity_batch(batch)
            for qid in batch:
                name = label_of(entities.get(qid, {}))
                if name:
                    genre_labels[qid] = name
    if meta_cache is not None:
        meta_cache.write_text(
            json.dumps({"films": films, "genre_labels": genre_labels}, ensure_ascii=False),
            encoding="utf-8",
        )

    selected = films[: wanted + extra]
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        poster_jobs = {
            pool.submit(download_poster, film["image"] + "?width=240", poster_path_for(poster_dir, film["qid"])): film
            for film in selected
        }
        poster_ok: set[str] = set()
        for job in concurrent.futures.as_completed(poster_jobs):
            film = poster_jobs[job]
            if job.result():
                poster_ok.add(film["qid"])

    usable = [film for film in selected if film["qid"] in poster_ok][:wanted]
    if len(usable) < wanted:
        raise RuntimeError(f"nur {len(usable)} Filme mit Poster")

    now = iso_now()
    index_path = meta_dir / "index.json"
    index = json.loads(index_path.read_text(encoding="utf-8"))
    for film in usable:
        item_id = f"wiki-{film['qid']}"
        poster = f"file:///android_asset/posters/{item_id}.jpg"
        genres = [genre_labels[gid] for gid in film["genreIds"][:4] if gid in genre_labels]
        item = {
            "id": item_id,
            "title": film["title"],
            "type": "movie",
            "year": film["year"],
            "genres": genres,
            "rating": None,
            "runtime": film["runtime"],
            "overview": film["description"],
            "poster": poster,
        }
        existing.append(item)
        (meta_dir / f"{item_id}.json").write_text(
            json.dumps(
                {
                    "schema": 1,
                    "id": item_id,
                    "source": "wikipedia",
                    "sourceId": film["qid"],
                    "fetchedAt": now,
                    "live": False,
                    "attribution": "Wikidata/Wikimedia Commons",
                    "title": film["title"],
                    "year": film["year"],
                    "type": "movie",
                    "overview": film["description"],
                    "poster": poster,
                    "genres": genres,
                    "rating": None,
                    "runtime": film["runtime"],
                    "episodes": [],
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )

    catalog["version"] = 4
    catalog["generatedAt"] = now
    catalog["provenance"]["count"] = str(len(existing))
    catalog["provenance"]["films"] = str(wanted)
    catalog["items"] = existing
    index["entries"] = [
        {
            "id": item["id"],
            "title": item["title"],
            "year": item["year"],
            "type": item["type"],
            "poster": item["poster"],
        }
        for item in existing
    ]
    wanted_meta = {f"{item['id']}.json" for item in existing}
    for path in meta_dir.glob("*.json"):
        if path.name != "index.json" and path.name not in wanted_meta:
            path.unlink(missing_ok=True)
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    index["generatedAt"] = now
    index_path.write_text(
        json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"geschrieben: {len(existing)} Titel, davon {wanted} Filme", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--count", type=int, default=400)
    parser.add_argument("--sparql-limit", type=int, default=800)
    parser.add_argument("--sparql-cache", default="", help="vorheriger SPARQL-JSON-Export unter /tmp")
    parser.add_argument("--meta-cache", default="", help="Zwischenspeicher fuer Wikidata-Metadaten unter /tmp")
    parser.add_argument("--extra", type=int, default=0, help="zusaetzliche Poster-Kandidaten")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    cache = Path(args.sparql_cache) if args.sparql_cache else None
    meta_cache = Path(args.meta_cache) if args.meta_cache else None
    if args.dry_run:
        films = sparql_films(min(args.sparql_limit, 50), cache)
        print(f"SPARQL ok: {len(films)} Filme, z.B. {films[0]['title'] if films else '-'}")
        return 0
    build(root, args.count, args.sparql_limit, cache, meta_cache, args.extra)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
