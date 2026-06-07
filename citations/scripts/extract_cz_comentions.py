"""Extract co-mentioned software from CZ Software Mentions for the SM-citing papers.

For each paper in `data/cz_software_mentions/sequencematrix_mentions.csv`
(the 372 papers that CZ catches mentioning SequenceMatrix), this script
streams the three relevant CZ source TSVs and aggregates every OTHER
software name CZ extracted from those same papers. The result is a
ranked vocabulary suitable for extending the Phase 2 co-mention scan
beyond the curated default list.

Inputs (place in `data/cz_software_mentions/_downloads/`):
    raw.tar.gz            2,788,799,205 bytes
        SHA-256 a20670a29bba09c778bafbd7661fb8ab767fae641a57257606971f2b34e07c77
    disambiguated.tar.gz  1,067,929,681 bytes
        SHA-256 da7f66172e9cb3862df27aecdf06e81f37c00fd13ae11c4cc1523e0f90e53e16

Both are part of CZ Software Mentions v11 on Dryad
(doi:10.5061/dryad.6wwpzgn2c). They are gitignored under
`_downloads/.gitignore` and safe to delete after the script runs.

Outputs (written to `data/cz_software_mentions/`):
    comentioned_software.csv    ranked vocabulary
    comentioned_per_paper.csv   one row per (paper, co-mentioned tool)

Usage:
    uv run scripts/extract_cz_comentions.py
    uv run scripts/extract_cz_comentions.py --skip-verify   # skip SHA-256 check
    uv run scripts/extract_cz_comentions.py --cleanup       # delete extracted TSVs at end

Stdlib only.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import re
import sys
import tarfile
from collections import Counter, defaultdict
from pathlib import Path

# Some CZ rows have very long sentence text in the `text` column; bump the
# csv module's field-size limit so DictReader doesn't choke.
csv.field_size_limit(sys.maxsize)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
CZ_DIR = ROOT / "data" / "cz_software_mentions"
DOWNLOADS = CZ_DIR / "_downloads"
SM_MENTIONS_CSV = CZ_DIR / "sequencematrix_mentions.csv"

OUT_VOCAB = CZ_DIR / "comentioned_software.csv"
OUT_PER_PAPER = CZ_DIR / "comentioned_per_paper.csv"

EXPECTED_SHA256 = {
    "raw.tar.gz": "a20670a29bba09c778bafbd7661fb8ab767fae641a57257606971f2b34e07c77",
    "disambiguated.tar.gz": "da7f66172e9cb3862df27aecdf06e81f37c00fd13ae11c4cc1523e0f90e53e16",
}

TARBALL_MEMBERS = {
    "raw.tar.gz": [
        "raw/non_comm_raw.tsv.gz",
        "raw/publishers_collections_raw.tsv.gz",
    ],
    "disambiguated.tar.gz": [
        "disambiguated/comm_disambiguated.tsv.gz",
    ],
}

# Match every surface form CZ used for SequenceMatrix so we don't list SM
# itself in the co-mention vocabulary. Mirrors the regex used to build
# sequencematrix_mentions.csv (see data/cz_software_mentions/README.md).
SM_NAME_RE = re.compile(r"sequence\s?matrix", re.IGNORECASE)

CHUNK = 1 << 20


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for buf in iter(lambda: f.read(CHUNK), b""):
            h.update(buf)
    return h.hexdigest()


def verify_downloads(skip: bool) -> None:
    missing = [n for n in EXPECTED_SHA256 if not (DOWNLOADS / n).exists()]
    if missing:
        print(f"ERROR: missing {missing} in {DOWNLOADS.relative_to(ROOT)}", file=sys.stderr)
        sys.exit(2)
    if skip:
        print("Skipping SHA-256 verification (--skip-verify).")
        return
    print("Verifying SHA-256 of bulk downloads ...")
    for name, expected in EXPECTED_SHA256.items():
        actual = sha256(DOWNLOADS / name)
        ok = actual == expected
        marker = "OK" if ok else f"MISMATCH (expected {expected})"
        print(f"  {name}: {actual}  [{marker}]")
        if not ok:
            sys.exit(2)


def extract_members() -> None:
    print("Extracting needed TSVs from tarballs ...")
    for archive, members in TARBALL_MEMBERS.items():
        with tarfile.open(DOWNLOADS / archive, "r:gz") as tf:
            for m in members:
                target = DOWNLOADS / m
                if target.exists():
                    print(f"  already on disk: {m}")
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                info = tf.getmember(m)
                with tf.extractfile(info) as src, target.open("wb") as dst:
                    while True:
                        buf = src.read(CHUNK)
                        if not buf:
                            break
                        dst.write(buf)
                print(f"  extracted: {m} ({target.stat().st_size:,} bytes)")


def load_paper_ids() -> tuple[set[str], set[str], set[str]]:
    """Read the SM-mentions CSV and return the paper-ID sets per subset."""
    comm: set[str] = set()
    non_comm: set[str] = set()
    publishers: set[str] = set()
    with SM_MENTIONS_CSV.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            subset = row["subset"]
            if subset == "comm" and row["pmcid"]:
                comm.add(row["pmcid"].strip())
            elif subset == "non_comm" and row["pmcid"]:
                non_comm.add(row["pmcid"].strip())
            elif subset == "publishers" and row["doi"]:
                publishers.add(row["doi"].strip().lower())
    return comm, non_comm, publishers


def stream_tsv_gz(path: Path):
    with gzip.open(path, "rt", encoding="utf-8", newline="") as f:
        yield from csv.DictReader(f, delimiter="\t")


def is_sm(*values: str) -> bool:
    return any(v and SM_NAME_RE.search(v) for v in values)


def filter_subset(
    label: str,
    path: Path,
    paper_ids: set[str],
    id_field: str,
    id_normalizer,
    per_paper: dict[tuple[str, str], dict[str, set[str]]],
) -> tuple[int, int]:
    """Stream one TSV, keep rows whose paper-ID is in `paper_ids`, drop SM rows."""
    scanned = 0
    kept = 0
    for row in stream_tsv_gz(path):
        scanned += 1
        raw_id = (row.get(id_field) or "").strip()
        if not raw_id:
            continue
        pid = id_normalizer(raw_id)
        if pid not in paper_ids:
            continue
        surface = (row.get("software") or "").strip()
        canonical = (row.get("mapped_to_software") or "").strip()
        if is_sm(surface, canonical):
            continue
        if not surface and not canonical:
            continue
        kept += 1
        # If mapped_to_software is missing or marked not_disambiguated,
        # fall back to the surface form as the canonical key.
        key = canonical if canonical and canonical != "not_disambiguated" else surface
        per_paper[(label, pid)][key].add(surface or canonical)
        if scanned % 1_000_000 == 0:
            print(f"  {label}: scanned {scanned:>10,}  kept {kept:>6,}", flush=True)
    print(f"  {label}: scanned {scanned:>10,}  kept {kept:>6,}  (final)", flush=True)
    return scanned, kept


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--skip-verify", action="store_true", help="Skip SHA-256 verification.")
    ap.add_argument(
        "--cleanup",
        action="store_true",
        help="Delete the extracted .tsv.gz files after writing outputs.",
    )
    args = ap.parse_args()

    if not SM_MENTIONS_CSV.exists():
        print(f"ERROR: missing {SM_MENTIONS_CSV}", file=sys.stderr)
        return 2

    verify_downloads(args.skip_verify)
    extract_members()

    comm_pmcids, non_comm_pmcids, publishers_dois = load_paper_ids()
    print(
        f"\nPaper-ID sets from {SM_MENTIONS_CSV.relative_to(ROOT)}:\n"
        f"  comm PMCIDs:        {len(comm_pmcids)}\n"
        f"  non_comm PMCIDs:    {len(non_comm_pmcids)}\n"
        f"  publishers DOIs:    {len(publishers_dois)}"
    )

    # (subset, paper_id) -> {canonical_name: set(surface forms)}
    per_paper: dict[tuple[str, str], dict[str, set[str]]] = defaultdict(
        lambda: defaultdict(set)
    )

    filter_subset(
        "comm",
        DOWNLOADS / "disambiguated" / "comm_disambiguated.tsv.gz",
        comm_pmcids,
        "pmcid",
        str.strip,
        per_paper,
    )
    filter_subset(
        "non_comm",
        DOWNLOADS / "raw" / "non_comm_raw.tsv.gz",
        non_comm_pmcids,
        "pmcid",
        str.strip,
        per_paper,
    )
    filter_subset(
        "publishers",
        DOWNLOADS / "raw" / "publishers_collections_raw.tsv.gz",
        publishers_dois,
        "doi",
        lambda s: s.strip().lower(),
        per_paper,
    )

    # Aggregate
    vocab_papers: Counter[str] = Counter()
    vocab_mentions: Counter[str] = Counter()
    vocab_surfaces: dict[str, set[str]] = defaultdict(set)
    for (_, _), tools in per_paper.items():
        for canonical, surfaces in tools.items():
            vocab_papers[canonical] += 1
            vocab_mentions[canonical] += len(surfaces)
            vocab_surfaces[canonical].update(surfaces)

    print(
        f"\nPapers with at least one OTHER tool: {len(per_paper)}\n"
        f"Distinct canonical co-mentioned tools: {len(vocab_papers):,}"
    )

    OUT_VOCAB.parent.mkdir(parents=True, exist_ok=True)
    with OUT_VOCAB.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["canonical", "n_papers", "n_surface_forms", "surface_forms"])
        for canonical, n_pap in vocab_papers.most_common():
            forms = sorted(vocab_surfaces[canonical])
            w.writerow([canonical, n_pap, len(forms), "; ".join(forms)])
    print(f"Wrote {OUT_VOCAB.relative_to(ROOT)}")

    with OUT_PER_PAPER.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["subset", "paper_id", "canonical", "surface_forms"])
        for (subset, pid), tools in sorted(per_paper.items()):
            for canonical, surfaces in sorted(tools.items()):
                w.writerow([subset, pid, canonical, "; ".join(sorted(surfaces))])
    print(f"Wrote {OUT_PER_PAPER.relative_to(ROOT)}")

    print("\nTop 30 co-mentioned tools (paper count):")
    for canonical, n in vocab_papers.most_common(30):
        forms = sorted(vocab_surfaces[canonical])
        sample = forms[0] if forms == [canonical] else f"{forms[:3]}{'…' if len(forms) > 3 else ''}"
        print(f"  {n:>4}  {canonical}   {sample}")

    if args.cleanup:
        print("\nCleaning up extracted TSVs (--cleanup) ...")
        for archive, members in TARBALL_MEMBERS.items():
            for m in members:
                p = DOWNLOADS / m
                if p.exists():
                    p.unlink()
                    print(f"  removed: {p.relative_to(ROOT)}")
        # Remove now-empty subdirs.
        for sub in ("raw", "disambiguated"):
            d = DOWNLOADS / sub
            if d.exists() and not any(d.iterdir()):
                d.rmdir()
                print(f"  removed empty dir: {d.relative_to(ROOT)}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
