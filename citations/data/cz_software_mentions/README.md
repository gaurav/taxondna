# CZ Software Mentions — SequenceMatrix extract

This directory holds the results of searching the **Chan Zuckerberg Initiative's
Software Mentions** dataset for citations of SequenceMatrix. The bulk downloads
have been deleted after extraction; only the derived hit files and the merge
script are kept here. See `citations/README.md` for the broader project plan.

## Headline findings

- **457 sentence-level mentions across 372 unique-DOI papers** explicitly name
  SequenceMatrix in the CZ corpora.
- That's ~14% of OpenAlex's ~2,597 papers citing the SequenceMatrix paper
  (`W2131473084` / DOI `10.1111/j.1096-0031.2010.00329.x`).
- Coverage by CZ subset: **comm 338 / publishers 87 / non_comm 32**.
- 16 surface forms; 94% are `SequenceMatrix` (335) or `Sequence Matrix` (94).
  Long-tail variants include `SEQUENCEMATRIX`, `Sequencematrix`,
  `Java Sequence Matrix`, and a few NER errors that fused the name with its
  citation number (`SequenceMatrix148`).
- Mentions are concentrated in Methods-style sections: `Phylogenetic analysis`
  (44), `Phylogenetic analyses` (43), `Materials and Methods` (variants
  totalling ~50). No surprises.
- Year trend (CZ collection cutoff: October 2021):

  | Year | Mentions |
  | ---: | ---: |
  | 2010 | 1 |
  | 2011 | 1 |
  | 2012 | 7 |
  | 2013 | 6 |
  | 2014 | 17 |
  | 2015 | 25 |
  | 2016 | 32 |
  | 2017 | 56 |
  | 2018 | 59 |
  | 2019 | 89 |
  | 2020 | 89 |
  | 2021 | 75 (partial) |

## Files in this directory

| File | Description |
| --- | --- |
| `sequencematrix_mentions.csv` | **Primary output.** All 457 mentions, unified schema (subset, pmcid, pmid, doi, pubdate, section, paragraph_number, software_mention, version, curation_label, mapped_to_software, text). |
| `comentioned_software.csv` | **Co-mention vocabulary.** 1,672 distinct canonical tool names CZ also extracted from the 372 SM-citing papers, ranked by paper count (`canonical, n_papers, n_surface_forms, surface_forms`). Top entries (MrBayes, RAxML, MAFFT, MEGA, …) feed the Phase 2 software co-mention scan. |
| `comentioned_per_paper.csv` | One row per (paper, co-mentioned canonical tool), with the surface forms CZ saw. Useful for QA on the vocabulary. |
| `sm_hits_disambiguated.tsv` | Raw hits from the `comm` disambiguated TSV (338 rows + header). Has the `mapped_to_software` clustering field that the raw subsets lack. |
| `sm_hits_non_comm.tsv` | Raw hits from PMC-OA non-commercial (32 rows + header). |
| `sm_hits_publishers.tsv` | Raw hits from the CZI publishers' collection (87 rows + header). |
| `unique_software.txt` | All 16 surface forms with combined counts, for eyeballing typos/variants. |
| `unique_mapped_to_software.txt` | Canonical (disambiguated) names with counts. Non-comm and publishers rows can't be disambiguated and are bucketed under `(not disambiguated …)`. |
| `merge_hits.py` | Script that produced `sequencematrix_mentions.csv` from the three TSVs. |
| `_downloads/` | Gitignored; holds the bulk Dryad tarballs and the three extracted `.tsv.gz` files when present. Empty in the checked-in repo. |
| `README.md` | This file. |

The upstream dataset's own `README.md` (the authoritative column schema)
is not kept here — fetch it from the Dryad landing page if you need to
verify column meanings.

### Bulk downloads (when present)

The two bulk tarballs are re-downloaded into `_downloads/` only when the
co-mention extract needs to be regenerated:

| File | Size | SHA-256 |
| --- | ---: | --- |
| `raw.tar.gz` | 2,788,799,205 bytes | `a20670a29bba09c778bafbd7661fb8ab767fae641a57257606971f2b34e07c77` |
| `disambiguated.tar.gz` | 1,067,929,681 bytes | `da7f66172e9cb3862df27aecdf06e81f37c00fd13ae11c4cc1523e0f90e53e16` |

Both match Dryad's published digests at
[`/api/v2/versions/198470/files`](https://datadryad.org/api/v2/versions/198470/files).
Anonymous CLI download does not work (Dryad's API requires an OAuth
bearer token); go through the landing page in a browser instead.
After `extract_cz_comentions.py` finishes you can delete `_downloads/`
to reclaim ~3.9 GB; the script will re-extract on the next run if asked.

## Source and licensing

- **Dataset paper:** Istrate, A.-M., Li, D., Taraborelli, D., Torkar, M.,
  Veytsman, B., & Williams, I. (2022). *A large dataset of software mentions
  in the biomedical literature.*
  arXiv:[2209.00693](https://arxiv.org/abs/2209.00693).
- **Data DOI:** [10.5061/dryad.6wwpzgn2c](https://doi.org/10.5061/dryad.6wwpzgn2c)
- **Dryad landing page:** https://datadryad.org/dataset/doi:10.5061/dryad.6wwpzgn2c
- **Source code:** https://github.com/chanzuckerberg/software-mentions
  (separately published as
  Zenodo:[7041594](https://zenodo.org/record/7041594))
- **Data license:** **CC0 1.0 Universal (Public Domain Dedication).** No
  attribution legally required, but please cite the dataset paper above when
  reusing the data.
- **Code license:** MIT (per the GitHub repo).
- The dataset is derived from PubMed Central plus papers contributed under
  agreement by various publishers (the "publishers' collection"). The PMC
  corpus collection was October 2021. The dataset version on Dryad is
  v11, published 2022-09-27.
- **Files downloaded on 2026-05-24** (via Gaurav's browser; see the table
  above for sizes and SHA-256s).

## Method

1. Untar each archive and pull only the TSVs we need:
   ```sh
   tar -xzf disambiguated.tar.gz disambiguated/comm_disambiguated.tsv.gz
   tar -xzf raw.tar.gz raw/non_comm_raw.tsv.gz raw/publishers_collections_raw.tsv.gz
   ```
2. Stream each gzipped TSV through `awk`, keeping the header plus any row whose
   `software` column matches the case-insensitive regex `sequence ?matrix`:
   ```sh
   gunzip -c disambiguated/comm_disambiguated.tsv.gz \
     | awk -F'\t' 'NR==1 {print; next} tolower($10) ~ /sequence ?matrix/ || tolower($14) ~ /sequence ?matrix/' \
     > sm_hits_disambiguated.tsv

   gunzip -c raw/non_comm_raw.tsv.gz \
     | awk -F'\t' 'NR==1 {print; next} tolower($10) ~ /sequence ?matrix/' \
     > sm_hits_non_comm.tsv

   gunzip -c raw/publishers_collections_raw.tsv.gz \
     | awk -F'\t' 'NR==1 {print; next} tolower($6) ~ /sequence ?matrix/' \
     > sm_hits_publishers.tsv
   ```
   (Column indices: `software` is col 10 in the PMC TSVs, col 6 in the publishers'
   TSV, which has a narrower schema. The disambiguated TSV adds `mapped_to_software`
   at col 14.)

   **Platform gotcha:** the `tolower($n)` call above is portable, but the
   first version used `BEGIN{IGNORECASE=1}` which is gawk-only and is
   *silently ignored* by macOS's BSD `awk`. If you re-run this on macOS,
   either keep the `tolower()` form or install GNU awk (`brew install gawk`)
   and invoke it as `gawk`.

3. Merge into one CSV with a unified schema: `python3 merge_hits.py`.
   Python 3.9+ stdlib only — no third-party dependencies.
4. Spot-check the 16 surface forms in `unique_software.txt`. All variants in
   our pull are genuine SequenceMatrix references; the disambiguator missed
   the 6 all-caps `SEQUENCEMATRIX` rows and the 1 `Java Sequence Matrix` row,
   which is why those show as `not_disambiguated` in the `mapped_to_software`
   field but **are** real hits and are kept in the CSV.
5. Delete the bulk downloads and extracted gzipped TSVs (optional — they
   are gitignored under `_downloads/`).

### Co-mention vocabulary build (`comentioned_software.csv`)

To extend Phase 2's software co-mention scan beyond a hand-picked default
list, we want every *other* software CZ extracted from the same 372
SM-citing papers. This is done by
[`../../scripts/extract_cz_comentions.py`](../../scripts/extract_cz_comentions.py)
(stdlib-only Python; no `awk` gymnastics):

```sh
cd citations
uv run scripts/extract_cz_comentions.py
```

The script:

1. Verifies the SHA-256 of `_downloads/raw.tar.gz` and
   `_downloads/disambiguated.tar.gz` against the digests in this README.
2. Selectively `tar -x`s the three needed `.tsv.gz` files (it never
   inflates them).
3. Reads `sequencematrix_mentions.csv` to build three paper-ID sets:
   - `comm` PMCIDs (331 unique)
   - `non_comm` PMCIDs (31 unique)
   - `publishers` DOIs (41 unique, lowercased for matching)
4. Streams each gzipped TSV through `gzip.open` + `csv.DictReader`,
   keeping rows whose paper-ID is in the matching set AND whose
   `software` (and `mapped_to_software`, on the comm subset) does not
   match `/sequence\s?matrix/i`. Each kept row contributes its
   `mapped_to_software` (or surface form, where disambiguation is
   absent) to a per-paper bag.
5. Aggregates across all papers and writes `comentioned_software.csv`
   ranked by paper count, plus the per-paper detail in
   `comentioned_per_paper.csv`.

Headline numbers from the 2026-05-24 run:

- Scanned: 14.8M comm rows + 4.5M non_comm rows + 48.2M publishers rows.
- Kept: 7,628 + 786 + 875 = 9,289 mention rows in our 372 papers.
- Result: **402 unique (subset, paper) records with at least one other
  tool, naming 1,672 distinct canonical co-mentioned software**.
- Top 5 by paper count: MrBayes (206), RAxML (191), MAFFT (179),
  MEGA (177), PartitionFinder (124).

The 402 > 372 gap is because some PMC papers appear in both the `comm`
and `non_comm` subsets (different licensing buckets, same paper).

The top ~30 of these (≥15-paper threshold, NER noise filtered) are
hand-curated into `TOOL_PATTERNS` in
[`../../scripts/analyze_phase2.py`](../../scripts/analyze_phase2.py).
The long tail stays in `comentioned_software.csv` for inspection — it
is more useful as input to Phase 4 LLM extraction than as additional
abstract-scan regexes.

### Reproducing from scratch

If the Dryad files were ever to disappear, the original CZI source code on
GitHub plus a fresh PMC dump would reproduce a comparable extract — but the
disambiguation cluster IDs would not match (they are tied to the 2022 run),
and the publishers' collection is irretrievable without re-negotiating the
CZI publisher agreements. So treat the Dryad snapshot as the authoritative
source.

## Caveats and what's still missing

- **CZ doesn't see ~86% of the literature citing SequenceMatrix.** OpenAlex
  counts ~2,597 citing works; we find 372 here. The gap is mostly papers in
  taxonomy/zoology journals that aren't in PMC and weren't part of CZI's
  publisher agreements (Cladistics itself, Zootaxa, *Insect Systematics*,
  *Systematic Entomology*, etc.). Those need Phase 1 (OpenAlex) of the main
  plan to enumerate, and Phase 3 (Unpaywall / direct download) for full text.
- **Disambiguation only covers the comm subset.** For non_comm and publishers
  rows, we don't have CZ's clustering — but the 16 surface forms across the
  combined hits are all visibly SequenceMatrix, so this isn't a real loss.
- **Version extraction is patchy.** Even in the disambiguated comm subset, the
  `version` field is empty for most rows; it's populated when the NER model
  caught a version token directly adjacent to the mention.
- **Mention != usage.** Some of these papers may cite SequenceMatrix in
  passing or in a review/methods comparison rather than actually using it.
  Confirming actual use is a Phase 4 (LLM extraction) job, not something the
  CZ data answers directly. The `section` column is a useful first filter:
  mentions in "Materials and Methods" / "Phylogenetic analysis" sections are
  much more likely to be real uses than mentions in introductions or
  references.

## How this feeds the broader plan

The 372 papers here are essentially a **free, high-confidence seed set** for
Phase 4 of the analysis (`citations/README.md`):

- Every row already has DOI + the exact mention sentence + the section name.
- For the 338 + 32 = 370 PMC rows, Europe PMC will give us full-text XML
  with sections already tagged — no GROBID step needed.
- Run the LLM dataset-size / complaint extraction against this subset first
  as a pilot, before going to the wider OpenAlex corpus.
