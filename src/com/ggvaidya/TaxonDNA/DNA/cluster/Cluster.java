
/*
 *
 *  Cluster
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
 * A cluster is a set of sequences which have
 * been united under a distance constraint. Its
 * main magic stems from its ability to compare
 * itself against other clusters, to identify
 * itself, and to summarise sets of clusters.
 * 
 * It isn't *actually* a SequenceList, being properly
 * a *set* of sequences, but we'll create SequenceSet
 * when we need to. For now, SequenceLists will do.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class Cluster extends SequenceList {
	/**
	 * Creates an empty cluster.
	 */
	public Cluster() {}

	/**
	 * Creates a cluster from another SequenceList.
	 */
	public Cluster(SequenceList list) { super(list); }

	/**
	 * Gets a 'name' for the current cluster. The trick
	 * is that we'd like this to be in one of the following
	 * formats:
	 *	- "12 sequences of 'Genus species'"
	 *  - "3 seqs of 'Genus species' and 1 seqs of 'Genus species'"
	 *  - "32 sequences from 12 species in genus 'Genus'"
	 *  - "33 sequences from 32 species across 5 genera"
	 *
	 * Oh well, TODO.
	 *
	 * @return A name for the current cluster.
	 */
	@Override public String toString() {
		return "Cluster of " + count() + " sequences";
	}
}
