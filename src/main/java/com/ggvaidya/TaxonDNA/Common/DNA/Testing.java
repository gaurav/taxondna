/** 	
 * Testing tests the DNA segment. You can either call Testing.testAll() (or another 'testing' function)
 * or you can run the class directly (we've got our own main()).
 */

/*
    TaxonDNA
    Copyright (C) 2007	Gaurav Vaidya

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

import java.util.*;

public class Testing { 
	public static void main(String args[]) {
		println("Starting testing of TaxonDNA.DNA ...");
		Testing.testAll();
		println("Testing ended.");
	}

	public static void testTransScoring() {
		println("Testing pairwise distances calculated by transversions ...");

		try {
			char[] purines = 	"AGR".toCharArray();	
			char[] pyrimidines = 	"TCY".toCharArray();
			char[] neither = 	"KMSWBDHVN".toCharArray();

			println("Testing purines ...");
			for(int x = 0; x < purines.length; x++) {
				if(!Sequence.isPurine(purines[x]))
					println("ERROR: purine " + purines[x] + " is not counted as a purine!");
				if(Sequence.isPyrimidine(purines[x]))
					println("ERROR: purine " + purines[x] + " is counted as a pyrimidine!");
			}
			
			println("Testing pyrimidines ...");
			for(int x = 0; x < pyrimidines.length; x++) {
				if(!Sequence.isPyrimidine(pyrimidines[x]))
					println("ERROR: pyrimidine " + pyrimidines[x] + " is not counted as a pyrimidine!");
				if(Sequence.isPurine(pyrimidines[x]))
					println("ERROR: pyrimidine " + pyrimidines[x] + " is counted as a purines!");
			}

			println("Testing neither ...");
			for(int x = 0; x < neither.length; x++) {
				if(Sequence.isPyrimidine(neither[x]))
					println("ERROR: NPNP " + neither[x] + " is counted as a pyrimidine!");
				if(Sequence.isPurine(neither[x]))
					println("ERROR: NPNP " + neither[x] + " is counted as a purines!");
			}

			println("Testing distances ...");

			Sequence.setPairwiseDistanceMethod(Sequence.PDM_TRANS_ONLY);
			Sequence.setMinOverlap(1);

			Sequence seq_a = new Sequence("T1", "--ATCCCTGGTA");
			Sequence seq_b = new Sequence("T2", "ACCGCCCT--CG");

			if(seq_a.getPairwise(seq_b) != 0.2)
				println("Incorrect: ought to be 0.2, reported as " + seq_a.getPairwise(seq_b) + "; #trans = " + seq_a.countTransversions(seq_b) + "; overlap = " + seq_a.getSharedLength(seq_b));
			else
				println("Simple test successful!");

		} catch(Exception e) {
			println("ERROR: " + e);
		}

		println("Transversion testing done!");
	}
	
	public static void testSequence() {
		int old_method = Sequence.getPairwiseDistanceMethod();
		int old_overlap = Sequence.getMinOverlap();
		testTransScoring();
		Sequence.setPairwiseDistanceMethod(old_method);
		Sequence.setMinOverlap(old_overlap);
	}

	public static void testAll() {
		testSequence();
	}

	public static void println(String s) {
		System.err.println(new Date() + ": " + s);
	}
}
