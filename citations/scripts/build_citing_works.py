"""Flatten raw OpenAlex pages into one record per citing paper.

Reads citations/data/openalex/raw/page_*.json (produced by
fetch_openalex_citing.py), extracts the fields named in citations/README.md
Phase 1, and writes them to citations/data/openalex/citing_works.jsonl with
a `sources` provenance field so Semantic Scholar / Crossref enrichments can
append to it later.

Usage:
    uv run scripts/build_citing_works.py
"""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RAW_DIR = ROOT / "data" / "openalex" / "raw"
OUT_JSONL = ROOT / "data" / "openalex" / "citing_works.jsonl"


def decode_abstract(inverted: dict[str, list[int]] | None) -> str | None:
    """OpenAlex stores abstracts as {word: [positions]}; recover the text."""
    if not inverted:
        return None
    positions: list[tuple[int, str]] = []
    for word, idxs in inverted.items():
        for i in idxs:
            positions.append((i, word))
    positions.sort()
    return " ".join(word for _, word in positions)


def flatten_work(w: dict) -> dict:
    primary = w.get("primary_location") or {}
    source = primary.get("source") or {}
    oa = w.get("open_access") or {}

    authors = []
    for a in w.get("authorships") or []:
        author = a.get("author") or {}
        insts = []
        for inst in a.get("institutions") or []:
            insts.append(
                {
                    "ror": inst.get("ror"),
                    "display_name": inst.get("display_name"),
                    "country_code": inst.get("country_code"),
                    "type": inst.get("type"),
                }
            )
        authors.append(
            {
                "display_name": author.get("display_name"),
                "orcid": author.get("orcid"),
                "position": a.get("author_position"),
                "is_corresponding": a.get("is_corresponding"),
                "raw_affiliation_strings": a.get("raw_affiliation_strings") or [],
                "institutions": insts,
                "countries": a.get("countries") or [],
            }
        )

    concepts = [
        {
            "display_name": c.get("display_name"),
            "level": c.get("level"),
            "score": c.get("score"),
        }
        for c in (w.get("concepts") or [])
    ]
    topics = [
        {
            "display_name": t.get("display_name"),
            "score": t.get("score"),
            "subfield": (t.get("subfield") or {}).get("display_name"),
            "field": (t.get("field") or {}).get("display_name"),
            "domain": (t.get("domain") or {}).get("display_name"),
        }
        for t in (w.get("topics") or [])
    ]

    return {
        "openalex_id": w.get("id"),
        "doi": w.get("doi"),
        "title": w.get("display_name"),
        "abstract": decode_abstract(w.get("abstract_inverted_index")),
        "publication_year": w.get("publication_year"),
        "publication_date": w.get("publication_date"),
        "language": w.get("language"),
        "type": w.get("type"),
        "is_retracted": w.get("is_retracted"),
        "is_paratext": w.get("is_paratext"),
        "cited_by_count": w.get("cited_by_count"),
        "referenced_works_count": w.get("referenced_works_count"),
        "host_venue": {
            "source_id": source.get("id"),
            "display_name": source.get("display_name"),
            "type": source.get("type"),
            "issn_l": source.get("issn_l"),
            "issns": source.get("issn") or [],
            "host_organization": source.get("host_organization_name"),
            "is_oa": primary.get("is_oa"),
            "license": primary.get("license"),
            "version": primary.get("version"),
            "landing_page_url": primary.get("landing_page_url"),
            "pdf_url": primary.get("pdf_url"),
        },
        "open_access": {
            "is_oa": oa.get("is_oa"),
            "oa_status": oa.get("oa_status"),
            "oa_url": oa.get("oa_url"),
            "any_repository_has_fulltext": oa.get("any_repository_has_fulltext"),
        },
        "authors": authors,
        "concepts": concepts,
        "topics": topics,
        "sources": ["openalex"],
    }


def main() -> int:
    pages = sorted(RAW_DIR.glob("page_*.json"))
    if not pages:
        print(f"No pages found in {RAW_DIR}", file=sys.stderr)
        return 2

    OUT_JSONL.parent.mkdir(parents=True, exist_ok=True)

    seen_ids: set[str] = set()
    dup_count = 0
    year_hist: Counter[int] = Counter()
    type_hist: Counter[str] = Counter()
    oa_hist: Counter[str] = Counter()
    missing_doi = 0
    rows_written = 0

    with OUT_JSONL.open("w", encoding="utf-8") as out:
        for page in pages:
            data = json.loads(page.read_text())
            for w in data.get("results", []):
                wid = w.get("id")
                if wid in seen_ids:
                    dup_count += 1
                    continue
                seen_ids.add(wid)

                row = flatten_work(w)
                out.write(json.dumps(row, ensure_ascii=False) + "\n")
                rows_written += 1

                if row["publication_year"]:
                    year_hist[row["publication_year"]] += 1
                if row["type"]:
                    type_hist[row["type"]] += 1
                oa_hist[row["open_access"]["oa_status"] or "unknown"] += 1
                if not row["doi"]:
                    missing_doi += 1

    print(f"Wrote {rows_written} unique records to {OUT_JSONL.relative_to(ROOT)}")
    print(f"  duplicates skipped: {dup_count}")
    print(f"  missing DOI: {missing_doi}")
    print("\n  type breakdown:")
    for t, n in type_hist.most_common():
        print(f"    {n:>5} {t}")
    print("\n  OA status breakdown:")
    for s, n in oa_hist.most_common():
        print(f"    {n:>5} {s}")
    print("\n  year histogram:")
    for y in sorted(year_hist):
        print(f"    {y}: {year_hist[y]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
