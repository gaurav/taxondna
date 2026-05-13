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
mvn test                     # Run unit tests (Kotest + JUnit 5)
mvn spotless:check           # Check code formatting (runs in CI)
mvn spotless:apply           # Auto-fix code formatting
```

Output JARs go to `target/` (e.g., `TaxonDNA-1.11-SNAPSHOT-SpeciesIdentifier.jar`).

Native installers (macOS `.dmg`, Windows `.msi`, Linux `.deb`) are built via OS-specific profiles wired to `jpackage-maven-plugin`. Each profile must run on its target OS — jpackage cannot cross-compile.

```bash
mvn -Pmac-installer     package -Dapp.version=1.11.0   # macOS only
mvn -Pwindows-installer package -Dapp.version=1.11.0   # Windows only; needs WiX 3
mvn -Plinux-installer   package -Dapp.version=1.11.0   # Linux only; needs dpkg + fakeroot
```

`app.version` must be `MAJOR[.MINOR[.PATCH]]` with `MAJOR >= 1` (macOS rejects a leading zero). See [RELEASING.md](RELEASING.md) for the full release flow, the GitHub Actions pipeline, and the deferred macOS/Windows signing setup.

To run an application:

```bash
java -Xmx16G -jar target/TaxonDNA-1.11-SNAPSHOT-SpeciesIdentifier.jar
```

The `Tests/` directory contains sample data files, not test code. Unit tests live in `src/test/kotlin/` and use Kotest FunSpec style. See [TESTING.md](TESTING.md) for the testability roadmap.

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

Runtime dependencies: `kotlin-stdlib`. Test dependencies: Kotest (`kotest-runner-junit5-jvm`, `kotest-assertions-core-jvm`, `kotest-property-jvm`). Each application is packaged as a self-contained shaded JAR (via `maven-shade-plugin`) that bundles the `Common` classes and all runtime dependencies.

## Key Conventions

- Java 17 is the minimum supported version (source/target in pom.xml)
- Kotlin 2.1.0 is configured for mixed compilation; Kotlin compiles before Java (via `kotlin-maven-plugin`)
- New code can be written in Kotlin (`src/main/kotlin/`) with full Java interop
- UI uses Java AWT and Swing (not JavaFX)
- Sequences are stored as `char[]` arrays; applications are memory-intensive and require `-Xmx` flags for large datasets
- Species names are parsed from FASTA title strings; hyphens are gaps, question marks are missing data
- Kotlin test files need `@file:Suppress("ktlint:standard:package-name")` because the Java package names use uppercase (e.g., `Common.DNA`), which ktlint disallows
- Kotlin 2.1.0's bundled IntelliJ `JavaVersion` parser rejects the string `"25.0.2"`, so `mvn test-compile` (and any goal that triggers it) fails on JDK 25. Workaround for local builds on JDK 25 is `-Dmaven.test.skip=true`; CI runs Java 17/21/23 and is unaffected. Tracked in [#123](https://github.com/gaurav/taxondna/issues/123).

