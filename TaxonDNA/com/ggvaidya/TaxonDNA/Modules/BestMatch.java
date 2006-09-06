/**
 * BestMatch looks at two things: the closest match to any particular
 * sequence, and the closest "good" match (within a pairwise distance
 * limit set by the user).
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2005 Gaurav Vaidya
    
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
import java.awt.datatransfer.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class BestMatch extends Panel implements UIExtension, ActionListener, Runnable {
	private TaxonDNA	taxonDNA = null;

	private TextArea	text_main = new TextArea();			// displays the results
	private TextField	text_threshold = new TextField();		// tiny textfield, to display the
										// threshold (etc. 3%) 

	private Button		btn_recalculate = new Button(" Calculate! ");
	private Button		btn_Copy;
	private Button		btn_threshold = new Button("Compute from Pairwise Summary");

	private boolean		processingDone = false;
	private double		threshold = 0;

	public BestMatch(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
		
		setLayout(new BorderLayout());

		Panel top = new Panel();
		RightLayout rl = new RightLayout(top);
		top.setLayout(rl);

		rl.add(new Label("Please enter the threshold for best close match:"), RightLayout.NONE);

		text_threshold.setText("03.000");
		rl.add(text_threshold, RightLayout.BESIDE);

		rl.add(new Label("%"), RightLayout.BESIDE);

		btn_threshold.addActionListener(this);
		rl.add(btn_threshold, RightLayout.BESIDE);
		
		btn_recalculate.addActionListener(this);
		rl.add(btn_recalculate, RightLayout.NEXTLINE | RightLayout.FILL_4);

		add(top, BorderLayout.NORTH);

		text_main.setEditable(false);
		add(text_main);

		text_main.setText("No data loaded.");

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Copy = new Button("Copy to Clipboard");
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);		
	
		add(buttons, BorderLayout.SOUTH);
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		// Copy to Clipboard
		if(cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException ex) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");
		}

		// Calculate the threshold (by invoking PairwiseDistribution)
		if(e.getSource().equals(btn_threshold)) {
			if(taxonDNA.getExtension("Pairwise Summary") != null) {
				PairwiseSummary ps = (PairwiseSummary) taxonDNA.getExtension("Pairwise Summary");
				double cutoff = ps.getFivePercentCutoff();
	
				if(cutoff > 0)
					text_threshold.setText(String.valueOf(cutoff));
				else {
					// invalid cutoff?! how can??
					ps.run();
					cutoff = ps.getFivePercentCutoff();
					if(cutoff > 0)
						text_threshold.setText(String.valueOf(cutoff));
				}	
			}
		}
		
		// Calculate the BestMatch (via new Thread->run(this))
		if(e.getSource().equals(btn_recalculate)) {
			// Recalculate!
			btn_recalculate.setLabel("Recalculate!"); 
			if(text_threshold.getText().trim().equals("")) {	// no threshold specified
				MessageBox mb = new MessageBox(
					taxonDNA.getFrame(),
					"No threshold specified!",
					"You did not specify a threshold for the \"best close match\" algorithm!\n\nWould you like to continue anyway, using a default threshold of 3%?",
					MessageBox.MB_YESNO);
				if(mb.showMessageBox() == MessageBox.MB_YES)
					text_threshold.setText("3.0");
				else
					return;
			}
			threshold = Double.valueOf(text_threshold.getText()).doubleValue();
			if(threshold == 0) {
				text_threshold.setText("0.0");
			}
			new Thread(this, "BestMatch").start();
		}
	}
	
	public void dataChanged() {
		SequenceList set = taxonDNA.lockSequenceList();

		if(set == null) {
			text_main.setText("");
			text_threshold.setText("3.0");
		} else {
			if(processingDone) {
				processingDone = false;
				text_main.setText("Please press the 'Calculate' button to begin a best match analysis.");
			}
		}

		taxonDNA.unlockSequenceList();
	}

	public void run() {
		// clear the flag
		processingDone = false;
		
		// counters
		int x = 0;

		// counts
		int total_count_sequences = 0;	// the count of *all* the sequences 
		int valid_sequences = 0;
		
		int best_match_correct = 0;
		int best_match_ambiguous = 0;
		int best_match_incorrect = 0;
		int best_match_noallo = 0;

		int best_close_match_noallo = 0;
		int best_close_match_correct = 0;
		int best_close_match_ambiguous = 0;
		int best_close_match_incorrect = 0;
		int best_close_match_nomatch = 0;

		// sequence listing
		StringBuffer str_listings = new StringBuffer("Query\tClosest conspecific match\tDistance\tOverlap\tClosest allospecific match\tDistance\tOverlap\t\tBest match\t\tBest close match\n");
		
		// get the new threshold
		double threshold = Double.parseDouble(text_threshold.getText());
		text_threshold.setText(String.valueOf(threshold));
		threshold /= 100;

		// get the sequence set, and figure out its stats
		SequenceList set = taxonDNA.lockSequenceList();
		SortedSequenceList sset = new SortedSequenceList(set);

		total_count_sequences = set.count();

		/*
		// We need to know what the species summary is.
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
		*/

		// set up us the ProgressDialog
		ProgressDialog pd = new ProgressDialog(
				taxonDNA.getFrame(), 
				"Please wait, doing best match analysis ...", 
				"The best match analysis is being performed. Sorry for the wait!", 
				0
			);

		pd.begin();

		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence query = (Sequence) i.next();

			String name_query = query.getSpeciesName();

			// notify user
			int count_sequences = set.count();
				try {
					pd.delay(x, count_sequences);
				} catch(DelayAbortedException e) {
					dataChanged();
					taxonDNA.unlockSequenceList();
					return;
				}
			
			// for each query, we run a SortedSequenceSet.
			try { 
				sset.sortAgainst(query, null);
			} catch(DelayAbortedException e) {
				// no DelayCallback
			}

			// begin processing
			count_sequences = 		sset.count();
			Sequence first_con = 		null;
			Sequence first_allo = 		null;
			boolean conspecific_no_overlap = false;		// true -> there is no conspecific within overlap
			boolean allospecific_no_overlap = false;	// true -> there is no allospecific within overlap

			for(int y = 1; y < count_sequences; y++) {
				Sequence match = sset.get(y);	
				String name_species = match.getSpeciesName();
				double distance = match.getPairwise(query);

				// if we don't have a conspecific, and this is a conspecific -> we have a first_con!
				if(conspecific_no_overlap == false && first_con == null && name_species.equals(name_query))
				{
					if(distance < 0)
						conspecific_no_overlap = true;	
					else
						first_con = match;
				}

				// if we don't have an allospecific, and this is an allospecific -> we have a first_allo!
				if(allospecific_no_overlap == false && first_allo == null && !name_species.equals(name_query)) 
				{
					if(distance < 0)
						allospecific_no_overlap = true;
					else
						first_allo = match;
				}

				// if we've got everything, GET ON WITH LIFE!
				if(
					// conspecifics done?
					(
						(conspecific_no_overlap == true) || (first_con != null)
				        ) && (
						(allospecific_no_overlap == true) || (first_allo != null)
					)
				)
					break;
			}

			double dist_con = -1;
			if(first_con != null) {
				dist_con = first_con.getPairwise(query);
				valid_sequences++;
			}

			double dist_allo = -1;
			if(first_allo != null)
				dist_allo = first_allo.getPairwise(query);			

			// explain to the user what's happening with distances 
			// conspecifics 
			str_listings.append(query.getName() + "\t");
			if(first_con == null) {
				if(conspecific_no_overlap)
					str_listings.append("Inadequate overlap\t-1.0\t0\t");
				else
					str_listings.append("No conspecifics in database\t-1.0\t0\t");
			} else {
				double distance = query.getPairwise(first_con);
				int overlap = query.getSharedLength(first_con);
				str_listings.append(first_con.getName() + "\t" + percentage(distance, 1) + "\t" + overlap + "\t");
			}

			// allospecifics
			if(first_allo == null) {
				if(allospecific_no_overlap)
					str_listings.append("Inadequate overlap\t-1.0\t0\t");
				else
					str_listings.append("No allospecifics in database\t-1.0\t0\t");
			} else {
				double distance = query.getPairwise(first_allo);
				int overlap = query.getSharedLength(first_allo);
				str_listings.append(first_allo.getName() + "\t" + percentage(distance, 1) + "\t" + overlap + "\t");
			}
			
			// best match and best close match
			if(dist_allo == -1) {
				best_match_noallo++;
				str_listings.append("\tno_allospecific_match\t");
				
				best_close_match_noallo++;
				str_listings.append("\tno_allospecific_match\t");

			} else if(dist_con == -1) {
				best_match_incorrect++;
				str_listings.append("\tincorrect_no_con\t");	

				if(dist_allo <= threshold) {
					best_close_match_incorrect++;
					str_listings.append("\tincorrect_no_con\t");
				} else {
					best_close_match_nomatch++;
					str_listings.append("\tno_match\t");
				}

			} else if(com.ggvaidya.TaxonDNA.DNA.Settings.identical(dist_con, dist_allo)) {
				best_match_ambiguous++;
				str_listings.append("\tambiguous\t");

				if(dist_con <= threshold && dist_allo <= threshold) {
					best_close_match_ambiguous++;
					str_listings.append("\tambiguous\t");
				} else {
					best_close_match_nomatch++;
					str_listings.append("\tno_match\t");
				}

			} else if(dist_con < dist_allo) {
				best_match_correct++;
				str_listings.append("\tcorrect\t");

				if(dist_con > threshold) {
					best_close_match_nomatch++;	
					str_listings.append("\tno_match\t");
				} else {
					best_close_match_correct++;
					str_listings.append("\tcorrect\t");
				}
					
			} else if(dist_allo < dist_con) {
				best_match_incorrect++;
				str_listings.append("\tincorrect\t");

				if(dist_allo > threshold) {
					best_close_match_nomatch++;
					str_listings.append("\tno_match\t");
				} else {
					best_close_match_incorrect++;
					str_listings.append("\tincorrect\t");
				}

			} else {
				throw new RuntimeException("Hit an unexpected program situation! Contact the programmer!");
			} 

			str_listings.append("\n");
		       	
			// increment counter
			x++;
		}

		// Now, since we are NOT counting sequences which matched against NOTHING
		// (i.e. best_match_noallo), we calculate percentages based on count_sequences_with_match;
		int count_sequences_with_match = total_count_sequences - best_match_noallo;

		text_main.setText(
				"Sequences:\t" + total_count_sequences + 
				"\nSequences with atleast one matching conspecific sequence in the data set:\t" + valid_sequences +
				"\nSequences with atleast one matching sequence in the data set:\t" + count_sequences_with_match +
				"\n\nCorrect identifications according to \"Best Match\":\t" + best_match_correct + " (" + percentage(best_match_correct, count_sequences_with_match) + "%)" +
				"\nAmbiguous according to \"Best Match\":\t" + best_match_ambiguous + " (" + percentage(best_match_ambiguous, count_sequences_with_match) + "%)" +
				"\nIncorrect identifications according to \"Best Match\":\t" + best_match_incorrect + " (" + percentage(best_match_incorrect, count_sequences_with_match) + "%)" +
//				"\nSequences without any match with adequate overlap:\t" + best_match_noallo + " (" + percentage(best_match_noallo, total_count_sequences) + "%)" +
				"\n\nCorrect identifications according to \"Best Close Match\":\t" + best_close_match_correct + " (" + percentage(best_close_match_correct, count_sequences_with_match) + "%)" + 
				"\nAmbiguous according to \"Best Close Match\":\t" + best_close_match_ambiguous + " (" + percentage(best_close_match_ambiguous, count_sequences_with_match) + "%)" +
				"\nIncorrect identifications according to \"Best Close Match\":\t" + best_close_match_incorrect + " (" + percentage(best_close_match_incorrect, count_sequences_with_match) + "%)" +
				"\nSequences without any match closer than " + percentage(threshold, 1) + "%:\t" + best_close_match_nomatch + " (" + percentage(best_close_match_nomatch, count_sequences_with_match) + "%)" +
				"\n\n" + str_listings.toString());

		pd.end();
		
		taxonDNA.unlockSequenceList();
		processingDone = true;
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}
	
	public String getShortName() {		return "Best Match/Best Close Match"; 	}
	public String getDescription() {	return "Determines the best match and best close match for sequences"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
