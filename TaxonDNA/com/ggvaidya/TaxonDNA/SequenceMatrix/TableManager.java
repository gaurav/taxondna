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

public class TableManager implements ActionListener {

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
	private JTextField	tf_statusBar = 	new JTextField();

	// toolbar
	ToolbarManager		toolbarManager =	new ToolbarManager(this);

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
	private java.util.List	sortedCharsets =	null;
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

			resizeColumnToFit(colName, false);
		}
	}

	/**
	 * Resizes column 'x' to fit the widest entry, allowing shrinking to
	 * occur. Java, meet default arguments. Default arguments, meet Java.
	 */
	public void resizeColumnToFit(String x) {
		resizeColumnToFit(x, true);
	}

	/**
	 * Resizes column 'x' to fit the widest entry.
	 *
	 * @param shrinkAllowed Is shrinking of columns allowed?
	 */
	public void resizeColumnToFit(String x, boolean shrinkAllowed) {
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

		if(!shrinkAllowed) {
			if(maxLength < tc.getPreferredWidth())
				return;				// dont shrink it!
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

	public boolean warned(String title, String msg) {
		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				title,
				msg,
				MessageBox.MB_YESNOTOALL |
				MessageBox.MB_TITLE_IS_UNIQUE);

		if(mb.showMessageBox() == MessageBox.MB_YES)
			return true;

		return false;
	}

	public void deleteColumn(String colName) {
		if(warned("Delete this column?", "Are you sure you wish to delete column '" + colName + "'?")) {
			dataStore.deleteColumn(colName);
			updateDisplay();
		}
	}

	public void deleteRow(String seqName) {
		if(warned("Delete this row?", "Are you sure you wish to delete sequence '" + seqName + "'?")) {
			dataStore.deleteRow(seqName);
			updateDisplay();
		}
	}

	public void cancelRow(String seqName) {
		boolean changed = false;
		
		Iterator i = getCharsets().iterator();	
		while(i.hasNext()) {
			String colName = (String) i.next();

			if(!dataStore.isSequenceCancelled(colName, seqName)) {
				dataStore.toggleCancelled(colName, seqName);
				changed = true;
			}
		}

		if(changed)
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

	public java.util.List getCharsets() {
		if(sortedCharsets == null) {
			sortedCharsets = new Vector(sortedColumns);

			for(int x = 0; x < currentDisplayMode.additionalColumns; x++) {
				sortedCharsets.remove(0);
			}
		}
		return sortedCharsets;
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
//		Hashtable widths = null;
		if(currentDisplayMode != null) {
//			widths = currentDisplayMode.saveWidths();			
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

		updateDisplay();	// fire!
		
//		if(currentDisplayMode != null && widths != null)
//			currentDisplayMode.restoreWidths(widths);
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
		sortedCharsets = null;
		sortedSequences = dm.getSortedSequences(dataStore.getSequences());

		return dm;
	}

	/** 
	 * UpdateDisplay calls on current DisplayMode to do its duty.
	 */ 
	public void updateDisplay() {
		sortedColumns = currentDisplayMode.getSortedColumns(dataStore.getColumns());
		sortedCharsets = null;
		sortedSequences = currentDisplayMode.getSortedSequences(dataStore.getSequences());

		Hashtable widths = currentDisplayMode.saveWidths();
		currentDisplayMode.updateDisplay();
		currentDisplayMode.restoreWidths(widths);

		resizeColumns();
		updateStatusBar();
	}

//
// X.	HACKS. These might have to go eventually
//
	public int countCancelledSequences() {
		return dataStore.getCancelledSequencesCount();
	}

	public TableModel getTableModel() {
		return (TableModel) currentDisplayMode;
	}

//
// USER INTERFACE CODE
//
	public JToolBar getToolbar() {
		JToolBar toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolbarManager.setToolbar(toolBar, table);
		return toolBar;
	}

	public JPanel getStatusBar() {
		JPanel statusbar = new JPanel();

		statusbar.setLayout(new BorderLayout());
		statusbar.add(tf_statusBar);

		tf_statusBar.setFont(new Font("SansSerif", Font.PLAIN, 12));

		return statusbar;
	}

	public void updateStatusBar() {
		StringBuffer buff = new StringBuffer("  ");	// start with a bit of space

		int charsets = getCharsets().size();
		if(charsets == 0) {
			buff.append("No sequences loaded.");
		} else {
			buff.append(sortedSequences.size() + " sequences across " + charsets + " character sets. " + countCancelledSequences() + " cancelled sequences.");
		}

		buff.append(' ');
		if(currentDisplayMode != null)
			currentDisplayMode.setStatusBar(buff);
		tf_statusBar.setText(buff.toString());
	}

	public void defaultRightClick(MouseEvent e, int col, int row) {
//		popupMenu.show((Component)e.getSource(), e.getX(), e.getY());
		String colName = currentDisplayMode.getColumnName(col);
		String rowName = "";
		if(row >= 0)				// we don't use the value of rowName if (row == 0)
			rowName = currentDisplayMode.getRowName(row);

		PopupMenu pm = new PopupMenu();

		pm.add("Column: " + colName);
		MenuItem resizeCol = new MenuItem("Resize column to fit");
		resizeCol.setActionCommand("RESIZE_COLUMN:" + colName);
		pm.add(resizeCol);

		if(col >= currentDisplayMode.additionalColumns) {
			MenuItem delThisCol = new MenuItem("Delete column");
			delThisCol.setActionCommand("COLUMN_DELETE:" + colName);
			pm.add(delThisCol);

			MenuItem pdmThisCol = new MenuItem("Display pairwise distances");
			pdmThisCol.setActionCommand("DO_PDM:" + colName);
			pm.add(pdmThisCol);

		}
		pm.addSeparator();

		if(row < 0) {
			pm.add("Row: Headers");
		} else {
			pm.add("Row: " + rowName);

			MenuItem delThisRow = new MenuItem("Delete this row");
			delThisRow.setActionCommand("ROW_DELETE:" + rowName);
			pm.add(delThisRow);

			String outgroupName = getReferenceSequence();

			if(outgroupName != null && rowName.equals(outgroupName)) {
				MenuItem makeOutgroup = new MenuItem("Unset this row as the reference taxon");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:");
				pm.add(makeOutgroup);
			} else {
				MenuItem makeOutgroup = new MenuItem("Make this row the reference taxon");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:" + rowName);
				pm.add(makeOutgroup);
			}
		}

		pm.addActionListener(this);

		((JComponent)e.getSource()).add(pm);			// yrch!
		pm.show((JComponent)e.getSource(), e.getX(), e.getY());
	}

	public void defaultDoubleClick(MouseEvent e, int col, int row) {
		if(row > 0 && col != -1 && col >= currentDisplayMode.additionalColumns) {
			// it's, like, valid, dude.
			toggleCancelled(currentDisplayMode.getColumnName(col), currentDisplayMode.getRowName(row));
		}
	}

	/**
	 * ActionEvents generated by the rightClick and doubleClick
	 */
	public void actionPerformed(ActionEvent e) {
		// 
		// ACTION COMMANDS FOR THE MAIN TABLE POPUP MENU
		// Basically, these popups create certain action commands
		// like 'COLUMN_DELETE:colName', which we can then parse
		// and use to do things.
		//
		// These should probably be replaced by String.regionMatch...(),
		// but it's plenty understandable (if ugly and verbose), and most
		// importantly, it DOES THE JOB. So it stays.
		//
		String cmd = e.getActionCommand();

		// Delete a column
		if(cmd.length() > 14 && cmd.substring(0, 14).equals("COLUMN_DELETE:")) {
			String colName = cmd.substring(14);

			deleteColumn(colName);			
		}

		// Resize a column to fit the widest entry
		if(cmd.length() > 14 && cmd.substring(0, 14).equals("RESIZE_COLUMN:")) {
			String colName = cmd.substring(14);

			resizeColumnToFit(colName);
		}

		// Do a PDM on a particular column
		if(cmd.length() > 7 && cmd.substring(0, 7).equals("DO_PDM:")) {
			String colName = cmd.substring(7);

			changeDisplayMode(TableManager.DISPLAY_DISTANCES, colName);
		}

		// Delete a particular row
		if(cmd.length() > 11 && cmd.substring(0, 11).equals("ROW_DELETE:")) {
			String seqName = cmd.substring(11);

			deleteRow(seqName);
		}

		// Make a particular row into the 'outgroup', i.e. the sequence fixed on
		// top of the sequence listings.
		if(cmd.length() >= 14 && cmd.substring(0, 14).equals("MAKE_OUTGROUP:")) {
			String seqName = cmd.substring(14);

			if(seqName.equals(""))
				setReferenceSequence(null);
			else
				setReferenceSequence(seqName);
		}
	}	
}

/**
 * Manages the Toolbar.
 *
 * TODO: Wire this into the Table's selection interfaces rather than
 * into the MouseListener.			
 */
class ToolbarManager implements ActionListener {
	private TableManager	tm 	=	null;
	private JTable		table 	=	null;
		
	private JToolBar	toolbar    =	null;
	private JTextField	tf_colName = 	new JTextField(20);
	private JTextField	tf_seqName = 	new JTextField(20);

	public ToolbarManager(TableManager tm) {
		this.tm = tm;
	}

	public void setToolbar(JToolBar toolBar, JTable table) {
		toolbar = toolBar;
		this.table = table;
		initToolbar();
	}

	public void initToolbar() {
		// now, we set up the fields and a button
		// we have a single function which sets the whole scheboodle,
		// and we use setActionCommand() to make sure these
		// buttons generate the same events, etc.
		tf_colName.setEditable(false);
		toolbar.add(tf_colName);

		JButton btn = null;

		btn = new JButton("Delete column");
		btn.addActionListener(this);
		toolbar.add(btn);

		btn = new JButton("Do PDM");
		btn.addActionListener(this);
		toolbar.add(btn);
		
		tf_seqName.setEditable(false);
		toolbar.add(tf_seqName);

		btn = new JButton("Delete taxon");
		btn.addActionListener(this);
		toolbar.add(btn);

		btn = new JButton("Cancel entire taxon");
		btn.addActionListener(this);
		toolbar.add(btn);
		setToolbarStatus("", "");

		table.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				int colIndex = table.columnAtPoint(e.getPoint());
				int rowIndex = table.rowAtPoint(e.getPoint());

				String colName = (String) table.getColumnName(colIndex);
				String seqName = (String) table.getValueAt(rowIndex, 0);

				if(colName == null)	colName = "";
				if(seqName == null)	seqName = "";

				setToolbarStatus(colName, seqName);
			}	
		});
	}

	private String	currentColName =	null;
	private String	currentCharsetName =	null;
	private String	currentSeqName =	null;

	public void clearToolbarStatus() {
		setToolbarStatus("", "");
	}

	public void setToolbarStatus(String colName, String seqName) {
		if(colName != null && !colName.equals("")) {
			tf_colName.setText(colName);

			currentColName = colName;	
			if(tm.getCharsets().contains(colName)) {
				// it's a character set
				currentCharsetName = colName;		
			} else {
				// it's an 'additional column'
				currentCharsetName = null;
			}
		} else {
			currentCharsetName = null;
			currentColName = null;
		}

		if(seqName != null && !seqName.equals("")) {
			tf_seqName.setText(seqName);
			currentSeqName = seqName;
		} else {
			currentSeqName = null;
		}
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if(cmd.equals("Delete column")) {
			if(currentCharsetName != null)
				tm.deleteColumn(currentCharsetName);
		}

		if(cmd.equals("Do PDM")) {
			if(currentCharsetName != null)
				tm.changeDisplayMode(TableManager.DISPLAY_DISTANCES, currentCharsetName);
		}

		if(cmd.equals("Delete taxon")) {
			if(currentSeqName != null)
				tm.deleteRow(currentSeqName);
			
			// TODO: Use table.changeSelection() to move to the next row
		}

		if(cmd.equals("Cancel entire taxon")) {
			if(currentSeqName != null)
				tm.cancelRow(currentSeqName);
		}
	}
}
