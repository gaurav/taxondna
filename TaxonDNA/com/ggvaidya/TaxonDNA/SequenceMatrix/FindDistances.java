/**
 * Finds particular distances. Ideal for looking for all zero percent distances, but we can 
 * really find any arbitrary distance you might want to look for.
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006-07 Gaurav Vaidya
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *  
 */

package com.ggvaidya.TaxonDNA.SequenceMatrix;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class FindDistances implements WindowListener, ActionListener {
	private SequenceMatrix 		matrix 	= null;			// the SequenceMatrix object
	private Frame 			fr_findDistances 	= null;			// the Frame which we need to display

	private TextArea		text_main = new TextArea(12, 60);

	private TextField		tf_percentLow = new TextField("0.0000");
	private TextField		tf_percentHigh = new TextField("0.0000");
	private Choice			choice_gene = new Choice();
	private Button			btn_Go = new Button("Go!");

	private Button			btn_Copy = new Button("Copy to clipboard");
	private Button			btn_Export = new Button("Export as text");

	private Vector			pdColumns = null;

	/**
	 * Constructor. Sets us up the bomb.
	 */
	public FindDistances(SequenceMatrix matrix) {
		// set up the SequenceMatrix
		this.matrix = matrix;

		// before we set up the UI, we've actually got a bit of a chore to do:
		// we need to add a TableModelListener (of all things!) onto the tableModel.
		// We will use the listener to figure out when we need to flush our
		// datastructures.
//		matrix.getTableManager().getTableModel().addTableModelListener(this);

		// set up 'fr_findDistances'
		fr_findDistances = new Frame("Find Distances");
		fr_findDistances.setBackground(SystemColor.control);
		fr_findDistances.setLayout(new BorderLayout());

		// set up the optioons panel. These control how the output (into, of course, text_main)
		// is handled.
		Panel options = new Panel();
		RightLayout rl = new RightLayout(options);
		options.setLayout(rl);

		rl.add(new Label("Find distances between"), RightLayout.NONE  | RightLayout.FILL_2);
		rl.add(tf_percentLow, RightLayout.NEXTLINE);
		rl.add(new Label("% and"), RightLayout.BESIDE);
		rl.add(tf_percentHigh, RightLayout.NEXTLINE);
		rl.add(new Label("% (inclusive)"), RightLayout.BESIDE);
		btn_Go.addActionListener(this);

		rl.add(new Label("Search within "), RightLayout.NEXTLINE);
		rl.add(choice_gene, RightLayout.BESIDE);
		
		rl.add(btn_Go, RightLayout.NEXTLINE | RightLayout.STRETCH_X | RightLayout.FILL_2);
		fr_findDistances.add(options, BorderLayout.NORTH);

		// Text_main. All results are printed out here for the purview of the customer.
		text_main.setEditable(false);
		fr_findDistances.add(text_main);

		// set up the 'buttons' bar ... which is 'Copy to clipboard' and 'Export as text', our old friends
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER));
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);
		btn_Export.addActionListener(this);
		buttons.add(btn_Export);
		fr_findDistances.add(buttons, BorderLayout.SOUTH);

		// register us as a 'listener'
		fr_findDistances.addWindowListener(this);
	}

	public void go() {
		choice_gene.removeAll();
		choice_gene.add("All genes");
		Iterator i = matrix.getTableManager().getCharsets().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
			choice_gene.add(colName);
		}

		fr_findDistances.pack();
		fr_findDistances.setVisible(true);
	}

	public void displayResults() throws DelayAbortedException {
		// 
		// Step 1. Figure out what we're really (kinda, sorta) looking for.
		//
	
		String colName_to_use = null;
		int selected = choice_gene.getSelectedIndex();
		if(selected != -1 && selected != 0) 	// nothing or the 1st entry ('all genes')
			colName_to_use = (String) matrix.getTableManager().getCharsets().get(selected - 1);

		double from = 0.0;	// percentageBegin
		double to = 0.0;	// percentageEnd

		try {
			from = Double.parseDouble(tf_percentLow.getText()) / 100.0;
		} catch(NumberFormatException e) {
			new MessageBox(
					matrix.getFrame(),
					"What's that number?",
					"I couldn't understand the lower pairwise distance limit, which was '" + tf_percentLow.getText() + "'. I'm going to use 0% instead.").go();

			from = 0.0;
			tf_percentLow.setText("0.0000");
		}

		try {
			to = Double.parseDouble(tf_percentHigh.getText()) / 100.0;
		} catch(NumberFormatException e) {
			new MessageBox(
					matrix.getFrame(),
					"What's that number?",
					"I couldn't understand the upper pairwise distance limit, which was '" + tf_percentHigh.getText() + "'. I'm going to use 0% instead.").go();

			to = 0.0;
			tf_percentHigh.setText("0.0000");
		}

		//
		// Step 2. Get the pairwiseDistances ready.
		//
		// We store the names of the columns in a different Vector
		// note that both pdColumns.get(x) and pdColumns.get(x + 1) refer to vec_colNames.get((x + 1) / 2)	(really?)
		//
		Vector vec_colNames = new Vector();

		if(true || pdColumns == null) {	// since we don't reset pdColumns when the table changes
						// we can't guarantee that the last pdColumns is still valid
			pdColumns = new Vector();

			// get a SequenceList for each column
			TableManager tm = matrix.getTableManager();
			Iterator i = tm.getColumns().iterator();
			while(i.hasNext()) {
				String colName = (String)i.next();
				
				// if colName_to_use is available, skip this distance unless it's that particular colName
				if(colName_to_use != null && !colName.equals(colName_to_use))
					continue;

				SequenceList sl = tm.getSequenceListByColumn(colName);

				vec_colNames.add(colName);
			
				//colNames.add(colName);
				pdColumns.add(new PairwiseDistances(sl, PairwiseDistances.PD_INTRA, 
							new ProgressDialog(
								fr_findDistances,	
								"Please wait, calculating pairwise distances ...",
								"Calculating intraspecific pairwise distances for " + colName)
							));

				pdColumns.add(new PairwiseDistances(sl, PairwiseDistances.PD_INTER, 
							new ProgressDialog(
								fr_findDistances,
								"Please wait, calculating pairwise distances ...",
								"Calculating interspecific, congeneric pairwise distances for " + colName)
							)
						);
			}
		}

		//
		// Step 3. PairwiseDistances are all ready. All we need is to search 'em all ...
		//
		Vector results = new Vector();
		ProgressDialog delay = new ProgressDialog(
				fr_findDistances,
				"Please wait, finalizing results ...",
				"Searching for pairwise distances between " + percentage(from, 1) + "% and " + percentage(to, 1) + "%. Sorry for the delay!");
		delay.begin();
		int count = 0;
		boolean deleteThisTime = false;			// you won't believe what this does

		StringBuffer buff = new StringBuffer();

		Iterator i = pdColumns.iterator();
		while(i.hasNext()) {
			delay.delay(count, pdColumns.size());

			String colName = (String) vec_colNames.get(0);	// this can compressed to 'String colName = vec_colNames.remove(0);'
			if(deleteThisTime)
				vec_colNames.remove(0);			// but why bother?
			deleteThisTime = !deleteThisTime;

			PairwiseDistances pds = (PairwiseDistances) i.next();

//			System.err.println("From/to: " + from + " to " + to + " (aka " + percentage(from, 1) + "% and " + percentage(to, 1) +"%");

			Vector add = pds.getDistancesBetween(from, to);
			Iterator i_dists = add.iterator();
			while(i_dists.hasNext()) {
				PairwiseDistance pd = (PairwiseDistance) i_dists.next();
		
				// simple whole-table-to-half-table splitter
				//
				if(pd.getSequenceA().getFullName().compareTo(pd.getSequenceB().getFullName()) < 0) {
					count++;
					buff.append(colName + "\t" + pd.getSequenceA().getFullName() + "\t" + pd.getSequenceB().getFullName() + "\t" + percentage(pd.getDistance(), 1) + "\n");
				}
			}
		}

		text_main.setText(count + " sequence pairs found with distances between " + percentage(from, 1) + "% and " + percentage(to, 1) + "%.\n" + buff);

		delay.end();
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}


	//
	// Listeners
	// 

	/**
	 * Handles Action events (such as the 'OK' button).
	 */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();

		if(src.equals(btn_Go)) {
			// Calculate, etc.
			try {
				displayResults();
			} catch(DelayAbortedException ex) {
				return;
			}
		}
	}

	// 
	// WindowListener methods
	//
	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {
		fr_findDistances.setVisible(false);
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
}
