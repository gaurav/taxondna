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

import java.util.*;
import java.util.regex.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DisplaySequencesMode extends DisplayMode {
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

	public List getSortedColumns(Set colNames) {
		Vector v = new Vector(colNames);	
		Collections.sort(v);

		v.add(0, "Sequence name");
		v.add(1, "Total length");
		v.add(2, "No of charsets");

		sortedColumns = v;

		return (List) v;
	}

	public List getSortedSequences(Set seqNames) {
		Vector v = new Vector(seqNames);	
		Collections.sort(v);

		sortedSequences = v;

		return (List) v;
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
// OUR MORE IMPORTANT FUNCTIONS
//

}
