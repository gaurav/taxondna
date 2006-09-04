/**
 * SortedSequenceList is a sequence list sorted along a varying criterion;
 * in the current version, this criterion is always the same: it's sorted
 * based on its distance to a specified 'query' sequence.
 *
 * This is going to work like this:
 * 1.	You create a SortedSequenceList, giving it a SequenceList.
 * 	-	We store a pointer to the SSL
 * 2.	You run a 'query' against your SortedSequenceList
 * 	-	We reinit from the SSL; we get rid of sequences
 * 		without adequate overlap. 
 * 	-	We resort our *current* list using a Comparator,
 * 		created especially for the occasion.
 * 	-	Free the comparator; unlock our sortedSSL for
 * 		continued access.		
 *   
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA 
    Copyright (C) 2005	Gaurav Vaidya

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

public class SortedSequenceList {
	private SequenceList		original;	// the original list, our "source" list, so to speak
	private SequenceList		sorted;		// the sorted copy		
	private Sequence 		query;		// the query used to generate the copy
	
//
//	1.	STATIC FUNCTIONS. I have no idea why there should be any.
//

//
//	2.	CONSTRUCTORS. We have only one way of creating us - you tell us what you want to query, and
//		we'll make it happen. We don't do everything from here mostly for historical reasons, but it's
//		also for greater flexibility. Shouldn't make *too* much difference when you're calling this,
//		I should think.
//
	/**
	 * The constructor.
	 * You specify the SequenceList this will be based on. The order
	 * is undefined, until you call sortAgainst() and sort against
	 * something.
	 */
	public SortedSequenceList(SequenceList list) {
		// save a pointer to the original
		original = list;	
	}

//
//	3.	SEARCHING METHODS. These are also be thought of as 'do' functions, since 
//		they change this class.
//		
	/**
	 * Sorts this SortedSequenceSet against this query sequence.
	 * Allows you to resort the set without "filling in" the
	 * data again, or creating another SortedSequenceSet object.
	 */
	public void sortAgainst(Sequence query, DelayCallback delay) throws DelayAbortedException {
		int count = 0;

		// get rid of (and, hopefully, free) the last object
		sorted = null;
		System.gc();

		if(original == null || query == null) { // wtf?!
			throw new RuntimeException("sortAgainst called on a SortedSequenceList which hasn't been set up properly! Contact the programmer!");
		}

		// store the query
		this.query = query;

		// let's make sure nobody changes the original from "under" us
		original.lock();

		// copy the original
		sorted = new SequenceList(original);
		sorted.lock();

		if(delay != null)
			delay.begin();

		// now, let's preprocess the 'sorted' list.
		ListIterator listIterator = sorted.listIterator(); 
		count = 0;
		while(listIterator.hasNext()) {
			count++;
			Sequence seq = (Sequence) listIterator.next();

			// delay
			if(delay != null)
				delay.delay(count, original.count());

			// is it a valid comparison? 
			if(seq.getPairwise(query) < 0) {
				// invalid! waste of time! get rid of it!
				listIterator.remove(); 
			}
		}

		Collections.sort(sorted, new SortedSequenceComparator(query));

		sorted.unlock();

		if(delay != null)
			delay.end();
		
		// all done!
		original.unlock();
	}

//
//	4.	GETTERS. They'll retrieve some information for you.
//
	/**
	 * Gets the total number of sequences in this SortedSequenceSet.
	 */
	public int count() {
		return sorted.count();
	}

	/**
	 * Returns the query sequence we are currently using for everything.
	 */
	public Sequence getQuery() {
		return query;
	}	
	
	/**
	 * Gets sequence number x from the starting of the row.
	 * This is zero-based (i.e., get(0) will return the FIRST sequence).
	 * You can get the query sequence using getQuery(). 
	 */
	public Sequence get(int x) {
		return (Sequence) sorted.get(x);
	}

	/**
	 * Gets the distance to sequence number x.
	 * This is zero-based (i.e., get(0) will return the FIRST sequence).
	 * You can get the query sequence using getQuery().
	 *
	 */
	public double getDistance(int x) {
		if(query == null)
			return -1;
		return get(x).getPairwise(query);
	}
}

/**
 * The SortedSequenceComparator comparates Sequences within a
 * SequenceList. This isn't the *actual* SequenceList, merely
 * a copy maintained by SortedSequenceList, so we can do mean
 * stuff like removing entries if we feel like it.
 */ 
class SortedSequenceComparator implements Comparator {
	private Sequence query;
	/**
	 * Constructor for this sequence. You need to provide the query.
	 */
	public SortedSequenceComparator(Sequence seq) {
		this.query = seq;
	}	

	/**
	 * What's the query again?
	 */
	public Sequence getQuery() {
		return query;
	}

	/**
	 * Used to compare (and by Collections.sort() to sort SequenceLists). Determines if we are less than,
	 * equal to, or greater than another sequence to a particular decimal point.
	 *
	 * We have a modification which will select for the conspecific entry between two
	 * identically distance sequences. Note that this won't work in QuerySequence, since
	 * QuerySequence never knows the name of the sequence being put in. Should work great
	 * for BlockAnalysis, though.  
	 *
	 * From the Java docs [http://java.sun.com/j2se/1.4.2/docs/api/java/util/Comparator.html]: 
	 * 	a negative integer, zero, or a positive integer as the first argument is less than, 
	 * 	equal to, or greater than the second.
	 *
	 * 	negative -- 	obj1 less than obj2
	 * 	zero --		obj1 equals obj2
	 * 	positive -- 	obj2 less than obj1
	 *
	 */
	public int compare(Object obj1, Object obj2) {
		int OBJ2_THEN_OBJ1 = +1;
		int OBJ1_THEN_OBJ2 = -1;	
		int OBJ1_EQ_OBJ2 = 0;
		
		Sequence seq1 = (Sequence) obj1;	// a CastException is just as bad
		Sequence seq2 = (Sequence) obj2;	// as a RuntimeException

		// do we have a query?
		if(query == null)
			throw new RuntimeException("No query specified for a SortedSequenceComparator");

		// calculate the distances, and symmetry be damned
		double distance1 = query.getPairwise(seq1);
		double distance2 = query.getPairwise(seq2);
		
		// we should not have invalid distances, but just in case
		if(distance1 < 0) {
			// distance 1 is invalid, so obj2 is superior
			return OBJ2_THEN_OBJ1;
		}

		if(distance2 < 0) {
			// distance 2 is invalid, so obj1 is superior
			return OBJ1_THEN_OBJ2;
		}

		long diff = Settings.makeLongFromDouble(distance1 - distance2);
		
		if(diff == 0) {
			// if they are the same SEQUENCE, push this sequence UP!
//			System.err.println("Comparing query = " + query.getId() + " with seq1 = " + seq1.getId() + " and seq2 = " + seq2.getId());
			if(query.getId().equals(seq1.getId()) && query.getId().equals(seq2.getId()))
				return OBJ1_EQ_OBJ2;
			else if(query.getId().equals(seq1.getId()))
				return OBJ1_THEN_OBJ2;
			else if(query.getId().equals(seq2.getId()))
				return OBJ2_THEN_OBJ1;

			// prefer conspecific
			String query_name = 	query.getSpeciesName();
			String name1 = 		seq1.getSpeciesName();
			String name2 = 		seq2.getSpeciesName();			

			if(name1.equals(query_name) && name2.equals(query_name)) {
				// they have identical names, and are BOTH
				// the same species as the query
				return OBJ1_EQ_OBJ2;
			}

			if(name1.equals(query_name)) {
				return OBJ1_THEN_OBJ2; 
			}
			else if(name2.equals(query_name)) {
				return OBJ2_THEN_OBJ1; 
			}

			return OBJ1_EQ_OBJ2;
		}

		return (int)(diff);
	}

	/**
	 * By contract, we check to see if the other comparator is "identical" to us
	 * and override equals(Object). However, the only way we'll be identical is
	 * if
	 * 	(a) it's the same class
	 * 	(b) it's the same query
	 */
	public boolean equals(Object o) {
		if(o.getClass().equals( ((Object)this).getClass())) {
			SortedSequenceComparator c = (SortedSequenceComparator)o;
			if(c.getQuery().equals(getQuery()))	
				return true;			// same object, same query; the order will be the same
		}
		return false;
	}
}
