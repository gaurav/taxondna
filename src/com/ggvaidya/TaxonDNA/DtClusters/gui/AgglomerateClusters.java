
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
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import java.awt.BorderLayout;
import java.awt.Container;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;
import com.ggvaidya.TaxonDNA.UI.*;

/**
 * This class carries out and visualises "agglomerative clustering". We
 * keep fusing the smallest clusters together between the distances requested,
 * and visualise this onto the specified Canvas. The actual work is carried out
 * by ClusterNode.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class AgglomerateClusters {
	JPanel						parent;
	ClusterJob					clusterJob =	null;

	public AgglomerateClusters(JPanel parent)	{
		this.parent = parent;
		
		parent.setLayout(new BorderLayout());
	}

	public void changeInitialState(ClusterJob job) {
		clusterJob = job;
	}
	
	/**
	 * Redraw the view based on the final frame.
	 * 
	 * @param final_frame
	 * @param from
	 * @param to 
	 */
	public void redrawView(java.util.List<ClusterNode> final_frame, double from, double to, DelayCallback delay) throws DelayAbortedException {
		TableClusterView tcv = new TableClusterView(clusterJob, final_frame);
		JTable table = new JTable(tcv);
		
		//parent.removeAll();
		parent.add(new JScrollPane(table));
		table.doLayout();
	}

	public java.util.List<ClusterNode> agglomerateClusters(double to, DelayCallback delay) throws DelayAbortedException {
		java.util.List<ClusterNode> nodes = ClusterNode.agglomerateClusters(clusterJob, to, delay);
		
		redrawView(nodes, clusterJob.getThreshold(), to, delay);
		
		return nodes;
	}

	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}
}

class TableClusterView implements TableModel {
	ClusterJob			job;
	List<ClusterNode>	final_frame;
	List<Integer>		distances;
	Sequences[][]		contents;
	
	public TableClusterView(ClusterJob job, java.util.List<ClusterNode> final_frame) {
		this.job = job;
		this.final_frame = final_frame;
		
		// Step 1: figure out the distances we're covering.
		Map<Integer, Integer> distanceMap = new HashMap<Integer, Integer>();
		
		for(ClusterNode node: final_frame) {
			addDistances(distanceMap, node);
		}
		
		distances = new ArrayList<Integer>(distanceMap.keySet());
		Collections.sort(distances); 
		
		// Step 2: figure out the contents. This is a sparse array, so:
		contents = new Sequences[job.count() + 1000][distances.size()];
		
		int y = 0;
		for(ClusterNode node: final_frame) {
			//contents[y][distances.size()] = node;
			y = setContents(contents, node, y);
		}
	}

	public int getRowCount() {
		return job.count();
	}

	public int getColumnCount() {
		return distances.size() + 1;
	}

	public String getColumnName(int columnIndex) {
		if(columnIndex == 0)
			return "Index";
		return ((double)distances.get(columnIndex - 1)/100) + "%";
	}

	public Class<?> getColumnClass(int columnIndex) {
		return String.class;
	}

	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	public Object getValueAt(int rowIndex, int columnIndex) {
		if(columnIndex == 0)
			return (rowIndex + 1);
		
		columnIndex--;
		
		if(contents[rowIndex][columnIndex] == null) {
			if(columnIndex == 0 || columnIndex == distances.size() + 1) {
				return "";
			}
			
			// To draw a line or not? The answer depends on whether
			// the next sequence cluster is to the right, or  
			// above.
			for(int col = columnIndex; col < getColumnCount() - 1; col++) {
				if(contents[rowIndex][col] != null) {
					// Something on this line itself.
					return "--";
				}
			
				if(rowIndex > 1 && col < getColumnCount() - 1) {
					if(contents[rowIndex - 1][col] != null) {
						if(contents[rowIndex - 1][columnIndex] != null)
							return "-/";
						return "--";
					}
				}
			}
			
			return "";
		} else {
			return contents[rowIndex][columnIndex].toString();
		}
	}

	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		throw new UnsupportedOperationException("Not supported yet ... ");
	}

	Set<TableModelListener> listeners = new HashSet<TableModelListener>();
	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}

	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);
	}

	private void addDistances(Map<Integer, Integer> distances, ClusterNode node) {
		Integer integralDistance = integralDistance(node.getDistance());
		
		if(distances.containsKey(integralDistance)) {
			distances.put(integralDistance, new Integer(distances.get(integralDistance) + 1));
		} else {
			distances.put(integralDistance, 1);
		}

		for(Sequences seqs: node.getSequencesObjects()) {
			if(seqs.getClass().equals(ClusterNode.class))
				addDistances(distances, (ClusterNode) seqs);
		}
	}
	
	private static Integer integralDistance(double d) {
		return new Integer((int)(percentage(d) * 100));
	}
	
	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}

	private int setContents(Sequences[][] contents, Sequences seqs, int y) {
		if(seqs.getClass().equals(ClusterNode.class)) {
			ClusterNode node = (ClusterNode) seqs;
			
			int x = distances.indexOf(integralDistance(node.getDistance()));
		
			try {
				contents[y][x] = seqs;
			} catch(ArrayIndexOutOfBoundsException ex) {
				throw new RuntimeException("Attempting to set (y=" + y + ", x=" + x + ") when array is only [y=" + contents.length + ", x=" + contents[0].length + "]");
			}
		
			for(Sequences subseqs: node.getSequencesObjects()) {
				y = setContents(contents, subseqs, y);
			}
		} else {
			try {
				contents[y][0] = seqs;
			} catch(ArrayIndexOutOfBoundsException ex) {
				throw new RuntimeException("Attempting to set (y=" + y + ", x=0) when array is only [y=" + contents.length + ", x=0]");
			}
			y++;
		}
		
		return y;
	}
}