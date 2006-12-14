/**
 * The TableManager manages the JTable and all related user operations.
 * This includes left clicks, right clicks, selects, jumps, whatever
 * you could possible think of. Our purpose in life is to abstract 
 * away all mid-level UI operations. Basically, we want SequenceMatrix
 * to be able to say "tableManager.tableClicked(1, 24)", and dataStore
 * to be able to say "tableManager.repaint()", and just leave it at
 * that.
 *
 * The most complicated thing we have right now is the fact that the
 * Display systems have broken off from the DataStore - a good thing,
 * but, unfortunately, there's TWO of them. The TableManager is
 * one of the things we're explicitly creating to try and work out
 * the issues.
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
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class TableManager {

//
//	CONSTANTS
//
	public static final int		DISPLAY_SEQUENCES	=	0;	// our new name for the normal mode
	public static final int		DISPLAY_DISTANCES	=	1;	// our new name for PDM mode
	public static final int		DISPLAY_CORRELATIONS		=	2;	// our new name for the 'other' mode
	public static final int		DISPLAY_LAST		=	2;	// the last DISPLAY_* value (used in init)

//
//	VARIABLES
//
	// The SequenceMatrix is the Big Guy, and we report up to him (value set in constructor)
	// also: the table we are managing
	private SequenceMatrix 	matrix	=	null;
	private JTable		table	=	null;

	// The DataStore stores information about the sequences. That's it.
	private DataStore	dataStore =	new DataStore();

	// We need to track the DisplayModes
	private DisplayMode	currentDisplayMode =	null;
	private int		currentMode =		-1;
	private DisplayMode[]	displayModes = 		new DisplayMode[DISPLAY_LAST + 1];		
										// stores instances of the DisplayModes 
										// so we don't have to keep creating
										// them

	// Our sequences
	private java.util.List	sortedColumns = 	null;
	private java.util.List	sortedSequences =	null;

	// Our 'state'
	private String		referenceTaxon = 	null; 

	// The menu item we've inserted into the main menu for the main frame
	private Menu		last_displayModeMenu =	null;
	

//
// 0.	CONSTRUCTOR. Creates the TableManager.
//
	/** Create the TableManager for a particular SequenceMatrix */
	public TableManager(SequenceMatrix matrix, JTable jTable) {
		this.matrix = matrix;
		this.table = jTable;

		changeDisplayMode(DISPLAY_SEQUENCES, null);
		resizeColumns();
	}

//
// 1.	GETTERS. Returns the state or instance variable of the table
// 	at the moment.
//
	public String getReferenceSequence() {
		return referenceTaxon;
	}

// 
// 2.	SETTERS. Lets you set states or variables for us.
//
	public void setReferenceSequence(String x) {
		if(referenceTaxon != null && referenceTaxon.equals(x))
			return;			// don't change anything

		referenceTaxon = x;
		updateDisplay();
	}

//
// 3.	FUNCTIONAL CODE. Does something.
//
	/** 
	 * Clears all entries from the underlying datastore, and cleans
	 * up the JTable.
	 */
	public void clear() {
		dataStore.clear();
		updateDisplay();
	}

	/**
	 * Resizes ALL columns to fit their widest entry.
	 */
	public void resizeColumns() {
		Iterator i = sortedColumns.iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();

			resizeColumnToFit(colName);
		}
	}

	/**
	 * Resizes column 'x' to fit the widest entry.
	 */
	public void resizeColumnToFit(String x) {
		TableColumnModel tcm = table.getColumnModel();
		if(tcm == null)
			return;		// it could happen

		int col = sortedColumns.indexOf(x);
		if(col == -1)
			return;		// it could happen

		TableColumn tc = tcm.getColumn(col);
		if(tc == null)
			return;		// it could happen
	
		// now, we need to manually figure out who needs the most space
		int maxLength = 0;

		// do the header
		TableCellRenderer r = tc.getHeaderRenderer();
		if(r == null) {
			r = table.getTableHeader().getDefaultRenderer();
		}

		Component comp = r.getTableCellRendererComponent(table, tc.getHeaderValue(), true, true, -1, col);

		if(comp != null)
			maxLength = (int) comp.getPreferredSize().getWidth();
		

		// do all the entries in the column
		for(int row = 0; row < sortedSequences.size(); row++) {
			TableCellRenderer renderer = table.getCellRenderer(row, col);
			Component c = renderer.getTableCellRendererComponent(table, table.getValueAt(row, col), true, true, row, col);

			int length = -1;
			if(c != null)
				length = (int) c.getPreferredSize().getWidth();

			if(length > maxLength)
				maxLength = length;
		}

		if(maxLength > 0)
			tc.setPreferredWidth(maxLength + 10);	// The '10' is for the margins
	}

//
// X.	FILE HANDLING CODE. This is where we deal with file handling.
//

//
// X.	SEQUENCE HANDLING CODE. This is the bit where we talk to the DataStore, and
// 	back to anybody who would like to have a word. 
//
	public void addSequenceList(String colName, SequenceList sl, StringBuffer complaints, DelayCallback delay) {
		dataStore.addSequenceList(colName, sl, complaints, delay);	
		updateDisplay();
		resizeColumnToFit(colName);
	}

	/** 
	 * Returns true if 'colName' is really a column.
	 */
	public boolean doesColumnExist(String colName) {
		return dataStore.isColumn(colName);
	}	

	public Sequence getSequence(String colName, String seqName) {
		return dataStore.getSequence(colName, seqName);
	}

	public Sequence getCancelledSequence(String colName, String seqName) {
		return dataStore.getCancelledSequence(colName, seqName);
	}

	public void addSequenceList(SequenceList sl, StringBuffer complaints, DelayCallback delay) {
		dataStore.addSequenceList(sl, complaints, delay);
		updateDisplay();
		// If you really want to resize the new column to fit,
		// you should rewrite this as a call to addSequencelist(colName, ...).
		// Which means figuring out a column name, which is something I
		// really just don't have the patience for right now.
	}

	public void deleteColumn(String colName) {
		dataStore.deleteColumn(colName);
		updateDisplay();
	}

	public void deleteRow(String seqName) {
		dataStore.deleteRow(seqName);
		updateDisplay();
	}

	public void toggleCancelled(String colName, String seqName) {
		dataStore.toggleCancelled(colName, seqName);
		updateDisplay();
	}

	public boolean isSequenceCancelled(String colName, String seqName) {
		return dataStore.isSequenceCancelled(colName, seqName);
	}

	public int getCharsetsCount(String seqName) {
		return dataStore.getCharsetsCount(seqName);
	}

	public int getSequenceLength() {
		return dataStore.getCompleteSequenceLength();
	}

	public int getSequenceLength(String seqName) {
		return dataStore.getCombinedSequenceLength(seqName);
	}

	public int getColumnLength(String colName) {
		return dataStore.getColumnLength(colName);
	}

	public int getSequencesCount() {
		return sortedSequences.size();
	}
	
	public java.util.List getColumns() {
	       return (java.util.List) sortedColumns;
	}

	public java.util.List getSequences() {
	       return (java.util.List) sortedSequences;
	}

	public java.util.List getSequenceNamesByColumn(String colName) {
		Vector v = new Vector();

		Iterator i = sortedSequences.iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();
			
			if(dataStore.getCancelledSequence(colName, seqName) != null)
				v.add(colName);
		}

		return (java.util.List) v;
	}

	public SequenceList getSequenceListByColumn(String colName) {
		SequenceList sl = new SequenceList();

		Iterator i = sortedSequences.iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();
			
			Sequence seq = dataStore.getSequence(colName, seqName);
			if(seq != null)
				sl.add(seq);
		}

		return sl;
	}
	

	/**
	 * Renames a sequence
	 */
	public void renameSequence(String oldName, String newName) {
		if(oldName.equals(newName))
			return;			// don't do same name transforms

		String complaints = dataStore.testRenameSequence(oldName, newName);
		if(	
			(complaints != null)		&& 
			(complaints.length() != 0)
		) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Warning: sequences will be deleted!",
					"Renaming '" + oldName + "' to '" + newName + "' will cause the following problems. Are you sure you want to go ahead with this rename?\n" + complaints,
					MessageBox.MB_YESNO
			);

			if(mb.showMessageBox() == MessageBox.MB_NO)
				return;
		}

		dataStore.forceRenameSequence(oldName, newName);
		updateDisplay();
	}
	
//
// 4. 	DISPLAY MODE MANAGING CODE. Changes display modes, handles routing
// 	on to the specific objects which actually do the display mode-ing,
// 	and handling incoming event calls (updateDisplay(), etc.)
//
	/** 
	  * changeDisplayMode() on a diet: one argument only! Obviously, the
	  * mode to switch to.
	  */
	public void changeDisplayMode(int mode) {
		changeDisplayMode(mode, null);
	}

	/** 
	 * Easily the ugliest function EVER, changeDisplayMode() will change
	 * the display mode to the mode specified by mode (which is one of the
	 * DISPLAY_* constants), with an optional argument (which is passed
	 * straight on to the display mode object.
	 */
	public void changeDisplayMode(int mode, Object argument) {
		if(mode == currentMode)
			return;

		Hashtable widths = null;
		if(currentDisplayMode != null) {
			widths = currentDisplayMode.saveWidths();			
			currentDisplayMode.deactivateDisplay();
		}

		// now, deactivate the last menu!
		if(last_displayModeMenu != null) {
			matrix.getFrame().getMenuBar().remove(last_displayModeMenu);
			last_displayModeMenu = null;
		}

		currentDisplayMode = getDisplayMode(mode);

		currentDisplayMode.activateDisplay(table, argument);
		currentMode = mode;

		// turn on the new menu!
		Menu menu = currentDisplayMode.getDisplayModeMenu();
		if(menu != null) {
			// ugh
			matrix.getFrame().getMenuBar().add(menu);
			last_displayModeMenu = menu;
		}
		
		if(currentDisplayMode != null && widths != null)
			currentDisplayMode.restoreWidths(widths);
	}

	/**
	 * I'm sorry, I couldn't resist. Never 'new' when you can
	 * reload, right?
	 */
	public DisplayMode getDisplayMode(int mode) {
		DisplayMode dm = displayModes[mode];
		
		if(dm == null) {
			switch(mode) {
				default:
				case DISPLAY_SEQUENCES:
					dm = new DisplaySequencesMode(this);
					break;
				case DISPLAY_DISTANCES:
					dm = new DisplayDistancesMode(this);
					break;
				case DISPLAY_CORRELATIONS:
					dm = new DisplayCorrelationsMode(this);
					break;
			}
			displayModes[mode] = dm;
		}

		sortedColumns = dm.getSortedColumns(dataStore.getColumns());
		sortedSequences = dm.getSortedSequences(dataStore.getSequences());

		return dm;
	}

	/** 
	 * UpdateDisplay calls on current DisplayMode to do its duty.
	 */ 
	public void updateDisplay() {
		sortedColumns = currentDisplayMode.getSortedColumns(dataStore.getColumns());
		sortedSequences = currentDisplayMode.getSortedSequences(dataStore.getSequences());

		currentDisplayMode.updateDisplay();
	}


//
// X.	HACKS. These might have to go eventually
//
	public int getCancelledSequencesCount() {
		return dataStore.getCancelledSequencesCount();
	}

	public TableModel getTableModel() {
		return (TableModel) currentDisplayMode;
	}
}
