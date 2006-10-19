/**
 * A UIExtension which allows you to figure out the 'extreme' pairwise 
 * distances - the largest intra and the smallest inter.  
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2006

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


package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for clipboard
import java.io.*;		// for export

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class ExtremePairwise extends Panel implements UIExtension, ActionListener, Runnable {
	private SpeciesIdentifier	identifier;

	private Button btn_Calculate = new Button("Calculate!");

	private PairwiseDistances intra = null;	
	private PairwiseDistances inter = null;

	// Sequence View
	private TextArea	text_matches = new TextArea();

	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}

	public ExtremePairwise(SpeciesIdentifier view) {
		super();

		identifier = view;

		// let's get this UI started
		setLayout(new BorderLayout());

		// we need an array of buttons to flip between different 'sets' of data
		btn_Calculate.addActionListener(this);
		add(btn_Calculate, BorderLayout.NORTH);

		text_matches.setEditable(false);
		add(text_matches);
	}

	/* Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		inter = null;
		intra = null;

		text_matches.setText("");
	}

	public void run() {
		SequenceList list = identifier.lockSequenceList();
		PairwiseDistances current = null;

		// is there a 'list'?
		if(list == null) {
		       text_matches.setText("No sequences loaded.");
		       identifier.unlockSequenceList();
		       return;
		}

		// Tell the user we're working
		text_matches.setText("Please wait, processing data ...");

		// set up the PairwiseDistancess
		StringBuffer results = new StringBuffer();
		results.append("Sequence name\tLargest conspecific match\tDistance\tOverlap\tClosest interspecific match\tDistance\tOverlap\n"); 
		try {
			ProgressDialog pd = new ProgressDialog(
					identifier.getFrame(),
					"Please wait, calculating extreme pairwise distances ...",
					"I am calculating extreme pairwise distances. Please bear with me.");
			pd.begin();
			
			SortedSequenceList sorted = new SortedSequenceList(list);
			Iterator i = list.iterator();
			int total_sequences = list.count();
			int count = 0;
			while(i.hasNext()) {
				pd.delay(count, total_sequences);
				count++;

				Sequence seq = (Sequence) i.next();

				if(seq.getSpeciesName() == null) {
					results.append(seq.getFullName() + "\tUnable to identify species name\n");
					continue;
				}

				sorted.sortAgainst(seq, null);

				Sequence seq_largestIntra = null;
				double distance_largestIntra = -1;
				Sequence seq_smallestInter = null;
				double distance_smallestInter = -1;
				for(int x = 0; x < sorted.count(); x++) {
					Sequence seq2 = sorted.get(x);

					// ignore the nameless ones
					if(seq2.getSpeciesName() == null)
						continue;

					if(!seq.getSpeciesName().equals(seq2.getSpeciesName())) {
						// interspecific!
						if(seq_smallestInter == null) {
							if(seq2.getPairwise(seq) != -1) {
								// the first inter will also (by definition) be the SMALLEST
								seq_smallestInter = seq2;
								break;
							}
						}
					}
				}

				// we can't go until (x >= 0), since get(0) will get the query!
				for(int x = sorted.count() - 1; x >= 1; x--) {
					Sequence seq2 = sorted.get(x);

					// ignore the nameless ones
					if(seq2.getSpeciesName() == null)
						continue;

					if(seq.getSpeciesName().equals(seq2.getSpeciesName())) {
						// conspecific!	
						if(seq2.getPairwise(seq) != -1) {
							// the last valid intra by definition is the largestIntra
							seq_largestIntra = seq2;
							break;
						}
					}
				}

				results.append(seq.getDisplayName() + "\t");

				if(seq_largestIntra == null) {
					results.append("No matching conspecific sequence\tN/A\tN/A\t");
				} else {
					results.append(seq_largestIntra.getDisplayName() + "\t" + percentage(seq_largestIntra.getPairwise(seq), 1) + "\t" + seq_largestIntra.getOverlap(seq) + "\t");
//					System.err.println(seq + "\tINTRA\t" + seq_largestIntra.getDisplayName() + "\t" + percentage(seq_largestIntra.getPairwise(seq), 1) + "\t" + seq_largestIntra.getOverlap(seq) + "\n");
				}

				if(seq_smallestInter == null) {
					results.append("No matching interspecific sequence\tN/A\tN/A\t");
				} else {
					results.append(seq_smallestInter.getDisplayName() + "\t" + percentage(seq_smallestInter.getPairwise(seq), 1) + "\t" + seq_smallestInter.getOverlap(seq) + "\t");
//					System.err.println(seq + "\tINTER\t" + seq_smallestInter.getDisplayName() + "\t" + seq_smallestInter.getPairwise(seq) + "\t" + seq_smallestInter.getOverlap(seq) + "\n");
				}

				results.append('\n');
			}

			pd.end();
					
		} catch(DelayAbortedException e) {
			identifier.unlockSequenceList();
			text_matches.setText("Pairwise calculation cancelled.");
			return;
		}

		text_matches.setText(results.toString());
		identifier.unlockSequenceList();
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	private boolean identical(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.identical(x, y);
	}

	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_Calculate)) {
			new Thread(this, "ExtremePairwise").start();
			return;
		}
	}		

	// UIExtension stuff
	public String getShortName() { return "Extreme Pairwise"; }
	
	public String getDescription() { return "Determines the extreme pairwise distance - the largest intraspecific and smallest interspecific distance - for each sequence."; }
	
	public Frame getFrame() { return null; }
}
