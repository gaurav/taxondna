Readme for TaxonDNA 1.6.1
==========================

CONTENTS
1. 	System requirements
2.      Individual tools
3.      Building software
4. 	Memory requirements
5. 	File formats
6. 	Further information
7.      References

1. SYSTEM REQUIREMENTS

TaxonDNA is written entirely in Java, with its interface written using 
either Java AWT or Swing. Its only requirement is a standard Java VM - 
except that since TaxonDNA 1.6 we've given up supporting Java 1.4, so 
you'll need to have atleast Java 1.5 since then.

There are a long list of bugs [1], some of which prevent TaxonDNA from
working well on multiple platforms. Note particularly UI-related issues:
some UI elements only work on particular platforms, although we try to
work around them as well as we can.

2. INDIVIDUAL TOOLS

TaxonDNA consists of three tools - Species Identifier [2], SequenceMatrix,
and GenBank Explorer (still in beta). JNLP files for the first two
are available in this repository (but you'll have to get someone to
the latest JAR files to Sourceforge), as well as MS-DOS Batch files,
if you prefer those. You should be able to get GBX working by running:
        cd build/classes
        java -Xmx1024M com.ggvaidya.TaxonDNA.GenBankExplorer.GenBankExplorer

3. BUILDING SOFTWARE

You will need the javac compiler, a copy of the Java runtime and the
Ant build tool in order to build TaxonDNA. The Ant build.xml should
contain all the necessary instructions to build any of the components
you need. A plain "ant" will compile all the source; additional
targets are available to package TaxonDNA.

4. MEMORY REQUIREMENTS

TaxonDNA uses an unfortunately a large amount of memory per sequence. 
It can handle larger files fairly well, but by default, Java
applications are limited to 64mb. You will need to use the '-Xmx' 
option to increase your memory usage. The easiest way to do this is, 
at the command line, to enter:
	java -Xmx1024M -jar TaxonDNA.jar

You might also have to adjust the value of 1024 megabytes depending 
on the size of your dataset and on the memory available to your 
computer. Java will refuse to run if the Xmx value specified is too
large. Also, if TaxonDNA.jar is not in the directory specified, you 
might need to enter the complete path to TaxonDNA, as so:
	java -Xmx1024M -jar C:\TaxonDNA\TaxonDNA.jar

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

5. FILE FORMATS

This program can handle input in FASTA, MEGA, Nexus and TNT formats.
GenBank support is provided through the GenBankExplorer. FASTA
is our oldest supported format, and most of the tools are designed 
to accept sequences from NCBI GanBank (exported as FASTA) as input. 
It attempts to guess the species name from the FASTA title string,
and considers hyphens ('-') as gaps, and question marks ('?') as 
missing data (incidently, if you use a very different gap/missing 
syntax, you could use Nexus as an intermediate format, since it 
supports defining your own gap/missing data specifier.

6. FURTHER INFORMATION

If you need more information about this program, or have any other 
questions, queries or bug reports to make, please contact us, either 
via *any* of our websites:
	http://taxondna.sf.net/
        http://code.google.com/p/taxondna/
        http://github.com/gaurav/taxondna/
        http://groups.google.com/group/taxondna

or by e-mail at
	gaurav AT ggvaidya DOT com.

7. REFERENCES
[1] TaxonDNA bug list at http://code.google.com/p/taxondna/issues/list
[2] Meier, R., Kwong, S., Vaidya, G., Ng, Peter K. L. (2006) 
    DNA Barcoding and Taxonomy in Diptera: a Tale of High Intraspecific 
    Variability and Low Identification Success. 
    Systematic Biology, 55: 715-728.
