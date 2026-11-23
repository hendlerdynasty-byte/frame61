#!/usr/bin/env python3
"""Erntet den Katalog aus dem tatsächlichen Angebot der erreichbaren Quellen.

Der Katalog ist **invertiert**: Er entsteht nicht aus einer Metadaten-Datenbank,
sondern aus den A–Z-/Genre-/Neuerscheinungs-Listen der Indizes, die die App
selbst abfragt. Jeder Titel existiert damit per Konstruktion auf mindestens
einer Quelle.

Zielverteilung (Ownerauftrag 23.11.2026), Anteil am Gesamtkatalog:

  35 % Serien von s.to / serienstream   -> Gruppe `serienstream`
  20 % Filme von filmpalast / moflix    -> Gruppen `filmpalast` (10 %) + `moflix` (10 %)
  20 % Anime von aniworld               -> Gruppe `aniworld`
  15 % ARD/ZDF als verlässlicher Sockel  -> Gruppe `ard`
  10 % bs.to                            -> Gruppe `bsto`

Abweichungen und ihre Begründung stehen in `docs/ENTSCHEIDUNGEN.md`.

Jeder Eintrag trägt zusätzlich:
  - `group`     : Quellengruppe (die Quote wird je Gruppe ausgewiesen)
  - `sourceKey` : Adressierung **innerhalb** der Quelle (Slug, API-Id, CRID).
                  Nur die Quelle selbst kennt ihre Schlüssel; der Titel reicht
                  nicht, weil mehrere Quellen denselben Titel führen.

Sicherheit: nur https, nur die unten gelisteten Hosts, nur öffentlich
auflösbare IPs, jede Weiterleitung wird erneut geprüft (kein SSRF).

Aufruf:
    python3 tools/fetch/catalog_harvest.py --root <repo>
"""

from __future__ import annotations

import argparse
import ipaddress
import json
import random
import re
import socket
import sys
import time
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

# Zielverteilung laut Ownerauftrag: Gruppe -> Anzahl Titel.
TARGETS: dict[str, int] = {
    "serienstream": 350,
    "filmpalast": 100,
    "moflix": 100,
    "aniworld": 200,
    "ard": 150,
    "bsto": 100,
}

# Feste Reihenfolge der Stichprobe aus den Listings (dokumentiert, reproduzierbar).
LISTING_SEED = 20261123

ALLOWED_HOSTS = (
    "serienstream.to",
    "aniworld.to",
    "filmpalast.to",
    "moflix-stream.top",
    "burningseries.ac",
    "bs.cine.to",
    "streamcloud.date",
    "api.ardmediathek.de",
)

# Warten zwischen zwei Anfragen an denselben Host (DDoS-Guard-/Cloudflare-Schutz).
HOST_PAUSE_SECONDS = 0.25


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

_LAST_CALL: dict[str, float] = {}


def fetch(url: str, timeout: int = 25) -> str:
    validate_url(url)
    host = urllib.parse.urlparse(url).hostname or ""
    last = _LAST_CALL.get(host)
    if last is not None:
        gap = time.monotonic() - last
        if gap < HOST_PAUSE_SECONDS:
            time.sleep(HOST_PAUSE_SECONDS - gap)
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with OPENER.open(request, timeout=timeout) as answer:
        _LAST_CALL[host] = time.monotonic()
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


def item(
    identifier: str,
    title: str,
    kind: str,
    group: str,
    source_key: str,
    poster: str,
    year: int | None = None,
    overview: str = "",
) -> dict:
    return {
        "id": identifier,
        "title": title,
        "type": kind,
        "year": year,
        "genres": [],
        "rating": None,
        "runtime": None,
        "overview": overview,
        "poster": poster,
        "group": group,
        "sourceKey": source_key,
    }


# --------------------------------------------------------------- serienstream


def harvest_serienstream(limit: int, workers: int = 6) -> list[dict]:
    """Serien von s.to/serienstream.

    Die A–Z-Liste (`/serien`) liefert Slug und Titel ohne Bild. Das Bild kommt
    von der Detailseite (`og:image` = Backdrop der Quelle) — mehr gibt die
    Seite nicht her; ein echtes Poster-Pfad-Muster existiert nicht (geprüft:
    `/media/images/poster/...` antwortet 404).
    """
    html = fetch("https://serienstream.to/serien?page=1", timeout=60)
    entries: list[tuple[str, str]] = []
    for match in re.finditer(
        r'<li class="series-item"[^>]*>\s*<a href="/serie/([^"]+)"[^>]*>([^<]+)</a>', html
    ):
        slug, title = match.group(1), match.group(2).strip()
        if slug and title:
            entries.append((slug, title))
    unique: list[tuple[str, str]] = []
    seen: set[str] = set()
    for slug, title in entries:
        if slug in seen:
            continue
        seen.add(slug)
        unique.append((slug, title))
    random.Random(LISTING_SEED).shuffle(unique)

    def build(entry: tuple[str, str]) -> dict | None:
        slug, fallback_title = entry
        try:
            detail = fetch(f"https://serienstream.to/serie/{urllib.parse.quote(slug)}", timeout=20)
        except Exception:
            return None
        poster = ""
        match = re.search(r'og:image"\s+content="(https://[^"]+)"', detail)
        if match:
            poster = match.group(1)
        title = fallback_title
        heading = re.search(r'<h1[^>]*>(.*?)</h1>', detail, re.S)
        if heading:
            text = re.sub(r"<[^>]+>", "", heading.group(1)).strip()
            if text:
                title = text
        if not poster:
            return None
        return item(f"ss:{slug}", title, "series", "serienstream", slug, poster)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(build, unique[: int(limit * 1.6)]))
    return [r for r in results if r][:limit]


# -------------------------------------------------------------------- aniworld


def harvest_aniworld(limit: int, workers: int = 6) -> list[dict]:
    """Anime von aniworld.

    Die Übersicht (`/animes`) liefert Slug und Titel; das Cover-Pfadmuster
    enthält einen Titel-Hash und steht nur auf der Detailseite.
    """
    html = fetch("https://aniworld.to/animes", timeout=60)
    entries: list[tuple[str, str]] = []
    for match in re.finditer(
        r'href="/anime/stream/([^"]+)"[^>]*title="([^"]+)"', html
    ):
        slug, title = match.group(1), match.group(2).replace(" Stream anschauen", "").strip()
        if slug and title:
            entries.append((slug, title))
    unique: list[tuple[str, str]] = []
    seen: set[str] = set()
    for slug, title in entries:
        if slug in seen:
            continue
        seen.add(slug)
        unique.append((slug, title))
    random.Random(LISTING_SEED).shuffle(unique)

    def build(entry: tuple[str, str]) -> dict | None:
        slug, fallback_title = entry
        try:
            detail = fetch(f"https://aniworld.to/anime/stream/{urllib.parse.quote(slug)}", timeout=20)
        except Exception:
            return None
        match = re.search(r'src="(/public/img/cover/[^"]+)"', detail)
        if not match:
            return None
        poster = "https://aniworld.to" + match.group(1)
        title = fallback_title
        heading = re.search(r'<h1[^>]*>(.*?)</h1>', detail, re.S)
        if heading:
            text = re.sub(r"<[^>]+>", "", heading.group(1)).strip()
            if text:
                title = text
        return item(f"aw:{slug}", title, "series", "aniworld", slug, poster)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(build, unique[: int(limit * 1.6)]))
    return [r for r in results if r][:limit]


# ----------------------------------------------------------------------- bs.to


def harvest_bsto(limit: int, workers: int = 5) -> list[dict]:
    """Serien von bs.to / burningseries.

    Die Sitemap liefert alle Slugs; Cover und Titel stehen auf der Serienseite
    (`/public/images/cover/<id>.jpg`).
    """
    sitemap = fetch("https://burningseries.ac/sitemap.xml", timeout=60)
    slugs: list[str] = []
    for url in locs(sitemap):
        match = re.match(r"^https?://bs\.cine\.to/serie/(.+?)/?$", url)
        if match:
            slugs.append(match.group(1))
    random.Random(LISTING_SEED).shuffle(slugs)

    def build(slug: str) -> dict | None:
        try:
            detail = fetch(f"https://burningseries.ac/serie/{urllib.parse.quote(slug)}", timeout=25)
        except Exception:
            return None
        cover = re.search(r'src="(/public/images/cover/[^"]+)"', detail)
        if not cover:
            return None
        poster = "https://bs.cine.to" + cover.group(1)
        title = title_from_slug(slug)
        heading = re.search(r"<h1[^>]*>(.*?)</h1>", detail, re.S)
        if heading:
            text = re.sub(r"<[^>]+>", "", heading.group(1)).strip()
            if text:
                title = text
        return item(f"bs:{slug}", title, "series", "bsto", slug, poster)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(build, slugs[: int(limit * 1.6)]))
    return [r for r in results if r][:limit]


# ------------------------------------------------------------------ filmpalast


def harvest_filmpalast(limit: int) -> list[dict]:
    """Filme von filmpalast.

    Die A–Z-Listen (`/search/alpha/<Buchstabe>`) liefern Titel, Slug **und**
    den Cover-Pfad im selben Eintrag — kein Detailabruf nötig. Episoden-Slugs
    (`…-s02e04`) werden aussortiert: hier geht es um Filme.
    """
    items: list[dict] = []
    seen: set[str] = set()
    letters = [chr(code) for code in range(ord("A"), ord("Z") + 1)] + ["0-9"]
    for letter in letters:
        if len(items) >= limit:
            break
        try:
            html = fetch(f"https://filmpalast.to/search/alpha/{urllib.parse.quote(letter)}", timeout=30)
        except Exception:
            continue
        for article in re.findall(r"<article\b.*?</article>", html, re.S):
            heading = re.search(r'<a href="//filmpalast\.to/stream/([^"]+)"[^>]*title="([^"]+)"', article)
            if not heading:
                continue
            slug, title = heading.group(1), heading.group(2).strip()
            if not title or re.search(r"-s\d+e\d+", slug, re.I):
                continue
            cover = re.search(r'(/files/movies/[^"\']+)', article)
            if not cover:
                continue
            poster = "https://filmpalast.to" + cover.group(1)
            if slug in seen:
                continue
            seen.add(slug)
            items.append(item(f"fp:{slug}", title, "movie", "filmpalast", slug, poster))
            if len(items) >= limit:
                break
    return items[:limit]


# ---------------------------------------------------------------------- moflix


def harvest_moflix(limit: int) -> list[dict]:
    """Filme von moflix.

    Die Suche (`/search/<begriff>`) liefert ein JSON im `loaders`-Block mit
    Typ, Jahr und TMDB-Poster — direkt auswertbar.
    """
    terms = [
        "a", "the", "der", "die", "das", "man", "woman", "love", "night", "day",
        "dead", "game", "house", "city", "life", "world", "star", "moon", "fire",
        "dark", "last", "new", "old", "king", "queen", "war", "love", "money",
    ]
    items: list[dict] = []
    seen: set[str] = set()
    for term in terms:
        if len(items) >= limit:
            break
        try:
            html = fetch(f"https://moflix-stream.top/search/{urllib.parse.quote(term)}", timeout=30)
        except Exception:
            continue
        match = re.search(
            r'"loaders"\s*:\s*\{"searchPage"\s*:\s*\{"results"\s*:\s*(\[.*?\])', html, re.S
        )
        if not match:
            continue
        try:
            results = json.loads(match.group(1))
        except json.JSONDecodeError:
            continue
        for entry in results:
            if entry.get("type") != "movie":
                continue
            identifier = str(entry.get("id") or "")
            title = (entry.get("name") or "").strip()
            poster = (entry.get("poster") or "").replace("\\/", "/")
            if not identifier or not title or not poster.startswith("https://"):
                continue
            if identifier in seen:
                continue
            seen.add(identifier)
            year = None
            release = entry.get("release_date") or ""
            year_match = re.match(r"(\d{4})", release)
            if year_match:
                year = int(year_match.group(1))
            items.append(
                item(f"mf:{identifier}", title, "movie", "moflix", identifier, poster, year=year)
            )
            if len(items) >= limit:
                break
    return items[:limit]


# ------------------------------------------------------------------------ ARD


def ard_poster(entry: dict) -> str:
    """Bild-URL aus der ARD-Antwort; `{width}` wird auf 360 gesetzt."""
    images = entry.get("images") or {}
    for key in ("aspect16x9", "aspect16x7", "aspect1x1"):
        src = (images.get(key) or {}).get("src")
        if isinstance(src, str) and src.startswith("https://"):
            return src.replace("{width}", "360")
    return ""


def harvest_ard(limit: int) -> list[dict]:
    """ARD-Shows über die keyless Such-API.

    Die ARD-Mediathek ist die verlässliche Basis: sie spielt ohne Captcha über
    den `direct`-Hoster.
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
                poster = ard_poster(entry)
                if not poster:
                    continue
                items.append(
                    item(
                        f"ard:{crid}",
                        title,
                        "series" if kind != "SINGLE" else "movie",
                        "ard",
                        crid,
                        poster,
                    )
                )
                if len(items) >= limit:
                    break
    return items[:limit]


# ----------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--out", default="")
    parser.add_argument(
        "--targets",
        default="",
        help="z. B. 'bsto=20,ard=30' — sonst die Zielverteilung des Auftrags",
    )
    args = parser.parse_args()

    targets = dict(TARGETS)
    if args.targets:
        targets = {}
        for part in args.targets.split(","):
            if "=" not in part:
                continue
            key, value = part.split("=", 1)
            targets[key.strip()] = int(value)

    root = Path(args.root).resolve()
    harvested: dict[str, list[dict]] = {}
    order = ["serienstream", "filmpalast", "moflix", "aniworld", "ard", "bsto"]
    for group in order:
        target = targets.get(group, 0)
        if target <= 0:
            continue
        if group == "serienstream":
            harvested[group] = harvest_serienstream(target)
        elif group == "filmpalast":
            harvested[group] = harvest_filmpalast(target)
        elif group == "moflix":
            harvested[group] = harvest_moflix(target)
        elif group == "aniworld":
            harvested[group] = harvest_aniworld(target)
        elif group == "ard":
            harvested[group] = harvest_ard(target)
        elif group == "bsto":
            harvested[group] = harvest_bsto(target)
        print(f"{group}: {len(harvested[group])}/{target}")

    # Der Katalog ist die Auswahlliste der App: ein Titel darf nur einmal
    # auftauchen (Validator: doppelter Titel/Jahr ist ein Fehler).
    seen_titles: set[str] = set()
    items: list[dict] = []
    for group in order:
        for entry in harvested.get(group, []):
            key = f"{entry['title'].strip().lower()}|{entry.get('year') or 0}"
            if key in seen_titles:
                continue
            seen_titles.add(key)
            items.append(entry)

    catalog = {
        "version": 3,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "provenance": {
            "zielverteilung": ", ".join(f"{k}={v}" for k, v in targets.items()),
            "serienstream": "s.to/serienstream A–Z-Liste + Detailseite (Backdrop als Bild)",
            "filmpalast": "filmpalast.to A–Z-Liste (Titel, Slug, Cover im Eintrag)",
            "moflix": "moflix-stream.top Such-API (Typ, Jahr, TMDB-Poster)",
            "aniworld": "aniworld.to Anime-Übersicht + Detailseite (Cover)",
            "ard": "ARD-Mediathek Such-API (keyless)",
            "bsto": "bs.to Sitemap + Serienseite (Cover)",
            "note": (
                "Jeder Titel existiert per Konstruktion auf der jeweiligen Quelle. "
                "`sourceKey` adressiert den Titel innerhalb der Quelle."
            ),
        },
        "items": items,
    }
    out = Path(args.out) if args.out else root / "app/src/main/assets/catalog/catalog.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    empty = sum(1 for i in items if not i["poster"])
    groups: dict[str, int] = {}
    for entry in items:
        groups[entry["group"]] = groups.get(entry["group"], 0) + 1
    print(
        f"total={len(items)} ohne_poster={empty} gruppen={groups} -> {out}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
