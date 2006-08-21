/**
 * Allows you to "combine" datasets (only by "intersection", for now),
 * resulting in two sequence files.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/

package com.ggvaidya.TaxonDNA.Modules;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class CombineDatasets extends Panel implements UIExtension, Runnable, ActionListener {
	private static final long serialVersionUID = 7355315395770567772L;
	private TaxonDNA	taxonDNA = null;
	private SequenceList	list = null;

	// Actual data. Am *hoping* this is going to be really small, since
	// it's all pointers. But who knows? We shall see.
	// 
	private SequenceList	seqlistThis 	= null;
	private SequenceList	seqlistThat 	= null;
	
	// UI	
	private Panel		panel_selectFile = new Panel();
	private Label		label_selectFile = new Label("1. Select the file to combine the current file with:");
	private TextField	text_Filename 	= new TextField();
	private Button		btn_Browse 	= new Button("Browse");

	private Panel		panel_options 	= new Panel();
	private Label		label_options 	= new Label("2. Select how I should combine these files together, and hit the 'Combine these sequences' button");
	private Choice		choice_howToCombine = new Choice();
	private Button		btn_Combine	= new Button("Combine these sequences");

	private Panel		panel_results 	= new Panel();
	private TextField	text_This	= new TextField();
	private TextField	text_That	= new TextField();
	private TextField	text_Replicates	= new TextField("100");
	
	private java.awt.List	list_This	= new java.awt.List(50);
	private java.awt.List	list_Species 	= new java.awt.List(50);
	private java.awt.List	list_That	= new java.awt.List(50);
	
	private Button		btn_exportThis  = new Button("Export 'N' replicates to a directory");
	private Button		btn_exportThat	= new Button("Export 'N' replicates to a directory");
	private Button		btn_copyList	= new Button("Copy species list to clipboard");
	
	// Code begins here.
	public CombineDatasets(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
	
		// heavy duty AWT UI code
		// watch out below!
		
		// general settings
		setLayout(new BorderLayout(0, 10));	

		// panel: selectFile
		panel_selectFile.setLayout(new BorderLayout()); 
		panel_selectFile.add(label_selectFile, BorderLayout.NORTH);
		panel_selectFile.add(text_Filename);
		btn_Browse.addActionListener(this);
		panel_selectFile.add(btn_Browse, BorderLayout.EAST);

		add(panel_selectFile, BorderLayout.NORTH);

		// panel: options. 
		// for simplicity's sake, options actually goes into
		// selectFile's SOUTH. hope that's okay with y'all. 
		//
		panel_options.setLayout(new BorderLayout());
		panel_options.add(label_options, BorderLayout.NORTH);

		choice_howToCombine.add("Combine by intersection");
		panel_options.add(choice_howToCombine, BorderLayout.WEST);

		{	// set up a little panel with a label and a textfield for the "no of replicates"
			Panel p = new Panel();
			p.add(new Label("No of replicates: N = "));
			p.add(text_Replicates);
			panel_options.add(p, BorderLayout.EAST);
		}

		btn_Combine.addActionListener(this);
		panel_options.add(btn_Combine);

		panel_selectFile.add(panel_options, BorderLayout.SOUTH);

		// panel: results
		panel_results.setLayout(new BorderLayout()); 

		{	// left bar: "this"!
			Panel pLeft = new Panel();
			pLeft.setLayout(new BorderLayout());

			pLeft.add(text_This, BorderLayout.NORTH);
			pLeft.add(list_This);
			pLeft.add(btn_exportThis, BorderLayout.SOUTH);

			panel_results.add(pLeft, BorderLayout.EAST);
		}

		{	// center bar: "combination"
			Panel p = new Panel();
			p.setLayout(new BorderLayout());

			TextField tf = new TextField("Combined Species Summary");
			tf.setEditable(false);
			p.add(tf, BorderLayout.NORTH);
			p.add(list_Species);
			
			btn_copyList.addActionListener(this);
			p.add(btn_copyList, BorderLayout.SOUTH);

			panel_results.add(p);		
		}

		{	// right bar: "that"!
			Panel pRight = new Panel();
			pRight.setLayout(new BorderLayout());

			pRight.add(text_That, BorderLayout.NORTH);
			pRight.add(list_That);
			pRight.add(btn_exportThat, BorderLayout.SOUTH);

			panel_results.add(pRight, BorderLayout.WEST);
		}
		
		add(panel_results);

		// go!
		validate();
	}

	public void itemStateChanged(ItemEvent e) {
	}

	public void actionPerformed(ActionEvent evt) {
		// go! go! go! go! (i.e. combine the files)
		if(evt.getSource().equals(btn_Combine)) {
			new Thread(this, "CombineDatasets").start();
			return;	
		}

		if(evt.getSource().equals(btn_Browse)) {
			FileDialog fd = new FileDialog(
					taxonDNA.getFrame(),
					"Please select the file to combine this file with ...",
					FileDialog.LOAD
					);
			
			// goooooooooo ... modal!
			fd.setVisible(true);

			String tmp = fd.getFile();
			if(fd.getDirectory() != null)
				tmp = fd.getDirectory() + fd.getFile();

			text_Filename.setText(tmp);
			return;
		}
		
		// copy the sequence list 
		if(evt.getSource().equals(btn_Combine) && list != null){	
			// don't copy nothing if we don't have a valid 'list'

			// TODO: Insert code to make things happen HERE.
			
			StringBuffer buffer = new StringBuffer();
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(buffer.toString());
					
				clip.setContents(selection, selection);

				btn_copyList.setLabel("Copy species list to clipboard");
				validate();
			} catch(IllegalStateException e) {
				btn_copyList.setLabel("Failed! Try copying species list again?");
				validate();
			}
		}

		// TODO: exports
	}
	
	public void dataChanged()	{
		list = taxonDNA.lockSequenceList();
		if(list == null) {
			list_Species.removeAll();	
			seqlistThis = null;
			seqlistThat = null;
		}
		taxonDNA.unlockSequenceList();
	}

	private void cleanupRun(ProgressDialog pd) {
		// this will ONLY work if we're in 'run()' somewhere
		// I wish Java had goto's. I do. I really do.
		pd.end();
		
		if(seqlistThat != null) {
			seqlistThat.unlock();
			seqlistThat = null;
		}

	       	if(seqlistThis != null)
			taxonDNA.unlockSequenceList();
	}

	public void run() {
		int replicates = 100;
		
		list = taxonDNA.lockSequenceList();
		if(list == null) {
			taxonDNA.unlockSequenceList();
			return;
		}
		
		String filename = text_Filename.getText();
		if(filename == null)
			filename = new String();		// we'll handle it later
		
		ProgressDialog pd = new ProgressDialog(
				taxonDNA.getFrame(),
				"Please wait, combining datasets ...",
				"Please wait, I'm combining the present dataset with '" + filename + "'. This shouldn't take too long. Sorry if it does anyway.",
				0
			);
		pd.begin();
		
		seqlistThis = list;
		seqlistThat = null;

		// 1. check that 'filename' is not empty, the file exists, and open it.
		// 1.1. Did the user specify a filename? 
		if(filename.equals("")) {
			new MessageBox(taxonDNA.getFrame(),
				"You didn't give me a filename!",
				"You need to specify a filename in section '1' of this form. Please specify one now, or use the 'Browse' button to find the file you'd like to combine with this one."
				).go();
			cleanupRun(pd);
		}

		// 1.2. Can we open the file?
		File fileOther = new File(filename);
		if(!fileOther.exists() || !fileOther.canRead()) {
			new MessageBox(taxonDNA.getFrame(),
				"You didn't give me a filename!",
				"You need to specify a filename in section '1' of this form. Please specify one now, or use the 'Browse' button to find the file you'd like to combine with this one."
				).go();
			cleanupRun(pd);
		}

		// 2. Open 'fileOther', straight into a SequenceList (call it, oh, I don't know, 'seqlistThat')
		try { 
			seqlistThat = SequenceList.readFile(fileOther, null); 
		} catch(SequenceListException e) {
			// report to use, fall over backwards, and die.	
			new MessageBox(
				taxonDNA.getFrame(),
				"Error reading file: " + filename,
				e.toString()
				).go();
			cleanupRun(pd); 
		} catch(DelayAbortedException e) {
			// stop! it's over! OVER!
			cleanupRun(pd);	
		}

		// 3. How many replicates?
		replicates = new Integer(text_Replicates.getText()).intValue(); 
		if(replicates < 0) {
			replicates =	100;
		}

		// 	Potential user interface inconstituency: we 'guess' 100 if
		// 	N is not set. There isn't anything the user can do about this
		// 	right now without fixing everything. But the only solution I
		// 	can think up right now is to pop up a MessageBox, but that
		// 	feels suspiciously like overkill. 		
		
		// 4. Figure out the species structure of both seqlistThis and seqlistThat
		// THIS IS WHERE I COULD REALLY DO WITH A KICKASS CVS BEHIND ME!!!
		
		// it's OVER. OVER. roll over and play dead.
		cleanupRun(pd);
	}

	public String getShortName() {		return "Combine Datasets"; 	}
	public String getDescription() {	return "Combines datasets together."; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
