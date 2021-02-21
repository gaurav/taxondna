/** 	
 * DNA.Settings controls settings for all DNA classes. They check back here
 * for constants, controls, and optimizations.
 *
 * Just to clarify: Sequence has a certain, fixed format for dealing with
 * Sequences (using '-' for gaps, for instance). That is how it works. Deal
 * with it.
 *
 * Settings controls the (very little) stuff in DNA which can be played around
 * with. It should be noted that values are *read from* here, so all values
 * which might be affected ought to be recalculated in between changing
 * settings.
 *
 * Somewhere, somehow, people who actually know Java are laughing at me ...
 *
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

public class Settings {
	/**
	 * What proportion of the total memory the pairwise distance cache
	 * is allowed to use until. (Ha! Okay, seriously: the fraction of
	 * used memory to total memory at which the cache flushes itself)
	 */
	public static double PairwiseCacheMemoryUsageLimit = 0.9;
	
	/*
	 * What values should the DNA.* functions be accurate to?
	 * We'll be accurate to 1/accurateTo.
	 */
	private static int accurateTo = 100000;

	/**
	 * Returns the value we are accurate to.
	 */
	public static double getAccurateTo() {
		return 1/(double)accurateTo;
	}

	/**
	 * Sets the value we should be accurate to.
	 * e.g. 0.0001
	 */
	public static void setAccurateTo(double d) {
		accurateTo = (int)(1/d);
	}

	/**
	 * Sets the number of digits we should be accurate to.
	 * 	 * e.g. 4 for 0.0001
	 */
	public static void setAccurateToDigits(int digits) {
		accurateTo = (int)Math.pow(10, digits);
	}

	/**
	 * Converts the specified double to an int at the necessary
	 * level of accuracy. Think of it as a log-transform. You can
	 * use makeDoubleFromLong() to convert it back.
	 */
	public static long makeLongFromDouble(double d) {
		return (long)(d * accurateTo);
	}

	/**
	 * Converts the specified int (from makeLongFromDouble) to an int 
	 * at the necessary level of accuracy.
	 *
	 * @see #makeLongFromDouble(double)
	 */
	public static double makeDoubleFromLong(long i) {
		return i / (double)accurateTo;
	}

	/**
	 * Percentage, rounded off to two decimal places.
	 */
	public static double percentage(double x, double y) {
		if(y == 0)
			return 0;	// x% of nothing is zero percent
		return (double)((long)(roundOff(x/y) * 100 * 100)) / 100;
	}

	/**
	 * Rounds off a value to its nearest double which is accurate.
	 */
	public static double roundOff(double d) {
		return makeDoubleFromLong(makeLongFromDouble(d));
	}

	/**
	 * Compares two doubles at the necessary level of accuracy.
	 */
	public static boolean identical(double d1, double d2) {
		return (makeLongFromDouble(d1) == makeLongFromDouble(d2));
	}
	
	/**
	 * Get the minimum overlap necessary to make a comparison.
	 */
	public static int getMinimumOverlap() {
		return Sequence.getMinOverlap();
	}

	/**
	 * Set the minimum overlap necessary to make a comparison.
	 */
	public static void setMinimumOverlap(int minOverlap) {
		Sequence.setMinOverlap(minOverlap);
	}

	/**
	 * Returns true if ambiguous bases are allowed. If not,
	 * we'll convert ambiguous bases to 'N' during comparisons.
	 */
	public static boolean areAmbiguousBasesAllowed() {
		return Sequence.areAmbiguousBasesAllowed();
	}

	/**
	 * Set whether ambiguous bases are allowed. If not,
	 * we'll convert ambiguous bases to 'N' during comparisons.
	 */	
	public static void setAmbiguousBasesAllowed(boolean allowed) {
		Sequence.ambiguousBasesAllowed(allowed);
	}
}
