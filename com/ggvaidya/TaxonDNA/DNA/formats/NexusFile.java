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
		// when sequences get bigger than INTERLACE_AT, we'll
		// interlace to avoid confusion. Of course, we can't
		// interlace unless we interlace EVERYTHING, so we
	private int INTERLACE_AT = 	100;	
	
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
		// TODO
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
		
		if(set.getMaxLength() > INTERLACE_AT)
			interleaved = true;

		writer.println("\tFORMAT DATATYPE=DNA MISSING=? GAP=-;");
		writer.println("\tMATRIX");
		
		/*
		 * The following piece of code has to:
		 * 1.	Figure out VALID, UNIQUE names to output.
		 * 2.	Without hitting up against PAUP* and MacClade's specs (we'll 
		 * 	assume 32 chars for now - see MAX_TAXON_LENGTH - and work
		 * 	around things when we need to).
		 * 3.	Do interlacing, something no other part of the program does yet.
		 * 	(Unless I wrote it for Mega and forgot. See INTERLACE_AT)
		 */

		Hashtable names = new Hashtable();		// Hashtable(Strings)	of all the names currently in use
		Vector vec_names = new Vector();
		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			String name = seq.getSpeciesName(MAX_TAXON_LENGTH);
			int no = 1;
			while(names.get(name) != null) {
				int digits = 5;
				if(no > 0 && no < 10)		digits = 1;
				if(no >= 10 && no < 100)	digits = 2;
				if(no >= 100 && no < 1000)	digits = 3;
				if(no >= 1000 && no < 10000)	digits = 4;

				name = seq.getSpeciesName(MAX_TAXON_LENGTH - digits) + "_" + no;	
				no++;

				if(no == 10000) {
					// this has gone on long enough!
					throw new IOException("There are 9999 sequences named '" + seq.getSpeciesName(MAX_TAXON_LENGTH) + "', which is the most I can handle. Sorry. This is an arbitary limit: please let us know if you think we set it too low.");
				}
			}
			names.put(name, seq);

			name = name.replaceAll("\'", "\'\'");	// ' is reserved, so we 'hide' them
			name = name.replace(' ', '_');		// we do NOT support '. Pfft.
		
			// so now, we have a name. brilliant. What do we do with it?	
			if(interleaved)
				vec_names.add(name);
			else
				writer.println(name + "\t" + seq.getSequence() + "\t[" + seq.getLength() + "]");
		}

		// if we're not interleaved, we're done at this point.
		// if we ARE interleaved, we actually need to write out the sequences
		if(interleaved) {
			// go over all the 'segments'
			for(int x = 0; x < set.getMaxLength() + INTERLACE_AT; x+= INTERLACE_AT) {
				Iterator i = vec_names.iterator();

				// go over all the taxa 
				while(i.hasNext()) {
					String name = (String) i.next();

				}
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

