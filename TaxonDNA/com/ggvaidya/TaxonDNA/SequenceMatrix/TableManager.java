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
	public static final int		DISPLAY_RANKS		=	2;	// our new name for the 'other' mode

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
	private int		currentMode =		DISPLAY_SEQUENCES;
	private Vector		vec_displayModes = 	new Vector();		// stores instances of the DisplayModes 
										// so we don't have to keep creating
										// them

//
// 0.	CONSTRUCTOR. Creates the TableManager.
//
	/** Create the TableManager for a particular SequenceMatrix */
	public TableManager(SequenceMatrix matrix, JTable jTable) {
		this.matrix = matrix;
		this.table = jTable;
	}

//
// 1.	GETTERS. Returns the state or instance variable of the table
// 	at the moment.
//


// 
// 2.	SETTERS. Lets you set states or variables for us.
//

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
	}

	/** 
	 * Returns true if 'colName' is really a column.
	 */
	public boolean doesColumnExist(String colName) {
		return dataStore.isColumn(colName);
	}	

	public void addSequenceList(SequenceList sl, StringBuffer complaints, DelayCallback delay) {
		dataStore.addSequenceList(sl, complaints, delay);
		updateDisplay();
	}

	public void deleteColumn(String colName) {
		dataStore.deleteColumn(colName);
		updateDisplay();
	}

	public void deleteRow(String seqName) {
		dataStore.deleteRow(seqName);
		updateDisplay();
	}

	public void setReferenceSequenceName(String seqName) {
		// what to do, what to say
		// shall we carry a treasure away
		// what a gem, what a pearl
		// beyond rubies is our little seqName
	}

//
// X.	USER INTERFACE
//
	/**
	 * Event: somebody right clicked in the mainTable somewhere
	 */
	public void rightClick(MouseEvent e, int col, int row) {
		/*
		popupMenu.show((Component)e.getSource(), e.getX(), e.getY());
		/
		String colName = getColumnName(col);
		String rowName = "";
		if(row > 0)				// we don't use the value of rowName if (row == 0)
			rowName = getRowName(row);

		PopupMenu pm = new PopupMenu();

		if(col == 0) {
			// colName == ""
			// we'll replace this with 'Sequence names'
			colName = "Sequence names";
		}

		if(col < additionalColumns) {
			// it's a 'special' column
			// we can't do things to it
			pm.add("Column: " + colName);
		} else {
			Menu colMenu = new Menu("Column: " + colName);
			
			MenuItem delThisCol = new MenuItem("Delete this column");
			delThisCol.setActionCommand("COLUMN_DELETE:" + colName);
			colMenu.add(delThisCol);

			MenuItem pdmThisCol = new MenuItem("Do a PDM on this column");
			pdmThisCol.setActionCommand("DO_PDM:" + colName);
			colMenu.add(pdmThisCol);

			colMenu.addActionListener(matrix);	// wtf really?
			pm.add(colMenu);
		}

		if(row <= 0) {
			pm.add("Row: Headers");
		} else {
			Menu rowMenu = new Menu("Row: " + rowName);

			MenuItem delThisRow = new MenuItem("Delete this row");
			delThisRow.setActionCommand("ROW_DELETE:" + rowName);
			rowMenu.add(delThisRow);

			if(outgroupName != null && rowName.equals(outgroupName)) {
				MenuItem makeOutgroup = new MenuItem("Unset this row as the outgroup");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:");
				rowMenu.add(makeOutgroup);			
			} else {
				MenuItem makeOutgroup = new MenuItem("Make this row the outgroup");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:" + rowName);
				rowMenu.add(makeOutgroup);
			}

			rowMenu.addActionListener(matrix);		// wtf really?
			pm.add(rowMenu);
		}

		((JComponent)e.getSource()).add(pm);			// yrch!
		pm.show((JComponent)e.getSource(), e.getX(), e.getY());
		*/
	}

	/**
	 * Event: somebody double clicked in the mainTable somewhere
	 */
	public void doubleClick(MouseEvent e, int col, int row) {
		/*
		if(row > 0 && col != -1 && col >= additionalColumns) {
			// it's, like, valid, dude.
			toggleCancelled(getColumnName(col), getRowName(row));
		}
		*/
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

		currentDisplayMode.deactivateDisplay();

		currentDisplayMode = getDisplayMode(mode);

		currentDisplayMode.activateDisplay(table, argument);
		currentMode = mode;
	}

	/**
	 * I'm sorry, I couldn't resist. Never 'new' when you can
	 * reload, right?
	 */
	public DisplayMode getDisplayMode(int mode) {
		DisplayMode dm = (DisplayMode) vec_displayModes.get(mode);

		if(dm == null) {
			switch(mode) {
				default:
				case DISPLAY_SEQUENCES:
//					dm = new DisplaySequencesMode(this);
					break;
				case DISPLAY_DISTANCES:
//					dm = new DisplayDistancesMode(this);
					break;
				case DISPLAY_RANKS:
//					dm = new DisplayRanksMode(this);
					break;
			}
			vec_displayModes.set(mode, dm);
		}

		return dm;
	}

	/** 
	 * UpdateDisplay calls on current DisplayMode to do its duty.
	 */ 
	public void updateDisplay() {
		currentDisplayMode.updateDisplay();
	}

//
// X.	HACKS. These might have to go eventually
//
	public int getCancelledSequencesCount() {
		return dataStore.getCancelledSequencesCount();
	}

	public DataStore getDataStore() {
		return dataStore;
	}

	public TableModel getTableModel() {
		return (TableModel) currentDisplayMode;
	}
}

/*
// CODE MOVING IN FROM DATASTORE

//
// 4.	SORTING. Obviously the next section down ought to be the display code.
// 	But we'll need to sort before we display, in order to avoid disappointment.
//
	/**
	 * A convenience function which checks to see if either name1 or name2 are the
	 * outgroupName, in which case they'll get sorted up.
	 /
	private int checkForOutgroup(String name1, String name2) {
		if(outgroupName == null)
			return 0;

		if(name1.equalsIgnoreCase(outgroupName))
			return -1;
		if(name2.equalsIgnoreCase(outgroupName))
			return +1;
		return 0;
	}

	/**
	 * A sort Comparator which sorts a collection of Strings in natural order - except that outgroups get
	 * sorted to the top.
	 /
	private class SortByName implements Comparator {
		public int	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

			int res = checkForOutgroup(str1, str2);
			if(res != 0)
				return res;

			return str1.compareTo(str2);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings by - of all things - their SECOND name. Such is life.
	 /
	private class SortBySecondName implements Comparator {
		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

			int res = checkForOutgroup(str1, str2);
			if(res != 0)
				return res;

			String str1_second = null;
			String str2_second = null;

			Pattern p = Pattern.compile("\\w+\\s+(\\w+)\\b");	// \b = word boundary

			Matcher m = p.matcher(str1);
			if(m.lookingAt())
				str1_second = m.group(1);
			
			m = p.matcher(str2);
			if(m.lookingAt())
				str2_second = m.group(1);

			if(str1_second == null) {
				if(str2_second == null)
					return 0;		// identical
				else 
					return +1;		// str2 is valid
			}

			if(str2_second == null)
				return -1;			// str1 is valid

			return str1_second.compareTo(str2_second);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings (really taxon names) by the number of charsets it has.
	 /
	private class SortByCharsets implements Comparator {
		private DataStore store = null;

		public SortByCharsets(DataStore store) {
			this.store = store;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;
			
			int res = checkForOutgroup(str1, str2);
			if(res != 0)
				return res;

			int countCharsets1 = store.getCharsetsCount(str1);
			int countCharsets2 = store.getCharsetsCount(str2);

			return (countCharsets2 - countCharsets1);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings (really taxon names) by the total actual count of bases.
	 /
	private class SortByTotalActualLength implements Comparator {
		private DataStore store = null;

		public SortByTotalActualLength(DataStore store) {
			this.store = store;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;
			
			int res = checkForOutgroup(str1, str2);
			if(res != 0)
				return res;

			int count1 = store.getCombinedSequenceLength(str1);
			int count2 = store.getCombinedSequenceLength(str2);

			return (count2 - count1);
		}
	}

	/**
	 * Actually resort the sequences as instructed. We have a bunch of Vectors
	 * (sortedColumnNames and sortedSequenceNames) which store the column and 
	 * sequence order. They really ought to be resorted once new sequences are
	 * added.
	 *
	 * So:
	 * 	(x-coord, y-coord) 	is mapped to		(colName, seqName)
	 * 	(colName, seqName)	is mapped to		(Sequence)
	 *
	 * NOTE: Do NOT check if it's already sorted, unless you also check broken!
	 
	private void resort(int sort) {
		sortedColumnNames = new Vector(getColumnsUnsorted());
		Collections.sort(sortedColumnNames);

		sortedSequenceNames = new Vector(getSequencesUnsorted());

		switch(sort) {
			case SORT_BYTOTALLENGTH:
				Collections.sort(sortedSequenceNames, new SortByTotalActualLength(this));
				break;
			case SORT_BYCHARSETS:
				Collections.sort(sortedSequenceNames, new SortByCharsets(this));
				break;
			case SORT_BYSECONDNAME:
				Collections.sort(sortedSequenceNames, new SortBySecondName());
				break;
			case SORT_BYNAME:
			default:
				Collections.sort(sortedSequenceNames, new SortByName());
		}

		sortBy = sort;
		sortBroken = false;
		
		if(!suppressUpdates && DisplayPairwiseModel.class.isAssignableFrom(currentTableModel.getClass())) {
			DisplayPairwiseModel dpm = (DisplayPairwiseModel) currentTableModel;

			if(!dpm.resortPairwiseDistanceMode())		// uh-oh ... something went wrong!
				if(!exitPairwiseDistanceMode())		// go back to a sensible state
					fatalError();

			return;
		}
	}	
//
// 4.	DISPLAY THE SEQUENCE. This code will 'update' the code to screen. Except that
// 	what we _really_ do is to send messages to all the TableModelListeners that we
// 	are changing, and then 'return' the right information to them.
//
// 	This is hard to explain at 12pm on a Monday morning (Monday morning bluuuuueeees),
// 	so:
//
//	DataStore.updateSequence(seqName)
//	DataStore.updateColumn(colName)
//	DataStore.updateSort(newSort)
//	DataStore.updateDisplay()
//		|
//		+-------------------------------------------------------+
//									|
//							{Registered TableModelListener #1}
//		+---------------------------------------{Registered TableModelListener #2}
//		|					{Registered TableModelListener ...}
//		|
//	DataStore.getColumnName(...)
//	DataStore.getValueAt(...)
//
//	PHASE 2: The above mechanism has been changed (again). Now, we create a 
//	DisplayCountsModel, which handles the nitty-gritty. We are responsible for
//	passing all messages on.
//
//	Eventually, we will also have a DisplayPairwiseModel. At this point, things
//	really get an awful lot of fun, since we have to figure out WHICH MODEL TO
//	TALK TO, then talk to that model. And switch from one to the other.
//
//	Sigh. 
//

//
// 6.1.	THE TABLE MODEL LISTENER SYSTEM. We use this to let people know
// 	we've changed. When we change.
//
	public void addTableModelListener(TableModelListener l) {
		tableModelListeners.add(l);
	}
	
	public void removeTableModelListener(TableModelListener l) {
		tableModelListeners.remove(l);
	}

	public void updateSort(int sortBy) {
		if(suppressUpdates)
			return;

		resort(sortBy);
		updateNoSort();
	}
	
	public void updateDisplay() {
		if(suppressUpdates)
			return;

		resort(sortBy);
		updateNoSort();
	}

	public void updateNoSort() {
		if(suppressUpdates)
			return;

//		fireTableModelEvent(new TableModelEvent(this));
		
		// okay, firing the table model event resets everything
		// this is good for us (since even minor changes, like 
		// deleting a sequence, can cause major changes, like
		// removing an entire column which only contained that
		// one sequence).
		//
		// on the other hand, we need to conserve the column
		// widths. Since i can't find any better way of making
		// this happen, i'm just going to save them all (into
		// a hashtable by 'identifier'), then spew them back
		// out again.
		//
		// and may God have mercy on my soul.
		//
		Hashtable widths = saveWidths();
		fireTableModelEvent(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
		restoreWidths(widths);
	}


//
// 6.2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//
	/**
	 * Tells us what *class* of object to expect in columns. We can safely expect Strings.
	 * I don't think the world is ready for transferable Sequences just yet ...
	 /
	public Class getColumnClass(int columnIndex) {
		return currentTableModel.getColumnClass(columnIndex);  
	}

	/**
	 * Gets the number of columns.
	 /
	public int getColumnCount() {
		return currentTableModel.getColumnCount();
	}
	
	/**
	 * Gets the number of rows.
	 /
	public int getRowCount() {
		return currentTableModel.getRowCount(); 
	}

	/**
	 * Gets the name of column number 'columnIndex'.
	 /
        public String getColumnName(int columnIndex) {
		return currentTableModel.getColumnName(columnIndex);
	}

	/**
	 * Convenience function.
	 /
	public String getRowName(int rowIndex) {
		return (String) currentTableModel.getValueAt(rowIndex, 0);
	}

	/**
	 * Gets the value at a particular column. The important
	 * thing here is that two areas are 'special':
	 * 1.	Row 0 is reserved for the column names.
	 * 2.	Column 0 is reserved for the row names.
	 * 3.	(0, 0) is to be a blank box (new String("")).
	 /
        public Object getValueAt(int rowIndex, int columnIndex) {
		return currentTableModel.getValueAt(rowIndex, columnIndex);
	}

	/**
	 * Determines if you can edit anything. Which is only the sequences column.
	 /
        public boolean isCellEditable(int rowIndex, int columnIndex) {
		return currentTableModel.isCellEditable(rowIndex, columnIndex);
	}

	/** 
	 * Allows the user to set the value of a particular cell. That is, the
	 * sequences column. 
	 /
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		currentTableModel.setValueAt(aValue, rowIndex, columnIndex);
	}

	// Communicate the droppedSequences list to the user
		if(droppedSequences.length() > 0) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Warning: Sequences were dropped!",
					"Some sequences in the column '" + colName + "' were not added to the dataset. These are:\n" + droppedSequences.toString()
				);

			mb.go();
		}

//
// UI over the table
//
/*
	/**
	 * Get the current outgroup.
	 * @return null, if there is no current outgroup.
	 /
	public String getOutgroupName() {
		return outgroupName;
	}

	/**
	 * Set the current outgroup. Changes the current outgroup
	 * to the name mentioned.
	 /
	public void setOutgroupName(String newName) {
		outgroupName = newName;
		sortBroken = true;
	}

	/** 
	 * Activate PDM. Normally, we'd ask the user
	 * at this point which column he wants to use, 
	 * but for now we can just ignore it and get
	 * on with life.
	 /
	public boolean enterPairwiseDistanceMode() {
		if(getColumns().size() == 0)
			return false;

		return enterPairwiseDistanceMode((String)getColumns().get(0));
	}

	/** Activate PDM /
	public boolean enterPairwiseDistanceMode(String colNameOfInterest) {
		// are we already in PDM? In which case, we just need to
		// swap the colNameOfInterest around
		if(DisplayPairwiseModel.class.isAssignableFrom(currentTableModel.getClass())) {
			// already in PDM, need to call resort
			DisplayPairwiseModel dpm = (DisplayPairwiseModel) currentTableModel;

			if(isColumn(colNameOfInterest)) {
				if(dpm.resortPairwiseDistanceMode(colNameOfInterest)) {
					updateDisplay();
					return true;
				} else
					return false;
			} else
				return false;		// booh! no such column!
		}

		DisplayPairwiseModel pdm = new DisplayPairwiseModel(matrix, this);
		if(!pdm.enterPairwiseDistanceMode(colNameOfInterest))
			return false;

		currentTableModel = (TableModel) pdm;
		pdm_colName = colNameOfInterest;
		updateDisplay();
		return true;
	}

	/** Deactivate PDM
	 * @throws ClassCastException (?) if you're NOT in PDM when you call this method!
	 /
	public boolean exitPairwiseDistanceMode() {
		DisplayPairwiseModel dpm = (DisplayPairwiseModel) currentTableModel;

		if(!dpm.exitPairwiseDistanceMode())
			return false;

		currentTableModel = new DisplayCountsModel(matrix, this);
		pdm_colName = null;
		updateDisplay();
		return true;
	}

	// just in case
	private void fatalError() {
		new MessageBox(
				matrix.getFrame(),
				"Something went horribly wrong!",
				"There was a programming error in this program. I can't get back to a normal state. I'm going to remove all your sequences, which means you'll lose all your changes. I'm so very sorry. Please let the programmers know, and we'll get working on this immediately. Sorry again!").go();
		clear();
	}
*/
// NOTES:
// 1.	on renameSequence(), the outgroup should 'move' with the rename
// 2.	
