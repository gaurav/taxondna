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

    public int compareTo(Object o) {
        FromToPair ftp = (FromToPair) o;

        return (this.from - ftp.from);
    }

    public String toString() {
        return from + " to  " + to;
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
}

