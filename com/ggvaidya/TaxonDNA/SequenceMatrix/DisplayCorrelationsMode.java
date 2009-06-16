/**
 * This is a DisplayMode which displays the sequences (as returned by dataStore) by
 * their correlations. This is VERY complicated to display, so here's to hoping
 * we Get It Right.
 *
 * CDM is a whole lot like PDM, except that they're nothing at all alike. At all.
 * I can hardly stress this enough. So, CDM was initially forked straight off the
 * PDM code, but we've since gone in and cut a lot of the PDM bits out - except
 * that we'd still like to do PDM-like calculations and all, so it's pretty odd.
 * Oh well, pish. It's just another great Friday evening now.
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

import java.io.*;
import java.util.*;
import java.util.regex.*;

import java.net.*;		// for URLConnection, etc.
import java.lang.reflect.*;	// Reflection

import java.awt.Color;
import java.awt.Component;
import java.awt.event.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DisplayCorrelationsMode extends DisplayMode implements MouseListener, Runnable { 
	private String		seqName_top		=	null;	// the 'reference' sequence, the sequence ON_TOP

	private double		distances[][]		=	null;	// the distances from the seq ON_TOP
	private double		norm_distances[][]	=	null;	// the normalised distances from the seq ON_TOP
	private int		ranks[][]		=	null;	// the ranks from the seq ON_TOP

	public static double	DIST_ILLEGAL		=	-32.0;	// error in program (the distance wasn't set)
	public static double	DIST_NO_COMPARE_SEQ 	=	-64.0;	// no ON_TOP sequence
	public static double	DIST_SEQ_NA		=	-128.0;	// the sequence here is genuinely 'N/A'
	public static double	DIST_NO_OVERLAP		=	-256.0;	// inadequate overlap to the ON_TOP seq
	public static double	DIST_SEQ_ON_TOP		=	-1024.0;// This sequence IS the ON_TOP seq
	public static double 	DIST_CANCELLED		=	-2048.0;// the sequence exists, but is CANCELLED

	private List		scores			=	null;	// A List of scores for the entire row; we 
									// need this to display column #2

	private int		ignore_col		=	-1;	// the column component of the sequence to ignore while calculating correlation
	private int		ignore_row		=	-1;	// the row component of the sequence to ignore while calculating correlation

	private String		override_reference_sequence = null;	// try not to think about this ;-)
									// okay: for testCorrelation(), this will over-ride
									// the reference sequence.

	private class Score implements Comparable {
			private String seqName_top = "";	// the seqname named 'seqName_top' is floated to the top
			private String name = "";
			private double pairwise = 0.0;
			private int constant = +1;		// a multiplier; set the constant to '-1'
								// to sort in opposite-to-natural order
		
			/** 
			 * Full on constructor; sets the constant as well as the name
			 * of this 'score' and its pairwise distance.
			 */
			public Score(String name, double pairwise, int constant) {
				this(name, pairwise);
				this.constant = constant;
			}

			/**
			 * This is the constructor everybody uses because they don't
			 * _want_ to play around with the constant.
			 */
			public Score(String name, double pairwise) {
				this.name = name;
				this.pairwise = pairwise;

				seqName_top = tableManager.getReferenceSequence();
			}
			
			/** Returns the 'name' we use. */
			private String getName()	{	return name;		}

			/** Returns the pairwise distance we use */
			private double getPairwise() 	{	return pairwise;	}

			/** The comparator. The one and onlyiest reason we wrote this class to begin with. */
			public int compareTo(Object o) {
				Score s = (Score) o;

				if(getName().equals(seqName_top))
					return -1;

				if(s.getName().equals(seqName_top))
					return +1;

				// I can't believe it's an expression!
				return constant * (
					(int)(com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(getPairwise()) 
					- com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(s.getPairwise()))
				);

			}
		};

//
// 1.	CONSTRUCTOR
//

	/** 
	 * We need to know the TableManager we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DisplayCorrelationsMode(TableManager tm) {
		tableManager = tm;
		additionalColumns = 3;	// Sequence name, total score, and # of charsets
	}

//
// 2.	INITIALIZATION CODE
//
	/** "Activates" this display on the JTable */
	private TableCellRenderer static_oldRenderer = null;
	public void activateDisplay(JTable table, Object argument) {
		super.activateDisplay(table, argument);

		static_oldRenderer = table.getDefaultRenderer(String.class);
		table.setDefaultRenderer(String.class, new CorrelationsColorRenderer(this));

		// TODO: Do we really need to fire a correlation sort here?
		testCorrelation();	// go!
	}

	/** "Deactivates" this display on the JTable */
	public void deactivateDisplay() {
//		table.setModel(null);		-- CANNOT - you better pick it up on the next activateDisplay()!
		table.setDefaultRenderer(String.class, static_oldRenderer);	// back to before
	}

	public List getAdditionalColumns() {
		Vector v = new Vector();
	
		v.add(0, "Sequence name");
		v.add(1, "Total score");
		v.add(2, "No of charsets");

		return v;
	}

	/** Our standard get-a-list-of-columns function. We don't really have much to do here. */
	public List getSortedColumns(Set colNames) {
		Vector v = new Vector(colNames);
		Collections.sort(v);

		// set list_genes to be a list of all the genes we've got
		list_genes.setListData(new Vector(v));
		sortedColumns = v;

		return (java.util.List ) v;
	}

	/** Our standard get-a-list-of-sequences function. */
	public List getSortedSequences(Set sequences) {
		sortedSequences = new Vector(sequences);

		// step 1: copy whichever sequence we've been told to put on top
		seqName_top = tableManager.getReferenceSequence();

		if(seqName_top == null) {
			if(sortedSequences.size() > 0)
				seqName_top = (String) sortedSequences.get(0);
		} else {
			// we have a seqName, move it to the top
			if(sortedSequences.contains(seqName_top)) {
				sortedSequences.remove(seqName_top);
				sortedSequences.add(0, seqName_top);
			}
		}

		java.util.List columnList = sortedColumns;
		java.util.List sequencesList = sortedSequences;

		if(columnList == null || sequencesList == null || seqName_top == null) {
			// something's not right; bail out
			//throw new RuntimeException("Cannot updateDisplay() - essential variables have not been set!");
			//
			// something is probably wrong because we're shutting down/etc.
			// So nothing to throw a big fuss over.
			return new Vector();		// return an empty 'list'
		}

		String seqName_top = this.seqName_top;		// make a local copy
		if(override_reference_sequence != null)
			seqName_top = override_reference_sequence;
		
		// Okay: NOW, we need to calculate all the distances! Isn't this fun?!
		distances = new double[columnList.size()][sequencesList.size()];	// TODO: catch OutOfMemory here?

		double max[] = new double[columnList.size()];
		Arrays.fill(max, -1.0);
		double min[] = new double[columnList.size()];
		Arrays.fill(min, +2.0);

		// pass 1: calculate all the distances, figure out max and min
		for(int x = 0; x < columnList.size(); x++) {
			String colName = (String) columnList.get(x);

			for(int y = 0; y < sequencesList.size(); y++) {
				String seqName = (String) sequencesList.get(y);

				Sequence seq = tableManager.getSequence(colName, seqName);
				Sequence seq_compare = tableManager.getSequence(colName, seqName_top);

				double dist = DIST_ILLEGAL;
				if(seq_compare == null) {
					dist = DIST_NO_COMPARE_SEQ;
					//System.err.println("No compare seq for (" + colName + ", " + seqName + "); seqName_top = " + seqName_top);
				}
				else if(seqName_top.equals(seqName)) {
					dist = DIST_SEQ_ON_TOP;
				} else if(seq == null) {
					if(tableManager.isSequenceCancelled(colName, seqName))
						dist = DIST_CANCELLED;
					else
						dist = DIST_SEQ_NA;
				}
				else if((dist = seq.getPairwise(seq_compare)) < 0) {
					// illegal!
					dist = DIST_NO_OVERLAP;
				}

				distances[x][y] = dist;

				if(dist >= 0) {
					if(dist > max[x])
						max[x] = dist;

					if(dist < min[x])
						min[x] = dist;
				}
			}
		}
		System.err.println("Pass 1");

		// pass 2: normalise this
		norm_distances = new double[columnList.size()][sequencesList.size()];

		for(int x = 0; x < columnList.size(); x++) {
			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					norm_distances[x][y] = (distances[x][y] - min[x])/(max[x] - min[x]);
				else
					norm_distances[x][y] = distances[x][y];	// save the <0's
			}
		}
		
		System.err.println("Pass 2");

		// pass 3: now, we've got rationalised numbers
		// we need to figure out the average of THESE, and use THIS to sort the
		// sequenceList.
		scores = (List) new Vector();

		for(int y = 0; y < sequencesList.size(); y++) {
			String seqName = (String) sequencesList.get(y);
			
			double totalScore = 0.0;
			int count = 0;

			for(int x = 0; x < columnList.size(); x++) {
				String colName = (String) columnList.get(x);

				if(distances[x][y] >= 0) {
					totalScore += norm_distances[x][y];
					count++;
				}
			}

			totalScore = totalScore/count;

			scores.add(new Score(seqName, totalScore));
		}
		
		System.err.println("Pass 3");

		// sort out the sequence names ...
		Collections.sort(scores);

		double[][] old_distances = distances;		// ditto (see below)
		double[][] old_norm_distances = norm_distances;	// I'm just saving a pointer, I hope
		norm_distances = new double[columnList.size()][scores.size()];	// and writing over the old POINTER,
										// NOT the old data. I hope.
		distances = new double[columnList.size()][scores.size()];	// ditto (see above)
		
		System.err.println("Sort");

		// ... and resort the distances[][] table.
		Iterator i = scores.iterator();	
		int seqIndex = 0;
		while(i.hasNext()) {
			String seqName = ((Score) i.next()).getName();

			int oldIndex = sequencesList.indexOf(seqName);

			for(int x = 0; x < columnList.size(); x++) {
				distances[x][seqIndex] = old_distances[x][oldIndex];
				norm_distances[x][seqIndex] = old_norm_distances[x][oldIndex];
			}

			seqIndex++;
		}
		
		System.err.println("Resort");

		old_distances = null;	// free the array	
		old_norm_distances = null;
		sequencesList = (List) new Vector();

		i = scores.iterator();
		while(i.hasNext()) {
			String seqName = ((Score) i.next()).getName();

			sequencesList.add(seqName);
		}
		
		System.err.println("Iterate");

		// Now that we have a definite list, nicely synced up and
		// everything, we can figure out the rank table!
		//
		ranks = new int[columnList.size()][sequencesList.size()];
		for(int x = 0; x < columnList.size(); x++) {
			LinkedList ll = new LinkedList();		// easier to append

			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					ll.add(new Double(distances[x][y]));
			}

			Collections.sort(ll);	

			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					ranks[x][y] = ll.indexOf(new Double(distances[x][y]));
				else
					ranks[x][y] = (int) Math.floor(distances[x][y]);
			}
		}
		
		System.err.println("Sync");

		System.err.println("CDM:sorted");
		//if(Math.random() > 0.999)
		//	throw new RuntimeException("GO!");

		sortedSequences = sequencesList;
		return sortedSequences;
	}

//
// 2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//
	/**
	 * Gets the value at a particular column. The important
	 * thing here is that two areas are 'special':
	 * 1.	Row 0 is reserved for the column names.
	 * 2.	Column 0 is reserved for the row names.
	 * 3.	(0, 0) is to be a blank box (new String("")).
	 */
        public Object getValueAt(int rowIndex, int columnIndex) {
		String colName = getColumnName(columnIndex);
		String seqName = getRowName(rowIndex);

		// sanity checks
		if(colName == null)
			throw new IllegalArgumentException("Either rowIndex is out of range (rowIndex="+rowIndex+"), or sortedSequenceNames isn't primed.");

		if(seqName == null)
			throw new IllegalArgumentException("Either rowIndex is out of range (rowIndex="+rowIndex+"), or sortedSequenceNames isn't primed.");

		// if it's column name, return the name
		if(columnIndex == 0)
			return seqName;

		// if it's the total length column, return the total length columns
		if(columnIndex == 1)
			return percentage(((Score) scores.get(rowIndex)).getPairwise(), 1.0) + "%";

		// if it's the number of charsets column, return that.
		if(columnIndex == 2)
			return tableManager.getCharsetsCount(seqName) + "";

		// okay, it's an actual 'sequence'
		int col = columnIndex - additionalColumns;
		int row = rowIndex;

		int ndist = (int) Math.round(norm_distances[col][row] * 100);
		int rank = ranks[col][row];
		double dist = distances[col][row];

		// there ARE ways to do this properly, but I just can't be arsed right now
		// TODO do this properly
		if(dist >= 0) {
			return ndist + "% #" + rank+ " (" + percentage(dist, 1.0) + "%)";
		} else if(dist == DIST_ILLEGAL) {
			return "(N/A - SUPERbug)";
		} else if(dist == DIST_NO_COMPARE_SEQ) {
			return "(No seq to compare)";
		} else if(dist == DIST_SEQ_NA) {
			return "(N/A)";
		} else if(dist == DIST_NO_OVERLAP) {
			return "(NO OVERLAP)";
		} else if(dist == DIST_SEQ_ON_TOP) {
			return "(ON TOP)";
		} else if(dist == DIST_CANCELLED) {
			return "(EXCISED)";
		} else {
			return "(N/A - unknown)";
		}
	}

	/** Convenience function */
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	public void setValueAt(String colName, String rowName, Object aValue) {
		// nothing to do, to save his life, call his wife in
		// nothing to say, but what the hey, how's your boy been
		// nothing to do, it's up to you
		// I've got nothing to say, but it's okay
	}

	public String getValueAt(String colName, String rowName, Sequence seq) {
		// ignore
		return null;
	}

	/**
	 * Event: somebody double clicked in the mainTable somewhere
	 */
	public void doubleClick(MouseEvent e, int col, int row) {
		if(row > 0 && col != -1 && col >= additionalColumns) {
			// it's, like, valid, dude.
			String colName = getColumnName(col);
			String rowName = getRowName(row);

			// now, now, don't be STUPID.
			Sequence seq = tableManager.getCancelledSequence(colName, rowName);

			if(seq != null) {
				tableManager.toggleCancelled(colName, rowName);
				testCorrelation();
				tableManager.selectSequence(colName, rowName);
			}
		}
	}
	
//
// CORRELATION-SPECIFIC UI
//
	private JList		list_genes =		new JList();
	private JList		list_sequences =	new JList();
	private Hashtable	ht_matches =		new Hashtable();	// Hash String(geneName) -> Vector[String](descriptions)

	public JPanel getAdditionalPanel() {
		JPanel p = new JPanel();
		p.setLayout(new java.awt.BorderLayout());

		Vector v = new Vector();
		v.add("Please select a gene to view the most improving sequences for that gene");
		list_sequences.setListData(v);

		list_genes.addMouseListener(this);
		list_sequences.addMouseListener(this);

		p.add(new JScrollPane(list_genes), java.awt.BorderLayout.WEST);
		p.add(new JScrollPane(list_sequences));

		return p;
	}

	public void setStatusBar(StringBuffer buff) {
		double r2 = getAverageR();
		if(r2 > -1) {
			buff.append("Average intercolumn correlation coefficient = " + r2 + ".");
		}
	}
	
	/** 
	 * This (if you'll believe it!) is the callback used by the
	 * lists to let us know what was selected.
	 */
	public void mouseClicked(MouseEvent e) {
		// SIGH
		super.mouseClicked(e);

		if(e.getSource().equals(list_genes)) {
			// genes changed - update list_sequences 
			int index = list_genes.locationToIndex(e.getPoint());	// freak.

			if(index < 0)
				return;

			String colName = (String) sortedColumns.get(index);

			if(colName == null)
				return;

			if(ht_matches.get(colName) != null) {
				list_sequences.setListData((Vector) ht_matches.get(colName));
			}
		} else if(e.getSource().equals(list_sequences)) {
			if(e.getClickCount() == 2) {
				// double click!
				//
				// okay, what we've got to do now is to:
				// 1.	figure out the colName/seqName
				// 2.	select it on the main table
				// 3.	change it's nature somehow. Flashing yellow is
				// 	actually a very nice UI gimmick for this, but
				// 	I'm not sure how easy that would be to pull
				// 	off :)
				//
				String colName = (String) list_genes.getSelectedValue(); 
				if(colName == null)
					return;

				String entry = (String) list_sequences.getSelectedValue();
				if(entry == null)
					return;	

				int from = entry.indexOf(':') + 1;	// don't count the ':'
				int to = entry.indexOf('~');

				if(from < 0 || to < 0 || to < from)
					return;
				String seqName = entry.substring(from, to).trim();

				tableManager.selectSequence(colName, seqName);
			}
		}
	}
	public void mouseEntered(MouseEvent e) {}
 	public void mouseExited(MouseEvent e) {}
 	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
	}
	

//
// MATHEMATICS BACKING THE CORRELATION CALCULATIONS
//
	double[][] correlations = null;
	/**
	 * Calculates and returns the correlation between two columns;
	 * in this case indicated by indices into the arrays used by
	 * us.
	 */
	public double getCorrelation(int x, int y) {
		// only do a half-rectangle (a triangle)
		if(x == y)
			return 1.0;		// identical columns are perfectly correlated

		if(x > y)
			return getCorrelation(y, x);		// only do a triangle

		// okay, he're how it work: 
		// 0.	HACK TIME! Okay, here's how we're REALLY going to do it:
		// 	1.	The first time we're run, we generate an correlation
		// 		matrix: column vs. column correlations for everything
		// 		on the board. This matrix is obviously reset every
		// 		time getSortedSequences() is called (alternatively,
		// 		we could do it every time ignore_row and ignore_col
		// 		are at -1).
		//
		// 	2.	Every subsequent time we're called (with ignore_row
		// 		and ignore_col not being set to -1), we ONLY recalculate
		// 		the part of the matrix which is relevant to the
		// 		ignore_row/col. For a normal correlation matrix, this
		// 		is every entry in that column's ROW and COLUMN.
		//
		// See below for the actual (non-pre-calculated algorithm)
		//
		int N = sortedColumns.size();
		if(ignore_col == -1 || correlations == null || correlations[0].length < N) {

			correlations = new double[N][N];

			for(int c = 0; c < N; c++)
				Arrays.fill(correlations[c], -1.0);
		}

		if(ignore_col != -1) {
			// we have an ignore_row/col situation
			// do we have to recalculate ourselves?
			if(x != ignore_col && y != ignore_col) {
				// we don't know which order we'll come up with next
				if(correlations[x][y] != -1)
					return correlations[x][y];
				if(correlations[y][x] != -1)
					return correlations[y][x];
			}
		}


		//
		// 1.	walk pairwise along the column. 
		//
		// 	For every valid match:
		// 	1.	increment N
		// 	2.	sum += x
		// 	3.	sum_2 += (x * x)
		// 	etc.
		// ?
		//
		if(distances == null)
			return -1.0;

		int n = 0;

		double sum_x = 0;
		double sum_y = 0;

		double sum_x2 = 0;
		double sum_y2 = 0;

		double sum_xy = 0;

		if(distances[x][0] != DIST_SEQ_ON_TOP || distances[y][0] != DIST_SEQ_ON_TOP)
			return -2;	// error

		for(int c = 1; c < sortedSequences.size(); c++) {
			if(c == ignore_row) {
				if(x == ignore_col || y == ignore_col)
					continue;
			}

			double d_x = distances[x][c];
			double d_y = distances[y][c];

			if(d_x < 0 || d_y < 0)
				continue;

			// valid!
			n++;

			sum_x += d_x;
			sum_x2 += (d_x * d_x);

			sum_y += d_y;
			sum_y2 += (d_y * d_y);

			sum_xy += (d_x * d_y);
		}

		double variable_x = (n * sum_x2) - (sum_x * sum_x);
		double variable_y = (n * sum_y2) - (sum_y * sum_y);		

		// since these cases mean that there is inadequate information
		// for a match (too many N/A sequences, basically) this is
		// logically the same as there being no correlation between
		// this pair of numbers
		if(variable_x <= 0)
			return 0.0;

		if(variable_y <= 0)
			return 0.0;

		double r = (
				((double)n * sum_xy) - (sum_x * sum_y)
			) 
			/ 
			(
			 	Math.sqrt(
					variable_x
				) 
				*
				Math.sqrt(
					variable_y
				)
			);

		correlations[x][y] = r;

		return r;
	}

	public double getAverageR() {
		int N = sortedColumns.size();

		//System.err.println("avg_r begin");
		if(N == 0 || distances == null)
			return -2.0;		// don't try this unless you've got atleast one column

		if(N == 1)
			return -1.0;		// don't try this unless you've got atleast two columns

		//System.err.println("avg_r middle");

		double dist[][] = dist = (double[][]) distances.clone();
		double R_iy[] = new double[N - 1];
		double R_ii[][] = new double[N - 1][N - 1];
		double total = 0;
		long n = 0;

		// calculate R_ii, the matrix of correlations amongst Xs
		for(int y = 0; y < N; y++) {
			for(int x = 0; x < N; x++) {
				if(y < x)
					continue;

				double c = getCorrelation(y, x);
				if(c < -1)		// error
					continue;
				total += c;
				n++;
			}
		}
		
		//System.err.println("avg_r end");

		if(n == 0)
			return -1;

		return (total / (double)n);
	}

	public void testCorrelation() {
		Thread t = new Thread(this, "DisplayCorrelationsMode");
		try {
			t.start();
			t.join();		// wait until run() finishes
		} catch(InterruptedException e) {
			System.err.println("Gotcha!: " + e);
		}
	}

	public void run() {
		// here's what we do ...
		// 1. 	cancel every single sequence, one by one (note that this actually means
		// 	we UNCANCEL sequences which are already cancelled, which does kinda sorta
		// 	make something of a logical sense to me).
		// 2.	we calculate the getAverageR() for the entire dataset as a whole, storing
		// 	the initial, final and difference.
		// 3.	we sort them by difference (we can actually use Score to do this!)

		// what's the initial R2?
		double r2_initial = -1;

		ProgressDialog delay = new ProgressDialog(
				tableManager.getFrame(),
				"Please wait, calculation correlations ...",
				"Correlations between genes are being calculated. Sorry for the delay!");
		delay.begin();

		if(sortedSequences == null || sortedSequences.size() == 0)
			return;				// can't do without sequences to test

		if(sortedColumns == null || sortedColumns.size() == 0)
			return;				// can't do without columns to test

		// okay, we're committed!
		Vector v_list = new Vector();
		v_list.add("Please select a gene to view the most improving sequences for that gene");
		list_sequences.setListData(v_list);

		String initial_seqName = (String) sortedSequences.get(0);

		ignore_col = -1;
		ignore_row = -1;

		Hashtable ht_scoresPerGene = new Hashtable();
		Iterator i = sortedColumns.iterator();
		while(i.hasNext()) {
			String name = (String) i.next();

			ht_scoresPerGene.put(name, new Vector());
		}

		Set sequencesSet = new HashSet(sortedSequences);
		Vector initialSequencesList = new Vector(sortedSequences);
		for(int z = 0; z < initialSequencesList.size(); z++) {
			String seqName_z = (String) initialSequencesList.get(z);

			override_reference_sequence = seqName_z;
			System.err.println("Sorting z=" + seqName_z);
			try {
				delay.delay(z, initialSequencesList.size());
			} catch(DelayAbortedException e) {
				// ignore
			}
			getSortedSequences(sequencesSet);	// we don't need it, just the tables
			//updateDisplay();
		
			r2_initial = 	getAverageR();

			for(int y = 0; y < sortedColumns.size(); y++) {
				String colName = (String) sortedColumns.get(y);

				for(int x = 1; x < sortedSequences.size(); x++) {
					String seqName = (String) sortedSequences.get(x);

					// cancel (or uncancel) this sequence
					ignore_col = y;
					ignore_row = x;

					// what's the R_squared now?
					double r2_final =	getAverageR();
					double diff = 		r2_final - r2_initial;

//					String name = seqName_z + ":" + seqName + ":" + colName + "\t" + (float)r2_initial + "\t" + (float)r2_final + "\t(" + (float) diff + ")";
//					TODO: we will need a way to 'fall back', in case the system's font doesn't
//					support Unicode characters 2191-94.
//
					String str_direction = "\u2191";	// up_arrow
					if(diff < 0) {
						str_direction = "\u2193";	// down_arrow
						diff = -diff;			// make the different positive
					}
					if(identical(diff, 0.0))
						str_direction = "\u2194";	// '<->' symbol
					String name = seqName_z + ":" + seqName + " ~ " + (float)r2_final + " (" + (float)r2_initial + "" + str_direction + "" + (float) diff + ")";

					((Vector)ht_scoresPerGene.get(colName)).add(new Score(name, r2_final, -1));
//					vec_scores.add(new Score(name, diff, -1));
	
					// uncancel (or recancel) this sequence
					ignore_col = -1;
					ignore_row = -1;
				}
			}
		}

		override_reference_sequence = null;
		getSortedSequences(sequencesSet);	// we don't need it, just re-initialize to
							// what we're displaying
		ignore_col = -1;
		ignore_row = -1;
		r2_initial = getAverageR();

		// sort 'em!
		ht_matches = new Hashtable();

		i = ht_scoresPerGene.keySet().iterator();
		while(i.hasNext()) {
			String gene = (String) i.next();
			Vector matches = new Vector();

			int NO_SEQUENCES = 10;

			Vector v = (Vector) ht_scoresPerGene.get(gene);
			Collections.sort(v);
			
			Iterator i2 = v.iterator();
			int x = 0;
			while(i2.hasNext()) {
				Score s = (Score) i2.next();

				if(s.getName().indexOf("0.0)") != -1) {	// FIXME: TODO
					continue;
				}

				if(x >= NO_SEQUENCES)
					break;
				x++;

				matches.add(x + ". " + s.getName()) ;
			}

			ht_matches.put(gene, matches);
		}

		System.err.println("CDM:testCorrelation");
		delay.end();
		
		//MessageBox mb = new MessageBox(
		//		matrix.getFrame(),
		//		"Top 10 most improving sequences",
		//		"The following sequences most improve the total R2 when cancelled (or uncancelled):\n" + buff.toString());
		//mb.go();
	}

	public boolean identical(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.identical(x, y);
	}
}

class CorrelationsColorRenderer extends DefaultTableCellRenderer
{
	DisplayCorrelationsMode dcm = null;

	public CorrelationsColorRenderer(DisplayCorrelationsMode dcm) {
		this.dcm = dcm;
	}	

	/**
	 * Returns the colour for 'value' (which should be in the range [0, 1] inclusive)
	 */
	public Color getXORColor(Color basicColor, Color bg) {
		Color textColor = Color.WHITE;
		
		if(basicColor.equals(Color.RED))
			return Color.BLACK;

		// assume basicColor is BLACK
		if(bg.getRed() > 128) {
			return Color.BLACK;
		} else {
			return Color.WHITE;
		}
/*
		return new Color(
				bg.getRed() ^ textColor.getRed(), 
				bg.getGreen() ^ textColor.getGreen(),
				bg.getBlue() ^ textColor.getBlue()
		);
		*/
	}

	/**
	 * Returns the colour for 'value' (which should be in the range [0, 1] inclusive)
	 */
	public Color getColor(Color basicColor, double value) {
		if(basicColor.equals(Color.BLACK)) {
			// the XOR color get screwed up on HSB(0, 0, ~0.5),
			// so we limit the range of black to within this area
//			return Color.getHSBColor(0.0f, 0.0f, 1.0f - ((float)value * 0.4f));	
			return Color.getHSBColor(0.0f, 0.0f, 1.0f - (float)value);	// without limit
				
		} else 	
			// default: RED
			return Color.getHSBColor(0.0f, (float)value, 1.0f);
	}	
 
	public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int col)
    {
	// what would defaulttablecellrenderer do?
        JComponent comp = (JComponent) super.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,col);

	// omfg it reuses the same JComponent!
	comp.setOpaque(false);
	comp.setForeground(Color.BLACK);
	comp.setBackground(Color.WHITE);

	if(row < 0 || col < dcm.additionalColumns) {
		// if the row is invalid, or the column is not one of the sequence columns
		return comp;
	}

	Color basicColor = Color.BLACK;
	String val = (String) value;

	if(isSelected == true)
		basicColor = Color.RED;

	if(val.equals("(N/A)"))
		return comp;

	if(val.equals("(N/A - bug)"))
		return comp;

	if(val.indexOf(':') != -1)
		val = val.substring(0, val.indexOf(':'));
	if(val.indexOf('%') != -1)
		val = val.substring(0, val.indexOf('%'));
	val = val.replaceAll("\\%", "");
	val = val.replaceAll("\\:", "");

	try {
		// double d = Double.parseDouble(val)/100.0;
		//
		// okay, NOW we're using ranks
		double d = Double.parseDouble(val)/100; //dpm.getRowCount();

		comp.setOpaque(true);

		if(d < 0 || d > 1) {
			comp.setBackground(getColor(basicColor, 0.0)); 
			comp.setForeground(getXORColor(basicColor, comp.getBackground()));
		} else {
			comp.setBackground(getColor(basicColor, d)); 
			comp.setForeground(getXORColor(basicColor, comp.getBackground()));
		}

	} catch(NumberFormatException e) {
		// ah, ferget it
	}

        return comp;
    }
}
