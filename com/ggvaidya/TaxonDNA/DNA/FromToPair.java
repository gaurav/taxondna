package com.ggvaidya.TaxonDNA.DNA;

/**
 * A special datatype storing coordinates within a sequence.
 * At the moment, this is a pretty simple thing; you'll need
 * outside information to figure out what it means or where
 * it's actually point to.
 *
 * Most important note: FromToPairs must ALWAYS be one-based.
 * Zero-based FTPs are pure evil. This makes very little
 * sense, except that Sequence.getSubsequence uses 1-based
 * indexes, and I am NOT modifying that method, since it is
 * very very old and probably in use all over the place.
 */

public class FromToPair implements Comparable {
    public int from;
    public int to;

    public FromToPair(int from, int to) {
        if(from <= 0 || to <= 0)
            throw new RuntimeException("FromTo of " + from + " to " + to + " invalid: less than or equal to zero!");

        if(to < from) 
            throw new RuntimeException("FromTo of " + from + " to " + to + " invalid: incorrect order!");

        this.from = from;
        this.to = to;
    }

	public FromToPair(FromToPair ftp) {
		this.from =		ftp.from;
		this.to =		ftp.to;
	}

    public int compareTo(Object o) {
        FromToPair ftp = (FromToPair) o;

        return (this.from - ftp.from);
    }

    public String toString() {
		if(from == to)
			return from + "";	// String convert on the fly.
		else
			return from + " to " + to;
    }

	/***
	 * Checks for overlap between us and another FromToPair.
	 * @param ftp The FTP to compare against
	 * @return True if there is even a single digit of overlap, false otherwise.
	 */
	public boolean overlaps(FromToPair ftp) {
		return
			// Check whether ftp.from intersects with us.
			((ftp.from >= from) && (ftp.from <= to)) ||
			// Check whether ftp.to intersects with us.
			((ftp.to >= from) && (ftp.to <= to))
		;
	}
	
	/***
	 * Checks for overlap, but assumes both FTPs move in threes.
	 * (i.e. they were marked with \3s). Note that there is at
	 * present no way for this code to confirm that both FTPs
	 * move in threes: please make sure they do before calling
	 * this method.
	 * 
	 * @param ftp The FTP to compare against.
	 * @return True if there is any overlap, false otherwise.
	 */
	public boolean overlapsMovesInThrees(FromToPair ftp) {
		// All of this is meaningless with real overlap.
		// So:
		if(!this.overlaps(ftp))
			return false;

		// There can be no possible overlap unless ftp.from % 3
		// and self.from % 3 are not identical. For the sake of
		// convenience, let's call this a "track"
		if((this.from % 3) != (ftp.from % 3)) {
			System.err.println("No comparison necessary between /3s loci " + 
				this.from + " and " + ftp.from + ": separate tracks."
			);

			return false;
		}
		int track = this.from % 3;

		// So they're on the same track. To do the comparison now:
		//	1.	We round both this.to and ftp.to to the last position which
		//		is on the same track as self.from and ftp.from. Thus, both
		//		ftp.to and this.to are valid locations. For want of a name,
		//		we'll call this "rounding" the FTPs.
		FromToPair rounded_us =		new FromToPair(this);
		FromToPair rounded_ftp =	new FromToPair(ftp);

		// Round us.
		rounded_us.to = rounded_us.to - (rounded_us.to - rounded_us.from) % 3;

		if(rounded_us.to % 3 != track)
			throw new RuntimeException("Algorithm fails: our 'to' rounded from " + 
				this.to + " to " + rounded_us.to + " is not on track " + track +
				" from " + rounded_us.from);

		// Round ftp.
		rounded_ftp.to = rounded_ftp.to - (rounded_ftp.to - rounded_ftp.from) % 3;

		if(rounded_ftp.to % 3 != track)
			throw new RuntimeException("Algorithm fails: their 'to' rounded from " +
				ftp.to + " to " + rounded_ftp.to + " is not on track " + track +
				" from " + rounded_ftp.from);

		//	2.	Finally, everything is normalized; we just need to check for
		//		any overlaps between the two ftps.
		System.err.println("Rounded from: " + rounded_us + " to: " + rounded_ftp + ", comparing.");

		return rounded_us.overlaps(rounded_ftp);
	}
}

