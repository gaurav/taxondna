# SequenceMatrix Citation Analysis

Working notes and scripts for [issue #127](https://github.com/gaurav/taxondna/issues/127): figure out how, where, and why the [SequenceMatrix paper](https://doi.org/10.1111/j.1096-0031.2010.00329.x) (Vaidya, Lohman & Meier 2011, *Cladistics*) is being cited, so we can plan future development with evidence rather than guesswork. Findings will also feed a proposal due **2026-06-08**.

> **Status:** plan only. No code yet. See "Open questions" before starting.

## Anchor record

| Field | Value |
| --- | --- |
| DOI | `10.1111/j.1096-0031.2010.00329.x` |
| OpenAlex work ID | `W2131473084` |
| Google Scholar cites | 2,986 (1,740 since 2021) |
| OpenAlex cites | 2,597 |
| Crossref cites | retrieve at runtime |

The gap between Google Scholar and OpenAlex (~390 papers) is the rough size of the "long tail" we'll miss with open APIs. That's acceptable for trend analysis; Google Scholar has no usable API and scraping it is brittle and ToS-violating.

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
- [CZ Software Mentions](https://github.com/chanzuckerberg/software-mentions) — Chan-Zuckerberg-published ML-extracted software mentions across all of PubMed; we may be able to just **look SequenceMatrix up there** and skip a lot of work.
- [SoMeSci](https://data.gesis.org/somesci/) — software mentions knowledge graph with version/developer relations.
- [BioWorkflow](https://academic.oup.com/bib/article/26/6/bbaf571/8315884) — recent (2025) LLM+RAG framework that recovers ~80% of workflow steps from bioinformatics papers; closest to what we want for Phase 4.

**Action item before writing any code:** check whether SequenceMatrix already shows up in the CZ Software Mentions dump. If it does, Phases 3 + 4 collapse to "download their CSV, filter, analyze."

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
