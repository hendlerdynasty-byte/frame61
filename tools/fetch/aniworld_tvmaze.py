#!/usr/bin/env python3
"""Baut den lokalen Metadaten-Tiefenpfad fuer 20 aniworld-Titel.

Der Lauf holt TVmaze-Metadaten, Episoden, Cast und Bilder. Fehlende TVmaze-Felder
werden bei Bedarf aus dem keylosen AniList ergänzt; für die Filme "Origin:
Spirits of the Past" und "Hal" ist AniList der vollständige Datenfallback, weil
kein TVmaze-Datensatz existiert. Poster und Backdrops werden als JPEG ins
App-Asset-Verzeichnis geschrieben.
Fehlt ein echtes Backdrop, wird deterministisch ein dunkles Unscharf-Backdrop
aus dem lokalen Poster erzeugt. Der Meta-Index wird nur gemergt.

Aufruf:
    python3 tools/fetch/aniworld_tvmaze.py --root .
    python3 tools/fetch/aniworld_tvmaze.py --root . --check

Für die Bilderzeugung wird das bereits im Repository verwendete Pillow benoetigt.
--check ist rein lokal und benötigt weder Netzwerk noch Pillow.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import html
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
from typing import Any

TVMAZE_API = "api.tvmaze.com"
ANILIST_API = "graphql.anilist.co"
IMAGE_SUFFIXES = (".tvmaze.com", ".anilist.co")
USER_AGENT = "frame61-aniworld-meta/2.0 (+https://github.com/hendlerdynasty-byte/frame61)"
THROTTLE_SECONDS = 0.65
RETRIES = 3
CAST_LIMIT = 12
POSTER_SIZE = (240, 360)
BACKDROP_SIZE = (640, 360)
ASSET_PREFIX = "file:///android_asset/"

GENRE_MAP = {
    "Action": "Action",
    "Adventure": "Abenteuer",
    "Anime": "Anime",
    "Children": "Kinder",
    "Comedy": "Komödie",
    "Crime": "Krimi",
    "Drama": "Drama",
    "Fantasy": "Fantasy",
    "History": "Historie",
    "Horror": "Horror",
    "Medical": "Medical",
    "Music": "Musik",
    "Mystery": "Mystery",
    "Romance": "Romanze",
    "Sci-Fi": "Science-Fiction",
    "Science-Fiction": "Science-Fiction",
    "Sports": "Sport",
    "Supernatural": "Übernatürlich",
    "Thriller": "Thriller",
}

ANILIST_QUERY = """
query($search: String) {
  Page(perPage: 8) {
    media(search: $search, type: ANIME) {
      id
      title { romaji english native }
      synonyms
      averageScore
      duration
      episodes
      genres
      description(asHtml: false)
      startDate { year }
      coverImage { extraLarge large }
      bannerImage
      characters(perPage: 12, sort: ROLE) {
        edges {
          role
          voiceActors(language: JAPANESE) { name { full } }
          node { name { full } }
        }
      }
    }
  }
}
"""


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        safe_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(SafeRedirect())


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def log(message: str) -> None:
    print(message, flush=True)


def clean_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    without_markup = re.sub(r"<[^>]*>", "", html.unescape(value))
    without_zero_width = without_markup.replace("\u200b", "").replace("\ufeff", "")
    return re.sub(r"\s+", " ", without_zero_width).strip()


def safe_stem(identifier: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "-", identifier).strip("-") or "titel"


def normalize_title(value: object) -> str:
    text = unicodedata.normalize("NFKD", str(value or ""))
    text = "".join(char for char in text if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", "", text.lower())


def similarity(left: str, right: str) -> float:
    return difflib.SequenceMatcher(None, normalize_title(left), normalize_title(right)).ratio()


def mapped_genres(values: object) -> list[str]:
    if not isinstance(values, list):
        return []
    result: list[str] = []
    for value in values:
        genre = GENRE_MAP.get(str(value), str(value))
        if genre and genre != "Adult" and genre not in result:
            result.append(genre)
    return result


def load_targets(root: Path) -> list[dict[str, Any]]:
    path = root / "tools/fetch/aniworld_targets.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    targets = payload.get("targets") if isinstance(payload, dict) else None
    if not isinstance(targets, list) or len(targets) != 20:
        raise ValueError(f"{path}: genau 20 targets erwartet")
    seen: set[str] = set()
    for target in targets:
        if not isinstance(target, dict):
            raise ValueError(f"{path}: target ist kein Objekt")
        identifier = target.get("id")
        if not isinstance(identifier, str) or not identifier.startswith("aw:"):
            raise ValueError(f"{path}: ungueltige id {identifier!r}")
        if identifier in seen:
            raise ValueError(f"{path}: doppelte id {identifier!r}")
        seen.add(identifier)
        if target.get("type") not in ("movie", "series"):
            raise ValueError(f"{identifier}: type muss movie oder series sein")
        if not isinstance(target.get("query"), str) or not target["query"].strip():
            raise ValueError(f"{identifier}: query fehlt")
        aliases = target.get("aliases")
        if not isinstance(aliases, list) or not aliases:
            raise ValueError(f"{identifier}: aliases fehlen")
    return targets


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


def safe_url(raw: str, exact_host: str | None = None) -> str:
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme == "http":
        parsed = parsed._replace(scheme="https")
    host = (parsed.hostname or "").lower()
    if parsed.scheme != "https" or parsed.port not in (None, 443) or not host:
        raise ValueError(f"unerlaubte URL: {raw}")
    allowed = host == exact_host if exact_host else host.endswith(IMAGE_SUFFIXES)
    if not allowed:
        raise ValueError(f"Host nicht erlaubt: {host}")
    _assert_public_host(host)
    return urllib.parse.urlunparse(
        ("https", host, parsed.path or "/", parsed.params, parsed.query, "")
    )


def fetch_bytes(raw: str, exact_host: str | None = None, data: bytes | None = None) -> bytes:
    target = safe_url(raw, exact_host=exact_host)
    last: Exception | None = None
    for attempt in range(1, RETRIES + 1):
        time.sleep(THROTTLE_SECONDS)
        _assert_public_host(urllib.parse.urlparse(target).hostname or "")
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json, image/*"}
        request = urllib.request.Request(
            target,
            data=data,
            headers={
                **headers,
                **({"Content-Type": "application/json"} if data is not None else {}),
            },
        )
        try:
            with OPENER.open(request, timeout=30) as response:
                if response.status != 200:
                    raise urllib.error.HTTPError(
                        target, response.status, "HTTP", response.headers, None
                    )
                return response.read()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as exc:
            last = exc
            if attempt < RETRIES:
                wait = 2**attempt
                log(f"  retry {attempt}/{RETRIES - 1} in {wait}s: {exc}")
                time.sleep(wait)
    raise RuntimeError(f"Abruf fehlgeschlagen: {target}: {last}")


def fetch_json(raw: str) -> object:
    return json.loads(fetch_bytes(raw, exact_host=TVMAZE_API).decode("utf-8"))


def fetch_anilist(query: str) -> object:
    body = json.dumps({"query": ANILIST_QUERY, "variables": {"search": query}}).encode("utf-8")
    return json.loads(
        fetch_bytes(f"https://{ANILIST_API}/", exact_host=ANILIST_API, data=body).decode("utf-8")
    )


def select_tvmaze(results: object, target: dict[str, Any]) -> tuple[dict[str, Any], float]:
    if not isinstance(results, list):
        raise ValueError("TVmaze-Suche lieferte keine Liste")
    aliases = {normalize_title(alias) for alias in target["aliases"]}
    candidates: list[tuple[tuple[float, ...], dict[str, Any], float]] = []
    for result in results:
        if not isinstance(result, dict) or not isinstance(result.get("show"), dict):
            continue
        show = result["show"]
        name = str(show.get("name") or "")
        normalized = normalize_title(name)
        exact = normalized in aliases
        title_score = max((similarity(name, alias) for alias in target["aliases"]), default=0.0)
        if not exact and title_score < 0.92:
            continue
        api_score = float(result.get("score") or 0.0)
        rank = (
            1.0 if exact else 0.0,
            1.0 if show.get("type") == "Animation" else 0.0,
            title_score,
            api_score,
            -int(show.get("id") or 0),
        )
        candidates.append((rank, show, api_score))
    if not candidates:
        raise ValueError(f"kein sicherer TVmaze-Treffer für {target['id']}")
    candidates.sort(key=lambda entry: entry[0], reverse=True)
    _, show, api_score = candidates[0]
    return show, api_score


def select_anilist(payload: object, target: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("AniList-Antwort ist kein Objekt")
    data = payload.get("data")
    page = data.get("Page") if isinstance(data, dict) else None
    media = page.get("media") if isinstance(page, dict) else None
    if not isinstance(media, list):
        raise ValueError("AniList-Antwort enthält keine Medien")
    aliases = {normalize_title(alias) for alias in target["aliases"]}
    candidates: list[tuple[tuple[float, ...], dict[str, Any]]] = []
    for item in media:
        if not isinstance(item, dict):
            continue
        title = item.get("title") if isinstance(item.get("title"), dict) else {}
        names = [title.get(key) for key in ("english", "romaji", "native")]
        names.extend(item.get("synonyms") or [])
        exact = any(normalize_title(name) in aliases for name in names if name)
        title_score = max((similarity(name, alias) for name in names if name for alias in target["aliases"]), default=0.0)
        if not exact and title_score < 0.92:
            continue
        candidates.append(((1.0 if exact else 0.0, title_score, -int(item.get("id") or 0)), item))
    if not candidates:
        raise ValueError(f"kein sicherer AniList-Treffer für {target['id']}")
    candidates.sort(key=lambda entry: entry[0], reverse=True)
    return candidates[0][1]


def _resolution_url(resolutions: object, preferred: str = "original") -> str | None:
    if not isinstance(resolutions, dict):
        return None
    for key in (preferred, "original", "medium"):
        value = resolutions.get(key)
        if isinstance(value, dict) and isinstance(value.get("url"), str):
            return value["url"]
    return None


def _cast_from_tvmaze(show: dict[str, Any]) -> list[dict[str, str]]:
    embedded = show.get("_embedded") if isinstance(show.get("_embedded"), dict) else {}
    raw_cast = embedded.get("cast") if isinstance(embedded, dict) else []
    cast: list[dict[str, str]] = []
    if not isinstance(raw_cast, list):
        return cast
    for entry in raw_cast:
        if not isinstance(entry, dict):
            continue
        person = entry.get("person") if isinstance(entry.get("person"), dict) else {}
        character = entry.get("character") if isinstance(entry.get("character"), dict) else {}
        name = clean_text(person.get("name"))
        role = clean_text(character.get("name"))
        if name:
            cast.append({"name": name, "character": role})
        if len(cast) >= CAST_LIMIT:
            break
    return cast


def _episodes_from_tvmaze(show: dict[str, Any], item_type: str) -> list[dict[str, Any]]:
    if item_type == "movie":
        return []
    embedded = show.get("_embedded") if isinstance(show.get("_embedded"), dict) else {}
    raw_episodes = embedded.get("episodes") if isinstance(embedded, dict) else []
    episodes: list[dict[str, Any]] = []
    if not isinstance(raw_episodes, list):
        return episodes
    for episode in raw_episodes:
        if not isinstance(episode, dict) or not isinstance(episode.get("id"), int):
            continue
        episodes.append(
            {
                "id": episode["id"],
                "season": episode.get("season") if isinstance(episode.get("season"), int) else 0,
                "number": episode.get("number") if isinstance(episode.get("number"), int) else 0,
                "name": clean_text(episode.get("name")) or "Folge",
                "airdate": episode.get("airdate"),
                "runtime": episode.get("runtime"),
                "image": None,
            }
        )
    episodes.sort(key=lambda episode: (episode["season"], episode["number"], episode["id"]))
    return episodes


def tvmaze_match(target: dict[str, Any]) -> tuple[dict[str, Any], float]:
    query = urllib.parse.urlencode({"q": target["query"]})
    results = fetch_json(f"https://{TVMAZE_API}/search/shows?{query}")
    return select_tvmaze(results, target)


def build_tvmaze(target: dict[str, Any]) -> dict[str, Any]:
    selected, match_score = tvmaze_match(target)
    source_id = int(selected["id"])
    show_url = f"https://{TVMAZE_API}/shows/{source_id}?embed=episodes&embed=cast"
    show = fetch_json(show_url)
    if not isinstance(show, dict):
        raise ValueError(f"TVmaze-Show {source_id} ist kein Objekt")
    embedded = show.get("_embedded") if isinstance(show.get("_embedded"), dict) else {}
    if target["type"] == "series" and not embedded.get("episodes"):
        explicit_episodes = fetch_json(f"https://{TVMAZE_API}/shows/{source_id}/episodes")
        if isinstance(explicit_episodes, list):
            embedded["episodes"] = explicit_episodes
            show["_embedded"] = embedded
    images = fetch_json(f"https://{TVMAZE_API}/shows/{source_id}/images")
    if not isinstance(images, list):
        images = []

    poster_source: str | None = None
    backdrop_source: str | None = None
    image = show.get("image") if isinstance(show.get("image"), dict) else {}
    if isinstance(image.get("original"), str):
        poster_source = image["original"]
    for entry in images:
        if not isinstance(entry, dict):
            continue
        resolutions = entry.get("resolutions")
        if entry.get("type") == "poster" and not poster_source:
            poster_source = _resolution_url(resolutions)
        if entry.get("type") == "background" and not backdrop_source:
            backdrop_source = _resolution_url(resolutions)
    premiered = str(show.get("premiered") or "")
    year = int(premiered[:4]) if premiered[:4].isdigit() else target.get("yearHint")
    rating = (show.get("rating") or {}).get("average") if isinstance(show.get("rating"), dict) else None
    runtime = show.get("averageRuntime") or show.get("runtime")
    if target["type"] == "movie" and not show.get("premiered"):
        runtime = None
    episodes = _episodes_from_tvmaze(show, target["type"])
    if target["type"] == "series" and not episodes:
        raise ValueError(f"TVmaze-Show {source_id} ohne Episoden")
    metadata = {
        "source": "tvmaze",
        "sourceId": str(source_id),
        "tvmazeId": source_id,
        "matchScore": match_score,
        "year": year,
        "genres": mapped_genres(show.get("genres")),
        "overview": clean_text(show.get("summary")),
        "rating": rating,
        "runtime": runtime,
        "posterSource": poster_source,
        "backdropSource": backdrop_source,
        "episodes": episodes,
        "cast": _cast_from_tvmaze(show),
    }
    missing_fields = [
        field
        for field in ("year", "genres", "overview", "rating", "runtime", "cast")
        if metadata.get(field) in (None, "", [])
    ]
    if missing_fields or not poster_source:
        supplement = build_anilist(target)
        for field in missing_fields:
            if supplement.get(field) not in (None, "", []):
                metadata[field] = supplement[field]
        metadata["posterSource"] = poster_source or supplement.get("posterSource")
        metadata["backdropSource"] = backdrop_source or supplement.get("backdropSource")
        metadata["supplement"] = "anilist"
    if not metadata.get("posterSource"):
        raise ValueError(f"TVmaze-Show {source_id} ohne Poster")
    return metadata


def build_anilist(target: dict[str, Any]) -> dict[str, Any]:
    payload = fetch_anilist(target["query"])
    media = select_anilist(payload, target)
    source_id = int(media["id"])
    title = media.get("title") if isinstance(media.get("title"), dict) else {}
    cover = media.get("coverImage") if isinstance(media.get("coverImage"), dict) else {}
    poster_source = cover.get("extraLarge") or cover.get("large")
    backdrop_source = media.get("bannerImage")
    if not isinstance(poster_source, str):
        raise ValueError(f"AniList-Medium {source_id} ohne Cover")
    start_date = media.get("startDate") if isinstance(media.get("startDate"), dict) else {}
    year = start_date.get("year") or target.get("yearHint")
    score = media.get("averageScore")
    cast: list[dict[str, str]] = []
    characters = media.get("characters") if isinstance(media.get("characters"), dict) else {}
    for edge in characters.get("edges") or []:
        if not isinstance(edge, dict):
            continue
        actors = edge.get("voiceActors") if isinstance(edge.get("voiceActors"), list) else []
        actor = actors[0] if actors and isinstance(actors[0], dict) else {}
        actor_name = actor.get("name") if isinstance(actor.get("name"), dict) else {}
        character = edge.get("node") if isinstance(edge.get("node"), dict) else {}
        character_name = character.get("name") if isinstance(character.get("name"), dict) else {}
        name = clean_text(actor_name.get("full"))
        if name:
            cast.append({"name": name, "character": clean_text(character_name.get("full"))})
        if len(cast) >= CAST_LIMIT:
            break
    if not cast:
        raise ValueError(f"AniList-Medium {source_id} ohne Cast")
    return {
        "source": "anilist",
        "sourceId": str(source_id),
        "tvmazeId": None,
        "matchScore": None,
        "year": year,
        "genres": mapped_genres(media.get("genres")),
        "overview": clean_text(media.get("description")),
        "rating": score / 10.0 if isinstance(score, int) else None,
        "runtime": media.get("duration"),
        "posterSource": poster_source,
        "backdropSource": backdrop_source if isinstance(backdrop_source, str) else None,
        "episodes": [],
        "cast": cast,
    }


def jpeg_size(data: bytes) -> tuple[int, int]:
    if len(data) < 4 or data[:2] != b"\xff\xd8" or data[-2:] != b"\xff\xd9":
        raise ValueError("kein vollstaendiges JPEG")
    offset = 2
    sof_markers = {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}
    while offset < len(data):
        if data[offset] != 0xFF:
            offset += 1
            continue
        while offset < len(data) and data[offset] == 0xFF:
            offset += 1
        if offset >= len(data):
            break
        marker = data[offset]
        offset += 1
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7 or marker == 0x01:
            continue
        if offset + 2 > len(data):
            break
        length = int.from_bytes(data[offset : offset + 2], "big")
        if length < 2 or offset + length > len(data):
            raise ValueError("ungueltige JPEG-Segmentlaenge")
        if marker in sof_markers:
            if length < 7:
                raise ValueError("ungueltiger JPEG-SOF-Header")
            height = int.from_bytes(data[offset + 3 : offset + 5], "big")
            width = int.from_bytes(data[offset + 5 : offset + 7], "big")
            if width <= 0 or height <= 0:
                raise ValueError("JPEG ohne Abmessungen")
            return width, height
        offset += length
    raise ValueError("JPEG-SOF fehlt")


def _fit_image(image: Any, size: tuple[int, int]) -> Any:
    from PIL import Image, ImageOps

    return ImageOps.fit(image, size, method=Image.Resampling.LANCZOS, centering=(0.5, 0.4))


def write_poster(raw: bytes, target: Path) -> tuple[int, int]:
    from PIL import Image

    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".download")
    temporary.write_bytes(raw)
    try:
        with Image.open(temporary) as source:
            source.load()
            image = _fit_image(source.convert("RGB"), POSTER_SIZE)
            image.save(target, "JPEG", quality=80, optimize=True, progressive=True)
    finally:
        temporary.unlink(missing_ok=True)
    return jpeg_size(target.read_bytes())


def write_provider_backdrop(raw: bytes, target: Path) -> tuple[int, int]:
    from PIL import Image

    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + ".download")
    temporary.write_bytes(raw)
    try:
        with Image.open(temporary) as source:
            source.load()
            image = _fit_image(source.convert("RGB"), BACKDROP_SIZE)
            image.save(target, "JPEG", quality=78, optimize=True, progressive=True)
    finally:
        temporary.unlink(missing_ok=True)
    return jpeg_size(target.read_bytes())


def write_derived_backdrop(poster: Path, target: Path) -> tuple[int, int]:
    from PIL import Image, ImageEnhance, ImageFilter

    target.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(poster) as source:
        image = _fit_image(source.convert("RGB"), BACKDROP_SIZE)
        image = image.filter(ImageFilter.GaussianBlur(radius=18))
        image = ImageEnhance.Brightness(image).enhance(0.42)
        image.save(target, "JPEG", quality=76, optimize=True, progressive=True)
    return jpeg_size(target.read_bytes())


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def image_report(path: Path, size: tuple[int, int]) -> dict[str, Any]:
    return {
        "path": str(path.relative_to(path.parents[1])),
        "width": size[0],
        "height": size[1],
        "sha256": sha256_file(path),
    }


def asset_path(root: Path, relative: str) -> Path:
    return root / "app/src/main/assets" / relative


def local_asset(root: Path, value: object, prefix: str) -> Path:
    if not isinstance(value, str) or not value.startswith(ASSET_PREFIX + prefix):
        raise ValueError(f"kein lokales {prefix}-Asset: {value!r}")
    return asset_path(root, value.removeprefix(ASSET_PREFIX))


def build_target(root: Path, target: dict[str, Any], catalog_item: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    identifier = target["id"]
    try:
        metadata = build_tvmaze(target)
    except ValueError as exc:
        if identifier not in ("aw:origin-spirits-of-the-past", "aw:hal"):
            raise
        log(f"  TVmaze: {exc} -> AniList-Fallback")
        metadata = build_anilist(target)

    required = ("year", "genres", "overview", "rating", "runtime", "cast")
    for field in required:
        if metadata.get(field) in (None, "", []):
            raise ValueError(f"{identifier}: Feld {field} ist leer")

    stem = safe_stem(identifier)
    poster_relative = f"posters/{stem}.jpg"
    backdrop_relative = f"backdrops/{stem}.jpg"
    poster_path = asset_path(root, poster_relative)
    backdrop_path = asset_path(root, backdrop_relative)
    poster_raw = fetch_bytes(metadata["posterSource"])
    poster_size = write_poster(poster_raw, poster_path)
    if poster_size[0] < 200 or poster_size[1] < 300:
        raise ValueError(f"{identifier}: Poster zu klein: {poster_size}")
    backdrop_origin = "provider"
    if metadata.get("backdropSource"):
        backdrop_size = write_provider_backdrop(
            fetch_bytes(metadata["backdropSource"]), backdrop_path
        )
    else:
        backdrop_origin = "poster-derived"
        backdrop_size = write_derived_backdrop(poster_path, backdrop_path)
    if backdrop_size[0] < 320 or backdrop_size[1] < 180:
        raise ValueError(f"{identifier}: Backdrop zu klein: {backdrop_size}")

    poster_uri = ASSET_PREFIX + poster_relative
    backdrop_uri = ASSET_PREFIX + backdrop_relative
    title = clean_text(catalog_item.get("title")) or clean_text(metadata.get("title"))
    attribution = "TVmaze" if metadata["source"] == "tvmaze" else "AniList"
    if metadata.get("supplement"):
        attribution += " + AniList (fehlende Felder)"
    snapshot = {
        "schema": 1,
        "id": identifier,
        "source": metadata["source"],
        "sourceId": metadata["sourceId"],
        "fetchedAt": iso_now(),
        "live": True,
        "attribution": attribution,
        "title": title,
        "year": metadata["year"],
        "type": target["type"],
        "overview": metadata["overview"],
        "poster": poster_uri,
        "backdrop": backdrop_uri,
        "genres": metadata["genres"],
        "rating": metadata["rating"],
        "runtime": metadata["runtime"],
        "cast": metadata["cast"],
        "episodes": metadata["episodes"],
    }
    update = {
        "title": title,
        "type": target["type"],
        "year": metadata["year"],
        "genres": metadata["genres"],
        "rating": metadata["rating"],
        "runtime": metadata["runtime"],
        "overview": metadata["overview"],
        "poster": poster_uri,
        "backdrop": backdrop_uri,
        "cast": metadata["cast"],
    }
    report = {
        "id": identifier,
        "title": title,
        "source": metadata["source"],
        "supplement": metadata.get("supplement"),
        "tvmazeId": metadata["tvmazeId"],
        "matchScore": metadata["matchScore"],
        "episodes": len(metadata["episodes"]),
        "cast": len(metadata["cast"]),
        "poster": image_report(poster_path, poster_size),
        "backdrop": {**image_report(backdrop_path, backdrop_size), "origin": backdrop_origin},
    }
    return snapshot, update, report


def merge_index(existing: dict[str, Any], updates: list[dict[str, Any]], generated_at: str) -> dict[str, Any]:
    entries = existing.get("entries")
    if not isinstance(entries, list):
        entries = []
    merged = [dict(entry) for entry in entries if isinstance(entry, dict)]
    positions = {entry.get("id"): position for position, entry in enumerate(merged)}
    for update in updates:
        entry = {
            "id": update["id"],
            "title": update["title"],
            "year": update["year"],
            "type": update["type"],
            "poster": update["poster"],
        }
        identifier = entry["id"]
        if identifier in positions:
            merged[positions[identifier]] = entry
        else:
            positions[identifier] = len(merged)
            merged.append(entry)
    result = dict(existing)
    result["schema"] = existing.get("schema", 1)
    result["generatedAt"] = generated_at
    result["entries"] = merged
    return result


def contains_url(value: object) -> bool:
    if isinstance(value, str):
        return "http://" in value.lower() or "https://" in value.lower()
    if isinstance(value, list):
        return any(contains_url(item) for item in value)
    if isinstance(value, dict):
        return any(contains_url(item) for item in value.values())
    return False


def validate_outputs(root: Path) -> list[dict[str, Any]]:
    targets = load_targets(root)
    assets = root / "app/src/main/assets"
    catalog = json.loads((assets / "catalog/catalog.json").read_text(encoding="utf-8"))
    catalog_items = catalog.get("items") if isinstance(catalog, dict) else None
    if not isinstance(catalog_items, list):
        raise ValueError("Katalog hat keine items")
    by_id = {item.get("id"): item for item in catalog_items if isinstance(item, dict)}
    index = json.loads((assets / "meta/index.json").read_text(encoding="utf-8"))
    entries = index.get("entries") if isinstance(index, dict) else None
    if not isinstance(entries, list):
        raise ValueError("Meta-Index hat keine entries")
    index_ids = [entry.get("id") for entry in entries if isinstance(entry, dict)]
    if len(index_ids) != len(set(index_ids)):
        raise ValueError("Meta-Index enthält doppelte IDs")

    reports: list[dict[str, Any]] = []
    for target in targets:
        identifier = target["id"]
        item = by_id.get(identifier)
        if not isinstance(item, dict):
            raise ValueError(f"{identifier}: Katalogeintrag fehlt")
        for field in ("title", "year", "genres", "overview", "rating", "runtime", "poster", "backdrop", "cast"):
            if item.get(field) in (None, "", []):
                raise ValueError(f"{identifier}: Katalogfeld {field} ist leer")
        if contains_url(item):
            raise ValueError(f"{identifier}: Hotlink im Katalogeintrag")
        if identifier not in index_ids:
            raise ValueError(f"{identifier}: fehlt im Meta-Index")

        snapshot_path = assets / "meta" / f"{identifier}.json"
        if not snapshot_path.is_file():
            raise ValueError(f"{identifier}: Snapshot fehlt")
        snapshot = json.loads(snapshot_path.read_text(encoding="utf-8"))
        if snapshot.get("id") != identifier or snapshot.get("source") not in ("tvmaze", "anilist"):
            raise ValueError(f"{identifier}: Snapshot-Identität/-Quelle falsch")
        for field in ("title", "year", "genres", "overview", "rating", "runtime", "poster", "backdrop", "cast"):
            if snapshot.get(field) in (None, "", []):
                raise ValueError(f"{identifier}: Snapshotfeld {field} ist leer")
        episodes = snapshot.get("episodes")
        if not isinstance(episodes, list):
            raise ValueError(f"{identifier}: episodes ist keine Liste")
        if target["type"] == "series" and not episodes:
            raise ValueError(f"{identifier}: Serie ohne Episoden")

        poster_path = local_asset(root, item["poster"], "posters/")
        backdrop_path = local_asset(root, item["backdrop"], "backdrops/")
        if not poster_path.is_file() or not backdrop_path.is_file():
            raise ValueError(f"{identifier}: lokales Bild fehlt")
        poster_size = jpeg_size(poster_path.read_bytes())
        backdrop_size = jpeg_size(backdrop_path.read_bytes())
        if poster_size[0] < 200 or poster_size[1] < 300:
            raise ValueError(f"{identifier}: Poster zu klein: {poster_size}")
        if backdrop_size[0] < 320 or backdrop_size[1] < 180:
            raise ValueError(f"{identifier}: Backdrop zu klein: {backdrop_size}")
        reports.append(
            {
                "id": identifier,
                "title": item["title"],
                "source": snapshot["source"],
                "tvmazeId": int(snapshot["sourceId"]) if snapshot["source"] == "tvmaze" else None,
                "episodes": len(episodes),
                "cast": len(snapshot["cast"]),
                "poster": image_report(poster_path, poster_size),
                "backdrop": image_report(backdrop_path, backdrop_size),
            }
        )
    return reports


def write_json(path: Path, payload: object, *, sort_keys: bool = False) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=sort_keys) + "\n",
        encoding="utf-8",
    )


def generate(root: Path) -> int:
    targets = load_targets(root)
    assets = root / "app/src/main/assets"
    catalog_path = assets / "catalog/catalog.json"
    meta_dir = assets / "meta"
    index_path = meta_dir / "index.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    items = catalog.get("items")
    if not isinstance(items, list):
        raise ValueError("Katalog hat keine items")
    by_id = {item.get("id"): item for item in items if isinstance(item, dict)}
    existing_index = json.loads(index_path.read_text(encoding="utf-8"))
    if not isinstance(existing_index, dict):
        raise ValueError("Meta-Index ist kein Objekt")

    snapshots: dict[str, dict[str, Any]] = {}
    updates: list[dict[str, Any]] = []
    reports: list[dict[str, Any]] = []
    for position, target in enumerate(targets, start=1):
        identifier = target["id"]
        item = by_id.get(identifier)
        if not isinstance(item, dict):
            raise ValueError(f"{identifier}: Katalogeintrag fehlt")
        log(f"[{position}/20] {identifier} ({target['query']})")
        snapshot, update, report = build_target(root, target, item)
        snapshots[identifier] = snapshot
        updates.append({"id": identifier, **update})
        reports.append(report)
        log(
            f"  {report['source']} id={report['tvmazeId']} score={report['matchScore']} "
            f"episodes={report['episodes']} cast={report['cast']}"
        )

    generated_at = iso_now()
    for identifier, snapshot in snapshots.items():
        write_json(meta_dir / f"{identifier}.json", snapshot, sort_keys=True)
    for item in items:
        identifier = item.get("id")
        if identifier in snapshots:
            update = next(update for update in updates if update["id"] == identifier)
            for field, value in update.items():
                if field != "id":
                    item[field] = value
    catalog["generatedAt"] = generated_at
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8",
    )
    write_json(index_path, merge_index(existing_index, updates, generated_at), sort_keys=True)
    reports = validate_outputs(root)
    for report in reports:
        log("RESULT " + json.dumps(report, ensure_ascii=False, sort_keys=True))
    log(f"OK: 20 lokale Tiefenpfade; Meta-Index {len(existing_index.get('entries', []))} -> {len(json.loads(index_path.read_text(encoding='utf-8'))['entries'])} Einträge")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--check", action="store_true", help="nur lokale Assets prüfen, kein Netz")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    try:
        if args.check:
            reports = validate_outputs(root)
            for report in reports:
                log("RESULT " + json.dumps(report, ensure_ascii=False, sort_keys=True))
            log(f"OK: {len(reports)} lokale Tiefenpfade")
            return 0
        return generate(root)
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as exc:
        log(f"FEHLER: {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
