# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaxonDNA is a taxonomy-aware DNA sequence processing toolkit written in Java and Kotlin. It provides three desktop GUI applications for bioinformatics researchers:

- **SpeciesIdentifier** — DNA barcoding and species identification (entry: `com.ggvaidya.TaxonDNA.SpeciesIdentifier.SpeciesIdentifier`)
- **SequenceMatrix** — Multi-gene dataset concatenation (entry: `com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceMatrix`)
- **GenBankExplorer** — GenBank file browser/extractor, beta (entry: `com.ggvaidya.TaxonDNA.GenBankExplorer.GenBankExplorer`)

## Build Commands

```bash
mvn package                  # Build all JARs and ZIP distributions
mvn spotless:check           # Check code formatting (runs in CI)
mvn spotless:apply           # Auto-fix code formatting
```

Output JARs go to `target/` (e.g., `TaxonDNA-1.11-SNAPSHOT-SpeciesIdentifier.jar`).

To run an application:

```bash
java -Xmx16G -jar target/TaxonDNA-1.11-SNAPSHOT-SpeciesIdentifier.jar
```

There are no unit tests. The `Tests/` directory contains sample data files, not test code.

## Code Formatting

Spotless enforces Google Java Format (AOSP style) for Java and ktlint for Kotlin via `spotless-maven-plugin`. CI runs `mvn spotless:check` on PRs across Java 17, 21, and 23. Always run `mvn spotless:apply` before committing Java or Kotlin changes.

## Architecture

Java source lives under `src/main/java/com/ggvaidya/TaxonDNA/`, Kotlin source under `src/main/kotlin/`:

- **`Common/`** — Shared library used by all three applications
  - **`Common/DNA/`** — Core data model: `Sequence` (individual DNA sequence), `SequenceList` (collection), `SequenceGrid` (grid-based storage), pairwise distance calculations
  - **`Common/DNA/formats/`** — File format handlers implementing `FormatHandler` interface (FASTA, Nexus, TNT, MEGA, Phylip, GenBank). `NexusFile` and `TNTFile` are the largest/most complex.
  - **`Common/UI/`** — AWT/Swing UI components. `UIExtension` is the plugin interface used by SpeciesIdentifier modules.
- **`SpeciesIdentifier/`** — Plugin architecture: main class loads modules (Cluster, BestMatch, CompleteOverlap, etc.) that implement `UIExtension`, displayed via CardLayout
- **`SequenceMatrix/`** — Uses DisplayMode pattern: `DataStore` holds the model, `TableManager` handles JTable UI, display modes (Sequences, Distances, Correlations) control rendering. `FileManager` (largest file) handles all I/O.
- **`GenBankExplorer/`** — Simpler architecture for browsing GenBank files

The only external dependency is `kotlin-stdlib`. Each application is packaged as a self-contained shaded JAR (via `maven-shade-plugin`) that bundles the `Common` classes and all dependencies.

## Key Conventions

- Java 17 is the minimum supported version (source/target in pom.xml)
- Kotlin 2.1.0 is configured for mixed compilation; Kotlin compiles before Java (via `kotlin-maven-plugin`)
- New code can be written in Kotlin (`src/main/kotlin/`) with full Java interop
- UI uses Java AWT and Swing (not JavaFX)
- Sequences are stored as `char[]` arrays; applications are memory-intensive and require `-Xmx` flags for large datasets
- Species names are parsed from FASTA title strings; hyphens are gaps, question marks are missing data

