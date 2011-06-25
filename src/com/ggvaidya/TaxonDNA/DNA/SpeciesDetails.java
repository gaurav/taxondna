
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
import com.ggvaidya.TaxonDNA.Common.*;

/**
 * SpeciesDetails is a map which allows you get get the
 * SpeciesDetail on any species. Note that you really
 * shouldn't be allowed to access all this directly.
 * (hence the private constructors; I assume there's
 * a better way of doing this I don't know about)
 * So you call SequenceList.getSpeciesDetails(),
 * from which you can countSpecies(), countSpeciesWithMultipleSequences(),
 * and so on, as well as getSpeciesIterator, which
 * iterates over the SpeciesDetail objects.
 *
 * I would _love_ to find an easier way of doing
 * this, so if you have one, PLEASE let me know!
 * Thanks!
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

public class SpeciesDetails {
	SequenceList	list =						null;
	SequenceList	seqs_with_conspecifics =	null;

	// counts
	private int count_sequences =					0;
	private int count_species =						0;
	private int count_sequences_without_a_name =	0;	// sequences which don't have a speciesName
	private int count_sequences_invalid =			0;

	private HashMap<String, SpeciesDetail> details	= new HashMap<String, SpeciesDetail>();

	/**
	 * Return the details. Yes, all of them.
	 */
	public Map<String, SpeciesDetail> getDetails() {
		return (Map<String, SpeciesDetail>) details.clone();
	}

	/**
	 * This class can only be created with a SequenceList.
	 */
	private SpeciesDetails() {}	

	/**
	 * Tell me which list you need to calculate, and give me a DelayCallback
	 * to report to, and I'll figure things out.
	 */
	public SpeciesDetails(SequenceList a_list, DelayCallback delay) throws DelayAbortedException {

		// Make sure the list is valid.
		this.list = a_list;
		if(list == null)
			return;

		// setup the arrays which track the data
		details =					new HashMap<String, SpeciesDetail>();
		seqs_with_conspecifics =	new SequenceList();

		// Time to start work!
		list.lock();
		if (delay != null)
			delay.begin();

		// setup our initial variables
		for(Object o_seq: list) {
			Sequence	seq =			(Sequence) o_seq;
			String		species_name =	seq.getSpeciesName();

			// Update delay.
			if (delay != null) {
				try {
					delay.delay(count_sequences, list.count());
				} catch (DelayAbortedException e) {
					list.unlock();
					throw e;
				}
			}

			// What do we know already?
			count_sequences++;

			// Can't do any processing without species names.
			if(species_name == null) {
				count_sequences_without_a_name++;
				continue;
			}

			// Is this the first time we've seen this species?
			if(!details.containsKey(species_name)) {
				// Yes, it's the first of its kind.
				details.put(species_name, new SpeciesDetail(species_name));
				count_species++;
			} else {
				// We've seen this species name before!
				// This species name has conspecifics.

				// Unfortunately, this is a number we're not
				// actually interested in for now.
			}

			// Add this sequence to the detail.
			details.get(species_name).add(seq);
			
			//System.err.println("Species " + species_name + " has been assigned species detail " + details.get(species_name));

			// Count any invalid sequences (total length < overlap).
			if(seq.getLength() < Sequence.getMinOverlap())
				count_sequences_invalid++;
		}
		
		// All done!
		list.unlock();

		if (delay != null) {
			delay.end();
		}
	}

	/**
	 * @return The number of sequences passed to SpeciesDetails.
	 */
	public	int	getSequencesCount() 	{ return count_sequences; }

	/**
	 * @return The number of species in this SpeciesDetails.
	 */
	public	int getSpeciesCount()		{ return count_species; }

	/**
	 * @return The number of SpeciesDetail objects in this
	 * SpeciesDetails.
	 */
	public	int count()					{ return details.size(); }

	/**
	 * @param The species name to retrieve details on.
	 * @return The SpeciesDetail object corresponding to that species name.
	 */
	public	SpeciesDetail getSpeciesDetailsByName(String name) {
		return (SpeciesDetail) details.get(name);
	}

	/**
	 * @return The number of sequences without a species name.
	 */
	public	int getSequencesWithoutASpeciesNameCount() 
		{	return count_sequences_without_a_name; }

	/**
	 * @return The number of invalid sequences (sequences shorter than
	 *		the minimum overlap).
	 */
	public 	int getSequencesInvalidCount()	
		{	return count_sequences_invalid; }

	/**
	 * @return The number of species with atleast one valid sequences.
	 *
	 * Note that one valid sequence actually means *two* valid sequences,
	 * as they will be valid to each other.
	 */
	public 	int getValidSpeciesCount() {
		int species_with_valid_sequences = 0;
		
		for(SpeciesDetail sdet: details.values()) {
			if(sdet.getSequencesWithValidConspecificsCount() > 0)
				species_with_valid_sequences++;
		}
		
		return species_with_valid_sequences;
	}

	/**
	 * @return The number of sequences with atleast one valid conspecific
	 * match somewhere in the dataset.
	 */
	public int	getSequencesWithValidConspecificsCount() {
		int count_seqs_with_valid_consp = 0;

		for(SpeciesDetail sdet: details.values()) {
			count_seqs_with_valid_consp += sdet.getSequencesWithValidConspecificsCount();
		}

		return count_seqs_with_valid_consp;
	}

	/**
	 * Generate a count of sequences with valid conspecifics.
	 * This is only used in one place, so I'm going to do
	 * the generation here instead of butting into any of
	 * the other, already-working code.
	 * 
	 * @return A count of such sequences.
	 */
	public SequenceList getSequencesWithValidConspecifics() {
		SequenceList list_valid_consp = new SequenceList();

		for(Object o_seq: list) {
			Sequence seq =			(Sequence) o_seq;
			String species_name =	seq.getSpeciesName();

			if(species_name == null)
				continue;

			for(Object o_seq_inner: list) {
				Sequence seq_inner =	(Sequence) o_seq_inner;

				// Ignore identicals.
				if(seq_inner.equals(seq))
					continue;
				
				// Ignore sequences without species names.
				if(seq_inner.getSpeciesName() == null || seq_inner.getSpeciesName() == null)
					continue;

				// Conspecifics only.
				if(!seq_inner.getSpeciesName().equals(seq_inner.getSpeciesName()))
					continue;

				// Valid?
				if(seq_inner.hasMinOverlap(seq)) {
					// Ah! Valid non-identical conspecifics.
					list_valid_consp.add(seq);
					break;
				}
			}
		}

		return list_valid_consp;
	}

	/**
	 * Returns an iterator over the list of species names in the dataset.
	 * But we can't just give them an iterator over the species names,
	 * since they can't get the details easily. Or can they?
	 * Tricky stuff.
	 *
	 * Answer: we return an Iterator over Strings (the keys of our Hashtable).
	 * You can use getSpeciesDetailsByName(name) to get the SpeciesDetail
	 *
	 * As a free bonus, we give you names in Alphabetic Order!
	 */
	public 	Iterator getSpeciesNamesIterator()	
	{
		LinkedList ll = new LinkedList(details.keySet());
		Collections.sort(ll);
		return ll.iterator();
	}

/*
	public List getSpeciesNamesWithMultipleValidSequencesList() {
	}

	public Iterator getSpeciesNamesWithMultipleValidSequencesIterator() {
		return vector_speciesWithMultipleValidSequences.iterator();
	}
 * 
 */
}
