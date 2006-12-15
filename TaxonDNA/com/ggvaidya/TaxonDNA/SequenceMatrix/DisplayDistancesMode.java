/**
 * This is a DisplayMode which displays the sequences (as returned by dataStore) by
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

import java.net.*;		// for URLConnection, etc.
import java.lang.reflect.*;	// Reflection

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

public class DisplayDistancesMode extends DisplayMode { 
	public static final int	SELECTED_COLUMN		=	3;		// change this if the no of additionalColumns increase!
	private String		selected_colName	=	null;		// the colName who is leftmost

	private double		distances[][]		=	null;
	private double		norm_distances[][]	=	null;
	private int		ranks[][]		=	null;

	public static double	DIST_ILLEGAL		=	-32.0;
	public static double	DIST_NO_COMPARE_SEQ 	=	-64.0;
	public static double	DIST_SEQ_NA		=	-128.0;
	public static double	DIST_NO_OVERLAP		=	-256.0;	
	public static double	DIST_SEQ_ON_TOP		=	-1024.0;
	public static double 	DIST_CANCELLED		=	-2048.0;

	private List		scores			=	null;

	private class Score implements Comparable {
			private String seqName_top = "";
			private String seqName = "";
			private double pairwise = 0.0;
			private int constant = +1;

			public Score(String seqName, double pairwise) {
				this.seqName = seqName;
				this.pairwise = pairwise;

				seqName_top = tableManager.getReferenceSequence();
			}
			
			public Score(String seqName, double pairwise, int constant) {
				this(seqName, pairwise);
				this.constant = constant;
			}

			private String getName()	{	return seqName;		}
			private double getPairwise() 	{	return pairwise;	}

			public int compareTo(Object o) {
				Score s = (Score) o;

				if(getName().equals(seqName_top))
					return -1;

				if(s.getName().equals(seqName_top))
					return +1;

				return constant * ((int)(com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(getPairwise()) 
					- com.ggvaidya.TaxonDNA.DNA.Settings.makeLongFromDouble(s.getPairwise())));
			}
		};

//
// 1.	CONSTRUCTOR
//

	/** 
	 * We need to know the TableManager we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DisplayDistancesMode(TableManager tm) {
		tableManager = tm;
		additionalColumns = 3;	// Sequence name, total length, and # of charsets
	}

//
// 2.	INITIALIZATION CODE
//
	private TableCellRenderer static_oldRenderer = null;
	public void activateDisplay(JTable table, Object argument) {
		super.activateDisplay(table, argument);

		selected_colName = (String) argument; 
		System.err.println("DDM go: colname = " + selected_colName);

		static_oldRenderer = table.getDefaultRenderer(String.class);
		table.setDefaultRenderer(String.class, new PDMColorRenderer(this));

	}

	public void deactivateDisplay() {
//		table.setModel(null);		-- CANNOT - you better pick it up on the next activateDisplay()!
		table.setDefaultRenderer(String.class, static_oldRenderer);	// back to before
	}

	public List getSortedColumns(Set colNames) {
		Vector v = new Vector(colNames);
		Collections.sort(v);

		v.add(0, "Sequence name");
		v.add(1, "Total score");
		v.add(2, "No of charsets");

		if(selected_colName != null) {
			v.remove(selected_colName);
			v.add(3, selected_colName);
		} else {
			// no selected colname!
			// pick sequence #3
			if(v.size() > 3)
				selected_colName = (String) v.get(3);
		}

		sortedColumns = v;

		return (java.util.List ) v;
	}

	public List getSortedSequences(Set sequences) {
		// step 1: copy whichever sequence we've been told to put on top
		String seqName_top = tableManager.getReferenceSequence();

		// step 2: copy out a basic sequencesList 
		List sequencesList = new Vector(sequences);
		List columnList = new Vector(sortedColumns);
		columnList.remove(0);		// get rid of "Sequence name"
		columnList.remove(0);		// get rid of "Total score"
		columnList.remove(0);		// get rid of "No of charsets"

		if(seqName_top == null) {
			if(sequencesList.size() > 0)
				seqName_top = (String) sequencesList.get(0);	
		}

		if(columnList == null || selected_colName == null || seqName_top == null) {
			// something's not right; bail out
			sortedSequences = sequencesList;

			return sequencesList;
		}
		
		// Okay: NOW, we need to calculate all the distances! Isn't this fun?!
		distances = new double[columnList.size()][sequencesList.size()];	// TODO: catch OutOfMemory here?

		double max[] = new double[columnList.size()];
		Arrays.fill(max, -1.0);
		double min[] = new double[columnList.size()];
		Arrays.fill(min, +2.0);

		// pass 1: calculate all the distances, figure out max and min
		for(int x = 0; x < columnList.size(); x++) {
			String colName = (String) columnList.get(x);

			for(int y = 0; y < sequencesList.size(); y++) {
				String seqName = (String) sequencesList.get(y);

				Sequence seq = tableManager.getSequence(colName, seqName);
				Sequence seq_compare = tableManager.getSequence(colName, seqName_top);

				double dist = DIST_ILLEGAL;
				if(seq_compare == null) {
					dist = DIST_NO_COMPARE_SEQ;
				}
				else if(seqName_top.equals(seqName)) {
					dist = DIST_SEQ_ON_TOP;
				} else if(seq == null) {
					if(tableManager.isSequenceCancelled(colName, seqName))
						dist = DIST_CANCELLED;
					else
						dist = DIST_SEQ_NA;
				}
				else if((dist = seq.getPairwise(seq_compare)) < 0) {
					// illegal!
					dist = DIST_NO_OVERLAP;
				}

				distances[x][y] = dist;

				if(dist >= 0) {
					if(dist > max[x])
						max[x] = dist;

					if(dist < min[x])
						min[x] = dist;
				}
			}
		}

		// pass 2: normalise this
		norm_distances = new double[columnList.size()][sequencesList.size()];

		for(int x = 0; x < columnList.size(); x++) {
			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					norm_distances[x][y] = (distances[x][y] - min[x])/(max[x] - min[x]);
				else
					norm_distances[x][y] = distances[x][y];	// save the <0's
			}
		}

		// pass 3: now, we've got rationalised numbers
		// we need to figure out the average of THESE, and use THIS to sort the
		// sequenceList.
		scores = (List) new Vector();

		for(int y = 0; y < sequencesList.size(); y++) {
			String seqName = (String) sequencesList.get(y);
			
			double totalScore = 0.0;
			int count = 0;

			for(int x = 0; x < columnList.size(); x++) {
				String colName = (String) columnList.get(x);

				if(colName.equals(selected_colName))
					continue;

				if(distances[x][y] >= 0) {
					totalScore += norm_distances[x][y];
					count++;
				}
			}

			totalScore = totalScore/count;

			scores.add(new Score(seqName, totalScore));
		}

		// sort out the sequence names ...
		Collections.sort(scores);

		double[][] old_distances = distances;		// ditto (see below)
		double[][] old_norm_distances = norm_distances;	// I'm just saving a pointer, I hope
		norm_distances = new double[columnList.size()][scores.size()];	// and writing over the old POINTER,
										// NOT the old data. I hope.
		distances = new double[columnList.size()][scores.size()];	// ditto (see above)

		// ... and resort the distances[][] table.
		Iterator i = scores.iterator();	
		int seqIndex = 0;
		while(i.hasNext()) {
			String seqName = ((Score) i.next()).getName();

			int oldIndex = sequencesList.indexOf(seqName);

			for(int x = 0; x < columnList.size(); x++) {
				distances[x][seqIndex] = old_distances[x][oldIndex];
				norm_distances[x][seqIndex] = old_norm_distances[x][oldIndex];
			}

			seqIndex++;
		}

		old_distances = null;	// free the array	
		old_norm_distances = null;
		sequencesList = (List) new Vector();

		i = scores.iterator();
		while(i.hasNext()) {
			String seqName = ((Score) i.next()).getName();

			sequencesList.add(seqName);
		}

		// Now that we have a definite list, nicely synced up and
		// everything, we can figure out the rank table!
		//
		ranks = new int[columnList.size()][sequencesList.size()];
		for(int x = 0; x < columnList.size(); x++) {
			LinkedList ll = new LinkedList();		// easier to append

			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					ll.add(new Double(distances[x][y]));
			}

			Collections.sort(ll);	

			for(int y = 0; y < sequencesList.size(); y++) {
				if(distances[x][y] >= 0)
					ranks[x][y] = ll.indexOf(new Double(distances[x][y]));
				else
					ranks[x][y] = (int) Math.floor(distances[x][y]);
			}
		}

		sortedSequences = sequencesList;
		return sequencesList;
	}

//
// 2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//
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
			return percentage(((Score) scores.get(rowIndex)).getPairwise(), 1.0) + "%";

		// if it's the number of charsets column, return that.
		if(columnIndex == 2)
			return tableManager.getCharsetsCount(seqName) + "";

		// okay, it's an actual 'sequence'
		int col = columnIndex - additionalColumns;
		int row = rowIndex;

		int ndist = (int) Math.round(norm_distances[col][row] * 100);
		int rank = ranks[col][row];
		double dist = distances[col][row];

		// there ARE ways to do this properly, but I just can't be arsed right now
		// TODO do this properly
		if(dist >= 0) {
			return ndist + "% #" + rank+ " (" + percentage(dist, 1.0) + "%)";
		} else if(dist == DIST_ILLEGAL) {
			return "(N/A - SUPERbug)";
		} else if(dist == DIST_NO_COMPARE_SEQ) {
			return "(N/A - bug)";
		} else if(dist == DIST_SEQ_NA) {
			return "(N/A)";
		} else if(dist == DIST_NO_OVERLAP) {
			return "(NO OVERLAP)";
		} else if(dist == DIST_SEQ_ON_TOP) {
			return "(ON TOP)";
		} else if(dist == DIST_CANCELLED) {
			return "(CANCELLED)";
		} else {
			return "(N/A - unknown)";
		}
	}

	/** Convenience function */
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	public void setValueAt(String colName, String rowName, Object aValue) {
		// nothing to do, to save his life, call his wife in
		// nothing to say, but what the hey, how's your boy been
		// nothing to do, it's up to you
		// I've got nothing to say, but it's okay
	}

	public String getValueAt(String colName, String rowName, Sequence seq) {
		// ignore
		return null;
	}

	/** For convenience */
	public boolean identical(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.identical(x, y);
	}
}

class PDMColorRenderer extends DefaultTableCellRenderer
{
	DisplayDistancesMode dpm = null;

	public PDMColorRenderer(DisplayDistancesMode dpm) {
		this.dpm = dpm;
	}	

	/**
	 * Returns the colour for 'value' (which should be in the range [0, 1] inclusive)
	 */
	public Color getXORColor(Color basicColor, Color bg) {
		Color textColor = Color.WHITE;
		
		if(basicColor.equals(Color.RED))
			return Color.BLACK;

		// assume basicColor is BLACK
		if(bg.getRed() > 128) {
			return Color.BLACK;
		} else {
			return Color.WHITE;
		}
/*
		return new Color(
				bg.getRed() ^ textColor.getRed(), 
				bg.getGreen() ^ textColor.getGreen(),
				bg.getBlue() ^ textColor.getBlue()
		);
		*/
	}

	/**
	 * Returns the colour for 'value' (which should be in the range [0, 1] inclusive)
	 */
	public Color getColor(Color basicColor, double value) {
		if(basicColor.equals(Color.BLACK)) {
			// the XOR color get screwed up on HSB(0, 0, ~0.5),
			// so we limit the range of black to within this area
//			return Color.getHSBColor(0.0f, 0.0f, 1.0f - ((float)value * 0.4f));	
			return Color.getHSBColor(0.0f, 0.0f, 1.0f - (float)value);	// without limit
				
		} else 	
			// default: RED
			return Color.getHSBColor(0.0f, (float)value, 1.0f);
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
	comp.setOpaque(false);
	comp.setForeground(Color.BLACK);
	comp.setBackground(Color.WHITE);

	if(row < 1 || col < DisplayDistancesMode.SELECTED_COLUMN) {
		return comp;
	}

	Color basicColor = Color.BLACK;
	if(col == DisplayDistancesMode.SELECTED_COLUMN)
		basicColor = Color.RED;
	
	String val = (String) value;

	if(val.equals("(N/A)"))
		return comp;

	if(val.equals("(N/A - bug)"))
		return comp;

	if(val.indexOf(':') != -1)
		val = val.substring(0, val.indexOf(':'));
	if(val.indexOf('%') != -1)
		val = val.substring(0, val.indexOf('%'));
	val = val.replaceAll("\\%", "");
	val = val.replaceAll("\\:", "");

	try {
		// double d = Double.parseDouble(val)/100.0;
		//
		// okay, NOW we're using ranks
		double d = Double.parseDouble(val)/100; //dpm.getRowCount();

		comp.setOpaque(true);

		if(d < 0 || d > 1) {
			comp.setBackground(getColor(basicColor, 0.0)); 
			comp.setForeground(getXORColor(basicColor, comp.getBackground()));
		} else {
			comp.setBackground(getColor(basicColor, d)); 
			comp.setForeground(getXORColor(basicColor, comp.getBackground()));
		}

	} catch(NumberFormatException e) {
		// ah, ferget it
	}

        return comp;
    }
}
