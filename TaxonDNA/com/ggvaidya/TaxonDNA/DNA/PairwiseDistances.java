/**
 *
 * PairwiseDistances is just like PairwiseDistribution, with one crucial
 * difference: it stores references to the actual Sequences, which means
 * you can figure out WHO caused any particular pairwise distance.
 *
 * Eventually, PairwiseDistribution will be redacted in toto and replaced
 * by this guy. Until then, use the one you need.
 *
 * The following documentation is pretty much direct from PairwiseDistribution.
 * And this is how it works:
 * 1.	You call it in a "mode". Yes, this means two loops per sequence list, 
 * 	but this makes our code _much_ easier to deal with and prevents
 * 	the irritating copy-around we had going on in the last loop.
 * 	(p.s. it's not that slow!)	
 * 2.	We still store all the distances. While this does get flushed when
 * 	the PairwiseDistances object goes out of context, this means a
 * 	LOT of memory spent. There's no real way around this, but now
 * 	we'll be smart about it, using a Vector to hide our array and 
 * 	using floats directly.	
 *  	
 *  We use floats, since in Java we are guaranteed 6-7 digits of accuracy.
 *  This also much simplifies code.	
 *
 * NOTE: This class is very, very thread-unsafe during creation (i.e. all
 * functions will return weird values if you run them before the constructor 
 * has finished running). 
 * If you need to access it from more than one thread, it's up to you to 
 * make the magic happen. 
 */

/*
    TaxonDNA
    Copyright (C) 2005-06	Gaurav Vaidya

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

public class PairwiseDistances {
	public static final int		PD_INTRA	=	0;
	public static final int		PD_INTER	=	1;
	
	public static final char	CUMUL_FORWARD	=	'F';
	public static final char	CUMUL_BACKWARD	=	'B';
	
	// distance, and other vars needed to handle it
	private Vector distances = new Vector();

	// statistics we might need to report to the user
	private int		count_sequences		= 	0;

	private void distances_push(Sequence seqA, Sequence seqB) {
		if(seqA.getPairwise(seqB) < 0)
			return;

		distances.add(new PairwiseDistance(seqA, seqB));
	}
	
	/**
	 * Constructor. Give it a list and what kind of distribution you
	 * want it to be, and watch it go to work!
	 */
	public PairwiseDistances(SequenceList list, int type, DelayCallback delay) throws DelayAbortedException {
		list.lock();

		if(delay != null)
			delay.begin();

		// Since _addIntra actually uses the conspecific iterator to speed things up, we need to resort
		// at this point. Don't worry - we'll sort it back before we unlock it.
		int oldSort = -1;
		if(type == PD_INTRA)
			oldSort = list.resort(SequenceList.SORT_BYNAME);

		// go thru the list, calculating all the distances in this category.
		// we use private "helper" functions to help (and make the code less painful)
		Iterator i = list.iterator();
		while(i.hasNext()) {
			Sequence query = (Sequence) i.next();

			if(type == PD_INTRA)
				_addIntra(list, query);
			else if(type == PD_INTER)
				_addInter(list, query);
			else
				throw new RuntimeException("Programmer Error in PairwiseDistances: Please inform the programmer!");		

			if(delay != null) {
				try {
					delay.delay(count_sequences, list.count());
				} catch(DelayAbortedException e) {
					if(type == PD_INTRA)
						list.resort(oldSort);
					list.unlock();
					throw e;	// get outta here
				}
			}
			
			count_sequences++;
		}

		// Sort it up, before we ship it out
		if(distances.size() > 0) 
			Collections.sort(distances);

		if(type == PD_INTRA)
			list.resort(oldSort);

		if(delay != null)
			delay.end();
		
		list.unlock();
	}

	/*
	 * These private "helper functions" will help out with generating the pairwise distribution
	 */
	/**
	 * Calculate all intraspecific pairwise distances for 'query'
	 * in SequenceList 'list', and add it to this pairwise distrib.
	 */
	private void _addIntra(SequenceList list, Sequence query) {
		Iterator i = list.conspecificIterator(query);

		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			if(seq.equals(query))
				continue;

			distances_push(query, seq);
		}
	}

	/**
	 * Calculate all interspecific pairwise distances for 'query'
	 * in SequenceList 'list', and add it to this pairwise distrib.
	 */
	private void _addInter(SequenceList list, Sequence query) {
		Iterator i = list.iterator();

		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			if(seq.equals(query))
				continue;

			if(query.getGenusName().equals(seq.getGenusName())) {
				// identical genera
				if(!query.getSpeciesNameOnly().equals(seq.getSpeciesNameOnly())) {
					// but non identical species
					//
					// however, only do it one way (half-table only)
					if(query.getSpeciesNameOnly().compareTo(seq.getSpeciesNameOnly()) < 0)
						distances_push(query, seq);	
				}
			}
		}
	}
	
	/** Number of sequences in this pairwise distribution. */ 
	public int countSequences() {
		return count_sequences;
	}

	/** Number of valid comparisons (i.e. non-negative comparisons) */
	public int countValidComparisons() {
		return distances.size();
	}	
	
	/** Number of comparisons which return a distance of exactly zero */
	public int getZero() {
		int count = 0;
		for(int x = 0; x < countValidComparisons(); x++) {
			if(identical(((PairwiseDistance)distances.get(x)).getDistance(), 0.0)) 
				count++;
			else
				// non-zero! let's get out!
				break; 
		}
		return count;
	}

	/** Number of comparisons which return a distance of exactly one */
	public int getOne() {
		int count = 0;
		for(int x = countValidComparisons() - 1; x >= 0; x--) {
			if(identical(((PairwiseDistance)distances.get(x)).getDistance(), 1.0)) 
				count++;
			else
				// non-one! let's get out!
				break; 
		}
		return count;
	}	

	/**
	 * Helper function, calculates percentages.
	 */
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}


	/**
	 * Returns the number of distances in between the two float ranges.
	 * Remember that the range is from &lt; this &lt;= to, so don't be
	 * surprised if the lower edge of the range doesn't turn up. This
	 * allows the ranges to fit into each other for printing, and the
	 * first range is then those with distance = 0, an important
	 * value in taxonomy.
	 */
	public int getBetween(double from, double to) {
		return getDistancesBetween(from, to - 0.000001).size();
	}

	/**
	 * Get between - Inclusive. So the 'from' values are counted too.
	 * (i.e. from &lt;= this &lt;= to) 
	 *
	 * "People who like this sort of thing, will find this the sort of thing they like."
	 * 					-- Abraham Lincoln
	 *
	 */
	public int getBetweenIncl(double from, double to) {
		return getDistancesBetween(from, to).size();
	}

	/**
	 * Return the largest distance in this pairwise distribution
	 */
	public double getMaximumDistance() {
		if(distances.size() > 0) {
			return ((PairwiseDistance)distances.get(distances.size() - 1)).getDistance();
			
		}
		return 0;
	}
	
	/**
	 * Return the smallest distance in this pairwise distribution.
	 */
	public double getMinimumDistance() {
		if(distances.size() > 0)
			return ((PairwiseDistance)distances.get(0)).getDistance();
		return 0;
	}

	/**
	 * Returns a Vector, containing all the distances between
	 * d_from and d_to. Since we assume you must want ALL
	 * the distances, it is inclusive both ends.
	 * 
	 * @return a vector of PairwiseDistance objects
	 */
	public Vector getDistancesBetween(double d_from, double d_to) {
		Vector vec = new Vector();
		boolean count = false;
		
		for(int x = 0; x < distances.size(); x++) {
			PairwiseDistance d = (PairwiseDistance) distances.get(x);

			if(d.getDistance() >= d_from)
				count = true;
			if(d.getDistance() > d_to)
				break;	

			if(count)
				vec.add(d);
		}
		return vec;
	}

	/**
	 * Compares two floats for 'identicality'. 
	 */ 
	private boolean identical(double x, double y) {
		return Settings.identical(x, y);
	}	

	/**
	 * Quickie: returns a percentage
	 */
	private double percentage(int x, int y) {
		return percentage((double)x, (double)y); 
	}
}


