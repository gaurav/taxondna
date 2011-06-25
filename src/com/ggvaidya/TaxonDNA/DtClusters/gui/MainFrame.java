
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

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import javax.swing.*;
import javax.swing.border.*;

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
		tf_filename.setFont(new Font("serif", Font.PLAIN, 36));
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
		
		/*
		 * MAIN PANEL (p_main)
		 * displays information on the current dataset.
		 */
		JPanel p_main = new JPanel();
		add(p_main);
		
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
		} catch (SequenceListException ex) {
			new JOptionPane("Could not load file '" + file + "': " + ex.getMessage(), JOptionPane.ERROR_MESSAGE)
				.setVisible(true);
			return;
		} catch (DelayAbortedException ex) {
			return;
		}
		
		currentFile = file;
		tf_filename.setText(currentFile.getName());
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
}