/**
 * The Interleaver helps you DNA.formats classes to output interleaved
 * sequences.
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

public class Interleaver {
	private Hashtable	seqs = new Hashtable();		// String(name) -> Sequence
	private Vector		sequenceNames = new Vector();	// sequenceNames, in the order they were added

	public Interleaver() {

	}

	/**
	 * Get the sequence with the specified name. This is the sequence 'to date': so please don't
	 * ask us before you're all done giving us the data.
	 */
	public Sequence getSequence(String name)  {
		return (Sequence) seqs.get(name);
	}

	/**
	 * Set OR REPLACE the sequence with the specified name.
	 */
	public void setSequence(String name, Sequence seq) {
		if(seqs.get(name) == null)
			sequenceNames.add(name);
		seqs.put(name, seq);
	}

	/**
	 * Append OR CREATE a sequence with the specified name. 
	 * Note that this is not tested.
	 *
	 * @throws SequenceException if there is an error in the combined Sequence
	 */
	public void appendSequence(String name, String sequence) throws SequenceException {
		appendSequence(name, BaseSequence.createSequence(name, sequence));
	}

	public void appendSequence(String name, Sequence seq) {
		if(seqs.get(name) == null) {
			sequenceNames.add(name);
			seqs.put(name, seq);
		} else {
			// append it onto the currently existing 'Sequence'
			Sequence our_seq = (Sequence) seqs.get(name);
			our_seq = our_seq.concatSequence(seq);
			seqs.put(name, our_seq);
		}
	}

	/**
	 * Returns an iterator to a list of species names. You can call getSequence() to get the Sequences themselves.
	 */
	public Iterator getSequenceNamesIterator() {
		return sequenceNames.iterator();
	}
}
