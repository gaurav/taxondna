
/*
 *
 *  Linkage
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

/**
 * A cluster job requires a Linkage object to specify how linking
 * should be carried out. Here is what a Linkage looks like.
 */
public interface Linkage {
	/**
	 * Determine if this sequence ought to be added to the
	 * specified cluster.
	 *
	 * @param list
	 * @param seq The sequence which may or may not fit into this cluster.
	 * @return True if this sequence can be linked to this sequence list,
	 *		false otherwise.
	 */
	public boolean canLink(Cluster cluster, Sequence seq, double threshold);

	/**
	 * Every Linkage should be able to represent itself as a string which
	 * includes the word "linkage" in it.
	 * @return The name of this linkage.
	 */
	public String toString();
}
