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

## Source and licensing

- **Dataset:** OpenAlex (https://openalex.org), a fully open index of
  scholarly works run by OurResearch (the non-profit behind Unpaywall).
- **Dataset citation:** Priem, J., Piwowar, H., & Orr, R. (2022).
  *OpenAlex: A fully-open index of scholarly works, authors, venues,
  institutions, and concepts.*
  arXiv:[2205.01833](https://arxiv.org/abs/2205.01833).
- **API documentation:** https://docs.openalex.org/api-entities/works
- **Rate-limit and auth policy:**
  https://docs.openalex.org/how-to-use-the-api/rate-limits-and-authentication
- **Data license:** **CC0 1.0 Universal (Public Domain Dedication)**, full
  text at https://creativecommons.org/publicdomain/zero/1.0/. No
  attribution legally required, but please cite the dataset paper above
  when reusing this data in publications.
- **Backing infrastructure:** OpenAlex itself is not open-source software,
  but a monthly bulk data snapshot is published to AWS Open Data
  (`s3://openalex`); we use the live API here because the result set is
  small (~2,500 records, ~90 MB raw JSON).
- **No versioned snapshots from the API.** OpenAlex updates its index
  continuously, so a re-fetch on a different day will return a slightly
  different set as new citing papers are indexed and metadata is
  refined. Treat the snapshot in this directory as authoritative for
  any analysis written into the proposal; cite it by this repo's
  commit SHA.
- **Fetched on 2026-05-24** using httpx 0.28.1, tenacity 9.1.4,
  python-dotenv 1.2.2 (exact pins in `citations/uv.lock`). OpenAlex
  reported `meta.count = 2509`, `cost_usd = 0.0001` per page.

## Exact query

The fetcher walks paginated results from this base query:

```
GET https://api.openalex.org/works
    ?filter=cites:W2131473084
    &per-page=200
    &cursor=*                    (* on first page; opaque next_cursor thereafter)
    &mailto=<your-email>         (polite pool contact)

Authorization: Bearer <OPENALEX_API_KEY>
User-Agent: taxondna-citations/0.0.0 (mailto:<your-email>)
```

`W2131473084` is OpenAlex's stable work ID for the SequenceMatrix paper
(DOI `10.1111/j.1096-0031.2010.00329.x`); confirm at
https://api.openalex.org/works/W2131473084.

Quick smoke test (no Python needed; just a key and `jq`):

```sh
OPENALEX_API_KEY=... OPENALEX_MAILTO=you@example.com
curl -sS \
  -H "Authorization: Bearer $OPENALEX_API_KEY" \
  -H "User-Agent: taxondna-citations/0.0.0 (mailto:$OPENALEX_MAILTO)" \
  "https://api.openalex.org/works?filter=cites:W2131473084&per-page=1&mailto=$OPENALEX_MAILTO" \
  | jq '.meta'
```

That returns `meta.count`, which is the total citing-works count for
W2131473084 *at the moment of the request*. If you see a number that is
materially different from 2509, OpenAlex has re-indexed since our pull
and you should re-fetch end-to-end rather than diff against this
snapshot.

**Cursor pagination warning:** OpenAlex no longer accepts `&page=N` for
result sets larger than 10,000; the supported pattern is to read
`meta.next_cursor` from page N and pass it as `&cursor=...` for page N+1
until `next_cursor` comes back null. Our 2,509-record result fits in
either scheme, but the fetcher uses cursors anyway for forward
compatibility.

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

Run on 2026-05-24. Full SHA-256s for `raw/page_NNNN.json` (verify with
`shasum -a 256 raw/page_*.json`):

```
252eb5342248b211783b407937049ab9ff01a793a74db3a9f21ce3d2d8c430af  raw/page_0001.json
6cdef7a324d79b266ab3b611e1dc833653dd6cc5a6a5b44e3ebc26a588d71fd4  raw/page_0002.json
c5afb4c32b70877701d21b92493fc9bd3b510af4eace5bae9376264d758bb964  raw/page_0003.json
d7c2dfd0b18b65cb64a7c6eeeaddbd4ad0e40d38ae607358d6e547ce4b150f5b  raw/page_0004.json
3967050eb814fab518253a2040f21e3b552e434897263d41e9406822f968ada0  raw/page_0005.json
343aed80d4d627383d12fe7c9c90cb48425a156834a43d2a7028de8ad8a8ef92  raw/page_0006.json
e504f0e9c023aad15b8140bd3a009c628d44e41fbe7f63b90c1c2857634f93d5  raw/page_0007.json
5f17249eb7ed0ad01ab47eaa969504dbc98a09095d0dcb4171afadab088fbe19  raw/page_0008.json
3ccb63fafaae29e58fa1cecbd7c9f18ccb5b780509127c5d97f5ab4ec2219830  raw/page_0009.json
0cad8d58c5144c033203bd8d616af903d50bf224f852446928edd2d7aed66295  raw/page_0010.json
8fb7ba2a9b4ee86cd27ab26d33a40bde4dac017fc9e7028e617eb37f18c9c1a5  raw/page_0011.json
25702aa3c35602391b728e4ffd6aa64b0395f373965afbe55a33982d8e1b3c6e  raw/page_0012.json
71db5a4d4ad03e7ac72afde8dd6cc5eeb95f75e2dc3e554298c82152e741ea10  raw/page_0013.json
```

Because OpenAlex doesn't snapshot, a re-fetch will not byte-equal the
hashes above. They exist so you can detect *unintended* changes to the
checked-in JSON (a botched git operation, an accidental reformat), not
to certify reproducibility against a re-pull from OpenAlex.

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
