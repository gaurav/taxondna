
/*
 *
 *  ClusterJob
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

import com.ggvaidya.TaxonDNA.Common.DelayAbortedException;
import com.ggvaidya.TaxonDNA.Common.DelayCallback;
import com.ggvaidya.TaxonDNA.DNA.*;

import java.util.*;

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
	 * A linkage object to define how linkage should be carried out during
	 * this job.
	 */
	protected Linkage			linkage;

	/**
	 * Once this job has executed, we will have a collection of
	 * Clusters to ponder.
	 */
	protected ArrayList<Cluster> results = null;

	/**
	 * Return the resulting clusters.
	 */
	public List<Cluster> getClusters() {
		return Collections.unmodifiableList(results);
	}

	/**
	 * Create a new Cluster job.
	 * 
	 * @param list			The sequences to cluster.
	 * @param threshold		The threshold to cluster at (as a percentage).
	 * @param linkage		The linkage to use.
	 */
	public ClusterJob(SequenceList list, Linkage linkage, double threshold) {
		this.sequences =	new SequenceList(list); // Copy it, just in case.
		this.threshold =	threshold;
		this.linkage =		linkage;
	}

	/**
	 * How many clusters do we have?
	 * @return the number of clusters, or -1 if this job has not yet been executed.
	 */
	public int count() {
		if(results != null)
			return results.size();
		else
			return -1;
	}

	public void execute(DelayCallback callback) throws DelayAbortedException {
		callback.begin();

		// Reset older variables.
		ArrayList<Cluster> clusters = new ArrayList<Cluster>();

		// Ready to go!
		int count = 0;
		int total = sequences.count();

		for(Object obj: sequences) {
			// Count 'em.
			count++;
			callback.delay(count, total);

			// Find our sequence.
			Sequence seq = (Sequence) obj;

			// For every bin we currently have:
			for(Cluster cluster: clusters) {
				// Check if this sequence should go into this bin.
				if(linkage.canLink(cluster, seq, threshold)) {
					// System.err.println("Sequence " + seq + " added to cluster " + cluster);
					cluster.add(seq);
					seq = null;
					break;
				}
			}

			// If the sequence hasn't been placed, put it into its
			// own bin.
			if(seq != null) {
				Cluster cluster = new Cluster();
				cluster.add(seq);
				clusters.add(cluster);
				// System.err.println("Sequence " + seq + " added to new cluster");
			}
		}

		// Save the clusters to this Job's results field and exit.
		results = clusters;
		callback.end();
	}
}
