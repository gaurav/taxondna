
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
import java.awt.event.*;
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
		int verticalSpace =		0;
		int horizontalSpace =	50;
		int columnWidth =		button.getPreferredSize().width		+ horizontalSpace;
		int rowHeight =			button.getPreferredSize().height	+ verticalSpace;
		
		double smallestDistance =	clusterJob.getThreshold();
		double largestDistance =	to;
		
		double columnSpan =	0.005;
		int columns =		(int)((largestDistance - smallestDistance) / columnSpan) + 2;
		
		System.err.println("Columns: " + columns);
		
		// Step 2. Start drawing the ClusterNodes.
		int y = 0;
		for(ClusterNode node: final_frame) {
			y = renderClusterNode(node, y, columns, rowHeight, columnWidth, columns, smallestDistance, largestDistance, null);
			
		}

		panel.setPreferredSize(new Dimension((columns + 2) * columnWidth, (y * rowHeight)));
	}

	private int renderClusterNode(Sequences seqs, int y, int column, int rowHeight, int columnWidth, int columns, double smallestDistance, double largestDistance, Component parent) {
		// Step 1: render the sequences object itself.
		JComponent current =	createSequencesComponent(seqs);
		
		current.setBounds(
			(columnWidth * column),
			(int)(y * rowHeight),
			current.getPreferredSize().width,
			current.getPreferredSize().height
		);
		panel.add(current);
		
		if(parent != null) {
			LineJoining lj = new LineJoining(parent, current);
			
			Rectangle current_bound =	current.getBounds();
			Rectangle parent_bound =	parent.getBounds(); 
			
			lj.setBounds(
				current.getX(),
				parent.getY(),
				parent_bound.x + parent_bound.width - current_bound.x,
				current_bound.y + current_bound.height - parent_bound.y
			);
			
			/*
			lj.setBounds(
				current_bound.x,
				current_bound.y + current_bound.height,
				parent_bound.x + parent_bound.width,
				parent_bound.y
			);
			 */
			
			panel.add(lj);
			
			// System.err.println("LineJoining " + lj + " added: " + parent + ", " + current);
		}
		
		// Step 2: render the immediate child nodes.
		if(seqs.getClass().equals(ClusterNode.class)) {
			ClusterNode node = (ClusterNode) seqs;
			
			int subnode_column = (int)((percentage(node.getDistance()) - smallestDistance*100)/0.01) + 1;
			for(Sequences subnode_seqs: node.getSequencesObjects()) {
				y = renderClusterNode(subnode_seqs, y, subnode_column, rowHeight, columnWidth, columns, smallestDistance, largestDistance, current);
			}
		} else {
			// "Drop" it to the first column.
			JComponent first_col =	createSequencesComponent(seqs);
		
			current.setBounds(
				0,
				(int)(y * rowHeight),
				current.getPreferredSize().width,
				current.getPreferredSize().height
			);
			panel.add(current);
		}
		
		return y + 1;
	}
	
	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
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

class LineJoining extends Component {
	Component	a;
	Component	b;
	
	public LineJoining(Component a, Component b) {
		this.a = a;
		this.b = b;
	}
	
	@Override
	public void paint(Graphics g) {
		// System.err.println("paint() fired: " + getBounds() + " bounds, " + getLocation() + " location.");
		g.setColor(Color.BLACK);
		
		// We need to draw three line segments:
		//	1. Center of component 'a' to the midpoint of the 'x' distance between 'a' and 'b'
		//	2. Travel up or down to the right 'y' dimension.
		//	3. Travel to the center of component 'b'.
		//g.drawLine(getHeight(), 0, 0, getWidth());
		
		// g.drawRect(0, 0, getWidth(), getHeight());
		
		// A 3-pixel thick line.
		g.drawRect(0, (int)(getHeight()/2)-3, getWidth(), (int)(getHeight()/2)+3);
	}
	
	@Override
	public String toString() {
		return ("location: " + getLocation() + ", bounds: " + getBounds());
	}
}