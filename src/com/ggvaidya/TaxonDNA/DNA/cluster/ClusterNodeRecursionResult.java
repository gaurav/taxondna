
/*
 *
 *  TaxonDNA
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
 * A rather odd interface, who's only purpose is to allow somebody to
 * quickly recurse into a ClusterNode.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public interface ClusterNodeRecursionResult {
	/**
	 * The ClusterNode.recurse(..) method will call this found(..)
	 * method for every Sequences object "under" this ClusterNode.
	 * Note that this includes every ClusterNode as well! So if
	 * you don't want to look at those, check if seq is a ClusterNode
	 * or not. The only Sequences object which won't be returned
	 * through this interface is the ClusterNode you started looking
	 * at.
	 * 
	 * @param seq		The sequences object which was found.
	 * @param childOf	The ClusterNode which is the immediate parent
	 *		of the sequences object.
	 *					
	 */
	public void found(Sequences seq, ClusterNode childOf);
}