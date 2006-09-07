/**
 *
 * A UIExtension which compares a given sequence against the whole set of sequences.
 * We have a public function - querySequence(Sequence) - which we can use to query 
 * a sequence.
 *
 * NOTES: SortedSequenceList 
 *	We maintain our very own SortedSequenceList in our own class. This will
 *	pretty obviously (and pretty quickly) fall out of sync with SpeciesIdentifier's
 *	list. THIS MEANS THAT IF YOU MODIFY A FILE IN TAXONDNA, THE RESULTS OF
 *	THE SEARCH WILL USE THE LAST SET. Click on "Query", and everything will 
 *	make sense again.
 *
 * 	I am not locking our local SSL. PLEASE NOBODY TOUCH THIS EVER PLEASE OH PLEASE.	
 *
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
import java.text.*;		// for MessageFormat

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class QuerySequence extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {	
	private static final long serialVersionUID = 8522777324004811805L;

	private SpeciesIdentifier	seqId;

	private SortedSequenceList	sset;

	private TextArea	text_sequence = new TextArea();		
	private java.awt.List	list_result = new java.awt.List();
	private TextArea	text_score = new TextArea();
	private TextArea	text_compare = new TextArea(3,60);

	private Map		map_index;

	private Button		btn_Query = new Button("Query");
	private Button		btn_Copy = new Button("Copy to Clipboard");

	public QuerySequence(SpeciesIdentifier view) {
		super();

		seqId = view;
		
		// create the panel
		setLayout(new BorderLayout());

		// we have two panels, one to provide for the "enter your sequence here"
		// area, and one for the "results and scores" area.
		Panel 	enterSeqHere = new Panel(); 
		enterSeqHere.setLayout(new BorderLayout());
		enterSeqHere.add(new Label("Please enter your query sequence here:"), BorderLayout.NORTH);
		text_sequence.setEditable(true);
		text_sequence.setFont(new Font("Monospaced", Font.PLAIN, 12));
		enterSeqHere.add(text_sequence);
		btn_Query.addActionListener(this);
		enterSeqHere.add(btn_Query, BorderLayout.SOUTH);
		add(enterSeqHere, BorderLayout.NORTH);
		
		Panel resultsAndScores = new Panel();
		resultsAndScores.setLayout(new BorderLayout());
		list_result.add("                                  ");
		list_result.addItemListener(this);
		resultsAndScores.add(list_result);
		text_score.setEditable(false);
		resultsAndScores.add(text_score, BorderLayout.EAST);

		text_compare.setEditable(false);
		text_compare.setFont(new Font("Monospaced", Font.PLAIN, 12));
		resultsAndScores.add(text_compare, BorderLayout.SOUTH);
		
		add(resultsAndScores);
		
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);
		
		add(buttons, BorderLayout.SOUTH);
	}

	/* Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		list_result.removeAll();
		text_score.setText("");
		text_sequence.setText("");
		text_compare.setText("");
	}

	public void run() {
	}


	public boolean addCommandsToMenu(Menu menu) {
		// should add some
		// but for now
		return false;
	}
	
	public void setSequence(String string_sequence) {
		text_sequence.setText(string_sequence);

			Sequence query = null;
			map_index = new Hashtable();
			
		SequenceList set = seqId.lockSequenceList();
		if(set == null) {
			seqId.unlockSequenceList();
			return;		// we have nothing
		}

		sset = new SortedSequenceList(set);
			
			try {
				String x = string_sequence;
				StringBuffer buff = new StringBuffer();

				for(int c = 0; c < x.length(); c++) {
					if(!Character.isWhitespace(x.charAt(c)))
						buff.append(x.charAt(c));
				}
				
				query = new Sequence("Query", buff.toString());
				seqId.lockSequenceList();
				try {
					sset.sortAgainst(query, new ProgressDialog(seqId.getFrame(), "Please wait, calculating distances ...", "All the pairwise distances are being calculated. Please wait.", 0));
					seqId.unlockSequenceList();
				} catch(DelayAbortedException e) {
					seqId.unlockSequenceList();
					sset = null;
					return;
				}				
			} catch(SequenceException e) {
				list_result.removeAll();
				list_result.add("Query is an incorrect sequence");
				text_score.setText("The technical description of this problem is:\n" + e);
				return;
			}

			list_result.removeAll();
			
			int index = 0;
			MessageFormat format = new MessageFormat("({0,number,##0.0000}%) {1}");
			for(int x = 0; x < sset.count(); x++) {
				if(sset.getDistance(x) < 0) { // invalid?!
					continue;
				}
				Object[] args = new Object[2];
				args[0] = (Object) new Double(sset.getDistance(x) * 100);
				args[1] = (Object) sset.get(x).getName();

				map_index.put(new Integer(index), new Integer(x));
				index++;
				list_result.add(format.format(args));
			}

			displaySummary();

		seqId.unlockSequenceList();
	}
		
	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();

		if(cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_score.getText());
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");
		}

		if(cmd.equals("Query")) {
			setSequence(text_sequence.getText());
		}
	}		

	// what to display in the text_main
	public void displaySummary() {
		String str = "Please select one of the matched sequences for more details.";

		text_score.setText(str);
	}

	public void displayDetails(Sequence seq) {
		StringBuffer str = new StringBuffer();
		
		if(seq != null && sset != null) {
			Sequence query = sset.getQuery();
			double pairwise = seq.getPairwise(query);
			String str_pairwise = new String("" + percentage(seq.getPairwise(query), 1));
					
			str.append("Name of sequence: " + seq.getName() + "\n\n");
			
			str.append("This sequence has a pairwise distance of " + str_pairwise + "% from the query sequence.\n\n");
			

			if(seqId.getExtension("Pairwise Summary") != null) {
				PairwiseSummary ps = (PairwiseSummary)seqId.getExtension("Pairwise Summary");

				PairwiseDistribution pd_intra = ps.getPD_intra();
				PairwiseDistribution pd_inter = ps.getPD_inter();

				if(pd_intra == null || pd_inter == null) {
					ps.dataChanged();
					
					pd_intra = ps.getPD_intra();
					pd_inter = ps.getPD_inter();
					
				} else {
					pd_intra = ps.getPD_intra();
					pd_inter = ps.getPD_inter();

					double prob_intra = 100 - percentage(pd_intra.getBetween(0, pairwise), pd_intra.countValidComparisons());
					double prob_inter = percentage(pd_inter.getBetween(0, pairwise), pd_inter.countValidComparisons());

					if(pd_intra.countValidComparisons() > 0) 
						str.append(prob_intra + "% of intraspecific distances are larger than " + str_pairwise + "%.\n");
				
					if(pd_inter.countValidComparisons() > 0) 
						str.append(prob_inter + "% of intrageneric but interspecific distances are smaller than " + str_pairwise + "%.\n");
	
				}
			} else {
				str.append("The pairwise distribution module (PairwiseSummary) has not been loaded into this program. I need that program to calculate pairwise distances.\n\nPlease recompile this program.");
			}
					
			str.append("\nNon-matching nucleotides:\t" + (seq.getActualLength() - seq.countIdentical(query)) + "\tbase pairs");
			str.append("\nOverlap:\t" + seq.getSharedLength(query) + "\tbase pairs");

			// Now DO the comparison - again!
			// how irritating ...
			char ch1, ch2;
			int length = Math.max(seq.getLength(), query.getLength());
			StringBuffer str_compare = new StringBuffer();
			for(int x = 0; x < length; x++) {
				if(x >= query.getSequence().length()) {
					ch1 = '-';
				} else {
					ch1 = query.getSequence().charAt(x);
				}
				if(x >= seq.getSequence().length()) {
					ch2 = '-';
				} else {
					ch2 = seq.getSequence().charAt(x);
				}

				str_compare.append(Sequence.getmatch(ch1, ch2));
			}
			text_compare.setText("Query:   " + query.getSequence() + "\n" + "         " + str_compare.toString() + "\n" + "Compare: " + seq.getSequence());
		}	
		
		text_score.setText(str.toString());
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}
	
	// ItemListener
	public void itemStateChanged(ItemEvent e) {
		if(e.getStateChange() == ItemEvent.SELECTED) {
			Integer integer = (Integer)e.getItem();
			int i = integer.intValue();

//			if(i == 0) { // summary
//				displaySummary();
//			} else {
				if(i < sset.count()) {
					displayDetails(sset.get(((Integer)map_index.get(integer)).intValue()));
				} else {
					// i dunno what this is, and i don't care!
				}
			}
//		}
	}
	
	// UIExtension stuff
	public String getShortName() { return "Query against sequences"; }
	
	public String getDescription() { return "Query a sequence against the other sequences in this dataset"; }
	
	public Frame getFrame() { return null; }
	
}
