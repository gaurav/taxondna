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

public class PairwiseExplorer extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {	
	private SpeciesIdentifier	identifier;

	private Button btn_Calculate = new Button("Calculate!");

	private Button btn_Intra = new Button("Intraspecific distances");
	private Button btn_Inter = new Button("Interspecific, congeneric distances");

	private PairwiseDistances intra = null;	
	private PairwiseDistances inter = null;
	private int		  mode  = 0;		// 0 == intra, 1 == inter

	private Hashtable hash_distances = null;

	private double fivePercentCutoff = 0.0;
	
	// Sequence View
	private java.awt.List	list_distances = new java.awt.List(25);
	private TextArea	text_matches = new TextArea();

	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}

	public PairwiseExplorer(SpeciesIdentifier view) {
		super();

		identifier = view;

		// let's get this UI started
		setLayout(new BorderLayout());

		// we need an array of buttons to flip between different 'sets' of data
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.LEFT));		// doesn't work; we're in BorderLayout.NORTH
										// and I'm really not up to a BL-in-BL hack.

		btn_Intra.addActionListener(this);
		buttons.add(btn_Intra);

		btn_Inter.addActionListener(this);
		buttons.add(btn_Inter);
		
		add(buttons, BorderLayout.NORTH);

		Panel main = new Panel();
		main.setLayout(new BorderLayout());

					//100.00% (99,999 sequences)
		list_distances.add(	 "88888888888888888888888888");
		list_distances.addItemListener(this);
		main.add(list_distances, BorderLayout.WEST);
		text_matches.setEditable(false);
		main.add(text_matches);

		add(main);
	}

	/* Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		inter = null;
		intra = null;

		hash_distances = null;

		fivePercentCutoff = 0;

		list_distances.removeAll();
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
		try { 

			// now: are we intra or inter? it's all in the threadname!
			if(Thread.currentThread().getName().equals("IntraPairwiseExplorer")) {
				if(intra == null) {
					intra = new PairwiseDistances(
						list, 
						PairwiseDistances.PD_INTRA, 
						ProgressDialog.create(
							identifier.getFrame(), 
							"Calculating pairwise distances", 
							"All intraspecific pairwise distances are being calculated. Sorry for the delay!", 0
						)
					);
				}
				current = intra;
			} else {
				if(inter == null) {
					inter = new PairwiseDistances(
						list, 
						PairwiseDistances.PD_INTER, 
						ProgressDialog.create(
							identifier.getFrame(), 
							"Calculating pairwise distances", 
							"All interspecific, congeneric pairwise distances are being calculated. Sorry for the delay!", 0
						)
					);
				}
				current = inter;
			}
		} catch(DelayAbortedException e) {
			identifier.unlockSequenceList();
			text_matches.setText("Pairwise summary cancelled.");
			return;
		}

		// put the distances in list_distances
		list_distances.removeAll();

		hash_distances = new Hashtable();
		
		for(int x = -5; x < 1000; x+= 5) {
			double from = (double)x / 1000 + 0.000001;
			double to = (double)(x + 5) / 1000;

			Vector distances = current.getDistancesBetween(from, to);
			
			if(distances.size() > 0) {
				String key = ((float)x / 10) + "% to " + ((float)x + 5)/10 + "% (" + distances.size() + " matches)";

				if(x + 5 == 0)
					key = "Before 0% (" + distances.size() + " matches)";

				list_distances.add(key);
				hash_distances.put(key, distances);
			}				
		}

		list_distances.add("Averages");

		text_matches.setText("Please select a distance category to examine.");

		// percentages - probably the most important part of this whole thing
		/*
		println(str, "\nPERCENTAGES");
		double min_inter_distance = inter.getMinimumDistance();
		double max_intra_distance = intra.getMaximumDistance();
		int count_comparisons = inter.countValidComparisons() + intra.countValidComparisons();
		double overlap = Math.abs(max_intra_distance - min_inter_distance);
		
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
				 *

				fivePercentCutoff = (float) percentage(max_intra_distance, 1);
				
				println(str, "Overlap with 5% error margins on both ends:\t " + percentage(overlap, 1) + "% (from " + percentage(min_inter_distance, 1) + "% to " + percentage(max_intra_distance, 1) + "%, covering " + percentage(within, count_comparisons) + "% of intra and interspecific but intrageneric sequences)");
				println(str, "Five percent intraspecific cutoff:\t " + fivePercentCutoff + "%");
			}
		}

		println(str, "\nDISTRIBUTION FOR ALL INTRASPECIFIC DISTANCES (FROM 0.0 TO 0.20)");
//		println(str, intra.getDistributionAsString(0.0, 0.20, 0.005, PairwiseDistances.CUMUL_BACKWARD));	

		println(str, "\nDISTRIBUTION FOR ALL INTRASPECIFIC DISTANCES");
//		println(str, intra.getDistributionAsString(0.0, 1.0, 0.05, PairwiseDistances.CUMUL_BACKWARD));	

		println(str, "\nDISTRIBUTION FOR ALL INTERSPECIFIC DISTANCES WITHIN A GENUS (FROM 0.0 to 0.20)");
//		println(str, inter.getDistributionAsString(0.0, 0.20, 0.005, PairwiseDistances.CUMUL_FORWARD));
		
		println(str, "\nDISTRIBUTION FOR ALL INTERSPECIFIC DISTANCES WITHIN A GENUS");
//		println(str, inter.getDistributionAsString(0.0, 1.0, 0.05, PairwiseDistances.CUMUL_FORWARD));

		/*
		println(str, "\nDISTRIBUTION FOR ALL CALCULATED POINTS (INTRA + INTER)");
		println(str, all.getDistributionAsString(0.0, 1.0, 0.05));
		text_matches.setText(str.toString());
		*/

		identifier.unlockSequenceList();
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	private boolean identical(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.identical(x, y);
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
	private void printAllDistances(PrintWriter pw, PairwiseDistances pd, DelayCallback delay) throws DelayAbortedException, IOException {
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
		if(evt.getSource().equals(btn_Intra)) {
			new Thread(this, "IntraPairwiseExplorer").start();
			mode = 0;
			return;
		}
		if(evt.getSource().equals(btn_Inter)) {
			new Thread(this, "InterPairwiseExplorer").start();
			mode = 1;
			return;
		}
	}		

	public void itemStateChanged(ItemEvent e) {
		if(e.getSource().equals(list_distances)) {
			if(hash_distances != null && e.getStateChange() == ItemEvent.SELECTED ) {
				Integer item = (Integer) e.getItem();
				String key = list_distances.getItem(item.intValue());
				StringBuffer buff = new StringBuffer();

				if(key.equals("Averages")) {
					String type = "";
					PairwiseDistances distances = null;
					if(mode == 0) {
						type = "intraspecific";
						distances = intra;
					} else {
						type = "congeneric, interspecific";
						distances = inter;
					}

					buff = new StringBuffer("The following are the average " + type + " distances for the following sequences:\n");
					buff.append("Sequence name\t\tAverage distance\n");
					Vector v = new Vector();
					v.addAll(distances.getAveragedSequences());
					Collections.sort(v);
					Iterator i = v.iterator();
					while(i.hasNext()) {
						String spName = (String) i.next();
						double d = distances.getAverageDistance(spName);

						if(Double.isNaN(d))
							buff.append(spName + "\t\tNo valid comparisons\n");
						else {
							d *= 100;
							d *= 10000;
							d = (double)Math.round(d); 	// round it off to 4 digits
							d /= 10000;			// back into %ages
							buff.append(spName + "\t\t" + d + "%\n");
						}
					}

				} else {
					buff = new StringBuffer("The following matches occured " + key + "\n");

					Vector vec = (Vector) hash_distances.get(key);	// ignore the returns-null case
					Iterator i = vec.iterator();
					while(i.hasNext()) {
						PairwiseDistance pd = (PairwiseDistance) i.next();

						double distance = pd.getDistance();
						// now, we are accurate to six decimal places
						// so lets round down EXACTLY to six decimal places
						// note that that means:
						// 	0.000001 is the smallest significant distance
						// but, since we have to convert it to 'percentages',
						// when we print our numbers out,
						// 	0.0001% is the smallest significant distance
						distance *= 100;
						distance *= 10000;
						distance = (double)Math.round(distance); // round it off to 4 digits
						distance /= 10000;			// back into %ages
						
						buff.append("\t" + pd.getSequenceA().getDisplayName() + "\t" + pd.getSequenceB().getDisplayName() + "\t" + distance + "%\n");
					}
				}

				text_matches.setText(buff.toString());
			}
		}
	}
	
	// UIExtension stuff
	public String getShortName() { return "Pairwise Explorer"; }
	
	public String getDescription() { return "Allows you to explore the pairwise distances present in this dataset"; }
	
	public Frame getFrame() { return null; }	
}
