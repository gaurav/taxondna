/**
 * Block analysis is used to study the properties of the entire SequenceSet: how
 * well it could be resolved if somebody needed to use DNA taxonomy.
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


public class BlockAnalysis extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {
	private TaxonDNA	taxonDNA = null;
	private SequenceList	set = null;

	private TextArea	text_main = new TextArea();
	private TextField	text_threshold = new TextField();

	private Button		btn_recalculate = new Button(" Calculate! ");
	private Button		btn_Copy;
	private Button		btn_threshold = new Button("Compute from Pairwise Summary");

	private boolean		processingDone = false;
	private double		threshold = 0;

	private String		display_strings[];

	public BlockAnalysis(TaxonDNA taxonDNA) {
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

	public void itemStateChanged(ItemEvent e) {
		if(e.getStateChange() == ItemEvent.SELECTED) {
			int item = ((Integer)e.getItem()).intValue();

			if(!processingDone)
				text_main.setText("Please click on the \"Calculate\" button to calculate.");
			else {
				if(item <= display_strings.length) {
					text_main.setText(display_strings[item]);
				} else {
					text_main.setText("Invalid item");
				}
			}
		}
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

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
		
		if(e.getSource().equals(btn_recalculate)) {
			// Recalculate!
			btn_recalculate.setLabel("Recalculate"); 
			if(text_threshold.getText().equals("")) {	// no threshold specified
				MessageBox mb = new MessageBox(
					taxonDNA.getFrame(),
					"No threshold specified!",
					"You did not specify a threshold for the all species barcodes!\n\nWould you like to continue anyway, using a default threshold of 3%?",
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
			new Thread(this, "BlockAnalysis").start();
		}
	}
	
	public void dataChanged() {
		set = taxonDNA.lockSequenceList();

		if(set == null) {
			text_main.setText("");
			text_threshold.setText("3.0");
		} else {
			processingDone = false;
			text_main.setText("Please press the 'Calculate' button to conduct an all species barcodes analysis.");
		}

		taxonDNA.unlockSequenceList();
	}

	public void run() {
		// clear the flag
		processingDone = false;
		
		// counters
		int x = 0, count_sequences;

		// counts
		int valid_sequences = 0;
		
		int block_correct = 0;
		int block_incorrect = 0;
		int block_ambiguous = 0;
		int block_nomatch = 0;
		int block_noseq = 0;

		// sequence listing
		StringBuffer str_listings = new StringBuffer("Query\tFound in a complete block?\n");
		
		// get the new threshold
		double threshold = Double.parseDouble(text_threshold.getText());
		text_threshold.setText(String.valueOf(threshold));
		threshold /= 100;

		// get the sequence set, and figure out its stats
		set = taxonDNA.lockSequenceList();
		count_sequences = set.count();
		SortedSequenceList sset = new SortedSequenceList(set); 

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

		// set up us the ProgressDialog
		ProgressDialog pd = new ProgressDialog(taxonDNA.getFrame(), "Please wait, doing all species barcodes analysis ...", "The all species barcodes analysis is being performed. Sorry for the wait!", 0);

		pd.begin();

		Iterator i = set.iterator();
		while(i.hasNext()) {
			Sequence query =	(Sequence) i.next();
			String name_query = query.getSpeciesName();

			// notify user
				try {
					pd.delay(x, count_sequences);
				} catch(DelayAbortedException e) {
					dataChanged();
					taxonDNA.unlockSequenceList();
					return;
				}
			
			
			// for each query, we run a SortedSequence...thingie
			try { 
				sset.sortAgainst(query, null);
			} catch(DelayAbortedException e) {
				// there isn't one
			}

			// and see whether we have a block
			int block_size = 1;		// we start from the 2nd place
			boolean is_block_correct = false;
			boolean is_block_incorrect = false;
			boolean block_nomatches = false;
			String name_first_match = "";
			if(sset.count() > 0)
				name_first_match = sset.get(1).getSpeciesName();

			if(sset.count() == 0) {
				block_noseq++;
			} else
			if(sset.get(1).getPairwise(query) > threshold) {
				block_nomatches = true;
			} else {
				for(int y = 2; y < sset.count(); y++) {
					Sequence match = sset.get(y);

					if(!match.getSpeciesName().equals(name_first_match))
						break;

					block_size++;
				}

				// is it a real block?
				if(block_size <= 1) {
					block_ambiguous++;
				} else {
					if(block_size == sd.getSpeciesDetailsByName(name_first_match).getSequencesWithValidMatchesCount()) {
						// block_size will be equal to the number of conspecifics
						// ONLY if all the conspecifics are blocked up ...
						// which means that either it's not a proper block,
						// or it's an INCORRECT block!
						if(!name_first_match.equals(name_query)) {
							block_incorrect++;
							is_block_incorrect = true;
						} else 
							block_ambiguous++;
					} else 
					if(block_size == sd.getSpeciesDetailsByName(name_first_match).getSequencesWithValidMatchesCount() - 1) {
						// its the right size for a real block ...
						// it's a real block!
						if(name_first_match.equals(name_query)) {
							block_correct++;
							is_block_correct = true;
						} else
							block_ambiguous++;
					}
					else
						block_ambiguous++;
				}
			}
			// done processing blocks
		
			// set the string
			str_listings.append(name_query);
			if(is_block_correct) {
				str_listings.append("\tcorrect\t");
			} else if(is_block_incorrect) {
				str_listings.append("\tincorrect\t");
			} else if(block_nomatches) {
				str_listings.append("\tno match\t");
				block_nomatch++;
			} else {
				str_listings.append("\tambiguous\t");
			}

			str_listings.append("\n");

			// increment counter
			x++;
		}

		valid_sequences = count_sequences - block_noseq;

		text_main.setText(
				"Sequences:\t" + count_sequences + 
				"\nSequences with atleast one sequence with an overlap of " + Sequence.getMinOverlap() + " base pairs:\t" + valid_sequences +
				"\n\nCorrect identifications according to \"All Species Barcodes\":\t" + block_correct + " (" + percentage(block_correct, valid_sequences) + "%)" +
				"\nAmbiguous according to \"All Species Barcodes\":\t" + block_ambiguous + " (" + percentage(block_ambiguous, valid_sequences) + "%)" +
				"\nIncorrect identifications according to \"All Species Barcodes\":\t" + block_incorrect + " (" + percentage(block_incorrect, valid_sequences) + "%)" + 
				"\nSequences with no match closer than " + percentage(threshold, 1) + "%:\t" + block_nomatch + " (" + percentage(block_nomatch, valid_sequences) + "%)" + 
				"\n\n" + str_listings.toString());

		pd.end();
		
		taxonDNA.unlockSequenceList();
		processingDone = true;
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}
	
	public String getShortName() {		return "All Species Barcodes"; 	}
	public String getDescription() {	return "Determines which sequences can be successfully used as barcodes"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
