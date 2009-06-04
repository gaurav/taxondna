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
    Copyright (C) 2005-09 Gaurav Vaidya
    
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
	public static final int EXPORT_AS_BLOCKS = 		1;
	public static final int EXPORT_AS_SINGLE_LINE = 	2;
	public static final int EXPORT_AS_INTERLEAVED =	        3;
	
	// what is the maximum lengh of taxon names allowed?
	private int MAX_TAXON_LENGTH = 	32;

	// when sequences get bigger than INTERLEAVE_AT, we'll
	// interlace to avoid confusion. Of course, we can't
	// interlace unless we interlace EVERYTHING, so we
	private int INTERLEAVE_AT = 	80;	

	/** Returns the extension. We'll go with '.nex', our most common extension */
	public String getExtension() {
		return "nex";
	}	
	
	/**
	 * Returns a valid Mega OTU (Operation Taxonomic Unit), that is, a taxon name. Note that this function isn't actually being used. Freaky.
	 */
	public String getNexusName(String name, int len) {
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
			name = "_" + name;
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
			int count_data = 0;
			long file_length = fileFrom.length();
			while(reader.ready()) {
				if(delay != null)
					delay.delay((int)((float)count_data/file_length * 100), 1000);
					// note that we're really going from 0% to 10%. This will
					// make the user less confused when we jump from 10% to 0% and
					// start over.
				count_data += reader.readLine().length();
				count_lines++;
			}

			// let's go!
			reader = new BufferedReader(new FileReader(fileFrom));
			NexusTokenizer tok = new NexusTokenizer(reader);
			// let's pre-read the #NEXUS line
			if(tok.nextToken() != '#')
				throw new FormatException("This file does not have a Nexus header (it doesn't start with '#')! Are you sure it's a Nexus file?");

			tok.nextToken();

			if(tok.sval == null || !tok.sval.equalsIgnoreCase("nexus")) {
				throw new FormatException("This file does not have a Nexus header (it doesn't start with '#nexus')! Are you sure it's a Nexus file?");
			}

		
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
				if(type == NexusTokenizer.TT_EOF)
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
				if(type == NexusTokenizer.TT_WORD) {
					String str = tok.sval;

					if(str.equalsIgnoreCase("END") || str.equalsIgnoreCase("ENDBLOCK")) {
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
						if(tok.nextToken() == NexusTokenizer.TT_WORD) {
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
                                                        else if(beginWhat.equalsIgnoreCase("CODONS"))
                                                                blockCodons(appendTo, tok, evt, delay, count_lines);
							else {
								inStrangeBlock = true;
								// warn the user!
								delay.addWarning("Block '" + beginWhat + "' cannot be read by TaxonDNA yet! This block will NOT be imported, and therefore cannot be re-exported.");
							}
						
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

	public FormatException formatException(NexusTokenizer tok, String message) {
		return new FormatException("Error on line " + tok.lineno() + ": " + message);
	}

	/**
	 * Processing the DATA block of NexusFile.
	 *
	 * Things to do:
	 * 1.	Change the definition of ' such that:
	 * 	'x y z' == x_y_z
	 * 	'Test''s testing' == Test''s_testing == [Test's testing]
	 * 2.	
	 *
	 */
	public void blockData(SequenceList appendTo, NexusTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
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
		
		tok.setGapChar(gapChar);
		tok.setMissingChar(missingChar);

		Hashtable hash_names = new Hashtable();			// String name -> Sequence
		String name = null;

		int commentLevel = 0;
		boolean newCommand = true;

		while(true) {
			int type = tok.nextToken();
			String str = tok.sval;

			if(delay != null)
				delay.delay(tok.lineno(), count_lines);

			if(type == NexusTokenizer.TT_EOF) {
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

				if(type == NexusTokenizer.TT_WORD) {
					str = tok.sval;

					if(str.equalsIgnoreCase("INTERLEAVE")) {
						str = getValueOfKey(tok);

						if(str == null) {
							// just INTERLEAVE; as per standard, 
							// this means INTERLEAVE is ON
							isDatasetInterleaved = true;
						} else {
							// now, str is either 'YES' or 'NO' or, err, something else
							// 
							// we'll try to be careful about it - you explicitly need
							// a 'YES' to activate this
							if(str.equalsIgnoreCase("YES"))
								isDatasetInterleaved = true;
							else
								isDatasetInterleaved = false;
						}
					}
		
					else if(str.equalsIgnoreCase("DATATYPE")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'DATATYPE' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						/* 
						 * Okay, the Nexus specification defines six 'datatype's:
						 * 1.	STANDARD (default): 	'discrete character data'. Sounds okay.
						 * 2.	DNA:			We were BORN for this.
						 * 3.	RNA:			It'll show up as a Sequence if there's no
						 * 				Uracil; otherwise, we'll get a BaseSequence.
						 * 				Dodgy, but, err, doable. I guess.
						 * 4.	NUCLEOTIDE:		Err ... I guess. It's not properly defined.
						 * 5.	PROTEIN:		Again, it's *sequence* data, so presumably
						 * 				BaseSequence should handle it just fine.
						 * 6.	CONTINUOUS:		Nope. Not happening.
						 *
						 * There's a lot of weirdness in STANDARD, which we really don't have the
						 * time to write complete support for just yet. 
						 *
						 * So: for now, EVERYTHING's okay - except CONTINUOUS, because we'll be
						 * able to handle it, one way or another. But RNA and PROTEIN might not
						 * do what you except, which is bad.
						 *
						 */
						if(str.equalsIgnoreCase("CONTINUOUS"))
							throw formatException(tok, "I can't understand DATATYPE '" + str + "'. I can only understand DATATYPE=DNA.");
					}

					else if(str.equalsIgnoreCase("GAP")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'GAP' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						if(str.length() > 1)
							throw formatException(tok, "I can't use more than one character as the GAP character. The file specifies: '" + str + "'");

						gapChar = str.charAt(0);
						tok.setGapChar(gapChar);
					}
						
					else if(str.equalsIgnoreCase("MISSING")) {
						str = getValueOfKey(tok);
						if(str == null)
							throw formatException(tok, "'MISSING' is misformed (or there's a comment in there somewhere. I can't abide comments in FORMAT).");

						if(str.length() > 1)
							throw formatException(tok, "I can't use more than one character as the MISSING character. The file specifies: '" + str + "'");

						missingChar = str.charAt(0); 
						tok.setMissingChar(missingChar);
					} else if(str.equalsIgnoreCase("SYMBOLS")) {
						String throwaway = getValueOfKey(tok);
						// okay, ignore this for now, since we can do ANY symbol.
						delay.addWarning("FORMAT SYMBOLS=... in the DATA/CHARACTERS block cannot be read by TaxonDNA yet. There is no guarantee that illegal symbols are not being used in your sequences.");
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
					tok.reportNewlines(false);
					continue;
				}
				else if(type == NexusTokenizer.TT_EOL) {
					// end of line!
					name = null;
					continue;
				}
				else if(type == '(') {
					// Okay, things are about to get
					// *very* tricky
					// one thing we _know_ is, we
					// have to consume until the ')'
					// or throw a FormatException if
					// there isn't one.
					//
					// we could have
					// (123)
					// or
					// (1,3,4)
					StringBuffer chars = new StringBuffer();
					while(type != ')') {
						type = tok.nextToken();

						if(type == NexusTokenizer.TT_WORD) {
							chars.append(tok.sval);	// add the current word(s)
						} else if(type == ')')
							break;
						else if(type == ',')
							continue;	// ignore
						else if(type == NexusTokenizer.TT_EOF)
							throw formatException(tok, "Unterminated brackets in sequence '" + name + "'");
						else
							throw formatException(tok, "Unexpected '" + (char)type + "' in ambiguous data");
					}
					if(name == null) {
						// wtf name?!
						throw formatException(tok, "You can't use '(' symbols in names, sorry!");
					} else {
						// direct copy from below
                                                // please translate bugs, too!
                                                String strseq = "[" + chars.toString() + "]";                                                
                                                
						try {
							Sequence seq = null;
							if(!isDatasetInterleaved || hash_names.get(name) == null) {
								// doesn't exist, just add it
								seq = new BaseSequence(name, strseq);
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
					}
				}
				else if(type == NexusTokenizer.TT_WORD) {
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
								seq = new BaseSequence(name, strseq);
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
					}
				} else {
					throw formatException(tok, "Unexpected '" + (char) type + "' in matrix.");
				}
			} else {
				if(type == NexusTokenizer.TT_WORD) {

					if(str.equalsIgnoreCase("FORMAT")) {
						inFormatCommand = true;	
					}

					if(str.equalsIgnoreCase("MATRIX")) {
						if(gapChar == missingChar) {
							missingChar = 0;	// ha! find *this* character!
							delay.addWarning("This Nexus file defines BOTH the gap character and the missing character as '" + gapChar + "'. I will use this as the gap character only; no missing data will be recognized for this dataset.");
						}
							
						tok.reportNewlines(true);
						inMatrix = true;
					}

					// Commands we ignore
					if(
							str.equalsIgnoreCase("DIMENSIONS") ||
							str.equalsIgnoreCase("DIMENSION") ||
							str.equalsIgnoreCase("ELIMINATE") ||
							str.equalsIgnoreCase("TAXLABELS") ||
							str.equalsIgnoreCase("CHARSTATELABELS") ||
							str.equalsIgnoreCase("CHARLABELS") ||
							str.equalsIgnoreCase("STATELABELS") ||
							str.equalsIgnoreCase("OPTIONS")
					) {
						inIgnoredCommand = true;
						delay.addWarning("Ignoring command '" + str + "' in the CHARACTERS/DATA block; TaxonDNA cannot interpret this at present.");
					}

					if(str.equalsIgnoreCase("END") || str.equalsIgnoreCase("ENDBLOCK")) {
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

				} else if(type == NexusTokenizer.TT_EOL) {
					// Look, I *know* I should figure out why this EOL is slipping out
					// of the inMatrix loop, but it's 10pm, and I really honestly truely
					// don't care any more. Sorry.

					continue;
				} else {
					if(inIgnoredCommand)
						continue;	// who *KNOWS* what ignored commands do these days?!

					// unknown symbol found
					//System.err.println("Last string (ish!): " + str);
					throw formatException(tok, "I found '" + (char)type + "' rather unexpectedly in the DATA/CHARACTERS block! Are you sure it's supposed to be here?");
				}
			}
			
			newCommand = false;
		}

		tok.setGapChar((char)0);
		tok.setMissingChar((char)0);

		// FINALLY, we need to convert any sequences we can into Real Sequences.
		Iterator i_names = hash_names.keySet().iterator();
		while(i_names.hasNext()) {
			String str_name = (String) i_names.next();
			Sequence sold = (Sequence) hash_names.get(str_name);
			Sequence snew = BaseSequence.promoteSequence(sold);
			if(!snew.equals(sold)) {
				int x = appendTo.indexOf(sold);
				appendTo.remove(sold);
				appendTo.add(x, snew);
			}
		}
	}

	/**
	 * Processes the 'SETS' block.
	 */
	public void blockSets(SequenceList appendTo, NexusTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
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

			if(type == NexusTokenizer.TT_EOF) {
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
			} else if(type == NexusTokenizer.TT_WORD) {
				//System.err.println("New command: " + str);

				// is it over?
				if(str.equalsIgnoreCase("END") || str.equalsIgnoreCase("ENDBLOCK")) {
					//System.err.println("Leaving strange block");
//					if(newCommand && tok.nextToken() == ';') {
						// okay, it's an 'END;'
						// and a new command ... so ';END;'
						break;
//					} else {
//						throw formatException(tok, "I found something strange after the END! I can't just ignore it. I'm sorry.");
//					}
				} else if(str.equalsIgnoreCase("CHARSET")) {
					if((type = tok.nextToken()) != NexusTokenizer.TT_WORD) 
						throw formatException(tok, "Unexpected symbol '" + (char)type + "' found after 'CHARSET'. This doesn't look like the name of a CHARSET to me!");

					String name = tok.sval;

					//System.err.println("Charset " + name + "!");

					type = tok.nextToken();
					if((char)type != '=') {
						// it might be STANDARD or VECTOR
						// god bless my anal soul

						if(type != NexusTokenizer.TT_WORD)
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
						} else if(type == NexusTokenizer.TT_WORD) {
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
					delay.addWarning("Command '" + str + "' in the SETS block cannot be read by TaxonDNA, and will be ignored.");
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
         * Extracts a single CodonPosSet range for a particular codon position.
         * I suppose we'll eventually be generating events for this, but for now we
         * need to consume all that information.
         * 
         * Note that the first argument is 0 for 'N', and 1, 2, 3 for the three positions respectively.
         */
        private void addCodonPosSet(int pos, FormatHandlerEvent evt, NexusTokenizer tok) throws FormatException, IOException {
            // I can't remember if 1.5 does autoboxing, so I'll manualbox.
            String position =   new Integer(pos).toString();

            int token = tok.nextToken();
            if(token != ':')
                throw formatException(tok, "Position " + position + " not followed by ':' as mandated, but '" + (char) token + "/" + tok.sval + "' instead");

            // Our expected format is roughly (\d+-\d+\\3) )+[,;]
            while(true) {
                token = tok.nextToken();

                if(token == ',')
                    return;
                    
                if(token == ';') {
                    tok.pushBack();
                    return;
                }

                if(token != NexusTokenizer.TT_WORD) {
                    throw formatException(tok, "In position " + position + ": I wasn't excepting " + (char) token + "; I thought I'd see a word!");
                }

                // So now we have: a number, followed possibly by a dash,
                // followed by another number, followed possibly by a '\',
                // followed by '3'.
                try {
                    int from;
                    int to;

                    from = Integer.parseInt(tok.sval);

                    token = tok.nextToken();
                    if(token == '-') {
                        if(tok.nextToken() != NexusTokenizer.TT_WORD) {
                            fireEvent(evt.makeCharacterSetFoundEvent(":" + pos, from, from));
                            tok.pushBack();
                            continue;
                        }

                        to = Integer.parseInt(tok.sval);

                        if(tok.nextToken() != '\\') {
                            fireEvent(evt.makeCharacterSetFoundEvent(":" + pos, from, to));

                            tok.pushBack();
                            continue;
                        }

                        if(tok.nextToken() != NexusTokenizer.TT_WORD || !tok.sval.equalsIgnoreCase("3")) {
                            throw formatException(tok, "I'm sorry, I can deal with CodonPosSets ending with /" + tok.sval + " - I only support 3!"); 
                        }

                        fireEvent(evt.makeCharacterSetFoundEvent(":" + pos, from, to));

                    } else {
                        tok.pushBack();
                        continue;
                    }

                } catch(NumberFormatException e) {
                    throw formatException(tok, "One of the values in CodonPosSet position " + position + " wasn't a number: " + e);
                }
            }
        }

	/**
	 * Processes the 'CODONS' block.
	 */
	public void blockCodons(SequenceList appendTo, NexusTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
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

			if(type == NexusTokenizer.TT_EOF) {
				// wtf?!
				throw formatException(tok, "I've reached the end of the file, and the CODONS block has *still* not been closed! Please make sure the block is closed.");
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
			} else if(type == NexusTokenizer.TT_WORD) {
				//System.err.println("New command: " + str);

				// is it over?
				if(str.equalsIgnoreCase("END") || str.equalsIgnoreCase("ENDBLOCK")) {
					break;
				} else if(str.equalsIgnoreCase("CODONPOSSET")) {
                                        // okay, the line looks like 'CODONPOSSET * \w+ = N: {desc}+, 1: {desc}+, 2: {desc}+, 3: {desc}+;'. And hopefully that's all we'll have to process.
                                        // where:
                                        //      desc = ((\d+)-(\d+)(\\3))*
                                        // let's just be strict for now, and pretend that anything even slightly off (except
                                        // for whitespace and comments) is wrong.

                                        // Okay, expecting a '*', but it's actually optional.
                                        if(tok.nextToken() != '*')      // If it's not '*' ...
                                            tok.pushBack();             // ... it must be the CondonPosSet name.

                                        // Now the CodonPosSet name.
                                        if(tok.nextToken() != NexusTokenizer.TT_WORD)
                                            throw formatException(tok, "Expecting the name of the CodonPosSet, but got " + tok + " instead");
                                        // Notice how we completely ignore the name here.

                                        // Now the '='. Sigh.
                                        if(tok.nextToken() != '=')
                                            throw formatException(tok, "Expecting a '=', but got something else altogether.");

                                        // Now apparently, the standard (or it's copy at 
                                        // https://www.nescent.org/wg/phyloinformatics/index.php?title=NEXUS_Specification&oldid=2978
                                        // says there *must* be three. We'll put that code elsewhere,
                                        // so it's easier to deal with. This is HOP-learning at work,
                                        // peoples. 
                                        String[] str_positions = new String[4];
                                        str_positions[0] = "N";
                                        str_positions[1] = "1";
                                        str_positions[2] = "2";
                                        str_positions[3] = "3";

                                        int token = tok.nextToken();
                                        for(int x = 0; x < str_positions.length; x++) {
                                            if(
                                                token != NexusTokenizer.TT_WORD ||
                                                !tok.sval.equalsIgnoreCase(str_positions[x])
                                            )
                                                //throw formatException(tok, "I expect '" + str_positions[x] + "' to be in the correct order in CODONPOSSET. The standard says so!");
                                                // On second thoughts: keep going.
                                                continue;

                                            addCodonPosSet(x, evt, tok);
                                            token = tok.nextToken();
                                        }

                                        // Consume the last ';'
                                        if(token != ';')
                                            throw formatException(tok, "Wait, the CODONPOSSET didn't end with ';' (it ended with " + (char) token + "/" + tok.sval + "). I wasn't expecting that. Ouch.");

				// commands we ignore in CODONS 
				} else if(
						str.equalsIgnoreCase("GENETICCODE") ||
						str.equalsIgnoreCase("CODESET")
				) {
					inIgnoredCommand = true;
					delay.addWarning("Command '" + str + "' in the CODONS block cannot be read by TaxonDNA, and will be ignored.");
				} else {
 					throw formatException(tok, "Unknown word/command '" + str + "' found in the CODONS block.");
				}
 			} else {
				throw formatException(tok, "Unexpected symbol '" + (char)type + "' found in the CODONS block.");
			}
			
			newCommand = false;
		}

		// final cleanup, if any
	}



	/**
	 * Gets the String 'value' of the key=value pair from the NexusTokenizer.
	 * I assume you've already got the 'key', so I'll get the '=' token,
	 * followed by a TT_WORD, and return the Integer.parseInt of that word.
	 *
	 * I'll return null if something went wrong.
	 *
	 * @throws IOException if the tokenizer gets IO issues.
	 */
	private String getValueOfKey(NexusTokenizer tok) throws FormatException, IOException {
		if(tok.nextToken() == '=') {
			int type = tok.nextToken();
			// all good so far
			if(type == NexusTokenizer.TT_WORD) {
				// yay! we got a word
				return tok.sval;
			} else if(
					!Character.isWhitespace((char)type)	&&
					!Character.isISOControl((char)type)
			) {
				char data[] = {(char)type};
				return new String(data);
			} else {
				throw formatException(tok, "Unexpected value '" + (char) type + "' found when looking for the value of a KEY=VALUE pair.");
			}
		} else {
			tok.pushBack();	// push the last token back in	
			return null;
		}
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
		int count_names = 0;
		while(i.hasNext()) {
			if(delay != null)
				delay.delay(count_names, set.count());
			count_names++;

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

				name = seq.getFullName(MAX_TAXON_LENGTH - digits - 1);
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
		String dataType = "DNA";
		Iterator i_seqs = set.iterator();
		while(i_seqs.hasNext()) {
			Sequence seq = (Sequence) i_seqs.next();

			if(seq.getClass().isAssignableFrom(BaseSequence.class)) {
				dataType = "STANDARD";		
				break;			// even one is good enough
			}
		}
		
		// We need fixups in here (i.e. TODO)
		// Most importantly, we need to convert Sequences 'segments' to [ACTG], etc.
		// While BaseSequences can remain in whatever form the raw data is.
		// This seems to be pretty complicated, and I'm far too sleepy today
		// to work on this. But I'm not committing this comment in until I know
		// what to do.
		// 
		writer.print("\tFORMAT DATATYPE=" + dataType + " MISSING=? GAP=- ");
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
			// interleaved!
			//
			// go over all the 'segments'
			for(int x = 0; x < set.getMaxLength(); x+= interleaveAt) {
				Iterator i_names = vec_names.iterator();

				//System.err.println("Writing segment " + x);

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

					//System.err.println("Writing sequence " + name + ": " + seq);

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
						throw new IOException("Could not get subsequence (" + (x + 1) + ", " + until + ") from sequence " + seq + ". This is most likely a programming error. The reason given was: " + e.getMessage());
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

	/**
	 * Export an entire SequenceGrid in one shot. We can do this,
	 * because we are the coolest. One problem: how do we let
	 * user decide what constants to use?
	 */
	public void writeFile(File f, SequenceGrid grid, DelayCallback delay) throws IOException, DelayAbortedException {
		writeNexusFile(f, grid, EXPORT_AS_INTERLEAVED, INTERLEAVE_AT, delay);
	}
	
	/** 
	 * A first stab at a Nexus/SequenceGrid writer. Most of the code has been 'borrowed' out of
	 * SequenceMatrix.
	 */
	public void writeNexusFile(File f, SequenceGrid grid, int exportAs, int interleaveAt, DelayCallback delay) throws IOException, DelayAbortedException {
		int countThisLoop = 0;

		// how do we have to do this?
		int how = exportAs;

                if(how != EXPORT_AS_BLOCKS && how != EXPORT_AS_SINGLE_LINE && how != EXPORT_AS_INTERLEAVED) {
                    throw new IOException("Internal program error: incorrect 'how', " + how);
                }

		// set up delay 
		if(delay != null)
			delay.begin();
		
		StringBuffer buff_sets = new StringBuffer();		// used to store the 'SETS' block
		buff_sets.append("BEGIN SETS;\n");

		// let's get this party started, etc.
		// we begin by obtaining the Taxonsets (if any).
		/* NO TAXONSETS AS YET WE'LL IMPLEMENT THIS LATER WHEN WE FEEL LIKE IT 
		Taxonsets tx = matrix.getTaxonsets(); 
		if(tx.getTaxonsetList() != null) {
			Vector v = tx.getTaxonsetList();
			Iterator i = v.iterator();

			countThisLoop = 0;
			while(i.hasNext()) {
				countThisLoop++;
				if(delay != null)
					delay.delay(countThisLoop, v.size());

				String taxonsetName = (String) i.next();
				// Nexus has offsets from '1'
				String str = getTaxonset(taxonsetName, 1);
				if(str != null)
					buff_sets.append("\tTAXSET " + taxonsetName + " = " + str + ";\n");
			}
		}
		*/

		// we begin by calculating the SETS block,
		// since:
		// 1.	we need to coordinate the names right from the get-go
		// 2.	INTERLEAVED does not have to write the Nexus file
		// 	at all, but DOES need the SETS block.
		//

		// Calculate the SETS blocks, with suitable widths etc.	
		int widthThusFar = 0;
		Iterator i = grid.getColumns().iterator();

		countThisLoop = 0;
		while(i.hasNext()) {
			countThisLoop++;
			if(delay != null)
				delay.delay(countThisLoop, grid.getColumns().size());

			String columnName = (String)i.next();

			// write out a CharSet for this column, and adjust the widths
			buff_sets.append("\tCHARSET " + fixColumnName(columnName) + " = " + (widthThusFar + 1) + "-" + (widthThusFar + grid.getColumnLength(columnName)) + ";\n");
			widthThusFar += grid.getColumnLength(columnName);
		}

		// end and write the SETS block
		buff_sets.append("END;");
		
		// Now that the blocks are set, we can get down to the real work: writing out
		// all the sequences. This is highly method specific.
		//
		// First, we write out the header, unless it's going to use NexusFile to
		// do the writing.
		PrintWriter writer = null;
		if(how == EXPORT_AS_BLOCKS || how == EXPORT_AS_SINGLE_LINE) {
			writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));

			writer.println("#NEXUS");
			writer.println("[Written by TaxonDNA " + Versions.getTaxonDNA() + " on " + new Date() + "]");

			writer.println("");

			writer.println("BEGIN DATA;");
			writer.println("\tDIMENSIONS NTAX=" + grid.getSequencesCount() + " NCHAR=" + grid.getCompleteSequenceLength() + ";");

			writer.print("\tFORMAT DATATYPE=DNA GAP=- MISSING=? ");
			if(how == EXPORT_AS_BLOCKS)
				writer.print("INTERLEAVE");
			writer.println(";");

			writer.println("MATRIX");
		}

		SequenceList list = null;
		if(how == EXPORT_AS_INTERLEAVED) {
			list = new SequenceList();
		}

		// Now, there's a loop over either the column names or the sequence list
		//
		if(how == EXPORT_AS_BLOCKS) {
			// loop over column names
			Iterator i_cols = grid.getColumns().iterator();

			countThisLoop = 0;
			int total_count = grid.getColumns().size();
			while(i_cols.hasNext()) {
				if(delay != null)
					delay.delay(countThisLoop, total_count);
				countThisLoop++;

				String colName = (String) i_cols.next();
				int colLength = grid.getColumnLength(colName);
				
				// first of all, write the column name in as a comment (if in block mode)
				writer.println("[beginning " + fixColumnName(colName) + "]");

				// then loop over all the sequences
				Iterator i_seqs = grid.getSequences().iterator();
				while(i_seqs.hasNext()) {
					String seqName = (String) i_seqs.next();
					Sequence seq = grid.getSequence(colName, seqName); 

					if(seq == null)
						seq = Sequence.makeEmptySequence(seqName, colLength);

					writer.println(getNexusName(seqName, MAX_TAXON_LENGTH) + " " + seq.getSequence() + " [" + colLength + " bp]"); 
				}
				
				writer.println("[end of " + fixColumnName(colName) + "]");
				writer.println("");	// leave a blank line
			}

		} else if(how == EXPORT_AS_SINGLE_LINE || how == EXPORT_AS_INTERLEAVED) {
			// loop over sequence names

			Iterator i_rows = grid.getSequences().iterator();
			countThisLoop = 0;
			while(i_rows.hasNext()) {
				if(delay != null)
					delay.delay(countThisLoop, grid.getSequences().size());
				countThisLoop++;

				String seqName = (String) i_rows.next();
				Sequence seq_interleaved = null;
				int length = 0;

				if(how == EXPORT_AS_SINGLE_LINE)
					writer.print(getNexusName(seqName, MAX_TAXON_LENGTH) + " ");
				else if(how == EXPORT_AS_INTERLEAVED)
					seq_interleaved = new Sequence();

				Iterator i_cols = grid.getColumns().iterator();
				while(i_cols.hasNext()) {
					String colName = (String) i_cols.next();
					Sequence seq = grid.getSequence(colName, seqName);

					if(seq == null)
						seq = Sequence.makeEmptySequence(colName, grid.getColumnLength(colName));

					length += seq.getLength();

					if(how == EXPORT_AS_SINGLE_LINE)
						writer.print(seq.getSequence());
					else if(how == EXPORT_AS_INTERLEAVED)
						seq_interleaved = seq_interleaved.concatSequence(seq);
					else
						throw new RuntimeException("'how' makes no sense in NexusFile.exportAsNexus()! [how = " + how + "]");
				}

				if(how == EXPORT_AS_INTERLEAVED)
					seq_interleaved.changeName(getNexusName(seqName, MAX_TAXON_LENGTH));

				if(how == EXPORT_AS_SINGLE_LINE)
					writer.println(" [" + length + " bp]");
				else if(how == EXPORT_AS_INTERLEAVED)
					list.add(seq_interleaved);
			}
		}

		// close up the file ... if there WAS a file to close, that is.
		if(how == EXPORT_AS_BLOCKS || how == EXPORT_AS_SINGLE_LINE) {
			// end the DATA block
			writer.println(";");
			writer.println("END;");
		
			writer.println(buff_sets);

			writer.close();
		}
		
		// shut down delay 
		if(delay != null)
			delay.end();

		// otherwise, err ... actually write the darn file out to begin with :p
		if(how == EXPORT_AS_INTERLEAVED) {
			NexusFile nf = new NexusFile();
			nf.writeNexusFile(f, list, interleaveAt, buff_sets.toString(), delay);
		}
		
	}
  
	private String fixColumnName(String columnName) {
		columnName = columnName.replaceAll("\\.nex", "");
		columnName = columnName.replace('.', '_');
		columnName = columnName.replace(' ', '_');
		columnName = columnName.replace('-', '_');
		columnName = columnName.replace('\\', '_');
		columnName = columnName.replace('/', '_');
		return columnName;
	}
}

