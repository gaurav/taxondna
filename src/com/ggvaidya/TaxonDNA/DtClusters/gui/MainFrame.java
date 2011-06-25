
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

import com.ggvaidya.TaxonDNA.DtClusters.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.UI.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;

/**
 * The main frame creates a frame for us to use to put DtClusters stuff
 * up.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class MainFrame extends JFrame {
	
	File			currentFile =	null;
	SequenceList	sequenceList =	null;
	
	// UI objects.
	JTextField		tf_filename =	new JTextField("No file loaded", JLabel.LEFT);
	JTextField		tf_status =		new JTextField("Starting application ...", JLabel.LEFT);
	JTable			tb_fileinfo =	new JTable();
	JTable			tb_clusters =	new JTable();
	
	JTextField tf_minOverlap;
	JTextField tf_startThreshold;
	JTextField tf_endThreshold;
	
	/**
	 * Create the frame.
	 */
	public MainFrame() {
		super(DtClusters.getName() + "/" + DtClusters.getVersion());
		setDefaultCloseOperation(MainFrame.DISPOSE_ON_CLOSE);
		
		setupUI();
	}
	
	/**
	 * Set up the user interface.
	 */
	public void setupUI() {
		setLayout(new BorderLayout());
		
		setStatus("Please wait, starting application ...");
		
		/* 
		 * TOP PANEL (p_top)
		 * allows you to change the currently loaded file. 
		 */
		JPanel p_top = new JPanel();
		p_top.setLayout(new BorderLayout());
		p_top.setBorder(new SoftBevelBorder(SoftBevelBorder.RAISED));
		add(p_top, BorderLayout.NORTH);
		
		// Display the filename.
		tf_filename.setEditable(false);
		tf_filename.setFont(new Font("serif", Font.PLAIN, 24));
		tf_filename.setBorder(new LineBorder(Color.GRAY));
		p_top.add(tf_filename);
		
		// Action panel.
		JPanel p_action = new JPanel();
		p_action.setLayout(new FlowLayout(FlowLayout.RIGHT));
		p_top.add(p_action, BorderLayout.SOUTH); 
		
		// Button to change the filename.
		final MainFrame thisObject = this;
		JButton btn_changeFile = new JButton(new AbstractAction("Change file ...") {
			public void actionPerformed(ActionEvent e) {
				FileDialog fd = new FileDialog(thisObject, "Select file to load ...", FileDialog.LOAD);
				fd.setVisible(true);
				
				File file = null;
				if(fd.getDirectory() != null) {
					if(fd.getFile() != null) {
						file = new File(fd.getDirectory(), fd.getFile());
					}
				} else {
					file = new File(fd.getFile());
				}
				
				if(file != null) {
					thisObject.changeFile(file);
				}
			}
		});
		p_action.add(btn_changeFile);
		
		JButton btn_recluster = new JButton(new AbstractAction("Recluster") {
			public void actionPerformed(ActionEvent e) {
				thisObject.recluster();
			}
		});
		p_action.add(btn_recluster);
		
		/*
		 * MAIN PANEL (p_main)
		 * displays information on the current dataset.
		 */
		JPanel p_main = new JPanel();
		add(p_main);
		
		// Results (TBD).
		JPanel p_results = new JPanel();
		p_main.add(p_results);
		
		// Side panel.
		JPanel p_side = new JPanel();
		p_side.setBorder(new SoftBevelBorder(SoftBevelBorder.RAISED));
		
		JSplitPane	splitPane = new JSplitPane(
			JSplitPane.HORIZONTAL_SPLIT,		
			p_side,
			p_results
		);
		
		splitPane.setResizeWeight(0.25);
		add(splitPane);
		
		// Set up some settings.
		JPanel p_settings = new JPanel();
		p_settings.setBorder(new SoftBevelBorder(SoftBevelBorder.LOWERED));
		RightLayout rl = new RightLayout(p_settings);
		p_settings.setLayout(rl);
		
		// Minimum overlap.
		tf_minOverlap = new JTextField();
		tf_minOverlap.setHorizontalAlignment(JTextField.RIGHT);
		rl.add(new JLabel("Minimum overlap:"),		RightLayout.NONE);
		rl.add(tf_minOverlap,						RightLayout.STRETCH_X | RightLayout.BESIDE);
		rl.add(new JLabel("base pairs"),			RightLayout.BESIDE);
		tf_minOverlap.setText("" + Sequence.getMinOverlap());
		
		Choice ch_linkage = new Choice();
		ch_linkage.add("Single linkage");
		ch_linkage.add("Average linkage");
		ch_linkage.add("Maximum linkage");
		rl.add(new JLabel("Linkage method"),		RightLayout.NEXTLINE);
		rl.add(ch_linkage,							RightLayout.BESIDE | RightLayout.FILL_2);
		
		tf_startThreshold = new JTextField();
		tf_startThreshold.setHorizontalAlignment(JTextField.RIGHT);
		rl.add(new JLabel("Cluster at:"),			RightLayout.NEXTLINE);
		rl.add(tf_startThreshold,					RightLayout.BESIDE);
		rl.add(new JLabel("%"),						RightLayout.BESIDE);
		tf_startThreshold.setText("0.05");
		
		tf_endThreshold = new JTextField();
		tf_endThreshold.setHorizontalAlignment(JTextField.RIGHT);
		rl.add(new JLabel("Cluster until:"),		RightLayout.NEXTLINE);
		rl.add(tf_endThreshold,						RightLayout.BESIDE);
		rl.add(new JLabel("%"),						RightLayout.BESIDE);
		tf_endThreshold.setText("0.50");
		
		rl.add(new JLabel("File information:"),	RightLayout.NEXTLINE);
		rl.add(tb_fileinfo,						RightLayout.BESIDE | RightLayout.FILL_2);
		
		p_side.setLayout(new BorderLayout());
		p_side.add(p_settings, BorderLayout.NORTH);
		
		// Add the clusters table to the side panel.
		p_side.add(new JScrollPane(tb_clusters));
		
		/*
		 * BOTTOM PANEL (p_bottom)
		 * displays information on current processing.
		 */
		JPanel p_bottom = new JPanel();
		p_bottom.setLayout(new BorderLayout());
		p_bottom.setBorder(new SoftBevelBorder(SoftBevelBorder.LOWERED));
		add(p_bottom, BorderLayout.SOUTH);
		
		// Add a status bar.
		tf_status.setEditable(false);
		p_bottom.add(tf_status);
		
		// All done!
		pack();
		
		setStatus("Ready for processing!");
	}
	
	/**
	 * Change the file currently being loaded.
	 * 
	 * @param file		The file to change this to.
	 */
	private void changeFile(File file) {
		// This time, we don't have to save up the last file!
		
		// Load the new file up.
		try {
			sequenceList = SequenceList.readFile(file, ProgressDialog.create(
				this, 
				"Loading file '" + file + "' ...", 
				"The file '" + file + "' is being loaded. We apologize for the inconvenience!"
			));
			
			SpeciesDetails sd = sequenceList.getSpeciesDetails(ProgressDialog.create(
				this, 
				"Compiling species information ...", 
				"Identifying and counting species information in the dataset we loaded. " +
				"We apologize for the inconvenience!"
			));
			setStatus("Loaded '" + 
				file.getName() + "': " + 
				sequenceList.count() + " sequences across " + 
				sd.getSpeciesCount() + " species."
			);
			
			DefaultTableModel dtm = new DefaultTableModel();
			tb_fileinfo.setModel(dtm);
			dtm.addColumn(
				"File information",
				new String[] {
					"File name",
					"File path",
					"Sequences",
					"Species",
					"Sequences with a valid conspecific",
					"Sequences without a valid conspecific",
					"Sequences without a species name"
				}
			);
			dtm.addColumn(
				"(as loaded)",
				new String[] {
					file.getName(),
					file.getAbsolutePath(),
					sequenceList.count() + " sequences",
					sd.getSpeciesCount() + " species",
					sd.getSequencesWithValidConspecificsCount() + " sequences",
					(sequenceList.count() - sd.getSequencesWithValidConspecificsCount()) + " sequences",
					sd.getSequencesWithoutASpeciesNameCount() + " sequences"
				}
			);
		} catch (SequenceListException ex) {
			new JOptionPane("Could not load file '" + file + "': " + ex.getMessage(), JOptionPane.ERROR_MESSAGE)
				.setVisible(true);
			return;
		} catch (DelayAbortedException ex) {
			return;
		}
		
		currentFile = file;
		tf_filename.setText(currentFile.getName());
		
		recluster();
	}

	/**
	 * Set the status message and the progress bar.
	 * 
	 * @param message		Set the status message to display.
	 * @param complete		Has the current task completed?
	 */
	private void setStatus(String message) {
		tf_status.setText(message);
	}

	private void recluster() {
		// Can't recluster without sequences!
		if(sequenceList == null) {
			JOptionPane.showMessageDialog(this, "No file has been loaded! Please load a file and try clustering again.");
			return;
		}

		// Recluster.
		ClusterJob job = new ClusterJob(
			sequenceList, 
			new Linkages.SingleLinkage(), 
			Double.parseDouble(tf_startThreshold.getText())/100
		);
		try {
			job.execute(ProgressDialog.create(
				this, 
				"(Re)clustering datasets ...", 
				"Your dataset is being divided into clusters at a " + (job.getThreshold() * 100) + "% threshold. We apologize for the inconvenience!"
			));
		} catch (DelayAbortedException ex) {
			return;
		}
		
		// Done!
		Vector clusterIndex =		new Vector();
		Vector clusterNames =		new Vector();
		Vector clusterSequenceCount = new Vector();
		Vector clusterDistances =	new Vector();
		int x = 0;
		for(Cluster c: job.getClusters()) {
			clusterIndex.add	(++x);
			clusterNames.add	(c.toString());
			clusterSequenceCount.add(c.count());
			clusterDistances.add(c.getDistances());
		}
		
		DefaultTableModel dtm = new DefaultTableModel();
		tb_clusters.setModel(dtm);
		
		dtm.addColumn("#",			clusterIndex);
		dtm.addColumn("Name",		clusterNames);
		dtm.addColumn("Sequences",	clusterSequenceCount);
		dtm.addColumn("Distances",	clusterDistances);
	}
}