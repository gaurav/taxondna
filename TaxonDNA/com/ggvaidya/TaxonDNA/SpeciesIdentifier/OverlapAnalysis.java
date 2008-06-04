/**
 * OverlapAnalaysis:
 * Analyses the overlap distances - all of them.
 *
 * What does this mean? At the moment, we just calculate
 * paired overlaps (seqA vs seqB: 300bp overlap).
 *
 * Mind you, we only need intraspecific overlaps for Michael's fix.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2008 Gaurav Vaidya
    
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
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class OverlapAnalysis extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {
	private SpeciesIdentifier	seqId = null;
	private SequenceList		list = null;

	private TextArea	text_main = new TextArea();

	private Button		btn_Calculate = new Button("Calculate overlaps for all conspecifics");
	private Button		btn_Copy;

	private String		display_strings[];

	public OverlapAnalysis(SpeciesIdentifier seqId) {
		this.seqId = seqId;
		
		setLayout(new BorderLayout());

		Panel top = new Panel();
		RightLayout rl = new RightLayout(top);
		top.setLayout(rl);

		btn_Calculate.addActionListener(this);
		rl.add(btn_Calculate, RightLayout.NEXTLINE | RightLayout.FILL_4);

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

			if(item <= display_strings.length) {
				text_main.setText(display_strings[item]);
			} else {
				text_main.setText("Invalid item");
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

		if(e.getSource().equals(btn_Calculate)) {
			new Thread(this, "OverlapAnalysis").start();
		}
	}
	
	public void dataChanged() {
		list= seqId.lockSequenceList();

		if(list == null) {
			text_main.setText("No sequences loaded.");
		} else {
			text_main.setText("Please press the 'Calculate' button to conduct an overlap analysis.");
		}

		seqId.unlockSequenceList();
	}

	private double average(Vector v) {
		double sum = 0.0;
		int count = 0;
		
		Iterator i = v.iterator();
		while(i.hasNext()) {
			Double d = (Double) i.next();
			double val = d.doubleValue();
			if(val > -1) {
				sum += d.doubleValue();
				count++;
			}
		}

		return (sum/count);
	}

	private void append_to_hash_table(Hashtable ht, Object key, Object value) {
		if(ht.get(key) == null) {
			Vector v = new Vector();
			v.add(value);
			ht.put(key, v);
		} else
			((Vector) ht.get(key)).add(value);
	}

	public void run() {
		// Okay we need to figure out a lot of Vectors to keep track of numbers as we go, and then average them out.
		
		// Init!
		SequenceList list = seqId.lockSequenceList();

		// First off, we need SSLs. Lots of SSLs.
		// That means PairwiseDistances
		ProgressDialog delay = new ProgressDialog(
				seqId.getFrame(),
				"Please wait, calculating overlaps ...",
				"Overlaps for this dataset are being calculated. Sorry for the delay!");

		try {
			// for each sequence in the dataset, we need:
			// 1.	a smallest inter/congen distance
			// 2.	an average inter/congen distance	
			delay.begin();
			
			// To store the results
			StringBuffer buff = new StringBuffer();
			buff.append("Name\tName\tOverlap (bp)\n");

			// data structures
			for(int y = 0; y < list.count(); y++) {
				delay.delay(y, list.count());
				
				Sequence seq = (Sequence) list.get(y);

				// Only do the half-table: each sequence is compared against the sequence
				// in front of it.
				for(int x = y; x < list.count(); x++) {
					Sequence seq2 = (Sequence) list.get(x);

					if(seq2 == seq) continue;	// Don't compare with itself
					
					if(seq2.getSpeciesName().equals(seq.getSpeciesName())) {
						// Conspecifics only!
						buff.append(seq.getFullName() + "\t" + seq2.getFullName() + "\t" + seq.getOverlap(seq2) +"\n");
					}
				}
			}

			// Done!
			text_main.setText(buff.toString());
			
			delay.end();
			delay = null;
 		} catch(DelayAbortedException e) {
			return;
		} finally {
			seqId.unlockSequenceList();
			if(delay != null)		// this will actually work! trust me.
				delay.end();
		}
		
		return;
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}
	
	public String getShortName() {		return "Overlap Analysis"; 	}
	public String getDescription() {	return "Calculates overlap between conspecific sequences"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
