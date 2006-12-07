/**
 * Finds particular distances. Ideal for looking for all zero percent distances, but we can 
 * really find any arbitrary distance you might want to look for.
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006 Gaurav Vaidya
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
	private Dialog 			dialog 	= null;			// the Dialog which we need to display

	private TextArea		text_main = new TextArea(12, 60);

	private TextField		tf_percentLow = new TextField("0.0000");
	private TextField		tf_percentHigh = new TextField("0.0000");
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

		// set up 'dialog'
		dialog = new Dialog(matrix.getFrame(), "Find Distances", false);
		dialog.setLayout(new BorderLayout());

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
		rl.add(btn_Go, RightLayout.NEXTLINE | RightLayout.STRETCH_X | RightLayout.FILL_2);
		dialog.add(options, BorderLayout.NORTH);

		// Text_main. All results are printed out here for the purview of the customer.
		text_main.setEditable(false);
		dialog.add(text_main);

		// set up the 'buttons' bar ... which is 'Copy to clipboard' and 'Export as text', our old friends
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER));
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);
		btn_Export.addActionListener(this);
		buttons.add(btn_Export);
		dialog.add(buttons, BorderLayout.SOUTH);

		// register us as a 'listener'
		dialog.addWindowListener(this);
	}

	public void go() {
		dialog.pack();
		dialog.setVisible(true);
	}

	public void displayResults() throws DelayAbortedException {

		// 
		// Step 1. Figure out what we're really (kinda, sorta) looking for.
		//

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
		if(true || pdColumns == null) {	// since we don't reset pdColumns when the table changes
						// we can't guarantee that the last pdColumns is still valid
			pdColumns = new Vector();

			// get a SequenceList for each column
			DataStore ds = matrix.getTableManager().getDataStore();
			Iterator i = ds.getColumns().iterator();
			while(i.hasNext()) {
				String colName = (String)i.next();
				SequenceList sl = ds.getSequenceListByColumn(colName);
			
				pdColumns.add(new PairwiseDistances(sl, PairwiseDistances.PD_INTRA, 
							new ProgressDialog(
								matrix.getFrame(),
								"Please wait, calculating pairwise distances ...",
								"Calculating intraspecific pairwise distances for " + colName)
							));

				pdColumns.add(new PairwiseDistances(sl, PairwiseDistances.PD_INTER, 
							new ProgressDialog(
								matrix.getFrame(),
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
				matrix.getFrame(),
				"Please wait, finalizing results ...",
				"Searching for pairwise distances between " + percentage(from, 1) + "% and " + percentage(to, 1) + "%. Sorry for the delay!");
		delay.begin();
		int count = 0;
		Iterator i = pdColumns.iterator();		

		while(i.hasNext()) {
			delay.delay(count, pdColumns.size());

			PairwiseDistances pd = (PairwiseDistances) i.next();
			Vector add = pd.getDistancesBetween(from, to);

			results.addAll(add);
		}

		//
		// Step 4. We now have a Vector of PairwiseDistance objects. If only we could format
		// them for output and display them, somehow.
		//
		StringBuffer buff = new StringBuffer();
		count = 0;
		i = results.iterator();
		while(i.hasNext()) {
			count++;
			
			delay.delay(count, results.size());

			PairwiseDistance pd = (PairwiseDistance) i.next();

			buff.append(pd.getSequenceA().getDisplayName() + "\t" + pd.getSequenceB().getDisplayName() + "\t" + percentage(pd.getDistance(), 1) + "\n");

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
		dialog.setVisible(false);
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
}
