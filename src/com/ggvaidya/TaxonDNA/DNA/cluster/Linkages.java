
/*
 *
 *  Linkages
 *  Copyright (C) 2010-11 Gaurav Vaidya
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */

package com.ggvaidya.TaxonDNA.DNA.cluster;

import com.ggvaidya.TaxonDNA.DNA.*;

/**
 * Linkage defines how clusters can be combined together.
 * 
 * There are three main types:
 *	- complete linkage clustering: every item in both clusters are
 *		within (threshold) of each other.
 *	- single linkage clustering: at least one item in both clusters
 *		are within (threshold) of each other.
 *	- mean linkage clustering: the average distance between the two
 *		clusters is within (threshold).
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class Linkages {
	/**
	 * Single/minimum linkage uses as its criterion that the *smallest* distance
	 * between a new sequence and members of a cluster be within threshold.
	 */
	public static class SingleLinkage implements Linkage {
		
		public boolean canLink(Sequences a, Sequences b, double threshold) {
			double distance = pairwiseDistance(a, b);

			if(distance < 0)			return false;
			if(distance >= threshold)	return false;
			
			return true;
		}

		// O(n^2)
		public double pairwiseDistance(Sequences a, Sequences b) {
			double min_distance = -1;

			for(Object obj1: a.getSequences()) {
				Sequence outer = (Sequence) obj1;
				for(Object obj2: b.getSequences()) {
					Sequence inner = (Sequence) obj2;

					double dist = outer.getPairwise(inner);
					if(dist >= 0) {
						// Valid distance!
						if(min_distance < dist) {
							min_distance = dist;
						}
					}
				}
			}

			return min_distance;
		}

		@Override public String toString() {
			return "single linkage";
		}
	}
}