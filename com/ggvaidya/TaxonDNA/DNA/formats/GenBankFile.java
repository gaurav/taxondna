/**
 * Allows you to read and write files in GenBank format.
 * This is the coolest, because just about all the information
 * is readily available and easily accessible. On the down
 * side, it's a lot of parsing.
 *
 */
/*
   TaxonDNA
   Copyright (C) 2006	Gaurav Vaidya

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

public class GenBankFile implements FormatHandler, Testable {
	/** Creates a GenBankFile format handler */
	public GenBankFile() {}
	
	/** Returns the short name of this handler, which is "GenBank Flat File format" */
	public String getShortName() {
		return "GenBank format";
	}

	/** Returns the full name of this handler*/
	public String getFullName() {
		return "GenBank format, including being able to select specific coding segments";
	}

	/** 
	 * Writes the sequence list 'list' into the File 'file', in GenBank format.
	 *
	 * Not currently implemented; it throws an exception if you try, etc.
	 * 
	 * @throws IOException if it can't create or write the file
	 */
	public void writeFile(File file, SequenceList list, DelayCallback delay) throws IOException, DelayAbortedException {
		throw new IOException("Writing GenBank files is not currently supported, sorry!");
	}

	/**
	 * Could 'file' be a GenBank file? That is the question. We 'test' for GenBank files
	 * by looking for a 'LOCUS' as the first non-blank line of the file.
	 */
	public boolean mightBe(File file) {
		try {
			BufferedReader	read	=	new BufferedReader(new FileReader(file));
			String 		line;
			while((line = read.readLine()) != null) {
				line = line.trim();
				if(!line.equals("")) {
					if(line.length() >= 5 && line.toUpperCase().substring(0,5).equals("LOCUS"))
						return true;
					else  {
						return false;
					}
				}
			}			
		} catch(IOException e) {
		}
		return false;
	}

	/**
	 * Read a GenBank file (from 'file') and return a SequenceList containing all the entries. 
	 * @throws FormatException if anything is in any whatsoeverway wrong with this format.
	 * @throws IOException if there was a problem accessing or reading the file specified.
	 * @throws DelayAbortedException if the user aborted the DelayCallback.
	 */
	public SequenceList readFile(File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {
		SequenceList list = new SequenceList();
		appendFromFile(list, file, delay);
		return list;
	}

	private String reverseComplement(String str) {
		// we flip' and complement
		char[] data = str.toUpperCase().toCharArray();
		char[] result = new char[data.length];

		for(int x = 0; x < data.length; x++) {
			char ch = data[x];

			// complement 'ch'
			ch = Sequence.complement(ch);

			// put it into 'result' in reverse
			result[data.length - 1 - x] = ch;
		}

		return new String(result);
	}


	/*
	 * Note: the following function uses the term 'CDS' a LOT. This is because I thought
	 * I'd be using the CDS information. For our purposes, however, the 'gene' information
	 * is a lot easier to deal with, so we're REALLY using that. Sorry about the confusion.
	 */
	public void appendFromFile(SequenceList list, File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException
	{
		list.lock();
		
		if(delay != null)
			delay.begin();

		Hashtable	hash_genes	=	new Hashtable();
		Hashtable	hash_cds	=	new Hashtable();	// Hash of String(seq full name) to (Hash of 
										// 	String(cds gene name) to String(coordinates 
										// 	in a custom "extractable" format)
										//
										// Sigh.
										//
										
		boolean		sequence_mode	=	false;		// are we reading in a sequence?
		boolean		features_mode 	=	false;		// are we in the 'features' section?

		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));	

			// There is an easier way to do this (using RandomAccessFile to jump to the end, etc.)
			// but I'm not sure it's more efficient.
			int total_lines = 0;
			
			while((reader.readLine() != null)) {
				total_lines++;
			}

			reader = new BufferedReader(new FileReader(file));	

			// And now, back to your regularly scheduled program.

			Pattern p_sequence = 	Pattern.compile("^\\s*\\d+\\s+([\\w\\s]+)\\s*$");
			Pattern p_version =	Pattern.compile("^VERSION\\s+(\\w{2}(?:[_])*\\d{6}\\.\\d+)\\s+GI:(\\d+)\\s*$");
			Pattern p_gi =		Pattern.compile("^VERSION\\s+.+\\s+GI:(\\d+)\\s*$");

			Pattern p_organism = 	Pattern.compile("^\\s*ORGANISM\\s+(.+)\\s*$");
			Pattern p_CDS =		Pattern.compile("^\\s*CDS\\s+(complement\\()*([<\\d]+)\\.\\.([\\d>]+)\\)*\\s*$", Pattern.CASE_INSENSITIVE);
			Pattern p_CDS_gene =	Pattern.compile("^\\s*/gene=\"(.*)\"\\s*$");
			Pattern p_CDS_product =	Pattern.compile("^\\s*/product=\"(.*)\"\\s*$");

			String currentName = "";
			StringBuffer currentSequence = new StringBuffer();
			Hashtable currentCDSs = new Hashtable();

			int line_no = 1;
			int interval = total_lines / 100;
			if(interval == 0)
				interval = 1;
			while(reader.ready()) {
				String line = reader.readLine().trim();

				// little formatting stuff
				if(line == null)
					break;


				// handle 'delay'
				if(delay != null && line_no % interval == 0)
					try {
						delay.delay(line_no, total_lines);
					} catch(DelayAbortedException e) {
						// cleanup will be handled by the 'finally' at the end of this method
						return;
					}
				line_no++;

				if(line.equals(""))
					continue;

				if(line.equals("//")) {
					// end of record!
					Sequence seq = null;
					try {
						seq = new Sequence(currentName.trim(), currentSequence.toString());
						list.add(seq);
					} catch(SequenceException e) {
						throw new FormatException("There was an error while processing the sequence '" + currentName + "' on line " + line_no + ": " + e);
					}

					if(!currentCDSs.isEmpty())
						hash_cds.put(seq, currentCDSs);

					currentName = "";
					currentSequence = new StringBuffer();
					currentCDSs = new Hashtable();

					sequence_mode = false;
					features_mode = false;
					continue;
				}

				if(sequence_mode) {
					Matcher m = p_sequence.matcher(line);
					if(m.matches()) {
						String sequence = m.group(1);
						// sequence includes spaces, which need to be removed.
						sequence = sequence.replaceAll("\\s", "");

						currentSequence.append(sequence);
					}
					continue;
				} else {
					Matcher m;
					// we are in no 'mode', as such
					// try to find a match for the 'ORGANISM'
					m = p_organism.matcher(line);

					if(m.matches()) {
						String organism = m.group(1);

						currentName = organism + " " + currentName;
						continue;
					}

					// try to find a match for the 'VERSION' line
					m = p_version.matcher(line);

					if(m.matches()) {
						String gb = m.group(1);
						String gi = m.group(2);

						currentName = currentName + " gi|" + gi + "|gb|" + gb + "|";
						continue;
					}

					// if the whole "VERSION" reg-ex didn't work,
					// (i.e. we couldn't understand the accession number)
					// we fall back to using a basic "how-much-is-that-GI-at-the-end-of-the-line" one.
					m = p_gi.matcher(line);
					if(m.matches()) {
						String gi = m.group(1);
						
						currentName = currentName + " gi|" + gi + "|";
						continue;
					}
				}

				if(features_mode) {
					// we handle feature specific stuff here.
					Matcher m;

					// I spy with my little eye ... a CDS entry!
					m = p_CDS.matcher(line);
					if(m.matches()) {
						String complement = m.group(1);
						String from = m.group(2);
						String to = m.group(3);
						String my_line = "";
						boolean open_quotes = false;

						while((my_line = reader.readLine().trim()) != null) {
							String gene = null; 

							if(open_quotes) {
								// if open_quotes, we have an unclosed '"' running
								// around. So we ignore EVERYTHING until we close
								// it.
								if(my_line.indexOf('"') != -1) {
									// it's here!
									open_quotes = false;
								}

								// in any case, we continue; this line is a quote infestation etc.
								continue;
							}

							if(my_line.length() == 0) {
								// this will happen if it's an empty line after the features.
								// unorthodox, but flexibility is good for you.
								continue;
							}

							if(my_line.charAt(0) != '/') {
								// if it's not a '/', we are OUT of this feature
								// aiyee!
								//
								// It should be noted that a GOTO would be
								// *brilliant* right about now.
								line = my_line;
								break;
							}

							if(my_line.indexOf('"') != -1) {
								// if there's a '"' in this line

								int count = 0;

								for(int x = 0; x < my_line.length(); x++) {
									if(my_line.charAt(x) == '"')
										count++;
								}

								if(count % 2 == 0) {	// even number
									// ignore
								} else {
									// quote mode!
									open_quotes = true;
								}
							}

							// look for '/gene' 
							m = p_CDS_gene.matcher(my_line);							
							if(m.matches()) {
								gene = m.group(1);
							}

							// look for '/product'
							m = p_CDS_product.matcher(my_line);

							if(m.matches()) {
								gene = m.group(1);
							}

							// if we have EITHER a /gene or a /product
							// on this line, we can do our 
							// adding-the-entry magick. 
							if(gene != null) {
								// watch out for the error case: two identically
								// named 'features' in one sequence!
								if(currentCDSs.get(gene) != null) {
									System.err.println("Warning: GenBankFile: '" + currentName + "' has two features named " + gene + ", using the last one.");
								}

								// count the gene/product name
								//System.err.println("Adding " + gene + " to list");
								if(hash_genes.get(gene) != null)
									hash_genes.put(gene, new Integer(((Integer)hash_genes.get(gene)).intValue() + 1));
								else
									hash_genes.put(gene, new Integer(1));

								// set up the cdsString for this particular gene; we'll store it in a hash shortly.
								String cdsString = "";

								if(complement == null)
									// non-complement sequence
									cdsString = " [taxondna_CDS:" + from + ":" + to + "]";
								else
									cdsString = " [taxondna_CDS_complement:" + from + ":" + to + "]";

								// store the cdsString into the currentCDSs hash.
								// This will be transfered to hash_cds once we hit the end of this sequence.
								currentCDSs.put(gene, cdsString);
							}
						}

						if(open_quotes)
							throw new FormatException("Double quotes not closed properly in file!");
					}
				}

				if(line.equals("ORIGIN")) {
					// begin sequence information!
					sequence_mode = true;	
					continue;
				}

				if(line.length() > 8 && line.substring(0,8).equals("FEATURES")) {
					features_mode = true;
					continue;
				}
			}

			// Okay, so we're out, BUT
			// what about anything left in currentName/currentSequence?!
			if(!currentSequence.toString().equals("")) {
				try {
					list.add(new Sequence(currentName, currentSequence.toString()));
				} catch(SequenceException e) {
					throw new FormatException("There was an error while processing the sequence '" + currentName + "' on line " + line_no + ": " + e);
				}	
			}

			// Hehe, now the fun REALLY starts
			//
			// Translation:		Now that we have "imported" the entire list, we need to get
			// 			rid of all the CDS tags. The Right Way of doing that is
			// 			determining which CDSs to keep, and which to remove, and
			// 			then do the chop-chop on the list itself.

			// We need UI control again.
			if(delay != null)
				delay.end();

			delay = null;

			// Now here's the dodgy thing
			// We are a data-only part of the program, so we're not really supposed
			// to interact with the user at ALL, except through exceptions or the
			// like.
			//
			// The best compromise I can come up with in my present caffeine-deprived
			// state is, we have a corresponding Module called CDScutter, which we
			// will directly invoke. CDScutter will handle the interaction, the CDS
			// cutting, etc.
			//
			// It's not much, but it'll have to do.
			//
			Class class_cdsexaminer = null;
			boolean keep_sequences_without_CDS = false;
			try {
				class_cdsexaminer = ClassLoader.getSystemClassLoader().loadClass("com.ggvaidya.TaxonDNA.Modules.CDSExaminer");
				Class[] signature = new Class[2];
				signature[0] = String.class;
				signature[1] = Hashtable.class;

				Object[] args = new Object[2];
				args[0] = "genes";
				args[1] = (Object) hash_genes;

				args = (Object[]) class_cdsexaminer.getDeclaredMethod("checkHashOfCDSs", signature).invoke(null, args);
				
				keep_sequences_without_CDS = ((Boolean)args[0]).booleanValue();
				hash_genes = ((Hashtable)args[1]);
			} catch(ClassNotFoundException e) {
				throw new FormatException("An essential component of TaxonDNA (the CDS Examiner) was not found. Please ensure that your TaxonDNA setup is not missing any files.\n\nThe technical description is as follows: " + e);
			} catch(Exception e) {
				throw new FormatException("There have been strange changes in the CDS Examiner since installation. This is probably a programming error. Please inform the programmers at [http://taxondna.sf.net/].\n\nThe technical description is as follows: " + e);
			}

			// Now, we run through the list, deleting any [taxondna_cds...] tags
			// *Except* those mentioned in the hash_genes as returned to us by CDSExaminer
			Iterator i = list.iterator();
			Pattern p_taxondna_cds = Pattern.compile("\\[taxondna_CDS(_complement)*:(.+?):(.+?)\\]");

			while(i.hasNext()) {
				Sequence seq = (Sequence)i.next();
				Hashtable cdss = (Hashtable) hash_cds.get(seq); 
					
				boolean fixed_one_in_this_sequence = false;

				if(cdss != null) {
					Iterator iCDS = cdss.keySet().iterator();
					while(iCDS.hasNext()) {
						String gene = (String)iCDS.next();
						String code = (String)cdss.get(gene);

						Matcher m = p_taxondna_cds.matcher(code);
	
						if(m.find()) {
							String complement = m.group(1);
							String from = m.group(2);
							String to = m.group(3);

							// is this gene in the list?
							if(!fixed_one_in_this_sequence && hash_genes.get(gene) != null) {
								int i_from = 0;
								int i_to = 0;
						
								if(from.indexOf("<") != -1) {
									i_from = Integer.parseInt(from.substring(from.indexOf("<")+1));
								} else
									i_from = Integer.parseInt(from);
	
								if(to.indexOf(">") != -1) {
									i_to = Integer.parseInt(to.substring(to.indexOf(">")+1));
								} else
									i_to = Integer.parseInt(to);

								int old_length = seq.getLength();
								String new_sequence = seq.getSequence().substring(i_from - 1, i_to);
		
								if(complement != null)
									new_sequence = reverseComplement(new_sequence);
	
								try {
									seq.changeSequence(new_sequence);
								} catch(SequenceException e) {
									throw new FormatException("I couldn't cut sequence " + seq + "; please check that the CDS of " + gene + " from " + from + " to " + to + " is actually valid.");	
								}
	
								String complementary_str = "";
								if(complement != null)
									complementary_str = " complementary";
	
								seq.changeName(seq.getFullName() + " (cut '" + gene + "' from " + from + " to " + to + complementary_str + "; the sequence used to be " + old_length + " bp long)"); 
	
								// we can't find more than one gene in any given sequence - sorry!
								fixed_one_in_this_sequence = true;
							}
						}
					}	
				}

				if(!keep_sequences_without_CDS && !fixed_one_in_this_sequence) {
					// we couldn't find a match in this sequence! 
					// Best get rid of the Sequence entirely?

					i.remove();
				}				
			}	
		} finally {
			if(delay != null)
				delay.end();

			list.setFile(file);
			list.setFormatHandler(this);
			list.unlock();
		}
	}

	/**
	 * Tests the GenBankFile class extensively so that bugs don't creep in.
	 */
	public void test(TestController testMaster, DelayCallback delay) throws DelayAbortedException {
		testMaster.begin("DNA.formats.GenBankFile");

		File coi_gb = testMaster.file("DNA/formats/genbankfile/coii.gb");
		File coi_fasta = testMaster.file("DNA/formats/genbankfile/coii.fasta");		

		GenBankFile gbf = new GenBankFile();

		testMaster.beginTest("Recognize a file as being a GenBank file");
			if(gbf.mightBe(coi_gb))
				try {
					FastaFile ff = new FastaFile();

					int count = gbf.readFile(coi_gb, delay).count();
					int real_count = ff.readFile(coi_fasta, delay).count();

					if(count == real_count)
						testMaster.succeeded();
					else	
						testMaster.failed("I got back " + count + " sequences instead of " + real_count + " (from the FASTA file)");

				} catch(IOException e) {
					testMaster.failed("There was an IOException reading a file: " + e);
				} catch(SequenceException e) {
					testMaster.failed("There was a SequenceException reading a file: " + e);
				} catch(FormatException e) {
					testMaster.failed("There was a FormatException reading a file: " + e);
				}

			else
				testMaster.failed(coi_gb + " was not recognized as a GenBank file");

/*
		testMaster.beginTest("Recognize a file generated by MEGA export from a FASTA file as a MEGA file");
			File test2 = testMaster.file("DNA/formats/megafile/test_mega2.txt");
			if(ff.mightBe(test2))
				try {
					if(ff.readFile(test2, delay).count() == 10)
						testMaster.succeeded();
				} catch(IOException e) {
					testMaster.failed("There was an IOException reading " + test2 + ": " + e);
				} catch(SequenceException e) {
					testMaster.failed("There was a SequenceException reading " + test2 + ": " + e);
				} catch(FormatException e) {
					testMaster.failed("There was a FormatException reading " + test2 + ": " + e);
				}


			else
				testMaster.failed(test + " was not recognized as a MEGA file");
			

		testMaster.beginTest("Recognize other files as being non-MEGA");
			File notfasta = testMaster.file("DNA/formats/megafile/test_nonmega1.txt");
			if(notfasta.canRead() && !ff.mightBe(notfasta))
				testMaster.succeeded();
			else
				testMaster.failed(notfasta + " was incorrectly identified as a MEGA file");

		// err, skip this last test
		// IT DOESNT WORK
		testMaster.done();
		return;
*/
/*

		testMaster.beginTest("Write out a MEGA file, then read it back in (twice!)");
			File input = testMaster.file("DNA/formats/megafile/test_megacopy.txt");
			File success = testMaster.file("DNA/formats/megafile/test_megacopy_success.txt");
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
						"I read a MEGA file from " + input + ", then wrote it to '" + output2 + "', but they aren't identical"
					);
			} catch(IOException e) {
				testMaster.failed(
					"I read a MEGA file from " + input + ", then wrote it to '" + output2 + "', but I got an IOException: " + e
					);
			} catch(SequenceException e) {
				testMaster.failed(
					"I read a MEGA file from " + input + ", then wrote it to '" + output2 + "', but I got a SequenceListException: " + e
					);
			} catch(FormatException e) {
				testMaster.failed(
					"I read a MEGA file from " + input + ", then wrote it to '" + output2 + "', but I got a SequenceListException: " + e
					);
			}

		testMaster.done();
	*/
	}
}
