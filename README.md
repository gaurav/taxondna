# TaxonDNA

[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.14994608.svg)](https://doi.org/10.5281/zenodo.14994608)

## Citing TaxonDNA

Two publications cover the two main TaxonDNA tools:

* For SpeciesIdentifier, please cite: Meier, R., Kwong, S., Vaidya, G., Ng, Peter K. L. (2006)
  *DNA Barcoding and Taxonomy in Diptera: a Tale of High Intraspecific Variability and Low Identification Success*.
  Systematic Biology **55**: 715--728. [doi:10.1080/10635150600969864](https://doi.org/10.1080/10635150600969864).
* For SequenceMatrix, please cite: Vaidya, G., Lohman, D.J., Meier, R. (2011) *SequenceMatrix: concatenation software
  for the fast assembly of multi-gene datasets with character set and codon information*. Cladistics 27: 171--180.
  [doi:10.1111/j.1096-0031.2010.00329.x](https://doi.org/10.1111/j.1096-0031.2010.00329.x).

For other software, please cite this repository via Zenodo:

* Vaidya (2025) *TaxonDNA*. GitHub: https://github.com/gaurav/taxondna. [doi:10.5281/zenodo.14994608](https://doi.org/10.5281/zenodo.14994608)

## System requirements

TaxonDNA is written entirely in Java, with its interface written using
either Java AWT or Swing. Its only requirement is a standard Java VM -
except that since TaxonDNA 1.6 we've given up supporting Java 1.4, so
you'll need to have atleast Java 1.5 since then.

There are a [long list of bugs](http://code.google.com/p/taxondna/issues/list),
some of which prevent TaxonDNA from working well on multiple platforms. Note
particularly UI-related issues: some UI elements only work on particular
platforms, although we try to work around them as well as we can.

## Individual tools

TaxonDNA consists of three tools - Species Identifier [2], SequenceMatrix,
and GenBank Explorer (still in beta). JNLP files for the first two
are available in this repository (but you'll have to get someone to
the latest JAR files to Sourceforge), as well as MS-DOS Batch files,
if you prefer those. You should be able to get GBX working by compiling it
from source:

```shell
$ mvn package
$ java -jar target/TaxonDNA-1.10-SNAPSHOT-SpeciesIdentifier.jar 
```

## Building software

TaxonDNA uses Maven for packaging. You can build all the outputs by running:

```shell
$ mvn package
```

This will create both JAR files and ZIP files for distribution in the `target/`
directory.

## Tests

We do not currently have tests. But someday we will!

## Running TaxonDNA

TaxonDNA uses an unfortunately a large amount of memory per sequence.
It can handle larger files fairly well, but by default, Java
applications are limited to 64mb. You will need to use the '-Xmx'
option to increase your memory usage. The easiest way to do this is,
at the command line, to enter:

```shell
$ java -Xmx1024M -jar TaxonDNA.jar
```

You might also have to adjust the value of 1024 megabytes depending
on the size of your dataset and on the memory available to your
computer. Java will refuse to run if the Xmx value specified is too
large. Also, if TaxonDNA.jar is not in the directory specified, you
might need to enter the complete path to TaxonDNA, as so:

```shell
> java -Xmx1024M -jar C:\TaxonDNA\TaxonDNA.jar
```

If the program runs out of memory while running, it will appear to
"hang" and become completely unresponsive. There is at present no
way to recover from this state. You'll want to increase your memory
setting using "-Xmx" as shown above.

Please note that trying to load a sequences file larger than the
memory size specified (i.e. 1024 mb in the above example) will also
result in the program hanging. We have tested this program with a
file of 2,185 sequences with 2,664 base pairs, and it has been known
to work on datasets upto 8,000 sequences long. Please let us know if
memory size is an issue for you, and we will try to incorporate
workarounds in future versions.

## File formats

This program can handle input in FASTA, MEGA, Nexus and TNT formats.
GenBank support is provided through the GenBankExplorer. FASTA
is our oldest supported format, and most of the tools are designed
to accept sequences from NCBI GanBank (exported as FASTA) as input.
It attempts to guess the species name from the FASTA title string,
and considers hyphens ('-') as gaps, and question marks ('?') as
missing data (incidently, if you use a very different gap/missing
syntax, you could use Nexus as an intermediate format, since it
supports defining your own gap/missing data specifier.

## Contacting us

You can get in touch with the developers in our 
[GitHub repository](https://github.com/gaurav/taxondna), specifically
by [opening an issue](https://github.com/gaurav/taxondna/issues/new)
if something doesn't work correctly. You can also e-mail me
at <tt>gaurav</tt> at <tt>ggvaidya dot com</tt>.
