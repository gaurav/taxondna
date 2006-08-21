/**
 * NexusFile allows you to read and write Nexus files. I'm going to try
 * to replicate the format from
 * 	Maddison, D. et. al. (1997). NEXUS: An extensible file format
 * 	for systematic information. Syst. Biol. 46(4): 590-621.
 * Writing will be written first, so this program might have a
 * *little* more difficulty reading Nexus files than it does writing
 * them. Also, to simplify things, we always "write fresh" - we
 * forget that we read out of a file, and write back exactly
 * whatever we got out of it. 
 *
 * TODO:
 * 1.	Nexus has a different way of handling polymorphisms: [AT]
 * 	instead of one letter shortages. So we need to convert
 * 	it both ways (right now, we convert NEITHER) 
 * 2.	'block CHARACTERS: FORMAT symbols'
 *
 */

/*
    TaxonDNA
    Copyright (C) 2005 Gaurav Vaidya
    
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

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;

public class NexusFile implements FormatHandler {
		// what is the maximum lengh of taxon names allowed?
	private int MAX_TAXON_LENGTH = 	32;
		// when sequences get bigger than INTERLEAVE_AT, we'll
		// interlace to avoid confusion. Of course, we can't
		// interlace unless we interlace EVERYTHING, so we
	private int INTERLEAVE_AT = 	80;	
	
	/**
	 * Returns a valid Mega OTU (Operation Taxonomic Unit), that is, a taxon name.
	 */
	public String getMegaOTU(String name, int len) {
		// Rule #1: the name must start with '[A-Za-z0-9\-\+\.]'
		char first = name.charAt(0);
		if(
		 	(first >= 'A' && first <= 'Z') ||
			(first >= 'a' && first <= 'z') ||
			(first >= '0' && first <= '9') ||
			(first == '-') ||
			(first == '+') ||
			(first == '.')
		) {
			// it's all good!
		} else {
			name = "." + name;
		}

		// Rule #2: strange characters we'll turn into '_' 
		name = name.replaceAll("[^a-zA-Z0-9\\-\\+\\.\\_\\*\\:\\(\\)\\|\\\\\\/]", "_");

		// Rule #3: spaces we'll turn into '_'
		name = name.replace(' ', '_');
		
		// Rule #4: truncate to 'len'
		int size = name.length();
		if(size <= len)
			return name;
		else
			return name.substring(0, len);
	}

	/**
	 * Returns the short name of this file format.
	 */
	public  String getShortName() {		return "NEXUS";		}

	/**
	 * Returns the full name of this file format handler. E.g. "Nexus file format v2 and below".
	 * You ought to put in something about what versions of the software you support.
	 * But not too long: think about whether you could display it in a list.
	 */
	public  String getFullName() {		return "Partial NEXUS support"; 	}
	
	/**
	 * Read this file into the specified SequenceList. This will read all the files straight into
	 * this sequence list, in the correct order.
	 * 
	 * @throws IOException if there was an error doing I/O
	 * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
	 * @throws FormatException if there was an error in the format of the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public SequenceList readFile(File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {
		SequenceList sl = new SequenceList();
		sl.lock();	// retarded.
		appendFromFile(sl, file, delay);
		sl.unlock();
		return sl;
	}

	/**
	 * Append this file to the specified SequenceList. This will read in all the sequences from
	 * the file and append them directly onto the end of this SequenceList.
	 *
	 * @throws IOException if there was an error doing I/O
	 * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
	 * @throws FormatException if there was an error in the format of the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public void appendFromFile(SequenceList appendTo, File fileFrom, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {
		// set up the delay
		if(delay != null)
			delay.begin();

		appendTo.lock();

		try {
			BufferedReader reader = new BufferedReader(new FileReader(fileFrom));

			// count lines
			int count_lines = 0;
			while(reader.ready()) {
				reader.readLine();
				count_lines++;
			}

			reader = new BufferedReader(new FileReader(fileFrom));
			StreamTokenizer tok = new StreamTokenizer(reader);

			tok.ordinaryChar('/');	// this is the default StringTokenizer comment char, so we need to set it back to nothingness

			// turn off parseNumbers()
		        tok.ordinaryChar('.');
		        tok.ordinaryChar('-');
		        tok.ordinaryChars('0','9');
		        tok.wordChars('.','.');
		        tok.wordChars('-','-');
		        tok.wordChars('0','9');


			tok.wordChars('_', '_');
					// we need to replace this manually. they form one 'word' in NEXUS.

			tok.ordinaryChar('[');	// We need this to be an 'ordinary' so that we can use it to discriminate comments
			tok.ordinaryChar(']');	// We need this to be a 'word' so that we can use it to discriminate comments
			tok.ordinaryChar(';');	// We need this to be a 'word' so that we can use it to discriminate comments
		
			// states
			int 		commentLevel = 		0;
			boolean		newCommand =		true;
			boolean		inDataBlock =		false;
			boolean		inStrangeBlock =	false;		// strange -> i.e. unknown to us, foreign.
			char		missingChar =		'?';
			char		gapChar =		':';		// this is against the standard, but oh well

			// TODO: we need to add the 'gap' chars into tok so they come out okay.
		        tok.wordChars(gapChar,gapChar);
		        tok.wordChars(missingChar,missingChar);

			// flags
			boolean isDatasetInterleaved = false;

			while(true) {
				int type = tok.nextToken();
				
				// is it an ordinary char?
				if(type == '[')
					commentLevel++;

				if(type == ']')
					commentLevel--;

				if(commentLevel > 0)
					continue;

				// semi-colons indicate end of line. mostly, anybody interested should have already flagged this off.
				if(type == ';') {
					newCommand = true;
					continue;
				}

				// break at end of file
				if(type == StreamTokenizer.TT_EOF) {
					break;
				}

				// is it a word?
				else if(type == StreamTokenizer.TT_WORD) {
					String str = tok.sval;

					// if we are in a foreign block, we ONLY watch out for the 'END;'.
					// This way, foreign blocks can safely contain 'BEGIN foo' without
					// tripping us over.
					if(inStrangeBlock) {
						if(str.equalsIgnoreCase("END")) {
							System.err.println("Leaving strange block");
							if(newCommand && tok.nextToken() == ';') {
								// okay, it's an 'END;'
								// and a new command ... so ';END;'
								inStrangeBlock = false;
								continue;
							}
						}
					}

					if(inDataBlock) {
						if(str.equalsIgnoreCase("INTERLEAVE")) {
							// turn interleaving on
							isDatasetInterleaved = true;
						}

						if(str.equalsIgnoreCase("MATRIX")) {
							System.err.println("Entering the matrix");
							// okay, at this point, we import in the entire friggin'
							// data matrix. It might be interleaved (see boolean 'interleave') 
							Hashtable hash_names = new Hashtable();		// String name -> Sequence
							while(true) {
								type = tok.nextToken();

								if(tok.sval != null)
									System.err.println("Word in Matrix: " + tok.sval);

								if(tok.nval != 0)
									System.err.println("Numb in Matrix: " + tok.nval);

								if(type == ';')
									break;

								// is it an ordinary char?
								else if(type == '[')
									commentLevel++;

								else if(type == ']')
									commentLevel--;

								else if(commentLevel > 0)
									continue;

								else if(type == StreamTokenizer.TT_WORD) {
									String name = tok.sval;

									System.err.println("Processing: " + name);

									// put spaces back
									name = name.replace('_', ' ');		// we do NOT support '. Pfft.

									// get the sequence
									type = tok.nextToken();

									if(type == StreamTokenizer.TT_WORD) {
										tok.sval = tok.sval.replace(gapChar, '-');
										tok.sval = tok.sval.replace(missingChar, '?');
										try {
											Sequence seq = null;
											if(!isDatasetInterleaved || hash_names.get(name) == null) {
												// doesn't exist, just add it
												seq = new Sequence(name, tok.sval);
												appendTo.add(seq);
												hash_names.put(name, seq);
											} else {
												// it DOES exist, append it
												seq = (Sequence) hash_names.get(name);
												seq.changeSequence(seq.getSequence() + tok.sval);
											}
										} catch(SequenceException e) {
											throw new FormatException("There is an error in line " + tok.lineno() + ": the sequence for taxon '" + name + "' is invalid: " + e);
										}
									} else if(type == ';') {
										throw new FormatException("There is an error in line " + tok.lineno() + ": I can't currently handle comments inside a MATRIX, sorry!");
									} else {
										throw new FormatException("There is an error in line " + tok.lineno() + ": Unexpected " + (char) type + ".");
									}

								} else {
									throw new FormatException("There is an error in line " + tok.lineno() + ": Unexpected " + (char) type + ".");
								}
							}
							continue;
						}	
						if(str.equalsIgnoreCase("END")) {
							System.err.println("Leaving data block");
							if(newCommand && tok.nextToken() == ';') {
								// okay, it's an 'END;'
								// and a new command ... so ';END;'
								inDataBlock = false;
								continue;
							}
						}
					}
					
					// the BEGIN command
					if(str.equalsIgnoreCase("BEGIN")) {
						// begin what?
						if(tok.nextToken() == StreamTokenizer.TT_WORD) {
							String beginWhat = tok.sval;	

							System.err.println("Begin block: " + beginWhat + ".");
							
							if(beginWhat.equalsIgnoreCase("DATA"))
								inDataBlock = true;
							else if(beginWhat.equalsIgnoreCase("CHARACTERS"))
								inDataBlock = true;
								// the reference says they *are* identical
								// (except that NEWTAXA is implicit in DATA)
								// TODO: we might want to care about this.
								// you know. to be anal, and all that.
							else
								inStrangeBlock = true;
							
							continue;
						} else {
							// something is wrong!
							throw new FormatException("There is an error in line " + tok.lineno() + ": BEGIN is specified without a valid block name.");
						}
					}

					newCommand = false;		// this will be reset at the end of the line -- i.e, by ';'
				}
			}
		} finally {
			if(delay != null)
				delay.end();
			appendTo.unlock();
		}
		
		appendTo.setFile(fileFrom);
		appendTo.setFormatHandler(this);
	}

	/**
	 * Gets the integer value of the key=value pair from the StreamTokenizer.
	 * I assume you've already got the 'key', so I'll get the '=' token,
	 * followed by a TT_WORD, and return the Integer.parseInt of that word.
	 *
	 * I'll return Integer.MIN_VALUE if something goes wrong.
	 *
	 * @throws IOException if the tokenizer gets IO issues.
	 */
	private int getIntValueOfKey(StreamTokenizer tok) throws IOException {
		if(tok.nextToken() == '=') {
			// all good so far
			if(tok.nextToken() == StreamTokenizer.TT_WORD) {
				// yay! we got the number!
				return Integer.parseInt(tok.sval);
			}
		}

		// something went wrong
		return Integer.MIN_VALUE;
	}
	
	/**
	 * Writes the content of this sequence list into a file. The file is
	 * overwritten. The order of the sequences written into the file is
	 * guaranteed to be the same as in the list.
	 *
	 * @throws IOException if there was a problem creating/writing to the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public void writeFile(File file, SequenceList set, DelayCallback delay) throws IOException, DelayAbortedException {
		boolean interleaved = false;

		set.lock();
		
		// it has begun ...
		if(delay != null)
			delay.begin();	

		// write out a 'preamble'
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));

		writer.println("#NEXUS\n");
		writer.println("BEGIN TAXA;");
		writer.println("\tDIMENSIONS NTAX=" + set.count() + ";");
		// TAXLABELS - otherwise, no point of having a TAXA block
		/*
		 * The following piece of code has to:
		 * 1.	Figure out VALID, UNIQUE names to output.
		 * 2.	Without hitting up against PAUP* and MacClade's specs (we'll 
		 * 	assume 32 chars for now - see MAX_TAXON_LENGTH - and work
		 * 	around things when we need to).
		 * 3.	Do interlacing, something no other part of the program does yet.
		 * 	(Unless I wrote it for Mega and forgot. See INTERLEAVE_AT)
		 */

		Hashtable names = new Hashtable();		// Hashtable(Strings) -> Sequence	of all the names currently in use
		Vector vec_names = new Vector();		// Vector(String)
		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			String name = seq.getSpeciesName(MAX_TAXON_LENGTH);
			name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
			name = name.replace(' ', '_');		// we do NOT support '. Pfft.

			int no = 2;
			while(names.get(name) != null) {
				int digits = 5;
				if(no > 0 && no < 10)		digits = 1;
				if(no >= 10 && no < 100)	digits = 2;
				if(no >= 100 && no < 1000)	digits = 3;
				if(no >= 1000 && no < 10000)	digits = 4;

				name = seq.getSpeciesName(MAX_TAXON_LENGTH - digits);
				name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
				name = name.replace(' ', '_');		// we do NOT support '. Pfft.
				name += "_" + no;

				no++;

				if(no == 10000) {
					// this has gone on long enough!
					throw new IOException("There are 9999 sequences named '" + seq.getSpeciesName(MAX_TAXON_LENGTH) + "', which is the most I can handle. Sorry. This is an arbitary limit: please let us know if you think we set it too low.");
				}
			}
			
			names.put(name, seq);
			vec_names.add(name);
		}

		writer.print("\tTaxLabels ");
		i = vec_names.iterator();
		while(i.hasNext()) {
			String name = (String) i.next();

			writer.print(name + " ");
		}

		writer.println(";\n");

		writer.println("END;\n");
		
		writer.println("BEGIN DATA;");		// DATA because I *think* CHARACTERS is not allowed
							// to define its own Taxa, and I really can't be arsed.
							// Maybe later ... ? 
							
							// the following is actually somewhat controversial
							// this *will* mess things up if all strings AREN'T
							// 'maxLength()' long.
							//
							// What to do?
							//
							// Options:	silently increase size to maxLength
							// 		alert the user, then increase size etc.
							//
							// TODO tomorrow.
							//
		writer.println("\tDIMENSION NCHAR=" + set.getMaxLength() + ";");

							// The following is standard 'TaxonDNA' speak.
							// It's just how we do things around here.
							// So we can hard code this.
		
		writer.println("\tFORMAT DATATYPE=DNA MISSING=? GAP=-;");
		if(set.getMaxLength() > INTERLEAVE_AT) {
			interleaved = true;
			writer.println("\tINTERLEAVE;\n");
		}

		writer.println("\tMATRIX");

		// if we're not interleaved, we're done at this point.
		// if we ARE interleaved, we actually need to write out the sequences
		if(interleaved) {
			// go over all the 'segments'
			for(int x = 0; x < set.getMaxLength(); x+= INTERLEAVE_AT) {
				Iterator i_names = vec_names.iterator();

				// go over all the taxa 
				while(i_names.hasNext()) {
					String name = (String) i_names.next();
					Sequence seq = (Sequence) names.get(name);
					Sequence subseq = null;

					int until = 0;

					try {
						until = x + INTERLEAVE_AT;

						// thanks to the loop, we *will* walk off the end of this 
						if(until > seq.getLength()) {
							until = seq.getLength();
						}

						subseq = seq.getSubsequence(x + 1, until);
					} catch(SequenceException e) {
						delay.end();
						throw new IOException("Could not get subsequence (" + (x + 1) + ", " + until + ") from sequence " + seq + ". This is most likely a programming error.");
					}

					writer.println(pad_string(name, MAX_TAXON_LENGTH) + " " + subseq.getSequence() + " [" + subseq.getLength() + ":" + (x + 1) + "-" + (until) + "]");
				}

				writer.println("");	// print a blank line
			}
		}

		// wrap up.
		writer.println(";");
		
		writer.println("END;");

		writer.close();

		// it's over ...
		if(delay != null)
			delay.end();

		set.unlock();
	}

	/* Pad a string to a size */
	private String pad_string(String x, int size) {
		StringBuffer buff = new StringBuffer();
		
		if(x.length() < size) {
			buff.append(x);
			for(int c = 0; c < (size - x.length()); c++)
				buff.append(' ');
		} else if(x.length() == size)
			return x;
		else	// length is LESS than size, so we actually need to 'play tricks'
			return x.substring(x.length() - 3) + "___";

		return buff.toString();
	}

	/**
	 * Checks to see if this file *might* be of this format. Good for internal loops.
	 * 
	 * No exceptions: implementors, please swallow them all up. If the file does not
	 * exist, it's not very likely to be of this format, is it? 
	 */
	public boolean mightBe(File file) {
		try {
			BufferedReader buff = new BufferedReader(new FileReader(file));

			while(buff.ready()) {
				String str = buff.readLine().trim();

				if(str.equals(""))
					continue;

				if(str.equalsIgnoreCase("#nexus")) {
					// we find signature!
					return true;
				} else {
					return false;
				}
			}

			return false;
		} catch(IOException e) {
			return false;
		}
	}
}

