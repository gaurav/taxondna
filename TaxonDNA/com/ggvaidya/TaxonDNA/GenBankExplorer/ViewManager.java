/**
 * The View Manager translates what's in the data model (GenBankFile) into visible and 
 * processable information on the screen. Most of the really good magic happens in 
 * GenBankFile, but a lot of the grunt stuff happens here. That's a good thing. Really!
 *
 */

/*
 *
 *  GenBankExplorer 
 *  Copyright (C) 2007 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.GenBankExplorer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;
import java.awt.dnd.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.text.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class ViewManager {
	private GenBankExplorer explorer = 	null;			// the GenBankExplorer object

	// public information
	// DisplayModes
	public static final int	DM_LOCI =	1;
	public static final int	DM_FEATURES =	2;

	// Internal information
	private DisplayMode	currentDisplayMode =	null;

	// UI objects
	private JPanel		panel =		null;			// the 'view' itself
	private JTree		tree =		new JTree();		// the tree
	private JTextArea	ta_file =	new JTextArea();	// the text area for file information
	private JTextArea	ta_selected =	new JTextArea();	// the text area for selection information

	// Data objects
	private GenBankFile	genBankFile =	null;			// the currently loaded file

	/**
	 * Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet)
	 * and 
	 */
	public ViewManager(GenBankExplorer explorer) {
		// set up the GenBankExplorer
		this.explorer = explorer;

		initDisplayModes();
		switchDisplayMode(DM_LOCI);

		createUI();
	}

	/**
	 * Create the UI we will use for interacting with the user.
	 */
	public void createUI() {
		panel = new JPanel();
		panel.setLayout(new BorderLayout());

		// okay, boys
		// lessgo!
		// 
		// we'll 'create' UI objects first, then create the intricate set of split panes
		// which lay them out.
		//
		ta_file.setEditable(false);
		ta_selected.setEditable(false);

		// layout time!
		JSplitPane p_textareas = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(ta_file), new JScrollPane(ta_selected));
		p_textareas.setResizeWeight(0.5);	// half way (by default)
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), p_textareas);
		split.setResizeWeight(0.3);		// bit more right by default

		split.setPreferredSize(new Dimension(400, 500));

		// err ... I don't think this is the best way to do this
		// but it's certainly the quickest, so.
		new DropTarget(panel, explorer).setActive(true);
		new DropTarget(tree, explorer).setActive(true);
		new DropTarget(ta_file, explorer).setActive(true);
		new DropTarget(ta_selected, explorer).setActive(true);

		panel.add(split);
	}

	/**
	 * Clear the currently loaded file.
	 */
	public void clear() {
		if(genBankFile != null) {
			genBankFile = null;
			updateTree();
		}
	}

	/**
	 * Loads the new file, after clearing the previous one.
	 */
	public void loadFile(File f) {
		clear();

		try {
			ProgressDialog pd = new ProgressDialog(
					explorer.getFrame(),
					"Please wait, loading file ...",
					"Loading GenBank file " + f.getAbsolutePath() + ". Sorry for the delay!");
			genBankFile = new GenBankFile(f, pd);
		} catch(IOException e) {
			displayIOExceptionWhileWriting(e, f);
			return;
		} catch(FormatException e) {
			displayException("Error while reading file '" + f + "'", "The file '" + f + "' could be read as a GenBank file. Are you sure it is a properly formatted GenBank file?\nThe following error occured while trying to read this file: " + e.getMessage());
			return;
		} catch(DelayAbortedException e) {
			return;
		}

		// update the entire tree
		updateTree();
	}

	/**
	 * Returns the panel to anyone interested.
	 */
	public JPanel getPanel() {
		return panel;
	}

	public GenBankFile getGenBankFile() {
		return genBankFile;
	}

// 	ERROR HANDLING AND DISPLAY CODE
//
	public void displayIOExceptionWhileWriting(IOException e, File f) {
		new MessageBox(
			explorer.getFrame(),
			"Error while writing file '" + f + "'!",
			"The following error was encountered while writing to file " + f + ": " + e.getMessage() + "\n\nPlease ensure that you have the permissions to write to this file, that the disk is not full, and that the file is not write-protected.",
			MessageBox.MB_ERROR).go();
	}

	public void displayIOExceptionWhileReading(IOException e, File f) {
		new MessageBox(
			explorer.getFrame(),
			"Error while reading file '" + f + "'!",
			"The following error was encountered while trying to read from file " + f + ": " + e.getMessage() + "\n\nPlease ensure that the file exists, and that you have the permissions to read from it.",
			MessageBox.MB_ERROR).go();
	}

	public void displayException(String title, String message) {
		new MessageBox(
			explorer.getFrame(),
			title,
			message,
			MessageBox.MB_ERROR).go();
	}

// UI GET/SETs AND SO ON
//
	public JTree getTree() {
	       return tree;
	}	       

	public void setFileText(String text) {
		Caret c = ta_file.getCaret();
		ta_file.setCaret(null);
		ta_file.setText(text);
		ta_file.setCaret(c);
	}

	public void setSelectionText(String text) {
		// Thank you, Sun bug #4227520!
		Caret c = ta_selected.getCaret();
		ta_selected.setCaret(null);
		ta_selected.setText(text);
		ta_selected.setCaret(c);
	}

// 	DISPLAY MODE SWITCHING/HANDLING CODE
//
	private Vector	vec_displayModes = new Vector();

	public void initDisplayModes() {
		vec_displayModes.add(new LociDisplayMode(this));
		vec_displayModes.add(new FeaturesDisplayMode(this));		
	}

	public void switchDisplayMode(int mode) {
		if(currentDisplayMode != null)
			currentDisplayMode.deactivateMode();

		switch(mode) {
			case DM_LOCI:
				currentDisplayMode = (DisplayMode) vec_displayModes.get(0); 
				break;

			case DM_FEATURES:
				currentDisplayMode = (DisplayMode) vec_displayModes.get(1);
				break;
		}

		currentDisplayMode.activateMode();
		updateTree();
	}

	public void updateTree() {
		currentDisplayMode.setGenBankFile(genBankFile);
		currentDisplayMode.updateTree();
	}

	public void updateNode(TreePath path) {
		currentDisplayMode.setGenBankFile(genBankFile);
		currentDisplayMode.updateNode(path);
	}

	public void updateFileInfo() {
		// the file information changed (more sequences, file name change, etc.)
		currentDisplayMode.setGenBankFile(genBankFile);
	}
}
