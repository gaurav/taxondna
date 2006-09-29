Readme for TaxonDNA 0.9.5
=========================

CONTENTS
1. 	System requirements
2. 	Memory requirements
3. 	File formats
4. 	Further information

1. SYSTEM REQUIREMENTS

TaxonDNA is written entirely in Java,
with its interface written using the
Java AWT. Thus, it's only requirements
are any program running Java.

It has been tested extensively only on
Java 1.4 on Windows. It should work on
later versions of Java and on other
operating systems with suitable 
Java VMs without any problems. However,
there are known issues with the 
interface on MacOS X at the moment. We
hope to have these resolved by
TaxonDNA 1.0.

2. MEMORY REQUIREMENTS

TaxonDNA has fairly high memory requirements.
The normal Java memory limit of around 64mb
is far too small for any but the smallest
datasets. You will need to use the '-Xmx'
option to increase your memory usage. The
easiest way to do this is, at the command
line, to enter:
	java -Xmx1024M -jar TaxonDNA.jar

You might have to adjust the value of
1024 megabytes depending on the size of
your dataset and on the memory available
in your computer. Java will refuse to
run if the Xmx value specified is too
large. Also, if TaxonDNA.jar is not in the
directory specified, you might need to
enter the complete path to TaxonDNA,
such as
	java -Xmx1024M -jar C:\TaxonDNA\TaxonDNA.jar

If the program runs out of memory while 
running, it will appear to "hang", and
become completely unresponsive. There is
at present no way to recover from this
state, although this will be fixed by 
TaxonDNA 1.0. Please see the "HOWTO.txt" 
file for details on setting up a batch
file in Windows to run the program with
additional memory. 

Please note that trying to load a 
sequence file larger than the memory size
specified (i.e. 1024 mb in the above 
example) will also result in the program
hanging. We have tested this program
with a file of 2,185 sequences with 
2,664 base pairs, and it has been known
to work on datasets upto 8,000 sequences
long. Please let us know if memory size
is an issue for you, and we will try to
incorporate workarounds in future 
versions.

3. FILE FORMATS

This program can handle input in FASTA and
MEGA formats, and has been designed to accept
sequences from NCBI GanBank (exported as FASTA)
as input. It attempts to guess the species name 
from the FASTA title string. At the moment, 
it considers hyphens ('-') as gaps, and question
marks ('?') as missing data.

There is also partial support for importing
GenBank files. It's not perfect yet, but
it should be better than nothing. It uses
'CDS' feature entries to 'cut' out genes of 
interest, and can't write GenBank files yet. 
Please file bug reports if you have problems 
with it!

4. FURTHER INFORMATION

If you need more information about this
program, or have any other questions,
queries or bug reports to make, please
contact us, either via our SourceForge
page at:
	http://taxondna.sf.net/
or by e-mail at
	gaurav AT ggvaidya DOT com.
