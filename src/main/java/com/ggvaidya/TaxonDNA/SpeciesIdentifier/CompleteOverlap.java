/**
 * Determines the largest "complete block" in your dataset. A "complete block"
 * is one where every single sequence in the dataset has no gaps whatsoever. 
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005, 2006

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

import java.io.*;

import java.util.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;

public class CompleteOverlap extends Panel implements UIExtension, Runnable, ActionListener {
	private SpeciesIdentifier	seqId = null;
	private SequenceList	set = null;

	private java.awt.List	list_results = new java.awt.List();

	private TextField	tf_minimumBlockLength = new TextField("300");

	private Checkbox	check_wellDefinedOnly = new Checkbox("Drop all sequences which have more than ");
	private TextField	tf_ambiguousLimit = new TextField("1"); 
	private Label		label_wellDefinedOnly = new Label(" percent ambiguous bases.");

	private Button		btn_Calculate = new Button("Calculate!");

	private Button 		btn_Export_Results = new Button("Export all results");
	private Button 		btn_Export_Fasta = new Button("Export this block as FASTA");

	private int 		step_width = 300;
	private double		ambiguous_percent = 1;

	public CompleteOverlap(SpeciesIdentifier seqId) {
		this.seqId = seqId;
	
		// layouting
		setLayout(new BorderLayout());

		Panel options = new Panel();
		RightLayout rl = new RightLayout(options);
		options.setLayout(rl);

		/*
		Label l = new Label("This module will determine the largest complete block (the largest section without gaps) in your dataset."); 
//		ta.setEditable(false);
		rl.add(l, RightLayout.FILL_3 | RightLayout.STRETCH_X);
		*/

		rl.add(new Label("Find and extract blocks of sequences "), RightLayout.NEXTLINE);
		rl.add(tf_minimumBlockLength, RightLayout.BESIDE);
		rl.add(new Label(" bp long."), RightLayout.BESIDE  | RightLayout.STRETCH_X);

		rl.add(check_wellDefinedOnly, RightLayout.NEXTLINE);
		rl.add(tf_ambiguousLimit, RightLayout.BESIDE);
		rl.add(label_wellDefinedOnly, RightLayout.BESIDE);

		btn_Calculate.addActionListener(this);
		rl.add(btn_Calculate, RightLayout.NEXTLINE | RightLayout.FILL_3);

		add(options, BorderLayout.NORTH);

		// okay
		// BorderLayout...err...CENTER(?) is a Panel with the list and the match in it.
		// The button panel (buttons) will be at the bottom, Flow->right as is usual.

		Panel center = new Panel();
		center.setLayout(new BorderLayout());

		center.add(list_results);

		add(center);

		// buttons!
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Export_Results.addActionListener(this);
		buttons.add(btn_Export_Results);

		btn_Export_Fasta.addActionListener(this);
		buttons.add(btn_Export_Fasta);

		add(buttons, BorderLayout.SOUTH);
	}

	private String getOverlapDisplay(SequenceList sl, int from, int to, DelayCallback delay) {
		// the overlap display is actually really easy
		// we display the consensus, followed by all
		// gap-free and - depending on the state of check_wellDefinedOnly -
		// ambiguity-okay sequences
		//
		double ambiguous_percent = 0.01;

		try {
			step_width = Integer.parseInt(tf_minimumBlockLength.getText(), 10);
			ambiguous_percent = Double.parseDouble(tf_ambiguousLimit.getText()) / 100;
		} catch(NumberFormatException e) {
//			MessageBox mb = new MessageBox(
//					seqId.getFrame(),
			tf_minimumBlockLength.setText("300");
			step_width = 300;
			ambiguous_percent = 0.01;
		}

		int ambiguous_allowed = (int)(ambiguous_percent * (double)(step_width));	// how MANY are allowed?

		sl.lock();
		
		if(delay != null)
			delay.begin();

		StringBuffer buff = new StringBuffer();		

		Iterator i = sl.iterator();
		int x = 0;

		Sequence cons = null;
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			if(cons == null)
				cons = seq;

			x++;
			if(delay != null)
				try {
					delay.delay(x, sl.count());
				} catch(DelayAbortedException e) {
					sl.unlock();
					return "";
				}
			
			Sequence subseq = null;
			try {
				subseq = seq.getSubsequence(from, to);
			} catch(SequenceException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Unable to extract subsequence",
						e.toString());
				mb.go();
				if(delay != null)
					delay.end();
				sl.unlock();
				return "An error occurred while processing this view.";
			}

			// don't write it if there is a gap in this subsequence
			if(subseq.getSequenceWithExternalGaps().indexOf('_') != -1)
				continue;

			// if check_wellDefinedOnly is on, don't let ambiguous sequences through
			if(check_wellDefinedOnly.getState()) {
				if(subseq.getAmbiguous() > ambiguous_allowed)
					continue;
			}

			// look ma! fasta!
			buff.append(">" + subseq.getFullName() + "\n" + subseq.getSequenceWrapped(70) + "\n");
			try {
				cons = cons.getConsensus(subseq);
			} catch(SequenceException e) {
				buff.append("Could not add " + seq + ": internal error (" + e + ")");
			}
		}

		if(delay != null)
			delay.end();

		sl.unlock();

		return buff.toString();
	}
	/* Pad a string to a size */
	private String pad_string(String x, int size) {
		StringBuffer buff = new StringBuffer();
		
		if(x.length() < size) {
			for(int c = 0; c < (size - x.length()); c++)
				buff.append(' ');
			
			return (x + buff);
		} else if(x.length() == size)
			return x;
		else
			return (x.substring(0, size - 3) + "...");
	}

	public void actionPerformed(ActionEvent evt) {
		String str_to_copy = "";
		Button btn = null;

		if(evt.getSource().equals(btn_Calculate)) {
			btn = (Button) evt.getSource();

			if(btn.getLabel().equals("Clear results")) {
				// unfreeze controls
				check_wellDefinedOnly.setEnabled(true);
				tf_minimumBlockLength.setEnabled(true);
				tf_ambiguousLimit.setEnabled(true);

				// clear results
				list_results.removeAll();

				// restore button
				btn.setLabel("Calculate!");
			} else {
				// freeze controls
				check_wellDefinedOnly.setEnabled(false);
				tf_minimumBlockLength.setEnabled(false);
				tf_ambiguousLimit.setEnabled(false);

				btn.setLabel("Clear results");
				new Thread(this, "LargestCompleteBlock").start();			
			}
			return;	
		}

		if(evt.getSource().equals(btn_Export_Results)) {
			// we need to export list_results, basically
			// but where?
			File f = null;

			FileDialog fd = new FileDialog(
				seqId.getFrame(),
				"Where would you like me to save the results to?",
				FileDialog.SAVE
			);

			fd.setVisible(true);	// go, go, gadget power!

			if(fd.getFile() == null)	// cancelled?
				return;
			if(fd.getDirectory() != null)
				f = new File(fd.getDirectory() + fd.getFile());
			else
				f = new File(fd.getFile());

			// now, we just write out list_results to file, in a CSV format
			try {
				PrintWriter pw = new PrintWriter(new FileWriter(f));

				if(check_wellDefinedOnly.getState())
					// well defined only, so ignore the 'sequences' count
					pw.println("from,to,sequences_defined,species,non_singleton_species");
				else
					pw.println("from,to,sequences,sequences_defined,species,non_singleton_species");

				String all_entries[] = list_results.getItems();
				for(int x = 0; x < all_entries.length; x++) {
					String str = all_entries[x];

					// this is FOR YOUR OWN GOOD
					// NOT MY FAULT DON'T BLAME ME
					//
					// __USES_LIST_STRING__
					//
					str = str.replaceFirst(" to ", ",");
					str = str.replaceFirst(": ", ",");
					str = str.replaceFirst(" sequences, ", ",");
					str = str.replaceFirst(" defined sequences across ", ",");
					str = str.replaceFirst(" species \\(with ", ",");
				    str = str.replaceFirst(" non-singleton sequences\\)","");

					pw.println(str);
				}

				pw.close();

				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Done!",
						"I exported " + all_entries.length + " entries into " + f + "."
				);
				
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Error while writing to '" + f + "'",
						seqId.getMessage(Messages.IOEXCEPTION_WRITING, f, e)
				);
			}
		}

		if(evt.getSource().equals(btn_Export_Fasta)) {
			// we need to export the _current_ text.
			// this is kinda easy, since we basically dump ta_matches into a file, converting
			// around a bit so that the results is a kinda-sorta-FASTA file.
			//
			// but is something selected?
			int from = list_results.getSelectedIndex() + 1; 				
			int to = from + step_width - 1;

			if(from == 0)		// means: there's nothing there! (getSelectedIndex() returned -1)
				return;

			// so the segment is [from] to [to] inclusive
			SequenceList sl = seqId.lockSequenceList();
			ProgressDialog pd = ProgressDialog.create(
					seqId.getFrame(),
					"Please wait, writing out overlapping regions ...",
					"Writing out all overlaping regions between " + from + " and " + to + " now. Please be patient!"
					);
			String output = getOverlapDisplay(sl, from, to, pd);
			seqId.unlockSequenceList();

			// but where?
			File f = null;

			FileDialog fd = new FileDialog(
				seqId.getFrame(),
				"Where would you like me to save the results for " + from + " to " + to + " to?",
				FileDialog.SAVE
			);

			fd.setVisible(true);	// go, go, gadget power!

			if(fd.getFile() == null)	// cancelled?
				return;
			if(fd.getDirectory() != null)
				f = new File(fd.getDirectory() + fd.getFile());
			else
				f = new File(fd.getFile());

			// now, we just write out ta_matches in a Fasta format
			try {
				PrintWriter pw = new PrintWriter(new FileWriter(f));

				pw.println(output);
				
				pw.close();

				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Done!",
						"I exported all the entries in this set into " + f + "."
				);
				
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Error while writing to '" + f + "'",
						seqId.getMessage(Messages.IOEXCEPTION_WRITING, f, e)
				);
			}
			
		}		

		/*
		// do we have to copy something, melud?	
		if(btn != null && !str_to_copy.equals("")) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(str_to_copy);
					
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
			}
		}*/
	}
	
	public void dataChanged()	{
		list_results.removeAll();
	}

	/**
	 * @param ambiguous_allowed	the NUMBER of ambiguous base pairs allowed in any sequence for it be 'defined'
	 */
	private int[] sequencesInBlock(SequenceList sl, int from, int to, int ambiguous_allowed) {
		// technicalities:
		// 1.	not all sequences will HAVE content between 'from' and 'to'.
		// 	Sequences are allowed to Be Different etc	
		//
		int results[] = new int[4];		// we have to return three things:
							//	- no of sequences completely in this block
							//	- no of species completely in this block
							//	- no of non-singleton species completely in this block
		int no_sequences = 0;
		int no_species = 0;
		int no_singletons = 0;
		int no_sequences_defined = 0;

		sl.lock();

		try {
			Hashtable ht = new Hashtable();

			Iterator i = sl.iterator();
			while(i.hasNext()) {
				int no_ambiguous_bases = 0;
				Sequence seq = (Sequence) i.next();
				String str_seq = seq.getSequenceWithExternalGaps();

				// now: is seq LONG enough?	
				if(str_seq.length() < to) {	// we need to atleast be able to reach 'to'	
					// if one of the sequence 'ends' in this area,
					// it's an implicit gap, and thus, an exit.
					continue;
				}

				char[] array = new char[to - from + 1];
				str_seq.getChars(from, to, array, 0);

				// ugh, the kludges, the kludges!
				// basically: if we hit a '_', we break
				// out of the for look and set
				// everything_okay to false. If
				// everything_okay is FALSE, we
				// continue the outer while loop.
				boolean sequence_failed = false;
				for(int x = 0; x < to - from; x++) {
					char ch = array[x];

					// we are NOT testing for validity
					// as any Sequence must be valid.
					if(ch == '_') {
						sequence_failed = true;
						break;
					}

					// is this character ambiguous?
					if(Sequence.isAmbiguous(ch))
						no_ambiguous_bases++;
				}

				if(sequence_failed)
					continue;

				// if we're here, 'seq' is valid!
				if(check_wellDefinedOnly.getState()) {
					// skip undefineds!
					if(no_ambiguous_bases > ambiguous_allowed) {
						continue;
					}
				}
				
				// if we're here, we need to add this sequences to the statistics
				no_sequences++;

				if(no_ambiguous_bases <= ambiguous_allowed)
					no_sequences_defined++;

				// KLUDGE, dead ahead!
				//
				// 	first time around (ht.get() == null), we count it
				// 	as a new species, and a singleton, and ht.put(new Integer)
				//	
				//	the SECOND time around, we ht.put(new Object()), and
				//	DECREMENT the singleton count. very hairy, but as a
				//	five line hack, I'll leave it in (atleast until tomorrow :p)
				//
				//	only for sequences with species names, obviously. Non-species name
				//	sequences are not counted.
				//
				if(seq.getSpeciesName() != null) {
					if(ht.get(seq.getSpeciesName()) == null) {
						// not already in list
						no_species++;
						ht.put(seq.getSpeciesName(), new Integer(0));
						no_singletons++;
					} else {
						// already in list
						if(ht.get(seq.getSpeciesName()).getClass().equals(Integer.class)) {
							no_singletons--;
							ht.put(seq.getSpeciesName(), new Object());
						}
					}
				}
			}

			results[0] = no_sequences;
			results[1] = no_species;
			results[2] = no_species - no_singletons;
			results[3] = no_sequences_defined;

			return results;

		} finally {
			sl.unlock();	
		}
	}

	public void run() {
		SequenceList sl = seqId.lockSequenceList();

		if(sl == null)
			return;
		
		if(sl.count() == 0) {
			seqId.unlockSequenceList();
			return;
		}

		// the rules:
		// 1.	minimal memory. really. please.
		// 2.	as fast as possible, GIVEN ONE.
		//
		// the algo:	
		// 1.	create an array of the 'score' for each 'position'.
		// 	The 'score' is either zero (if atleast one of the
		// 	sequences in this area 'failed'), or 1 (if
		// 	everybody is happy here).
		//
		// 	score will be O(N) for size
		// 2.	Loop through the score matrix. Push run pairs onto
		// 	a results stack.
		//
		// 	results stack will be between O(N-1) and O(1) in size.
		//
		// 3.	Loop thorugh the results stack. the largest total distance
		// 	wins!
		//
		
		step_width = 300;
		ambiguous_percent = 0.01;

		try {
			step_width = Integer.parseInt(tf_minimumBlockLength.getText(), 10);
			ambiguous_percent = Double.parseDouble(tf_ambiguousLimit.getText()) / 100;
		} catch(NumberFormatException e) {
//			MessageBox mb = new MessageBox(
//					seqId.getFrame(),
			tf_minimumBlockLength.setText("300");
			step_width = 300;
			ambiguous_percent = 0.01;
		}

		ProgressDialog pd = ProgressDialog.create(
				seqId.getFrame(),
				"Please wait, determining largest complete block ...",
				"I am attempting to determine the largest complete block right now. Sorry for the inconvenience!");
		pd.begin();

		// the number of ambiguous bases allowed is ('ambiguous_percent' x 'step_width')
		int ambiguous_allowed = (int)((double)ambiguous_percent * step_width);
		int no_of_entries = sl.getMaxLength() - step_width + 1;

		int interval = (no_of_entries) / 100;
		if(interval == 0)
			interval = 1;

		list_results.removeAll();
		String defined = "";
		if(check_wellDefinedOnly.getState()) {
			// ONLY the defined sequences	
			defined = " defined";
		}

		for(int x = 0; x < no_of_entries; x++) {
			int results[] = sequencesInBlock(sl, x, x + step_width, ambiguous_allowed);
			int sequences = results[0];
			int species = results[1];
			int non_singletons = results[2];
			int sequences_defined = results[3];

			if(!check_wellDefinedOnly.getState()) {
				// if we're NOT in 'defined only' mode
				// we should report the number of defined as well
				defined = " sequences, " + sequences_defined + " defined";
			}

			// We add 1 to it to make it *indices* and not *offsets*
			//
			// if you edit this line, please search for __USES_LIST_STRING__ and
			// fix those too.
			list_results.add((x + 1) + " to " + (x + step_width) + ": " + sequences + defined + " sequences across " + species + " species (with " + non_singletons + " non-singleton sequences)");

			// do the delay
				try {
					pd.delay(x, sl.getMaxLength() - step_width);
				} catch(DelayAbortedException e) {
					seqId.unlockSequenceList();
					return;
				}
		}

		pd.end();

		seqId.unlockSequenceList();
	}

	public String getShortName() {		return "Complete Overlap"; 	}
	public String getDescription() {	return "Find continuous sections (\"blocks\") without missing bases or external gaps"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
