"""Merge SequenceMatrix hits from the three CZ Software Mentions subsets into one CSV.

Inputs (all TSV, tab-separated, header in row 1):
  sm_hits_disambiguated.tsv   PMC-OA commercial subset (disambiguated)
  sm_hits_non_comm.tsv        PMC-OA non-commercial subset (raw)
  sm_hits_publishers.tsv      CZI publishers' collection (raw)

Output:
  sequencematrix_mentions.csv     one row per mention, unified schema
"""

import csv
from pathlib import Path

HERE = Path(__file__).parent
OUT = HERE / "sequencematrix_mentions.csv"

FIELDS = [
    "subset",          # comm | non_comm | publishers
    "pmcid",           # empty for publishers
    "pmid",            # empty for publishers
    "doi",
    "pubdate",
    "section",         # from the "source" column in the source TSV
    "paragraph_number",
    "software_mention",
    "version",         # empty for publishers (not extracted)
    "curation_label",
    "mapped_to_software",  # only present for the comm/disambiguated subset
    "text",            # the sentence containing the mention
]


def read_tsv(path):
    with path.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            yield row


def from_disambiguated(row):
    return {
        "subset": "comm",
        "pmcid": row.get("pmcid", ""),
        "pmid": row.get("pmid", ""),
        "doi": row.get("doi", ""),
        "pubdate": row.get("pubdate", ""),
        "section": row.get("source", ""),
        "paragraph_number": row.get("number", ""),
        "software_mention": row.get("software", ""),
        "version": row.get("version", ""),
        "curation_label": row.get("curation_label", ""),
        "mapped_to_software": row.get("mapped_to_software", ""),
        "text": row.get("text", ""),
    }


def from_non_comm(row):
    return {
        "subset": "non_comm",
        "pmcid": row.get("pmcid", ""),
        "pmid": row.get("pmid", ""),
        "doi": row.get("doi", ""),
        "pubdate": row.get("pubdate", ""),
        "section": row.get("source", ""),
        "paragraph_number": row.get("number", ""),
        "software_mention": row.get("software", ""),
        "version": row.get("version", ""),
        "curation_label": row.get("curation_label", ""),
        "mapped_to_software": "",
        "text": row.get("text", ""),
    }


def from_publishers(row):
    return {
        "subset": "publishers",
        "pmcid": "",
        "pmid": "",
        "doi": row.get("doi", ""),
        "pubdate": row.get("pubdate", ""),
        "section": row.get("source", ""),
        "paragraph_number": row.get("number", ""),
        "software_mention": row.get("software", ""),
        "version": "",
        "curation_label": row.get("curation_label", ""),
        "mapped_to_software": "",
        "text": row.get("text", ""),
    }


def main():
    rows = []
    rows.extend(from_disambiguated(r) for r in read_tsv(HERE / "sm_hits_disambiguated.tsv"))
    rows.extend(from_non_comm(r) for r in read_tsv(HERE / "sm_hits_non_comm.tsv"))
    rows.extend(from_publishers(r) for r in read_tsv(HERE / "sm_hits_publishers.tsv"))

    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, quoting=csv.QUOTE_MINIMAL)
        w.writeheader()
        w.writerows(rows)

    by_subset = {}
    unique_papers = set()
    for r in rows:
        by_subset[r["subset"]] = by_subset.get(r["subset"], 0) + 1
        key = r["doi"] or r["pmcid"] or r["text"][:60]
        unique_papers.add((r["subset"], key))

    print(f"Wrote {OUT.name}: {len(rows)} mention rows")
    for k, v in sorted(by_subset.items()):
        print(f"  {k}: {v} mentions")
    print(f"  ~unique papers (subset, doi|pmcid): {len(unique_papers)}")


if __name__ == "__main__":
    main()
