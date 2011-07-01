
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

package com.ggvaidya.TaxonDNA.DtClusters.gui;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.UI.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedList;
import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.*;
import javax.swing.border.*;

/**
 * A SequencesViewer displays a set of sequences. Although we're going to
 * write it specifically for Cluster objects, the dream is that this will
 * be a general component (i.e. moved to TaxonDNA.UI) which can display any
 * Sequences object in a cogent fashion. But for now, Clusters.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
class SequencesViewer extends JDialog {
	
	JFrame		parent;
	String		title;
	String		description;
	Sequences	sequences;
	
	JTextArea	ta_description =	new JTextArea(3, 20);
	
	SequencesVisualization visualization;
	JComponent	comp_visualization;
	
	public SequencesViewer(JFrame parent, String title, String description, Sequences sequences) {
		super(parent, "Sequences: " + description + " (" + sequences.getSequences().count() + ")", false);
		
		this.parent =		parent;
		this.title =		title;
		this.description =	description;
		this.sequences =	sequences;
		
		createUI();
		
		setVisible(true);
	}
	
	/**
	 * Creates the user interface for this sequences viewer.
	 */
	private void createUI() {
		setLayout(new BorderLayout());
		
		JPanel panel_top = new JPanel();
		panel_top.setLayout(new BorderLayout());
		panel_top.setBorder(new SoftBevelBorder(SoftBevelBorder.RAISED));
		add(panel_top, BorderLayout.NORTH);
		
		ta_description.setEditable(false);
		ta_description.setText(description);
		panel_top.add(new JScrollPane(ta_description));
		
		JPanel exports = new JPanel();
		exports.setLayout(new GridLayout(3, 1));
		final Sequences		seqs = sequences;
		final JFrame		frame = parent;
		JButton btn_export_fasta = new JButton(new AbstractAction("Export as FASTA") {
			public void actionPerformed(ActionEvent e) {
				FileDialog fd = new FileDialog(frame, "Where would you like to save these sequences as FASTA?", FileDialog.SAVE);
				fd.setVisible(true);
				
				File f = null;
				if(fd.getDirectory() != null) {
					if(fd.getFile() != null) {
						f = new File(fd.getDirectory(), fd.getFile());
					}
				} else if(fd.getFile() != null) {
					f = new File(fd.getFile());
				}
				
				if(f == null)
					return;
				
				SequenceList sl = seqs.getSequences();
				try {
					new FastaFile().writeFile(f, sl, ProgressDialog.create(
						frame, 
						"Exporting sequences " + title + " to FASTA file " + f, 
						sl.count() + " sequences are being exported to file '" + f + "' in FASTA format."
					));
				} catch(IOException ex) {
					JOptionPane.showMessageDialog(parent, frame);
				} catch(DelayAbortedException ex) {
					return;
				}
			}
		});
		exports.add(btn_export_fasta);
		panel_top.add(exports, BorderLayout.EAST);
		
		// Create a visualization.
		visualization = getVisualizationFor(sequences);
		comp_visualization = visualization.getVisualization();
		add(comp_visualization);
		
		pack();
	}

	/**
	 * Determines the appropriate visualization component for the Sequences
	 * object provided.
	 * 
	 * @param sequences The sequences to visualize.
	 * @return A JComponent object which provided the visualization. Note that
	 *	any required JScrollPanes would already have been added here. 
	 */
	public SequencesVisualization getVisualizationFor(Sequences sequences) {
		return new SequenceListVisualization(sequences.getSequences());
	}
}

interface SequencesVisualization {
	public JComponent getVisualization();
}

/**
 * Default visualization for sequences. Essentially the same as the SequenceMatrix
 * visualization.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
class SequenceListVisualization implements TableModel, SequencesVisualization {
	SequenceList	sl;
	JTable			table;
	
	public SequenceListVisualization(SequenceList sl) {
		this.sl = sl;
		
		table = new JTable(this);
		
		table.setModel(this);
		table.setRowSorter(new TableRowSorter(this));
	}
	
	public JComponent getVisualization() {
		return new JScrollPane(table);
	}
	
	public int getRowCount() {
		return sl.count();
	}

	public int getColumnCount() {
		return 5;
	}

	public String getColumnName(int columnIndex) {
		switch(columnIndex) {
			case 0:		return "#";
			case 1:		return "Name";
			case 2:		return "Length (bp)";
			case 3:		return "'N's";
			case 4:		return "'?'s";
		}
		
		return "(err)";
	}

	public Class<?> getColumnClass(int columnIndex) {
		switch(columnIndex) {
			case 0:		return Integer.class;
			case 1:		return String.class;
			case 2:		return Integer.class;
			case 3:		return Integer.class;
			case 4:		return Integer.class;
		}
		
		return String.class;
	}

	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	public Object getValueAt(int rowIndex, int columnIndex) {
		int row = table.getRowSorter().convertRowIndexToModel(rowIndex);
		
		Sequence seq = (Sequence) sl.get(row);
		if(seq == null)
			return "(err)";
		
		switch(columnIndex) {
			case 0:		return (rowIndex + 1);
			case 1:		return seq.getName();
			case 2:		return seq.getActualLength();
			case 3:
				int count_n = 0;
				for(char ch: seq.getSequence().toCharArray()) {
					if(ch == 'N' || ch == 'n') count_n++;
				}
				return count_n;
				
			case 4:
				int count_q = 0;
				for(char ch: seq.getSequence().toCharArray()) {
					if(ch == '?') count_q++;
				}
				return count_q;
		}
		
		return "(err)";
	}

	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		return;
	}

	protected java.util.List<TableModelListener> tableModelListeners = new LinkedList<TableModelListener>();
	public void addTableModelListener(TableModelListener l) {
		tableModelListeners.add(l);
	}

	public void removeTableModelListener(TableModelListener l) {
		tableModelListeners.remove(l);
	}
}