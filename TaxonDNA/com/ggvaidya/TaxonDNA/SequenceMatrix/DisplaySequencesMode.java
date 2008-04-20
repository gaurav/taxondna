/**
 * This is a DisplayMode which displays sequences (as returned by dataStore), 
 * as represented by their basepair counts (their "widths").
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

public class DisplaySequencesMode extends DisplayMode implements ItemListener {
// ALL THE FOLLOWING VARIABLES ARE INHERITED FROM DISPLAYMODE
//	private TableManager	tableManager = null;
//	public static final int additionalColumns 	= 	3;		// Sequence names, total length, and # of charsets
//	private List 	sortedColumns = null;		// note that this INCLUDES the additionalColumns
//	private List 	sortedSequences = null;

	// Currently selected CheckboxMenuItem
	private CheckboxMenuItem 	last_chmi = null;
	private CheckboxMenuItem	chmi_name = null;
	private CheckboxMenuItem	chmi_name_gi = null;
	private CheckboxMenuItem	chmi_species = null;
	private CheckboxMenuItem	chmi_charsets = null;
	private CheckboxMenuItem	chmi_length = null;

	private Comparator 		currentComparator = new SortByName();
	private String			sortBy = "name";

	private boolean			display_gi_noes = false;

//
// INNER CLASSES
//
	/**
	 * A convenience function which checks to see if either name1 or name2 are the
	 * outgroupName, in which case they'll get sorted up.
	 /
	private int checkForOutgroup(String name1, String name2) {
		String outgroupName = tm.getReferenceSequence();

		if(outgroupName == null)
			return 0;

		if(name1.equalsIgnoreCase(outgroupName))
			return -1;
		if(name2.equalsIgnoreCase(outgroupName))
			return +1;

		return 0;
	}
	*/

	/**
	 * A sort Comparator which sorts a collection of Strings in natural order - except that outgroups get
	 * sorted to the top.
	 */
	private class SortByName implements Comparator {
		public int	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

			return str1.compareTo(str2);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings by - of all things - their SECOND name. Such is life.
	 */
	private class SortBySecondName implements Comparator {
		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

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
	 */
	private class SortByCharsets implements Comparator {
		private TableManager tm = null;

		public SortByCharsets(TableManager tm) {
			this.tm = tm;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;
			
			int countCharsets1 = tm.getCharsetsCount(str1);
			int countCharsets2 = tm.getCharsetsCount(str2);

			return (countCharsets2 - countCharsets1);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings (really taxon names) by the total actual count of bases.
	 */
	private class SortByTotalActualLength implements Comparator {
		private TableManager tm = null;

		public SortByTotalActualLength(TableManager tm) {
			this.tm = tm;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;
			
			int count1 = tm.getSequenceLength(str1);
			int count2 = tm.getSequenceLength(str2);

			return (count2 - count1);
		}
	}

//
// CONSTRUCTOR
//
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
	private TableCellRenderer static_oldRenderer = null;
	public void activateDisplay(JTable table, Object argument) {
		super.activateDisplay(table, argument);

		static_oldRenderer = table.getDefaultRenderer(String.class);
		table.setDefaultRenderer(String.class, new SequencesColorRenderer(this));

		table.setModel(this);
	}

	public void deactivateDisplay(JTable table) {
		// unfortunately, we can't *unset* ourselves as model.
		// Hopefully, somebody else will pick up on this whole
		// model business.
		
		// reset the default renderer
		table.setDefaultRenderer(String.class, static_oldRenderer);	// back to before

		super.deactivateDisplay();
	}

	public java.util.List getAdditionalColumns() {
		Vector v = new Vector();

		v.add(0, "Sequence name");
		v.add(1, "Total length");
		v.add(2, "No of charsets");

		return v;
	}

	public java.util.List getSortedColumns(Set colNames) {
		Vector v = new Vector(colNames);	
		Collections.sort(v);
		sortedColumns = v;

		return (java.util.List ) v;
	}

	public java.util.List getSortedSequences(Set seqNames) {
		Vector v = new Vector(seqNames);	
		Collections.sort(v, currentComparator);

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
			return "(No data)";
		
		// if GI is on, display GI!
		if(display_gi_noes) {
			String gi = seq.getGI();
			if(gi != null)
				return "GI:" + gi;
			else
				return "GI:unknown";
		}

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
		super.rightClick(e, col, row);		// default
	}

	/**
	 * Event: somebody double clicked in the mainTable somewhere
	 */
	public void doubleClick(MouseEvent e, int col, int row) {
		super.doubleClick(e, col, row);
	}	

	public Menu getDisplayModeMenu() {
		Menu m = new Menu("Sequences");

		if(chmi_name == null) {		// not initialised?
			chmi_name = new CheckboxMenuItem("Sort by name");
			chmi_name.addItemListener(this);

			chmi_name_gi = new CheckboxMenuItem("Sort by name, but display GI numbers");
			chmi_name_gi.addItemListener(this);

			chmi_species = new CheckboxMenuItem("Sort by species epithet");
			chmi_species.addItemListener(this);

			chmi_charsets = new CheckboxMenuItem("Sort by number of character sets");
			chmi_charsets.addItemListener(this);

			chmi_length = new CheckboxMenuItem("Sort by total length");
			chmi_length.addItemListener(this);
		}

		m.add(chmi_name);
		m.add(chmi_name_gi);
		m.add(chmi_species);
		m.add(chmi_charsets);
		m.add(chmi_length);

		if(last_chmi == null) {
			chmi_name.setState(true);	// tick Sort by name by default
			last_chmi = chmi_name;		// this is currently selected
		} else {
			last_chmi.setState(true);	// just in case
		}

		return m;
	}

	public void itemStateChanged(ItemEvent e) {
		CheckboxMenuItem chmi = (CheckboxMenuItem) e.getSource();

		if(last_chmi != null) {
			last_chmi.setState(false);
		}

		chmi.setState(true);
		last_chmi = chmi;

		// just in case
		currentComparator = new SortByName();
		sortBy = "name";
		display_gi_noes = false;
		if(chmi.equals(chmi_name_gi)) {
			// okay, we keep the default
			// but turn on display_gi_noes
			display_gi_noes = true;
		}
		if(chmi.equals(chmi_species)) {
			currentComparator = new SortBySecondName();
			sortBy = "species epithet";
		}
		if(chmi.equals(chmi_charsets)) {
			currentComparator = new SortByCharsets(tableManager);
			sortBy = "number of charsets";
		}
		if(chmi.equals(chmi_length)) {
			currentComparator = new SortByTotalActualLength(tableManager);
			sortBy = "total length";
		}

		tableManager.updateDisplay();
	}

	public void setStatusBar(StringBuffer buff) {
		if(sortBy == null)
			return;

		buff.append("Sorted by " + sortBy + ".");
	}
}

class SequencesColorRenderer extends DefaultTableCellRenderer
{
	DisplaySequencesMode dsm = null;

	public SequencesColorRenderer(DisplaySequencesMode dsm) {
		this.dsm = dsm;
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
		comp.setOpaque(true);
		comp.setForeground(Color.BLACK);
		comp.setBackground(Color.WHITE);

		if(row < 0 || col < dsm.additionalColumns) {
			// if the row is invalid, or the column is not one of the sequence columns
			return comp;
		}

		// okay, our mission here is ridiculously simple
		// we make all 'cancelled' cells slightly gray
//		System.err.println("Wokay: '" + value + "', isSelected = " + isSelected + ", hasFocus = " + hasFocus);
		if(((String)value).equalsIgnoreCase("(CANCELLED)")) {
			comp.setBackground(Color.GRAY);
		}

        	return comp;
    }
}
