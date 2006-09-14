/**
 * A UIExtension which allows for summary of all pairwise distances.  
 * This ought to be the architypical "basic" pairwise distance thingie. 
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


package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for clipboard
import java.io.*;		// for export

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class PairwiseSummary extends Panel implements UIExtension, ActionListener, Runnable {	
	private static final long serialVersionUID = 4986465198540567487L;
	
	private SpeciesIdentifier	seqId;
	private SequenceList	set = null;

	private PairwiseDistribution intra = null;		
	private PairwiseDistribution inter = null;
	
	private float	fivePercentCutoff = 0;

	// Sequence View
	private TextArea	text_main = new TextArea();		

	private Button		btn_Calculate = new Button("Calculate now!");
	private Button		btn_Copy = new Button("Copy to Clipboard");

	private Button		btn_dumpIntra = new Button("Export intraspecific distances");
	private Button		btn_dumpInter = new Button("Export interspecific distances");

	public float getFivePercentCutoff() {
		return fivePercentCutoff;
	}

	public PairwiseDistribution getPD_intra() {
		return intra;
	}
	
	public PairwiseDistribution getPD_inter() {
		return inter;
	}
	
	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}

	public PairwiseSummary(SpeciesIdentifier view) {
		super();

		seqId = view;
		
		// create the panel
		setLayout(new BorderLayout());

		btn_Calculate.addActionListener(this);
		add(btn_Calculate, BorderLayout.NORTH);
		
		text_main.setEditable(false);
		text_main.setFont(new Font("Monospaced", Font.PLAIN, 12));
		add(text_main);
		
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_dumpIntra.addActionListener(this);
		buttons.add(btn_dumpIntra);

		btn_dumpInter.addActionListener(this);
		buttons.add(btn_dumpInter);
		
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);

		add(buttons, BorderLayout.SOUTH);
		
	}

	/* Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		inter = null;
		intra = null;

		fivePercentCutoff = 0;

		text_main.setText("");
	}

	public void run() {
		set = seqId.lockSequenceList();

		// is there a 'set'?
		if(set == null) {
		       text_main.setText("No sequences loaded.");
		       seqId.unlockSequenceList();
		       return;
		}

		// PDs don't make sense for [0, 1] sequences
		if(set.count() < 2) {
			text_main.setText("Pairwise distributions cannot be determined for less than two sequences");
		      	seqId.unlockSequenceList();
			return;
		}
		
		// Tell the user we're working
		text_main.setText("Please wait, processing data ...");

		// set up the PairwiseDistributions
		try { 
			intra = new PairwiseDistribution(
					set, 
					PairwiseDistribution.PD_INTRA, 
					new ProgressDialog(
						seqId.getFrame(), 
						"Calculating pairwise distances", 
						"All intraspecific pairwise distances are being calculated. Sorry for the delay!", 0
					)
				);
			inter = new PairwiseDistribution(
					set, 
					PairwiseDistribution.PD_INTER, 
					new ProgressDialog(
						seqId.getFrame(), 
						"Calculating pairwise distances", 
						"All interspecific, congeneric pairwise distances are being calculated. Sorry for the delay!", 0
					)
				);
		} catch(DelayAbortedException e) {
			seqId.unlockSequenceList();
			this.set = null;
			text_main.setText("Pairwise summary cancelled.");
			return;
		}

		// a slow, hacky way of checking the number of species
		Hashtable species = new Hashtable();
		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();
			if(seq.getSpeciesName() != null)
				species.put(seq.getSpeciesName(), new Integer(1));
		}

		// and now it begins!
		StringBuffer str = new StringBuffer();

		// simple counts
		println(str, "COUNTS");
		println(str, "No of sequences:", set.count() + "\tsequences");
		println(str, "No of species:", species.keySet().size() + "\tspecies");

//		println(str, "\nPAIRWISE COMPARISIONS");
//		println(str, "No of valid comparisons with adequate overlap:", all.countValidComparisons() + "\tcomparisons");

		// percentages - probably the most important part of this whole thing
		println(str, "\nPERCENTAGES");
		float min_inter_distance = inter.getMinimumDistance();
		float max_intra_distance = intra.getMaximumDistance();
		int count_comparisons = inter.countValidComparisons() + intra.countValidComparisons();
		float overlap = Math.abs(max_intra_distance - min_inter_distance);
		
		if(inter.countValidComparisons() == 0) { 
			// If there are NO interspecific comparisons
			println(str, 
					"Total overlap:\tNo interspecific, intrageneric comparisons.\n              \tIntraspecific comparisons range from " + percentage(intra.getMinimumDistance(), 1) + "% to " + percentage(intra.getMaximumDistance(), 1) + "% (total width: " + percentage(intra.getMaximumDistance() - intra.getMinimumDistance(), 1) + "%)");

		} else {
			// If there ARE interspecific comparisons
			int within = 
				intra.getBetweenIncl(min_inter_distance, max_intra_distance) + 
				inter.getBetweenIncl(min_inter_distance, max_intra_distance);

			Vector distances_intra = intra.getDistancesBetween(0, 1);
			Vector distances_inter = inter.getDistancesBetween(0, 1);

			if(distances_intra.size() == 0) { // no distances for intra
				println(str, "Total overlap:\t No intraspecific distances present.");
				println(str, "Overlap with 5% error margs on both ends:\t No intraspecific distances present.");
			} else if(distances_inter.size() == 0) { // no distance for inter
				println(str, "Total overlap:\t No interspecific distances present.");
				println(str, "Overlap with 5% error margs on both ends:\t No interspecific distances present.");
			} else {
				int x = 0;				
			
				println(str, 
					"Total overlap:\t" + percentage(overlap, 1) + "% (from " + percentage(min_inter_distance, 1) + "% to " + percentage(max_intra_distance, 1) + "%, covering " + percentage(within, count_comparisons) + "% of all intra and interspecific but intrageneric sequences)");

				// remove the 5% smallest interspecific pairwise distances
				// 
				i = distances_inter.iterator();
				x = 0;
				while(i.hasNext()) {
					Float dist = (Float) i.next();

					if((float)x / distances_inter.size() > 0.05)
						break;

					min_inter_distance = dist.floatValue();


					x++;
				}

				// remove the 5% biggest intraspecific pairwise distances
				//
				x = 1;
				while(x < distances_intra.size()) {
					Float dist = (Float) distances_intra.get(distances_intra.size() - x);

					if((float)x / distances_intra.size() > 0.05)
						break;
					
					max_intra_distance = dist.floatValue();

					x++;
				}

				// calculate the usual suspects
				// 
				overlap = Math.abs(max_intra_distance - min_inter_distance);
	
				within = inter.getBetweenIncl(min_inter_distance, max_intra_distance) + intra.getBetweenIncl(min_inter_distance, max_intra_distance);
				
				/*
				 * 5% off each end of the OVERLAP region (which we also need to return)
				 *
				 * |----------------------| <-- overlap region
				 * |---|              |---| <-- chop this region
				 *     |--------------|     <-- we have the middle region (so a more conservative estimate)
				 *
				 */ 

				fivePercentCutoff = (float) percentage(max_intra_distance, 1);
				
				println(str, "Overlap with 5% error margins on both ends:\t " + percentage(overlap, 1) + "% (from " + percentage(min_inter_distance, 1) + "% to " + percentage(max_intra_distance, 1) + "%, covering " + percentage(within, count_comparisons) + "% of intra and interspecific but intrageneric sequences)");
				println(str, "Five percent intraspecific cutoff:\t " + fivePercentCutoff + "%");
			}
		}

		println(str, "\nDISTRIBUTION FOR ALL INTRASPECIFIC DISTANCES (FROM 0.0 TO 0.20)");
		println(str, intra.getDistributionAsString(0.0, 0.20, 0.005, PairwiseDistribution.CUMUL_BACKWARD));	

		println(str, "\nDISTRIBUTION FOR ALL INTRASPECIFIC DISTANCES");
		println(str, intra.getDistributionAsString(0.0, 1.0, 0.05, PairwiseDistribution.CUMUL_BACKWARD));	

		println(str, "\nDISTRIBUTION FOR ALL INTERSPECIFIC DISTANCES WITHIN A GENUS (FROM 0.0 to 0.20)");
		println(str, inter.getDistributionAsString(0.0, 0.20, 0.005, PairwiseDistribution.CUMUL_FORWARD));
		
		println(str, "\nDISTRIBUTION FOR ALL INTERSPECIFIC DISTANCES WITHIN A GENUS");
		println(str, inter.getDistributionAsString(0.0, 1.0, 0.05, PairwiseDistribution.CUMUL_FORWARD));

		/*
		println(str, "\nDISTRIBUTION FOR ALL CALCULATED POINTS (INTRA + INTER)");
		println(str, all.getDistributionAsString(0.0, 1.0, 0.05));
		*/
		text_main.setText(str.toString());

		seqId.unlockSequenceList();
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	/* Pad a string to a size */
	private void println(StringBuffer main, String x) {
		StringBuffer buff = new StringBuffer();
		
		if(x.length() < 30) {
			for(int c = 0; c < (30 - x.length()); c++)
				buff.append(' ');
			
			main.append(x + buff + "\n");
		} else
			main.append(x + "\n");
	}
	
	/* Pad a string to a size */
	private void println(StringBuffer main, String x, Object y) {
		StringBuffer buff = new StringBuffer();
		
		if(x.length() < 30) {
			for(int c = 0; c < (30 - x.length()); c++)
				buff.append(' ');
			
			main.append(x + buff + "\t" + y + "\n");
		} else
			main.append(x + "\t" + y + "\n");
	}

	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	/**
	 * Combines all the distances in 'pd' into
	 * a single, long, '\n' delimited string
	 * and returns the same.
	 */
	private void printAllDistances(PrintWriter pw, PairwiseDistribution pd, DelayCallback delay) throws DelayAbortedException, IOException {
		if(pw == null)
			return;
		
		if(pd == null)
			return;
		
		if(delay != null)
			delay.begin();

		Vector v = pd.getDistancesBetween(0.0, 1.0);
		Iterator i = v.iterator();

		int count = 0;
		int total = v.size();
		while(i.hasNext()) {
			Float f = (Float)i.next();
			pw.println(f);
			
			if(delay != null) {
				delay.delay(count, total);	
			}
			count++;
		}
		
		if(delay != null)
			delay.end();
	}	

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_Calculate)) {
			new Thread(this, "PairwiseSummary").start();
			return;
		}
		
		Button btn = (Button) evt.getSource();
		if(btn.equals(btn_Copy)) {
			// clipboard
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
				
				clip.setContents(selection, selection);

				selection = null;
				clip = null;
			} catch(IllegalStateException e) {
				btn_Copy.setLabel("Oops! Try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");	

		} else if(btn.equals(btn_dumpIntra) || btn.equals(btn_dumpInter)) {
			PairwiseDistribution pd = intra;	// in case of programmer stupidity
			String these = "";

			if(btn.equals(btn_dumpIntra)) {
				pd = intra;
				these = "intraspecific pairwise";
			} else if(btn.equals(btn_dumpInter)) {
				pd = inter;
				these = "interspecific, congeneric pairwise";
			}

			// Now, if only we had a File to write to ...
			File file = null;
			FileDialog dialog = new FileDialog(
					seqId.getFrame(),
					"Please specify a filename to save " + these + " distances to.",
					FileDialog.SAVE
				);

			dialog.setVisible(true);

			if(dialog.getFile() == null)
				return;
			
			if(dialog.getDirectory() != null)
				file = new File(dialog.getDirectory() + dialog.getFile());
			else
				file = new File(dialog.getFile());

			try {
				PrintWriter pr = new PrintWriter(new FileWriter(file));

				printAllDistances(
					pr,
					pd, 
					new ProgressDialog(
						seqId.getFrame(),
						"Please wait, preparing list ...",
						"I'm preparing the list of " + these + " pairwise distances. Sorry for the delay!",
						0
						)
					);

				pr.close();
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"There was an error writing to " + file,
						SpeciesIdentifier.getMessage(Messages.IOEXCEPTION_WRITING, file, e)
						);
				mb.go();
				return;
			} catch(DelayAbortedException e) {
				return;
			}

		}

	}		
	
	// UIExtension stuff
	public String getShortName() { return "Pairwise Summary"; }
	
	public String getDescription() { return "Summarises the pairwise distances present in this dataset"; }
	
	public Frame getFrame() { return null; }	
}
