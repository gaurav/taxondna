/**
 * This is a DisplayMode which displays sequences (as returned by dataStore), 
 * as represented by their basepair counts (their "widths").
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
import java.util.regex.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DisplaySequencesMode extends DisplayMode implements ActionListener {
// ALL THE FOLLOWING VARIABLES ARE INHERITED FROM DISPLAYMODE
//	private TableManager	tableManager = null;
//	public static final int additionalColumns 	= 	3;		// Sequence names, total length, and # of charsets
//	private List 	sortedColumns = null;		// note that this INCLUDES the additionalColumns
//	private List 	sortedSequences = null;

	/** 
	 * We need to know the SequenceMatrix we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DisplaySequencesMode(TableManager tableManager) {
		this.tableManager = tableManager;
	
		sortedColumns = null;
		sortedSequences = null;
		additionalColumns = 3;
	}

//
// 1. ACTIVATION/DEACTIVATION.
//
	public void activateDisplay(JTable table) {
		super.activateDisplay(table);

		table.setModel(this);
	}

	public void deactivateDisplay(JTable table) {
		// unfortunately, we can't *unset* ourselves as model.
		// Hopefully, somebody else will pick up on this whole
		// model business.
	}

	public java.util.List getSortedColumns(Set colNames) {
		Vector v = new Vector(colNames);	
		Collections.sort(v);

		v.add(0, "Sequence name");
		v.add(1, "Total length");
		v.add(2, "No of charsets");

		sortedColumns = v;

		return (java.util.List ) v;
	}

	public java.util.List getSortedSequences(Set seqNames) {
		Vector v = new Vector(seqNames);	
		Collections.sort(v);

		String refTaxon = tableManager.getReferenceSequence();
		if(refTaxon != null) {
			v.remove(refTaxon);	// delete the reference taxon
			v.add(0, refTaxon);	// and move it to the top of the list
		}

		sortedSequences = v;

		return (java.util.List ) v;
	}

        public String getValueAt(String colName, String seqName, Sequence seq) {
		// if it's the total length column, return the total length columns
		if(colName.equalsIgnoreCase("Total length"))
			return tableManager.getSequenceLength(seqName) + " bp";

		// if it's the number of charsets column, return that.
		if(colName.equalsIgnoreCase("No of charsets"))
			return tableManager.getCharsetsCount(seqName) + "";

		// okay, it's an actual 'sequence'
		// is it cancelled?
		if(tableManager.isSequenceCancelled(colName, seqName))
			return "(CANCELLED)";

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

	public void setValueAt(String colName, String seqName, Object aValue) {
		return;
	}

//
// INTERFACE
//
	/**
	 * Event: somebody right clicked in the mainTable somewhere
	 */
	public void rightClick(MouseEvent e, int col, int row) {
		String outgroupName = "";	// TODO: Fix me!

//		popupMenu.show((Component)e.getSource(), e.getX(), e.getY());
		String colName = getColumnName(col);
		String rowName = "";
		if(row >= 0)				// we don't use the value of rowName if (row == 0)
			rowName = getRowName(row);

		PopupMenu pm = new PopupMenu();

		pm.add("Column: " + colName);
		MenuItem resizeCol = new MenuItem("Resize column to fit");
		resizeCol.setActionCommand("RESIZE_COLUMN:" + colName);
		pm.add(resizeCol);

		if(col >= additionalColumns) {
			MenuItem delThisCol = new MenuItem("Delete column");
			delThisCol.setActionCommand("COLUMN_DELETE:" + colName);
			pm.add(delThisCol);

			MenuItem pdmThisCol = new MenuItem("Display pairwise distances");
			pdmThisCol.setActionCommand("DO_PDM:" + colName);
			pm.add(pdmThisCol);

		}
		pm.addSeparator();

		if(row <= 0) {
			pm.add("Row: Headers");
		} else {
			pm.add("Row: " + rowName);

			MenuItem delThisRow = new MenuItem("Delete this row");
			delThisRow.setActionCommand("ROW_DELETE:" + rowName);
			pm.add(delThisRow);

			if(outgroupName != null && rowName.equals(outgroupName)) {
				MenuItem makeOutgroup = new MenuItem("Unset this row as the outgroup");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:");
				pm.add(makeOutgroup);		
			} else {
				MenuItem makeOutgroup = new MenuItem("Make this row the outgroup");
				makeOutgroup.setActionCommand("MAKE_OUTGROUP:" + rowName);
				pm.add(makeOutgroup);
			}
		}

		pm.addActionListener(this);

		((JComponent)e.getSource()).add(pm);			// yrch!
		pm.show((JComponent)e.getSource(), e.getX(), e.getY());
	}

	/**
	 * Event: somebody double clicked in the mainTable somewhere
	 */
	public void doubleClick(MouseEvent e, int col, int row) {
		if(row > 0 && col != -1 && col >= additionalColumns) {
			// it's, like, valid, dude.
			tableManager.toggleCancelled(getColumnName(col), getRowName(row));
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

			tableManager.deleteColumn(colName);			
		}

		// Resize a column to fit the widest entry
		if(cmd.length() > 14 && cmd.substring(0, 14).equals("RESIZE_COLUMN:")) {
			String colName = cmd.substring(14);

			tableManager.resizeColumnToFit(colName);
		}

		// Do a PDM on a particular column
		if(cmd.length() > 7 && cmd.substring(0, 7).equals("DO_PDM:")) {
			String colName = cmd.substring(7);

			tableManager.changeDisplayMode(TableManager.DISPLAY_DISTANCES, colName);
		}

		// Delete a particular row
		if(cmd.length() > 11 && cmd.substring(0, 11).equals("ROW_DELETE:")) {
			String seqName = cmd.substring(11);

			tableManager.deleteRow(seqName);
		}

		// Make a particular row into the 'outgroup', i.e. the sequence fixed on
		// top of the sequence listings.
		if(cmd.length() >= 14 && cmd.substring(0, 14).equals("MAKE_OUTGROUP:")) {
			String seqName = cmd.substring(14);

			if(seqName.equals(""))
				tableManager.setReferenceSequence(null);
			else
				tableManager.setReferenceSequence(seqName);
		}
	}
	

//
// OUR MORE IMPORTANT FUNCTIONS
//

}
