/**
 * A SequenceContainer contains sequence information, which can be accessed
 * via its getAsSequenceList() function. That's, err, it, really.
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2007

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

public interface SequenceContainer {
	/**
	 * Returns a SequenceList containing all the sequences 'contained'
	 * herein. Might just contain a single sequence.
	 *
	 * @throws SequenceException if there was a problem in retrieving or creating the one sequence (e.g. if the sequence is hosted remotely, and the server returned an error).
	 */
	public SequenceList getAsSequenceList() throws SequenceException;
	/** 
	 * Returns a List contains all the other SequenceContainers
	 * that this SequenceContainer 'contains'.
	 *
	 * @return aforementioned list, or an EMPTY list (NEVER null)
	 */
	public List alsoContains();
}
