#!/usr/bin/env python3
"""Erntet den Katalog aus dem tatsächlichen Angebot erreichbarer Indexseiten.

Gegenüber dem bisherigen TVmaze/Wikidata-Katalog gilt: Jeder Titel existiert per
Konstruktion auf mindestens einer Quelle. Genutzt werden die Sitemaps der
erreichbaren Indizes (bs.to/burningseries, streamcloud), weil sie die
vollständige Titelmenge ohne A–Z-Blättern liefern.

Serien: bs.to (Sitemap, Titel aus Slug, Poster `https://bs.cine.to/cover/<slug>.jpg`).
Filme: streamcloud (Sitemap `filme-*.xml`, Poster aus `og:image` der Detailseite).

Aufruf:
    python3 tools/fetch/catalog_harvest.py --root <repo> [--series 600] [--movies 400]
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import re
import socket
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path

UA = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
)
BS_SITEMAP = "https://burningseries.ac/sitemap.xml"
SC_SITEMAP_INDEX = "https://streamcloud.date/sitemap.xml"
BS_COVER = "https://bs.cine.to/cover/{slug}.jpg"

# Nur die geernteten Index-Hosts; alles andere wird verworfen.
ALLOWED_HOSTS = (
    "burningseries.ac",
    "bs.cine.to",
    "streamcloud.date",
    "api.ardmediathek.de",
)


class UrlBlocked(urllib.error.URLError):
    pass


def _host_allowed(host: str) -> bool:
    return any(host == allowed or host.endswith("." + allowed) for allowed in ALLOWED_HOSTS)


def _ip_is_public(host: str) -> bool:
    try:
        infos = socket.getaddrinfo(host, 443, proto=socket.IPPROTO_TCP)
    except OSError:
        return False
    if not infos:
        return False
    for info in infos:
        address = info[4][0]
        try:
            ip = ipaddress.ip_address(address.split("%")[0])
        except ValueError:
            return False
        if not (ip.is_global and not ip.is_private and not ip.is_loopback
                and not ip.is_link_local and not ip.is_reserved):
            return False
    return True


def validate_url(url: str) -> None:
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https":
        raise UrlBlocked(f"nur https erlaubt: {url}")
    if not _host_allowed(parsed.hostname or ""):
        raise UrlBlocked(f"Host nicht erlaubt: {parsed.hostname}")
    if not _ip_is_public(parsed.hostname or ""):
        raise UrlBlocked(f"Host nicht oeffentlich aufloesbar: {parsed.hostname}")


class _ValidatingRedirect(urllib.request.HTTPRedirectHandler):
    """Jede Weiterleitung wird erneut geprueft, kein Redirect auf private Ziele."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ANN001
        validate_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(_ValidatingRedirect)


def fetch(url: str, timeout: int = 25) -> str:
    validate_url(url)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with OPENER.open(request, timeout=timeout) as answer:
        return answer.read().decode("utf-8", "replace")


SMALL_WORDS = {
    "und", "oder", "der", "die", "das", "von", "zu", "im", "in", "am", "an",
    "auf", "für", "mit", "the", "a", "an", "of", "and", "or", "to", "in",
}


def locs(xml: str) -> list[str]:
    return [urllib.parse.unquote(m) for m in re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)]


def title_from_slug(slug: str) -> str:
    """Aus einem Slug einen lesbaren Titel machen, ohne die Schreibweise zu erfinden."""
    text = urllib.parse.unquote(slug).replace("_", " ").replace("-", " ")
    words = re.sub(r"\s+", " ", text).strip().split(" ")
    out: list[str] = []
    for index, word in enumerate(words):
        lower = word.lower()
        if index > 0 and lower in SMALL_WORDS:
            out.append(lower)
        else:
            out.append(word[:1].upper() + word[1:] if word else word)
    return " ".join(out)


def harvest_series(limit: int) -> list[dict]:
    items: list[dict] = []
    seen: set[str] = set()
    for url in locs(fetch(BS_SITEMAP)):
        match = re.match(r"^https?://bs\.cine\.to/serie/(.+?)/?$", url)
        if not match:
            continue
        slug = match.group(1)
        if slug in seen:
            continue
        seen.add(slug)
        items.append(
            {
                "id": f"bs:{slug}",
                "title": title_from_slug(slug),
                "type": "series",
                "year": None,
                "genres": [],
                "rating": None,
                "runtime": None,
                "overview": "",
                "poster": BS_COVER.format(slug=urllib.parse.quote(slug)),
            }
        )
        if len(items) >= limit:
            break
    return items


def movie_poster(url: str) -> str:
    try:
        match = re.search(r'og:image"\s+content="([^"]+)"', fetch(url, timeout=15))
        if match:
            return match.group(1)
    except Exception:
        return ""
    return ""


def harvest_movies(limit: int, workers: int = 6) -> list[dict]:
    index = fetch(SC_SITEMAP_INDEX)
    sitemaps = [u for u in locs(index) if "/filme-" in u]
    entries: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for sitemap in sitemaps:
        if len(entries) >= limit:
            break
        for url in locs(fetch(sitemap)):
            match = re.match(r"^https?://streamcloud\.date/filme/(\d+)-(.+?)/?$", url)
            if not match:
                continue
            movie_id, slug = match.group(1), match.group(2)
            if movie_id in seen:
                continue
            seen.add(movie_id)
            slug = re.sub(r"-stream-deutsch$", "", slug)
            entries.append((movie_id, slug, url))
            if len(entries) >= limit:
                break

    def build(entry: tuple[str, str, str]) -> dict:
        movie_id, slug, url = entry
        return {
            "id": f"sc:{movie_id}",
            "title": title_from_slug(slug),
            "type": "movie",
            "year": None,
            "genres": [],
            "rating": None,
            "runtime": None,
            "overview": "",
            "poster": movie_poster(url),
        }

    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(build, entries))


def ard_poster(entry: dict) -> str:
    """Bild-URL aus der ARD-Antwort; `{width}` wird auf 360 gesetzt."""
    images = entry.get("images") or {}
    for key in ("aspect16x9", "aspect16x7", "aspect1x1"):
        src = (images.get(key) or {}).get("src")
        if isinstance(src, str) and src.startswith("https://"):
            return src.replace("{width}", "360")
    return ""


def harvest_ard(limit: int) -> list[dict]:
    """Erntet ARD-Shows über die keyless Such-API (pro Show ein belegter Titel).

    Die ARD-Mediathek ist eine der wenigen Quellen, die auf dem Gerät
    nachweislich ohne Captcha spielt (direct-Hoster). Ihr Bestand ist damit die
    verlässliche Basis der Bibliothek.
    """
    queries = [
        "die", "der", "das", "und", "ein", "eine", "mit", "von", "zu", "im",
        "für", "auf", "nicht", "sich", "dem", "den", "des", "wird", "sind",
        "als", "auch", "wie", "nur", "aber", "bei", "aus", "war", "kann",
        "man", "wenn", "was", "wer", "wo", "jetzt", "heute", "neue", "staffel",
        "folge", "film", "serie", "doku", "show", "live", "tatort", "natur",
        "tier", "geschichte", "wissen", "musik", "sport", "kinder",
    ]
    items: list[dict] = []
    seen: set[str] = set()
    for query in queries:
        if len(items) >= limit:
            break
        for page in (1, 2):
            if len(items) >= limit:
                break
            url = (
                "https://api.ardmediathek.de/page-gateway/pages/ard/search"
                f"?searchString={urllib.parse.quote(query)}&page={page}"
            )
            try:
                data = json.loads(fetch(url))
            except Exception:
                continue
            for entry in data.get("showResults") or []:
                crid = entry.get("id")
                title = (entry.get("longTitle") or "").strip()
                if not crid or not title or crid in seen:
                    continue
                seen.add(crid)
                kind = entry.get("coreAssetType") or ""
                items.append(
                    {
                        "id": f"ard:{crid}",
                        "title": title,
                        "type": "series" if kind != "SINGLE" else "movie",
                        "year": None,
                        "genres": [],
                        "rating": None,
                        "runtime": None,
                        "overview": "",
                        "poster": ard_poster(entry),
                    }
                )
                if len(items) >= limit:
                    break
    return items


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--series", type=int, default=600)
    parser.add_argument("--movies", type=int, default=400)
    parser.add_argument("--ard", type=int, default=0, help="ARD-Shows zusätzlich ernten")
    parser.add_argument("--out", default="")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    series = harvest_series(args.series)
    movies = harvest_movies(args.movies)
    ard = harvest_ard(args.ard) if args.ard > 0 else []
    # Der Katalog ist die Auswahlliste der App: ein Titel darf nur einmal
    # auftauchen (Validator: doppelter Titel/Jahr ist ein Fehler).
    seen_titles: set[str] = set()
    items: list[dict] = []
    for item in series + movies + ard:
        key = f"{item['title'].strip().lower()}|{item.get('year') or 0}"
        if key in seen_titles:
            continue
        seen_titles.add(key)
        items.append(item)

    catalog = {
        "version": 2,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "provenance": {
            "series": "bs.to / burningseries.ac Sitemap (eigene Ernte, keine API)",
            "movies": "streamcloud.date Sitemap (eigene Ernte)",
            "note": "Jeder Titel existiert per Konstruktion auf der jeweiligen "
            "Quelle. Titel aus dem Slug, Poster von der Quelle.",
        },
        "items": items,
    }
    out = Path(args.out) if args.out else root / "app/src/main/assets/catalog/catalog.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    empty = sum(1 for i in items if not i["poster"])
    print(
        f"series={len(series)} movies={len(movies)} ard={len(ard)} "
        f"total={len(items)} ohne_poster={empty} -> {out}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())