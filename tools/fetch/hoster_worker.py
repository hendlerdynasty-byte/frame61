#!/usr/bin/env python3
"""Prueft Hoster-Packs gegen echte Seiten und schreibt Status + Fixture.

Cookie-Jar, Browser-User-Agent, Referer, 3 Versuche mit Backoff. Ein Fehler
bricht den Lauf nicht ab; der Pack bekommt `verified:false` und `check.reason`.
"""

from __future__ import annotations

import argparse
import hashlib
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
RETRIES = 3


def iso_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def build_opener(cookies: http.cookiejar.CookieJar):
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))


class HttpStatusError(RuntimeError):
    def __init__(self, status: int, body: str) -> None:
        super().__init__(f"HTTP {status}")
        self.status = status
        self.body = body


def fetch(
    url: str, referer: str, opener, attempts: int = RETRIES, data: bytes | None = None
) -> tuple[int, str]:
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(
            url,
            data=data,
            headers={
                "User-Agent": UA,
                "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
                "Accept-Language": "de-DE,de;q=0.9,en;q=0.7",
                "Referer": referer,
            },
        )
        try:
            with opener.open(request, timeout=25) as response:
                charset = response.headers.get_content_charset() or "utf-8"
                return response.status, response.read().decode(charset, errors="replace")
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            if attempt < attempts:
                time.sleep(2 ** attempt)
                continue
            raise HttpStatusError(exc.code, body) from exc
        except (urllib.error.URLError, TimeoutError) as exc:
            last = exc
            if attempt < attempts:
                time.sleep(2 ** attempt)
    raise RuntimeError(f"fetch failed: {last}")


def render(template: str, source_url: str, variables: dict[str, str]) -> str:
    parsed = urllib.parse.urlparse(source_url)
    out = template.replace("{host}", parsed.netloc).replace("{path}", parsed.path)
    out = out.replace("{url}", source_url)
    for key, value in variables.items():
        out = out.replace("{" + key + "}", value)
    return out


def walk(value, path: str):
    current = value
    for segment in path.split("."):
        if not segment:
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


def run_steps(pack: dict, source_url: str, opener, referer: str) -> tuple[str | None, str | None, str]:
    variables: dict[str, str] = {}
    page_body = ""
    for index, step in enumerate(pack.get("steps", [])):
        action = step.get("action")
        if action in ("get", "post"):
            url = render(step.get("url", ""), source_url, variables)
            if not url.startswith("https://"):
                return None, None, f"step {index}: non-https {url}"
            payload = None
            if action == "post":
                payload = render(step.get("body", ""), source_url, variables).encode("utf-8")
            try:
                _, body = fetch(url, referer, opener, data=payload)
            except HttpStatusError as exc:
                return None, exc.body, f"step {index} {action}: HTTP {exc.status}"
            except Exception as exc:  # noqa: BLE001
                return None, page_body, f"step {index} {action} failed: {exc}"
            if not page_body:
                page_body = body
            variables[step.get("saveAs") or "page"] = body
        elif action == "regex":
            raw = variables.get(step.get("input") or "")
            if raw is None:
                return None, page_body, f"step {index}: input fehlt"
            match = re.search(step.get("pattern", ""), raw)
            if not match:
                return None, page_body, f"step {index}: regex ohne Treffer"
            variables[step.get("saveAs") or "match"] = match.group(step.get("group", 1))
        elif action == "json":
            raw = variables.get(step.get("input") or "")
            if raw is None:
                return None, page_body, f"step {index}: input fehlt"
            try:
                value = walk(json.loads(raw), step.get("path", ""))
            except json.JSONDecodeError:
                return None, page_body, f"step {index}: kein JSON"
            if value is None:
                return None, page_body, f"step {index}: Pfad ohne Wert"
            variables[step.get("saveAs") or "json"] = str(value)
        elif action == "copy":
            variables[step.get("saveAs") or "copy"] = render(
                step.get("url", ""), source_url, variables
            )
        elif action == "sleep":
            time.sleep(min(max(step.get("ms", 0), 0), 5_000) / 1000)
    result_name = pack.get("result")
    final = variables.get(result_name or "") if result_name else None
    if not final:
        return None, page_body, "result nicht erzeugt"
    if not final.startswith("https://"):
        return None, page_body, f"result non-https: {final[:80]}"
    return final, page_body, ""


def classify(body: str, fallback: str) -> str:
    """Benennt den beobachteten Blockiergrund, statt nur 'regex ohne Treffer'."""
    head = body[:200_000]
    if "Just a moment" in head or "cf_chl" in head or "challenge-platform" in head:
        return "Cloudflare-Challenge liefert kein Player-HTML"
    if 'id="root"' in head and 'type="module"' in head:
        return "SPA: HTML ohne Stream-URL, JS/API erforderlich"
    if "localStorage" in head and "location.href" in head:
        return "JS-Redirect/verschleierte Quelle, kein <source> im HTML"
    return fallback


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]))
    parser.add_argument("--pack", action="append", default=[])
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    targets = json.loads((root / "tools/fetch/hoster_targets.json").read_text())["targets"]
    packs_dir = root / "app/src/main/assets/hosters"
    fixture_root = root / "scraper/resolver/src/test/resources/fixtures-hosters"

    ids = args.pack or sorted(p.stem for p in packs_dir.glob("*.json") if not p.name.startswith("._"))
    checked = 0
    verified = 0
    for pack_id in ids:
        target = targets.get(pack_id)
        path = packs_dir / f"{pack_id}.json"
        pack = json.loads(path.read_text())
        now = iso_now()
        status = "no_target"
        reason = (target or {}).get("reason") or "kein Ziel in hoster_targets.json"
        url = None
        page_body = ""
        if target and target.get("url"):
            cookies = http.cookiejar.CookieJar()
            opener = build_opener(cookies)
            referer = target.get("referer", "https://aniworld.to/")
            try:
                url, page_body, reason = run_steps(pack, target["url"], opener, referer)
                status = "verified" if url else "step_failed"
                if not url:
                    reason = classify(page_body, reason)
            except Exception as exc:  # noqa: BLE001
                status = "fetch_failed"
                reason = str(exc)
        checked += 1
        ok = bool(url)
        if ok:
            verified += 1
        print(f"{pack_id:<14} {status:<14} {url or reason}")

        if args.dry_run:
            continue
        pack["verified"] = ok
        pack["last_check"] = now
        pack["check"] = {"status": status, "reason": "" if ok else reason}
        path.write_text(json.dumps(pack, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        if ok and target:
            fixture_dir = fixture_root / pack_id
            fixture_dir.mkdir(parents=True, exist_ok=True)
            (fixture_dir / "page.body.html").write_text(page_body, encoding="utf-8")
            (fixture_dir / "result.expected.json").write_text(
                json.dumps({"url": url}, indent=2) + "\n", encoding="utf-8"
            )
            (fixture_dir / "meta.json").write_text(
                json.dumps(
                    {
                        "id": pack_id,
                        "provenance": "recorded",
                        "capturedAt": now,
                        "sourceUrl": target["url"],
                        "responseSha256": __import__("hashlib").sha256(
                            page_body.encode("utf-8")
                        ).hexdigest(),
                        "note": "Echte Hoster-Antwort, Schrittkette mit Cookie-Jar ausgefuehrt.",
                    },
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )

    print(f"\n{checked} Hoster geprueft, {verified} verifiziert")
    return 0


if __name__ == "__main__":
    sys.exit(main())
