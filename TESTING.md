# Testing Guide for TaxonDNA

This document tracks testability of files in `Common/` to guide future test-writing PRs.

## Test Framework

- **Kotest** (FunSpec style) with JUnit 5 runner
- Test sources: `src/test/kotlin/`
- Run: `mvn test`

## Testability Tiers

### Tier 1: Pure Logic, No Dependencies (test now)

These files have no UI or I/O dependencies and can be tested with simple unit tests.

|                 File                 | Lines | Status  |                              Notes                               |
|--------------------------------------|------:|---------|------------------------------------------------------------------|
| `DNA/FromToPair.java`                |   130 | Tested  | Constructor validation, compareTo, overlaps                      |
| `DNA/Settings.java`                  |   124 | Tested  | Static math utilities; uses static state (save/restore in tests) |
| `DNA/Sequence.java` (static methods) |  ~300 | Tested  | isValid, isAmbiguous, complement, consensus, etc.                |
| `DNA/SpeciesDetail.java`             |   125 | Next    | Simple data holder with equals/toString                          |
| `DNA/PairwiseDistance.java`          |    74 | Next    | Thin wrapper around a double value                               |
| `DNA/SequenceException.java`         |    64 | Trivial | Exception subclass, low value to test                            |
| `DNA/SequenceListException.java`     |    49 | Trivial | Exception subclass, low value to test                            |
| `DNA/SequenceContainer.java`         |    45 | Skip    | Interface only, no logic                                         |

### Tier 2: Core Model with Some Setup Required

These need `Sequence` or `SequenceList` instances but no I/O or UI.

|                  File                  | Lines |                                 Notes                                 |
|----------------------------------------|------:|-----------------------------------------------------------------------|
| `DNA/Sequence.java` (instance methods) | ~1800 | Constructors, getSubsequence, species name parsing, pairwise distance |
| `DNA/BaseSequence.java`                |   319 | Base class for Sequence; testable via Sequence subclass               |
| `DNA/SequenceList.java`                |  1031 | Collection class; needs Sequence instances                            |
| `DNA/SortedSequenceList.java`          |   276 | Wraps SequenceList with sorting                                       |
| `DNA/SequenceGrid.java`                |   898 | Grid storage; needs Sequence instances                                |
| `DNA/SpeciesDetails.java`              |   257 | Aggregates SpeciesDetail from a SequenceList                          |
| `DNA/PairwiseDistances.java`           |   303 | Distance matrix; needs Sequence pairs                                 |
| `DNA/PairwiseDistribution.java`        |   402 | Statistical distribution of distances                                 |

### Tier 3: Format Handlers (need file I/O mocking or test fixtures)

These parse/write files. Best tested with small fixture files in `src/test/resources/`.

|                 File                  | Lines |                           Notes                            |
|---------------------------------------|------:|------------------------------------------------------------|
| `DNA/formats/FastaFile.java`          |   423 | Most common format; good candidate for fixture-based tests |
| `DNA/formats/NexusFile.java`          |  1839 | Largest, most complex format handler                       |
| `DNA/formats/TNTFile.java`            |  1272 | Second largest format handler                              |
| `DNA/formats/MegaFile.java`           |   437 | MEGA format                                                |
| `DNA/formats/PhylipFile.java`         |   242 | Phylip format                                              |
| `DNA/formats/SequencesFile.java`      |   554 | Generic sequences format                                   |
| `DNA/formats/NexusTokenizer.java`     |   408 | Tokenizer for Nexus; could test independently              |
| `DNA/formats/BaseFormatHandler.java`  |    78 | Base class with file extension logic                       |
| `DNA/formats/FormatHandler.java`      |   109 | Interface with default methods                             |
| `DNA/formats/FormatHandlerEvent.java` |   100 | Event class, simple                                        |
| `DNA/formats/FormatException.java`    |    47 | Exception subclass                                         |
| `DNA/formats/FormatListener.java`     |    44 | Interface only                                             |
| `DNA/formats/Interleaver.java`        |    75 | Utility for interleaved output                             |
| `DNA/formats/SequencesHandler.java`   |   109 | Handler interface                                          |

### Tier 4: UI Components (need AWT/Swing, hard to unit test)

These depend on AWT/Swing and are best validated manually or with integration tests.

|               File                | Lines |           Notes            |
|-----------------------------------|------:|----------------------------|
| `UI/MessageBox.java`              |   353 | Modal dialogs              |
| `UI/ProgressDialog.java`          |   271 | Progress UI with threading |
| `UI/RightLayout.java`             |   274 | Custom LayoutManager       |
| `UI/DirectoryInputPanel.java`     |   207 | File chooser panel         |
| `UI/FileInputPanel.java`          |   205 | File chooser panel         |
| `UI/LargeTextArea.java`           |   155 | Text display component     |
| `UI/ProgressBar.java`             |   115 | Progress display           |
| `UI/DefaultButton.java`           |    84 | Button wrapper             |
| `UI/CloseableWindow.java`         |    73 | Window close handler       |
| `UI/UIExtension.java`             |    66 | Plugin interface           |
| `UI/UndoStack.java`               |    61 | Undo manager               |
| `UI/UndoTask.java`                |    45 | Undo task interface        |
| `UI/DirectoryFilenameFilter.java` |    38 | Filename filter (testable) |

### Tier 5: Miscellaneous

|             File              | Lines |                          Notes                          |
|-------------------------------|------:|---------------------------------------------------------|
| `Pearls.java`                 |    74 | Custom hash function; replace with stdlib in future PR  |
| `Others/UUID.java`            |    71 | Custom UUID; replace with `java.util.UUID` in future PR |
| `Others/BrowserLauncher.java` |   654 | Platform-specific browser launch; not worth testing     |
| `DelayCallback.java`          |    56 | Interface                                               |
| `DelayAbortedException.java`  |    50 | Exception subclass                                      |
| `Testable.java`               |    42 | Legacy test interface                                   |
| `TestController.java`         |    54 | Legacy test runner                                      |
| `Versions.java`               |    34 | Version tracking interface                              |
| `DNA/Testing.java`            |   111 | Legacy test driver with main(); superseded by Kotest    |

## Recommended Next Steps

1. **Tier 1 remaining**: SpeciesDetail, PairwiseDistance
2. **Tier 2 core**: Sequence instance methods (constructors, species name parsing, getSubsequence)
3. **Tier 3 start**: FastaFile with small fixture files, NexusTokenizer
4. **Tier 2 collections**: SequenceList, SortedSequenceList

