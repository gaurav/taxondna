/**
 * The SequenceGrid is the core data structure for
 * SequenceMatrix. It is the 'matrix' itself.
 *
 * Note: we are ENTIRELY thread-unsafe. Do NOT touch
 * this class inside a Thread unless you REALLY know
 * what you're doing.
 * 
 * The overall algorithm is:
 * 1.	Split up the incoming SequenceList into its Sequence
 * 	components. Store these in a hashtable. Also remember
 * 	to only select ONE of the incoming sequences if there's
 * 	more than one, and only warn the user ONCE (right at
 * 	the end, ideally with a list of the 'squished' entries).
 *
 * 	We will either use the full name or the species name,
 * 	depending on what Preferences.getUseWhichName() says.
 *
 * 	The Hashtable will reference the sequenceId (String) to
 * 	a Vector, which is an array by [column]. You can use
 * 	getColumnName(column) to figure out which file it
 * 	refers to.
 * 	
 * 2.	TableModel can use getSequences(name) to get the vector,
 * 	then reference it by row to figure out which sequence
 * 	is in a particular place, and getSequenceList() to get
 * 	a list of all the names.
 *
 * 3.	TO ADD A SEQUENCELIST: We increment our column count,
 * 	and then just add them in as appropriate. Remember to
 * 	vector.add(null) for 'empty' ones, otherwise everything
 * 	will go kind of nuts.
 *
 * 4.	TO MERGE TWO DATASETS: We handle 'merges' entirely
 * 	by ourselves. The algo is simple enough: although we
 * 	get a list of sequence names to combine, we combine
 * 	them in pairs, checking to make sure that there are
 * 	no pre-existing sequences in a merge. I'm not sure
 * 	how we're going to handle the interactivity here:
 * 	probably, we'll have to get a Frame and talk to the
 * 	user direct-like.
 *
 * 5.	TO REMOVE A SEQUENCE LIST: We remove the column
 * 	from the list, and then MANUALLY run through the
 * 	ENTIRE hashTable, removing entries as we go.
 * 	Thank god this doesn't happen all that frequently.
 * 
 */

/*
 *
 *  SpeciesMatrix
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

package com.ggvaidya.TaxonDNA.SpeciesMatrix;

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

public class SequenceGrid {
	SpeciesMatrix	matrix 		= null;
	Hashtable	hash_seqs 	= new Hashtable();		// master hash
	Vector		column_names 	= new Vector();			// list of column names (zero-based)
	Vector		seq_names 	= new Vector();			// list of sequence names (zero-based, sorted)
	int		totalLength	= 0;				// helper function, to keep track of the total length of the thing.

//
//	1.	CONSTRUCTOR.
//	
	/**
	 * Constructor. Gives us a SpeciesMatrix to play with,
	 * if we want it.
	 */
	public MatrixModel(SpeciesMatrix matrix) {
		this.matrix = matrix;
	}

//
//	X.	GETTERS. Code to report on things.
//
	/**	Returns a vector of all the column names */
	public Vector getColumns() {
		return column_names;
	}

	/**	Returns a vector of all the sequence names */
	public Vector getSequenceNames() {
		return seq_names;
	}

	/**	Returns the Sequence at the specified (seqName, col) */
	public Sequence getSequence(String seqName, int col) {
		Vector v = (Vector) hash_seqs.get(seqName);
		if(v == null) {
			return null;
		}

		if(col >= v.size()) {
			// it cannot be!
			// but this time, we'll fake it
			return null;
		}

		return (Sequence) v.get(col);
	}

//
//	X.	CHANGE EVERYTHING. Changes, err, everything.
//
	/**
	 * Completely clear up *everything*. For us,
	 * thanks to the miracle of garbage collection,
	 * that's not an awful lot :).
	 */
	public void clear() {
		hash_seqs.clear();
		System.gc();
		updateDisplay();
	}

//
//	X.	CHANGE COLUMNS. Change SequenceList/column related information. 
//
	/**
	 * Add a new sequence list to this dataset. It's inserted at the end.
	 */
	public void addSequenceList(SequenceList sl) {
		insertSequenceList(sl, getColumns().size());
	}

	/** 
	 * Inserts a new sequence list to this dataset. If col == getColumns().size(), it's inserted at the end.
	 */
	public void insertSequenceList(SequenceList sl, int loc) {
		sl.lock();

		// 1. Add the column name.
		if(sl.getFile() != null) {
			column_names.add(sl.getFile().getName(), loc);
		} else {
			// shouldn't happen very often
			column_names.add("Unknown", loc);
		}

		// 2. Set the new total width
		totalLength += sl.getMaxLength();

		// 3. Actually add the SequenceList 
		addSequencesByFullName(sl);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private void addSequencesByFullName(SequenceList sl) {
		Iterator i = sl.iterator();
		boolean speciesNameList_modified = false;
		StringBuffer collapsedSpecies = new StringBuffer("");

		while(i.hasNext() {
			Sequence seq = (Sequence) i.next();
				
			if(seqs.get(name) == null) {
				// add it
				speciesNameList_modified = true;

					seqs.put(name, );
					speciesNames.add(name);
				}
			} else {
				// now, the fun thing is, 
				collapsedSpecies.append("\t

			if(speciesNameList_modified) {
				// do some sort of everything-was-modified thing here
			}

			if(collapsedSpecies.length() > 0) {
				// display the collapsedSpecies list to the user
			}

			updateDisplay();
		}
	}

	/**
	 * Our private let-everybody-know-we've-changed function.
	 * Since only one object cares (TableModel), this is
	 * ridiculously easy.
	 */
	private void updateDisplay() {
		matrix.updateDisplay();	
	}

	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}
	
	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);
	}	

	public Class getColumnClass(int columnIndex) {
		return String.class;
	}

	public int getColumnCount() {
		//System.err.println("Request for col count: " + columns.size());
		return columns.size();
	}

        public String getColumnName(int columnIndex) {
		//System.err.println("Request for col name: " + columnIndex + ": " + columnNames.get(columnIndex));
		return (String) columnNames.get(columnIndex);
	}

	public int getRowCount() {
		return speciesList.keySet().size();
	}

        public Object getValueAt(int rowIndex, int columnIndex) {
		String speciesName = (String) speciesNames.get(rowIndex);

		if(columnIndex == 0) {
			// reserved for the speciesName!
			return speciesName;
		}
		
		SpeciesDetail det = null;

		SequenceList list = (SequenceList)(columns.get(columnIndex));
		list.lock();
		try {
			det = list.getSpeciesDetails(null).getSpeciesDetailsByName(speciesName);
		} catch(DelayAbortedException e) {
			// wtf?
			return null;
		} finally {
			list.unlock();
		}

		if(det == null)
			return "(N/A)";

		return String.valueOf(det.getLargestSequenceLength()) + " bp";
	}

	public void exportAsNexus(File f) throws IOException {
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));
		StringBuffer buff_sets = new StringBuffer();
		Hashtable seqs = new Hashtable();
		Interleaver interleaver = new Interleaver();

		// need we interleave?
		int interleaveAt = 0;
		if(matrix.getPrefs().getNexusOutput() == Preferences.PREF_NEXUS_INTERLEAVED)
			interleaveAt = 1000;		// TODO: Get from Preferences

		buff_sets.append("BEGIN SETS;\n");

		writer.println("#NEXUS");
		writer.println("[Written by SpeciesMatrix on " + new Date() + "]");

		writer.println("");

		writer.println("BEGIN DATA;");
		writer.println("\tDIMENSIONS NTAX=" + speciesNames.size() + " NCHAR=" + totalLength + ";");
		writer.println("\tFORMAT DATATYPE=DNA GAP=- MISSING=? INTERLEAVE;");
		writer.println("MATRIX");

		int widthThusFar = 0;
		for(int x = 1; x < columnNames.size(); x++) {
			String columnName = (String)columnNames.get(x);
			writer.println("[beginning " + columnName + "]");	
		
			columnName = columnName.replaceAll("\\.nex", "");
			columnName = columnName.replace('.', '_');
			columnName = columnName.replace(' ', '_');
			columnName = columnName.replace('-', '_');
			columnName = columnName.replace('\\', '_');
			columnName = columnName.replace('/', '_');

			SequenceList sl = (SequenceList) columns.get(x);
			if(sl == null)
				break;
			
			buff_sets.append("\tCHARSET " + columnName + " = " + (widthThusFar + 1) + "-" + (widthThusFar + sl.getMaxLength()) + ";\n");
			widthThusFar += (sl.getMaxLength());

			SpeciesDetails det = null;
			try {
				det = sl.getSpeciesDetails(null);
			} catch(DelayAbortedException e) {
				// ignore
			}

			Iterator i = speciesNames.iterator();
			while(i.hasNext()) {
				String name = (String) i.next();
				Sequence seq_out = null;

				// TODO: figure out proper, unique names etc.

				if(det.getSpeciesDetailsByName(name) == null) {
					// it doesn't exist!
					seq_out = Sequence.makeEmptySequence(sl.getMaxLength());
				} else {
					Sequence seq = null;
					Sequence seq_longest = null;
					Iterator i2 = sl.iterator();

					while(i2.hasNext()) {
						seq = (Sequence) i2.next();

						if(seq.getSpeciesName().equals(name)) {
							seq_longest = seq;
							sl.lock();
							i2 = sl.conspecificIterator(seq);

							while(i2.hasNext()) {
								seq = (Sequence) i2.next();

								if(seq.getActualLength() > seq_longest.getActualLength())
									seq_longest = seq;
							}
							
							sl.unlock();
							break;
						}
					}
								
					seq_out = seq_longest;
				}

				// so now, we have a name_for_output and a buff_seq.
				if(interleaveAt == 0) {
					writer.println(nexusName(name) + " " + seq_out.getSequence());
				} else {
					interleaver.appendSequence(nexusName(name), seq_out);
				}
			}

			writer.println("[end of " + (String)columnNames.get(x) + "]");	
			writer.println("");	
		}

		// end the DATA block
		writer.println(";");
		writer.println("END;");
		
		// end and write the SETS block
		buff_sets.append("END;");
		writer.println(buff_sets);

		writer.close();
	}

	private String nexusName(String x) {
		return x.replaceAll("'", "''").replace(' ', '_');
	}
}
