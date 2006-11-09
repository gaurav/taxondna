/**
 * TNTFile allows you to read and write TNT files. I'm going to be
 * guesstimating the format from, of all things, my own TNT exporter.
 * I'm also going to be using TNT's own (fairly limited) 
 * documentation.
 *
 * This module really is pretty messed up, since I made the insanely
 * great design decision of writing the square-bracket-handling code
 * in here (thereby practically inverting an entire function) instead
 * of just rewriting Sequence to handle it. Stupid, stupid, stupid,
 * and a great example of why I shouldn't code when sleepy or
 * writer's-blocked.
 */

/*
    TaxonDNA
    Copyright (C) 2006 Gaurav Vaidya
    
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

public class TNTFile extends BaseFormatHandler {
	private static final int MAX_LENGTH =	31;		// TNT truncates at 32 characters, but it gives a warning at 32
								// I don't like warnings
	private static final int INTERLEAVE_AT = 80;		// default interleave length

	/**
	 * Returns a valid OTU (Operation Taxonomic Unit); that is, a taxon name.
	 */
	public String getOTU(String name, int len) {
		// Rule #1: the name must start with '[A-Za-z0-9\-\+\.]'
		char first = name.charAt(0);
		if(
		 	(first >= 'A' && first <= 'Z') ||
			(first >= 'a' && first <= 'z') ||
			(first >= '0' && first <= '9') ||
			(first == '_')
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
	public  String getShortName() {		return "TNT";		}

	/**
	 * Returns the full name of this file format handler. E.g. "Nexus file format v2 and below".
	 * You ought to put in something about what versions of the software you support.
	 * But not too long: think about whether you could display it in a list.
	 */
	public  String getFullName() {		return "TNT/Hennig86 support"; 	}
	
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
			StreamTokenizer tok = new StreamTokenizer(reader);

			// okay, here's how it's going to work:
			// 1. 	we will ONLY handle xread for now. i.e. NO other commands will be
			// 	processed at ALL. If we see any other command, we will just quietly
			// 	wait for the terminating semicolon.
			// 2.	well, okay - we will also handle nstates. But only to check for 
			// 	'nstates dna'. Anything else, and we barf with extreme prejudice.
			// 3.	we will also handled comments 'perfectly' ... err, assuming that 
			// 	anything in single quotes is a comment.	Which we can't really
			// 	assume. Ah well.
			// 4.	A LOT of the naming rules are just being imported in en toto from
			// 	NEXUS. This is probably a stupid idea, but I hate giving up on the
			// 	rigid flexibility they allowed. If any of these rules do NOT work
			// 	in TNT, lemme know.

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

			tok.ordinaryChar('\'');	// We need this to be an 'ordinary' so that we can use it to discriminate comments
			tok.ordinaryChar(';');	// We need this to be a 'word' so that we can use it to distinguish commands 
		
			// states
			int 		commentLevel = 		0;
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
				if(type == '\'') {
					if(commentLevel == 0)
						commentLevel = 1;
					else
						commentLevel = 0;

					continue;
				}

				if(commentLevel > 0)
					continue;

				// semi-colons indicate end of line.
				// some commands use this to determine the end of command 
				if(type == ';') {
					newCommand = true;
					continue;
				}

				// Words in here are guaranteed to be a 'command'
				if(newCommand && type == StreamTokenizer.TT_WORD) {
					String str = tok.sval;

					// nstates {we only understand 'nstates tnt'}
					if(str.equalsIgnoreCase("nstates")) {
						int token = tok.nextToken();

						if(
							(token == StreamTokenizer.TT_WORD) &&
							(tok.sval.equalsIgnoreCase("dna"))
						) {
							// nstates dna! we can handle this ...
						} else {
							throw formatException(tok, "TaxonDNA can currently only load TNT files which contain DNA sequences. This file does not (it uses 'nstates " + tok.sval + "').");
						}
					}

					// xread {okay, we need to actually read the matrix itself}
					else if(str.equalsIgnoreCase("xread")) {
						xreadBlock(appendTo, tok, evt, delay, count_lines);
					}

					else {
						// okay, we're 'in' a command
						// but thanks to newCommand, we can just ignore
						// all the crap; the program will just loop
						// around until it sees the ';', newCommand
						// is activated, and we look for the next
						// block.
					}
				} else {
					// strange symbol (or ';') found
					// since we're probably in a 'strange' block, we can just
					// ignore this.
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

	/**
	 * Parses an 'xread' command. This is going to be a pretty simple interpretation, all 
	 * things considered: we'll ignore amperstands,
	 * and we'll barf quickly and early if we think we're going in over our head. Pretty
	 * much, we are just targeting trying to be able to open files we spit out. Can't be
	 * _that_ hard, can it?
	 *
	 * Implementation note: the string '[]' in the sequence will be converted into a single '-' 
	 */
	public void xreadBlock(SequenceList appendTo, StreamTokenizer tok, FormatHandlerEvent evt, DelayCallback delay, int count_lines) 
		throws FormatException, DelayAbortedException, IOException
	{
		Interleaver interleaver = new Interleaver();
		String lastSequenceName = null;
		int seq_names_count = 0;
		int begin_at = tok.lineno();		// which line did this xreadBlock start at
		int lineno = tok.lineno();
		char missingChar =	'?';
		char gapChar =		'-';		// there is no standard definition of which characters
	       						// TNT uses for these, nor can I figure out how to
							// determine these values. So I'm just going with our
							// own defaults, to be tweaked as required.	

		tok.wordChars(gapChar,gapChar);
	        tok.wordChars(missingChar,missingChar);

		// To handle the []s correctly, we really need to process the newlines separately
		// note the implicit assumption, that we only have ONE sequence per LINE.
		tok.ordinaryChars('\r', '\r');
		tok.ordinaryChars('\n', '\n');

		Hashtable hash_names = new Hashtable();			// String name -> Sequence
		String name = null;

		// okay, 'xread' has started.
		if(tok.ttype == StreamTokenizer.TT_WORD && tok.sval.equalsIgnoreCase("xread"))
			;	// we've already got xread on the stream, do nothing
		else	
			tok.nextToken();		// this token IS 'xread'
		
		// handle the possible newline in between, and all that, gah, etc.
		do {
			tok.nextToken();
			lineno++;
		} while(tok.ttype == '\r' || tok.ttype == '\n');
		lineno--;

		// first, we get the title
		StringBuffer title = null;
		if(tok.ttype == '\'') {
			title = new StringBuffer();

			while(true) {
				int type = tok.nextToken();

				if(delay != null)
					delay.delay(lineno, count_lines);
				
				if(type == '\'')
					break;

				if(type == StreamTokenizer.TT_WORD)
					title.append(tok.sval);
				else
					title.append(type);

				if(type == '\r' || type == '\n')
					lineno++;

				if(type == StreamTokenizer.TT_EOF)
					throw formatException(tok, "The title doesn't seem to have been closed properly. Are you sure the final quote character is present?");
			}
		}
		
		// handle the possible newline in between, and all that, gah, etc.
		do {
			lineno++;
			tok.nextToken();
		} while(tok.ttype == '\r' || tok.ttype == '\n');
		lineno--;

		// finished (or, err, not) the (optional) title
		// but we NEED two numbers now
		int nChars = 0;
		if(tok.ttype != StreamTokenizer.TT_WORD)
			throw formatException(tok, "Couldn't find the number of characters. I found '" + (char)tok.ttype + "' instead!");
		try {
			nChars = Integer.parseInt(tok.sval);
		} catch(NumberFormatException e) {
			throw formatException(tok, "Couldn't convert this file's character count (which is \"" + tok.sval + "\") into a number. Are you sure it's really a number?");
		}

		do {
			tok.nextToken();
			lineno++;
		} while(tok.ttype == '\r' || tok.ttype == '\n');
		lineno--;
		
		int nTax = 0;
		if(tok.ttype != StreamTokenizer.TT_WORD)
			throw formatException(tok, "Couldn't find the number of taxa. I found '" + (char)tok.ttype + "' instead!");
		try {
			nTax = Integer.parseInt(tok.sval);
		} catch(NumberFormatException e) {
			throw formatException(tok, "Couldn't convert this file's taxon count (which is \"" + tok.sval + "\") into a number. Are you sure it's really a number?");
		}
		
		// okay, all done ... sigh.
		// now we can fingally go into the big loop

		while(true) {
			int type = tok.nextToken();

			if(delay != null)
				delay.delay(lineno, count_lines);

			if(type == StreamTokenizer.TT_EOF) {
				// wtf?!
				throw formatException(tok, "I've reached the end of the file, but the 'xread' beginning at line " + begin_at + " was never terminated.");
			}

			if(type == ';')
				// it's over. Go back home, etc.
				break;

			if(type == '\n' || type == '\r') {
				// okay, it's a new line
				// clear the last sequence name
				lastSequenceName = null;
				lineno++;

				if(type == 13)		// if we see carraige return, SKIP IT
					lineno--;	// this isn't serious, but the lineno count will double
							// (since on CR+LF systems, we match BOTH CR and LF)

				continue;
			}

			// okay, i'm commenting out this comment handling
			// code IN CASE it's ever needed again.
			/*
			if(type == '[' || type == ']') {
				if(type == '[')
					commentLevel++;

				if(type == ']')
					commentLevel--;

				continue;
			}
				
			if(commentLevel > 0)
				continue;
			*/
			if(type == '[') {
				// first of all: do we have a sequence name?
				if(lastSequenceName == null) {
					// oh no you didn't!
					throw formatException(tok, "Unexpected '[' found outside a sequence.");
				}

				// okay .. the next 'word' should be the set of characters which make up this
				// one character. The next token should then be the closing ']'. Anything else
				// is evil and scum and really very not nice.
				if(tok.nextToken() == StreamTokenizer.TT_WORD) {
					String word = tok.sval;

					if(tok.nextToken() == ']') {
						// okay, so we need to make a character out of word ...
						char dna_char = '-';
						for(int x = 0; x < word.length(); x++) {
							//System.err.println(dna_char + " and " + word.charAt(x) + " become " + Sequence.consensus(dna_char, word.charAt(x)));
							dna_char = Sequence.consensus(dna_char, word.charAt(x));
						}

						try {
							interleaver.appendSequence(lastSequenceName, new Character(dna_char).toString());
							//System.err.println("Adding " + new Character(dna_char).toString());
						} catch(SequenceException e) {
							formatException(tok, "Internal error while processing square brackets: this should never happen. Please contact the programmer!");
						}
					} else {
						throw formatException(tok, "Unexpected '" + (char)tok.ttype + "' inside square brackets.");
					}
				} else {
					throw formatException(tok, "Unexpected '" + (char)tok.ttype + "' within square brackets.");
				}

				continue;
			}

			if(type == StreamTokenizer.TT_WORD) {
				// word!
				String word = tok.sval;

				// okay, here's how it works: sequence names ought to be 'words' alternating
				// with, err, other words. I really have no idea what state the Tokenizer is
				// in right now, but I'm going to assume that the following 'just works'.
				//
				// Of course, if it doesn't, I'll be right back to fix it, won't I?
				// Grr.
				//
				String seq_name = new String(word);	// otherwise, technically, both word and seq 
									// would point to tok.sval.
				seq_name = seq_name.replace('_', ' ');
				String sequence = null;
				
				if(lastSequenceName == null) {
					if(tok.nextToken() != StreamTokenizer.TT_WORD) {
						throw formatException(tok, "I recognize sequence name '" + name + "', but instead of the sequence, I find '" + (char)tok.ttype + "'. What's going on?");
					}
					sequence = tok.sval;
					
					// save this one for future reference
					lastSequenceName = seq_name;
					seq_names_count++;
				} else {
					seq_name = lastSequenceName;
					sequence = word;
				}

				try {
					interleaver.appendSequence(seq_name, sequence);

				} catch(SequenceException e) {
					throw formatException(tok, "Sequence '" + name + "' contains invalid characters. The exact error encountered was: " + e);
				}

			} else {
				throw formatException(tok, "I found '" + (char)type + "' rather unexpectedly in the xread block! Are you sure it's supposed to be here?");
			}
		}

		// now, let's 'unwind' the interleaver and 
		// check that the numbers we get match up with
		// the numbers specified in the file itself.
		Iterator i = interleaver.getSequenceNamesIterator();
		int count = 0;
		while(i.hasNext()) {
			if(delay != null)
				delay.delay(count, seq_names_count);
			count++;

			String seqName = (String) i.next();
			Sequence seq = interleaver.getSequence(seqName);
	
			if(seq.getLength() != nChars) {
				throw new FormatException("The number of characters specified in the file (" + nChars + ") do not match with the number of characters is sequence '" + seqName + "' (" + seq.getLength() + ").");
			}

			appendTo.add(seq);
		}

		if(count != nTax)
			throw new FormatException("The number of sequences specified in the file (" + nTax + ") does not match the number of sequences present in the file (" + count + ").");

		// only important in the xread section
	        tok.ordinaryChar(gapChar);
	        tok.ordinaryChar(missingChar);

		// and you ESPECIALLY don't need to deal with this, praise the Lord
		tok.whitespaceChars('\r', '\r');
		tok.whitespaceChars('\n', '\n');
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
		writeTNTFile(file, set, 0, "", delay);
	}
	
	/**
	 * A species TNTFile-only method to have a bit more control over how
	 * the Nexus file gets written.
	 *
	 * @param interleaveAt Specifies where you want to interleave. Note that TNTFile.INTERLEAVE_AT will be entirely ignored here, and that if the sequence is less than interleaveAt, it will not be interleaved at all. '-1' turns off all interleaving (flatline), although you can obviously use a huge value (999999) to get basically the same thing.
	 * @param otherBlocks We put this into the file at the very bottom. It should be one or more proper 'BLOCK's, unless you really know what you're doing.
	 */
	public void writeTNTFile(File file, SequenceList set, int interleaveAt, String otherBlocks, DelayCallback delay) throws IOException, DelayAbortedException {
		boolean interleaved = false;
		
		if(interleaveAt > 0 && interleaveAt < set.getMaxLength())
			interleaved = true;

		set.lock();
		
		// it has begun ...
		if(delay != null)
			delay.begin();

		// write out a 'preamble'
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));

		writer.println("nstates dna;");		// it's all DNA information
		writer.println("xread");		// begin sequence output
		writer.println("'Written by TaxonDNA " + Versions.getTaxonDNA() + " on " + new Date() + "'");
							// commented string
		// write the maxlength/sequence count
		writer.println(set.getMaxLength() + " " + set.count());

		writer.println("");			// leave a blank line for the prettyness

		/*
		 * The following piece of code has to:
		 * 1.	Figure out VALID, UNIQUE names to output.
		 * 2.	Without hitting up against PAUP* and MacClade's specs (we'll 
		 * 	assume 32 chars for now - see MAX_LENGTH - and work
		 * 	around things when we need to).
		 *
		 * Interleaving will be handled later.
		 */
		Hashtable names = new Hashtable();		// Hashtable(Strings) -> Sequence	
								//	hash of all the names currently in use
		Vector vec_names = new Vector();		// Vector(String)
		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			String name = getOTU(seq.getFullName(MAX_LENGTH), MAX_LENGTH);
			name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
			name = name.replace(' ', '_');		// we do NOT support ' '. Pfft.

			int no = 2;
			while(names.get(name) != null) {
				int digits = 5;
				if(no > 0 && no < 10)		digits = 1;
				if(no >= 10 && no < 100)	digits = 2;
				if(no >= 100 && no < 1000)	digits = 3;
				if(no >= 1000 && no < 10000)	digits = 4;

				name = getOTU(seq.getFullName(MAX_LENGTH - digits - 1), MAX_LENGTH - digits - 1);
				name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
				name = name.replace(' ', '_');		// we do NOT support '. Pfft.
				name += "_" + no;

				no++;

				if(no == 10000) {
					// this has gone on long enough!
					throw new IOException("There are 9999 sequences named '" + seq.getFullName(MAX_LENGTH) + "', which is the most I can handle. Sorry. This is an arbitary limit: please let us know if you think we set it too low.");
				}
			}
			
			names.put(name, seq);
			vec_names.add(name);
		}

		if(!interleaved) {
			Iterator i_names = vec_names.iterator();

			int x = 0;
			while(i_names.hasNext()) {
				// report the delay
				if(delay != null) {
					try {
						delay.delay(x, vec_names.size());
					} catch(DelayAbortedException e) {
						writer.close();
						set.unlock();
						throw e;
					}
				}

				String name = (String) i_names.next();
				Sequence seq = (Sequence) names.get(name);

				writer.println(pad_string(name, MAX_LENGTH) + " " + seq.getSequence());

				x++;
			}
		} else {
			// go over all the 'segments'
			for(int x = 0; x < set.getMaxLength(); x+= interleaveAt) {
				Iterator i_names = vec_names.iterator();

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

					writer.println(pad_string(name, MAX_LENGTH) + " " + subseq.getSequence());
				}

				writer.println("&");	// the TNT standard (ha!) requires an '&' in between blocks.
			}
		}

		writer.println(";");	// the semicolon ends the 'xread' command.

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

				if(str.toLowerCase().indexOf("xread") != -1) {
					// we find xread!
					// we don't know if its parseable, but ... well.
					// *shrugs*
					// </irresponsibility>
					//
					// TODO Make this actually work, etc.
					return true;
				}
			}

			return false;
		} catch(IOException e) {
			return false;
		}
	}
}

