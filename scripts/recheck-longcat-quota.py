#!/usr/bin/env python3
"""Non-blocking recheck of the LongCat quota, plus the follow-up eval path.

LongCat answered HTTP 402 (balance/quota exhausted) in round K1, so the real
provider could not be part of that batch. This script is the reusable way to
ask "is the quota back?" without touching the gate: it makes one minimal,
non-streaming chat request with the credentials already in `backend/.env`, and
classifies the answer.

Exit codes (deliberately non-zero on failure so a scheduled runner can pipe it):
    0  provider answered normally  -> rerun the real-model evaluation
    2  configuration problem (no key / no model / running in CI)
    3  still throttled or exhausted (HTTP 402 / 429, or an explicit quota error)
    4  provider unreachable or another HTTP error

It never prints the API key or the request body, and it refuses to run inside
CI. The recovery path it prints is the existing, call-capped harness:

    env -u CI -u GITHUB_ACTIONS EVAL_LIMIT=20 EVAL_MAX_CALLS=20 \
        ./scripts/eval-model-quality.sh

Usage:
    python3 scripts/recheck-longcat-quota.py
    python3 scripts/recheck-longcat-quota.py --timeout 30
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENV_FILE = ROOT / "backend" / ".env"
FOLLOW_UP = (
    "env -u CI -u GITHUB_ACTIONS EVAL_LIMIT=20 EVAL_MAX_CALLS=20 "
    "./scripts/eval-model-quality.sh"
)


def load_env(path: Path) -> dict[str, str]:
    """Read KEY=VALUE pairs without overriding the real environment."""
    values: dict[str, str] = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def endpoint(base_url: str) -> str:
    base = base_url.rstrip("/")
    if base.endswith("/v1"):
        return base + "/chat/completions"
    return base + "/v1/chat/completions"


def classify(status: int, body: str) -> tuple[str, str]:
    text = body.lower()
    if status == 200:
        return "recovered", "provider answered normally"
    if status == 402:
        return "exhausted", "quota or balance exhausted (HTTP 402)"
    if status == 429:
        return "throttled", "rate limited (HTTP 429)"
    if "quota" in text or "balance" in text or "insufficient" in text:
        return "exhausted", f"quota/balance error (HTTP {status})"
    return "unavailable", f"HTTP {status}"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timeout", type=float, default=20.0)
    parser.add_argument("--env-file", default=str(ENV_FILE))
    args = parser.parse_args()

    if os.environ.get("CI"):
        print("refusing to call a real provider in CI", file=sys.stderr)
        return 2
    path = Path(args.env_file)
    if not path.is_file():
        print(f"missing {path} (credentials live there)", file=sys.stderr)
        return 2
    values = load_env(path)
    key = values.get("OPENAI_API_KEY") or values.get("SPRING_AI_OPENAI_API_KEY")
    model = values.get("OPENAI_MODEL") or values.get("SPRING_AI_OPENAI_MODEL")
    base_url = values.get("OPENAI_BASE_URL") or "https://api.deepseek.com"
    if not key or not model:
        print("OPENAI_API_KEY/OPENAI_MODEL not configured in backend/.env",
              file=sys.stderr)
        return 2

    payload = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": "Reply with the single word ok."}],
        "max_tokens": 8,
        "temperature": 0,
        "stream": False,
    }).encode()
    request = urllib.request.Request(
        endpoint(base_url), data=payload, method="POST",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {key}"})

    started = time.perf_counter()
    status, body = 0, ""
    try:
        with urllib.request.urlopen(request, timeout=args.timeout) as response:
            status = response.status
            body = response.read().decode(errors="replace")
    except urllib.error.HTTPError as error:
        status = error.code
        body = error.read().decode(errors="replace")
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        print(f"provider: {base_url} model: {model}")
        print(f"verdict: UNAVAILABLE ({type(error).__name__}: {error})")
        return 4
    elapsed_ms = (time.perf_counter() - started) * 1000

    verdict, reason = classify(status, body)
    detail = ""
    if status != 200:
        try:
            error = json.loads(body).get("error", {})
            detail = " code=" + str(error.get("code")) + " message=" + str(
                error.get("message"))[:120]
        except (ValueError, AttributeError):
            detail = " " + body[:120].replace("\n", " ")
    print(f"provider: {base_url} model: {model}")
    print(f"status: HTTP {status} in {elapsed_ms:.0f} ms -> {reason}{detail}")
    print(f"verdict: {verdict.upper()}")
    if verdict == "recovered":
        print(f"follow-up: {FOLLOW_UP}")
        return 0
    print("follow-up: none; the real-model evaluation stays blocked")
    return 3


if __name__ == "__main__":
    raise SystemExit(main())
