# OpenAlex citing-works pull

This directory holds the **Phase 1** output of the SequenceMatrix citation
analysis: every paper OpenAlex says cites Vaidya, Lohman & Meier 2011
(`W2131473084` / DOI `10.1111/j.1096-0031.2010.00329.x`), with rich metadata
attached. See `citations/README.md` for the broader plan.

## Headline findings

- **2,509 unique citing works** retrieved from OpenAlex on 2026-05-24.
  (The README's anchor table guesses ~2,597; OpenAlex's current count is
  2,509. The Google Scholar count of 2,986 implies ~480 papers / ~16% live
  outside OpenAlex's index.)
- **70% open access** in some form: 955 gold + 306 green + 183 diamond +
  175 bronze + 133 hybrid = 1,752 papers. 757 (30%) are closed.
- **~44% post-CZ-cutoff:** 1,114 of 2,509 citing works were published in
  2022 or later — exactly the slice the CZ Software Mentions dump in
  `../cz_software_mentions/` misses. This is the main payoff of doing
  Phase 1 rather than relying on CZ alone.
- **Steady citation tempo since 2021:** 296 (2021) → 267 → 228 → 283 → 265
  → 71 (partial 2026). The paper is still being actively cited 15 years
  after publication.
- Almost everything is an `article` (2,264) or `preprint` (112). The rest:
  68 peer-reviews, 38 dissertations, 13 reviews, plus a long tail.
- **9 records (~0.4%) have no DOI** — typically grey literature or
  dissertations. They are kept in the JSONL with `doi: null`; Phase 3 will
  not be able to pull full text for those.
- Year distribution (full):

  | Year | Citing works | | Year | Citing works |
  | ---: | ---: | --- | ---: | ---: |
  | 2010 | 2   | | 2019 | 244 |
  | 2011 | 3   | | 2020 | 259 |
  | 2012 | 13  | | 2021 | 296 |
  | 2013 | 29  | | 2022 | 267 |
  | 2014 | 51  | | 2023 | 228 |
  | 2015 | 71  | | 2024 | 283 |
  | 2016 | 94  | | 2025 | 265 |
  | 2017 | 156 | | 2026 | 71 (partial) |
  | 2018 | 177 | | | |

## Files in this directory

| File | Description |
| --- | --- |
| `citing_works.jsonl` | **Primary output.** One JSON object per citing paper (2,509 lines). Schema below. |
| `raw/page_0001.json` … `raw/page_0013.json` | Verbatim OpenAlex API responses. Saved so the analysis is reproducible and so re-runs don't re-hit the API. Pages 1–12 hold 200 results each; page 13 holds 109. |
| `README.md` | This file. |

`citing_works.jsonl` is regenerated from `raw/` by `../../scripts/build_citing_works.py`; it is checked in for convenience but is fully derivable from the raw pages.

## Schema of `citing_works.jsonl`

Each line is one citing paper. The `sources` field is a list of provenance
tags; right now it always contains just `["openalex"]`, and Semantic Scholar
or Crossref enrichments (if we ever run them) will append to it.

| Field | Type | Notes |
| --- | --- | --- |
| `openalex_id` | str URL | e.g. `https://openalex.org/W2979811825` |
| `doi` | str URL or null | e.g. `https://doi.org/10.1111/1755-0998.13096`; null for ~9 records |
| `title` | str | OpenAlex's `display_name` |
| `abstract` | str or null | reconstructed from OpenAlex's `abstract_inverted_index` |
| `publication_year` | int | |
| `publication_date` | str | ISO date |
| `language` | str | ISO 639-1 code (`en`, `pt`, …) |
| `type` | str | `article`, `preprint`, `dissertation`, … |
| `is_retracted` | bool | |
| `is_paratext` | bool | |
| `cited_by_count` | int | citations of the citing paper itself |
| `referenced_works_count` | int | |
| `host_venue` | object | source ID, display name, type, ISSNs, host org, OA flags, license, version, landing-page URL, PDF URL |
| `open_access` | object | `is_oa`, `oa_status` (gold/green/diamond/bronze/hybrid/closed), `oa_url`, `any_repository_has_fulltext` |
| `authors` | list of object | per author: `display_name`, `orcid`, `position`, `is_corresponding`, `raw_affiliation_strings`, `institutions` (each with `ror`, `display_name`, `country_code`, `type`), `countries` |
| `concepts` | list of object | OpenAlex concepts with `display_name`, `level`, `score`. Will be deprecated in favor of `topics`. |
| `topics` | list of object | OpenAlex topics with `display_name`, `score`, `subfield`, `field`, `domain` |
| `sources` | list of str | provenance — currently `["openalex"]` |

## Source

- **OpenAlex** (https://openalex.org), CC0 metadata.
- API endpoint: `GET https://api.openalex.org/works?filter=cites:W2131473084&per-page=200&cursor=*`
- Authentication via `Authorization: Bearer <key>` header (not query param,
  to keep keys out of any logged URLs). Plus `mailto=` query for the polite
  pool contact.
- Documentation: https://docs.openalex.org/api-entities/works
- Polite pool / API-key policy: https://docs.openalex.org/how-to-use-the-api/rate-limits-and-authentication

## Method

1. **Fetch.** `uv run scripts/fetch_openalex_citing.py` paginates with
   `cursor=*`, requests 200 records per page, writes each page verbatim to
   `raw/page_NNNN.json`. The script is idempotent: re-running it skips
   pages already on disk and resumes from the last page's `next_cursor`.
   Retries on HTTP errors are handled by tenacity (exponential backoff,
   max 5 attempts).
2. **Flatten.** `uv run scripts/build_citing_works.py` walks the raw pages
   and writes one record per work to `citing_works.jsonl`, with the schema
   above. OpenAlex's `abstract_inverted_index` is decoded back to plain
   text. Duplicate `openalex_id`s are deduplicated (none were found this
   run, but the deduper is there for safety against pagination quirks).

Run on 2026-05-24. Pages 1–13 SHA-256s:

```
252eb5342248b211783b407937049ab9ff01a793a74db3a9f21ce3d2d8c430af  raw/page_0001.json
6cdef7a324d79b266ab3b611e1dc833653dd6cc5a6a5b44e3ebc26a588d71fd4  raw/page_0002.json
c5afb4c32b70877701d21b92493fc9bd3b510af4eace5bae9376264d758bb964  raw/page_0003.json
(... full list: shasum -a 256 raw/page_*.json)
```

OpenAlex updates its corpus continuously, so a re-fetch will return a
slightly different result set. Treat the snapshot here as authoritative
for the analysis written into the proposal; cite this directory by commit
SHA when referencing the numbers.

## Reproducing from scratch

```sh
cd citations
cp .env.example .env       # fill in OPENALEX_API_KEY and OPENALEX_MAILTO
uv sync
rm -rf data/openalex/raw   # only if you want a fresh fetch
uv run scripts/fetch_openalex_citing.py
uv run scripts/build_citing_works.py
```

The fetcher requires both env vars; a missing one exits with code 2 before
hitting the network. An API key has been required for the OpenAlex polite
pool since 2026-02-13 — sign up at https://openalex.org.

## Caveats and what's still missing

- **~16% recall gap vs. Google Scholar.** Google Scholar reports 2,986
  citations (vs. 2,509 here). The gap is roughly the rate at which papers
  index in GS but not OpenAlex (preprint server quirks, theses, conference
  proceedings, non-English regional journals). For trend analysis and
  top-line numbers this is fine; for "every paper that ever cited SM",
  it is not.
- **Single-source.** README Phase 1 also calls for Semantic Scholar and
  Crossref cross-checks. Deferred per the kickoff decision — fast path
  to a usable table first; add enrichments if Phase 2 surfaces gaps.
- **Affiliations are NER-extracted.** `authors[].institutions[].ror`
  is missing for some authors where OpenAlex couldn't resolve the
  affiliation string to a ROR record. The `raw_affiliation_strings`
  field is kept so we can do our own normalization in Phase 2 if needed.
- **`concepts` is being deprecated** by OpenAlex in favor of `topics`.
  Both are kept on disk so we can pick whichever holds up better in
  Phase 2 analysis.
- **Mention != usage.** As with the CZ data, citation here only means
  "this paper has SequenceMatrix in its reference list" — not necessarily
  that the authors used the tool. Confirming actual use is a Phase 4
  (LLM extraction) job.

## How this feeds the broader plan

- **Phase 2** (metadata-only analysis) reads `citing_works.jsonl` directly:
  author/institution/country breakdowns, journal/topic mix, temporal
  trend, software co-mentions in abstracts.
- **Phase 3** (full text) uses `doi`, `open_access.oa_url`, and
  `host_venue.pdf_url` to route each paper through Unpaywall /
  Europe PMC / direct download.
- **Phase 4** (LLM extraction) operates on the Phase 3 outputs but joins
  back on `openalex_id` to keep provenance.
