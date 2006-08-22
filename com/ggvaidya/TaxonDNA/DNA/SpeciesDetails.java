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

import java.util.*;		// Hashtables
import java.io.*;		// Input/output

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;


public class SpeciesDetails {
	SequenceList	list			=	null;

	// counts
	private int count_sequences 			= 0;
	private int count_species 			= 0;
	private int count_sequences_without_a_name 	= 0;	// sequences which don't have a speciesName

	private int count_sequences_invalid 	= 0;
	private int count_species_valid 	= 0;

	private Hashtable species		= new Hashtable();	// of type 'SpeciesDetail'

	private Vector vector_speciesWithMultipleValidSequences =
		new Vector();

	/**
	 * Ouch no you can't do not no please stop please
	 */
	private SpeciesDetails() {}	

	/**
	 * Tell me which list you need to calculate, and give me a DelayCallback
	 * to report to, and I'll figure things out.
	 */
	public SpeciesDetails(SequenceList list, DelayCallback delay) throws DelayAbortedException {
		this.list = list;

		if(list == null)
			return;

		list.lock();

			/*
			 * Strategy:
			 * 1.	Sort the list by name. Since this means by Genus' 'species
			 * 	name, so this means that conspecifics WILL be grouped together.
			 * 	This makes everything else a LOT simpler.
			 * 2.	We go through the list, adding each new species name into
			 *	a hashtable. Keys: species names, value: a special private
			 *	class (SpeciesData).
			 * 3.	We calculate statistics on the entire dataset as we go.
			 *
			 */

			if(delay != null)
				delay.begin();

			list.resort(SequenceList.SORT_BYNAME);		// sort alphabetically

			// setup our initial variables
			Iterator i = list.iterator();
			int count = list.count();

			// setup the arrays which track the data 
			species = new Hashtable();
	
			// variables for use during processing only
			String lastSpeciesName = "";
			Vector conspecifics = new Vector();

			// go loop!
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();
				String speciesName = (String) seq.getSpeciesName();

				// handle all the increments and delay callbacking here
				int increment = list.count()/100;
				if(increment == 0)
                        		increment = 1;

				if(delay != null && count_sequences % increment == 0) {
					try {
						delay.delay(count_sequences, list.count());
					} catch(DelayAbortedException e) {
						list.unlock();
						throw e;	
					}
				}

				// code begins! update the usual suspects
				count_sequences++;

				// is the current species name ""? if so, 
				// it's not really a species, so we
				// count it off and go on to the next one.
				//
				if(speciesName.equals("")) {
					count_sequences_without_a_name++;
					continue;
				}
			
				// now, let's see if our speciesName matches lastSpeciesName
				// if it matches: 	add the information on the new sequence to the "pile" we're 
				// 			accumulating on this species name
				// if it doesn't match: new species! write out our accumulated statistics onto this
				// 			speciesName, and reset lastSpeciesName.
				if(lastSpeciesName.equals("")) {
					// lastSpeciesName is "", so we need to 'prime' it to the first speciesName
					lastSpeciesName = speciesName;
					conspecifics.add(seq);

				} else if(speciesName.equals(lastSpeciesName)) {
					// same name. continue accumulating conspecifics.
					conspecifics.add(seq); 

				} else {
					// it's a new name! add up the tally ...
					species.put(lastSpeciesName, process_species(lastSpeciesName, conspecifics));
			
					// now begin accumulating stats on this brand new speciesName
					lastSpeciesName = speciesName;
					conspecifics = new Vector();
					conspecifics.add(seq);
				}
			}

		// the final "lastSpeciesName" does not get processed
		// so now, we process it.
		species.put(lastSpeciesName, process_species(lastSpeciesName, conspecifics));

		// All done!
		list.unlock();
		
		if(delay != null)
			delay.end();
	}

	private SpeciesDetail process_species(String name, Vector conspecifics) {
		int count_valid = 0;
		int count_invalid = 0;
		int largest_length = 0;

		if(name.equals(""))
			return null;

		count_species++;

		SpeciesDetail detail = new SpeciesDetail(name);

		// summarise information for this species 'name', with sequences 'sequences'
		Iterator i1 = conspecifics.iterator();
		while(i1.hasNext()) {
			Sequence seq_1 = (Sequence) i1.next();
			
			if(seq_1.getActualLength() > largest_length)
				largest_length = seq_1.getActualLength();
	
			if(!seq_1.getGI().equals("")) {
				detail.pushSequenceIdentifier("gi|" + seq_1.getGI() + "|");
			} else {
				detail.pushSequenceIdentifier("(Sequence with unknown GI)");
			}
			
			// Is this *sequence* invalid? (i.e. is its total size less than the minimum limit?)
			//
			if(seq_1.getActualLength() < Sequence.getMinOverlap()) {
				count_sequences_invalid++;	
			}
		
			Iterator i2 = conspecifics.iterator();
			boolean invalid_match = false;
			boolean valid_match = false;
			while(i2.hasNext()) {
				Sequence seq_2 = (Sequence) i2.next();

				if(seq_2.getPairwise(seq_1) == -1)
					invalid_match = true;
				else
					valid_match = true;
			}
	
			if(invalid_match && !valid_match) {
				// ONLY invalid matches on this sequence
				count_invalid++;
			} else {	
				// anything else must, by definition, be valid
				count_valid++;
			}
		}

		detail.setSequencesCount(conspecifics.size());
		detail.setSequencesWithValidMatchesCount(count_valid);				
		detail.setSequencesWithoutValidMatchesCount(count_invalid);
		detail.setLargestSequenceLength(largest_length);

		if(count_valid > 1) {
			count_species_valid++;
			vector_speciesWithMultipleValidSequences.add(name);
		}

		return detail;
	}

//
//	X.	GET functions
//
	public	int	getSequencesCount() 	{	return count_sequences;			}
	public	int 	getSpeciesCount() 	{	return count_species;			}
	// for ease of use etc.
	public	int 	count() 		{	return count_species;			}
	public	int 	getSequencesWithoutASpeciesNameCount() 
						{	return count_sequences_without_a_name;	}

	public 	int 	getSequencesInvalidCount()	{	return count_sequences_invalid;		}
	public 	int 	getValidSpeciesCount()	{	return count_species_valid;		}

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
		LinkedList ll = new LinkedList(species.keySet());
		Collections.sort(ll);
		return ll.iterator();
	}

	public	SpeciesDetail getSpeciesDetailsByName(String name) {
		return (SpeciesDetail) species.get(name);
	}
			
	public List getSpeciesNamesWithMultipleValidSequencesList() {
		return (List) vector_speciesWithMultipleValidSequences;
	}

	public Iterator getSpeciesNamesWithMultipleValidSequencesIterator() {
		return vector_speciesWithMultipleValidSequences.iterator();
	}
}
