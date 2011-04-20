
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
	java.util.List<ClusterNode>	final_frame =	null;

	public AgglomerateClusters()	{
		panel.setLayout(null);
	}

	public JPanel getPanel()			{	return panel;	}

	public void changeInitialState(ClusterJob job) {
		clusterJob = job;

		panel.removeAll();

		int largestWidth = -1;

		int y = 10;
		for(Cluster c: clusterJob.getClusters()) {
			String title = c.toString();

			if(title.length() > 30) {
				title = title.substring(0, 26) + " ...";
			}
			
			JButton btn =		new JButton(title);
			btn.setToolTipText(c.toString());

			// Is this really the best way to do this?
			Insets insets =		btn.getInsets();
			Dimension size =	btn.getPreferredSize();

			if(largestWidth == -1 || (largestWidth - insets.left) < size.width)
				largestWidth = size.width + insets.left;

			btn.setBounds(
				insets.left,
				y,
				size.width,
				size.height
			);

			y += size.height + insets.top + 5;
			
			panel.add(btn);
		}

		int totalHeight = y + 5;

		panel.setPreferredSize(new Dimension(largestWidth + 20, totalHeight));
	}
}
