/**
 * A DisplayMode handles the actual display of content. It does this by
 * being a TableModel, so the JTable can just use this as its backing
 * database. The trick is to make sure that the JTable doesn't realise
 * that the model is coming from three completely different objects.
 *
 * The way we're going to try to play this is like this: every 
 * DisplayMode inherits from this class, so they all have a repertoire
 * of functions they don't need to reimplement, etc. They will
 * also have a set of functions that TableManager can use as an
 * interface to talk to them - for instance, something like
 * activateDisplay(JTable) will allow the class to set itself up
 * as the TableModel (and active display), then have a deactivateDisplay()
 * to turn it off or something. Or something.
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

import java.util.*;	// Vectors, Lists and the like

import java.awt.Menu;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.Model.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public abstract class DisplayMode implements TableModel, MouseListener {
// The Table we're displaying onto
	/** Please do put your JTable value here, or put in 'null' if you're doing something funky.
	 * There's several functions who expect to find the table here.
	 */
	// my first protected variable! ::sniffs::
	protected TableManager		tableManager =		null;
	protected JTable		table =			null;

// Table model listeners
	private Vector			tableModelListeners =	new Vector();

//
// 0.	OVERLOADED CODE. Here's how it works: if you want, you can just overload
// 	the heck out of everybody (by which I mean the TableModel functions) and
// 	just BE a table model - just remember to update the following two 
// 	functions, so that the exporter can figure things out, and we'll all be
// 	happy.
//
// 	HOWEVER, in the default implementation, we handle a lot of the grunge work
// 	out here. I mean, if you DON'T overload the TableModel functions, DisplayMode 
// 	will do all the work for you. In brief: the only functions you NEED to overload
// 	are the following two. All else will be defaulted for you.
//
	protected List 	sortedColumns = null;		// note that this DOES NOT INCLUDE the additionalColumns
	protected List 	sortedSequences = null;
	protected int	additionalColumns = 0;

	public abstract List getAdditionalColumns();
	public abstract List getSortedColumns(Set colNames);
	public abstract List getSortedSequences(Set seqNames);
	public abstract String getValueAt(String colName, String seqName, Sequence seq);
	public abstract void setValueAt(String colName, String seqName, Object aValue);

//
// 1.	GETTERS. Returns the state or instance variable of the table
// 	at the moment.
//
	public Class getColumnClass(int columnIndex) { 
		return String.class; 
	}

	public int getColumnCount() {
		return 
			sortedColumns.size()		// the number of sorted columns
			+ additionalColumns;		// the number of additional columns
	
	}

	/**
	 * Note that this function will return the *absolute* index (it must,
	 * it IS in the interface). So do remember that when you use this
	 * function, alright children?
	 */
	public String getColumnName(int columnIndex) {
		if(columnIndex < additionalColumns)
			return (String) getAdditionalColumns().get(columnIndex);

		return (String) sortedColumns.get(columnIndex - additionalColumns);
	}

	// not in interface: just very convenient :)
	public String getRowName(int rowIndex) {
		return (String) sortedSequences.get(rowIndex);
	}

	public int getRowCount() {
		return sortedSequences.size();
	}

	public Object getValueAt(int rowIndex, int columnIndex) {
		String colName = getColumnName(columnIndex);
		String rowName = getRowName(rowIndex);

		if(columnIndex == 0) {
			return getRowName(rowIndex);

		} else if(columnIndex < additionalColumns) {
			return getValueAt(colName, rowName, null);
		} else {
			Sequence seq = tableManager.getSequence(colName, rowName);

			return getValueAt(colName, rowName, seq);
		}
	}

	public boolean isCellEditable(int rowIndex, int columnIndex) {
		if(columnIndex == 0)	// yes, you can rename the sequence names
			return true;
		return false;		// but nothing else (by default)
	}

// 
// 2.	SETTERS. Lets you set states or variables for us.
//
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if(columnIndex == 0)
			tableManager.renameSequence(getRowName(rowIndex), (String)aValue);
		else
			setValueAt(getColumnName(columnIndex), getRowName(rowIndex), aValue);
	}


//
// 3.	FUNCTIONAL CODE. Does something.
//
	
	/** 
	 * Saves the column widths into a Hashtable. You can use restoreWidths() to
	 * restore your widths to where they were a while ago.
	 */
	protected Hashtable saveWidths() {
		Hashtable widths = new Hashtable();
		JTable j = table;
		if(j == null)
			return null;

		// save all widths
		TableColumnModel tcm = j.getColumnModel();
		if(tcm == null)
			return null;

		Enumeration e = tcm.getColumns();
		while(e.hasMoreElements()) {
			TableColumn tc = (TableColumn) e.nextElement();
			widths.put(tc.getIdentifier(), new Integer(tc.getWidth()));
		}

		return widths;
	}

	/**
	 * Restores the column widths saved into the Hashtable as the column widths
	 * on the JTable.
	 */
	protected void restoreWidths(Hashtable widths) {
		if(widths == null)
			return;
		
		JTable j = table;
		if(j == null)
			return;
		
		// load all widths
		TableColumnModel tcm = j.getColumnModel();
		if(tcm == null)
			return;

		Enumeration e = tcm.getColumns();
		while(e.hasMoreElements()) {
			TableColumn tc = (TableColumn) e.nextElement();

			Integer oldWidth = (Integer) widths.get(tc.getIdentifier());
			if(oldWidth != null)
				tc.setPreferredWidth(oldWidth.intValue());
		}	
	}


//
// 4.	TABLE MODEL LISTENERS.
//
	/** 
	 * Adds a new TableModelListener. Since much funkiness could (and probably will!)
	 * happen with TableModels being set repeatedly, it makes sense to check that
	 * we're not double adding a single listener.
	 */
	public void addTableModelListener(TableModelListener l) {
		if(!tableModelListeners.contains(l))
			tableModelListeners.add(l);
	}

	/**
	 * Removes a TableModelListener.
	 */
	public void removeTableModelListener(TableModelListener l) {
		tableModelListeners.remove(l);
	}

//
// 5.	DISPLAY MODE METHODS	
//
	/** Overload this one if you need an argument as well. */
	public void activateDisplay(JTable table, Object argument) {
		this.table = table;
		table.addMouseListener(this);
		table.getTableHeader().addMouseListener(this);
		table.setModel(this);
	}

	/** Activate this display on the table mentioned. */
	public void activateDisplay(JTable table) {
		activateDisplay(table, null);
	}

	/** Deactivate this display from the table mentioned. */
	public void deactivateDisplay() {
//		table.setModel(null);		-- CANNOT: you gotta remember this yourself!
		table.getTableHeader().removeMouseListener(this);
		table.removeMouseListener(this);
		this.table = null;
	}

	/** Update the display (generally by firing an event at all the tableModelListeners). 
	 * Feel free to overload this if you think you've got a better idea - but PLEASE
	 * remember to save and reload the table headers before you do!
	 */
	public void updateDisplay() {
		Hashtable widths = saveWidths();
		fireTableModelEvent(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
		restoreWidths(widths);
	}

	/**
	 * Send a TableModelEvent to every listener. 
	 */
	public void fireTableModelEvent(TableModelEvent e) {
		Iterator i = tableModelListeners.iterator();
		while(i.hasNext()) {
			TableModelListener l = (TableModelListener)i.next();	

			l.tableChanged(e);
		}
	}

//
//	USER INTERFACE: MOUSE. What would you do if I clicked on a cell?
//	Overload rightClick() and leftClick(), but don't overload the
//	MouseListener code ... it's not worth the effort, really.
//
	public void rightClick(MouseEvent e, int columnIndex, int rowIndex) {
		tableManager.defaultRightClick(e, columnIndex, rowIndex);
	}

	public void doubleClick(MouseEvent e, int columnIndex, int rowIndex) {
		tableManager.defaultDoubleClick(e, columnIndex, rowIndex);
	}

//
// 	USER INTERFACE: STATUS BAR. 
//
	public void setStatusBar(StringBuffer buff) {
		buff.append("Currently in " + this.getClass());
	}

//
// 	USER INTERFACE: ADDITIONAL PANEL.
// 	Yrch!
//
	public JPanel getAdditionalPanel() {
		return null;
	}

	/**
	 * You can set a menu to be displayed in the main menubar when this
	 * display mode is selected. Don't worry about the details: the
	 * tableManager will query this either just after or just before 
	 * activating the display mode. You are responsible for adding any
	 * actionListeners which need adding. If you return 'null', no
	 * menu will be created or anything.
	 *
	 * Temporary note: Note that we expect a MENU, with a NAME. This
	 * behavior might change in the future. Apologies.
	 * 
	 */
	public Menu getDisplayModeMenu() {
		return null;
	}

// MOUSELISTENER. Don't touch this, or rightClick()/leftClick() mightn't work
	public void mouseClicked(MouseEvent e) {
		// either TableHeader or table will work (fine)
		if(e.getSource().equals(table.getTableHeader()) || e.getSource().equals(table)) {
			int colIndex = table.columnAtPoint(e.getPoint());
			int rowIndex = table.rowAtPoint(e.getPoint());

                        // Hmm, doesn't seem to be in the table. Weird. Ignore.
                        if(colIndex == -1 || rowIndex == -1)
                            return;

			// somehow, tablehandlers generate BOTH mouseClicked and mouseReleased,
			// while tables don't. So ...
			if(e.getSource().equals(table.getTableHeader())) {
				if(e.getID() == MouseEvent.MOUSE_RELEASED)
					return;
			}

			// if we're in the TableHeader, we're automatically popup 
			boolean popup = e.isPopupTrigger();
			if(e.getSource().equals(table.getTableHeader()))
				popup = true;
			
			// check for right clicks
			if(popup)
				rightClick(e, colIndex, rowIndex);

			// check for double clicks (but not both!)
			else if(e.getClickCount() == 2)
				doubleClick(e, colIndex, rowIndex);
		}
	}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {} 
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {
		// MS Windows needs this; also, Windows generates both mouseReleased 
		// and mouseClicked events when double clicking. So, if and only if
		// this is a right click activator, do we pass it on.
		//
		if(e.isPopupTrigger())
			mouseClicked(e);
	}
}
