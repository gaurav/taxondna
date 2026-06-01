# SequenceMatrix Citation Analysis

Working notes and scripts for [issue #127](https://github.com/gaurav/taxondna/issues/127): figure out how, where, and why the [SequenceMatrix paper](https://doi.org/10.1111/j.1096-0031.2010.00329.x) (Vaidya, Lohman & Meier 2011, *Cladistics*) is being cited, so we can plan future development with evidence rather than guesswork. Findings will also feed a proposal due **2026-06-08**.

> **Status:** Phase 1 done (OpenAlex pull, 2,509 citing works, 2026-05-24). Prior-art check done (CZ Software Mentions). Phase 2 done — see [`phase2_metadata.md`](phase2_metadata.md) for the proposal-ready digest and [`data/openalex/citing_works.csv`](data/openalex/citing_works.csv) for the flat table. The Phase 2 co-mention scan's tool vocabulary is grounded in CZ co-occurrence data — see [`data/cz_software_mentions/comentioned_software.csv`](data/cz_software_mentions/comentioned_software.csv) (1,672 tools across 402 papers). **Phase 3a done (full-text coverage table, no bytes downloaded) — see [`data/fulltext/coverage.csv`](data/fulltext/coverage.csv); 828 papers (33%) have section-tagged PMC XML, 912 more (36%) have an OA PDF, 760 (30%) are paywalled, 1,740 / 2,509 (69%) reachable for free.**

## Anchor record

| Field | Value |
| --- | --- |
| DOI | `10.1111/j.1096-0031.2010.00329.x` |
| OpenAlex work ID | `W2131473084` |
| Google Scholar cites | 2,986 (1,740 since 2021) |
| OpenAlex cites | 2,509 (fetched 2026-05-24) |
| Crossref cites | retrieve at runtime |

The gap between Google Scholar and OpenAlex (~480 papers, ~16%) is the rough size of the "long tail" we'll miss with open APIs. That's acceptable for trend analysis; Google Scholar has no usable API and scraping it is brittle and ToS-violating.

## Reproducing the analysis end-to-end

Environment setup (one-time):

```sh
cd citations
cp .env.example .env       # fill in OPENALEX_API_KEY (free, openalex.org) and OPENALEX_MAILTO
uv sync                    # installs httpx, tenacity, python-dotenv per uv.lock
```

Pipeline scripts, in dependency order. Each is idempotent — cached upstream responses live on disk and re-runs are cheap. Per-script details (schema, license, source citations, headline numbers from the canonical 2026-05-24/25 run) are in the linked subdir README.

| # | Script | Reads | Writes | See |
| --: | --- | --- | --- | --- |
| 1 | `scripts/fetch_openalex_citing.py` | OpenAlex API (cursor-paginated) | `data/openalex/raw/page_*.json` | [`data/openalex/README.md`](data/openalex/README.md) |
| 2 | `scripts/build_citing_works.py` | `data/openalex/raw/` | `data/openalex/citing_works.jsonl` | same |
| 3 | `scripts/analyze_phase2.py` | `data/openalex/citing_works.jsonl` | `data/openalex/citing_works.csv`, [`phase2_metadata.md`](phase2_metadata.md) | digest is self-documenting |
| 4 | `scripts/extract_cz_comentions.py` | `data/cz_software_mentions/_downloads/{raw,disambiguated}.tar.gz` (browser-downloaded from Dryad doi:10.5061/dryad.6wwpzgn2c), `data/cz_software_mentions/sequencematrix_mentions.csv` | `data/cz_software_mentions/comentioned_{software,per_paper}.csv` | [`data/cz_software_mentions/README.md`](data/cz_software_mentions/README.md) |
| 5 | `scripts/build_fulltext_coverage.py` | `data/openalex/citing_works.jsonl`, Europe PMC API | `data/fulltext/coverage.csv` | [`data/fulltext/README.md`](data/fulltext/README.md) |

Notes:

- **Steps 3 and 4 are independent.** Step 3's curated tool list was originally informed by step 4's output (top CZ co-mentioned tools above a 15-paper threshold), but the resulting `TOOL_PATTERNS` is hand-curated and frozen in `analyze_phase2.py`. You can run step 3 without step 4.
- **Step 4 needs ~3.9 GB of bulk Dryad downloads** that are not checked in. SHA-256s and the download recipe are in the CZ README; the script verifies SHA-256s before extracting.
- **Step 5 is OA-only by policy** — `paywalled` papers are tagged but not pursued. The script trusts OpenAlex's OA fields (sourced from Unpaywall) rather than re-querying Unpaywall directly; the only API actually called is Europe PMC.
- **No bytes of full text are downloaded yet.** Phase 3b (actual PDF / JATS-XML download) is gated on the Phase 4 scope decision and not implemented.

### Visual summary notebook

[`Analysis.ipynb`](Analysis.ipynb) is a read-only companion that loads `data/openalex/citing_works.csv` (step 3 above) and renders the proposal-ready figures: a publication-type breakdown, citations-per-year bar chart, top-10 journals and authors, and the most common two-word phrases in citing-paper titles. It hits no network APIs. The notebook source is generated from [`scripts/build_analysis_notebook.py`](scripts/build_analysis_notebook.py) for diff-friendly edits:

```sh
uv sync --group notebook
uv run --group notebook scripts/build_analysis_notebook.py          # regenerate cells
uv run --group notebook jupyter nbconvert --to notebook --execute --inplace Analysis.ipynb
```

## Prior-art check: CZ Software Mentions (done 2026-05-24)

Before kicking off the OpenAlex pipeline, we checked whether the [CZ Software Mentions dump](https://doi.org/10.5061/dryad.6wwpzgn2c) (Istrate et al. 2022) had already done the hard part. It hadn't, but it gave us a useful by-product. Full extract, method, and CSV in [`citations/data/cz_software_mentions/`](data/cz_software_mentions/README.md).

**Headline:** 457 sentence-level mentions across **372 unique-DOI papers** explicitly cite SequenceMatrix in the CZ corpora.

**Why this isn't our answer:**
- 372 papers is only **~15% of OpenAlex's 2,509 citing works** (confirmed by the Phase 1 pull). The other ~85% live in taxonomy/zoology journals outside PMC and outside CZI's publisher agreements (*Cladistics* itself, *Zootaxa*, *Insect Systematics*, *Systematic Entomology*, etc.).
- More importantly, **the CZ corpus collection cutoff is October 2021**, and we now know from OpenAlex that **1,114 papers (44% of all citations) were published in 2022 or later** — exactly the window the proposal needs to speak to, and exactly the window CZ cannot see. See the side-by-side comparison in "Phase 1 results" below.

**Why it's still worth what we spent on it:**
1. **Practice corpus / pilot set for Phase 4.** Every CZ row already carries DOI, exact mention sentence, section name, and (for 370 of 372 PMC-indexed papers) the full text is freely available as section-tagged XML from Europe PMC. That's a perfect place to develop and tune the LLM extraction prompts before pointing them at the full OpenAlex corpus — no PDF scraping, no GROBID, no rate-limit headaches.
2. **Fallback if OpenAlex stalls.** If the Phase 1 pipeline runs into API-key trouble, rate-limit walls, or schema issues we can't quickly debug, the CZ subset alone is enough to produce a first-pass usage analysis for the proposal. It just won't reflect anything published after October 2021.
3. **Reference architecture for the spin-off tool.** The CZ dump is roughly what a generic "software-citation analyzer" looks like at scale; the fact that it stops in 2021 and excludes most taxonomy journals is precisely the gap a follow-on tool could fill.

## Phase 1 results: OpenAlex citing-works pull (done 2026-05-24)

**2,509 unique citing works** retrieved from OpenAlex on 2026-05-24, spanning 2010–2026. Raw API pages, flattened JSONL, full methods, and the reproduction recipe are in [`citations/data/openalex/`](data/openalex/README.md).

**Five-year coverage check (the answer the proposal needs):** **1,410 of 2,509 works (56%) were published in 2021 or later** — 1,339 across the five complete years 2021–2025, plus 71 so far in partial 2026. OpenAlex sees the proposal-relevant window clearly; we have evidence, not extrapolation, for any "still in active use" claim.

### Per-year citation counts: OpenAlex vs CZ Software Mentions

CZ counts are deduped by DOI (or PMCID where DOI is missing) so the columns compare apples-to-apples papers, not mention sentences. "CZ recall" is `CZ papers / OpenAlex citing works` for the same year — a rough proxy for the fraction of citing literature that PMC and CZI's publishers' collection together cover.

| Year | OpenAlex citing works | CZ papers (deduped) | CZ recall |
| ---: | ---: | ---: | ---: |
| 2010 | 2   | 1  | 50%  |
| 2011 | 3   | 1  | 33%  |
| 2012 | 13  | 7  | 54%  |
| 2013 | 29  | 5  | 17%  |
| 2014 | 51  | 11 | 22%  |
| 2015 | 71  | 18 | 25%  |
| 2016 | 94  | 24 | 26%  |
| 2017 | 156 | 39 | 25%  |
| 2018 | 177 | 43 | 24%  |
| 2019 | 244 | 76 | 31%  |
| 2020 | 259 | 81 | 31%  |
| 2021 | 296 | 66 | 22% *(CZ ends Oct)* |
| 2022 | 267 | —  | 0% *(CZ cutoff)* |
| 2023 | 228 | —  | 0%  |
| 2024 | 283 | —  | 0%  |
| 2025 | 265 | —  | 0%  |
| 2026 | 71 *(partial)* | — | 0% |
| **total** | **2,509** | **372** | **~15%** overall |

Takeaways:
- **CZ catches roughly a quarter to a third** of OpenAlex-known papers in pre-cutoff years. The shortfall is driven by taxonomy/zoology journals not in PMC and not in CZI's publisher agreements (*Cladistics*, *Zootaxa*, *Systematic Entomology*, etc.), not by NER misses.
- **CZ catches zero of the 1,114 papers published since 2022.** That is the entire proposal-relevant window.
- **The OpenAlex trend is steady at 250–300 citations/year through 2022–2025**, with no decline 15 years post-publication. This is the headline numeric claim to feature in the proposal.

### Other metadata on the corpus

- **70% open access** in some form: gold 955, green 306, diamond 183, bronze 175, hybrid 133 = 1,752 papers. The remaining 757 (30%) are closed. Phase 3a (below) confirms this empirically — 69.4% of the corpus is reachable via Europe PMC + OpenAlex/Unpaywall OA URLs.
- **Composition:** 2,264 articles + 112 preprints + 38 dissertations + 13 reviews = >97% of the corpus; the remainder is peer-review records, datasets, errata, etc.
- **9 records (~0.4%) have no DOI**, mostly grey literature and dissertations; these are kept in the JSONL with `doi: null` but won't be reachable in Phase 3.
- **Recall vs Google Scholar:** GS reports 2,986; we have 2,509. The ~480-paper gap is the long tail of preprint quirks, theses, conference proceedings, and non-English regional journals that don't index in OpenAlex. For top-line claims and trend analysis this is fine.

## Phase 3a results: full-text coverage table (done 2026-05-25)

We ran every paper's DOI through Europe PMC and combined the result with the OA URLs OpenAlex already had. **No bytes were downloaded** — this is a coverage check, not a download. Full method, schema, and per-year table in [`data/fulltext/README.md`](data/fulltext/README.md).

| Strategy | Papers | % of corpus | What it means for Phase 4 |
| --- | ---: | ---: | --- |
| `pmc_xml` | **828** | **33.0%** | Section-tagged JATS XML in Europe PMC. No GROBID step. Best LLM input. |
| `oa_pdf` | **912** | **36.3%** | OA PDF reachable from OpenAlex/Unpaywall's `oa_url`. Needs GROBID to recover sections. |
| `paywalled` | 760 | 30.3% | Skipped per OA-only policy. |
| `no_doi` | 9 | 0.4% | Not reachable via DOI-keyed APIs. |
| **Reachable for free** | **1,740** | **69.4%** | |

Takeaways:
- **One in three citing papers ships with section-tagged JATS XML for free.** That's the cleanest Phase 4 input we can hope for — Methods/Results/Discussion already delimited.
- **Coverage is stable at 70%+ for 2021–2026** — exactly the window the 2026-06-08 proposal cares about, and Phase 4 is not bandwidth-limited.
- **OpenAlex's OA share (70%) matches our reachable count (69.4%) almost exactly** — OpenAlex's snapshot of Unpaywall is accurate, and querying Unpaywall directly would not have added meaningful coverage.

## Phased plan

### Phase 1 — Discover citing works and harvest metadata

**Goal:** one row per citing paper, with the richest metadata we can get for free.

- Pull citing works from **OpenAlex** with the filter `cites:W2131473084`. Free, paginated, JSON. Use the cursor pagination (`cursor=*`) — there will be ~2,600 records.
- For each work, OpenAlex gives DOI, title, abstract (inverted index), publication year, type, host venue, open-access status, authors (with ORCID where available), institutions (with [ROR](https://ror.org) IDs and country codes), concepts/topics, and cited_by_count of the citing work itself.
- Cross-check coverage against **Semantic Scholar** (`/graph/v1/paper/DOI:.../citations`). Some records appear only in one or the other; merging by DOI gives the best recall.
- Enrich missing fields from **Crossref** (`/works/{DOI}`) — useful for funder info and reference lists.
- Save raw JSON responses verbatim so re-runs don't re-hit the APIs.

**Output:** `citations/data/citing_works.jsonl` (one record per citing paper, with `sources: [openalex, semanticscholar, crossref]` showing where each field came from).

**Tools:** [PyAlex](https://github.com/J535D165/pyalex), `requests`, or [openalexR](https://docs.ropensci.org/openalexR/) if we prefer R. OpenAlex will require a (free) API key starting 2026-02-13 — get one and put it in `.env`.

### Phase 2 — Metadata-only analysis

**Goal:** answer the questions we can answer without ever reading a paper.

- Authors and affiliations: top contributors, country distribution, ROR-normalized organizations. ROR IDs make org normalization trivial — much better than the free-text affiliation strings in Google Scholar.
- Temporal trend: citations per year, projected through 2026.
- Venue analysis: top journals, OA share, journal-level subject categories.
- Topic mix from OpenAlex concepts/topics ("Phylogenetics", "Insect taxonomy", etc.) — these are noisy but useful as a first pass.
- Title/keyword n-grams as a rough proxy for what kinds of studies use SM.
- Software co-mention: scan abstracts (and later, methods) for other tool names — MEGA, MrBayes, RAxML, IQ-TREE, BEAST, Geneious, Mesquite. Tells us where SM sits in the typical workflow.

**Outputs:**
- `citations/data/citing_works.csv` — flat, OpenRefine-friendly (one row per paper, multi-valued fields semicolon-joined).
- `citations/notebooks/phase2_metadata.ipynb` — plots and tables.
- A short markdown digest that can paste straight into the proposal.

Phase 1 + Phase 2 alone are probably enough to anchor the proposal. The remaining phases sharpen the picture but aren't blocking.

### Phase 3 — Acquire full text where possible

**Goal:** know exactly how big the "we can read the paper" subset is, and have those texts on disk.

- Run all DOIs through the **Unpaywall** API to get OA PDF URLs (free, up to 100k DOIs/day, or use their database snapshot). This tells us OA-vs-not for every paper.
- Where a paper is in **PubMed Central**, prefer the [Europe PMC](https://europepmc.org/RestfulWebService) full-text **XML** over a PDF — already section-tagged (Methods, Discussion), no GROBID step needed, and the licence permits text-mining.
- For OA PDFs, download via Unpaywall's resolved URLs. Use [paperscraper](https://github.com/jannisborn/paperscraper) as a fallback for publishers with quirky landing pages.
- Track coverage in a table: total / DOI-matched / OA PDF / PMC XML / paywalled. Report this honestly — it will be a significant fraction.
- **Don't** try to fetch paywalled PDFs from Sci-Hub-style sources for a public deliverable.

**Outputs:**
- `citations/data/pdfs/{doi-slug}.pdf`
- `citations/data/pmc_xml/{pmcid}.xml`
- `citations/data/fulltext_coverage.csv`

### Phase 4 — LLM-based extraction of how SM was used

**Goal:** structured answers to the questions in #127 that need full text.

For each paper with full text:
1. If PMC XML, slice out Methods / Results / Discussion sections directly. If PDF, run [**GROBID**](https://grobid.readthedocs.io/) (Docker, one-shot) to produce TEI XML, then slice the same sections.
2. Cheap filter pass with **Claude Haiku 4.5**: "Does this paper actually use SequenceMatrix (vs just citing the paper)?" — many citations will be background/citation-padding. Drop the negatives. Use prompt caching since the question is constant.
3. Structured extraction pass with **Claude Sonnet 4.6** on the survivors, with a JSON schema along the lines of:
   ```
   {
     "used_sequencematrix": bool,
     "purpose": str,                   // one-line description
     "genes_or_markers": [str],
     "n_taxa": int | null,
     "n_genes": int | null,
     "n_characters": int | null,
     "downstream_software": [str],     // RAxML, MrBayes, etc.
     "complaints_or_limitations": [str],
     "evidence_quotes": [str]          // verbatim spans we extracted from
   }
   ```
4. Spot-check 20–30 extractions by hand against the source to estimate precision.

**Outputs:**
- `citations/data/extractions.jsonl`
- `citations/notebooks/phase4_summary.ipynb` — histograms of dataset size, common purposes, complaints word-cloud or sorted list.

**Cost sanity check:** ~2,600 papers × ~5K input tokens (Methods+Discussion) × Sonnet pricing is a real but manageable number; the Haiku filter should knock the survivor set well below 1,000 before Sonnet runs. Anthropic prompt caching makes the per-call system prompt effectively free after the first hit.

### Phase 5 — Report

- `citations/REPORT.md` — narrative report aimed at the proposal, with embedded tables and figures, every claim linked to the underlying CSV/JSONL row(s) in this repo.
- Headline numbers: total citing works analyzed, OA share, top-10 use cases, dataset-size distribution, top complaints, geographic and institutional spread.
- Reproducibility appendix: exact API queries, model versions, prompts used.

## Existing tools we should evaluate before building anything

Discovery / metadata:
- **OpenAlex** + [PyAlex](https://github.com/J535D165/pyalex) — primary source.
- [Semantic Scholar API](https://api.semanticscholar.org/) — secondary, good citation context snippets.
- [Crossref REST API](https://api.crossref.org) — gap filling and funder info.
- [Findpapers](https://github.com/jonatasgrosman/findpapers) — multi-DB aggregator, may save us writing glue.

Full text:
- [Unpaywall](https://unpaywall.org/products/api) — OA PDF resolution.
- [Europe PMC](https://europepmc.org/RestfulWebService) — section-tagged XML, no PDF parsing required.
- [paperscraper](https://github.com/jannisborn/paperscraper) — PDF download with publisher quirks handled.
- [GROBID](https://grobid.readthedocs.io/) — PDF → TEI XML.

Software-mention extraction (in case we don't have to build the LLM bits from scratch):
- [Softcite dataset and tool](https://github.com/softcite) — gold-standard corpus + a rule-based extractor that still beats most LLMs on F1.
- [CZ Software Mentions](https://github.com/chanzuckerberg/software-mentions) — Chan-Zuckerberg-published ML-extracted software mentions across PubMed + a CZI publishers' collection. **Already checked**, see the "Prior-art check" section above; we have 372 papers' worth of mentions in `citations/data/cz_software_mentions/` but the Oct 2021 cutoff means we still need OpenAlex for the recent literature.
- [SoMeSci](https://data.gesis.org/somesci/) — software mentions knowledge graph with version/developer relations.
- [BioWorkflow](https://academic.oup.com/bib/article/26/6/bbaf571/8315884) — recent (2025) LLM+RAG framework that recovers ~80% of workflow steps from bioinformatics papers; closest to what we want for Phase 4.

~~**Action item before writing any code:** check whether SequenceMatrix already shows up in the CZ Software Mentions dump.~~ Done — it does (372 papers), but the Oct 2021 cutoff means it doesn't replace Phases 1 + 3. It does give us a free Phase 4 pilot corpus.

## Suggestions

- **Ship Phase 1+2 first**, end-to-end, on the full corpus, within a few days. That alone produces proposal-grade numbers. Treat Phases 3–5 as upside.
- **Cache aggressively.** Save every API response and every LLM response to disk keyed by request hash. Iteration on prompts shouldn't re-bill.
- **Pilot Phase 4 on a 50-paper random sample** before running on the full OA set. Tune the prompt and schema against the sample, then scale.
- **Two-tier LLM**: Haiku to filter, Sonnet to extract. Prompt-cache the system prompt.
- **Write the OpenRefine recipe** for org/affiliation cleanup as you go; without it, "top organizations" will double-count "Smithsonian Institution" vs "Smithsonian Inst.".
- **Pre-register the report skeleton** in `REPORT.md` before extraction starts. Stops scope creep.
- Stay in this repo for now (`citations/`). If this looks like it has legs beyond SM, fork it into its own repo *after* the proposal lands — a generic `software-citation-analyzer` could plausibly be funded on its own.

## Open questions for Gaurav

1. **Language / runtime.** Python with `uv` or `poetry`, or stay closer to the Java setup of this repo? Recommend Python.
2. **LLM provider.** Claude via Anthropic API (Sonnet 4.6 + Haiku 4.5)? Local model? Hard budget cap?
3. **Scope of "downloadable."** OA only (Unpaywall + PMC), or also use a personal institutional proxy for closed-access? OA-only keeps the tool publishable.
4. **Sample vs. full corpus for Phase 4.** Run extraction on every OA paper, or on a representative stratified sample? Sample is cheaper and may be enough for the proposal.
5. **Spin-off timing.** Keep all phases in `taxondna/citations/` through the proposal deadline, then split? Or split now if we already see this becoming its own thing?
6. **Deliverable shape.** Markdown report, slide deck, both? Audience for the proposal — funder type matters for tone.
