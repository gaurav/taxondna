
/*
 *
 *  TaxonDNA
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

package com.ggvaidya.TaxonDNA.DtClusters.gui;

import java.awt.event.ActionEvent;
import java.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.swing.border.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.UI.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;
import com.ggvaidya.TaxonDNA.DtClusters.DtClusters;

/**
 * This class carries out and visualises "agglomerative clustering". We
 * keep fusing the smallest clusters together between the distances requested,
 * and visualise this onto the specified Canvas. The actual work is carried out
 * by ClusterNode.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class AgglomerateClusters extends JPanel {
	
	// Variables that handle the processing.
	ClusterJob					clusterJob =	null;

	// Variables that handle the UI.
	java.util.List<ClusterNode> finalFrame =	null;
	
	/**
	 * On creation, we need to clear out the UI.
	 */
	public AgglomerateClusters() {
		removeAll();
		
		JTextArea ta_information = new JTextArea(
			"This is " + DtClusters.getName() + "/" + DtClusters.getVersion() + "\n" + 
			DtClusters.getCopyrightNotice()
		);
		ta_information.setEditable(false);
		
		add(new JScrollPane(ta_information));
	}
	
	/**
	 * Set the initial state. When this happens, the original
	 * clustering has already been done. This is therefore
	 * also the right time to clear out our variables and
	 * wipe stuff clean.
	 * 
	 * @param job The ClusterJob to visualize.
	 */
	public void changeInitialState(ClusterJob job) {
		clusterJob = job;
		
		// Clear out the UI.
		removeAll();
		setLayout(new GridLayout(job.getClusters().size(), 1));
		
		// Visualize the clusters.
		for(Cluster c: job.getClusters()) {
			final Cluster cluster = c;
			
			add(new JButton(new AbstractAction(c.toString()) {
				public void actionPerformed(ActionEvent e) {
					SequencesViewer viewer = new SequencesViewer(
						null, 
						"Cluster: " + cluster.getSpeciesNameSummary(), 
						cluster.toString(),
						cluster
					);
					viewer.setVisible(true);
				}
			}));
		}
	}
	
	/**
	 * Agglomerate the clusters to the threshold required. This
	 * actually handles the processing, then uses the final frame
	 * to render the view.
	 * 
	 * This was sort of written in the heat of the moment, and
	 * might need to be cleaned up later.
	 * 
	 * @param	to		The threshold to clean it up to (on a 0.0 to 1.0 scale)
	 * @return	The final frame.
	 * @throws	DelayAbortedException 
	 */
	public java.util.List<ClusterNode> agglomerateClusters(double to) throws DelayAbortedException {
		DelayCallback delay = ProgressDialog.create(
			null, 
			"Extending clustering ...", 
			"Clustering is now being extended to " + (to*100) + "% threshold. We apologize for the inconvenience!"
		);
				
		finalFrame = ClusterNode.agglomerateClusters(clusterJob, to, delay);
		redrawView(finalFrame, clusterJob.getThreshold(), to);
		
		return finalFrame;
	}

	
	/**
	 * Redraw the view based on the final frame.
	 * 
	 * @param final_frame	The "final frame": the list of ClusterNodes we end up with at the 'to' threshold.
	 * @param from			The initial threshold.
	 * @param to			The final threshold.
	 */
	public void redrawView(java.util.List<ClusterNode> final_frame, double from, double to) {
		
		removeAll();
		
		// Reverse the tree: we want to be able to quickly find out who combines
		// with who at what percentage. So we'll create a HashMap which maps
		// Clusters with their corresponding ClusterNode.
		
		// Note that we use Sequences, not Cluster, as the key; this is so that
		// ClusterNodes (or indeed SequenceLists) can be stored in this node map.
		final HashMap<Sequences, ClusterNode> clusterNodeMap = new HashMap<Sequences, ClusterNode>();
		final ArrayList<Sequences> sequences = new ArrayList<Sequences>();
		
		for(ClusterNode node: final_frame) {
			node.recurse(new ClusterNodeRecursionResult() {
				public void found(Sequences seqs, ClusterNode childOf) {
					clusterNodeMap.put(seqs, childOf);
					sequences.add(seqs);
				}
			});
		}
		
		// Now, we already have all the clusters originally present, so we can
		// start drawing from there, then draw in the ClusterNodes as necessary.
		setLayout(new GridLayout(clusterJob.count(), 1));
		
		for(Sequences seqs: sequences) {
			JPanel panel = new JPanel();
			add(panel);
			
			RightLayout rl = new RightLayout(panel);
			panel.setLayout(rl);
			rl.add(getSequencesComponent(seqs), RightLayout.NONE);
			
			Sequences s = seqs;
			while(true) {
				// Keep going until we hit the end of the chain.
				if(!clusterNodeMap.containsKey(s))
					break;
				
				// Until then, keep add(..)ing stuff.
				s = clusterNodeMap.get(s);
				rl.add(getSequencesComponent(s), RightLayout.BESIDE);
			}
		}
	}
	
	private JComponent getSequencesComponent(Sequences seqs) {
		final Sequences sequences = seqs;
		return new JButton(new AbstractAction(seqs.toString()) {
			public void actionPerformed(ActionEvent e) {
				SequencesViewer viewer = new SequencesViewer(
					null, 
					sequences
				);
				viewer.setVisible(true);
			}
		});
	}

	/**
	 * Calculates the display percentage (0.00 to 100.00%) from an actual
	 * percentage (0.000 to 1.000).
	 * 
	 * @param	d		The actual percentage (0.000 to 1.000).
	 * @return	The display percentage (0.00 to 100.00%).
	 */
	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}
}