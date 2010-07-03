/**
 * A class representing a biological sequence. At the moment, this is  
 * DNA.Sequence, so nucleotide data only. I'm writing this deliberately
 * not encapsulated: you should be able to use this as a framework for
 * your own Sequence stuff (by subclassing it). Alternatively, don't
 * feel shy about screwing around with this class itself, if you need
 * your 'Sequences' to act in different ways.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2005-07, 2010 Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Model;

import 	java.util.*;		// hashtable
import	java.util.regex.*;	// used to regex the species names

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Others.UUID;	// UUIDs

public class DNASequence extends Sequence {


	// number of ambiguous bases in this sequence
	protected int		ambiguous = 0;
	protected char[] seq; // the sequence itself (as a char array)

	// All sequences have a length.
	protected int		len;

	public static final int	PDM_UNCORRECTED = 0;	// uncorrected pairwise distances
	public static final int	PDM_K2P = 1;		// Kimura 2-parameter distances
	public static final int PDM_TRANS_ONLY = 2;	// Transversion distances ONLY to be used
	private static int	pairwiseDistanceMethod 	// Determines how we calculate the pairwise distances
					= PDM_UNCORRECTED;
							
	private static int	minOverlap = 300;	// the minimum overlap necessary for comparison
							// overlaps less than this will cause the pairwise()
							// function to return a distance of -1d
							//
							// this value can be changed by using setMinOverlap(int)
							// and queried with getMinOverlap()
	
	private static boolean	ambiguousBasesAllowed		=	true;	
							// somewhat complicated; if ambiguousBasesAllowed is true,
							// then ambiguous bases (N, Y, etc.) *are* allowed. If
							// it is false, the ambiguous bases will be silently
							// converted into '?' (or 'N')

//
//	1.	STATIC FUNCTIONS. Handle our two "constants": ambiguousBasesAllowed and minOverlap
//
	/**
	 * Change the minimum overlap required to make a comparison. 
	 *
	 * Note that this is a static function, changing a static 
	 * variable - all threads are going to be sharing this value. 
	 * The value only affects the returned value from sequence 
	 * calculating functions.
	 */
	public synchronized static void setMinOverlap(int minOverlap) {
		DNASequence.minOverlap = minOverlap;
	}

	/**
	 * Returns the minimum overlap required to make a comparison.
	 *
	 * Note that this is a static function, changing a static 
	 * variable - all threads are going to be sharing this value. 
	 * The value only affects the returned value from sequence 
	 * calculating functions.
	 */
	public synchronized static int getMinOverlap() {
		return minOverlap;
	}

	/**
	 * Returns whether ambiguity codes are allowed (true/false).
	 *
	 * Note that this is a static function, changing a static 
	 * variable - all threads are going to be sharing this value. 
	 * The value only affects the returned value from sequence 
	 * calculating functions.	 
	 */
	public synchronized static boolean areAmbiguousBasesAllowed() {
		return ambiguousBasesAllowed;
	}
	
	/**
	 * Set whether ambiguity codes are allowed (true or false?)
	 * If they are not allowed, we'll convert ambiguous bases into 'N'.
	 *
	 * Note that this is a static function, changing a static 
	 * variable - all threads are going to be sharing this value. 
	 * The value only affects the returned value from sequence 
	 * calculating functions.	 
	 */
	public synchronized static void ambiguousBasesAllowed(boolean now) {
		ambiguousBasesAllowed =	now;
	}

	/**
	 * Returns the value of the current 'pairwise distance calculating method'.
	 */
	public synchronized static int getPairwiseDistanceMethod() {
		return pairwiseDistanceMethod;
	}

	/**
	 * Sets which method is used to calculate pairwise distances. This
	 * really ought to be one of PDM_K2P or PDM_UNCORRECTED.
	 */
	public synchronized static void setPairwiseDistanceMethod(int pdwRequested) {
		clearPairwiseCache();
		pairwiseDistanceMethod = pdwRequested;
	}

//
//	2. CONSTRUCTORS.
//	
	/**
	 * Empty constructor. Creates a blank, zero-length sequence.
	 * This is actually fairly important, since this is the
	 * ONLY way of creating a Sequence without throwing a
	 * SequenceException.
	 */	
	public DNASequence() {
		try {
			changeName("Empty sequence");
			changeSequence("");
		} catch(SequenceException e) {
			// shouldn't happen!
			throw new RuntimeException("Can't create empty Sequence!");
		}
	}

	/**
	 * Most basic constructor. Give me a name (which I'll use to interpret
	 * the full name from) and the sequence, and I'll set things up.
	 *
	 * @throws SequenceException if there is a problem with understanding the sequence.
	 */
	public DNASequence(String name, String seq) throws SequenceException {
		changeName(name);
		changeSequence(seq.toUpperCase().trim());
	}	

	/**
	 * 'Clone' constructor. We replicate a given Sequence, creating another identical
	 * sequence WITH A DIFFERENT UUID (duh) in the process. Note that the old Properties
         * object is COPIED over.
	 */
	public DNASequence(DNASequence seq) {
		try {
			changeName(seq.getFullName());
			changeSequence(seq.getSequence());
			if(seq.properties != null)
				properties = (Properties) seq.properties.clone();
		} catch(SequenceException e) {
			throw new RuntimeException("Can't 'clone' sequence seq: " + e.getMessage());
		}
	}

	/**
	 * Creates a sequence consisting entirely of 'missing'.
	 */
	public static DNASequence makeEmptySequence(String name, int size) {
		StringBuffer buff = new StringBuffer();
		for(int x = 0; x < size; x++) {
			buff.append('?');
		}

		try {
			DNASequence seq = new DNASequence(name, buff.toString());
			return seq;
		} catch(SequenceException e) {
			// shouldn't happen as long as '-' is the gap character
			throw new RuntimeException("The gap character was changed without changing Sequence.makeEmptySequence(int)!");
		}
	}

		public void changeSequence(String seq) throws SequenceException {
		// we're case insensitive here, so make the string uppercase first
		seq = seq.toUpperCase();
		// step 1: we need to look for [...] or (...), then replace
		// the sequence unit with the 'combined' character
		StringBuffer seq_buff = new StringBuffer();
		for (int x = 0; x < seq.length(); x++) {
			char ch = seq.charAt(x);
			if (ch == '(' || ch == '[') {
				StringBuffer buff = new StringBuffer();
				for (int y = x + 1; y < seq.length(); y++, x++) {
					// we need to keep 'x' synchronized with us!
					ch = seq.charAt(y);
					if (ch == '(' || ch == '[') {
						throw new SequenceException(this.name, "Character \'" + ch + "\' found unexpectedly while inside a ambiguous base");
					} else if (ch == ')' || ch == ']') {
						break;
					} else {
						buff.append(ch);
					}
				}
				x++; // move past the ')' or ']'
				// now, we need to convert buff into an int
				int singleChar = 0;
				for (int y = 0; y < buff.length(); y++) {
					char aChar = buff.charAt(y);
					if (!isValid(aChar)) {
						throw new SequenceException(this.name, "Invalid character \'" + ch + "\' found in sequence.");
					}
					singleChar |= getint(aChar);
				}
				seq_buff.append(getcode(singleChar));
			} else {
				seq_buff.append(ch);
			}
		}
		char[] sequence = seq_buff.toString().toCharArray();
		// we change the gaps before and after the sequence itself to '_',
		// which represent external GAPs. Using '_' in the sequence itself
		// will cause a SequenceException
		int length = sequence.length;
		int forwardStrokeStoppedAt = 0;
		// forward stroke
		for (int x = 0; x < sequence.length; x++) {
			if (sequence[x] == '_') {
				throw new SequenceException(getFullName(), "You may not use \'_\' in a sequence, " + "as this character is reserved for programme use. " + "\'_\' was used at index " + x + ".");
			} else if (sequence[x] == '-') {
				forwardStrokeStoppedAt = x;
				sequence[x] = '_';
			} else if (sequence[x] == '?') {
				continue;
			} else {
				break;
			}
		}
		// backward stroke
		for (int x = sequence.length - 1; x >= 0; x--) {
			if (sequence[x] == '_') {
				// but is this the backward stroke "hitting" the forward stroke?
				// (i.e. is the sequence made up entirely of gaps?)
				if (forwardStrokeStoppedAt == x) {
					break; // yes; stop here
				} else {
					throw new SequenceException(getFullName(), "You may not use \'_\' in a sequence, " + "as this character is reserved for " + "programme use. \'_\' was used at index " + x + ".");
				}
			} else if (sequence[x] == '-') {
				sequence[x] = '_';
			} else if (sequence[x] == '?') {
				continue; // keep going
			} else {
				break;
			}
		}
		doSanityChecks(sequence, length);
		// if we're here, everything passed, so we can overwrite our data.
		// otherwise, we stay on the old values, and nobody is any the worse off
		// we'd better synchronize this, so that sequences don't get one changed
		// and not the other.
		synchronized (this) {
			this.id = new UUID();
			this.seq = sequence;
			this.len = length;
		}
	}


	/**
	 * Returns the sequence (DNA) of this sequence as a simple String. Note that the sequence will
	 * be "sanitized", with external stuff released. If you don't know how YOUR implementation of
	 * Sequence does things, this is what you ought to use.
	 */
	public String getSequence() {
		return new String(seq).replace('_', '-');
	}

	/**
	 * This will return external gaps ('_') as such.
	 */
	public String getSequenceWithExternalGaps() {
		return new String(seq);
	}
	
	/**
	 * Returns the sequence (DNA), but wraps it to a particular length first. Why is this here?
	 * Cos this is the *only* way you're going to put this in human-readable format. 
	 */
	public String getSequenceWrapped(int wrapAt) {
		String s = getSequence();
		StringBuffer ret = new StringBuffer();

		for(int x = 0; x < s.length(); x+=wrapAt) {
			if(x + wrapAt > s.length())
				ret.append(s.substring(x));
			else
				ret.append(s.substring(x, x+wrapAt) + "\n");
		}

		return ret.toString();
	}

	/**
	 * Returns the sequence (DNA) in its 'expanded' form: ambiguous codons are expanded to
	 * their full form, with characters _begin_ and _end_ being used to demarcate them.
         * Both TNT and Nexus use this on occasion; it will probably be a good idea for
	 * BaseSequence to expand Sequences which are converted into BS, since most BS-type
	 * situations cannot distinguish between amino acids and character data, and don't
	 * know how to expand the ambiguous codons.	 
	 */
	public String getSequenceExpanded(char begin, char end) {
		String s = getSequence();
		StringBuffer ret = new StringBuffer();

		for(int x = 0; x < s.length(); x++) {
			char ch = s.charAt(x);

			// simple or ambiguous?
			if(ch == 'A' || ch == 'C' || ch == 'T' || ch == 'G')
				ret.append(ch);
			else if(ch == '?' || ch == '-')
				ret.append(ch);
			else {
				// okay, NOT simple
				int code = getcode(ch);
				StringBuffer sb = new StringBuffer();

				if((code & getcode('A')) != 0)
					sb.append("A");
				if((code & getcode('C')) != 0)
					sb.append("C");
				if((code & getcode('T')) != 0)
					sb.append("T");
				if((code & getcode('G')) != 0)
					sb.append("G");

				ret.append(begin);
				ret.append(sb);
				ret.append(end);
			}
		}

		return ret.toString();
	}

	/**
	 * Returns the number of INTERNAL gaps in this sequence. 
	 */
	public int countInternalGaps() {
		int count = 0;

		for(int x = 0; x < len; x++) {
			if(isInternalGap(seq[x]))
				count++;
		}

		return count;
	}

	/**
	 * Returns the index (zero-based) of the first character in this sequence which is
	 * not a gap (internal or external). Returns -1 if there is no non-gap character in
	 * this sequence.
	 */
	public int getFirstRealCharacter() {
		for(int x = 0; x < len; x++) {
			if(!isGap(seq[x]) && !isMissing(seq[x]))
				return x;
		}

		return -1;
	}

	/**
	 * Returns the index (zero-based) of the first character in this sequence which is
	 * not a gap (internal or external). Returns -1 if there is no non-gap character in
	 * this sequence.
	 */
	public int getLastRealCharacter() {
		for(int x = len - 1; x >= 0; x--) {
			if(!isGap(seq[x]) && !isMissing(seq[x]))
				return x;
		}

		return -1;
	}

	/**
	 * Returns the number of base x in this sequence.
	 * We'll throw an exception if 'x' isn't actually a
	 * character we understand.
	 */
	public int countBases(char ch) {
		if(!isValid(ch))
			throw new IllegalArgumentException("countBase was asked to return the number of occurances of the base '" + ch + "' - unfortunately, '" + ch + "' is not a valid base!");

		// make 'ch' uppercase
		if(ch > 'a' && ch < 'z')
			ch = (char)(ch - ('a' - 'A'));

		int count = 0;
		for(int x = 0; x < len; x++) {
			char ch_seq = seq[x];

			// make 'ch_seq' uppercase
			if(ch_seq > 'a' && ch_seq < 'z')
				ch_seq = (char)(ch_seq - ('a' - 'A'));

			// are they identical now?
			if(ch_seq == ch)
				count++;
		}

		return count;
	}

	/**
	 * Returns the number of ambiguous bases in this sequence.
	 */
	public int getAmbiguous() {
		return ambiguous;
	}


	public DNASequence concatSequence(DNASequence seq) {
		try {
			changeSequence(getSequence() + seq.getSequence());
			return this;
		} catch (SequenceException e) {
			// shouldn't happen!
			throw new RuntimeException("The combination of " + this + " and " + seq + " is not a valid sequence!");
		}
	}

		/**
	 * Returns the actual length of the sequence: no leading or lagging gaps, basically.
	 */
	public int getActualLength() {
		int count = len;
		for (int x = 0; x < len; x++) {
			if ((isGap(seq[x]) || isMissing(seq[x])) && !isInternalGap(seq[x])) {
				count--;
			}
		}
		return count;
	}


	protected String getSequenceRaw() {
		return new String(seq);
	}


	public DNASequence getSubsequence(int from, int to) throws SequenceException {
		// make sure we're not being fed garbage
		if (from < 1 || to > getLength()) {
			throw new SequenceException(this.getFullName(), "There is no subsequence at (" + from + ", " + to + ") in sequence " + this);
		}
		boolean complement = false;
		if (to < from) {
			complement = true;
			int tmp = from;
			from = to;
			to = tmp;
		}
		String seq_str = getSequence();
		int no_gaps_to_fill = 0;
		if (seq_str.length() >= to) {
			seq_str = seq_str.substring(from - 1, to);
		} else if (seq_str.length() > from) {
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
		if (no_gaps_to_fill > 0) {
			StringBuffer buff = new StringBuffer();
			for (int c = 0; c < no_gaps_to_fill; c++) {
				buff.append('-');
			}
			seq_str += buff.toString();
		}
		if (complement) {
			// RC the resulting string
			StringBuffer tmp = new StringBuffer(seq_str).reverse();
			for (int x = 0; x < seq_str.length(); x++) {
				tmp.setCharAt(x, complement(tmp.charAt(x)));
			}
			seq_str = tmp.toString();
		}
		try {
			return new DNASequence(getFullName() + "(segment:" + from + "-" + to + ":inclusive)", seq_str);
		} catch (SequenceException e) {
			throw new AssertionError("Generating a subsequence (from " + this + ": " + from + "-" + to + ") caused strange characters to enter the sequence. This should never happen! The exception reported was: " + e);
		}
	}

//
//	5.	COMPARATORS. Used by Comparators to compare us to other sequences.
//		This means that the tricky job of sorting through sequences can
//		be tossed into the sort algorithm used by Java.
//		


	/**
	 * Compares two sequences by size (descending). This is true - we ignore warning flags
	 * for this sort.
	 */
	public int compareByActualLength(DNASequence seq) {
//		if(compareByWarningFlag(seq) != 0)
//			return compareByWarningFlag(seq);

		return getActualLength() - seq.getActualLength();
	}

	/**
	 * Compares two sequences by their full names.
	 */
	public int compareByFullName(DNASequence seq) {
		return getFullName().compareTo(seq.getFullName());
	}

	//
	//	6.	INDIVIDUAL CHARACTER FUNCTIONS. The following functions work on single
	//		characters only - for instance, is a character valid, what is the
	//		consensus of two characters, etc.
	//
	//		Some of these are public, some not; the general rule I'm using is that:
	//		1.	Public, character-only methods will be static methods. Where
	//			there is a functionality for full strings, the string version
	//			will have a Java-like name (e.g. getConsensus(String)), while
	//			the single-character one will have a C-like name
	//			(e.g. consensus(char, char))
	//		2.	Things like 'getint' are really more like helper functions, and
	//			should not be public unless they *need* to be, in which case
	//			they should be given better names.
	//

	/**
	 * Checks to see if a char is an ambiguous base.
	 */	
	public static boolean isAmbiguous(char ch) {
		// convert lowercase to uppercase
		if(ch > 'a' && ch < 'z')
			ch = (char)(ch - ('a' - 'A'));

		// is it valid?
		switch(ch) {
			case 'A':
			case 'C':
			case 'T':
			case 'G':
				return false;
			case 'R':	// A/G
			case 'Y':	// C/T
			case 'K':	// G/T
			case 'M':	// A/C
			case 'S':	// G/C
			case 'N':	// A/T/G/C
			case 'B':	// C/G/T
			case 'D':	// A/G/T
			case 'H':	// A/C/G
			case 'V':	// A/C/G
			case 'W':	// A/T
				return true;
			case '-':	// Dash ALWAYS means a gap
				return false;
			case '?':	// ? means MISSING INFORMATION.
					// if you want to say "might be anything",
					// please use 'N'!
				return false;	
			case '_':	// _ means a gap before or after the sequence itself (non-internal gap)
					// in case we want to consider this different from the
					// sequence itself;
				return false;
		}
	
		// not ambiguous, then.	
		return false;
	}

	/**
	 * Calculates the consensus of the two given bases. For instance,
	 * A+T+C+G becomes 'N'.
	 */
	public static char consensus(char ch1, char ch2) {
		// missing data is missing data, nothing more.
		if(ch1 == '?' || ch2 == '?')
			return '?';
		
		// internal gaps can be combined 
		// with:
		// 	internal gaps to give internal gaps
		// 	external gaps to give external gaps
		// 	other characters to give other chars
		// 	
		if(ch1 == '_') {
			if(ch2 == '_')
				return '_';
			else if(ch2 == '-')
				return '-';
			else
				return ch2;
		} else if(ch2 == '_') {
			if(ch1 == '_')
				return '_';
			else if(ch1 == '-')
				return '-';
			else
				return ch1;
		}

		//
		//	if ch1 is a gap, go with ch2
		//	
		if(isGap(ch1))
			return ch2;

		//
		//	if ch2 is a gap, go with ch1
		//	
		if(isGap(ch2))
			return ch1;

		// 
		// a lil bit of magic:
		// 	getint(ch) returns an int encoded with the bits set.
		// 		for instance, getint('Y') will return (C|T).
		// 	then, we combine bits using (i1 | i2), and use
		// 		getcode, which converts the resulting code
		// 		back into a character.
		// 		
		int 	i1	=	getint(ch1);
		int	i2	=	getint(ch2);

		return getcode(i1 | i2);
	}

	public static char complement(char ch) {
		switch(ch) {
			case 'A':	return 'T';
			case 'T':	return 'A';
			case 'C':	return 'G';
			case 'G':	return 'C';

			case 'R':	return 'Y';
			case 'Y':	return 'R';

			case 'K':	return 'M';
			case 'M':	return 'K';

			case 'S':	return 'W';
			case 'W':	return 'S';

			case 'B':	return 'A';
			case 'D':	return 'C';
			case 'H':	return 'G';
			case 'V':	return 'T';
			case 'N':	return 'N';

			case '-':	return '-';
			case '_':	return '_';
			case '?':	return '?';
		}

		return 'x';
	}

	/**
	 * Converts a SINGLE base into its integer form.
	 * This means:
	 * 		0x01	=	A
	 * 		0x02	=	C
	 * 		0x04	=	T
	 * 		0x08	=	G
	 * 		
	 * Non-bases are returned as 0x00.
	 */	
	private static int getint(char ch) {
		int 	retval	=	0;

		if(ch >= 'a' && ch <= 'z')
			ch = (char)(ch - ('a' - 'A'));
		
		if(	ch == 'A' || 
			ch == 'R' || 
			ch == 'M' || 
			ch == 'N' || 
			ch == 'D' || 
			ch == 'H' || 
			ch == 'V' || 
			ch == 'W')
						retval |= 0x01;

		if(	ch == 'C' || 
			ch == 'Y' || 
			ch == 'M' || 
			ch == 'S' || 
			ch == 'N' || 
			ch == 'B' || 
			ch == 'H' || 
			ch == 'V')
						retval |= 0x02;

		if(	ch == 'T' ||
			ch == 'Y' ||
			ch == 'K' ||
			ch == 'N' ||
			ch == 'B' ||
			ch == 'D' ||
			ch == 'H' ||
			ch == 'W')
						retval |= 0x04;

		if(	ch == 'G' ||
			ch == 'R' ||
			ch == 'K' ||
			ch == 'S' ||
			ch == 'N' ||
			ch == 'B' ||
			ch == 'D' ||
			ch == 'V')
						retval |= 0x08;
		
		return retval;
	}

	/**
	 * Converts the integer part of a base back into its
	 * character form. So, 0x01 becomes A, 0x03 = A/C = M,
	 * and so on.
	 */
	private static char getcode(int val) {
		boolean A	=	(val & 0x01) != 0;
		boolean C	=	(val & 0x02) != 0;
		boolean T	=	(val & 0x04) != 0;
		boolean G	=	(val & 0x08) != 0;		
		
		if(A)
			if(C)
				if(T)
					if(G)	// ACTG
						return 'N';
					else	// ACT
						return 'H';
				else
					if(G)	// ACG
						return 'V';
					else	// AC
						return 'M';	
			else
				if(T)
					if(G)	// ATG
						return 'D';
					else	// AT
						return 'W';
				else
					if(G)	// AG
						return 'R';
					else	// A
						return 'A';
		else
			if(C)
				if(T)
					if(G)	// CTG
						return 'B';
					else	// CT
						return 'Y';
				else
					if(G)	// CG
						return 'S';
					else	// C
						return 'C';
			else
				if(T)
					if(G)	// TG
						return 'K';
					else	// T
						return 'T';
				else
					if(G)	// G
						return 'G';
					else	// -
						return '-';
	}

	/**
	 * Is this character a pyrimidine (cytosine, thymine)
	 *
	 * Note that characters which are both purine or pyrimidine (say, [ACT]),
	 * will return true here.
	 */
	public static boolean isPyrimidine(char ch) {
		if(ch == 'Y' || ch == 'T' || ch == 'C')
			return true;
		return false;
	}

	public static char getmatch(char ch1, char ch2) {
		if (!isValid(ch1)) {
			return ' ';
		}
		if (!isValid(ch2)) {
			return ' ';
		}
		if (isGap(ch1) || isGap(ch2)) {
			return ' ';
		}
		if (ch1 == ch2) {
			return '|';
		} else if (DNASequence.ambiguousBasesAllowed && ((getint(ch1) & getint(ch2)) != 0)) {
			return '|';
		} else {
			return ' ';
		}
	}

	public static boolean identical(char ch1, char ch2) {
		// are they valid?
		if (!isValid(ch1) || !isValid(ch2)) {
			return false;
		}
		if (ch1 > 'a' && ch1 < 'z') {
			ch1 = (char) (ch1 - ('a' - 'A'));
		}
		if (ch2 > 'a' && ch2 < 'z') {
			ch2 = (char) (ch2 - ('a' - 'A'));
		} // with missing data is just plain wrong
		// i.e. false
		if (ch1 == '?' || ch2 == '?') {
			return false;
		}
		if (isGap(ch1) || isGap(ch2)) {
			if (isInternalGap(ch1) || isInternalGap(ch2)) {
				if (ch1 == ch2) {
					return true;
				}
			} // internal gaps are false unless they are being compared
			// with another internal gap.
			return false;
		} // but are we dealing with ambiguous characters?
		// if not, we can safely turn all ambiguous characters
		// into 'N'
		if (!DNASequence.ambiguousBasesAllowed) {
			if (ch1 != 'A' || ch1 != 'C' || ch1 != 'T' || ch1 != 'G') {
				ch1 = 'N';
			}
			if (ch2 != 'A' || ch2 != 'C' || ch2 != 'T' || ch2 != 'G') {
				ch2 = 'N';
			}
		}
		if (ch1 == ch2) {
			return true;
		} // we decide they are identical if you can do
		// a logical "and", ie. they share atleast one
		// of the four bases:
		// 	N & A = identical
		// 	(TG) & G = identical
		// 	(CT) & G = not identical
		if (DNASequence.ambiguousBasesAllowed) {
			if ((getint(ch1) & getint(ch2)) != 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks to see if a char is part of a sequence. Mostly useful during sanity checks.
	 */
	public static boolean isValid(char ch) {
		// convert lowercase to uppercase
		if (ch > 'a' && ch < 'z') {
			ch = (char) (ch - ('a' - 'A'));
		}
		switch (ch) {
			case 'A':
			case 'C':
			case 'T':
			case 'G':
				return true;
			case 'R':
// A/G
			case 'Y':
// C/T
			case 'K':
// G/T
			case 'M':
// A/C
			case 'S':
// G/C
			case 'N':
// A/T/G/C
			case 'B':
// C/G/T
			case 'D':
// A/G/T
			case 'H':
// A/C/G
			case 'V':
// A/C/G
			case 'W':
				// A/T
				return true;
			case '-':
				// Dash ALWAYS means a gap
				return true;
			case '?':
				// ? means MISSING INFORMATION.
				// if you want to say "might be anything",
				// please use 'N'!
				return true;
			case '_':
				// _ means a gap before or after the sequence itself (non-internal gap)
				// in case we want to consider this different from the
				// sequence itself;
				return true;
		}
		// Nothing! It's wrong! It's all, totally, completely, frikkin' WRONG!
		// *panics* *dies*
		return false;
	}

	/**
	 * Is this character a purine (adenine, guanine).
	 *
	 * Note that characters which are both purine or pyrimidine (say, [ACT]),
	 * will return true here.
	 */
	public static boolean isPurine(char ch) {
		if(ch == 'R' || ch == 'A' || ch == 'G')
			return true;
		return false;
	}	

	/**
	 * Returns true if this is the 'missing' character.
	 * By current specification, this is '?'.
	 */
	public static boolean isMissing(char ch) {
		return (ch == '?');
	}

	/**
	 * Returns true if Sequence thinks of this character
	 * as the gap character. By current specification, this is the
	 * '-' or '_' (external).
	 */
	public static boolean isGap(char ch) {
		return ((ch == '-') || (ch == '_'));
	}

	/**
	 * Returns true if Sequence thinks of this character
	 * as the internal gap character. By specification,
	 * this is the '-' character.
	 */
	public static boolean isInternalGap(char ch) {
		return (ch == '-');
	}

//
//	7.	ENTIRE SEQUENCE FUNCTIONS. The following functions
//		compare, generate consensus, etc. of our sequence
//		compared to another sequence.
//

	/**
	 * Calculates the number of bases identical to each other.
	 * A simple loop, pretty much. It should be noted that we
	 * will only compare the length of the smaller string. Generally,
	 * the strings should be identical, but if they're not, we
	 * pretend the missing stuff is all '?' and NOT gaps. Use gaps
	 * if that's what you mean.
	 *
	 * Note that this is a sensible assumption: since we don't
	 * know what was beyond the last base of the smaller
	 * sequence, we assume that it IS significant, but missing.
	 * Since in this program, missing sequences and gaps are
	 * scored is similar ways, this should not make *too* much
	 * of a difference.
	 */
	public int countIdentical(DNASequence seq2) {
		char compare[] = seq2.getSequenceRaw().toCharArray();

		// find the shorter length
		int min = len;
		if(compare.length < min)
			min = compare.length;

		// walk the string 
		int count = 0;
		for(int x = 0; x < min; x++) {
			char ch1 = seq[x];
			char ch2 = compare[x];

			if(identical(ch1, ch2)) {
				count++;
			}
		}
		
		return count;
	}

	/**
	 * Calculates the number of bases which have been 'transversioned'.
	 * i.e. where a purine has become a pyrimidine, or vice versa.
	 *
	 * Note that this is a sensible assumption: since we don't
	 * know what was beyond the last base of the smaller
	 * sequence, we assume that it IS significant, but missing.
	 * Since in this program, missing sequences and gaps are
	 * scored is similar ways, this should not make *too* much
	 * of a difference.
	 */
	public int countTransversions(DNASequence seq2) {
		char compare[] = seq2.getSequenceRaw().toCharArray();

		// find the shorter length
		int min = len;
		if(compare.length < min)
			min = compare.length;

		// walk the string 
		int count = 0;
		for(int x = 0; x < min; x++) {
			char ch1 = seq[x];
			char ch2 = compare[x];

			if(
				(isPurine(ch1) && isPyrimidine(ch2)) ||
				(isPurine(ch2) && isPyrimidine(ch1))
			) {
				count++;
			}
		}
		
		return count;
	}

	/**
	 * Generates the consensus sequence for these two sequences.
	 */
	public DNASequence getConsensus(DNASequence seq2) throws SequenceException {
		char 		compare[] 	= seq2.getSequenceRaw().toCharArray();
		StringBuffer	buff 		= new StringBuffer();

		// find the longer length
		int max = len;
		if(compare.length > max)
			max = compare.length;

		// walk the string
		for(int x = 0; x < max; x++) {
			char ch1, ch2;
		       
			if(x < seq.length)
				ch1 = seq[x];
			else
				ch1 = '?';
			
			if(x < compare.length)
				ch2 = compare[x];
			else	
				ch2 = '?';

			buff.append(consensus(ch1, ch2));
		}
		
		return new DNASequence("Consensus of " + getFullName() + " and " + seq2.getFullName(), buff.toString().replace('_', '-'));
	}

	/**
	 * Returns the "overlap" - the length shared between this and another Sequence.
	 * Basically, the length of the sequence, without counting
	 * gaps and missing data.
	 */
	public int getOverlap(DNASequence seq2) {
		return getSharedLength(seq2);
	}

	/**
	 * Returns the length shared between this and another Sequence.
	 * Basically, the length of the sequence, without counting
	 * gaps and missing data.
	 *
	 * Rapidly becoming my least-liked function in TaxonDNA,
	 * I might add.
	 */
	public int getSharedLength(DNASequence seq2) {
		char 		compare[] 	= seq2.getSequenceRaw().toCharArray();

		// find the shorter length
		int min = len;
		if(compare.length < min)
			min = compare.length;

		// walk the wire
		int count = 0;
		for(int x = 0; x < min; x++) {
			char ch1, ch2;

			ch1 = seq[x];
			ch2 = compare[x];
		      
			if(ch1 == '?' || ch2 == '?') {
				// missing data is ignored, always
			} else if(isGap(ch1) || isGap(ch2)) {
				if(
						(isInternalGap(ch1) && !isInternalGap(ch2) && isGap(ch2))
						 ||
						(!isInternalGap(ch1) && isInternalGap(ch2) && isGap(ch1))
				) {
					// err, the next 'if' gets confused if only one of the
					// sequences is an internal gap. If they are BOTH gaps,
					// then that's fine; if only one is an internal gap, it
					// will also work fine. but it WILL fail if only one
					// of the gaps are internal, and the other is EXTERNAL.
					//
					// We test that here.
					//
					
					// and, err, discard it without actually doing anything
					// very productive, right?
					//
				} else if(isInternalGap(ch1) || isInternalGap(ch2)) {
					// internal gaps are counted, as they are "informative"
					// *unless* we're K2P mode, in which case we ignore them
					if(pairwiseDistanceMethod == PDM_K2P)
						; // ignore
					else
						count++;
				} else {
					// all other gaps are ignored
				}
			} else {
				// everything except missing data and gaps are counted
				if(pairwiseDistanceMethod == PDM_K2P) {
					// in K2P mode, something which is ambiguous
					// (either a purine OR a pyrimidine) is really
					// the same as '?', and should be ignored.
					// However, as long as BOTH of them are 
					// UNAMBIGUOUSLY one or the other, we're
					// good to go.
					// 
					if(isPurine(ch1) && isPyrimidine(ch1)) {
						// ch1 is BOTH. ignore!
					} else if(isPurine(ch2) && isPyrimidine(ch2)) {
						// ch2 is BOTH. ignore!
					} else {
						count++;
					}
				} else if(pairwiseDistanceMethod == PDM_TRANS_ONLY) {
					// if it's NOT obviously a purine or a pyramidine,
					// we should ignore it.
					if(
						(!isPurine(ch1) && !isPyrimidine(ch1)) ||
						(!isPurine(ch2) && !isPyrimidine(ch2))
					)
						;		// ignore
					else
						count++;	// count

				} else
					count++;
			}
		}
		
		return count;
	}

	/**
	 * Calculates the Kimura 2-parameter pairwise distance between us and another Sequence.
	 *
	 * The formula is as follows:
	 * 	d = -(1/2) * ln(w1) - (1/4) * ln(w2)
	 * 	w1 = 1 - 2P - Q
	 * 	w2 = 1 - 2Q
	 *
	 * 	where:
	 * 		P = freq(transitions)
	 * 		Q = freq(transversions)
	 *
	 * so: we walk along the string, counting nP and nQ as we go,
	 * as well as nChars (which is everything except '?' and '_').
	 */
	public double getK2PDistance(DNASequence seq2) {
		int nTransversions = 0;
		int nTransitions = 0;
		int n = 0;
		char compare[] 	= seq2.getSequenceRaw().toCharArray();

		// find the shorter length
		int min = len;
		if(compare.length < min)
			min = compare.length;

		for(int x = 0; x < min; x++) {
			char ch1, ch2;

			ch1 = seq[x];
			ch2 = compare[x];
		      
			if(ch1 == '?' || ch2 == '?') {
				// ignore missing data
			} else if(isGap(ch1) || isGap(ch2)) {
				// ignore gaps, too.
			} else {
				// but: is it a transition or transversions?
				if(!isPurine(ch1) && !isPyrimidine(ch1)) {
					// ch1 is ambiguous
					continue;
				} else if(!isPurine(ch2) && !isPyrimidine(ch2)) {
					// ch2 is ambiguous
					continue;
				} else {
					// everything except missing data, gaps, and ambiguous are counted
					n++;
		
					if(ch1 == ch2) {
						// identical!
						continue;
					}

					if(
						(isPurine(ch1) && isPurine(ch2)) || 
						(isPyrimidine(ch1) && isPyrimidine(ch2))
					) {
						// they're both purines/pyrimidines
						nTransitions++;
					} else if(
						(isPurine(ch1) && isPyrimidine(ch2)) ||
						(isPyrimidine(ch1) && isPurine(ch2))
					) {
						nTransversions++;
					} else {
						throw new RuntimeException("In getK2PDistance, comparing this=" + this + " ['" + ch1 + "'] with=" + seq2 + " ['" + ch2 + "']: Unexpected branch of code encountered. Something is very, very wrong.");
					}
				}
			}
		}

		double w1 = 1.0 - (2.0 * ((double)nTransitions)/n) - (((double)nTransversions)/n);
		double w2 = 1.0 - (2.0 * ((double)nTransversions)/n);

		// note that Math.log(double) is really Math.ln(double), i.e. it will return
		// the natural logarithm, not the common logarithm.
		double distance = (-0.5 * Math.log(w1)) - (0.25 * Math.log(w2));

//		System.err.println("Distance = " + distance + ", nTransitions = " + nTransitions + ", nTransversions = " + nTransversions + ", n = " + n);
//
		if(distance <= 0)	// if we're here, we can't be -1
			return 0;	// at the same time, this will stop the '-0' distances from appearing.

		return distance;
	}

//
//	8.	PAIRWISE DISTANCE CACHE. We keep a track of all the pairwise distances generated,
//		tagged against their UUIDs. 
//		1)	Right now we're using Sequence NAMES, which is stupid.
//		2)	UUIDs can be reset by Sequences being disposed(), as well as
//			by a changeSequence(). Both of these should cause the necessary
//			changes in this code section.
//		3)	We rely on UUID having two properties:
//			1.	UUID.toString() is guaranteed to be a string as unique as the UUID itself.
//			2.	UUID.equal(UUID) works	
//		

	private static Hashtable pairwise_buffer = new Hashtable();	// maps '<UUID1>' to a Hashtable, which
									// in turn maps '<UUID2>' to a pairwise
									// distance (specified as a Double)
	/**
	 * Checks to ensure that the cache is in a working, consistent state.
	 * Ha. Right now, we just check if we're approaching an upper memory 
	 * limit (defined in Settings), and if we are, we kill the entire
	 * cache to date and start over. It's a slowdown, but it'll help
	 * avoid those nasty OutOfMemoryExceptions
	 */
	private static void checkPairwiseCache() {
		Runtime runtime = Runtime.getRuntime();
		long memoryTotal = runtime.maxMemory(); 
		long memoryUsed = runtime.totalMemory() - runtime.freeMemory();

		if(((double)memoryUsed/memoryTotal) > Settings.PairwiseCacheMemoryUsageLimit)
			clearPairwiseCache();
	}

	/**
	 * Clears the pairwise cache.
	 */
	public static void clearPairwiseCache() {
		pairwise_buffer = new Hashtable();
		System.gc();		
	}
	
	/**
	 * Returns the cached distance stored against (seq1, seq2).
	 * We synchronize this, because I'm planning for 'dispose'
	 * to clean up its act. 
	 */
	private Double getCachedDistance(DNASequence seq1, DNASequence seq2) {
		synchronized(pairwise_buffer) {
			// this is to ensure that we only do a half-table
			if(seq1.compareByFullName(seq2) < 0)
				return getCachedDistance(seq2, seq1);
			
			// get the inner hashtable - if there is one
			Hashtable inner = (Hashtable) pairwise_buffer.get(seq1.getUUID());
			if(inner == null)
				return null;

			// return the inner table's value (might be null)
			Double result = (Double) inner.get(seq2.getUUID());
			return result;
		}
	}

	/**
	 * Sets the cached distance for (seq1, seq2)
	 */
	private void setCachedDistance(DNASequence seq1, DNASequence seq2, Double distance) {
		synchronized(pairwise_buffer) {
			// this is to ensure we only do a half-table
			if(seq1.compareByFullName(seq2) < 0) {
				setCachedDistance(seq2, seq1, distance);
				return;
			}
			
			// get the inner hashtable - or make one
			Hashtable inner = (Hashtable) pairwise_buffer.get(seq1.getUUID());
			if(inner == null) {
				// there is no inner hashtable ... yet!
				inner = new Hashtable();
				pairwise_buffer.put(seq1.getUUID(), inner);
			}

			// put the value in (but we don't care if this succeeds or not)
			inner.put(seq2.getUUID(), distance);
		}
	}

	/**
	 * Once this Sequence is 'disposed', go through checking where
	 * we're being stored in the table, and FREE us!
	 */
	private void uncacheSequence(DNASequence seq) {
		synchronized(pairwise_buffer) {
			// step 1: does seq have it's own inner Hashtable?
			pairwise_buffer.remove(seq.getUUID());

			// step 2: delete EVERYTHING
			Iterator keys = pairwise_buffer.keySet().iterator();
			while(keys.hasNext()) {
				Hashtable inner = 	(Hashtable) pairwise_buffer.get(keys.next());
				
				// don't know if it exists, but can't hurt to try
				inner.remove(seq.getUUID());
			}
		}
	}
	
	/**
	 * Calculate the uncorrected pairwise distance. If we have inadequate overlap,
	 * we will return -1.0d. You can change the minimum overlap used by using the
	 * static functions specified above.
	 */
	public double getPairwise(DNASequence seq2) {
		double distance = 0;

		checkPairwiseCache();

		Double buffer = getCachedDistance(this, seq2);
		if(buffer != null)
			return buffer.doubleValue();

		distance = getPairwiseNoBuffer(seq2);
		
		setCachedDistance(this, seq2, new Double(distance));
		return distance;
	}

	/**
	 * Calculate the uncorrected pairwise distance. If we have inadequate overlap,
	 * we will return -1.0d. You can change the minimum overlap used by using the
	 * static functions specified above.
	 *
	 * Unlike getPairwise(), this function is guaranteed not to cache the result,
	 * nor to use the cached result in any way.
	 * 
	 */
	public double getPairwiseNoBuffer(DNASequence seq2) {
		int shared	=	getSharedLength(seq2);
		double distance = 0;

		if(shared >= minOverlap) {
			switch(pairwiseDistanceMethod) {
				case PDM_K2P:
					return getK2PDistance(seq2);
				case PDM_TRANS_ONLY:
					return ((double)countTransversions(seq2))/shared;
				default:
				case PDM_UNCORRECTED:
					return 1.0 - ((double)countIdentical(seq2)/shared);
			}
		} else {
			// special value to indicate inadequate overlap
			return -1.0;
		}
	}

//
// 	9.	INTERNAL FUNCTIONS
//
	/**
	 * Checks the "sanity" of this sequence, ensuring that no
	 * illegal basepairs are used, etc.
	 *
	 * @throws SequenceException if the sanity tests fail.
	 */
	private void doSanityChecks(char seq[], int len) throws SequenceException {
		// is the length matching up?
		if(seq.length != len)
			throw new SequenceException(getFullName(), 
					"The actual length of sequence '" + getFullName() + 
					"', " +  seq.length  + " bp, does not match its reported length, " + 
					len + " bp. This is most likely a programming error. " + 
					"Please contact the programmer.");

		// are all elements actually base pairs?
		// also: count the ambiguous
		ambiguous = 0;
		for(int x = 0; x < len; x++) {
			if(seq[x] == 'U' || seq[x] == 'u')
				throw new SequenceException(getFullName(),
						"This program currently supports only DNA sequences! I found a Uracil at index " + x);
			if(!isValid(seq[x]))
				throw new SequenceException(getFullName(), 
						"An illegal base '" + seq[x] + "' was found in sequence '" + 
						getFullName() + "' at index " + x);
			
			if(isAmbiguous(seq[x]))
				ambiguous++;
		}
	}


	public void dispose() {
		uncacheSequence(this);
	}

	/**
	  Converts external gaps to missing characters. Basically just a raw replace of '_'s to '?'s.
	  */
	public void convertExternalGapsToMissingChars() {
		for(int x = 0; x < seq.length; x++) {
			if(seq[x] == '_')
				seq[x] = '?';
		}
	}

		public String toString() {
		if (len < 20) {
			return "DNA.Sequence(" + getName() + ", length: " + len + "): " + new String(seq);
		} else {
			return "DNA.Sequence(" + getName() + ", length: " + len + ")";
		}
	}

			public void test(TestController test, DelayCallback delay) {
		DNASequence seq;
		int oldMinOverlap = DNASequence.getMinOverlap();
		DNASequence.setMinOverlap(1);
		test.begin("DNA.Sequence");
		test.beginTest("Create a sequence");
		try {
			seq = new DNASequence("Fasta sequence (this one has WEIRD symbols in it, like  ~ and # and, oh, I don\'t know, ~!@!%~!#~$!$!@!%!!!!!!!!!!!!!!!!!!@#$%^&*()K_+{}|:\"<>?~[]\\;\',./=-098789194678`90260`92836`0892846`129340`927380`712387`19073190¡¡ñõóá also, it has every single valid DNA sequence string thingie thingie in the Sequence", "----ACTG?WRKYSMBHDVN----????");
			if (seq.getFullName().length() == 304 && seq.getFullName().charAt(216) == '–' && seq.getLength() == 28 && seq.getActualLength() == 24) {
				test.succeeded();
			} else {
				test.failed("There was an error reading the test sequence, it came out as: " + seq);
			}
		} catch (SequenceException e) {
			test.failed("The test sequence threw a SequenceException: " + e);
		}
		test.beginTest("The really small sequences with known values suite");
		try {
			DNASequence seq1 = new DNASequence("1", "AAAAAAAAAAAAAAAAAAAAAAAAA---------------------------------------------------------------------------");
			DNASequence seq2 = new DNASequence("2", "----------AAAAATTTTT?????---------------------------------------------------------------------------");
			if (seq1.getSharedLength(seq2) != 10) {
				test.failed("The overlap is reported as " + seq1.getSharedLength(seq2) + " when it\'s really 10.");
			} else {
				if (seq1.getPairwise(seq2) != 0.5) {
					test.failed("The pairwise distance is reported as " + seq1.getPairwise(seq2) + " when it\'s really 50%.");
				} else {
					test.succeeded();
				}
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Double gapped overlaps");
		try {
			DNASequence seq1 = new DNASequence("1", "AAA---CCCCCCTTTTTTTTTTTTTTTTTTTTTTTT---TTTTTTTTTTTT");
			DNASequence seq2 = new DNASequence("2", "AAA---CCCCCCTTTTT----------------------------------");
			if (seq1.getSharedLength(seq2) != 17) {
				test.failed("The shared length is " + seq1.getSharedLength(seq2) + ", not 12");
			} else {
				test.succeeded();
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Subsequence test 1: When is a subsequence more than a sequence?");
		try {
			DNASequence seq1 = new DNASequence("1", "AAATTTCCCGGG");
			if (seq1.getSubsequence(1, 12).getSequence().equals("AAATTTCCCGGG")) {
				test.succeeded();
			} else {
				test.failed("seq1.getSubsequence(1, 12).getSequence() returns " + seq1.getSubsequence(1, 12).getSequence());
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Subsequence test 2: Too small for ya?");
		try {
			DNASequence seq1 = new DNASequence("1", "AAATTTCCCGGG");
			if (seq1.getSubsequence(1, 10).getSequence().equals("AAATTTCCCG")) {
				test.succeeded();
			} else {
				test.failed("seq1.getSubsequence(1, 10).getSequence() returns " + seq1.getSubsequence(1, 10).getSequence());
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Subsequence test 3: Oops, too big now!");
		try {
			DNASequence seq1 = new DNASequence("1", "AAATTTCCCGGG");
			if (seq1.getSubsequence(1, 20).getSequence().equals("AAATTTCCCGGG--------")) {
				test.succeeded();
			} else {
				test.failed("seq1.getSubsequence(1, 20).getSequence() returns " + seq1.getSubsequence(1, 20).getSequence());
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Subsequence test 4: I give you crap! And you must eat it!");
		try {
			DNASequence seq1 = new DNASequence("1", "AAATTTCCCGGG");
			if (seq1.getSubsequence(-10, -5).getSequence().equals("")) {
				test.succeeded();
			} else {
				test.failed("seq1.getSubsequence(-10, -5).getSequence() returns " + seq1.getSubsequence(-10, -5).getSequence());
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Subsequence test 5: What do you mean, you can\'t handle gaps?");
		try {
			DNASequence seq1 = new DNASequence("1", "AAATTT-CCGGG");
			if (seq1.getSubsequence(1, 8).getSequence().equals("AAATTT-C")) {
				test.succeeded();
			} else {
				test.failed("seq1.getSubsequence(1, 20).getSequence() returns " + seq1.getSubsequence(1, 20).getSequence() + " instead of AAATTT-C");
			}
		} catch (SequenceException e) {
			test.failed("There was SequenceExceptions: " + e);
		}
		test.beginTest("Testing \'complement\'s");
		{
			String str = "ACTG-RYKM-SW-BDHV-N";
			String result = "TGAC-YRMK-WS-ACGT-N";
			StringBuffer buff = new StringBuffer();
			for (int x = 0; x < str.length(); x++) {
				buff.append(DNASequence.complement(str.charAt(x)));
			}
			if (buff.toString().equals(result)) {
				test.succeeded();
			} else {
				test.failed("The reverse complement of " + str + " is " + result + ", not " + buff.toString() + "!");
			}
		}
		test.beginTest("Testing \'getSpeciesName(size)\'");
		try {
			DNASequence seq_trex = new DNASequence("Tyrannosaurus rex", "AAAAAAAAAAAAAAAAAAAAAAAAA");
			if (seq_trex.getFullName(5).equals("T rex") && seq_trex.getFullName(9).equals("Tyran rex") && seq_trex.getFullName(50).equals("Tyrannosaurus rex")) {
				test.succeeded();
			} else {
				test.failed("I shrink the name with getSpeciesName! e.g. \"Tyrannosaurus rex\".getFullName(10) = \'" + seq_trex.getFullName(10) + "\'");
			}
		} catch (SequenceException e) {
			test.failed(e.toString());
		}
		test.beginTest("Testing strange off-by-one actual length bug");
		try {
			seq = new DNASequence("Testing", "TTTAAATGGCCGCAGTATACTAACTGTGCAAAGGTAGCATAATCATTAGTCTTTTAATTGAAGGCTGGTATGAATGGTTGGACGAGATATTAACTGTTTCATAAAAATTTATATTAGAATTTTATTTTTTAGTCAAAAAGCTAAAATTTATTTAAAAGACGAGAAGACCCTATAAATCTTTATATTTAGGTTATTATAATTTTATAGATTATTTTTATTATAATGATTAATAATATTTTATTGGGGTGATATTAAAATTTAATGAACTTTTAATTGTTAAAA--TCATTAATTTATGAATAAGTGATCCGTTAT-TAACGATTAAAAAAATAAGTTACTTTAGGGATAACAGCGTAATTTTTTTGGAGAGTTCTTATCGATAAAAAAGATTGCGACCTCGATGTTGGATTAAGATATAATTTTAGGTGTAGCCGCTTAAATTTTAAGTCTGTTCGACTTTTA?ATTGATGCTCCTGGTCACAGAGATTTCATCAAGAACATGATCACTGGTACATCTCAAGCCGATTGTGCCGTATTGATTGTTGCTGCCGGTACTGGTGAATTCGAAGCCGGTATCTCCAAGAACGGTCAAACTCGCGAACACGCTTTGTTGGCCTTCACCTTGGGTGTCAAACAATTGATTGTAGGTGTCAACAAGATGGATTCCTCTGAACCACCTTACAGCGAAGCCCGTTATGAGGAAATCAAGAAGGAAGTCTCCTCTTACATCAAGAAGATCGGTTACAATCCCGCTGCTGTTGCCTTCGTACCCATCTCCGGCTGGCACGGTGATAACATGTTGGAACCCTCTTCCAACATGCCTTGGTTCAAGGGATGGGCCGTCGAACGTAAAGAAGGTAAGGCTGATGGTAAGACTCTTATCGAAGCTTTGGATGCTATATTGCCTCCATCTCGTCCCACCGACAAGCCCCTGCGTTTACCCTTGCAGGATGTTTACAAAATCGGTGGTATCGGCACAGTACCCGTCGGTCGTGTCGAAACTGGTATTTTGAAACCCGGTACCGTTGTCGTCTTCGCTCCCGCTAACATTACCACTGAAGTCAAGTCCGTTGAAATGCATCACGAAGCTCTCACCGAAGCTGTTCCCGGTGACAACGTTGGTTTCAACGTTAAGAACGTCTCCGTCAAGGAATTGCGTCGTGGCTACGTCGCTGGTGATTCCAAAGTCAGTCCCCCCAGAGGTGCTGCTGACTTCACCGCTCAAGTCATCGTATTGAACCATCCCGGTCAAATCTCTAACGGTTATACTCCCGTATTGGATTGTCACACCGCTCATATTGCTTGCAAATTCGCCGAAATCAAGGAGAAGGTCGATCGTCGTTCCGGTAAGACCACCGAAGAAGCACCCAAATTCATCAAGTCTGGTGATGCTGCCATCGTCAACTTGGTTCCTTCAAAACCTTTGTGCGTGACGATTATGGTCCTGAATCGAGAGGTTTCGTAGAAAATTCATATCTTGCCGGTCTGACGCCTTCGGAGTTCTATTTCCACGCTATGGGTGGTCGTGAAGGTCTTATTGATACTGCTGTAAAGACTGCGGAAACTGGTTATATTCAACGTCGTTTGATAAAGGCTATGGAATCTGTCATGGTAAACTACGACGGTACTGTCCGTAATTCTGTGGGACAACTTATTCAGTTGCGTTACGGTGAAGACGGGTTGGCCGGTGAAACAGTAGAGTTCCAGAATTTGCCCACCGTCAAGCTATCGAATAAGTCCTTTGAAAAGCGATTCAAATTCGATTGGTCTAATGAACGGTACATGCGCAAAGTTTTTACGGATGAGGTCATTAAGGATCTAAGTGAAAGTGGCAATGCTTTGCCCCAACTGGAAGTCGAGTGGGAACAATTGTGTCGCGATCGTGAAGCTTTGAGAGAGATTTTCCCAAATGGTGAATCGAAAGTTGTATTGCCATGTAACCTTCATAGAATTAATTTTACCGGGATTCGGAATAATCTCACACATTATCAGCCAAGAATCAGGGAAAAAAGAAACATTCGGTTCTTTAGGGATAATCTACGCTATACTAGCTATTGGTCTATTAGGATTTATTGTATGAGCTCACCACATATTTACTGTAGGAATAGATGTAGATACACGGGCTTATTTTACATCTGCAACAATAATTATTGCTGTGCCAACAGGAATTAAAATTTTTAGTTGACTAGCTACTTTATACGGAACTCAATTAAATTATTCCCCTGCTACTTTATGAGCTTTAGGATTTGTTTTTTTATTCACAGTAGGAGGGCTAACAGGAGTTGTACTAGCTAACTCTTCCTTAGACATTATTTTACACGATACTTATTATGTAGTAGCCCATTTTCATTATGTGTTATCAATAGGAGCTGTATTTGCTATTATAGCAGGATTTGTGCATTGATACCCCTTATTTACTGGATTAACAATAAATAATACCCTATTAAAAAGCCAATTTATTATTATATTTATTGGAGTTAATTTAACATTTTTCCCCCAACATTTCTTAGGTCTAGCCGGTATACCTCGGCGGTATTCAGATTACCCTGATGCCTATACAACATGAAATGTAGTTTCAACTATTGGCTCAACAATCTCTTTACTCGGAATTTTATTTTTCTTTTTTATCATTTGAGAAAGTTTAGTTTCTCAACGACAAGTATTATTCCCAGTACAATTAAACTCTTCAATTGAATGACTACAAAATACACCCCCAGCAGAACATAGTTACTCTGAATTACCTTTATTAACTAATTTCTATCCCTTATTTAGGGATTGACTTAGTACAATGAGTGTGAGGAGGATTCGCTGTTGATAACGCCACTCTTACTCGATTTTTCACTTTCCATTTTATTTTACCCTTTATCGTTTTAGCTATAACAATAATTCATTTATTATTTTTACACCAAACTGGCTCAAATAACCCAATAGGGTTAAATTCTAATATTGACAAAATTCCTTTTCACCCATATTTTACTTACAAAGATATTGTAGGATTTATTATTATATTAATAATATTAATTTTATTAATTTTGATTAACCCTAACTTATTAGGAGACCCTGATAACTTTATCCCAGCCAATCCCCTAGTTACCCCAGTTCATATCCAACCTGAATGATATTTTTTATTCGCCTATGCTATTTTACGTTCAATTCCTAATAAATTAGGAGGAGTAATTGCTTTAGTCCTATCTATTGCAATTTTAGCTATTTTACCTTTTTACCACCTAAGAAAATTTCGAGGAATTCAATTTTACCCAATTAATCAAGTTTTATTTTGATTAATAGTAGTCACAGTAATTTTATTGACTTGAATCGGAGCTCGACCTGTAGAAGACCCTTACGTATTAGTTGGACAAATTTTAACTATTATTTATTTCTCTTATTTTATATTTAACCCCCTAATCATTAAATGATGACTCAATTCTGACAATCGATTTGCACGTCAGAACTGTTTCGGTCTTCCATCAGGGTTTCCCCTGACTTCAACCTGATCAAGTATAGTTCACCATCTTTCGGGTCACAGCATATATGCTCAAGGTACGCTCTAGTTAGTGGCATAAATAATATAAATATTATTATACATAACTGTATAGAACGCCCCGGGATTGAATTAATAGACTATAAAA-----TAGACCTAAAAACTAATCCCATTATATAA-----GTTATGTTAATTTCGCTATTAGGTTTTT-AATTCCCAATAACTTGCAAATATGTTAGACTCCTTGGTCCGTGTTTCAAGACGGGTCCCGAAGGTATCCTGAATCTTTCGCATTGTTAATCATATAAGTGCATA-TAAT-G-AACATAAAAATC-ATTGATCAAATACGCTATTATAGAATATATAAAAATA--TATTCAAGCACTATATATAA-TAAATCTATCAACACTTTATCAAATCAAAAGCATTTATTCTATGTTAAATT-GCAAGCAAAA-TAATTTGAATAAACTAAA--GC-AAT-GATCTTATAATAAAT-CTGTTTT-GTTAATAGATTACAATGTCCTTATATGGAAAAAATGCACACCATTATTA-TAATATTATAAA--TATTAAAATCATAATGATGAATTTTCCATAATGGATATTCAGGTTCATCGGGCTTAACCTCTAAGCAGTTTCACGTACTATTTAACTCTCTATTCAGAGTTCTTTTCAACTTTCCCTCACGGTACTTGTTTACTATCGGTCTCATGGTTATATTTAGTTTTAGATGGAGTTTACCACCCACTTAGTGCTGCACTATCAAGCAACACGACTCTTTGGAAATGTCATCTAGTAATCATCAACGTTATACGGGCCTGGCACCCTCTTTGGGTAAATGGCCTCATTTAAGAAGGAC-TTAAATCGTTAATTTCTCATACTAGAAATTTGCCATTCCATACACTGCATCTCACATTTGCCATAGAGACAAAGTTATTTCAATTTTCTTTCACAATACTATTGCACTATAATTAAAATTATTTTTTCTATATTAAATACTAAAACA-AATTTTTATATAATTATTTTTAATAATTTAAATTTTTAAAA---AATATAAATTAATAAATAAAATCTAA-TCAATTTATATTGATTTGCACAAAAATCTTTTCAATGTAAATGAAATACTTTACTTTATAAGCTTTAAATTGCATTCTAGGTACACTTTCCAGTACATCTACTATGTTACGACTTATCTTACCTTAATAATAAGAGTGACGGGCGATGTGTGCATATTTTAGAGCTAAAATCAAATTATTTATCTTTATAATTTTACTATCAAATCCACCTTTAATAAATTTTT--CAAATTT-ATATCCGTA--TAAATAAATTTATTGTAACCCATTACTTCTTAAATATAAGCTACACCTTGATCTGATATATTTTCTTTTTAAAAA-TTTTGAAAATTAACATTCTTATAAAATATTCTAATAACGACGGTATATAAACTGACTACAAATTTAAGTAAGGTCCATCGTGGATTATCGATTATAGAACAGGTTCCTCTGAATAGACTAAAATACCGCCAAATTTTTTAAGTTTCAAGAACATAACTA");
			// the '-1' is for the single missing in the middle there somewhere
			if (seq.getActualLength() != (seq.getLength() - 1)) {
				test.failed("Actual length = " + seq.getActualLength() + ", length = " + seq.getLength() + ", raw sequence is " + seq.getSequenceRaw());
			} else {
				test.succeeded();
			}
		} catch (SequenceException e) {
			test.failed(e.toString());
		}
		test.done();
		DNASequence.setMinOverlap(oldMinOverlap);
	}
}
