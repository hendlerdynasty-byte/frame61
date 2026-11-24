#!/usr/bin/env python3
"""Prueft Hoster-Packs gegen echte Seiten und schreibt Status + Fixture.

Cookie-Jar, Browser-User-Agent, Referer, 3 Versuche mit Backoff. Ein Fehler
bricht den Lauf nicht ab; der Pack bekommt `verified:false` und `check.reason`.
"""

from __future__ import annotations

import argparse
import base64
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
    url: str,
    referer: str,
    opener,
    attempts: int = RETRIES,
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, str]:
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        request_headers = {
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "Accept-Language": "de-DE,de;q=0.9,en;q=0.7",
            "Referer": referer,
        }
        request_headers.update(headers or {})
        request = urllib.request.Request(
            url,
            data=data,
            headers=request_headers,
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
    out = out.replace("{{url}}", source_url).replace("{url}", source_url)
    for key, value in variables.items():
        out = out.replace("{{" + key + "}}", value)
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


M32 = 0xFFFFFFFF
FM_LR = 2654435761
FM_HR = 2246822519


def _rotl32(value: int, bits: int) -> int:
    value &= M32
    return ((value << bits) | (value >> (32 - bits))) & M32


def _fm_ye(t: list[int]) -> None:
    t[0] = (t[0] + t[1]) & M32
    t[3] = _rotl32(t[3] ^ t[0], 16)
    t[2] = (t[2] + t[3]) & M32
    t[1] = _rotl32(t[1] ^ t[2], 12)
    t[0] = (t[0] + t[1]) & M32
    t[3] = _rotl32(t[3] ^ t[0], 8)
    t[2] = (t[2] + t[3]) & M32
    t[1] = _rotl32(t[1] ^ t[2], 7)


def filemoon_hash(data: bytes) -> list[int]:
    e = [1779033703, 3144134277, 1013904242, 2773480762]
    for byte in data:
        e[0] = (e[0] + byte) & M32
        e[0] = _rotl32(e[0], 7)
        _fm_ye(e)
    for _ in range(8):
        _fm_ye(e)
    r = [0] * 512
    for i in range(512):
        _fm_ye(e)
        r[i] = (e[0] ^ e[2]) & M32
    for _ in range(2):
        for s in range(512):
            a = r[s] & 511
            c = _rotl32((r[s] + r[a]) & M32, 13)
            c = (c ^ ((r[(s + 1) & 511] * FM_LR) & M32)) & M32
            r[s] = c
            e[0] = (e[0] ^ c) & M32
            _fm_ye(e)
    n = [0] * 8
    for i in range(8):
        _fm_ye(e)
        ss = e[0]
        for c in range(64):
            d = r[i * 64 + c]
            ss = (ss + d) & M32
            ss = _rotl32(ss, 5)
            ss = (ss ^ ((d * FM_HR) & M32)) & M32
        n[i] = (ss ^ e[2]) & M32
    return n


def leading_zero_bits(words: list[int]) -> int:
    bits = 0
    for word in words:
        if word == 0:
            bits += 32
            continue
        return bits + 32 - word.bit_length()
    return bits


def solve_pow(step: dict, variables: dict[str, str]) -> str | None:
    algorithm = step.get("algorithm")
    raw_input = variables.get(step.get("input") or "")
    if raw_input is None:
        return None
    separator = step.get("separator", ":")
    max_iterations = int(step.get("maxIterations", 1_000_000))
    if algorithm == "filemoon-v1":
        difficulty = step.get("difficulty")
        difficulty_input = step.get("difficultyInput")
        if difficulty_input:
            difficulty = variables.get(difficulty_input)
        try:
            difficulty = int(difficulty)
        except (TypeError, ValueError):
            return None
        for counter in range(max_iterations):
            if leading_zero_bits(filemoon_hash(f"{raw_input}{separator}{counter}".encode())) >= difficulty:
                return str(counter)
        return None
    if algorithm == "sha256":
        prefix = (step.get("targetPrefix") or "").lower()
        if not prefix:
            return None
        for counter in range(max_iterations):
            digest = hashlib.sha256(f"{raw_input}{separator}{counter}".encode()).hexdigest()
            if digest.startswith(prefix):
                return str(counter)
        return None
    return None


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
            step_headers = {
                key: render(value, source_url, variables)
                for key, value in (step.get("headers") or {}).items()
            }
            try:
                _, body = fetch(url, referer, opener, data=payload, headers=step_headers)
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
        elif action == "pow":
            solution = solve_pow(step, variables)
            if solution is None:
                return None, page_body, f"step {index}: pow ohne Lösung"
            variables[step.get("saveAs") or "solution"] = solution
        elif action == "aesgcm":
            raw = variables.get(step.get("input") or "")
            if raw is None:
                return None, page_body, f"step {index}: input fehlt"
            plain = decrypt_playback(raw)
            if plain is None:
                return None, page_body, f"step {index}: playback-Block nicht lesbar"
            variables[step.get("saveAs") or "aesgcm"] = plain
        elif action == "js":
            return None, page_body, f"step {index}: js nur im App-WebView ausführbar"
    result_name = pack.get("result")
    final = variables.get(result_name or "") if result_name else None
    if not final:
        return None, page_body, "result nicht erzeugt"
    if not final.startswith("https://"):
        return None, page_body, f"result non-https: {final[:80]}"
    return final, page_body, ""


def b64url_decode(text: str) -> bytes:
    clean = text.replace("-", "+").replace("_", "/")
    return base64.b64decode(clean + "=" * ((4 - len(clean) % 4) % 4))


def decrypt_playback(body: str) -> str | None:
    """Entschluesselt den playback-Block (AES-256-GCM) eines Video-JSON.

    Schluesselauswahl 1:1 wie der Resolver: Version v -> Teile v und 31-v
    (1-basiert), sonst alle Teile (Filemoon, belegt 24.11.2026).
    """
    try:
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        return None
    try:
        playback = json.loads(body).get("playback") or {}
        iv = b64url_decode(playback["iv"])
        payload = b64url_decode(playback["payload"])
        parts = [part for part in playback.get("key_parts", []) if part]
        version = int(playback.get("version") or 0)
    except (AttributeError, KeyError, TypeError, ValueError):
        return None
    order = [version, 31 - version] if 1 <= version <= 20 else []
    picked = [parts[index - 1] for index in order if 1 <= index <= len(parts)] if order else []
    if not picked:
        picked = parts
    try:
        key = b"".join(b64url_decode(part) for part in picked)
    except (ValueError, TypeError):
        return None
    if len(key) not in (16, 24, 32):
        return None
    try:
        return AESGCM(key).decrypt(iv, payload, None).decode("utf-8")
    except Exception:  # noqa: BLE001
        return None


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
            existing_meta = fixture_dir / "meta.json"
            # `copy`-only packs (direct) have no fetched body; keep the recorded
            # ARD fixture instead of overwriting it with an empty file.
            if not page_body and existing_meta.exists():
                continue
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
