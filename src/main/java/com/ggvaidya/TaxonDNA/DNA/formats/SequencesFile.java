/**
 * A very simple but complete format for biological sequences. It works
 * like this:
 *
 * 1. 	The 'header', on the FIRST line, needs to be '#sequences'. Plural!
 * 	That's our file identification magic word. 
 *
 * 	A 'straight' sequences file can actually avoid the header entirely:
 * 	in which case it degrades straight into a perfectly ordinary Fasta file!
 *
 * 	The header is crucial because it contains the capabilities string, which
 * 	outlines which set of commands this file contains. This will allow
 * 	parses to quickly identify what they are capable of (TaxonDNA can warn
 * 	the user that it will disregard protein sequences if the 'protein' 
 * 	command is used, for instance). The capabilities string is a space
 * 	delimited string on the same line as the header. It is put into 
 * 	brackets to stand out. For instance:
 * 	
 * 		#sequences (nucleotide taxonomy)
 *
 * 2.	'#' is a comment indicator; ANY '#' is removed and everything after it
 * 	is completely ignored. You have been warned.
 *
 * 3.	Sequence names are indicated with '&gt;'. After that, anything goes until 
 * 	the end of line.
 *
 * 4.	Everything without a special starting character is 'data'. Data is associated
 * 	with the last sequence named, or '0' if there hasn't been one yet. Anything
 * 	goes, but it's up to the program what it will accept or reject (or complain
 * 	about) 
 *
 * 5.	'&gt;' is used to change the sequence name (like with Fasta). This means
 * 	that to convert a Fasta file into a Sequences file (why would you want to?),
 * 	you just need to add the magic word to the top.
 *
 * 6.	'@' is used to indicate a global command. The first word on the line will
 * 	indicate the nature of the command. If you do not understand a command, GO ON.
 * 	Global commands will NOT affect the data processing, only the data 
 * 	interpretation. If you don't look upon data that way, that's fine.
 *
 * 	If we really need to use some sort of strictiness, we will eventually
 * 	introduce a '@strict' command which will force strict processing.
 *
 * 	'@' commands are read in the order entered RIGHT AT THE VERY END OF THE
 * 	FILE. Basically, all '@' commands ought to be read up into a buffer,
 * 	then processed one by one in the order they were present in the file.
 * 	This is to bring home the message that '@' commands are GLOBAL commands
 * 	which do NOT affect data processing.
 *
 * 7.	'^' commands refer to the LAST sequence mentioned. These should be 
 * 	processed in order of appereance JUST BEFORE the next sequence name
 * 	is set. This will be used to set sequence-specific parameters etc.
 *
 * 	As before, any commands you don't understand can be ignored.
 *
 * 8.	You can use the capabilities string (see #1) to check for which
 * 	commands you _can_ understand. Any commands not specified in the
 * 	capabilities can be ignored, or informed to the user, at your
 * 	convenience.
 *
 * ADDENDUM
 * 1.	Names which are composed entirely of whitespace are allowed, and
 * 	accepted as such. Really.
 *
 * SECTION 2: TAG GROUPS
 * nucleotide:	the sequence information is nucleotide information
 * taxonomy:
 * 	^gi			GI number
 * 	^species		Species name
 * 	^subspecies		Subspecies name
 * 	^family			Family name
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2005-6 Gaurav Vaidya

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

public class SequencesFile extends BaseFormatHandler implements Testable {
	/** A vector of all command families supported by us (SequencesHandler). */
	private static Vector vec_handlers = new Vector();
	
	/** A vector of all command familie NAMES supported by us (String). */
	private static Vector vec_handlers_names = new Vector();

	/** Register a SequencesHandler with SequencesFile. */
	public static void addSequencesHandler(SequencesHandler handler) {
		synchronized(vec_handlers) {
			vec_handlers.add(handler);
			vec_handlers_names.add(handler.getSequencesHandlerName());
		}
	}

	/** Helper function to get a key/value pair out of sequences.
	 * There are three better ways of writing this function depending
	 * on whether we can use:
	 * 1.	Multiple return values
	 * 2.	Continuations
	 * 3.	References/pointers
	 *
	 * @param ret An array of two parts, both strings. ret[0] is filled in with the key, ret[1] is filled in with the value.
	 * */
	public static boolean isCommand(String line, String ret[]) {
		Matcher m = p_keyValue.matcher(line);

		if(m.matches()) {	// warning: we're using matches() since we ARE using the entire string
					// if you need to find() stuff, change this!
			ret[0] = m.group(1).trim();
			ret[1] = m.group(2).trim();
			return true;
		}		

		return false;
	}
	private static Pattern		p_keyValue = Pattern.compile("^[\\^\\@]\\s*([\\w\\.]+)\\s*(.*)$");

	/** Creates a SequencesFile reader/writer. */
	public SequencesFile() {}
	
	/** Returns the short name of this handler, namely: "FASTA" */
	public String getShortName() {
		return "Sequences";
	}

	/** Returns the extension. We'll go with '.fas' as our semi-official DOS-compat extension */
	public String getExtension() {
		return "txt";
	}

	/** Returns the full name of this handler */
	public String getFullName() {
		return "TaxonDNA's 'Sequences' file format";
	}

	/**
	 * Looks for the Sequences file header ('#sequences')
	 */
	public boolean mightBe(File file) {
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new FileReader(file));

			return reader.readLine().trim().regionMatches(
					true,		// ignore case?
					0,		// my offset
					"#sequences",	// other string
					0,		// your offset
					new String("#sequences").length()
							// length
					);
			
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

	public void fireLocalCmds(Sequence seq, Vector localCmds) throws FormatException {
		Iterator i = localCmds.iterator();
		while(i.hasNext()) {
			String localCmd = (String) i.next();
			boolean handled = false;

			synchronized(vec_handlers) {
				Iterator i_handlers = vec_handlers.iterator();

				while(i_handlers.hasNext()) {
					SequencesHandler handler = (SequencesHandler) i_handlers.next();

					if(handler.readLocalCommand(localCmd, seq)) {
						handled = true;
						break;	// somebody consumed the command
					}
				}
			}

			if(!handled)
				throw new FormatException("Local command '" + localCmd + "' in sequence " + seq.getFullName() + " cannot be interpreted by this program!");
		}
	} 

	public void fireGlobalCmds(SequenceList list, Vector globalCmds) throws FormatException {
		Iterator i = globalCmds.iterator();
		while(i.hasNext()) {
			String globalCmd = (String) i.next();
			boolean handled = false;

			synchronized(vec_handlers) {
				Iterator i_handlers = vec_handlers.iterator();

				while(i_handlers.hasNext()) {
					SequencesHandler handler = (SequencesHandler) i_handlers.next();

					if(handler.readGlobalCommand(globalCmd, list)) {
						handled = true;
						break;	// somebody consumed the command
					}
				}
			}

			if(!handled)
				throw new FormatException("Global command '" + globalCmd + "' cannot be interpreted by this program!");
		}
	}

	private String getLocalCommands(Sequence seq) {
		StringBuffer buff = null;

		synchronized(vec_handlers) {
			Iterator i_handlers = vec_handlers.iterator();

			while(i_handlers.hasNext()) {
				SequencesHandler handler = (SequencesHandler) i_handlers.next();

				String str = handler.writeLocalCommand(seq);
				if(str != null) {
					if(buff == null)
						buff = new StringBuffer();
					buff.append(str + "\n");	// just in case
				}
			}
		}

		if(buff == null)
			return null;
		else
			return buff.toString().trim();	// this is okay, since the local command will be written out with a writeln anyway
	}

	private String getGlobalCommands(SequenceList sl) {
		StringBuffer buff = null;

		synchronized(vec_handlers) {
			Iterator i_handlers = vec_handlers.iterator();

			while(i_handlers.hasNext()) {
				SequencesHandler handler = (SequencesHandler) i_handlers.next();

				String str = handler.writeGlobalCommand(sl);
				if(str != null) {
					if(buff == null)
						buff = new StringBuffer();
					buff.append(str + "\n");	// just in case
				}
			}
		}

		if(buff == null)
			return null;
		else
			return buff.toString().trim();	// this is okay, since the local command will be written out with a writeln anyway
	}	

	/**
	 * Appends the contents of a FASTA file to the specified SequenceList.
	 */
	public void appendFromFile(SequenceList list, File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException
	{
		// get going
		list.lock();
		if(delay != null)
			delay.begin();
		
		// variables
		Pattern 	pBlank	=	Pattern.compile("^\\s*$");
		Pattern 	pComment =	Pattern.compile("#.*$");
		Pattern		pName =		Pattern.compile("^\\s*>\\s*(.*)\\s*$");
		Pattern		pGlobalCmd =	Pattern.compile("^\\s*\\@(.*)$");
		Pattern		pLocalCmd =	Pattern.compile("^\\s*\\^(.*)$");

		String		name =	"0";			// as per spec
		StringBuffer	data = 	new StringBuffer();	// building up data
		Vector		localCmds = new Vector();	// local commands (^...)
		Vector		globalCmds = new Vector();	// global commands (@...)
		int		line_no = 0;			// track the line number

		int		count_lines = 0;		// we need to know how many sequences there are 

		// okay, first: count sequences. yes, all of them.
		BufferedReader reader = new BufferedReader(new FileReader(file));
		while(reader.readLine() != null) {
			count_lines++;
		}
		reader.close();
		reader = null;

		// start reading the file
		reader = new BufferedReader(new FileReader(file));
		
		// the hard work
		while(reader.ready()) {
			line_no++;
				
			try {
				if(delay != null)
					delay.delay(line_no, count_lines);
			} catch(DelayAbortedException e) {
				if(delay != null)
					delay.end();
				list.unlock();
				throw e;
			}

			// read line
			String line = reader.readLine();

			// okay, rule #2 says we need to get rid of all comments
			// so: away you go!
			Matcher m_comment = pComment.matcher(line);
			if(m_comment.matches()) {
				// it matches! it must go! it must go!
				//
				// one question: what happens to a comment in a comment?
				line = m_comment.replaceFirst("");
			}

			// trim this guy
			line = line.trim();

			// what kind of line is it?
			Matcher m = null;
			if(pBlank.matcher(line).matches()) {
				// Ignore blank lines 
			} else if((m = pName.matcher(line)).matches()) {
				// add the previous entry
				try {
					Sequence x = new Sequence(name, data.toString());

					// fire the local commands
					fireLocalCmds(x, localCmds);
					localCmds = new Vector();

					list.add(x);
				} catch(SequenceException e) {
					if(delay != null)
						delay.end();
					throw e; 
				}

				// start new seq
				name = m.group(1);
				data = new StringBuffer(); 

			} else if((m = pGlobalCmd.matcher(line)).matches()) {
				String cmd = m.group(1);

				globalCmds.add(line);

			} else if((m = pLocalCmd.matcher(line)).matches()) {
				String cmd = m.group(1);
				
				localCmds.add(line);

			} else {
				// as per the 'spec', this is a valid piece
				// of data - whatever it is. Add it!
				data.append(line);
			}
		}

		// done!
		if(delay != null)
			delay.end();

		reader.close();

		// is there anything in the buffers?
		if(data.length() != 0) {
			try {
				Sequence x = new Sequence(name, data.toString());
				list.add(x);
					
				// fire the local commands
				fireLocalCmds(x, localCmds);

			} catch(SequenceException e) {
				if(delay != null)
					delay.end();
			}
		}

		// fire the global commands
		fireGlobalCmds(list, globalCmds);

		list.setFile(file);
		list.setFormatHandler(this);	
		list.unlock();
	}

	/**
	 * Write this SequenceList into a '#sequences' file.
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

		// TODO: Quick and dirty implementation, we'll have to make
		// this a whole lot better once we're done with this thang ...
		StringBuffer signature = new StringBuffer();
		Iterator i_names = vec_handlers_names.iterator();

		while(i_names.hasNext()) {
			signature.append(((String)i_names.next()) + " ");
		}

		writer.println("#sequences (nucleotide " + signature.toString().trim() + ")");

		while(i.hasNext()) {
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

			Sequence seq = (Sequence) i.next();

			writer.println(">" + seq.getFullName());
			String commands = getLocalCommands(seq);
			if(commands != null)
				writer.println(commands);
			writer.println(seq.getSequenceWrapped(70));
		}
		String commands = getGlobalCommands(set);
		if(commands != null)
			writer.println(commands);

		if(delay != null)
			delay.end();

		writer.close();
	}

	/**
	 * Tests the SequencesFile class extensively so that bugs don't creep in.
	 */
	public void test(TestController testMaster, DelayCallback delay) throws DelayAbortedException {
		testMaster.begin("DNA.formats.SequencesFile");

		SequencesFile ff = new SequencesFile();

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
