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
 * ALSO TO BE NOTED that BaseSequence is really a sub-in for 'symbolic' data;
 * i.e. Any Symbol Will Do. The downside of this is that we, and we alone,
 * need to handle symbols which represent other symbols.
 *
 * The current proposal is to do that the dead-simple way: every incoming 
 * string will be checked for completeness in bracketing 
 * (no_closing_brackets === no_opening_brackets). BOTH square and round
 * brackets are assumed to be bracketing information; you can use angle
 * brackets if you need to use your own bracketing symbol we'll ignore.
 * When we're asked for the sequence, we return it with 'TaxonDNA' brackets 
 * (which are by definition square brackets). 
 *
 * The only really tricky thing here is getSubsequence(), which must count
 * indexes using bracketting information. For now, each and every getSubsequence()
 * request will traverse the entire sequence, one character at a time, then
 * traverse the copy region, again, one character at a time.
 *
 * If that doesn't work, we can store the sections in chunks, viz:
 * 	chunk#	string
 * 	0:	1394919191
 * 	1:	[124
 * 	2:	56193381
 * Any chunch not beginning with '[' can be assumed to have the length
 * of str.length(), while any chunk beginning with '[' has a length of
 * 1, unless the 2nd character is [[, in which case it has a lenght of
 * (str.length() - 1). But you know what they say about premature
 * optimization.
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
		try {
			changeSequence("");
		} catch(SequenceException e) {
			throw new RuntimeException("Internal error: changeSequence() doesn't seem to be working properly!");
		}
	}

	/**
	 * Most basic constructor. Give me a name (which I'll use to interpret
	 * the full name from) and the sequence, and I'll set things up.
	 *
	 * @throws SequenceException if there is a problem with understanding the sequence.
	 */
	public BaseSequence(String name, String seq) throws SequenceException {
		changeName(name);
		changeSequence(seq);
	}

	/**
	 * The constructor to "bind them all" :P
	 */
	public static Sequence createSequence(String name, String seq) throws SequenceException {
		try {
			return new Sequence(name, seq);
		} catch(SequenceException e) {
			return new BaseSequence(name, seq);
		}
	}

	public static Sequence promoteSequence(Sequence x) {
		if(BaseSequence.class.isAssignableFrom(x.getClass())) {
			try {
				return new Sequence(x.getFullName(), x.getSequence());
			} catch(SequenceException e) {
				return x;
			}	
		} else
			// if it's not a BaseSequence, there isn't anything
			// to 'promote' to! (yet)
			return x;
	}

	/**
	 * Normally, getSequenceExpanded(char x, char y) would 'expand' a Sequence.
	 * You can't expand a BaseSequence. However, we will replace '['...']' with
	 * the characters you specify.
	 */
	public String getSequenceExpanded(char begin, char end) {
		return getSequence().replace('[', begin).replace(']', end);
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
        
        public int getLength() {
            // we have to go through to figure this out.
            // what a pain
            return len;
        }
        
        public int getActualLength() {
            // how are we ever really going to handle this?!
            int actualLength = 0;
            int bracket_level = 0;
            
            for(int x = 0; x < seq.length; x++) {
                char ch = seq[x];
                
                if(ch == '[')
                    bracket_level++;
                else if(ch == ']')
                    bracket_level--;
                
                if(bracket_level > 0)
                    continue;
                
                if((isGap(ch) && !isInternalGap(ch)) || isMissing(ch) ) {
                } else {
                    actualLength++;
                }
            }
            
            return actualLength;
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

		if(to < from) {
			throw new SequenceException(this.getFullName(), "I cannot generate the reverse-complement of this sequence, since I do not understand what sort of data it is.");
		}

		// so, now we have a 'from' and a 'to'.
		// Let's get cracking!
		java.io.StringReader r = new java.io.StringReader(getSequence());
		StringBuffer output = new StringBuffer();

		//System.err.println("Base.cut: from: " + from + " to: " + to);

		boolean writing = false;
		int char_at = 0;
		try {
			while(r.ready()) {
				int x = r.read();
				
				if(x == -1)
					throw new java.io.EOFException();

				if(char_at == from - 1)		// off by one, here
					writing = true;

				if(char_at == to)		// off by one, here too, but for a slightly different reason
					writing = false;

				if(x == '(' || x == '[') {
					int initial_char = x;
					int final_char = ' ';

					if(x == '(')
						final_char = ')';
					else
						final_char = ']';

					// convert brackets to the Real Thing
					if(writing) output.append('[');
	
					while(x != ']') {
						x = r.read();

						if(x == final_char)
							x = ']';

						if(x == -1)
							throw new java.io.EOFException();
	
						if(writing)	output.append((char)x);
					}
				} else {
					if(writing)
						output.append((char)x);
				}				
				
				char_at++;
			}
		} catch(java.io.EOFException e) {
			// okay, done.
			// did you know that 
		} catch(java.io.IOException e) {
			throw new RuntimeException("Why is reading from a string causing an IOException?! " + e);
		}

		String seq_str = output.toString();
		//System.err.println("What've we got: " + seq_str);
		Sequence seq = BaseSequence.createSequence(getFullName() + " (segment:" + from + "-" + to + ":inclusive)", seq_str);
		
		//System.err.println("Ended up with: " + seq);
		
		return seq;
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
	public void changeSequence(String seq) throws SequenceException {
		StringBuffer buff = new StringBuffer();

		// important step: is this sequence actually 'valid' (are all the brackets closed?)
		//
		// Also: convert round brackets into 'system-standard' (i.e. TaxonDNA specific) square brackets.
		//
		int count = 0;
                int length = 0;
		for(int x = 0; x < seq.length(); x++) {
			char ch = seq.charAt(x);

			// convert round brackets to square
			if(ch == '(')
				ch = '[';
			else if(ch == ')')
				ch = ']';

                	// count the brackets!
			if(ch == '[')
				count++;
			if(ch == ']')
				count--;
                
                        // this way, we delete this *once*
                        if(count == 0)
                            length++;		

			buff.append(ch);
		}
	
		if(count != 0)
			throw new SequenceException(getFullName(), "This sequence cannot be changed to the specified sequence, as the brackets are not properly closed. Please ensure that all brackets are closed, and try again.");

		seq = buff.toString();	// hey, if we _can_ ...

		synchronized(this) { 
			this.id = new UUID();
			this.seq = seq.toCharArray();
			this.len = length;
		}
	}
	
	/**
	  Converts external gaps to missing characters. Basically just a raw replace of '_'s to '?'s.
	  */
	public void convertExternalGapsToMissingChars() {
		// front sweep
		for(int x = 0; x < seq.length; x++) {
			if(seq[x] == '-')
				seq[x] = '?';
			else
				break;
		}
		
		// reverse sweep
		for(int x = seq.length - 1; x >= 0; x--) {
			if(seq[x] == '-')
				seq[x] = '?';
			else
				break;
		}

		// done!
	}
}
