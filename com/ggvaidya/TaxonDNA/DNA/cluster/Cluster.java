
/*
 *
 *  Cluster
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
 * Defines a "cluster" of Sequences, created by a ClusterJob.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class Cluster extends SequenceList {
	/** The cluster job which created this cluster. */
	private ClusterJob		clusterJob;

	/**
	 * Create a cluster. This requires a cluster job: if
	 * you're only talking about a "bunch of sequences",
	 * just make a SequenceList!
	 */
	public Cluster(ClusterJob clusterJob) {
		this.clusterJob = clusterJob;
	}

	/** Return the cluster job which created this cluster. */
	public ClusterJob getClusterJob() {
		return clusterJob;
	}

	/** Describe this cluster as a string */
	@Override
	public String toString() {
		return
			"A cluster of " + count() +
			" sequences created by " + clusterJob.toString() +
			" encapsulating sequence list: " + super.toString()
		;
	}
}
