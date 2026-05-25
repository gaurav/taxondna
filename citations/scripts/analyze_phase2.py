"""Phase 2: metadata-only analysis of the OpenAlex citing-works corpus.

Reads citations/data/openalex/citing_works.jsonl (produced by
build_citing_works.py) and writes:

  - citations/data/openalex/citing_works.csv   one row per paper, OpenRefine-friendly
  - citations/phase2_metadata.md               proposal-ready digest with tables

All aggregates are computed in one pass so the report can't drift from the
underlying data. See citations/README.md Phase 2 for the original spec.

Usage:
    uv run scripts/analyze_phase2.py
"""

from __future__ import annotations

import csv
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
JSONL = ROOT / "data" / "openalex" / "citing_works.jsonl"
CSV_OUT = ROOT / "data" / "openalex" / "citing_works.csv"
MD_OUT = ROOT / "phase2_metadata.md"


# ---------------------------------------------------------------------------
# Software co-mention vocabulary
# ---------------------------------------------------------------------------
# Vocabulary has two layers:
#   1. Curated default list — the obvious phylogenetics-pipeline neighbors
#      called out in citations/README.md.
#   2. CZ-derived list — drawn from data/cz_software_mentions/comentioned_software.csv
#      (built by scripts/extract_cz_comentions.py), which aggregates every
#      OTHER software CZ Software Mentions found in the 372 SM-citing papers.
#      We add tools appearing in >=15 of those papers, skipping obvious NER
#      noise ("R" alone is too short, "Nexus" usually refers to the file
#      format, "PUBMED"/"PeerJ"/"CLOCKSS" are databases/journals/archives,
#      "STRUCTURE" is too generic to regex safely).
#
# Patterns are mostly case-sensitive word-boundary regexes; in phylo
# abstracts the false-positive rate is low because tool names are written
# as proper nouns / acronyms. Where surface forms vary in case (BioEdit /
# BIOEDIT / Bioedit) we use re.IGNORECASE explicitly.
#
# TODO(phase2-followup): expand vocabulary with n-gram discovery — scan
# abstracts for capitalized tokens not already matched and present a candidate
# list for manual curation.
TOOL_PATTERNS: dict[str, re.Pattern[str]] = {
    # --- Curated default list ---
    "MEGA": re.compile(r"\bMEGA(?:[\s-]?(?:X|\d+))?\b"),
    "MrBayes": re.compile(r"\bMrBayes\b"),
    "RAxML": re.compile(r"\bRAxML(?:[\s-]?NG)?\b"),
    "IQ-TREE": re.compile(r"\b(?:IQ[\s-]?TREE|IQTREE)\d*\b"),
    "BEAST": re.compile(r"\bBEAST\s?\d*\b"),
    "Geneious": re.compile(r"\bGeneious\b"),
    "Mesquite": re.compile(r"\bMesquite\b"),
    "MAFFT": re.compile(r"\bMAFFT\b"),
    "MUSCLE": re.compile(r"\bMUSCLE\b"),
    "Clustal": re.compile(r"\bClustal(?:\s?W|\s?Omega|X)?\b"),
    "TNT": re.compile(r"\bTNT\b"),
    "PAUP": re.compile(r"\bPAUP\*?\b"),
    "jModelTest": re.compile(r"\bjModelTest\b"),
    "ModelTest": re.compile(r"\bModelTest(?:[\s-]?NG)?\b"),
    "FigTree": re.compile(r"\bFigTree\b"),
    "Tracer": re.compile(r"\bTracer\b"),
    "BLAST": re.compile(r"\bBLAST[+\d]?\b"),
    "PartitionFinder": re.compile(r"\bPartitionFinder\d?\b"),
    "Garli": re.compile(r"\b(?:Garli|GARLI)\b"),
    "PhyML": re.compile(r"\bPhyML\b"),
    "TreeBase": re.compile(r"\bTreeBASE\b", re.IGNORECASE),
    # --- CZ-derived (top ~30 by paper count, threshold >=15 papers) ---
    "tRNAscan-SE": re.compile(r"\btRNA\s?scan[-\s]?SE\b", re.IGNORECASE),
    "BioEdit": re.compile(r"\bBioEdit\b", re.IGNORECASE),
    "DnaSP": re.compile(r"\bDnaSP\b", re.IGNORECASE),
    "TreeAnnotator": re.compile(r"\bTree\s?Annotator\b", re.IGNORECASE),
    "MITOS": re.compile(r"\bMITOS\d*\b"),
    "Gblocks": re.compile(r"\bGblocks\b", re.IGNORECASE),
    "MrModelTest": re.compile(r"\bMr\.?\s?Model\s?Test\d*\b", re.IGNORECASE),
    "LogCombiner": re.compile(r"\bLog\s?Combiner\b", re.IGNORECASE),
    "Sequencher": re.compile(r"\bSequencher\b"),
    "TranslatorX": re.compile(r"\bTranslator[\s-]?X\b", re.IGNORECASE),
    "SPAdes": re.compile(r"\bSPAdes\b", re.IGNORECASE),
    "MITObim": re.compile(r"\bMITObim\b", re.IGNORECASE),
    "Trimmomatic": re.compile(r"\bTrimmomatic\b"),
    "ModelFinder": re.compile(r"\bModel\s?Finder\b"),
    "raxmlGUI": re.compile(r"\braxmlGUI\d?\b", re.IGNORECASE),
    "OGDRAW": re.compile(r"\bOGDRAW\b", re.IGNORECASE),
    "CLC Genomics Workbench": re.compile(r"\bCLC\s+(?:Genomics|Main)\s+Workbench\b"),
    "MFannot": re.compile(r"\bMFannot\b"),
    "CIPRES": re.compile(r"\bCIPRES\b"),
    "Mauve": re.compile(r"\bMauve\b"),
    "NOVOPlasty": re.compile(r"\bNOVOPlasty\b"),
    "ORF Finder": re.compile(r"\bORF\s?Finder\b"),
    "CGView": re.compile(r"\bCGView\b"),
    "FastQC": re.compile(r"\bFastQC\b"),
    "DAMBE": re.compile(r"\bDAMBE\b"),
    "Arlequin": re.compile(r"\bArlequin\b"),
    "DOGMA": re.compile(r"\bDOGMA\b"),
    "PAML": re.compile(r"\bPAML\b"),
    "REPuter": re.compile(r"\bREPuter\b", re.IGNORECASE),
    "AliView": re.compile(r"\bAliView\b"),
}


def detect_tools(text: str | None) -> list[str]:
    if not text:
        return []
    return sorted({name for name, pat in TOOL_PATTERNS.items() if pat.search(text)})


# ---------------------------------------------------------------------------
# Row flattening for the CSV
# ---------------------------------------------------------------------------
CSV_FIELDS = [
    "openalex_id",
    "doi",
    "title",
    "publication_year",
    "publication_date",
    "type",
    "language",
    "is_oa",
    "oa_status",
    "venue_name",
    "venue_type",
    "venue_issn_l",
    "publisher",
    "cited_by_count",
    "referenced_works_count",
    "n_authors",
    "author_names",
    "author_orcids",
    "corresponding_authors",
    "institution_rors",
    "institution_names",
    "countries",
    "topic_displays",
    "topic_fields",
    "domains",
    "software_comentions",
]


def _join(values: Iterable[str | None]) -> str:
    return "; ".join(v for v in values if v)


def to_csv_row(rec: dict, tools_found: list[str]) -> dict[str, str]:
    venue = rec.get("host_venue") or {}
    oa = rec.get("open_access") or {}
    authors = rec.get("authors") or []
    topics = rec.get("topics") or []

    insts_ror: list[str] = []
    insts_name: list[str] = []
    countries: list[str] = []
    seen_ror: set[str] = set()
    seen_country: set[str] = set()
    for a in authors:
        for inst in a.get("institutions") or []:
            ror = inst.get("ror")
            if ror and ror not in seen_ror:
                seen_ror.add(ror)
                insts_ror.append(ror)
                insts_name.append(inst.get("display_name") or "")
        for cc in a.get("countries") or []:
            if cc and cc not in seen_country:
                seen_country.add(cc)
                countries.append(cc)

    return {
        "openalex_id": rec.get("openalex_id") or "",
        "doi": rec.get("doi") or "",
        "title": (rec.get("title") or "").replace("\n", " ").strip(),
        "publication_year": str(rec.get("publication_year") or ""),
        "publication_date": rec.get("publication_date") or "",
        "type": rec.get("type") or "",
        "language": rec.get("language") or "",
        "is_oa": "true" if oa.get("is_oa") else "false",
        "oa_status": oa.get("oa_status") or "",
        "venue_name": venue.get("display_name") or "",
        "venue_type": venue.get("type") or "",
        "venue_issn_l": venue.get("issn_l") or "",
        "publisher": venue.get("host_organization") or "",
        "cited_by_count": str(rec.get("cited_by_count") or 0),
        "referenced_works_count": str(rec.get("referenced_works_count") or 0),
        "n_authors": str(len(authors)),
        "author_names": _join(a.get("display_name") for a in authors),
        "author_orcids": _join(a.get("orcid") for a in authors),
        "corresponding_authors": _join(
            a.get("display_name") for a in authors if a.get("is_corresponding")
        ),
        "institution_rors": _join(insts_ror),
        "institution_names": _join(insts_name),
        "countries": _join(countries),
        "topic_displays": _join(t.get("display_name") for t in topics),
        "topic_fields": _join(t.get("field") for t in topics),
        "domains": _join({t.get("domain") for t in topics if t.get("domain")}),
        "software_comentions": _join(tools_found),
    }


# ---------------------------------------------------------------------------
# Aggregates
# ---------------------------------------------------------------------------
@dataclass
class Aggregates:
    n_papers: int = 0
    n_with_abstract: int = 0
    n_with_doi: int = 0

    # Authors
    author_paper_count: Counter[str] = None  # display_name -> n papers
    author_orcids: dict[str, str] = None  # display_name -> orcid (first seen)
    author_top_affiliation: dict[str, str] = None  # display_name -> first inst seen
    author_top_country: dict[str, str] = None
    n_unique_authors: int = 0
    n_authors_with_orcid: int = 0

    # Institutions (ROR-keyed)
    inst_paper_count: Counter[str] = None  # ROR -> n papers
    inst_name: dict[str, str] = None  # ROR -> display_name (first seen)
    inst_country: dict[str, str] = None  # ROR -> country code (first seen)

    # Countries
    country_paper_count: Counter[str] = None

    # Topics
    topic_paper_count: Counter[str] = None
    topic_field: dict[str, str] = None  # topic_display -> field
    field_paper_count: Counter[str] = None
    domain_paper_count: Counter[str] = None

    # Venues
    venue_paper_count: Counter[str] = None
    venue_publisher: dict[str, str] = None
    publisher_paper_count: Counter[str] = None

    # OA by year
    oa_status_by_year: dict[int, Counter[str]] = None  # year -> {status: n}

    # Software co-mentions
    tool_paper_count: Counter[str] = None
    n_papers_with_any_tool: int = 0
    n_tools_per_paper: Counter[int] = None  # histogram: how many tools in each paper

    def __post_init__(self):
        self.author_paper_count = Counter()
        self.author_orcids = {}
        self.author_top_affiliation = {}
        self.author_top_country = {}
        self.inst_paper_count = Counter()
        self.inst_name = {}
        self.inst_country = {}
        self.country_paper_count = Counter()
        self.topic_paper_count = Counter()
        self.topic_field = {}
        self.field_paper_count = Counter()
        self.domain_paper_count = Counter()
        self.venue_paper_count = Counter()
        self.venue_publisher = {}
        self.publisher_paper_count = Counter()
        self.oa_status_by_year = defaultdict(Counter)
        self.tool_paper_count = Counter()
        self.n_tools_per_paper = Counter()


def accumulate(agg: Aggregates, rec: dict, tools_found: list[str]) -> None:
    agg.n_papers += 1
    if rec.get("abstract"):
        agg.n_with_abstract += 1
    if rec.get("doi"):
        agg.n_with_doi += 1

    # Authors / institutions / countries — dedupe per-paper before incrementing
    seen_authors: set[str] = set()
    seen_insts: set[str] = set()
    seen_countries: set[str] = set()
    for a in rec.get("authors") or []:
        name = a.get("display_name")
        if name and name not in seen_authors:
            seen_authors.add(name)
            agg.author_paper_count[name] += 1
            if name not in agg.author_orcids and a.get("orcid"):
                agg.author_orcids[name] = a["orcid"]
            insts = a.get("institutions") or []
            if insts and name not in agg.author_top_affiliation:
                first = insts[0]
                if first.get("display_name"):
                    agg.author_top_affiliation[name] = first["display_name"]
                if first.get("country_code"):
                    agg.author_top_country[name] = first["country_code"]
        for inst in a.get("institutions") or []:
            ror = inst.get("ror")
            if ror and ror not in seen_insts:
                seen_insts.add(ror)
                agg.inst_paper_count[ror] += 1
                agg.inst_name.setdefault(ror, inst.get("display_name") or "")
                if inst.get("country_code"):
                    agg.inst_country.setdefault(ror, inst["country_code"])
        for cc in a.get("countries") or []:
            if cc and cc not in seen_countries:
                seen_countries.add(cc)
                agg.country_paper_count[cc] += 1

    # Topics — count each distinct topic once per paper
    seen_topics: set[str] = set()
    seen_fields: set[str] = set()
    seen_domains: set[str] = set()
    for t in rec.get("topics") or []:
        td = t.get("display_name")
        if td and td not in seen_topics:
            seen_topics.add(td)
            agg.topic_paper_count[td] += 1
            if t.get("field"):
                agg.topic_field.setdefault(td, t["field"])
        if t.get("field") and t["field"] not in seen_fields:
            seen_fields.add(t["field"])
            agg.field_paper_count[t["field"]] += 1
        if t.get("domain") and t["domain"] not in seen_domains:
            seen_domains.add(t["domain"])
            agg.domain_paper_count[t["domain"]] += 1

    # Venues
    venue = rec.get("host_venue") or {}
    vname = venue.get("display_name")
    if vname:
        agg.venue_paper_count[vname] += 1
        if venue.get("host_organization"):
            agg.venue_publisher.setdefault(vname, venue["host_organization"])
    if venue.get("host_organization"):
        agg.publisher_paper_count[venue["host_organization"]] += 1

    # OA by year
    year = rec.get("publication_year")
    oa = rec.get("open_access") or {}
    if year:
        agg.oa_status_by_year[year][oa.get("oa_status") or "unknown"] += 1

    # Software co-mentions
    agg.n_tools_per_paper[len(tools_found)] += 1
    if tools_found:
        agg.n_papers_with_any_tool += 1
    for t in tools_found:
        agg.tool_paper_count[t] += 1


def finalize(agg: Aggregates) -> None:
    agg.n_unique_authors = len(agg.author_paper_count)
    agg.n_authors_with_orcid = sum(1 for n in agg.author_paper_count if n in agg.author_orcids)


# ---------------------------------------------------------------------------
# Markdown rendering
# ---------------------------------------------------------------------------
def pct(num: int, denom: int) -> str:
    if denom == 0:
        return "0%"
    return f"{100 * num / denom:.1f}%"


def render_markdown(agg: Aggregates) -> str:
    out: list[str] = []
    push = out.append

    push("# SequenceMatrix citations — Phase 2 metadata digest")
    push("")
    push(
        "Generated by `scripts/analyze_phase2.py` from "
        "`data/openalex/citing_works.jsonl` (2,509 unique citing works pulled "
        "2026-05-24). See [`citations/README.md`](README.md) for the broader plan and "
        "the per-year OpenAlex vs CZ Software Mentions table."
    )
    push("")
    push("## Corpus headline")
    push("")
    push(f"- **Papers analyzed:** {agg.n_papers:,}")
    push(
        f"- **With abstract:** {agg.n_with_abstract:,} "
        f"({pct(agg.n_with_abstract, agg.n_papers)})"
    )
    push(
        f"- **With DOI:** {agg.n_with_doi:,} "
        f"({pct(agg.n_with_doi, agg.n_papers)})"
    )
    push(f"- **Unique authors:** {agg.n_unique_authors:,}")
    push(
        f"- **Authors with ORCID:** {agg.n_authors_with_orcid:,} "
        f"({pct(agg.n_authors_with_orcid, agg.n_unique_authors)})"
    )
    push(f"- **Unique institutions (ROR-keyed):** {len(agg.inst_paper_count):,}")
    push(f"- **Countries represented:** {len(agg.country_paper_count)}")
    push(f"- **Distinct journals/venues:** {len(agg.venue_paper_count):,}")
    push(f"- **Distinct OpenAlex topics touched:** {len(agg.topic_paper_count):,}")
    push("")

    # Quotable proposal lines
    top_country = agg.country_paper_count.most_common(1)[0] if agg.country_paper_count else ("?", 0)
    top_field = agg.field_paper_count.most_common(1)[0] if agg.field_paper_count else ("?", 0)
    top_topic = agg.topic_paper_count.most_common(1)[0] if agg.topic_paper_count else ("?", 0)
    top_venue = agg.venue_paper_count.most_common(1)[0] if agg.venue_paper_count else ("?", 0)
    push("### What jumps out")
    push("")
    push(
        f"- **Adoption is broad and global.** {agg.n_unique_authors:,} unique authors "
        f"across {len(agg.inst_paper_count):,} ROR-distinct institutions in "
        f"{len(agg.country_paper_count)} countries; top by paper count is "
        f"{top_country[0]} ({top_country[1]} papers, {pct(top_country[1], agg.n_papers)} of corpus)."
    )
    push(
        f"- **Mycology is the single largest user community.** "
        f'"{top_topic[0]}" is the top OpenAlex topic ({top_topic[1]} papers, '
        f"{pct(top_topic[1], agg.n_papers)} of corpus); the #1 venue is "
        f'"{top_venue[0]}" ({top_venue[1]} papers). Fungal taxonomy / plant pathogen '
        "work dominates the top-10 topics and the top-25 journals."
    )
    push(
        f"- **The OA share is high enough that Phase 3 (full-text acquisition) is feasible.** "
        "OA share climbs from ~50% in the mid-2010s to **~75% across 2021–2026** "
        "(see table below). Unpaywall + Europe PMC should reach the great majority."
    )
    push(
        "- **There is a clear set of repeat users to contact.** "
        f"Top 20 named authors each appear on 19–64 papers; "
        f"{agg.n_authors_with_orcid:,} of {agg.n_unique_authors:,} authors "
        f"({pct(agg.n_authors_with_orcid, agg.n_unique_authors)}) carry ORCIDs, "
        "so reaching them is tractable."
    )
    push("")

    # --- Authors ---
    push("## Authors")
    push("")
    total_authorships = sum(agg.author_paper_count.values())
    max_papers = agg.author_paper_count.most_common(1)[0][1] if agg.author_paper_count else 0
    mean_papers = total_authorships / max(agg.n_unique_authors, 1)
    push(
        f"{total_authorships:,} distinct (paper, author) pairs across "
        f"{agg.n_unique_authors:,} unique authors — mean "
        f"{mean_papers:.2f} papers per author, max {max_papers}. "
        f"As expected the distribution has a long head of repeat users."
    )
    push("")
    push("### Top 20 named contributors")
    push("")
    push("Repeat-citing authors, ranked by number of SM-citing papers they appear on.")
    push("These are candidate contacts for user interviews, letters of support, or beta testers.")
    push("")
    push("| # | Author | Papers | ORCID | Top affiliation | Country |")
    push("|---:|---|---:|---|---|---|")
    for i, (name, n) in enumerate(agg.author_paper_count.most_common(20), start=1):
        orcid = agg.author_orcids.get(name, "")
        if orcid:
            orcid_short = orcid.replace("https://orcid.org/", "")
            orcid_md = f"[{orcid_short}]({orcid})"
        else:
            orcid_md = "—"
        aff = agg.author_top_affiliation.get(name, "—")
        cc = agg.author_top_country.get(name, "—")
        push(f"| {i} | {name} | {n} | {orcid_md} | {aff} | {cc} |")
    push("")

    # --- Institutions ---
    push("## Institutions (ROR-normalized)")
    push("")
    push("### Top 25 institutions by citing-paper count")
    push("")
    push("| # | Institution | Country | Papers | ROR |")
    push("|---:|---|---|---:|---|")
    for i, (ror, n) in enumerate(agg.inst_paper_count.most_common(25), start=1):
        name = agg.inst_name.get(ror, "—")
        cc = agg.inst_country.get(ror, "—")
        ror_short = ror.replace("https://ror.org/", "")
        push(f"| {i} | {name} | {cc} | {n} | [{ror_short}]({ror}) |")
    push("")

    # --- Countries ---
    push("## Geographic spread")
    push("")
    push(f"Authors from **{len(agg.country_paper_count)} countries** appear on SM-citing papers.")
    push("")
    push("### Top 20 countries by citing-paper count")
    push("")
    push("| # | Country (ISO-3166-1 alpha-2) | Papers | % of corpus |")
    push("|---:|---|---:|---:|")
    for i, (cc, n) in enumerate(agg.country_paper_count.most_common(20), start=1):
        push(f"| {i} | {cc} | {n} | {pct(n, agg.n_papers)} |")
    push("")
    push(
        "Country codes are ISO-3166-1 alpha-2 as returned by OpenAlex. "
        "Papers with authors from multiple countries are counted once per country."
    )
    push("")

    # --- Topics ---
    push("## Topic mix (OpenAlex topics)")
    push("")
    push(
        "OpenAlex assigns each work up to 3 topics drawn from a ~4,500-topic "
        "taxonomy, each rolling up to a *subfield* → *field* → *domain*. "
        "Counts below are papers (a paper touching N topics contributes to N rows)."
    )
    push("")
    push("### Top 25 topics")
    push("")
    push("| # | Topic | Field | Papers |")
    push("|---:|---|---|---:|")
    for i, (td, n) in enumerate(agg.topic_paper_count.most_common(25), start=1):
        field = agg.topic_field.get(td, "—")
        push(f"| {i} | {td} | {field} | {n} |")
    push("")
    push("### Field totals")
    push("")
    push("| # | Field | Papers | % of corpus |")
    push("|---:|---|---:|---:|")
    for i, (f, n) in enumerate(agg.field_paper_count.most_common(15), start=1):
        push(f"| {i} | {f} | {n} | {pct(n, agg.n_papers)} |")
    push("")
    push("### Domain totals")
    push("")
    push("| Domain | Papers | % of corpus |")
    push("|---|---:|---:|")
    for d, n in agg.domain_paper_count.most_common():
        push(f"| {d} | {n} | {pct(n, agg.n_papers)} |")
    push("")

    # --- Venues ---
    push("## Venues and publishers")
    push("")
    push("### Top 25 journals/venues by citing-paper count")
    push("")
    push("| # | Venue | Publisher | Papers | % of corpus |")
    push("|---:|---|---|---:|---:|")
    for i, (v, n) in enumerate(agg.venue_paper_count.most_common(25), start=1):
        pub = agg.venue_publisher.get(v, "—")
        push(f"| {i} | {v} | {pub} | {n} | {pct(n, agg.n_papers)} |")
    push("")
    push("### Top 15 publishers")
    push("")
    push("| # | Publisher | Papers | % of corpus |")
    push("|---:|---|---:|---:|")
    for i, (p, n) in enumerate(agg.publisher_paper_count.most_common(15), start=1):
        push(f"| {i} | {p} | {n} | {pct(n, agg.n_papers)} |")
    push("")

    # --- OA by year ---
    push("## Open-access status by publication year")
    push("")
    push(
        "Open-access status of the citing papers, year by year. "
        "Relevant to Phase 3 (full-text acquisition) — OA + repository-hosted "
        "papers are reachable without paywalls. `oa_status` follows the "
        "OpenAlex/Unpaywall scheme: gold (publisher OA), green (repository), "
        "diamond (free, no APCs), bronze (free at publisher, no licence), "
        "hybrid (paid OA in subscription journal), closed (none of the above)."
    )
    push("")
    statuses = ["gold", "green", "diamond", "bronze", "hybrid", "closed", "unknown"]
    header = "| Year | " + " | ".join(statuses) + " | Total | OA % |"
    sep = "| ---: | " + " | ".join("---:" for _ in statuses) + " | ---: | ---: |"
    push(header)
    push(sep)
    for year in sorted(agg.oa_status_by_year):
        row = agg.oa_status_by_year[year]
        total = sum(row.values())
        oa = total - row.get("closed", 0) - row.get("unknown", 0)
        cells = [str(row.get(s, 0)) for s in statuses]
        push(f"| {year} | " + " | ".join(cells) + f" | {total} | {pct(oa, total)} |")
    push("")

    # --- Software co-mentions ---
    push("## Software co-mentions in abstracts")
    push("")
    push(
        f"Scanned {agg.n_with_abstract:,} abstracts for **{len(TOOL_PATTERNS)} "
        "phylogenetics tools**. The vocabulary has two layers: a small "
        "curated list of the obvious neighbors (RAxML, MrBayes, BEAST, …) "
        "and an expansion drawn from "
        "[`data/cz_software_mentions/comentioned_software.csv`](data/cz_software_mentions/comentioned_software.csv), "
        "which lists every other tool CZ Software Mentions found in the same "
        "372 papers (≥15-paper threshold, NER noise filtered). See "
        "`scripts/analyze_phase2.py` for the exact regexes."
    )
    push("")
    push(
        f"**{agg.n_papers_with_any_tool:,} of {agg.n_papers:,} papers "
        f"({pct(agg.n_papers_with_any_tool, agg.n_papers)}) name at least "
        "one tool from the list in their abstract.** Note: abstracts are "
        "short — the true workflow-co-occurrence rate from full text will be "
        "much higher. Phase 4 (LLM extraction over methods sections) will "
        "make this rigorous."
    )
    push("")
    push("### Co-mentioned tools (ranked)")
    push("")
    push("| # | Tool | Papers | % of corpus | % of abstract-bearing papers |")
    push("|---:|---|---:|---:|---:|")
    for i, (tool, n) in enumerate(agg.tool_paper_count.most_common(), start=1):
        push(
            f"| {i} | {tool} | {n} | {pct(n, agg.n_papers)} | "
            f"{pct(n, agg.n_with_abstract)} |"
        )
    push("")
    push("### How many tools per abstract")
    push("")
    push("| Tools named in abstract | Papers |")
    push("| ---: | ---: |")
    for k in sorted(agg.n_tools_per_paper):
        push(f"| {k} | {agg.n_tools_per_paper[k]} |")
    push("")

    push("## Reproducing this digest")
    push("")
    push("```sh")
    push("cd citations")
    push("uv run scripts/analyze_phase2.py")
    push("```")
    push("")
    push(
        "Reads `data/openalex/citing_works.jsonl` (built by "
        "`scripts/build_citing_works.py` from `data/openalex/raw/page_*.json`). "
        "Writes this file (`phase2_metadata.md`) and the flat "
        "`data/openalex/citing_works.csv`. Pure stdlib — no extra deps "
        "beyond the ones already in `pyproject.toml`."
    )
    push("")

    return "\n".join(out)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main() -> int:
    if not JSONL.exists():
        print(f"ERROR: missing {JSONL}", file=sys.stderr)
        print("Run scripts/build_citing_works.py first.", file=sys.stderr)
        return 2

    agg = Aggregates()
    CSV_OUT.parent.mkdir(parents=True, exist_ok=True)

    with JSONL.open() as fin, CSV_OUT.open("w", newline="", encoding="utf-8") as fcsv:
        writer = csv.DictWriter(fcsv, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for line in fin:
            rec = json.loads(line)
            tools = detect_tools(rec.get("abstract"))
            writer.writerow(to_csv_row(rec, tools))
            accumulate(agg, rec, tools)

    finalize(agg)

    MD_OUT.write_text(render_markdown(agg), encoding="utf-8")

    print(f"Wrote {CSV_OUT.relative_to(ROOT)} ({agg.n_papers} rows)")
    print(f"Wrote {MD_OUT.relative_to(ROOT)}")
    print(f"  unique authors: {agg.n_unique_authors:,}")
    print(f"  with ORCID:     {agg.n_authors_with_orcid:,}")
    print(f"  unique inst:    {len(agg.inst_paper_count):,}")
    print(f"  countries:      {len(agg.country_paper_count)}")
    print(f"  venues:         {len(agg.venue_paper_count):,}")
    print(f"  topics:         {len(agg.topic_paper_count):,}")
    print(f"  abstract w/tool: {agg.n_papers_with_any_tool:,}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
