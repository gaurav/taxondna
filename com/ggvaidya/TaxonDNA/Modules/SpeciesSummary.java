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
	private Button		btn_export_multiple = new Button("Export sequences with species having more than one sequence");
	private Button		btn_Copy = new Button("Copy species list");
	
	private Hashtable	species	=		null;			// all species names, mapped to Integers
	private int		lastIndex = 		-1;			// the last used index in species
	private int[]		sequences;					// the number of sequences for each species
	private int[]		invalid_conspecifics;
	private int[]		valid_conspecifics;
	private StringBuffer[]	gi_list;

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
	}
	
	/**
	 * Data got changed. We just reset everything and wait.
	 */
	public void dataChanged() {
		SequenceList list = taxonDNA.lockSequenceList();
		if(list == null) {
			text_main.setText("");
			list_species.removeAll();
		}	
		taxonDNA.unlockSequenceList();
	}

	/**
	 * Data processing and calculations happen in here.
	 */
	public void run() {
		SequenceList list = taxonDNA.lockSequenceList();

		try {
			SpeciesDetails species = list.getSpeciesDetails(
					new ProgressDialog(taxonDNA.getFrame(), "Please wait, calculating species information ...", "Species summary information is being calculated. Sorry for the wait.", 0)
				);

			// now we use information from 'info' to populate stuff up.
			//
			StringBuffer str = new StringBuffer();

			str.append("Number of sequences: " + list.count() + "\n");			// check
			str.append("Number of species: " + species.count() + "\n\n");			// check
			str.append("Number of sequences without a species name: " + species.getSequencesWithoutASpeciesNameCount()+ "\n\n");
												// check

			str.append("Number of sequences shorter than " + Sequence.getMinOverlap() + " base pairs: " + species.getSequencesInvalidCount() + "\n");	// check
			str.append("Number of species with valid conspecifics: " + species.getValidSpeciesCount() + "\n");										 
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
			}



			text_main.setText(str.toString());
		} catch(DelayAbortedException e) {
			// we could care less	
		}

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
