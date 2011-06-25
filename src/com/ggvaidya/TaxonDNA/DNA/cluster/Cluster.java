
/*
 *
 *  DtClusters
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
import com.ggvaidya.TaxonDNA.DNA.*;

import java.util.*;

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
public class Cluster extends SequenceList implements Comparable, Sequences {
	/** The job which created this cluster */
	protected ClusterJob job;

	/**
	 * Creates an empty cluster.
	 */
	public Cluster(ClusterJob job) {	this.job = job; }

	/**
	 * Creates a cluster from another SequenceList.
	 */
	public Cluster(ClusterJob job, SequenceList list) { super(list); this.job = job; }

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
		SpeciesDetails details;
		try {
			details = getSpeciesDetails(null);
		} catch(DelayAbortedException e) {
			return "A cluster";
		}

		/*
		 * For full sequence listing.

		StringBuilder seqs = new StringBuilder();
		for(Object obj: this) {
			Sequence seq = (Sequence) obj;

			seqs.append(" - ")
				.append(seq.getSpeciesName())
				.append(": ")
				.append(seq.getFullName())
				.append(" (")
				.append(seq.getLength())
				.append(" bp)")
				.append("\n");
		}
		 * 
		 */

		String distances = ""; // (distances: " + getDistances() + ")";

		int species_count = details.getSpeciesCount();
		if(species_count == 1) {
			return "A " + getClusterSummary() + " cluster of " + details.getSequencesCount() + " sequences from " + details.getSpeciesNamesIterator().next() +
					" " + distances;
		} else {
			HashMap<String, SpeciesDetail> speciesDetails = new HashMap<String, SpeciesDetail>(details.getDetails());
			ArrayList<String> speciesNames = new ArrayList<String>(speciesDetails.keySet());

			if(species_count == 2) {
				return "A " + getClusterSummary() + " cluster of " +
					speciesDetails.get(speciesNames.get(0)).getSequencesCount() + " sequence(s) from " + speciesNames.get(0) +
					" and " +
					speciesDetails.get(speciesNames.get(1)).getSequencesCount() + " sequence(s) from " + speciesNames.get(1) +
					" " + distances
				;
			} else {
				int diff_sequences = count()
						- speciesDetails.get(speciesNames.get(0)).getSequencesCount()
						- speciesDetails.get(speciesNames.get(1)).getSequencesCount();

				return "A " + getClusterSummary() + " cluster of " +
					speciesDetails.get(speciesNames.get(0)).getSequencesCount() + " sequence(s) from " + speciesNames.get(0) +
					", " +
					speciesDetails.get(speciesNames.get(1)).getSequencesCount() + " sequence(s) from " + speciesNames.get(1) +
					", and " + diff_sequences + " other sequences from " + (species_count - 2) + " species " +
					distances
				;
			}
		}
	}

	/**
	 * A "cluster summary" is a one-word description of a cluster.
	 *
	 * @return Either "complete", "incomplete" or "mixed"
	 */
	public String getClusterSummary() {
		if(getSpeciesCount() == 1) {
			SpeciesDetails det_cluster;
			SpeciesDetails det_job;
			
			try {
				det_cluster = getSpeciesDetails(null);
				det_job = job.getOriginalSequences().getSequences().getSpeciesDetails(null);
			} catch(DelayAbortedException e) {
				return "aborted";
			}

			SpeciesDetail detail = det_job.getSpeciesDetailsByName((String) det_cluster.getSpeciesNamesIterator().next());
			if(detail.getSequencesCount() != count()) {
				return "incomplete";
			} else {
				return "complete";
			}
		} else {
			return "mixed";
		}
	}

	public int compareTo(Object t) {
		Cluster other = (Cluster) t;

		int diff = (other.getSpeciesCount() - getSpeciesCount());

		if(diff != 0)
			return diff;
		else
			return (other.count() - count());
	}

	public int getSpeciesCount() {
		try {
			return getSpeciesDetails(null).count();
		} catch(DelayAbortedException e) {
			return -1;
		}
	}

	public String getDistances() {
		double smallest =	-1;
		double largest =	-1;
		double sum =		0;
		int count =			0;

		for(Object outer: this) {
			for(Object inner: this) {
				if(outer == inner)
					continue;

				double pairwise = ((Sequence)outer).getPairwise((Sequence)inner);
				if(pairwise > 0) {
					count++;
					sum += pairwise;

					if(smallest == -1 || pairwise < smallest)
						smallest =	pairwise;
					if(largest == -1 || pairwise > largest)
						largest =	pairwise;
				}
			}
		}

		double average = -1;
		if(count > 0)
			average = (sum/count);


		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(smallest, 1) +
			"|" + com.ggvaidya.TaxonDNA.DNA.Settings.percentage(average, 1) +
			"|" + com.ggvaidya.TaxonDNA.DNA.Settings.percentage(largest, 1)
		;
	}

	/**
	 * @return A unique identifier for this cluster's sequences. Two clusters
	 *	with identical sequences *will* return identical Contents UUIDs,
	 *  *as long as* the two clusters were generated from the same original
	 *  SequenceList. If you use two SequenceLists, all bets are off.
	 */
	public String getContentsUUID() {
		StringBuilder uuid = new StringBuilder();
		resort(SORT_BYNAME);
		for(Object o: this) {
			Sequence seq = (Sequence) o;

			uuid.append("|");
			uuid.append(seq.getUUID());
			uuid.append("|");
		}

		return uuid.toString();
	}

}
