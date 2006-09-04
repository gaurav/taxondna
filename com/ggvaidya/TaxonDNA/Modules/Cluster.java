/**
 * 
 * A UIExtension which generates all possible clusters for a particular dataset. 
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
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for clipboard

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class Cluster extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {	
	private TaxonDNA	taxonDNA;
	private SequenceList	set = null;

	private Button		btn_MakeClusters = new Button("Make clusters now?");
	private TextField	text_threshold = new TextField("3");
	private java.awt.List	list_clusters = new java.awt.List();
	private TextArea	text_main = new TextArea();

	private double		max_pairwise = 0.03;

	private Vector		clusters;
	
	private Button		btn_Copy = new Button("Copy to Clipboard");

	// helper function
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	public Cluster(TaxonDNA view) {
		super();

		taxonDNA = view;
		
		// create the panel
		setLayout(new BorderLayout());

		Panel settings = new Panel();

		
		settings.add(new Label("Please select the threshold at which to cluster:"));
		settings.add(text_threshold);
		settings.add(new Label("%"));
		
		btn_MakeClusters.addActionListener(this);
		settings.add(btn_MakeClusters);
		add(settings, BorderLayout.NORTH);
		
		Panel main = new Panel();
		main.setLayout(new BorderLayout());
		list_clusters.add("                                  ");
		list_clusters.addItemListener(this);
		main.add(list_clusters);
		text_main.setEditable(false);
		main.add(text_main, BorderLayout.EAST);
		add(main);
		
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);

		add(buttons, BorderLayout.SOUTH);
	}

	/* Item listener, to display the stuffs */
	public void itemStateChanged(ItemEvent e) {
		if(e.getStateChange() == ItemEvent.SELECTED) {
			selectItem(((Integer)e.getItem()).intValue());
		}
	}
	
	/* 
	 * Somebody selected something from our list; either the "summary" (i == 0) or one of the selected
	 * clusters.
	 */
	private String item_strings[];
	public void selectItem(int i) {
		if(i < item_strings.length) {
		       text_main.setText(item_strings[i]);
		}
	}

	public void writeupItemStrings(DelayCallback delay) throws DelayAbortedException {	
		int delaySteps = clusters.size() + clusters.size();
		int delayInterval = 0;

		if(set != null)
			delaySteps += set.count() * 2;	// try to get as accurate a guess as possible

		delayInterval = delaySteps / 100;
		if(delayInterval == 0)
			delayInterval = 1;
			
		item_strings = new String[1 + clusters.size()];

		if(delay != null)		
			delay.begin();
		
		// summary
		StringBuffer str_final = new StringBuffer("Summary of results\n\n");
		int no_threshold_violations = 0;
		int no_clusters_with_one_species = 0;
		int largest_no_of_species_in_a_cluster = 0;
		double largest_pairwise_distance_observed = 0;
		SpeciesSummary speciesSummary = null;
		int no_of_clusters_with_all_sequences_for_a_species = 0;		
		
		SpeciesDetails sd = null;
		try {
			sd = set.getSpeciesDetails(
					new ProgressDialog(
						taxonDNA.getFrame(),
						"Please wait, calculating the species details ...",
						"I'm calculating the species details for this sequence set. This might take a while. Sorry!"
						)
					);
		} catch(DelayAbortedException e) {
			taxonDNA.unlockSequenceList();
			return;
		}	

		StringBuffer str = new StringBuffer(); 
		Hashtable hash_species = new Hashtable();

		str.append("Cluster\tNo of sequences\tNo of species\tMax pairwise distance\tPercentage of valid comparisons over " + max_pairwise + "\n");
		int delay_count = 0;
		for(int x = 0; x < clusters.size(); x++) {
			Vector bin = (Vector)clusters.get(x);
			Iterator i1 = bin.iterator();


			double largest_pairwise = 0;
			int valid_comparisons = 0;
			int valid_comparisons_over = 0;
			Hashtable hash_species_this = new Hashtable();
				
			str.append((x + 1) + "\t");

			while(i1.hasNext()) {
				Sequence seq = (Sequence) i1.next();

				try {
					if(delay != null && delay_count % delayInterval == 0)
						delay.delay(delay_count, delaySteps);
					delay_count++;
				} catch(DelayAbortedException e) {
					item_strings[0] = "Clustering incomplete; please recluster.";
					return;
				}

				if(hash_species_this.get(seq.getSpeciesName()) == null) {
					hash_species_this.put(seq.getSpeciesName(), new Integer(1));

					if(hash_species.get(seq.getSpeciesName()) == null) {
						hash_species.put(seq.getSpeciesName(), new Integer(1));
					} else {
						hash_species.put(seq.getSpeciesName(), new Integer(((Integer)hash_species.get(seq.getSpeciesName())).intValue() + 1));	
					}

				} else {
					hash_species_this.put(seq.getSpeciesName(), new Integer(((Integer)hash_species.get(seq.getSpeciesName())).intValue() + 1));	
				}


				Iterator i2 = bin.iterator();
				while(i2.hasNext()) {
					Sequence seq2 = (Sequence) i2.next();

					if(seq2.getFullName().equals(seq.getFullName())) {
						continue;
					}
					
					valid_comparisons++;
						
					double pairwise = seq.getPairwise(seq2);

					if(pairwise < 0)	continue;

					if(pairwise > largest_pairwise) {
						largest_pairwise = pairwise;
					}

					if(pairwise > max_pairwise) {
						valid_comparisons_over++;
					}
				}
			}					

			double percentage = 0;
			if(valid_comparisons != 0) { 
				percentage = percentage(valid_comparisons_over, valid_comparisons);
			}
			
			if(largest_pairwise > max_pairwise)
				no_threshold_violations++;

			if(largest_pairwise > largest_pairwise_distance_observed)
				largest_pairwise_distance_observed = largest_pairwise;

			if(hash_species_this.keySet().size() == 1) {
				no_clusters_with_one_species++;
			
				// now, compare no of seq found for
				// this one species with the no of
				// seq available for this species
				// in the database.

				// of course, we just cancel
				// unless the SpeciesSummary
				// module is loaded
				String speciesName = ( (String) (hash_species_this.keySet().toArray())[0]);
				if(sd.getSpeciesDetailsByName(speciesName).getSequencesWithValidMatchesCount() == bin.size())
					no_of_clusters_with_all_sequences_for_a_species++;
			}

			if(hash_species_this.keySet().size() > largest_no_of_species_in_a_cluster)
				largest_no_of_species_in_a_cluster = hash_species_this.keySet().size();
			
			str.append(bin.size() + "\t" + hash_species_this.keySet().size() + "\t" + percentage(largest_pairwise, 1) + "%\t" + (percentage) + "%\n");
		}

		str.append("\n\nSummary of species\nSpecies\tSequences\tFound in how many clusters?\tFound with how many other species?\n");

		Enumeration enu = hash_species.keys();
		while(enu.hasMoreElements()) {
			String name = (String) enu.nextElement();
			int count_sequences = 0;
			int species_found_with = 0;

			Iterator i = set.iterator();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				if(seq.getSpeciesName().equals(name))
					count_sequences++;
			}

			Iterator iter1 = clusters.iterator();
			while(iter1.hasNext()) {
				Vector bin = (Vector)iter1.next();

				int contains_species = 0;
				Iterator iter2 = bin.iterator();
				boolean containsThisOne = false;
				Hashtable species = new Hashtable();
				while(iter2.hasNext()) {
					Sequence seq = (Sequence) iter2.next();
					String speciesName = seq.getSpeciesName();
					
					if(speciesName.equals(name)) {
						containsThisOne = true;
					} else if(species.get(speciesName) == null) {
						species.put(speciesName, new Integer(1));
						contains_species++;
					}
				}

				if(containsThisOne) {
					containsThisOne = false;
					species_found_with += contains_species;
				}
			}

			str.append(name + "\t" + count_sequences + "\t" + hash_species.get(name) + "\t" + species_found_with + "\n"); 
		}

		// add stuff to str_final
		str_final.append("Clustering at:\t" + percentage(max_pairwise, 1) + "%\n");
		str_final.append("Number of clusters:\t" + clusters.size() + "\n");
		str_final.append("Number of clusters with threshold violations:\t" + no_threshold_violations + " (" + percentage(no_threshold_violations, clusters.size()) + "%)\n");
		str_final.append("Largest pairwise distance:\t" + percentage(largest_pairwise_distance_observed, 1) + "%\n");
		str_final.append("Profiles with only one species:\t" + no_clusters_with_one_species + "\n");
		str_final.append("Profiles corresponding to traditional taxonomy:\t" + no_of_clusters_with_all_sequences_for_a_species + "\n");
		str_final.append("Largest number of species in a cluster:\t" + largest_no_of_species_in_a_cluster + "\n");
		
		str_final.append("\n");
		str_final.append(str);

		item_strings[0] = str_final.toString();

		delay_count++;
		for(int i = 1; i <= clusters.size(); i++) {
		// information on that particular clusters
			Vector bin = (Vector)clusters.get(i - 1); // 
			str = new StringBuffer();
			Iterator i1;
			Hashtable species = new Hashtable();


			str.append("Cluster " + (i) + " consists of " + bin.size() + " sequences ");
			// count species?
			int nSpecies = 0; 
			int nValidComparisons = 0;
			int nOverLimit = 0;
			StringBuffer first_line = new StringBuffer();
			StringBuffer pairwise_table = new StringBuffer();
			i1 = bin.iterator();
			while(i1.hasNext()) {
				Sequence seq1 = (Sequence)i1.next();

				try {
					if(delay != null && delay_count % delayInterval == 0)
						delay.delay(delay_count, delaySteps);
				} catch(DelayAbortedException e) {
					item_strings[0] = "Clustering incomplete; please recluster.";
					return;
				}

				if(species.get(seq1.getSpeciesName()) == null) {
					species.put(seq1.getSpeciesName(), new Integer(1));
					nSpecies++;
				}

				first_line.append(seq1.getFullName() + "\t");

				pairwise_table.append(seq1.getFullName() + "\t");
				
				Iterator i2 = bin.iterator();
				while(i2.hasNext()) {
					double pairwise = 0;
					Sequence seq2 = (Sequence)i2.next();
						
					pairwise = seq2.getPairwise(seq1);
					if(seq2.getPairwise(seq1) < 0) {
						pairwise_table.append("\t(inadequate overlap)");
					} else {
						pairwise_table.append("\t" + percentage(pairwise, 1) + "%");
						
						if(pairwise > max_pairwise)
							nOverLimit++;
					}
				}
				pairwise_table.append("\n");
			}

			str.append("with " + nOverLimit + " sequences (" + percentage(nOverLimit, nValidComparisons) + "%) over " + percentage(max_pairwise, 1) + "%\n");

			item_strings[i] = (str.toString() + "\n\t" + first_line.toString() + "\n" + pairwise_table.toString());
		}
		
		if(delay != null)
			delay.end();
	}

	/* Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		text_threshold.setText("3");
		list_clusters.removeAll();
		text_main.setText("");

		//btn_MakeClusters.setLabel("THE DATA HAS CHANGED SINCE THE LAST CLUSTERING. Recluster?");
	}
		
	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();

		if(cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");
		}

		if(evt.getSource().equals(btn_MakeClusters)) {
			try {
				Double d = new Double(text_threshold.getText());
				max_pairwise = d.doubleValue()/100;
			} catch(NumberFormatException e) {
				list_clusters.removeAll();
				list_clusters.add("Could not process");
				text_main.setText("Please enter a valid number for the threshold");
				return;
			}
			
			new Thread(this, "Cluster").start();
		}
	}
	
	public void run() {
		set = taxonDNA.lockSequenceList();
				
		ProgressDialog pb = new ProgressDialog(taxonDNA.getFrame(), "Clustering sequences at " + (max_pairwise * 100) + "% ...", "All your sequences are being clustered, please wait ...", 0);
				
		pb.begin();


			text_main.setText("");

			clusters = new Vector();
			
			if(set != null) {
				Iterator iter = set.iterator();
				int c = 0;
				while(iter.hasNext()) {
					boolean	done = false;
					Sequence seq = (Sequence) iter.next();
					Vector our_first_bin = null;
	
					int increment = set.count()/100;
					if(increment == 0)
						increment = 1;
					try {
						if(c % increment == 0)
							pb.delay(c, set.count());
					} catch(DelayAbortedException e) {
						taxonDNA.unlockSequenceList();
						return;
					}

					c++;	// only used to drive the pb.delay
		
					int cluster = clusters.size();
					while(cluster > 0) {
						cluster--;
						
						Vector v = (Vector) clusters.get(cluster);

						int current = v.size();
						while(current > 0) {
							current--;
		
							Sequence compare = (Sequence) v.get(current);

							if(seq.getPairwise(compare) < 0) continue;

							if(seq.getPairwise(compare) <= max_pairwise) {
								if(done) {
									// merge them bins
									Iterator i = v.iterator();

									while(i.hasNext()) {
										our_first_bin.add(i.next());
									}

									clusters.remove(v);
									current = 0; //i.e. break out of inner loop
								} else {
									// add this sequence
									v.add(seq);
									our_first_bin = v;
									
									current = 0; //i.e. no need to look in this bin any more
									done = true;
								}
							}
									
						}
					}	
				
					if(!done) {
						// new cluster
						Vector vec = new Vector();
						vec.add(seq);
						clusters.add(vec);
					}
				}	

				// now all the sequences have been clustered
				list_clusters.removeAll();
				list_clusters.add("Summary");

				Iterator i = clusters.iterator();
				int x = 0;
				while(i.hasNext()) {
					try {
						pb.delay(x, clusters.size());
					} catch(DelayAbortedException e) {
						taxonDNA.unlockSequenceList();
						return;
					}
					
					Vector v = (Vector)i.next();

					StringBuffer name = new StringBuffer("Cluster " + (++x) + " (");
					Iterator i2 = v.iterator();

					while(i2.hasNext()) {
						Sequence seq = (Sequence)i2.next();
						
						name.append(seq.getSpeciesName());
						if(i2.hasNext())
							name.append(", ");
					}

					name.append(")");

					list_clusters.add(name.toString());
				}
		
			}

		pb.end();

		pb = new ProgressDialog(
				taxonDNA.getFrame(),
				"Writing up information ...",
				"Formatting and writing the results, please wait.",
				0);

		try {
			writeupItemStrings(pb);
		} catch(DelayAbortedException e) {
			taxonDNA.unlockSequenceList();
			return;
		}

		selectItem(0);
		
		taxonDNA.unlockSequenceList();
	}
	
	// UIExtension stuff
	public String getShortName() { return "Cluster"; }
	
	public String getDescription() { return "Clusters the dataset."; }
	
	public Frame getFrame() { return null; }
	
}
