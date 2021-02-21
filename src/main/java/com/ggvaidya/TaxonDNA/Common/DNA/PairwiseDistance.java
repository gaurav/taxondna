/**
 * PairwiseDistance is a single instance of a 'pairwise distance', comprising
 * the distance itself, as well as the two sequences which are that far apart.
 * You should check out PairwiseDistances (which is really just a sorted Vector
 * of PairwiseDistance objects) to figure out what's really going on.
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2006

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

package com.ggvaidya.TaxonDNA.Common.DNA;

public class PairwiseDistance implements Comparable {
	private double distance = 	-1;
	public double getDistance() {	return distance;	}

	private Sequence seqA =		null;
	public Sequence getSequenceA()	{	return seqA;	}
	private Sequence seqB =		null;
	public Sequence getSequenceB()	{	return seqB;	}

	public PairwiseDistance(Sequence a, Sequence b) {
		seqA = a;
		seqB = b;

		distance = seqA.getPairwise(seqB);
	}

	public boolean isMentioned(Sequence seq) {
		return (seq.equals(seqA) || seq.equals(seqB));
	}

	public int compareTo(Object o) {
		// we 'naturally' sort by distance as smallest first
		PairwiseDistance pd = (PairwiseDistance) o;

		return (int)Settings.makeLongFromDouble(getDistance() - pd.getDistance());
	}

	public String toString() {
		return super.toString() + ": " + getDistance() + " between " + getSequenceA() + " and " + getSequenceB();
	}
}
