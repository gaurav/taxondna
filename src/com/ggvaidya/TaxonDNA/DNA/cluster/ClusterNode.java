
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
 * A cluster node associates together sequences which would
 * merge at a particular distance. For instance, a ClusterNode 
 * might specify that three clusters merge together at 4%.
 * 
 * Note that ClusterNode combines Sequences objects, not Cluster
 * objects. This is so that we don't need to create a Cluster
 * object for every sequence which needs combining.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ClusterNode implements Sequences {
	
	/** Sequences which merge at 'distance' */
	protected List<Sequences>	sequences;

	/** The pairwise distance at which 'sequences' merge. */
	protected double			distance;

	/**
	 * A ClusterNode consists of two or more Sequences objects which 
	 * 'merge' at a particular distance.
	 * 
	 * @param distance	The distance at which these sequences merge.
	 * @param seqs		The sequences which merge at the specified distance.
	 */
	public ClusterNode(double distance, Sequences... seqs) {
		this.sequences =	Arrays.asList(seqs);
		this.distance =		distance;
	}

	/**
	 * A ClusterNode can also be created by using a list of sequences.
	 * @param distance	The distance at which these sequences merge.
	 * @param seqs		The sequences which merge at the specified distance.
	 */
	public ClusterNode(double distance, List<Sequences> seqs) {
		this.sequences =	new ArrayList(seqs);
		this.distance =		distance;
	}
	
	/**
	 * @return The distance these Sequences merge at
	 */
	public double getDistance() {	return distance;	}

	/**
	 * @return The list of Sequences objects which merge at this distance.
	 */
	public List<Sequences> getSequencesObjects() {
		return sequences;
	}
	
	/**
	 * Note that this is a recursive list of *all* the sequences at this node;
	 * i.e. we go through our ClusterNodes and their Clusters and their Clusters,
	 * and so on.
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
	 * Note: we agglomerate at percentage(threshold), instead of the threshold itself.
	 * Eventually, we'll fix this to guarantee that we round off to the 2nd decimal
	 * place, but for now, we'll just go with what we've got.
	 *
	 * @param job					The cluster job we need to 'extend'.
	 * @param final_threshold		The final threshold to stop at.
	 * 
	 * @return A list of ClusterNodes left standing at the final_threshold.
	 */
	public static List<ClusterNode>	agglomerateClusters(ClusterJob job, double final_threshold) {
		List<Cluster> clusters =		job.getClusters();
		ArrayList<ClusterNode> frame =	new ArrayList<ClusterNode>();

		Linkage linkage =	job.getLinkage();
		double threshold =	job.getThreshold();

		// Load the frame - one ClusterNode per incoming cluster.
		System.err.println(" - Frame initialized at " + percentage(threshold));
		for(Cluster c: clusters) {
			frame.add(new ClusterNode(threshold, c));
		}

		// Move from threshold to final_threshold, combining ClusterNodes as
		// we go.
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
			// We use percentage(..) to round this off, so that the ClusterNode
			// structure 'merges' similar entries (i.e. 3.151 and 3.156 both
			// become 3.15). Note that percentage(..) is specifically designed
			// for *display* percentages; we will eventually replace this will
			// a Best Practice scientific round-off algorithm.
			ClusterNode newNode = new ClusterNode(percentage(smallestPairwise), smallest_a, smallest_b);
			frame.remove(smallest_a);
			frame.remove(smallest_b);
			frame.add(newNode);

			// And move the threshold
			threshold = smallestPairwise;
		}

		// This is now the final frame.
		return frame;
	}

	/**
	 * @param d The percentage (in 0.0 to 1.0) to round off.
	 * @return The percentage (in 0.00 to 100.0), rounded off to the second decimal
	 * place (I think).
	 */
	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}

	/**
	 * Visualises a 'frame' (list of ClusterNodes) to a string.
	 * 
	 * @param nodes The frame to visualise.
	 * @return The visualisation of this frame as a string.
	 */
	public static String visualizeFrame(List<ClusterNode> nodes) {
		StringBuilder builder = new StringBuilder();
		Iterator i = nodes.iterator();
		while(i.hasNext()) {
			ClusterNode node = (ClusterNode) i.next();
			
			builder.append(node.visualizeNode(0));
			if(i.hasNext())
				builder.append(", ");
		}
		
		return builder.toString();
	}

	boolean firstNode = false;
	/**
	 * Visualise a single cluster node. This is designed to be used
	 * recursively by visualizeFrame(), but you can use it on a single
	 * ClusterNode if you need to.
	 * 
	 * @param indent
	 * @return 
	 */
	public String visualizeNode(int indent) {
		StringBuilder builder = new StringBuilder();
		
		StringBuilder indentation = new StringBuilder();
		for(int x = 0; x < indent; x++) {
			indentation.append(" ");
		}

		// System.out.println(indentation + "CN [" + (cn.getDistance() * 100) + "%] { ");
		builder.append("(");
		Iterator i = getSequencesObjects().iterator();
		while(i.hasNext()) {
			Sequences seqs = (Sequences) i.next();
			Class cls = seqs.getClass();

			if (cls.equals(ClusterNode.class)) {
				((ClusterNode) seqs).visualizeNode(indent + 2);
				
			} else if(cls.equals(Cluster.class)) {
				Cluster c = (Cluster) seqs;

				// System.out.println(indentation + "  C(" + c + ")");
				builder.append("'").append(c);
				
			} else if(cls.equals(Sequence.class)) {
				Sequence seq = (Sequence) seqs;

				// System.out.println(indentation + " |gi|" + seq.getGI() + "|");
				builder.append("'gi|").append(seq.getGI()).append("|'");
			}
			
			if(i.hasNext())
				builder.append(", ");
		}
		builder.append(")");
		
		return builder.toString();
	}
	
	/**
	 * @return A string description of this ClusterNode.
	 */
	@Override
	public String toString() {
		if(sequences.size() == 1)
			return sequences.get(0).toString();
		return sequences.size() + " clusters/sequences at " + percentage(distance) + "%";
	}
}
