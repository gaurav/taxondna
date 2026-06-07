# Full-text coverage

This directory holds the **Phase 3a** output: a one-row-per-paper table
that says, for each of the 2,509 SM-citing OpenAlex works, *can we get
the full text for free, and if so, in what form?* See
[`citations/README.md`](../../README.md) for the broader plan.

No bytes are downloaded; Phase 3a is metadata-only. Per the scope answer
on 2026-05-25, the actual full-text download step (Phase 3b) is gated on
Phase 4 decisions (50-paper pilot vs full corpus, etc.).

## Headline findings (run on 2026-05-25)

- **828 papers (33.0%) → `pmc_xml`** — paper is in the Europe PMC Open
  Access subset; section-tagged JATS XML is downloadable from
  `https://www.ebi.ac.uk/europepmc/webservices/rest/article/PMC{id}/fullTextXML`.
  Best input for Phase 4 LLM extraction: methods/results/discussion are
  already tagged, no GROBID step needed.
- **912 papers (36.3%) → `oa_pdf`** — paper is OA (gold / green /
  bronze / diamond / hybrid per OpenAlex+Unpaywall) and has a resolvable
  PDF URL, but is not in the PMC OA subset. Phase 3b would download via
  the `oa_url` and Phase 4 would need GROBID to recover sections.
- **760 papers (30.3%) → `paywalled`** — no OA route. Skipped per the
  OA-only policy.
- **9 papers (0.4%) → `no_doi`** — grey literature, dissertations, etc.
  Not reachable through DOI-keyed APIs.
- **Reachable for free: 1,740 / 2,509 (69.4%).**

### Coverage by year

| Year | pmc_xml | oa_pdf | paywalled | no_doi | Total | Free % |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2010 | 1 | 1 | 0 | 0 | 2 | 100.0% |
| 2011 | 1 | 0 | 2 | 0 | 3 | 33.3% |
| 2012 | 6 | 4 | 3 | 0 | 13 | 76.9% |
| 2013 | 5 | 8 | 16 | 0 | 29 | 44.8% |
| 2014 | 12 | 21 | 17 | 1 | 51 | 64.7% |
| 2015 | 16 | 23 | 29 | 3 | 71 | 54.9% |
| 2016 | 26 | 33 | 33 | 2 | 94 | 62.8% |
| 2017 | 38 | 55 | 61 | 2 | 156 | 59.6% |
| 2018 | 37 | 71 | 69 | 0 | 177 | 61.0% |
| 2019 | 77 | 89 | 77 | 1 | 244 | 68.0% |
| 2020 | 79 | 93 | 87 | 0 | 259 | 66.4% |
| 2021 | 103 | 111 | 82 | 0 | 296 | 72.3% |
| 2022 | 109 | 95 | 63 | 0 | 267 | 76.4% |
| 2023 | 80 | 89 | 59 | 0 | 228 | 74.1% |
| 2024 | 92 | 121 | 70 | 0 | 283 | 75.3% |
| 2025 | 116 | 73 | 76 | 0 | 265 | 71.3% |
| 2026 | 30 | 25 | 16 | 0 | 71 *(partial)* | 77.5% |
| **all** | **828** | **912** | **760** | **9** | **2,509** | **69.4%** |

Reach is stable at **~70%+ for 2021–2026** — exactly the window the
2026-06-08 proposal cares about. Phase 4 input size is on the order of
1,700 papers without any paywall-circumvention.

## Files in this directory

| File | Description |
| --- | --- |
| `coverage.csv` | **Primary output.** One row per paper (2,509 + header). See schema below. |
| `_cache/europepmc/{sha256(doi)}.json` | Cached Europe PMC search responses. Gitignored. Reruns of `build_fulltext_coverage.py` read from disk and skip the HTTP call. Safe to delete; the next run will refetch. |
| `README.md` | This file. |

`coverage.csv` is regenerated from `../openalex/citing_works.jsonl` plus
Europe PMC by [`../../scripts/build_fulltext_coverage.py`](../../scripts/build_fulltext_coverage.py).
It is checked in for convenience.

## Schema of `coverage.csv`

| Column | Source | Description |
| --- | --- | --- |
| `openalex_id` | OpenAlex | E.g. `https://openalex.org/W2979811825`. Primary key. |
| `doi` | OpenAlex (normalized) | Bare DOI, lowercased, prefix stripped. Empty for 9 records. |
| `title`, `publication_year`, `type` | OpenAlex | Carried through for spot-checking. |
| `is_oa` | OpenAlex | Boolean. |
| `oa_status` | OpenAlex | `gold` / `green` / `diamond` / `bronze` / `hybrid` / `closed` / empty. |
| `oa_url` | OpenAlex | Unpaywall-resolved best OA URL, if any. |
| `license` | OpenAlex | The host_venue licence, when populated (often empty). |
| `pmcid` | Europe PMC | E.g. `PMC5603991`. Empty if the paper isn't indexed in PMC. |
| `pmid` | Europe PMC | PubMed ID, if indexed. |
| `epmc_source` | Europe PMC | Almost always `MED` (PubMed source) even when the paper's full-text is in PMC. Recorded for transparency. |
| `in_epmc`, `is_oa_epmc`, `has_pdf_epmc`, `has_fulltext_epmc` | Europe PMC | `Y`/`N` flags from the search hit. |
| `pmc_xml_url` | constructed | Europe PMC full-text XML endpoint when `best_strategy == pmc_xml`. Confirmed-working URL pattern. |
| `best_strategy` | derived | One of `pmc_xml`, `oa_pdf`, `paywalled`, `no_doi`. |

### Strategy decision rule (from `scripts/build_fulltext_coverage.py`)

```
1. If the paper has no DOI                                            → no_doi
2. Else if EPMC found it AND isOpenAccess=Y AND inEPMC=Y AND pmcid    → pmc_xml
3. Else if OpenAlex says is_oa AND has a non-empty oa_url             → oa_pdf
4. Otherwise                                                          → paywalled
```

EPMC quirk worth knowing: search hits in `resultType=lite` return
`source=MED` (the PubMed metadata source) for almost every paper, even
ones whose body is mirrored in PMC. The actual signal that full-text is
available is `isOpenAccess=Y` + `inEPMC=Y` + a populated `pmcid` — not
the `source` field.

## Method

```sh
cd citations
uv run scripts/build_fulltext_coverage.py
```

The script:

1. Loads `OPENALEX_MAILTO` from `citations/.env` (reused as the polite
   contact in the `User-Agent` header for Europe PMC).
2. Reads every record in `../openalex/citing_works.jsonl`.
3. For each paper with a DOI, hits Europe PMC's
   `/webservices/rest/search?query=DOI:"…"&resultType=lite` endpoint.
   Raw JSON responses are cached to `_cache/europepmc/{sha256(doi)}.json`;
   reruns are free.
4. Picks the best hit (preferring PMC > MED > PPR if multiple), classifies
   per the rule above, and writes `coverage.csv`.

Tenacity-driven retries with exponential backoff handle transient HTTP
errors. The achieved request rate against Europe PMC was ~5 req/sec
(no concurrency); the full 2,500-paper run took 502 seconds.

We deliberately **do not** re-query Unpaywall directly. OpenAlex sources
its OA data from Unpaywall (Priem et al. 2022, arXiv:2205.01833) and
Phase 1 already snapshot that information; the marginal accuracy gain
from a second Unpaywall pass isn't worth the request volume.

## Source and licensing

- **Europe PMC REST API:** [docs](https://europepmc.org/RestfulWebService).
  No API key needed. Polite-pool convention is to include a contact email
  in the `User-Agent` string; we do.
- **Europe PMC OA full-text terms:** the OA subset is licensed for
  text-mining ([terms](https://europepmc.org/Help#textmining)). We are
  recording URLs here, not downloading bytes; Phase 3b will respect the
  per-article license.
- **Europe PMC citation:** Europe PMC Consortium (2015). *Europe PMC: a
  full-text literature database for the life sciences and platform for
  innovation.* Nucleic Acids Research, 43(D1), D1042–D1048.
  [doi:10.1093/nar/gku1061](https://doi.org/10.1093/nar/gku1061).

## What's still missing (notes for Phase 3b / Phase 4)

- **Confirmation that `oa_url` actually resolves.** OpenAlex/Unpaywall
  occasionally point at dead landing pages. Phase 3b should HEAD-check
  each URL and downgrade to `paywalled` on 4xx/5xx.
- **A handful of `green` papers were classified `paywalled`** (7 cases).
  These have `oa_status=green` per OpenAlex but no usable `oa_url`,
  which usually means a repository deposit OpenAlex knows about but
  couldn't resolve. Worth a re-pass through Unpaywall directly if those
  7 papers matter.
- **No PDF size / page count estimate.** Phase 4 cost projection
  (Sonnet 4.6 input tokens) depends on these. Easy to add in Phase 3b
  via a HEAD request on each PDF URL.
- **No deduplication against the CZ Software Mentions extract.** 370 of
  our pmc_xml hits are likely the same papers already in
  `../cz_software_mentions/sequencematrix_mentions.csv`; that overlap
  is a free Phase 4 pilot corpus.
