/**
 * A file format handler from DNA.formats. Each format handler is tied in to different
 * file formats - there would be one for FASTA files, one for Mega files, etc. You
 * can interact with that code using five methods:
 * 
 * 	String getShortName():	returns the short name of the format
 * 	String getExtension():	the default extension for this format. Should be three letters (just in case).
 * 	String getLongName():	returns the long name of the format
 * 	void readFile():	reads a file, and appends it onto a SequenceList
 *	void writeFile():	writes a SequenceList into the specific file
 *	boolean mightBe():	checks the file signature and syntax to see if
 *				it might possibly belong to this format. This
 *				should be relatively fast: we'll be running
 *				through the entire list, looking for matches.
 *
 * This is fairly low level, and so you'll find long lists of exceptions so that
 * people can figure out what's going on. If you want a simple way of loading a file,
 * look at SequenceList.
 *
 */

/*
    TaxonDNA
    Copyright (C) 2005, 2006 Gaurav Vaidya
    
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

package com.ggvaidya.TaxonDNA.Common.DNA.formats;

import java.io.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;

public interface FormatHandler {
	/**
	 * Returns the short name of this file format. E.g. "NEXUS", "MEGA", or "FASTA".
	 * Think about whether you could put it into a sentence as "This is the ___ file format."
	 */
	public  String getShortName();

	/**
	 * Returns the extension (ideally lowercase, no fullstops) for this kind of file.
	 */
	public String getExtension();

	/**
	 * Returns the full name of this file format handler. E.g. "Nexus file format v2 and below".
	 * You ought to put in something about what versions of the software you support.
	 * But not too long: think about whether you could display it in a list.
	 */
	public  String getFullName();
	
	/**
	 * Read this file into the specified SequenceList. This will read all the files straight into
	 * this sequence list, in the correct order.
	 * 
	 * @throws IOException if there was an error doing I/O
	 * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
	 * @throws FormatException if there was an error in the format of the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public SequenceList readFile(File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException;

	/**
	 * Append this file to the specified SequenceList. This will read in all the sequences from
	 * the file and append them directly onto the end of this SequenceList.
	 *
	 * @throws IOException if there was an error doing I/O
	 * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
	 * @throws FormatException if there was an error in the format of the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public void appendFromFile(SequenceList appendTo, File fileFrom, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException;

	/**
	 * Writes the content of this sequence list into a file. The file is
	 * overwritten. The order of the sequences written into the file is
	 * guaranteed to be the same as in the list.
	 *
	 * @throws IOException if there was a problem creating/writing to the file.
	 * @throws DelayAbortedException if the DelayCallback was aborted by the user.
	 */
	public void writeFile(File file, SequenceList set, DelayCallback delay) throws IOException, DelayAbortedException;
	
	/**
	 * Checks to see if this file *might* be of this format. Good for internal loops.
	 * 
	 * No exceptions: implementors, please swallow them all up. If the file does not
	 * exist, it's not very likely to be of this format, is it? 
	 */
	public boolean mightBe(File file);

	/**
	 * Add a new FormatListener to this FormatHandler. We'll keep the Listener notified during
	 * a parse, so that he can chug up any other information which we can't stuff into the file.
	 */
	public void addFormatListener(FormatListener listener);

	/**
	 * Removes a FormatListener from this FormatHandler. 
	 */
	public void removeFormatListener(FormatListener listener);
}
