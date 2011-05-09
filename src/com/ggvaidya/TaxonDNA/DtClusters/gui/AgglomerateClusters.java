
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

import java.util.*;
import java.awt.*;
import javax.swing.*;

import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;

/**
 * This class carries out and visualises "agglomerative clustering". We
 * keep fusing the smallest clusters together between the distances requested,
 * and visualise this onto the specified Canvas. The actual work is carried out
 * by ClusterNode.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class AgglomerateClusters {
	JPanel						panel =			new JPanel();
	ClusterJob					clusterJob =	null;

	public AgglomerateClusters()	{
		panel.setLayout(null);
	}

	public JPanel getPanel()			{	return panel;	}

	public void changeInitialState(ClusterJob job) {
		clusterJob = job;
		
		panel.removeAll();
		panel.repaint();
		
		JLabel label = new JLabel("Please wait, processing has not finished yet.");
		label.setBounds(
			0, 0,
			label.getPreferredSize().width + 20,
			label.getPreferredSize().height + 20
		);
		panel.add(label);
		
		panel.setPreferredSize(
			new Dimension(
				label.getPreferredSize().width + 20,
				label.getPreferredSize().height + 20
			)
		);
	}
	
	/**
	 * Redraw the view based on the final frame.
	 * 
	 * @param final_frame
	 * @param from
	 * @param to 
	 */
	public void redrawView(java.util.List<ClusterNode> final_frame, double from, double to) {
		if(final_frame == null)
			throw new RuntimeException("No frame specified! Can not draw!");
		
		if(clusterJob.getThreshold() != from)
			throw new RuntimeException("'from' specified as " + from + ", but the clusterJob started at " + clusterJob.getThreshold());
		
		panel.removeAll();
		panel.repaint();
		
		// Step 1. Figure out our "key lengths", so it's easy to do the pixel
		// calculations.
		JButton button =	new JButton("Checking button height 1234567"); // This string is 30 characters long.
		
		// 1.1. Delimit a grid.
		int verticalSpace =		5;
		int horizontalSpace =	5;
		int columnWidth =	button.getPreferredSize().width		+ horizontalSpace;
		int rowHeight =		button.getPreferredSize().height	+ verticalSpace;
		
		double smallestDistance =	clusterJob.getThreshold();
		double largestDistance =	to;
		
		double columnSpan =	0.005;
		int columns =		(int)((largestDistance - smallestDistance) / columnSpan) + 1;
		
		System.err.println("Columns: " + columns);
		
		// Step 2. Start drawing the ClusterNodes.
		int y = 0;
		for(ClusterNode node: final_frame) {
			// All our components for this node will have to fit within a large
			// rectangle, whose size depends on the number of child nodes that
			// this cluster has.
			java.util.List<Sequences> leaves = node.getLeafNodes();
			
			// Draw all the leaf nodes on the left first.
			for(Sequences seqs: leaves) {
				y++;
				
				JComponent comp = createSequencesComponent(node);
				comp.setBounds(
					0,
					(y * rowHeight),
					comp.getPreferredSize().width,
					comp.getPreferredSize().height
				);
				panel.add(comp);
			}
			
			// Finally, draw this button at the final line.
			int rows =		(leaves.size());
			double diff_y =	(y - ((double)rows/2));
			
			JComponent comp = createSequencesComponent(node);
			comp.setBounds(
				(columnWidth * columns),
				(int)(diff_y * rowHeight),
				comp.getPreferredSize().width,
				comp.getPreferredSize().height
			);
			panel.add(comp); 
			
			// Next, draw "child" components at their respective places.
			for(Sequences seqs: node.getSequencesObjects()) {
				comp = createSequencesComponent(seqs);
			
				double percentage = (node.getDistance() - smallestDistance)/(largestDistance - smallestDistance);
				
				comp.setBounds(
					(int)(columnWidth * columns * percentage) + columnWidth,
					(y * rowHeight),
					comp.getPreferredSize().width,
					comp.getPreferredSize().height
				);
				
				panel.add(comp);
			}
			
		}

		panel.setPreferredSize(new Dimension((columns + 1) * columnWidth, (y * rowHeight)));
	}

	public java.util.List<ClusterNode> agglomerateClusters(double to) {
		java.util.List<ClusterNode> nodes = ClusterNode.agglomerateClusters(clusterJob, to);
		
		redrawView(nodes, clusterJob.getThreshold(), to);
		
		return nodes;
	}

	private JComponent createSequencesComponent(Sequences sequences) {
		String title = sequences.toString();

		if(title.length() > 30) {
			title = title.substring(0, 26) + " ...";
		}

		JButton btn =		new JButton(title);
		btn.setToolTipText(sequences.toString());
		
		return btn;
	}
}
