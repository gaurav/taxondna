# Changelog

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [v1.9]: May 14, 2021

- Replaced Ant-based build process with Maven build.
- Changed minimum Java version from 1.8 to 11.
- Consolidated all shared code into TaxonDNA.Common.
- Added memory information in the SequenceMatrix About dialog box.
- Added Spotless for code style checking.

## [v1.8.2]: April 17, 2020

- Updated Java version from 1.5 to 1.8. No Mac application is available for this release, but I'll try to fix that in #70 before the next release. You can run the JAR file on macOS by opening the Terminal and running:

## [v1.8.1]: April 17, 2020

## Older versions

- [1.8]: June 22, 2015
  - Used 'codesign' to sign "Sequence Matrix.app" as this might prevent
    it from being recognized on MacOS X systems.
  - Thanks to Shiyu Phan for this suggestion!
- 1.7.9
  - Turned off displaying 'N's in the display, as we don't re-export
    those anyway.
  - Fixed a bug in the Cluster tool reported by Michael Balke: single
    species without conspecific sequences in the dataset were being
    reported as 'Split'.
- 1.7.8
  - Added DOI to the citation for Sequence Matrix.
- 1.7.7-dev4
  - Naked Nexus now replaces all non-alphanumeric characters with underscores.
- 1.7.7-dev3
  - Fixed a minor bug with clustering in which the "% of valid comparisons
    	above the threshold" number was always 0%.
  - Fixed a bug in which multiple "Cluster" analyses in the same session
    caused the ProgressDialog to never close, resulting in the program
    getting "stuck" and a forced quit becoming necessary.
- 1.7.7-dev2
  - Rewrote SpeciesDetails to remove the old, bug-ridden algorithm.
    The new one will probably actually work.
  - Added discrimination between "Split only", "Lumped/Split" and
    "Lumped" for clusters in Cluster.
- 1.7.6 (June 6, 2010)
  - Added code to detect overlapping codonsets on input (#19).
  - Fixed a bug relating to TNT files exported into directory (#55).

[Unreleased]: https://github.com/gaurav/taxondna/compare/v1.9...HEAD
[v1.9]: https://github.com/gaurav/taxondna/compare/v1.8.2...v1.9
[v1.8.2]: https://github.com/gaurav/taxondna/compare/v1.8.1...v1.8.2
[v1.8.1]: https://github.com/gaurav/taxondna/compare/v1.8...v1.8.1
[1.8]: https://github.com/gaurav/taxondna/releases/tag/1.8

