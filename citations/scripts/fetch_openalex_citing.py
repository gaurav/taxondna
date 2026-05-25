"""Fetch all OpenAlex works that cite a given work ID, saving raw JSON pages.

Pages are written verbatim to citations/data/openalex/raw/page_NNNN.json so
re-runs don't re-hit the API. The script is idempotent: pages already on
disk are skipped.

Default target is the SequenceMatrix paper (W2131473084 / DOI
10.1111/j.1096-0031.2010.00329.x). See citations/README.md for the broader
plan.

Usage:
    uv run scripts/fetch_openalex_citing.py
    uv run scripts/fetch_openalex_citing.py --max-pages 1      # smoke test
    uv run scripts/fetch_openalex_citing.py --work-id W12345    # other paper

Environment (loaded from citations/.env):
    OPENALEX_API_KEY  required since 2026-02-13 for the polite pool
    OPENALEX_MAILTO   email used as the polite contact
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import httpx
from dotenv import load_dotenv
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

import os

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent  # citations/
RAW_DIR = ROOT / "data" / "openalex" / "raw"

DEFAULT_WORK_ID = "W2131473084"
BASE = "https://api.openalex.org/works"
PER_PAGE = 200


def build_client(api_key: str, mailto: str) -> httpx.Client:
    ua = f"taxondna-citations/0.0.0 (mailto:{mailto})"
    return httpx.Client(
        headers={
            "User-Agent": ua,
            "Accept": "application/json",
            # API key in header, NOT a query param, so it never lands in
            # request URLs printed by exceptions or server logs.
            "Authorization": f"Bearer {api_key}",
        },
        params={"mailto": mailto},
        timeout=httpx.Timeout(60.0, connect=15.0),
    )


@retry(
    stop=stop_after_attempt(5),
    wait=wait_exponential(multiplier=2, min=2, max=60),
    retry=retry_if_exception_type((httpx.HTTPError,)),
    reraise=True,
)
def fetch_page(client: httpx.Client, work_id: str, cursor: str) -> dict:
    r = client.get(
        BASE,
        params={
            "filter": f"cites:{work_id}",
            "per-page": PER_PAGE,
            "cursor": cursor,
        },
    )
    r.raise_for_status()
    return r.json()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-id", default=DEFAULT_WORK_ID)
    parser.add_argument(
        "--max-pages",
        type=int,
        default=0,
        help="Stop after this many pages (0 = no limit). Useful for smoke tests.",
    )
    args = parser.parse_args()

    load_dotenv(ROOT / ".env")
    api_key = os.environ.get("OPENALEX_API_KEY", "").strip()
    mailto = os.environ.get("OPENALEX_MAILTO", "").strip()
    if not api_key:
        print("ERROR: OPENALEX_API_KEY not set in citations/.env", file=sys.stderr)
        return 2
    if not mailto:
        print("ERROR: OPENALEX_MAILTO not set in citations/.env", file=sys.stderr)
        return 2

    RAW_DIR.mkdir(parents=True, exist_ok=True)

    cursor = "*"
    page_num = 0
    total_seen = 0
    expected_total: int | None = None

    with build_client(api_key, mailto) as client:
        while cursor:
            page_num += 1
            page_path = RAW_DIR / f"page_{page_num:04d}.json"

            if page_path.exists():
                # Idempotent resume: read the cursor from the existing page.
                data = json.loads(page_path.read_text())
                action = "skip"
            else:
                data = fetch_page(client, args.work_id, cursor)
                page_path.write_text(json.dumps(data, indent=2, ensure_ascii=False))
                action = "fetch"

            meta = data.get("meta", {})
            results = data.get("results", [])
            if expected_total is None:
                expected_total = meta.get("count")
                print(
                    f"OpenAlex reports {expected_total} works citing {args.work_id}",
                    flush=True,
                )

            total_seen += len(results)
            cursor = meta.get("next_cursor")
            print(
                f"  page {page_num:>4} [{action}] {len(results):>3} results "
                f"(running total {total_seen}/{expected_total}) "
                f"next_cursor={'<end>' if not cursor else cursor[:20] + '...'}",
                flush=True,
            )

            if not results:
                # Defensive: an empty page with no cursor means we're done;
                # an empty page with a cursor shouldn't happen but break out
                # rather than loop forever.
                break
            if args.max_pages and page_num >= args.max_pages:
                print(f"  hit --max-pages={args.max_pages}, stopping", flush=True)
                break

    print(
        f"\nDone. Saved {page_num} page(s) to {RAW_DIR.relative_to(ROOT)}; "
        f"collected {total_seen} of {expected_total} works.",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
