
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

import java.util.*;

/**
 * A cluster node associates together clusters which would
 * merge at a particular distance. For instance, a ClusterNode might specify
 * that three clusters merge together at 4%.
 *
 * Note that ClusterNodes can only contain *Clusters*. If you need to use a
 * Sequence instead, pop it into a one-sequence Cluster.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class ClusterNode {
	/** Cluster node */

	/** Clusters to match */
	protected List<Cluster>		clusters;

	/** Distance at which they would merge. */
	protected double			distance;

	/**
	 * A ClusterNode consists of two or more Clusters which 'merge' at
	 * a particular distance.
	 */
	public ClusterNode(double distance, Cluster... clusters) {
		this.clusters =		Arrays.asList(clusters);
		this.distance =		distance;
	}

	/**
	 * @return What distance do these clusters merge at?
	 */
	public double getDistance() {	return distance;	}

}
