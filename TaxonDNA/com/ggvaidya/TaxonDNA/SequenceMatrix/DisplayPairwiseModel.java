/**
 * This is a TableModel which displays the sequences (as returned by dataStore) by
 * their pairwise distances. This is actually pretty complicated, so we'll be
 * talking to Prefs and to the use to figure out how to work this. Atleast now
 * we're in a class by ourselves, so it should be a bit easier to work with.
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

import java.io.*;
import java.util.*;
import java.util.regex.*;

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

public class DisplayPairwiseModel implements TableModel {
	//
	// Variables we'll need to track
	//
	private SequenceMatrix 	matrix 			= 	null;	
	private DataStore	dataStore 		=	null;
	public static final int additionalColumns 	= 	3;	// Sequence name, total length, and # of charsets

	private List		columnList 		= 	null;
	private List		sequencesList		=	null;
	private String		seqName_top = null;		// the 'selected' sequence

	private Hashtable	pdm_hash_maxDist = null;	// a hash of maximum distances in a column
	private Hashtable	pdm_hash_minDist = null;	// a hash of minimum distances in a column

//
// 1.	CONSTRUCTOR
//

	/** 
	 * We need to know the SequenceMatrix we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DisplayPairwiseModel(SequenceMatrix sm, DataStore ds) {
		matrix = sm;
		dataStore = ds;
	}

//
// 2.	INITIALIZATION CODE
//
	/**
	 * Sets the dataStore into PairwiseDistanceMode. Please keep in sync
	 * with exitPairwiseDistanceMode(), which should undo EVERYTHING this
	 * method does.
	 *
	 * @return true, if we're now in PDM
	 */
	public boolean enterPairwiseDistanceMode(String colNameOfInterest) {
		// we need atleast one sequence name to do this
		if(dataStore.getSequences().size() == 0) {
			new MessageBox(
					matrix.getFrame(),
					"You need atleast one sequence to run this analysis!",
					"I can't run a pairwise distance analysis without atleast one sequence! Try loading a sequence and trying again.").go();
			return false;
		}

		// is there a real colNameOfInterest?
		if(!dataStore.isColumn(colNameOfInterest)) {
			new MessageBox(
					matrix.getFrame(),
					"Column name not present!",
					"You are trying to generate a pairwise distance analysis on the column '" + colNameOfInterest + "'. This column, however, does not exist! This is most likely a programming bug. Please let the programmers know!").go();		
			return false;
		}

		// okay! let's go! leeeeeeeeeeeeroy jenkins!
		ProgressDialog pd = new ProgressDialog(
			matrix.getFrame(),		
			"Please wait, calculating reference order ...",
			"I am generating a 'reference order' . Please wait!"
		);
		pd.begin();

		// step 1: pick the sequence we're going to use as the one-on-top
		seqName_top = (String) dataStore.getSequences().get(0);

		// step 2: generate the reference sequence list
		List sequences = dataStore.getSequences();
		List columns = dataStore.getColumns();

		class Score implements Comparable {
			private String seqName = "";
			private double pairwise = 0.0;

			public Score(String seqName, double pairwise) {
				this.seqName = seqName;
				this.pairwise = pairwise;
			}

			private String getSeqName()	{	return seqName;		}
			private double getPairwise() 	{	return pairwise;	}

			public int compareTo(Object o) {
				Score s = (Score) o;

				if(getSeqName().equals(seqName_top))
					return -1;

				if(s.getSeqName().equals(seqName_top))
					return +1;

				return (int)(com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(getPairwise()) 
					- com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(s.getPairwise()));
			}
		};

		Vector scores = new Vector();

		Iterator i_seq = sequences.iterator();
		while(i_seq.hasNext()) {
			String seqName = (String) i_seq.next();
			double totalScore = 0;

			// don't do seqName_top
			if(seqName.equals(seqName_top)) {
				scores.add(new Score(seqName_top, 0));
				continue;
			}

			int count_validSequences = 0;
			double sum_distances = 0.0;
			Iterator i_col = columns.iterator();
			while(i_col.hasNext()) {
				String colName = (String) i_col.next();

				// don't do the Column Of Interest
				if(colName.equals(colNameOfInterest))
					continue;

				// let's do this!
				Sequence seqTop = dataStore.getSequence(colName, seqName_top);
				Sequence seq = dataStore.getSequence(colName, seqName);

				if(seqTop == null)
					continue;		// TODO fix this!

				if(seq == null)
					continue;
				
				double pairwise = seqTop.getPairwise(seq);
				if(pairwise < 0)
					continue;

				// okay, it's a valid distance
				sum_distances += pairwise;
				count_validSequences++;		
			}

			totalScore = sum_distances / (double)count_validSequences;

			scores.add(new Score(seqName, totalScore));
		}

		// step 3: sort the reference sequence list
		Collections.sort(scores);

		sequencesList = (List) new Vector();

		Iterator i = scores.iterator();
		while(i.hasNext()) {
			Score s = (Score) i.next();
			String seqName = s.getSeqName();

			sequencesList.add(seqName);
		}

		// TODO: temporary hack
		columnList = dataStore.getColumns();
		pdm_hash_maxDist = new Hashtable();
		pdm_hash_minDist = new Hashtable();

		matrix.getJTable().setDefaultRenderer(String.class, new PDMColorRenderer(Color.BLACK));
		matrix.getJTable().getColumnModel().getColumn(additionalColumns + 1).setCellRenderer(new PDMColorRenderer(Color.RED));
		
		// hmpf
		for(int y = 0; y < getColumnCount(); y++) {
			for(int z = 0; z < getRowCount(); z++) {
				getValueAt(z, y);
			}
		}

		pd.end();

		return true;
	}

	/**
	 * Undoes all the changes made by enterPairwiseDistanceMode(). Each and every one.
	 * 	 
	 * @return true, if we're now out of PDM
	 */
	public boolean exitPairwiseDistanceMode() {
		matrix.getJTable().setDefaultRenderer(String.class, new DefaultTableCellRenderer());
		dataStore.updateDisplay();

		return true;
	}	

//
// 1.	THE TABLE MODEL LISTENER SYSTEM. We use this to let people know
// 	we've changed. When we change.
//
	/** Don't call this function. We don't support it at all. */
	public void addTableModelListener(TableModelListener l) {
		throw new UnsupportedOperationException("You are not supposed to call " + this + ".addTableModelListener(" + l + ")!");
	}
	
	/** Don't call this function. We don't support it at all. */
	public void removeTableModelListener(TableModelListener l) {
		throw new UnsupportedOperationException("You are not supposed to call " + this + ".removeTableModelListener(" + l + ")!");
	}

	/** Internal function to fire a TableModelEvent. We send it to the DataStore for further processing. */
	private void fireTableModelEvent(TableModelEvent e) {
		dataStore.fireTableModelEvent(e);
	}

//
// 2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//
	/**
	 * Tells us what *class* of object to expect in columns. We can safely expect Strings.
	 * I don't think the world is ready for transferable Sequences just yet ...
	 */
	public Class getColumnClass(int columnIndex) {
		return String.class;
	}

	/**
	 * Gets the number of columns.
	 */
	public int getColumnCount() {
		return dataStore.getColumns().size() + additionalColumns; 
	}

	/**
	 * Gets the number of rows.
	 */
	public int getRowCount() {
		return sequencesList.size(); 
	}

	/**
	 * Gets the name of column number 'columnIndex'.
	 */
        public String getColumnName(int columnIndex) {
		if(columnIndex == 0)
			return "";		// upper left hand box

		if(columnIndex == 1)
			return "Total length";

		if(columnIndex == 2)
			return "Number of sets";

		return (String) columnList.get(columnIndex - additionalColumns);
	}

	/**
	 * Convenience function.
	 */
	public String getRowName(int rowIndex) {
		return (String) sequencesList.get(rowIndex);
	}

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
			return dataStore.getCompleteSequenceLength(seqName) + " bp";

		// if it's the number of charsets column, return that.
		if(columnIndex == 2)
			return dataStore.getCharsetsCount(seqName) + "";

		// okay, it's an actual 'sequence'
		// is it cancelled?
		if(dataStore.isSequenceCancelled(colName, seqName))
			return "(CANCELLED)";

		// if not, get the sequence
		Sequence seq 	= dataStore.getSequence(colName, seqName);

		if(seq == null)
			return "(N/A)";	

		Sequence seq_compareAgainst = dataStore.getSequence(colName, seqName_top); 
		if(seq_compareAgainst == null)
			return "(N/A - bug)";

		double dist = seq.getPairwise(seq_compareAgainst);

		// min and max dist for this row
		double minDist = +2.0;
		double maxDist = -1.0;

		if(pdm_hash_maxDist.get(colName) == null)
			pdm_hash_maxDist.put(colName, new Double(maxDist));
		else
			maxDist = ((Double)pdm_hash_maxDist.get(colName)).doubleValue();

		if(pdm_hash_minDist.get(colName) == null)
			pdm_hash_minDist.put(colName, new Double(minDist));
		else
			minDist = ((Double)pdm_hash_minDist.get(colName)).doubleValue();

		if(dist > 0 && dist < minDist) {
			minDist = dist;
			pdm_hash_minDist.put(colName, new Double(minDist));
		}

		if(dist > 0 && dist > maxDist) {
			maxDist = dist;
			pdm_hash_maxDist.put(colName, new Double(maxDist));
		}

		if(seqName.equals(seqName_top))
			return "(N/A)";
		else
			return percentage(dist - minDist, maxDist - minDist) + "%";
	}

	/** Convenience function */
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	/**
	 * Determines if you can edit anything. Which is only the sequences column.
	 */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
		if(columnIndex == 0)
			return true;
		return false;
	}

	/** 
	 * Allows the user to set the value of a particular cell. That is, the
	 * sequences column. 
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if(columnIndex == 0) {
			String strOld = (String) getRowName(rowIndex);
			String strNew = (String) aValue;

			dataStore.renameSequence(strOld, strNew);
		}
	}


}

class PDMColorRenderer extends DefaultTableCellRenderer
{
	private Color basicColor = Color.BLACK;

	public PDMColorRenderer(Color basicColor) {
		this.basicColor = basicColor;	
	}

	public Color getColor(double value) {
		if(basicColor.equals(Color.BLACK))
			return Color.getHSBColor(0.0f, 0.0f, 1.0f - (float)value);
		else 	// default: RED
			return Color.getHSBColor(0.0f, 1.0f, (float)value);
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

	comp.setOpaque(false);

	if(row < 1 || col < DisplayPairwiseModel.additionalColumns)
		return comp;
	
	String val = (String) value;

	if(val.equals("(N/A)"))
		return comp;

	if(val.equals("(N/A - bug)"))
		return comp;

	val = val.replaceAll("\\%", "");

	try {
		double d = Double.parseDouble(val)/100.0;

		comp.setOpaque(true);

		if(d < 0 || d > 1)
			comp.setBackground(getColor(0.0)); 
		else
			comp.setBackground(getColor(d)); 

	} catch(NumberFormatException e) {
		// ah, ferget it
	}

        return comp;
    }
}
