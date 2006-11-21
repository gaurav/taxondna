/**
 * This is a TableModel which displays the sequences (as returned by dataStore) by
 * their basepair counts (their "widths").
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

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DisplayCountsModel implements TableModel {
	//
	// Variables we'll need to track
	//
	private SequenceMatrix 	matrix 			= 	null;	
	private DataStore	dataStore 		=	null;
	public static final int additionalColumns 	= 	3;		// Sequence names, total length, and # of charsets

	/** 
	 * We need to know the SequenceMatrix we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DisplayCountsModel(SequenceMatrix sm, DataStore ds) {
		matrix = sm;
		dataStore = ds;
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
		return dataStore.getSequencesCount();
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

		return (String) dataStore.getColumns().get(columnIndex - additionalColumns);
	}

	/**
	 * Convenience function.
	 */
	public String getRowName(int rowIndex) {
		return (String) dataStore.getSequences().get(rowIndex);
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

		int internalGaps = seq.countInternalGaps();
		int n_chars = seq.countBases('N');
		if(internalGaps == 0) {
			if(n_chars == 0)
				return seq.getActualLength() + "";
			else
				return seq.getActualLength() + " (" + n_chars + " 'N' characters)";
		} else {
			if(n_chars == 0)
				return seq.getActualLength() + " (" + internalGaps + " gaps)";
			else
				return seq.getActualLength() + " (" + internalGaps + " gaps, " + n_chars + " 'N' characters)";
		}
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
