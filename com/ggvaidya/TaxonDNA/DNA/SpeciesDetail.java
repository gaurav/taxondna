/**
 * A SpeciesDetail object holds information about one particular species in a
 * SpeciesDetails map.
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

public class SpeciesDetail {
	private String 	name;
	private int	sequences;
	private int 	sequences_with_valid_matches;
	private int 	sequences_with_no_valid_matches;
	private int	length_largestSequence;
	private Vector	gi_numbers = new Vector();

	/**
	 * You can't create a SpeciesDetail unless you, err,
	 * provide the details.
	 */
	private SpeciesDetail() {}

	/**
	 * Our most useful constructor.
	 */
	public SpeciesDetail(String name) {
		this.name = name;
	}

	/**
	 * Some other helper functions to add information.
	 */
	public void pushSequenceIdentifier(String gi) {
		gi_numbers.add(gi);
	}

	/*
	 * GET functions
	 * SET functions
	 */
	public int getSequencesCount() 					{	return sequences; 			}
	public void setSequencesCount(int count) 			{ 	sequences = count; 			}
	public int getSequencesWithValidMatchesCount() 			{ 	return sequences_with_valid_matches; 	}
	public void setSequencesWithValidMatchesCount(int count) 	{ 	sequences_with_valid_matches = count; 	}
	public int getSequencesWithoutValidMatchesCount()		{ 	return sequences_with_no_valid_matches; }
	public void setSequencesWithoutValidMatchesCount(int count)	{ 	sequences_with_no_valid_matches = count;}
	public void setLargestSequenceLength(int len)			{	length_largestSequence = len;		}
	public 	int getLargestSequenceLength()				{	return length_largestSequence; 		}

	public Vector getIdentifiers() 					{ return gi_numbers; }
	public Iterator getIdentifiersIterator() 			{ return gi_numbers.iterator(); }
	public String getIdentifiersAsString() {
		StringBuffer buff = new StringBuffer();
		Iterator i = gi_numbers.iterator();
		while(i.hasNext()) {
			buff.append(((String)i.next()) + " ");
		}

		return buff.toString();
	}
}
