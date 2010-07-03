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

package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class BestMatch extends Panel implements UIExtension, ActionListener, Runnable {
	private SpeciesIdentifier	seqId = null;

	private TextArea	text_main = new TextArea();			// displays the results
	private TextField	text_threshold = new TextField();		// tiny textfield, to display the
										// threshold (etc. 3%) 

	private Button		btn_recalculate = new Button(" Calculate! ");
	private Button		btn_Copy;
	private Button		btn_threshold = new Button("Compute from Pairwise Summary");

	private boolean		processingDone = false;
	private double		threshold = 0;

	public BestMatch(SpeciesIdentifier seqId) {
		this.seqId = seqId;
		
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
			if(seqId.getExtension("Pairwise Summary") != null) {
				PairwiseSummary ps = (PairwiseSummary) seqId.getExtension("Pairwise Summary");
				double cutoff = ps.getFivePercentCutoff();
	
				if(cutoff > -1)
					text_threshold.setText(String.valueOf(cutoff));
				else {
					// invalid cutoff?! how can??
					ps.run();
					cutoff = ps.getFivePercentCutoff();
					if(cutoff > -1)
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
					seqId.getFrame(),
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
		SequenceList set = seqId.lockSequenceList();

		if(set == null) {
			text_main.setText("");
			text_threshold.setText("3.0");
		} else {
			if(processingDone) {
				processingDone = false;
				text_main.setText("Please press the 'Calculate' button to begin a best match analysis.");
			}
		}

		seqId.unlockSequenceList();
	}

	public void run() {
		// clear the flag
		processingDone = false;
		
		// counters
		int x = 0;

		// counts
		int total_count_sequences = 0;	// the count of *all* the sequences 
		int count_sequences_without_species_names = 0;
		
		// all of the following should add up to (total_count_sequences - number_no_matches)
		int best_match_correct = 0;
		int best_match_ambiguous = 0;
		int best_match_incorrect = 0;

		int best_close_match_correct = 0;
		int best_close_match_ambiguous = 0;
		int best_close_match_incorrect = 0;
		int best_close_match_nomatch = 0;

		int count_allo_at_zero = 0;
		int count_zero_percent_matches = 0;
		int count_seqs_with_valid_conspecific_matches = 0;

		// the no-matches-found count
		int count_no_matches = 0;

		// sequence listing
		StringBuffer str_listings = new StringBuffer("Query\tMatch\tIdentification\n");
		
		// get the new threshold
		double threshold = Double.parseDouble(text_threshold.getText());
		text_threshold.setText(String.valueOf(threshold));
		threshold /= 100;

		// get the sequence set, and figure out its stats
		SequenceList set = seqId.lockSequenceList();
		if(set == null) {
			text_main.setText("No sequences loaded!");
			seqId.unlockSequenceList();
			return;
		}
		SortedSequenceList sset = new SortedSequenceList(set);

		total_count_sequences = set.count();

		/*
		// We need to know what the species summary is.
		SpeciesDetails sd = null;
		try {
			sd = set.getSpeciesDetails(
					ProgressDialog.create(
						seqId.getFrame(),
						"Please wait, calculating the species details ...",
						"I'm calculating the species details for this sequence set. This might take a while. Sorry!"
						)
					);
		} catch(DelayAbortedException e) {
			seqId.unlockSequenceList();
			return;
		}
		*/

		// set up us the ProgressDialog
		ProgressDialog pd = ProgressDialog.create(
				seqId.getFrame(), 
				"Please wait, doing best match analysis ...", 
				"The best match analysis is being performed. Sorry for the wait!", 
				0
			);

		pd.begin();

		Iterator i = set.iterator();

		while(i.hasNext()) {
			Sequence query = (Sequence) i.next();
			Sequence first_con = null;
			Sequence first_allo = null;

			// notify user
			try {
				pd.delay(x, total_count_sequences);
			} catch(DelayAbortedException e) {
				dataChanged();
				seqId.unlockSequenceList();
				return;
			}

			// increment counter
			x++;

			// but does it have a species name? an analysis is
			// pretty pointless without a species name!
			if(query.getSpeciesName() == null) {
				count_sequences_without_species_names++;
				continue;
			}
			
			// for each query, we run a SortedSequenceSet.
			try { 
				sset.sortAgainst(query, null);
			} catch(DelayAbortedException e) {
				// no DelayCallback
			}

			// begin processing
			int count_sequences = 		sset.count();
			Sequence bestMatch =		sset.get(1);

			// add ourselves to the listings
			str_listings.append(query.getDisplayName());

			// is 'bestMatch' valid? If not, we have no_match at all!
			if(bestMatch == null || bestMatch.getPairwise(query) == -1) {
				str_listings.append("\t\tNo match.\n");
				count_no_matches++;

				continue;
			}

			double bestMatchDistance = bestMatch.getPairwise(query);

			if(identical(bestMatchDistance, 0)) {
				count_zero_percent_matches++;
			}

			// look for a block after the 'best match'
			boolean clean_block = false;
			boolean mixed_block = false;
			int count_bestMatches = 0;
			for(int y = 2; y < count_sequences; y++) {
				Sequence match = sset.get(y);

				if(match == null) {
					// wtf? shouldn't happen, but say it does.
					throw new RuntimeException("I ran out of Sequences when looking up " + query + "! This is a programming error.");
				}

				if(!identical(match.getPairwise(query), bestMatchDistance)) {
					// NOT identical
					// we're now out of the block!
					break;
				}

				count_bestMatches++;

				// now, in the block, check whether we're still clean ... or mixed
				// please note that here (and ONLY here), conspecific and allospecific
				// refer to whether the sequences in the block are con and allospecific
				// to the bestMatch, NOT to the query!
				if(match.getSpeciesName().equals(bestMatch.getSpeciesName())) {
					// conspecific
					clean_block = true;
				} else {
					// allospecific	
					mixed_block = true;
				}
			}

			// completely independently: check for allo and conspecific matches
			for(int y = 1; y < count_sequences; y++) {
				Sequence match = sset.get(y);

				if(match == null) {
					// shouldn't happen; say it does.
					throw new RuntimeException("I ran out of Sequences when looking up " + query + "! This is a programming error.");
				}

				// if the match has no species name, we should report this to the Authorities.
				if(match.getSpeciesName() == null) {
					// if the match has no species name, no worries - we'll catch it in the query check above
					// we ignore it and move on.
					continue;
				}

				if(match.getPairwise(query) >= 0) {
					if(first_con == null && match.getSpeciesName().equals(query.getSpeciesName())) {
						// conspecific
						first_con = match;
						count_seqs_with_valid_conspecific_matches++;
					} else if(first_allo == null && !match.getSpeciesName().equals(query.getSpeciesName())) {
						// allospecific
						first_allo = match;
					}

					if(first_con != null && first_allo != null)
						break;
				}
			}

			// write down first_con and first_allo into the listings.
			if(first_con == null) {
				str_listings.append("\tNo conspecific in database\t---\t0");
			} else {
				str_listings.append("\t" + first_con.getDisplayName() + "\t" + percentage(query.getPairwise(first_con), 1) + "\t" + query.getSharedLength(first_con));
			}

			if(first_allo == null) {
				str_listings.append("\tNo allospecific in database\t---\t0");
			} else {
				str_listings.append("\t" + first_allo.getDisplayName() + "\t" + percentage(query.getPairwise(first_allo), 1) + "\t" + query.getSharedLength(first_allo));
			}	

			// is it conspecific or allospecific?
			boolean conspecific = false;
			
			if(bestMatch.getSpeciesName() != null && bestMatch.getSpeciesName().equals(query.getSpeciesName()))
					conspecific = true;

			// so: what's the block situation?
			if(!clean_block && !mixed_block) {
				// there is NO block. the sequence is decided on its own merit.
				str_listings.append("\t" + bestMatch.getDisplayName());
				if(conspecific) {
					str_listings.append("\tSuccessful match at " + percentage(bestMatchDistance, 1) + "%");
					best_match_correct++;

					if(bestMatchDistance <= threshold) {
						best_close_match_correct++;
						str_listings.append(" (within threshold)\n");
					} else {
						best_close_match_nomatch++;
						str_listings.append(" (outside threshold)\n");
					}
				} else {
					if(identical(bestMatchDistance, 0)) {
						count_allo_at_zero++;
					}

					str_listings.append("\tIncorrect match at " + percentage(bestMatchDistance, 1) + "%");
					best_match_incorrect++;

					if(bestMatchDistance <= threshold) {
						best_close_match_incorrect++;
						str_listings.append(" (within threshold)\n");
					} else {
						best_close_match_nomatch++;
						str_listings.append(" (outside threshold)\n");
					}
				}
			} else if(clean_block && !mixed_block) {
				// now, bear in mind that you can't actually have BOTH
				// clean_block and mixed_block. If mixed_block is ON, it's
				// a mixed_block, and there ain't much you can do about it.
				//
				// this is the only other alternative: clean_block WITHOUT mixed_block

				str_listings.append("\t" + bestMatch.getDisplayName() + " and " + count_bestMatches + " others");
				if(conspecific) {
					str_listings.append("\tSuccessful match at " + percentage(bestMatchDistance, 1) + "%");
					best_match_correct++;

					if(bestMatchDistance <= threshold) {
						best_close_match_correct++;

						str_listings.append(" (within threshold)\n");
					} else {
						best_close_match_nomatch++;

						str_listings.append(" (outside threshold)\n");
					}
				} else {
					if(identical(bestMatchDistance, 0)) {
						count_allo_at_zero++;
					}

					str_listings.append("\tIncorrect match at " + percentage(bestMatchDistance, 1) + "%");
					best_match_incorrect++;

					if(bestMatchDistance <= threshold) {
						best_close_match_incorrect++;

						str_listings.append(" (within threshold)\n");
					} else {
						best_close_match_nomatch++;

						str_listings.append(" (outside threshold)\n");
					}
				}
			} else if(mixed_block) {
				// mixed blocks
				// by definition, this is ambiguous all over :).
				if(identical(bestMatchDistance, 0)) {
					count_allo_at_zero++;
				}

				str_listings.append("\t" + bestMatch.getDisplayName() + " and " + count_bestMatches + " others from different species\tMultiple species found at " + percentage(bestMatchDistance, 1) + "%, identification with certainty is impossible" );
				best_match_ambiguous++;

				if(bestMatchDistance <= threshold) {
					best_close_match_ambiguous++;

					str_listings.append(" (within threshold)\n");
				} else {
					best_close_match_nomatch++;

					str_listings.append(" (outside threshold)\n");
				}
			} else {
				throw new RuntimeException("Programming error: the program is now somewhere where it really shouldn't be. Please contact the programmer!");
			}
		}

		// Now, since we are NOT counting sequences which matched against NOTHING
		// (i.e. best_match_noallo), we calculate percentages based on count_sequences_with_valid_matches;
		int count_sequences_with_valid_matches = total_count_sequences - count_no_matches - count_sequences_without_species_names;

		text_main.setText(
				"Sequences:\t" + total_count_sequences + 
				"\nSequences without recognizable species names (ignored in all subsequent counts):\t" + count_sequences_without_species_names +
				"\nSequences with atleast one matching sequence in the data set:\t" + count_sequences_with_valid_matches +
				"\nSequences with atleast one matching conspecific sequence in the data set:\t" + count_seqs_with_valid_conspecific_matches + 
				"\nSequences with a closest match at 0%:\t" + count_zero_percent_matches +
				"\nAllospecific matches at 0%:\t" + count_allo_at_zero + "\t(" + percentage(count_allo_at_zero, count_zero_percent_matches) + "% of all matches at 0%)" +
				"\n\nCorrect identifications according to \"Best Match\":\t" + best_match_correct + " (" + percentage(best_match_correct, count_sequences_with_valid_matches) + "%)" +
				"\nAmbiguous according to \"Best Match\":\t" + best_match_ambiguous + " (" + percentage(best_match_ambiguous, count_sequences_with_valid_matches) + "%)" +
				"\nIncorrect identifications according to \"Best Match\":\t" + best_match_incorrect + " (" + percentage(best_match_incorrect, count_sequences_with_valid_matches) + "%)" +
//				"\nSequences without any match with adequate overlap:\t" + best_match_noallo + " (" + percentage(best_match_noallo, total_count_sequences) + "%)" +
				"\n\nCorrect identifications according to \"Best Close Match\":\t" + best_close_match_correct + " (" + percentage(best_close_match_correct, count_sequences_with_valid_matches) + "%)" + 
				"\nAmbiguous according to \"Best Close Match\":\t" + best_close_match_ambiguous + " (" + percentage(best_close_match_ambiguous, count_sequences_with_valid_matches) + "%)" +
				"\nIncorrect identifications according to \"Best Close Match\":\t" + best_close_match_incorrect + " (" + percentage(best_close_match_incorrect, count_sequences_with_valid_matches) + "%)" +
				"\nSequences without any match closer than " + percentage(threshold, 1) + "%:\t" + best_close_match_nomatch + " (" + percentage(best_close_match_nomatch, count_sequences_with_valid_matches) + "%)" +
				"\n\n" + str_listings.toString());

		pd.end();
		
		seqId.unlockSequenceList();
		processingDone = true;
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.Model.Settings.percentage(x, y);
	}

	private boolean identical(double x, double y) {
		return com.ggvaidya.TaxonDNA.Model.Settings.identical(x, y);
	}
	
	public String getShortName() {		return "Best Match/Best Close Match"; 	}
	public String getDescription() {	return "Determines the best match and best close match for sequences"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
