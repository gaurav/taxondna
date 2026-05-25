"""Phase 3a: build a full-text coverage table without downloading bytes.

For each of the 2,509 SM-citing papers in
`data/openalex/citing_works.jsonl`, decide whether we can read the full
text for free, and how. Strategy ranking, best to worst:

    pmc_xml   — paper is in Europe PMC with section-tagged fullTextXML
                available. Best for Phase 4 LLM extraction (no GROBID).
    oa_pdf    — OpenAlex / Unpaywall says the paper is OA and has a
                resolvable OA URL (publisher or repository PDF).
    paywalled — neither route works; skipped per Phase 3 OA-only policy.
    no_doi    — paper has no DOI in OpenAlex (rare, ~0.4% of corpus).

We trust OpenAlex's existing OA fields rather than re-querying Unpaywall:
OpenAlex sources its OA data from Unpaywall directly (Priem et al. 2022),
and our Phase 1 snapshot already carries `is_oa`, `oa_status`, `oa_url`
per paper. Europe PMC is the only API actually called by this script.

Output: `data/fulltext/coverage.csv`. One row per paper, no bytes.

Caching: every Europe PMC search response is written verbatim to
`data/fulltext/_cache/europepmc/{sha256(doi)}.json` (gitignored). Reruns
read from disk and skip the HTTP call.

Usage:
    uv run scripts/build_fulltext_coverage.py
    uv run scripts/build_fulltext_coverage.py --max 50          # smoke test
    uv run scripts/build_fulltext_coverage.py --no-cache         # force refetch
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import sys
import time
from pathlib import Path

import httpx
from dotenv import load_dotenv
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
JSONL = ROOT / "data" / "openalex" / "citing_works.jsonl"
OUT_CSV = ROOT / "data" / "fulltext" / "coverage.csv"
CACHE_DIR = ROOT / "data" / "fulltext" / "_cache" / "europepmc"

EPMC_SEARCH = "https://www.ebi.ac.uk/europepmc/webservices/rest/search"
# Section-tagged JATS XML for OA papers in PMC. The `article/` segment is
# load-bearing — without it the endpoint 404s. Only works when the paper is
# in the PMC OA subset (`isOpenAccess=Y` AND `inEPMC=Y` AND has pmcid).
EPMC_FULLTEXT_FMT = (
    "https://www.ebi.ac.uk/europepmc/webservices/rest/article/{pmcid}/fullTextXML"
)

CSV_FIELDS = [
    "openalex_id",
    "doi",
    "title",
    "publication_year",
    "type",
    "is_oa",
    "oa_status",
    "oa_url",
    "license",
    "pmcid",
    "pmid",
    "epmc_source",
    "in_epmc",
    "is_oa_epmc",
    "has_pdf_epmc",
    "has_fulltext_epmc",
    "pmc_xml_url",
    "best_strategy",
]


def doi_cache_path(doi: str) -> Path:
    h = hashlib.sha256(doi.lower().encode("utf-8")).hexdigest()
    return CACHE_DIR / f"{h}.json"


def normalize_doi(raw: str | None) -> str | None:
    if not raw:
        return None
    # OpenAlex stores DOIs as full URLs like https://doi.org/10.xxx/yyy.
    # Strip to the bare prefix-based DOI.
    s = raw.strip()
    for prefix in ("https://doi.org/", "http://doi.org/", "doi:"):
        if s.lower().startswith(prefix):
            s = s[len(prefix):]
            break
    return s.lower() or None


def build_client(mailto: str) -> httpx.Client:
    return httpx.Client(
        headers={
            "User-Agent": f"taxondna-citations/0.0.0 (mailto:{mailto})",
            "Accept": "application/json",
        },
        timeout=httpx.Timeout(30.0, connect=10.0),
    )


@retry(
    stop=stop_after_attempt(4),
    wait=wait_exponential(multiplier=1.5, min=1, max=20),
    retry=retry_if_exception_type((httpx.HTTPError,)),
    reraise=True,
)
def epmc_search_by_doi(client: httpx.Client, doi: str) -> dict:
    """Query Europe PMC by DOI. Returns raw JSON (the wrapper around resultList).

    Multiple hits are possible (very rare); we take the first one in the caller.
    """
    r = client.get(
        EPMC_SEARCH,
        params={
            "query": f'DOI:"{doi}"',
            "format": "json",
            "resultType": "lite",
            "pageSize": 5,
        },
    )
    r.raise_for_status()
    return r.json()


def pick_epmc_hit(payload: dict) -> dict | None:
    """Pick the best Europe PMC record for a DOI hit.

    Prefer PMC source (full-text usually available) over MED, then PPR.
    """
    results = (payload.get("resultList") or {}).get("result") or []
    if not results:
        return None
    order = {"PMC": 0, "MED": 1, "PPR": 2}
    return sorted(results, key=lambda r: order.get(r.get("source"), 99))[0]


def classify(oa_block: dict, hit: dict | None, has_doi: bool) -> tuple[str, str | None]:
    """Decide best_strategy + return constructed pmc_xml_url if applicable.

    EPMC quirk: search returns `source=MED` for most PubMed-indexed papers
    even when full-text is mirrored in PMC. The real signal that the OA
    JATS XML is available is `isOpenAccess=Y` AND `inEPMC=Y` AND a `pmcid`.
    """
    if not has_doi:
        return "no_doi", None

    if hit:
        in_epmc = (hit.get("inEPMC") or "").upper() == "Y"
        is_oa_epmc = (hit.get("isOpenAccess") or "").upper() == "Y"
        pmcid = hit.get("pmcid")  # e.g. "PMC5603991"
        if in_epmc and is_oa_epmc and pmcid:
            return "pmc_xml", EPMC_FULLTEXT_FMT.format(pmcid=pmcid)

    if oa_block.get("is_oa") and oa_block.get("oa_url"):
        return "oa_pdf", None
    return "paywalled", None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--max", type=int, default=0, help="Stop after N papers (0 = no limit).")
    ap.add_argument("--no-cache", action="store_true", help="Bypass cached EPMC responses.")
    args = ap.parse_args()

    if not JSONL.exists():
        print(f"ERROR: missing {JSONL}", file=sys.stderr)
        return 2

    load_dotenv(ROOT / ".env")
    mailto = os.environ.get("OPENALEX_MAILTO", "").strip() or "anonymous@example.invalid"
    if mailto == "anonymous@example.invalid":
        print("WARN: no OPENALEX_MAILTO in citations/.env; using anonymous contact.")

    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)

    n_total = 0
    n_no_doi = 0
    n_pmc_xml = 0
    n_oa_pdf = 0
    n_paywalled = 0
    n_epmc_hit = 0
    n_cache_hit = 0
    n_cache_miss = 0

    t_start = time.monotonic()

    with build_client(mailto) as client, JSONL.open() as fin, OUT_CSV.open(
        "w", newline="", encoding="utf-8"
    ) as fout:
        writer = csv.DictWriter(fout, fieldnames=CSV_FIELDS)
        writer.writeheader()

        for line in fin:
            rec = json.loads(line)
            n_total += 1
            if args.max and n_total > args.max:
                break

            oa_block = rec.get("open_access") or {}
            venue = rec.get("host_venue") or {}
            doi = normalize_doi(rec.get("doi"))

            hit: dict | None = None
            payload: dict | None = None
            if doi:
                cache_path = doi_cache_path(doi)
                if cache_path.exists() and not args.no_cache:
                    payload = json.loads(cache_path.read_text())
                    n_cache_hit += 1
                else:
                    try:
                        payload = epmc_search_by_doi(client, doi)
                    except httpx.HTTPError as e:
                        print(f"  ! {doi}: EPMC error {e!r}", file=sys.stderr)
                        payload = {"error": str(e)}
                    cache_path.write_text(json.dumps(payload, ensure_ascii=False))
                    n_cache_miss += 1
                hit = pick_epmc_hit(payload) if "error" not in payload else None

            if hit:
                n_epmc_hit += 1

            strategy, pmc_xml_url = classify(oa_block, hit, has_doi=bool(doi))
            if strategy == "pmc_xml":
                n_pmc_xml += 1
            elif strategy == "oa_pdf":
                n_oa_pdf += 1
            elif strategy == "paywalled":
                n_paywalled += 1
            else:
                n_no_doi += 1

            writer.writerow({
                "openalex_id": rec.get("openalex_id") or "",
                "doi": doi or "",
                "title": (rec.get("title") or "").replace("\n", " ").strip(),
                "publication_year": rec.get("publication_year") or "",
                "type": rec.get("type") or "",
                "is_oa": "true" if oa_block.get("is_oa") else "false",
                "oa_status": oa_block.get("oa_status") or "",
                "oa_url": oa_block.get("oa_url") or "",
                "license": venue.get("license") or "",
                "pmcid": (hit or {}).get("pmcid") or "",
                "pmid": (hit or {}).get("pmid") or "",
                "epmc_source": (hit or {}).get("source") or "",
                "in_epmc": (hit or {}).get("inEPMC") or "",
                "is_oa_epmc": (hit or {}).get("isOpenAccess") or "",
                "has_pdf_epmc": (hit or {}).get("hasPDF") or "",
                "has_fulltext_epmc": (hit or {}).get("hasFullText") or "",
                "pmc_xml_url": pmc_xml_url or "",
                "best_strategy": strategy,
            })

            if n_total % 100 == 0:
                rate = n_total / max(time.monotonic() - t_start, 0.001)
                print(
                    f"  {n_total:>5} processed (cache hit/miss "
                    f"{n_cache_hit}/{n_cache_miss}, EPMC hits {n_epmc_hit}, "
                    f"{rate:.1f} req/sec)",
                    flush=True,
                )

    elapsed = time.monotonic() - t_start
    print(f"\nDone in {elapsed:.1f}s. Wrote {OUT_CSV.relative_to(ROOT)} ({n_total} rows).")
    print(f"  cache hit/miss:      {n_cache_hit} / {n_cache_miss}")
    print(f"  Europe PMC hits:     {n_epmc_hit}")
    print(f"\nStrategy breakdown:")
    print(f"  pmc_xml:    {n_pmc_xml:>5}  ({100*n_pmc_xml/n_total:.1f}%)")
    print(f"  oa_pdf:     {n_oa_pdf:>5}  ({100*n_oa_pdf/n_total:.1f}%)")
    print(f"  paywalled:  {n_paywalled:>5}  ({100*n_paywalled/n_total:.1f}%)")
    print(f"  no_doi:     {n_no_doi:>5}  ({100*n_no_doi/n_total:.1f}%)")
    reachable = n_pmc_xml + n_oa_pdf
    print(f"\nReachable for free: {reachable} / {n_total} ({100*reachable/n_total:.1f}%)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
