/**
 * Summarise species information. 
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

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class SpeciesSummary extends Panel implements UIExtension, Runnable, ActionListener {
	private TaxonDNA	taxonDNA = null;
	private TextArea	text_main = new TextArea();

	private java.awt.List	list_species = new java.awt.List();
	private Button		btn_Calculate = new Button("Calculate now!");
	private Button		btn_Delete = new Button("Delete all sequences of the selected species");
	private Button		btn_export_multiple = new Button("Export sequences with species having more than one sequence");
	private Button		btn_Copy = new Button("Copy species list");
	
	private Vector		vec_Species =		null;

	private boolean		flag_weChangedTheData = false;

	/**
	 * Constructor. Needs one taxonDNA object.
	 */
	public SpeciesSummary(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
	
		// layouting
		setLayout(new BorderLayout());	
	       	
		text_main.setEditable(false);
		add(text_main, BorderLayout.NORTH);

		add(list_species);

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.LEFT));

		btn_Calculate.addActionListener(this);
		buttons.add(btn_Calculate);

		btn_Delete.addActionListener(this);
		buttons.add(btn_Delete);

		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);

		btn_export_multiple.addActionListener(this);
		buttons.add(btn_export_multiple);
		
		add(buttons, BorderLayout.SOUTH);
	}

	/**
	 * actionListener. We're listening to events as they come in.
	 */
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(btn_Calculate)) {
			new Thread(this, "SpeciesSummary").start();
			return;
		}
		if(e.getSource().equals(btn_export_multiple)) {
			SequenceList list = taxonDNA.lockSequenceList();
				
			if(list == null)
				return;

			SequenceList result = new SequenceList();
			
			Iterator i = list.iterator();
			Hashtable species = new Hashtable();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();
	
				Integer integer = (Integer) species.get(seq.getSpeciesName());
				if(integer == null) {
					integer = new Integer(1);
					species.put(seq.getSpeciesName(), integer);
				} else {
					species.put(seq.getSpeciesName(), new Integer(integer.intValue() + 1));
				}	
			}

			i = list.iterator();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				Integer integ = (Integer) species.get(seq.getSpeciesName());

				if(integ.intValue() > 1)
				{
					try {
						result.add(seq);
					} catch(Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			FileDialog fd = new FileDialog(taxonDNA.getFrame(), "Export sequences to Fasta file ...", FileDialog.SAVE);
			fd.setVisible(true);

			File file = null;
			if(fd.getFile() != null) {
				if(fd.getDirectory() != null)
					file = new File(fd.getDirectory() + fd.getFile());
				else
					file = new File(fd.getFile());

				try {
					com.ggvaidya.TaxonDNA.DNA.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile();
					ff.writeFile(file, new SequenceList(result), null);
				} catch(Exception ex) {
					// HACK HACK HACK
					// Will fix this when I have more time
					ex.printStackTrace();
				}
			}

			taxonDNA.unlockSequenceList();
		}

		if(e.getSource().equals(btn_Copy)) {
			StringBuffer text_use = new StringBuffer();

			text_use.append(text_main.getText() + "\n\n");

			if(list_species.getItemCount() != 0) {
				for(int x = 0; x < list_species.getItemCount(); x++) {
					text_use.append(list_species.getItem(x) + "\n");
				}
			}
			
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_use.toString());
				
				clip.setContents(selection, selection);
			
				btn_Copy.setLabel("Copy species list");
			} catch(IllegalStateException ex) {
				btn_Copy.setLabel("Oops, try again?");
			}
		}

		if(e.getSource().equals(btn_Delete)) {
			if(vec_Species == null)
				return;

			int selected = list_species.getSelectedIndex();
			if(selected == -1)
				return;

			String sp_name = (String) vec_Species.get(selected);
			SequenceList list = taxonDNA.lockSequenceList();
			SpeciesDetail det = null;
			try {
				det = list.getSpeciesDetails(null).getSpeciesDetailsByName(sp_name);
			} catch(DelayAbortedException ex) {
				// wtf
				return;
			}
			taxonDNA.unlockSequenceList();

			if(det == null) {
				MessageBox mb = new MessageBox(
						taxonDNA.getFrame(),
						"This species does not exist!",
						"I cannot find any sequences with the species '" + sp_name + "'! Are you sure you have't already removed or renamed sequences?\n\nTry recalculating the Species Summary. If this species still appears, there might be a programming error in this program.");
				mb.go();
				
				return;
			}
			int count = det.getSequencesCount();

			MessageBox mb = new MessageBox(
				taxonDNA.getFrame(),
				"Are you sure?",
				"Are you sure you want to delete all " + count + " sequences belonging to the species '" + sp_name + "'?\n\nThis cannot be undone!",
				MessageBox.MB_YESNO);
			if(mb.showMessageBox() == MessageBox.MB_YES) {
				list = taxonDNA.lockSequenceList();
				Iterator i = list.conspecificIterator(sp_name);
				int x = 0;
				while(i.hasNext()) {
					i.next();
					i.remove();
					x++;
				}
				System.err.flush();

				vec_Species.remove(sp_name);
				list_species.remove(selected);

				flag_weChangedTheData = true;
				taxonDNA.sequencesChanged();
				taxonDNA.unlockSequenceList();

				mb = new MessageBox(
						taxonDNA.getFrame(),
						"Sequences deleted!",
						x + " sequences were successfully deleted."
						);
				mb.go();
			}
		}
	}
	
	/**
	 * Data got changed. We just reset everything and wait.
	 */
	public void dataChanged() {
		if(flag_weChangedTheData) {
			flag_weChangedTheData = false;
			return;
		}

		text_main.setText("");
		list_species.removeAll();
		vec_Species = null;
	}

	/**
	 * Data processing and calculations happen in here.
	 */
	public void run() {
		SequenceList list = taxonDNA.lockSequenceList();
		SpeciesDetails species = null;

		try {
			species = list.getSpeciesDetails(
					new ProgressDialog(taxonDNA.getFrame(), "Please wait, calculating species information ...", "Species summary information is being calculated. Sorry for the wait.", 0)
				);
		} catch(DelayAbortedException e) {
			taxonDNA.unlockSequenceList();
			return;
		}

		vec_Species = new Vector();		

		// now we use information from 'info' to populate stuff up.
		//
		StringBuffer str = new StringBuffer();

		str.append("Number of sequences: " + list.count() + "\n");			// check
		str.append("Number of species: " + species.count() + "\n\n");			// check
		str.append("Number of sequences without a species name: " + species.getSequencesWithoutASpeciesNameCount()+ "\n\n");
												// check

		str.append("Number of sequences shorter than " + Sequence.getMinOverlap() + " base pairs: " + species.getSequencesInvalidCount() + "\n");	// check

		str.append("Number of species with valid conspecifics: " + species.getValidSpeciesCount() + " (" + com.ggvaidya.TaxonDNA.DNA.Settings.percentage(species.getValidSpeciesCount(), species.count()) + "% of all sequences)\n");										 
		// set up list_species
		//
		list_species.removeAll();

		Iterator i = species.getSpeciesNamesIterator();
		int index = 0;
		while(i.hasNext()) {
			String name = (String) i.next();
			SpeciesDetail det = species.getSpeciesDetailsByName(name);

			int count_total = det.getSequencesCount();
			int count_valid = det.getSequencesWithValidMatchesCount();
			int count_invalid = det.getSequencesWithoutValidMatchesCount();
			String gi_list = det.getIdentifiersAsString();
		
			index++;
			list_species.add(index + ". " + name + " (" + count_total + " sequences, " + count_valid + " valid, " + count_invalid + " invalid): " + gi_list);

			vec_Species.add(name);
		}

		text_main.setText(str.toString());
		taxonDNA.unlockSequenceList();
	}

	// OUR USUAL UIINTERFACE CRAP
	public String getShortName() {		return "Species Summary"; 	}
	public String getDescription() {	return "Summarises known information on species"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
