/**
 * EditDataset.java
 * Lets the user 'create a dataset' for further processing.
 * Basic features:
 * 	- import sequences from AlignmentHelper (needs to fix up sequence names)
 * Wishlist:
 * 	- check species information, verify that there are no duplicates, no spp, etc.  
 *
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
import java.util.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class EditDataset extends Panel implements UIExtension, ActionListener, ItemListener {	
	private static final long serialVersionUID = -3454757045376818292L;
	
	private TaxonDNA	taxonDNA;
	private SequenceList	set = null;

	// The edit area 
	java.awt.List	list_sequences = new java.awt.List(30, false); 
	Button		btn_InsertSpeciesInfo = new Button("Merge species information from another file");

	/**
	 * No, no commands to add, thank you very much.
	 *
	 * TODO: add a 'merge species information from another file ...' into this? 
	 */
	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}
 
	public EditDataset(TaxonDNA view) {
		super();

		taxonDNA = view;
		
		// create the panel
		setLayout(new BorderLayout());

		list_sequences.addItemListener(this);
		list_sequences.setFont(new Font("Monospaced", 12, Font.PLAIN));
		add(list_sequences);
		
		add(new Label("Edit this dataset"), BorderLayout.NORTH);

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_InsertSpeciesInfo.addActionListener(this);
		buttons.add(btn_InsertSpeciesInfo);
		
		add(buttons, BorderLayout.SOUTH);
	}

	/* 
	 * If the data changed, we should update. At any rate, this will keep us in sync
	 * with everybody else.
	 */
	public void dataChanged() {
		set = taxonDNA.lockSequenceList();

		if(set == null)
			return;

		list_sequences.removeAll();
		
		Iterator i = set.iterator();
		int max_no_of_digits = (int)	(Math.log(set.count())/Math.log(10) + 2); // 2 = 1 for correct no + 1 for the '.' after it
		int index = 1;
		list_sequences.add(
				pad("", max_no_of_digits) + " " + 
				pad("Display Name", 40) + " " +
				pad("Length", 9) + " " +
				pad("Species Name", 40) + " " + 
				pad("Full Name", 80)
		);
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			list_sequences.add(
					pad(Integer.toString(index) + ".", max_no_of_digits) + " " +
					pad(seq.getDisplayName(), 40) + " " + 
					pad(Integer.toString(seq.getActualLength()) + " bp", 9) + " " + 
					pad(seq.getSpeciesName(), 40) + " " + 
					// TODO: insert the comment field here
					pad(seq.getFullName(), 80)
			);

			index++;

			//
			// From herein on:
			// 	create a button, and implement the 'merge names from another file ...' operation in here
			// 	:)
			//
		}
		taxonDNA.unlockSequenceList();
	}

	/**
	 * This function is called when we need to merge species information from another file.
	 */
	public void insertSpeciesInfo() {
		FileDialog fd = new FileDialog(taxonDNA.getFrame(), "Please select the file to obtain the species information from", FileDialog.LOAD);

		fd.setVisible(true);

		if(fd.getFile() != null) {
			String filename = "";
			if(fd.getDirectory() != null) {
				filename = fd.getDirectory();
			}
			filename += fd.getFile();
			SequenceList other = null;
			try {
				other = SequenceList.readFile(new File(filename), null);
			} catch(SequenceListException e) {
				// something is wrong
				MessageBox mb = new MessageBox(taxonDNA.getFrame(), "Couldn't read '" + filename + "'", e.toString());
				mb.go();
				return;
			} catch(DelayAbortedException e) {
				return;
			}
				
			ProgressDialog pd = new ProgressDialog(taxonDNA.getFrame(), "Please wait, inserting information ...", "I am inserting species information from '" + filename + "' into the currently loaded file. Please note that DATA IN THE CURRENT FILE WILL BE OVERWRITTEN if you save your file. Only the species information will be used; all sequence data in this file can be deleted without any problems.");
			// TODO: magic binding code. dark, evil magic. be warned.
			set = taxonDNA.lockSequenceList();

			pd.begin();	

			Iterator i = set.iterator();
			int count = 0;
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				try {
					pd.delay(count, set.count());
				} catch(DelayAbortedException e) {
					taxonDNA.unlockSequenceList();
					return;
				}
				count++;

				/*
				 * cos I'm hacking this *overbloodynight*, I'm using a linear search,
				 * better searches ought to be used. Feel free to jump ahead if you have
				 * any bright ideas...
				 */
				Iterator search = other.iterator();
				while(search.hasNext()) {
					Sequence seq2 = (Sequence) search.next();
					if(!seq2.getGI().equals("") && seq2.getGI().equals(seq.getGI())) {
						seq.changeName(seq2.getFullName());
						break;
					}
				}
			}


			// clear the buffer and unlock
			// Sequence.resetAllDistances(); -- since we use UUIDs now, don't have to do this
			taxonDNA.unlockSequenceList(set);
			
			pd.end();
		}
	}

	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_InsertSpeciesInfo)) {
			insertSpeciesInfo();
		}
	}		

	/* Pad a string to a size */
	private String pad(String x, int max) {
		StringBuffer buff = new StringBuffer();
		
		if(x.length() <= max) {
			for(int c = 0; c < (max - x.length()); c++)
				buff.append(' ');
			
			return new String(x + buff);
		} else {
			if(x.length() <= 3) {
				return "...";
			} else 
				return new String(x.substring(0, max - 3) + "...");
		}
	}

	/*
	 * ItemListener
	 */
	public void itemStateChanged(ItemEvent e) {
		// nIndex is zero-based
		int nIndex = ( (Integer) e.getItem() ).intValue();
		// TODO
	}
	
	// UIExtension stuff
	public String getShortName() { return "Edit this dataset"; }
	
	public String getDescription() { return "Allows you to edit this dataset"; }
	
	public Frame getFrame() { return null; }
	
}
