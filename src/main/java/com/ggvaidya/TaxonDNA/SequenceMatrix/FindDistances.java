/**
 * Finds particular distances. Ideal for looking for all zero percent distances, but we can 
 * really find any arbitrary distance you might want to look for.
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006-07, 2009 Gaurav Vaidya
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
import java.awt.datatransfer.*; // For the Clipboard.
import java.io.*;
import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;

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
        private Button                  btn_Close = new Button("Close");

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
                btn_Close.addActionListener(this);
                buttons.add(btn_Close);
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
                // For every sequence in the set of columns to work on (by default, all of them), we need to 
                // compare against *every* other sequence in the grid.
		//
		Vector vec_colNames = new Vector();

                if(colName_to_use == null)
                    // Add all the column names
                    vec_colNames = new Vector(matrix.getTableManager().getCharsets());
                else
                    // Add just the column we're interested in
                    vec_colNames.add(colName_to_use);  

                // Calculation time!
                ProgressDialog pd = ProgressDialog.create(
                        fr_findDistances,
                        "Please wait, calculating pairwise distances ...",
                        "Calculating all pairwise distances, please wait! This will take a long time."
                );

                // get a SequenceList for each column
                TableManager tm = matrix.getTableManager();
                int count_results = 0;
                int count = 0;
                int total = // Very approximately 
                    vec_colNames.size() * tm.getSequencesCount()
                        *
                    (tm.getColumns().size() * tm.getSequencesCount() - 1)
                ;

                // Results go here.
                Vector results = new Vector();

                pd.begin();
                
                Iterator i = vec_colNames.iterator();
                while(i.hasNext()) {
                        String colName = (String)i.next();
                        
                        SequenceList sl = tm.getSequenceListByColumn(colName);

                        // Go through all the sequences.
                        Iterator i_outer_seq = sl.iterator();
                        while(i_outer_seq.hasNext()) {
                            Sequence seq_outer = (Sequence) i_outer_seq.next();

                            // Go through all the columns.
                            Iterator i_columns = vec_colNames.iterator();
                            while(i_columns.hasNext()) {
                                String compareTo_colName = (String) i_columns.next();

                                SequenceList sl_compareTo = tm.getSequenceListByColumn(compareTo_colName);

                                Iterator i_inner_seq = sl_compareTo.iterator();
                                while(i_inner_seq.hasNext()) {
                                    Sequence seq_inner = (Sequence) i_inner_seq.next();

                                    // Don't compare sequences to themselves.
                                    if(seq_inner.equals(seq_outer))
                                        continue;

                                    count++;
                                    pd.delay(count, total);
                                    double pairwise = seq_outer.getPairwise(seq_inner);

                                    if(from <= pairwise && pairwise <= to) {
                                        Sequence a, b;

                                        a = new Sequence(seq_outer);
                                        b = new Sequence(seq_inner);

                                        a.changeName(a.getDisplayName() + " (from " + colName + ")");
                                        b.changeName(b.getDisplayName() + " (from " + compareTo_colName + ")");

                                        PairwiseDistance pairdist = new PairwiseDistance(a, b);
                                        results.add(pairdist);

                                        count_results++;
                                    }
                                }
                            }
                        }
                }

                // TODO: Get rid of duplicates (X <-> Y and Y <-> X).

                StringBuffer buff = new StringBuffer();
                Collections.sort(results);

                i = results.iterator();
                while(i.hasNext()) {
                    PairwiseDistance pairdist = (PairwiseDistance) i.next();

                    buff.append(pairdist.getSequenceA().getFullName() + "\t" + pairdist.getSequenceB().getFullName() + "\t" + percentage(pairdist.getDistance(), 1) + "%\n");
                }

                String pdm_method = "(unknown)";
                int pdm_method_id = Sequence.getPairwiseDistanceMethod();
                if(pdm_method_id == Sequence.PDM_UNCORRECTED) {
                    pdm_method = "uncorrected pairwise distances";
                } else if(pdm_method_id == Sequence.PDM_K2P) {
                    pdm_method = "K2P distances";
                } else if(pdm_method_id == Sequence.PDM_TRANS_ONLY) {
                    pdm_method = "transversions only";
                }

		text_main.setText(count_results + " sequence pairs found with distances between " + percentage(from, 1) + "% and " + percentage(to, 1) + "%.\nNote that distances were calculated using " + pdm_method + " and sequence overlaps of less than " + Sequence.getMinOverlap() + " bp weren't counted.\n\n" + buff);

		pd.end();
	}

	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(x, y);
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

                if(src.equals(btn_Close)) {
                    // Reset this window.
                    text_main.setText("");

                    // and close it.
                    fr_findDistances.setVisible(false);
                }

                // Don't do anything unless there's text to work with.
                if(text_main.getText().length() > 0) {
                    if(src.equals(btn_Copy)) {
                        try {
                            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                            StringSelection selection = new StringSelection(text_main.getText());

                            clip.setContents(selection, selection);
                        } catch(IllegalStateException ex) {
                            btn_Copy.setLabel("Oops, try again?");
                        }
                        btn_Copy.setLabel("Copy to Clipboard");
                    }

                    if(src.equals(btn_Export)) {
                        File file = null;
                        FileDialog dialog = new FileDialog(
                                fr_findDistances,
                                "Where would like to save these results?",
                                FileDialog.SAVE
                        );
                        dialog.setVisible(true);

                        if(dialog.getFile() == null)
                            return;
                        if(dialog.getDirectory() != null)
                            file = new File(dialog.getDirectory() + dialog.getFile());
                        else
                            file = new File(dialog.getFile());

                        try {
                            PrintWriter pr = new PrintWriter(new FileWriter(file));
                            pr.print(text_main.getText());
                            pr.close();
                        } catch(IOException ex) {
                            MessageBox mb = new MessageBox(
                                    fr_findDistances,
                                    "There was an error writing to " + file,
                                    "An error occured while writing to " + file + ": " + ex.getMessage(),
                                    MessageBox.MB_ERROR
                            );
                            mb.go();
                            return;
                        }
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
