
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
		return (List<Cluster>) getResults().clone();
	}

	/**
	 * This can be used to figure out how this cluster is doing
	 * relative to this entire dataset.
	 * 
	 * @return The Sequences on which this clustering job was run.
	 */
	public Sequences getOriginalSequences() {
		return sequences;
	}

	/**
	 * Create a new Cluster job.
	 * 
	 * @param list			The sequences to cluster.
	 * @param threshold		The threshold to cluster at (as a percentage).
	 * @param linkage		The linkage to use.
	 */
	public ClusterJob(SequenceList list, Linkage linkage, double threshold) {
		if(list == null)
			this.sequences = null;
		else
			this.sequences =	new SequenceList(list); // Copy it, just in case.
		this.threshold =	threshold;
		this.linkage =		linkage;
	}

	/**
	 * How many clusters do we have?
	 * @return the number of clusters, or -1 if this job has not yet been executed.
	 */
	public int count() {
		if(getResults() != null)
			return getResults().size();
		else
			return -1;
	}

	public void execute(DelayCallback callback) throws DelayAbortedException {
		callback.begin();

		// Reset older variables.
		ArrayList<Cluster> clusters = new ArrayList<Cluster>();

		// Ready to go!
		int count = 0;
		int total = getSequences().count();

		for(Object obj: getSequences()) {
			// Count 'em.
			count++;
			callback.delay(count, total);

			// Find our sequence.
			Sequence seq = (Sequence) obj;

			// What we're doing here is similar but not identical to the
			// hierarchical clustering algorithm (see
			// http://home.dei.polimi.it/matteucc/Clustering/tutorial_html/hierarchical.html
			// for more details). We *should* be safe, as long as we
			// double-check that the final set of clusters are all minimally
			// different from each other (i.e. no clusters remain unmerged).
			//
			// Which we now do, check out the verification section below.

			// A quick reminder of "Kathy's Bug": it's possible that a new
			// sequence 'seq' will 'connect' two previously unconnected bins.
			// So we do this:
			//   |----------------------- bins -------------------------|
			//	 |-----* Found the first bin that matchs.				|
			//   |     *-----X---X-X-----X--X For every subsequent match|
			//	 |							  we merge that bin into the|
			//   |							  first bin we found.       |
			//	 |							 -------X-----------------X-|
			//   |------------------------------------------------------*
			//   | ^- No matches go into their own, new bin at the end. |
			//   |------------------------------------------------------|

			Cluster found_bin = null;

			// We will be modifying this list on the fly, but we need to
			// iterate over the *original* list.
			ArrayList<Cluster> original_clusters_list = (ArrayList<Cluster>)
					clusters.clone();

			// For every bin we currently have:
			for(Cluster cluster: original_clusters_list) {
				// Check if this sequence should go into this bin.
				if(getLinkage().canLink(cluster, seq, getThreshold())) {
					if(found_bin != null) {
						// We already have a bin for the current sequence.
						// So 'cluster' should be merged into found_bin
						// and then deleted from clusters.
						found_bin.addAll(cluster);
						clusters.remove(cluster);
					} else {
						// First bin to be found!
						cluster.add(seq);
						found_bin = cluster;
					}
				}
			}
			
			// No matches? No matter, put it into its own cluster
			// and save.
			if(found_bin == null) {
				found_bin = new Cluster(this);
				found_bin.add(seq);
				clusters.add(found_bin);
			}
		}

		callback.end();
		callback.begin();

		// VERIFICATION.
		// Make sure that NONE of the clusters can be linked to each other
		// using this algorithm.
		count = 0;
		total = clusters.size();
		for(Cluster outer: clusters) {
			// Count 'em.
			count++;
			callback.delay(count, total);
			
			for(Cluster inner: clusters) {
				if(outer == inner) continue;

				if(getLinkage().canLink(outer, inner, getThreshold())) {
					throw new RuntimeException(
						"Verification FAILED: cluster " + inner +
						" can be merged with cluster " + outer + 
						", but WASN'T (distance = " + getLinkage().pairwiseDistance(inner, outer) +
						")"
					);
				}
			}
		}

		// Save the clusters to this Job's results field and exit.
		results = clusters;
		callback.end();
	}

	/**
	 * @return the sequences
	 */ public SequenceList getSequences() {
		return sequences;
	}

	/**
	 * @return the threshold
	 */ public double getThreshold() {
		return threshold;
	}

	/**
	 * @return the linkage
	 */ public Linkage getLinkage() {
		return linkage;
	}

	/**
	 * @return the results
	 */ public ArrayList<Cluster> getResults() {
		return results;
	}

	public void createFakeResults() {
		results = new ArrayList<Cluster>();
	}

	public int countClustersSharedWith(ClusterJob other) {
		int count = 0;

		ClusterJob bigger = other;
		ClusterJob smaller = this;

		/*
		 * We need to find the smaller cluster job instead
		 * the bigger one. Otherwise:
		 *		(c1, c3) vs (c1, c1, c3)
		 * The correct answer is three: (c1, c1), (c1, c1), (c3, c3)
		 * but without swapping, we would end up only finding two.
		 */
		if(bigger.count() < smaller.count()) {
			ClusterJob t =	bigger;
			bigger =		smaller;
			smaller =		t;
		}

		for(Cluster outer: bigger.getResults()) {
			for(Cluster inner: smaller.getResults()) {
				if(outer.getContentsUUID().equals(inner.getContentsUUID())) {
					count++;
					break;
				}
			}
		}

		return count;
	}
}
