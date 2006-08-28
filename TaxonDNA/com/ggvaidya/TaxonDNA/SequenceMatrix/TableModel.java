/**
 * The TableModel for the main gene/sequence list thing.
 * Talks with SequenceGrid (which handles the backend),
 * and focuses on JUST handling things with the front-end
 * (i.e. the JTable).
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

public class TableModel implements javax.swing.table.TableModel {
	SequenceMatrix 	matrix = null;
	SequenceGrid 	seqGrid = null;

	Vector 		seq_names = new Vector();
	Vector		col_names = new Vector();

	/** We are required to keep track of classes which would like to be notified of changes */
	Vector listeners = new Vector();

	//
	//	1.	CONSTRUCTORS.
	//
	/**
	 * Creates a TableModel which will uses the specified SequenceMatrix.
	 */
	public TableModel(SequenceMatrix matrix) {
		this.matrix = matrix;
		seqGrid = matrix.getSequenceGrid();
	}

	/**
	 * Informs us that the underlying data has changed. We tell
	 * everybody who knows, who will - presumably - be in touch.
	 */
	public void updateDisplay() {
		// refresh the seq and col lists
		seq_names = seqGrid.getSequences();
		col_names = seqGrid.getColumns();

		// update the current width of the leftmost column to
		// the size of the largest sequence name
		Iterator i = seq_names.iterator();
		int largest_length = 0;
		while(i.hasNext()) {
			String seqName = (String) i.next();

			if(seqName.length() > largest_length)
				largest_length = seqName.length();
		}
//		matrix.setColumnWidthByChars(0, largest_length);

		// let everybody know
		i = listeners.iterator();
		while(i.hasNext()) {
			TableModelListener l = (TableModelListener)i.next();	

			l.tableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
		}
	}

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
		return col_names.size() + 1; 
	}
	
	/**
	 * Gets the number of rows.
	 */
	public int getRowCount() {
		return seq_names.size();
	}

	/**
	 * Gets the name of column number 'columnIndex'.
	 */
        public String getColumnName(int columnIndex) {
		if(columnIndex == 0)
			return "";		// upper left hand box

		return (String) col_names.get(columnIndex - 1) + " (bp)";
	}

	/**
	 * Gets the value at a particular column. The important
	 * thing here is that two areas are 'special':
	 * 1.	Row 0 is reserved for the column names.
	 * 2.	Column 0 is reserved for the row names.
	 * 3.	(0, 0) is to be a blank box (new String("")).
	 */
        public Object getValueAt(int rowIndex, int columnIndex) {
		if(seq_names.size() == 0)
			return "No sequences loaded";

		if(columnIndex == 0) {
			// can't be the empty box: we've already got that.
			return seq_names.get(rowIndex);
		}

		String seqName 	= (String) seq_names.get(rowIndex);
		String colName  = (String) col_names.get(columnIndex - 1);
		Sequence seq 	= seqGrid.getSequence(colName, seqName);

		// is it perhaps not defined for this column?
		if(seq == null)
			return "(N/A)";	

		return String.valueOf(seq.getActualLength());
	}

	/**
	 * Determines if you can edit anything. Which is only the sequences column.
	 */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
		if(columnIndex == 0)
			return true;
		return false;
	}

	/** Allows the user to set the value of a particular cell. That is, the
	 * sequences column. 
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if(columnIndex == 0) {
			String strOld = (String) getValueAt(rowIndex, columnIndex);
			String strNew = (String) aValue;

			seqGrid.renameSequence(strOld, strNew);
		}
			
	}

	//
	// X. 	THE TABLE MODEL LISTENER SYSTEM. We use this to let people know
	// 	we've changed. When we change.
	//
	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}
	
	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);
	}	
}
