/**
 * Allows you to read and write files in FASTA format.
 * We'll try to be smart about things: for instance,
 * a file containing multiple '&gt;' signs will be
 * flagged as an error or ignored by this class.
 *
 * We don't follow FASTA _very_ strictly, allowing a
 * FASTA species name to be more than 70 characters
 * long. Do let me know if you have any problems
 * with this.
 * 
 * Format:
 * &gt;Sequence name
 * Sequence
 *
 * &gt;Sequence name 2
 * Sequence 2
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2005-06 Gaurav Vaidya

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package com.ggvaidya.TaxonDNA.DNA.formats;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;

public class FastaFile extends BaseFormatHandler implements Testable {
	private int count_sequences = 0;
	
	/** Creates a FastaFile reader/writer. */
	public FastaFile() {}
	
	/** Returns the short name of this handler, namely: "FASTA" */
	public String getShortName() {
		return "FASTA";
	}

	/** Returns the extension. We'll go with '.fas' as our semi-official DOS-compat extension */
	public String getExtension() {
		return "fas";
	}

	/** Returns the full name of this handler */
	public String getFullName() {
		return "Fasta files (including support for long names)";
	}

	/**
	 * Returns true if it seems likely (heck, possible)
	 * the file is a FASTA file. To be strict, we'll
	 * parse the entire file, counting as we go. This
	 * also saves us a count later on, and it seems
	 * unlikely that we'll ever run readFile() without
	 * calling this first.
	 *
	 * Note: in the interests of saving time, we will
	 * NOT test past the 2nd '&gt;'. We'll just count
	 * the number of '&gt;' we get after this point.
	 * This should be "good enough".
	 *
	 */
	public boolean mightBe(File file) {
		BufferedReader reader = null;
		
		count_sequences = 0;		// mightBe() counts the number of sequences as it goes
		
		try {
			reader = new BufferedReader(new FileReader(file));

			Pattern 	pSequence =	Pattern.compile("^[A-Za-z\\-\\?\\[\\]\\(\\) ]*$");
			Pattern 	pBlank	=	Pattern.compile("^\\s*$");
			Pattern 	pComment =	Pattern.compile("^\\s*#.*$");
			Pattern		pName =		Pattern.compile("^>\\s*(.*)\\s*$");
			Pattern		pNameNotEmpty =	Pattern.compile("^>\\s*(.+)\\s*$");

			boolean		dontTest =	false;
	
			while(reader.ready()) {
				String line = reader.readLine().trim();

				if(dontTest) {
					// don't test the full para - just count '>'s
					if(line.length() > 0 && line.charAt(0) == '>')
						count_sequences++;
					continue;
				}
			
				if(pNameNotEmpty.matcher(line).matches()) {
					// it matches a name "^>.......$", so let's say it *might* be
					if(count_sequences > 2)
						dontTest = true;	// turn off reg ex testing
					count_sequences++;
				} else if(pName.matcher(line).matches()) {
					count_sequences++;
				} else if(pName.matcher(line).matches()) {
					return false;
				} else if(pSequence.matcher(line).matches()) {
					// it's a sequence bit
				} else if(pComment.matcher(line).matches()) {
					// comments we'll let pass
				} else if(pBlank.matcher(line).matches()) {
					// blank lines we'll let pass
				} else {
					// if it's not one of the above, something is wrong
					return false;
				}
			}

			reader.close();
			
			if(count_sequences == 0)
				return false;
		
			return true;
			
		} catch(IOException e) {
			return false;
		}
	}

	/**
	 * Reads the contents of a FASTA file into a new SequenceList.
	 */
	public SequenceList readFile(File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {
		SequenceList list = new SequenceList();
		appendFromFile(list, file, delay);
		return list;
	}

	/**
	 * Appends the contents of a FASTA file to the specified SequenceList.
	 * We run mightBe() internally, since it gives us the count anyways.
	 */
	public void appendFromFile(SequenceList list, File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException
	{
		Pattern 	pBlank	=	Pattern.compile("^\\s*$");
		Pattern 	pComment =	Pattern.compile("^\\s*#.*$");
		Pattern		pNameNotEmpty =	Pattern.compile("^>\\s*(.+)\\s*$");
		Pattern		pName	=	Pattern.compile("^>\\s*(.*)\\s*$");
		Pattern		pSequence =	Pattern.compile("^\\s*(.*)\\s*$");

		String 		name =	"";
		String 		seq = 	"";
		int		lineno = 0;
		Matcher		m;

		list.lock();
				
		if(count_sequences == 0)
			if(!mightBe(file))
				return;			// we do nothing to show disrespect for this file 

		if(count_sequences == 0)		// if there are no sequences in this file, it's presumbably not valid
			return;
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		
		if(delay != null)
			delay.begin();
		
		// the hard work
		int sequences = 0;
		while(reader.ready()) {
			lineno++;
			String line = reader.readLine();
		
			if(pBlank.matcher(line).matches()) {
				// Ignore blank lines 
			} else if(pComment.matcher(line).matches()) {
				// Ignore comment lines
			} else if((m = pNameNotEmpty.matcher(line)).matches()) {
				// add the previous entry
				if(!name.equals("")) {
					try {
						Sequence x = new Sequence(name, seq);
						list.add(x);
					} catch(SequenceException e) {
						if(delay != null)
							delay.end();
						throw e; 
					}
					sequences++;
				}
				// start new seq
				name = m.group(1);
				seq = "";

				try {
					if(delay != null) {
						delay.delay(sequences, count_sequences);
					}	
				} catch(DelayAbortedException e) {
					if(delay != null)
						delay.end();
					list.unlock();
					throw e;
				}
			} else if((m = pName.matcher(line)).matches()) {
				// add the previous entry
				if(!name.equals("")) {
					try {
						Sequence x = new Sequence(name, seq);
						list.add(x);
					} catch(SequenceException e) {
						if(delay != null)
							delay.end();
						throw e; 
					}
					sequences++;
				}
				// if we're here, we don't *have* a next
				// so we'll start a new sequence called 'no name specified in file'
				name = "No name specified in file";
				seq = "";

				try {
					if(delay != null) {
						delay.delay(sequences, count_sequences);
					}	
				} catch(DelayAbortedException e) {
					if(delay != null)
						delay.end();
					list.unlock();
					throw e;
				}
 
			} else if((m = pSequence.matcher(line)).matches()) {
				// append the sequence part
				seq += m.group(1);
			} else {
				delay.end();
				throw new FormatException("Unable to interpret line " + lineno + ": " + line);
			}
		}

		// done!
		if(delay != null)
			delay.end();

		reader.close();

		// is there anything in the buffers?
		if(!name.equals("")) {
			try {
				Sequence x = new Sequence(name, seq);
				list.add(x);
			} catch(SequenceException e) {
				if(delay != null)
					delay.end();
			}
		}

		list.setFile(file);
		list.setFormatHandler(this);	
		list.unlock();
	}

	/**
	 * Write this SequenceList into a Fasta file.
	 *
	 * @throws IOException if there was an error during input/output. 
	 * @throws DelayAbortedException if the user aborted this function.
	 */
	public void writeFile(File file, SequenceList set, DelayCallback delay) throws IOException, DelayAbortedException {
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		Iterator i = set.iterator();
		int count = set.count();
		int x = 0;

		if(delay != null)
			delay.begin();

		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			writer.println(">" + seq.getFullName());
			writer.println(seq.getSequenceWrapped(70));

			try {
				if(delay != null)
					delay.delay(x, count);
			} catch(DelayAbortedException e) {
				writer.close();
				if(delay != null)
					delay.end();	
				throw e;
			}

			x++;
		}

		if(delay != null)
			delay.end();

		writer.close();
	}

	/**
	 * Tests the FastaFile class extensively so that bugs don't creep in.
	 */
	public void test(TestController testMaster, DelayCallback delay) throws DelayAbortedException {
		testMaster.begin("DNA.formats.FastaFile");

		FastaFile ff = new FastaFile();

		testMaster.beginTest("Recognize a file as being a FASTA file");
			File test = testMaster.file("dna/formats/fastafile/test_fasta1.txt");
			if(ff.mightBe(test))
				try {
					int count = ff.readFile(test, delay).count();
					if(count == 10)
						testMaster.succeeded();
					else	
						testMaster.failed("I got back " + count + " sequences instead of 10!");
				} catch(IOException e) {
					testMaster.failed("There was an IOException reading " + test + ": " + e);
				} catch(SequenceException e) {
					testMaster.failed("There was a SequenceException reading " + test + ": " + e);
				} catch(FormatException e) {
					testMaster.failed("There was a FormatException reading " + test + ": " + e);
				}

			else
				testMaster.failed(test + " was not recognized as a FASTA file");

		testMaster.beginTest("Recognize other files as being non-FASTA");
			File notfasta = testMaster.file("dna/formats/fastafile/test_nonfasta1.txt");
			if(notfasta.canRead() && !ff.mightBe(notfasta))
				testMaster.succeeded();
			else
				testMaster.failed(notfasta + " was incorrectly identified as a FASTA file");

		testMaster.beginTest("Read in FASTA files with spaces/empty lines in them");
			File spaces = testMaster.file("dna/formats/fastafile/test_empty_lines.txt");
			if(spaces.canRead() && ff.mightBe(spaces)) {
				try {
					int count = ff.readFile(spaces, delay).count();
					if(count == 3)
						testMaster.succeeded();
					else
						testMaster.failed("I got back " + count + " sequences instead of 3!");
				} catch(IOException e) {
					testMaster.failed("There was an IOException reading " + test + ": " + e);
				} catch(SequenceException e) {
					testMaster.failed("There was a SequenceException reading " + test + ": " + e);
				} catch(FormatException e) {
					testMaster.failed("There was a FormatException reading " + test + ": " + e);
				}
			}

		testMaster.beginTest("Write out a FASTA file, then read it back in (twice!)");
			File input = testMaster.file("dna/formats/fastafile/test_fastacopy.txt");
			File success = testMaster.file("dna/formats/fastafile/test_fastacopy_success.txt");
			File output = testMaster.tempfile();
			File output2 = testMaster.tempfile();

			try {
				SequenceList list = ff.readFile(input, delay);
				ff.writeFile(output, list, delay);

				list = ff.readFile(output, delay);
				ff.writeFile(output2, list, delay);

				if(testMaster.isIdentical(success, output2))
					testMaster.succeeded();
				else
					testMaster.failed(
						"I read a FASTA file from " + input + ", then wrote it to '" + output + "', but they aren't identical"
					);
			} catch(IOException e) {
				testMaster.failed(
					"I read a FASTA file from " + input + ", then wrote it to '" + output + "', but I got an IOException: " + e
					);
			} catch(SequenceException e) {
				testMaster.failed(
					"I read a FASTA file from " + input + ", then wrote it to '" + output + "', but I got a SequenceListException: " + e
					);
			} catch(FormatException e) {
				testMaster.failed(
					"I read a FASTA file from " + input + ", then wrote it to '" + output + "', but I got a SequenceListException: " + e
					);
			}

		testMaster.done();
	}
}
