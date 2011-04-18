
/*
 *
 *  ClusterNode
 *  Copyright (C) 2011 Gaurav Vaidya
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

import java.util.*;

/**
 * A cluster node associates together clusters which would
 * merge at a particular distance. For instance, a ClusterNode might specify
 * that three clusters merge together at 4%.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ClusterNode implements Sequences {
	/** Cluster node */

	/** Clusters to match */
	protected List<Sequences>	sequences;

	/** Distance at which they would merge. */
	protected double			distance;

	/**
	 * A ClusterNode consists of two or more Clusters which 'merge' at
	 * a particular distance.
	 */
	public ClusterNode(double distance, Sequences... clusters) {
		this.sequences =	Arrays.asList(clusters);
		this.distance =		distance;
	}

	public void addCluster(Cluster c) {
		this.sequences.add(c);
	}

	/**
	 * @return What distance do these clusters merge at?
	 */
	public double getDistance() {	return distance;	}

	/**
	 * Note that this is a recursive list of *all* the sequences at this node.
	 *
	 * @return A SequenceList of all the sequences present at this sequence node.
	 */
	public SequenceList getSequences() {
		SequenceList sl = new SequenceList();

		for(Sequences s: sequences) {
			sl.addAll(s.getSequences());
		}

		return sl;
	}

	public List<Sequences> getSequencesObjects() {
		return sequences;
	}

	/**
	 * "Walk" from the ClusterJob provided to the final_threshold by combining
	 * ClusterNodes into a tree. Basically, once we're done, the ClusterJob will
	 * tell you what the nodes looked like before the "walk", the returned list
	 * will tell you what the nodes look like at the final threshold, and recursing
	 * through the nodes will tell you what the tree between the two thresholds
	 * looks like.
	 *
	 * It's ... pretty crazy.
	 *
	 * @param job			The cluster job we need to 'extend'. It's kinda hard
	 *						(and, I think, computationally expensive?) to do
	 *						agglumeration right from the sequence list level;
	 *						it's much easier to start off with a set of clusters.
	 * @param final_threshold	The final threshold to step up to.
	 * @return A list of ClusterNodes left standing at the final_threshold.
	 */
	public static List<ClusterNode>	walkToDistance(ClusterJob job, double final_threshold) {
		List<Cluster> clusters =		job.getClusters();
		ArrayList<ClusterNode> frame =	new ArrayList<ClusterNode>();

		Linkage linkage =	job.getLinkage();
		double threshold =	job.getThreshold();

		// Load the frame - one ClusterNode per incoming cluster.
		for(Cluster c: clusters) {
			frame.add(new ClusterNode(threshold, c));
		}

		while(threshold < final_threshold) {
			System.err.println(" - Moving frame to: " + percentage(threshold) + "%, " + frame.size() + " clusters remaining.");

			// Find the pair of ClusterNodes with the smallest pairwise distance.
			double	smallestPairwise = -1;
			ClusterNode	smallest_a = null;
			ClusterNode smallest_b = null;
			for(ClusterNode outer: frame) {
				for(ClusterNode inner: frame) {
					// Don't compare same with same.
					if(outer == inner)	break;

					double d = linkage.pairwiseDistance(outer, inner);

					if(d < 0)
						continue;

					if(smallestPairwise == -1 || d <= smallestPairwise) {
						smallestPairwise =	d;
						smallest_a =		outer;
						smallest_b =		inner;
					}
				}
			}
			
			if(smallestPairwise < 0) {
				System.err.println(" - Process terminated at " + percentage(threshold) + "%: no further clusters have adequate overlap");
				return frame;
			}

			// Oh, Java.
			if(smallest_a == null || smallest_b == null) {
				throw new RuntimeException("Smallest distance obtained but matching clusters not set!");
			}

			if(smallestPairwise < threshold) {
				throw new RuntimeException("Invalid: clusters " + smallest_a + " and " + smallest_b + " have a distance of " + percentage(smallestPairwise) + "%, below the threshold of " + percentage(threshold) + "%");
			}

			// All good. Now merge smallest_a and smallest_b!
			ClusterNode newNode = new ClusterNode(smallestPairwise, smallest_a, smallest_b);
			frame.remove(smallest_a);
			frame.remove(smallest_b);
			frame.add(newNode);

			// And move the threshold
			threshold = smallestPairwise;
		}

		// This is now the final frame.
		return frame;
	}

	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}

	public static void visualizeFrame(List<ClusterNode> nodes) {
		for(ClusterNode node: nodes) {
			visualizeClusterNode(node, 0);
		}
	}

	boolean firstNode = false;
	private static void visualizeClusterNode(ClusterNode cn, int indent) {
		StringBuilder indentation = new StringBuilder();
		for(int x = 0; x < indent; x++) {
			indentation.append(" ");
		}

		// System.out.println(indentation + "CN [" + (cn.getDistance() * 100) + "%] { ");
		System.out.print("(");
		for(Sequences sequences: cn.getSequencesObjects()) {
			Class cls = sequences.getClass();

			if (cls.equals(ClusterNode.class)) {
				visualizeClusterNode((ClusterNode) sequences, indent + 2);
				
			} else if(cls.equals(Cluster.class)) {
				Cluster c = (Cluster) sequences;

				// System.out.println(indentation + "  C(" + c + ")");
				System.out.print("'" + c + "',");
				
			} else if(cls.equals(Sequence.class)) {
				Sequence seq = (Sequence) sequences;

				// System.out.println(indentation + " |gi|" + seq.getGI() + "|");
				System.out.print("'gi|" + seq.getGI() + "|', ");
			}
		}
		System.out.print("),");
	}
}
