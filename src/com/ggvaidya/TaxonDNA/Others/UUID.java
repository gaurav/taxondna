/**
 * A class representing a UUID [1]. Okay, this is a hack. Read my lips: H-A-C-K.
 * I'm only doing this because it'll HELP (not STOP) programs from screwing up.
 * UUID engines are way too complex for me to try and handcode one right now
 * just to get AlignmentHelper working. Plus, hopefully, the WORSE thing that
 * can happen is a collision (two UUIDs are identical), which should be handled
 * by another part of the program - and ideally, that BARFS. So it's all good :).
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

package com.ggvaidya.TaxonDNA.Others;

public class UUID {
	private static long lastAllocated = 0;
	private static int lastSequenceNo = 0;
	private long myTimestamp = 0;
	private int mySequenceNo = 0;
	private static Object lock = new Object();
	
	public UUID() {
		synchronized(lock) {
			myTimestamp = System.currentTimeMillis();
			mySequenceNo = 1;

			if(myTimestamp == lastAllocated)
				mySequenceNo = lastSequenceNo + 1;
			
			lastAllocated = myTimestamp;
			lastSequenceNo = mySequenceNo;
		}
//		System.err.println("Unique ID allocated: " + toString());
	}

	// equals()? Only if you're another UUID, mate
	public boolean equals(Object o) {
		return equals( (UUID) o);
	}

	// equals()? Only if the UIDs match, mate
	public boolean equals(UUID uid2) {
		return uid2.getUUID().equals(getUUID());
	}

	// if you're "one of us" ... you get to know this
	// otherwise, please, just use equals(Object) and be happy with it ...
	protected String getUUID() {
		return myTimestamp + "_" + mySequenceNo;
	}

	// guaranteed to be unique
	public String toString() {
		return getUUID();
	}
}
