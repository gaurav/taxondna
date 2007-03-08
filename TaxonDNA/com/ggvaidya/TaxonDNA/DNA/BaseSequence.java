/**
 * A BaseSequence is a Sequence (like, DNA.Sequence) which can hold any
 * data you'd like. It *is* derived from DNA.Sequence, so hopefully it'll
 * just sneak along with any SequenceLists/UIExtensions/etc., but will
 * completely fail to be compared to a *real* Sequence.
 *
 * Note that if you need to make another type of sequence, you will - in
 * this model - have to extend Sequence, NOT BaseSequence.
 *
 * Note that every time anybody adds anything to Sequence, somebody will
 * have to add no-I-don't-mean-you code to BaseSequence.
 *
 * Note that this is all really very evil and grimy, but it's quick and
 * cheap, and pulling Sequence out from underneath TaxonDNA will be 
 * several different kinds of painful.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2007		Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.DNA;

import 	java.util.*;		// hashtable
import	java.util.regex.*;	// used to regex the species names

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Others.UUID;	// UUIDs

public class BaseSequence extends Sequence {
	private UUID		id = 	new UUID();	// just call them "UUIDs" and gag me with a spoon ...

//
//	1.	STATIC FUNCTIONS. Handle our two "constants": ambiguousBasesAllowed and minOverlap
//
	// none, m'lord!
	//
//
//	2. CONSTRUCTORS.
//	
	/**
	 * Empty constructor. Creates a blank, zero-length sequence.
	 * This is actually fairly important, since this is the
	 * ONLY way of creating a Sequence without throwing a
	 * SequenceException.
	 */	
	public BaseSequence() {
		changeName("Empty sequence");
		changeSequence("");
	}

	/**
	 * Most basic constructor. Give me a name (which I'll use to interpret
	 * the full name from) and the sequence, and I'll set things up.
	 *
	 * @throws SequenceException if there is a problem with understanding the sequence.
	 */
	public BaseSequence(String name, String seq) {
		changeName(name);
		changeSequence(seq);
	}

	/**
	 * The constructor to "bind them all" :P
	 */
	public static Sequence createSequence(String name, String seq) {
		try {
			return new Sequence(name, seq);
		} catch(SequenceException e) {
			return new BaseSequence(name, seq);
		}
	}

	public static Sequence promoteSequence(Sequence x) {
		if(BaseSequence.class.isAssignableFrom(x.getClass())) {
			try {
				return new Sequence(x.getName(), x.getSequence());
			} catch(SequenceException e) {
				return x;
			}	
		} else
			// if it's not a BaseSequence, there isn't anything
			// to 'promote' to! (yet)
			return x;
	}

	/**
	 * Returns a string representation of this object.
	 */
	public String toString() {
		if(len < 20)
			return "DNA.BaseSequence(" + getName() + ", length: " + len + "): " + new String(seq);
		else
			return "DNA.BaseSequence(" + getName() + ", length: " + len + ")";
	}

	/**
	 * Returns a subsequence of this sequence. If the subsequence is completely 'valid',
	 * we might return a Sequence! Otherwise, we return a BaseSequence.
	 */
	public Sequence getSubsequence(int from, int to) throws SequenceException {
		// make sure we're not being fed garbage
		if(
				from < 1 || 		// the first char is index = 1
				to > getLength()	// the 'to' field must not be greater than the length of the sequence
		)
			throw new SequenceException(this.getFullName(), "There is no subsequence at (" + from + ", " + to + ") in sequence " + this);

		boolean complement = false;
		if(to < from) {
			complement = true;
			int tmp = from;
			from = to;
			to = tmp;
		}
		
		String seq_str = getSequence();
		int no_gaps_to_fill = 0;
		if(seq_str.length() >= to) {
			seq_str = seq_str.substring(from - 1, to);
		} else if(seq_str.length() > from) {
			seq_str.substring(from - 1, seq_str.length());
			no_gaps_to_fill = (to - from) - seq_str.length() + 1;
		} else {
			// seq_str.length() < from
			no_gaps_to_fill = to - from;	
		}

//		System.err.println("to = " + to + ", seq_str.length() = " + seq_str.length() + ", no_gaps_to_fill = " + no_gaps_to_fill);
/*
		if(to > seq_str.length()) {
			no_gaps_to_fill += (to - seq_str.length());
		}
		*/

//		System.err.println("no_gaps_to_fill = " + no_gaps_to_fill);
		
		if(no_gaps_to_fill > 0) {
			StringBuffer buff = new StringBuffer();

			for(int c = 0; c < no_gaps_to_fill; c++)
				buff.append('-');

			seq_str += buff.toString();
		}

		if(complement) {
			// RC the resulting string
			StringBuffer tmp = new StringBuffer(seq_str).reverse();	

			for(int x = 0; x < seq_str.length(); x++) {
				tmp.setCharAt(x, complement(tmp.charAt(x)));
			}

			seq_str = tmp.toString();
		}

		try {
			return new Sequence(getFullName() + "(segment:" + from + "-" + to + ":inclusive)", seq_str);
		} catch(SequenceException e) { 
			return new BaseSequence(getFullName() + "(segment:" + from + "-" + to + ":inclusive)", seq_str);
		}		
	}


//
//	4.	SETTERS. Functions to set values in Sequence. This includes
//		both simple setters (such as 'setWarningFlag') all the way
//		to complicated functions (such as 'changeName').
//	
	/**
	 * Changes the sequence itself. BaseSequence doesn't care what sequence
	 * you try feeding in here.
	 */
	public void changeSequence(String seq) {
		synchronized(this) { 
			this.id = new UUID();
			this.seq = seq.toCharArray();
			this.len = seq.length();
		}
	}

}
