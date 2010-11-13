
/*
 *
 *  ClusterJob
 *  Copyright (C) 2010 Gaurav Vaidya
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
 * A ClusterJob encapsulates a single instance of clustering which needs to be
 * carried out.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ClusterJob {
	/** The SequenceList to be clustered. */
	protected SequenceList		sequences;

	/** The threshold to cluster at, represented as a percentage. */
	protected double			threshold;

	/** 
	 * A cluster job requires a Linkage object to specify how linking
	 * should be carried out. Here is what a Linkage looks like.
	 */
	public interface Linkage {
		/**
		 *
		 * @param list
		 * @param seq The sequence which may or may not fit into this cluster.
		 * @return True if this sequence can be linked to this sequence list,
		 *		false otherwise.
		 */
		public boolean canLink(SequenceList list, Sequence seq);
	}
	
	/**
	 * A linkage object to define how linkage should be carried out during
	 * this job.
	 */
	protected Linkage			linkage;

	/**
	 * Create a new Cluster job.
	 * 
	 * @param list			The sequences to cluster.
	 * @param threshold		The threshold to cluster at (as a percentage).
	 * @param linkage		The linkage to use.
	 */
	public ClusterJob(SequenceList list, double threshold, Linkage linkage) {
		this.sequences =	new SequenceList(list);
		this.threshold =	threshold;
		this.linkage =		linkage;
	}

	/**
	 * Describe this cluster job.
	 */
	@Override
	public String toString() {
		return
			"A cluster job clustering " + (sequences.count()) +
			" sequences at " + threshold + " with linkage " + linkage.toString()
		;
	}
}
