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

public class NexusFile extends BaseFormatHandler {
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
		FormatHandlerEvent evt = new FormatHandlerEvent(fileFrom, this, appendTo);

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

			// let's go!
			reader = new BufferedReader(new FileReader(fileFrom));
			StreamTokenizer tok = new StreamTokenizer(reader);
			// let's pre-read the #NEXUS line
			if(tok.nextToken() != '#')
				throw new FormatException("This file does not have a Nexus header (it doesn't start with '#')! Are you sure it's a Nexus file?");

			tok.nextToken();

			if(tok.sval == null || !tok.sval.equalsIgnoreCase("nexus")) {
				throw new FormatException("This file does not have a Nexus header (it doesn't start with '#nexus')! Are you sure it's a Nexus file?");
			}

			tok.ordinaryChar('/');	// this is the default StringTokenizer comment char, so we need to set it back to nothingness

			// turn off parseNumbers()
		        tok.ordinaryChar('.');
		        tok.ordinaryChar('-');
		        tok.ordinaryChars('0','9');
		        tok.wordChars('.','.');
		        tok.wordChars('-','-');
		        tok.wordChars('0','9');

		        tok.wordChars('|','|');		// this is officially allowed in Nexus, and pretty convenient for us
			tok.wordChars('_', '_');
					// we need to replace this manually. they form one 'word' in NEXUS.

			tok.ordinaryChar('[');	// We need this to be an 'ordinary' so that we can use it to discriminate comments
			tok.ordinaryChar(']');	// We need this to be a 'word' so that we can use it to discriminate comments
			tok.ordinaryChar(';');	// We need this to be a 'word' so that we can use it to distinguish commands 
		
			// states
			int 		commentLevel = 		0;
			boolean		inStrangeBlock =	false;		// strange -> i.e. unknown to us, foreign.
			boolean		newCommand =		true;

			// WARNING:	newCommand is reset at the BOTTOM of the while loop
			// 		Only use 'continue' if you've slurped up an ENTIRE
			// 		command (including the ';')
			while(true) {
				/* Before anything else, do the delay */
				if(delay != null) 
					delay.delay(tok.lineno(), count_lines);

				/* Now ... to business! */
				int type = tok.nextToken();
				
				// break at end of file
				if(type == StreamTokenizer.TT_EOF)
					break;
				
				// is it a comment?
				if(type == '[')
					commentLevel++;

				if(type == ']')
					commentLevel--;

				if(commentLevel > 0)
					continue;

				// semi-colons indicate end of line.
				// some commands use this to determine the end of command 
				if(type == ';') {
					newCommand = true;
					continue;
				}

				// Look out for END
				if(type == StreamTokenizer.TT_WORD) {
					String str = tok.sval;

					if(str.equalsIgnoreCase("END")) {
						//System.err.println("Leaving strange block");
						//if(newCommand && tok.nextToken() == ';') {
							// okay, it's an 'END;'
							// and a new command ... so ';END;'
							inStrangeBlock = false;

							continue;
//						} 
					}

					// the BEGIN command
					else if(str.equalsIgnoreCase("BEGIN")) {
						// begin what?
						if(tok.nextToken() == StreamTokenizer.TT_WORD) {
							String beginWhat = tok.sval;	
							int nextChar = tok.nextToken();

							if(nextChar != ';')
								throw formatException(tok, "There is a strange character ('" + nextChar + "') after the BEGIN " + beginWhat + " command! How odd.");

							if(beginWhat.equalsIgnoreCase("DATA") || beginWhat.equalsIgnoreCase("CHARACTERS"))
								blockData(appendTo, tok, evt, delay, count_lines);
								// the reference says they *are* identical
								// (except that NEWTAXA is implicit in DATA)
								// TODO: we might want to care about this.
								// you know. to be anal, and all that.
							else if(beginWhat.equalsIgnoreCase("SETS"))
								blockSets(appendTo, tok, evt, delay, count_lines);
							else
								inStrangeBlock = true;
						
						} else {
							// something is wrong!
							throw formatException(tok, "BEGIN is specified without a valid block name.");
						}
					}

					else {
						if(!inStrangeBlock) 
							throw formatException(tok, "Strange word '" + str + "' found! Is it one of yours?");
					}
				} else {
					// strange symbol (or ';') found
				}

				newCommand = false;		// this will be reset at the end of the line -- i.e, by ';'
			}
		} finally {
			if(delay != null)
				delay.end();
			appendTo.unlock();
		}
		
		appendTo.setFile(fileFrom);
		appendTo.setFormatHandler(this);
	}

	public FormatException formatException(StreamTokenizer tok, String message) {
		return new FormatException("Error on line " + tok.lineno() + ": " + message);
	}

	public void blockData(SequenceList appendTo, StreamTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
		throws FormatException, DelayAbortedException, IOException
	{
		boolean isDatasetInterleaved = false;
		boolean inFormatCommand = false;
		boolean inIgnoredCommand = false;
		boolean inMatrix = false;
		char missingChar =	'?';
		char gapChar =		'-';		// this is against the standard, but the GAPS
							// command should fix it, and it's a pretty nice
							// default for us. NEXUS says there is NO default,
							// but we're going to have one anyway, because
							// we're Radical and all.

		tok.wordChars(gapChar,gapChar);
	        tok.wordChars(missingChar,missingChar);

		Hashtable hash_names = new Hashtable();			// String name -> Sequence
		String name = null;

		int commentLevel = 0;
		boolean newCommand = true;

		while(true) {
			int type = tok.nextToken();
			String str = tok.sval;

			if(delay != null)
				delay.delay(tok.lineno(), count_lines);

			if(type == StreamTokenizer.TT_EOF) {
				// wtf?!
				throw formatException(tok, "I've reached the end of the file, and the DATA/CHARACTERS block has *still* not been closed! Please make sure the block is closed.");
			}

			if(type == ';')
				newCommand = true;

			if(type == '[' || type == ']') {
				if(type == '[')
					commentLevel++;

				if(type == ']')
					commentLevel--;

				continue;
			}
				
			if(commentLevel > 0)
				continue;

			if(inIgnoredCommand) {
				// we, err, ignore everything in such a command.
				if(newCommand) {
					inIgnoredCommand = false;
					continue;
				}
			} else if(inFormatCommand) {
				if(newCommand) {
					inFormatCommand = false;
					continue;
				}

				if(type == StreamTokenizer.TT_WORD) {
					str = tok.sval;

					if(str.equalsIgnoreCase("INTERLEAVE")) {
						// turn interleaving on
						isDatasetInterleaved = true;
					}
		
					else if(str.equalsIgnoreCase("DATATYPE")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'DATATYPE' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						if(!str.equalsIgnoreCase("DNA"))
							throw formatException(tok, "I can't understand DATATYPE '" + str + "'. I can only understand DATATYPE=DNA.");
					}

					else if(str.equalsIgnoreCase("GAP")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'GAP' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						if(str.length() > 1)
							throw formatException(tok, "I can't use more than one character as the GAP character. The file specifies: '" + str + "'");

						tok.ordinaryChar(gapChar);
						gapChar = str.charAt(0); 
						tok.wordChars(gapChar, gapChar);
					}
						
					else if(str.equalsIgnoreCase("MISSING")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'MISSING' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						if(str.length() > 1)
							throw formatException(tok, "I can't use more than one character as the MISSING character. The file specifies: '" + str + "'");

						tok.ordinaryChar(missingChar);
						missingChar = str.charAt(0); 
						tok.wordChars(missingChar, missingChar);
					} else {
						throw formatException(tok, "I found '" + str + "' in the FORMAT line of the DATA (or CHARACTERS) block. I don't understand " + str + " at the moment - I can only comprehend 'MISSING=x', 'GAP=x', 'DATATYPE=DNA' and 'INTERLEAVE'. Sorry!");
					}
				} else {
					throw formatException(tok, "Unexpected '" + (char)type + "' in FORMAT!");
				}

			} else if(inMatrix) {
				// are we done yet?
				if(type == ';') {
					if(name != null) {
						// but ... the sequence is incomplete!
						throw formatException(tok, "The sequence named '" + name + "' has no DNA sequence associated with it.");
					}
					inMatrix = false;
					continue;
				}

				else if(type == StreamTokenizer.TT_WORD) {
					if(name == null) {
						// put spaces back
						name = str.replace('_', ' ');		// we do NOT support '. Pfft.
					} else {
						String strseq = str;
						// fix up gaps and missings to TaxonDNA specs
						strseq = str.replace(gapChar, '-');
						strseq = strseq.replace(missingChar, '?');

						try {
							Sequence seq = null;
							if(!isDatasetInterleaved || hash_names.get(name) == null) {
								// doesn't exist, just add it
								seq = new Sequence(name, strseq);
								appendTo.add(seq);
								hash_names.put(name, seq);
							} else {
								// it DOES exist, append it
								seq = (Sequence) hash_names.get(name);
								seq.changeSequence(seq.getSequence() + strseq);
							}
						} catch(SequenceException e) {
							throw formatException(tok, "The sequence for taxon '" + name + "' is invalid: " + e);
						}

						name = null;
					}
				} else {
					throw formatException(tok, "Unexpected '" + (char) type + "' in matrix.");
				}
			} else {
				if(type == StreamTokenizer.TT_WORD) {

					if(str.equalsIgnoreCase("FORMAT")) {
						inFormatCommand = true;	
					}

					if(str.equalsIgnoreCase("MATRIX")) {
						inMatrix = true;
					}

					// Commands we ignore
					if(
							str.equalsIgnoreCase("DIMENSIONS") ||
							str.equalsIgnoreCase("ELIMINATE") ||
							str.equalsIgnoreCase("TAXLABELS") ||
							str.equalsIgnoreCase("CHARSTATELABELS") ||
							str.equalsIgnoreCase("CHARLABELS") ||
							str.equalsIgnoreCase("STATELABLES")
					) {
						inIgnoredCommand = true;
					}

					if(str.equalsIgnoreCase("END")) {
						//System.err.println("Leaving strange block");
						// Note: PAUP will spit out blocks with END without a terminating ';'.
//						if(newCommand && tok.nextToken() == ';') {
							// okay, it's an 'END;'
							// and a new command ... so ';END;'
							break;
//						} else {
//							throw formatException(tok, "I found something strange after the END! I can't just ignore it. I'm sorry.");
//						}
					}

				} else {
					// unknown symbol found
					//System.err.println("Last string (ish!): " + str);
					throw formatException(tok, "I found '" + (char)type + "' rather unexpectedly in the DATA/CHARACTERS block! Are you sure it's supposed to be here?");
				}
			}
			
			newCommand = false;
		}

		// only important in the DATA block
	        tok.ordinaryChar(gapChar);
	        tok.ordinaryChar(missingChar);
	}

	/**
	 * Processes the 'SETS' block.
	 */
	public void blockSets(SequenceList appendTo, StreamTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
		throws FormatException, DelayAbortedException, IOException
	{
		int commentLevel = 0;
		boolean newCommand = true;

		boolean inIgnoredCommand = false;

		while(true) {
			int type = tok.nextToken();
			String str = tok.sval;

			if(delay != null)
				delay.delay(tok.lineno(), count_lines);

			if(type == StreamTokenizer.TT_EOF) {
				// wtf?!
				throw formatException(tok, "I've reached the end of the file, and the SETS block has *still* not been closed! Please make sure the block is closed.");
			}

			if(type == ';')
				newCommand = true;

			if(type == '[' || type == ']') {
				if(type == '[')
					commentLevel++;

				if(type == ']')
					commentLevel--;

				continue;
			}
			
			if(commentLevel > 0)
				continue;

			// "serious" processing begins here
			if(inIgnoredCommand) {
				if(newCommand) {
					inIgnoredCommand = false;
					continue;
				}
			} else if(type == StreamTokenizer.TT_WORD) {
				//System.err.println("New command: " + str);

				// is it over?
				if(str.equalsIgnoreCase("END")) {
					//System.err.println("Leaving strange block");
//					if(newCommand && tok.nextToken() == ';') {
						// okay, it's an 'END;'
						// and a new command ... so ';END;'
						break;
//					} else {
//						throw formatException(tok, "I found something strange after the END! I can't just ignore it. I'm sorry.");
//					}
				} else if(str.equalsIgnoreCase("CHARSET")) {
					if((type = tok.nextToken()) != StreamTokenizer.TT_WORD) 
						throw formatException(tok, "Unexpected symbol '" + (char)type + "' found after 'CHARSET'. This doesn't look like the name of a CHARSET to me!");

					String name = tok.sval;

					//System.err.println("Charset " + name + "!");

					type = tok.nextToken();
					if((char)type != '=') {
						// it might be STANDARD or VECTOR
						// god bless my anal soul

						if(type != StreamTokenizer.TT_WORD)
							throw formatException(tok, "Unexpected '" + (char) type + "' after the '=' in CHARSET " + name + ".");

						str = tok.sval;
						if(str.equalsIgnoreCase("STANDARD")) {
							// okay, we support it
						} else if(str.equalsIgnoreCase("VECTOR")) {
							throw formatException(tok, "Sorry, we don't support VECTOR CHARSETs at the moment! (" + name + " is defined as being a vector charset)");
						} else {
							throw formatException(tok, "Unexpected '" + str + "' after the '=' in CHARSET " + name + ".");
						}
					}
					
					// now we have to start 'picking up' the ranges in the CHARSET
					// and there might be more than one!
					//
					// basic format: 
					// 	1.	(\d+)\s :		$1 belongs to charset X
					// 	2.	(\d+)\s*\-\s*(\d+)\s :	$1,$1+1,..$2 belongs to charset X
					// 	3.	;			It's all over!
					//
					// 	ANYTHING else 'REMAINDER', 'ALL', (\d+)\/, are ALL invalid
					// 	and WILL ignore ALL of them. hmpf.
					int from = -1;
//					int to = -1;
					boolean expectingATo = false;
					while(true) {
						type = tok.nextToken();

						if((char)type == ';') {
							// yay! it's over!
							break;
						} else if((char) type == '-') {
							expectingATo = true;
							continue;
						} else if(type == StreamTokenizer.TT_WORD) {
							str = tok.sval;
							int num = 0;
					
							// are we a number? non-numbers are teh ENEMYIES
							try {
								num = Integer.parseInt(str);	
							} catch(NumberFormatException e) {
								throw formatException(tok, "I found a non-number in CHARSET " + name + " (the non-number is '" + str + "'). I can't currently understand 'ALL' or 'REMAINDER' commands. Can you please remove them from the source file if that's at all possible?");
							}

							// are we expecting a 'to'? if we are, fire the event
							if(expectingATo) {
								// well, we've got a 'to' now!
								fireEvent(evt.makeCharacterSetFoundEvent(name, from, num));

								from = -1;
								expectingATo = false;
							} else {
								// we are NOT expecting a to
								// i.e. from is a single character event, UNLESS it's just -1!
								if(from != -1)
									fireEvent(evt.makeCharacterSetFoundEvent(name, from, from));	
								
								// now, we don't know if 'num' is a character or a section
								// so ...
								from = num;
							}
						} else {
							throw formatException(tok, "Unexpected '" + (char)type + "' in CHARSET " + name + ".");
						}
					}
				// commands we ignore in SETS
				} else if(
						str.equalsIgnoreCase("STATESET") ||
						str.equalsIgnoreCase("CHANGESET") ||
						str.equalsIgnoreCase("TAXSET") ||
						str.equalsIgnoreCase("TREESET") ||
						str.equalsIgnoreCase("CHARPARTITION") ||
						str.equalsIgnoreCase("TAXPARTITION") ||
						str.equalsIgnoreCase("TREEPARTITION")
				) {
					inIgnoredCommand = true;
				} else {
 					throw formatException(tok, "Unknown word/command '" + str + "' found in the SETS block.");
				}
 			} else {
				throw formatException(tok, "Unexpected symbol '" + (char)type + "' found in the SETS block.");
			}
			
			newCommand = false;
		}

		// final cleanup, if any
	}

	/**
	 * Gets the String 'value' of the key=value pair from the StreamTokenizer.
	 * I assume you've already got the 'key', so I'll get the '=' token,
	 * followed by a TT_WORD, and return the Integer.parseInt of that word.
	 *
	 * I'll return null if something went wrong.
	 *
	 * @throws IOException if the tokenizer gets IO issues.
	 */
	private String getValueOfKey(StreamTokenizer tok) throws IOException {
		if(tok.nextToken() == '=') {
			int type = tok.nextToken();
			// all good so far
			if(type == StreamTokenizer.TT_WORD) {
				// yay! we got a word
				return tok.sval;
			}

			// sometimes, we will have a weird symbol
			// luckily, this only really happens for
			// fields like 'GAP' and 'MISSING', which
			// should really have only one character
			// anyway.
			if(
					!Character.isWhitespace((char)type)	&&
					!Character.isISOControl((char)type)
			) {
				char data[] = {(char)type};
				return new String(data);
			}
		}

		// something went wrong
		return null;
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
		writeNexusFile(file, set, INTERLEAVE_AT, "", delay);
	}
	
	/**
	 * A species NexusFile-only method to have a bit more control over how
	 * the Nexus file gets written.
	 *
	 * @param interleaveAt Specifies where you want to interleave. Note that NexusFile.INTERLEAVE_AT will be entirely ignored here, and that if the sequence is less than interleaveAt, it will not be interleaved at all. '-1' turns off all interleaving (flatline), although you can obviously use a huge value (999999) to get basically the same thing.
	 * @param otherBlocks We put this into the file at the very bottom. It should be one or more proper 'BLOCK's, unless you really know what you're doing.
	 */
	public void writeNexusFile(File file, SequenceList set, int interleaveAt, String otherBlocks, DelayCallback delay) throws IOException, DelayAbortedException {
		boolean interleaved = false;
		
		if(interleaveAt > 0)
			interleaved = true;

		set.lock();
		
		// it has begun ...
		if(delay != null)
			delay.begin();

		// write out a 'preamble'
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));

		writer.println("#NEXUS");
		writer.println("[Written by TaxonDNA " + Versions.getTaxonDNA() + " on " + new Date() + "]");
		writer.println("");
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

			String name = seq.getFullName(MAX_TAXON_LENGTH);
			name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
			name = name.replace(' ', '_');		// we do NOT support '. Pfft.

			int no = 2;
			while(names.get(name) != null) {
				int digits = 5;
				if(no > 0 && no < 10)		digits = 1;
				if(no >= 10 && no < 100)	digits = 2;
				if(no >= 100 && no < 1000)	digits = 3;
				if(no >= 1000 && no < 10000)	digits = 4;

				name = seq.getFullName(MAX_TAXON_LENGTH - digits);
				name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
				name = name.replace(' ', '_');		// we do NOT support '. Pfft.
				name += "_" + no;

				no++;

				if(no == 10000) {
					// this has gone on long enough!
					throw new IOException("There are 9999 sequences named '" + seq.getFullName(MAX_TAXON_LENGTH) + "', which is the most I can handle. Sorry. This is an arbitary limit: please let us know if you think we set it too low.");
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
		writer.println("\tDIMENSIONS NTAX=" + set.count() + " NCHAR=" + set.getMaxLength() + ";");

							// The following is standard 'TaxonDNA' speak.
							// It's just how we do things around here.
							// So we can hard code this.
		
		writer.print("\tFORMAT DATATYPE=DNA MISSING=? GAP=- ");
		if(set.getMaxLength() > interleaveAt) {
			interleaved = true;
			writer.print("\tINTERLEAVE");
		}
		writer.println(";");

		writer.println("\tMATRIX");

		if(!interleaved) {
			Iterator i_names = vec_names.iterator();

			int x = 0;
			while(i_names.hasNext()) {
				// report the delay
				if(delay != null)
					try {
						delay.delay(x, vec_names.size());
					} catch(DelayAbortedException e) {
						writer.close();
						set.unlock();
						throw e;
					}



				String name = (String) i_names.next();
				Sequence seq = (Sequence) names.get(name);

				writer.println(pad_string(name, MAX_TAXON_LENGTH) + " " + seq.getSequence() + " [" + seq.getLength() + "]");

				x++;
			}
		} else {
			// go over all the 'segments'
			for(int x = 0; x < set.getMaxLength(); x+= interleaveAt) {
				Iterator i_names = vec_names.iterator();

				System.err.println("Writing segment " + x);

				// report the delay
				if(delay != null)
					try {
						delay.delay(x, set.getMaxLength());
					} catch(DelayAbortedException e) {
						writer.close();
						set.unlock();
						throw e;
					}

				// go over all the taxa 
				while(i_names.hasNext()) {
					String name = (String) i_names.next();
					Sequence seq = (Sequence) names.get(name);
					Sequence subseq = null;

					System.err.println("Writing sequence " + name + ": " + seq);

					int until = 0;

					try {
						until = x + interleaveAt;

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
		
		writer.println("END;\n");

		// put in any other blocks
		if(otherBlocks != null)
			writer.println(otherBlocks);

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

