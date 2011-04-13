/*
    TaxonDNA
    Copyright (C) 2006, 2010-11	Gaurav Vaidya

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

import java.util.*;

/**
 * A SpeciesDetail object holds information about one particular species in a
 * SpeciesDetails map.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

public class SpeciesDetail {
	private String				species_name;
	private int					length_longestSequence = 0;
	private ArrayList<Sequence>	sequences = new ArrayList<Sequence>();

	/**
	 * You can't create a SpeciesDetail unless you
	 * provide the details.
	 */
	private SpeciesDetail() {}

	/**
	 * Our most useful constructor.
	 */
	public SpeciesDetail(String species_name) {
		this.species_name = species_name;
	}

	/**
	 * Add a sequence to this "species".
	 */
	public void add(Sequence seq) {
		if(!seq.getSpeciesName().equals(species_name))
			throw new RuntimeException("Tried to add " + seq + " to a species detail of " + species_name);

		// Process all the numbers.
		if(seq.getLength() > length_longestSequence) {
			length_longestSequence = seq.getLength();
		}

		// Add this sequence to our list.
		sequences.add(seq);
	}

	/**
	 * @return A list of sequences associated with this species detail.
	 */
	public List<Sequence> getSequences() {
		return (List<Sequence>) sequences;
	}

	/**
	 * @return The number of sequences in this species detail.
	 */
	public int getSequencesCount() {
		return sequences.size();
	}

	/**
	 * Determine how many sequences have a "valid match", i.e. a conspecific
	 * which has less than the minimum overlap.
	 *
	 * @return The number of such sequences.
	 */
	public int getSequencesWithValidConspecificsCount() {
		int valid_matches = 0;

		for(Sequence seq: sequences) {
			for(Sequence seq_inner: sequences) {
				if(seq_inner == seq) continue;

				if(seq.hasMinOverlap(seq_inner)) {
					valid_matches++;
					break;		// Will break out of inner, but not outer, loop.
				}
			}
		}

		return valid_matches;
	}

	/**
	 * Determine how many sequences *don't* have a valid match, i.e. a
	 * conspecific which has less than the minimum overlap.
	 *
	 * @return The number of such sequences.
	 */
	public int getSequencesWithoutValidConspecificsCount() {
		return getSequencesCount() - getSequencesWithValidConspecificsCount();
	}

	/**
	 * @return The length of the longest sequence in this SpeciesDetail.
	 */
	public 	int getLongestSequenceLength() {
		return length_longestSequence;
	}

	/**
	 * @return a list of GI numbers as a concatenated string.
	 */
	public String getGINumbersAsString() {
		StringBuilder buff = new StringBuilder();

		for(Sequence seq: sequences) {
			if(seq.getGI() != null)
				buff.append("gi|").append(seq.getGI()).append("| ");
			else
				buff.append("(Sequence with unknown GI number) ");
		}
		
		return buff.toString();
	}
}
