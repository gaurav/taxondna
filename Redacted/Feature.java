/**
 * A feature is a segment of a Sequence tagged with
 * some sort of metadata. They are stored in a vector
 * on the Sequences (which is allocated lazily, to
 * save memory for Fasta sequences).
 *
 * The best things about features is that you can
 * 'get' the feature as a subsequence 
 * (Feature.getSubsequence()). No, that's it,
 * really. But check out SpeciesInfo (I think)
 * if you want to see something funny you
 * can do (look for 'feature.(\d+)'). 
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2006	Gaurav Vaidya

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

public class Feature {
	private Sequence seq = null;
	private int from = 0;
	private int to = 0;

	// package private: do NOT call this directly!
	// use Sequence.addFeature(seq, from, to)
	Feature(Sequence seq, int from, int to) throws SequenceException {
		this.seq = seq;
		this.from = from;
		this.to = to;

		// this will throw a SequenceException if there's something funny going on
		seq.getSubsequence(from, to);

		// this will clean up the new Sequence ... we hope.
		Runtime.getRuntime().gc();
	}

	public Sequence getSequence() {
		return seq;
	}

	public Sequence getSubsequence() throws SequenceException {
		return seq.getSubsequence(from, to);
	}
}
