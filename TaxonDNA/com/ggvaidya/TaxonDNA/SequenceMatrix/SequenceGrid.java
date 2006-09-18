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
import java.util.regex.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class SequenceGrid {
	// these sort modes apply to the sequences
	public static final int		SORT_BYNAME =		0;		// sort names alphabetically
	public static final int		SORT_BYSECONDNAME =	1;		// sort names alphabetically

	SequenceMatrix	matrix 		= null;
	
	Hashtable	hash_cols	= new Hashtable();		// the master hash:
									// Hashtable[colName => String] = Hashtable[seqName => String] = Sequence
	Hashtable 	seq_names	= new Hashtable();		// Hashtable[seqName => String]	= Integer(count_cols)
	Hashtable	col_lengths	= new Hashtable();		// Hashtable[colName => String] = Integer(length)
	int		total_length 	= 0;

	int		rowSortMethod 	= SORT_BYNAME;

	Vector		vec_rows	= null;
	Vector		vec_cols	= null;

//
//	1.	CONSTRUCTOR.
//	
	/**
	 * Constructor. Gives us a SequenceMatrix to play with,
	 * if we want it.
	 */
	public SequenceGrid(SequenceMatrix matrix) {
		this.matrix = matrix;
	}

//
//	X.	GETTERS. Code to report on things.
//

	/**	Returns the Sequence at colName, sequenceName.
	 * 	It is pretty important that you use this, since
	 * 	it handles 'cancelled' sequences, returning
	 * 	'null' on them.
	 */
	public Sequence getSequence(String colName, String seqName) {
		Hashtable h = (Hashtable) hash_cols.get(colName);
		if(h == null) return null;
		Sequence seq = (Sequence) h.get(seqName);
		
		if(seq == null)
			return null;

		if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled") != null)
			return null;

		return seq;
	}

	/**
	 * Returns true if this sequence has been 'cancelled'.
	 */
	public boolean isCancelled(String colName, String seqName) {
		Hashtable h = (Hashtable) hash_cols.get(colName);
		if(h == null) return false;
		Sequence seq = (Sequence) h.get(seqName);
		
		if(seq == null)
			return false;

		if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled") != null)
			return true;

		return false;
	}

	/**	Returns a vector of all the column names */
	public Vector getColumns() {
		return vec_cols;
	}

	/**	Returns a vector of all the sequence names */
	public Vector getSequences() {
		return vec_rows;
	}

	/**	Returns the number of sequence names */
	public int getSequencesCount() {
		return seq_names.keySet().size();
	}

	/**	How many columns have a non-N/A value for sequence seqName? */
	public int getCharsetsCount(String seqName) {
		int count = 0;

		Iterator i = col_lengths.keySet().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				count++;
		}

		return count;
	}

	/**	How many columns have a non-N/A value for sequence seqName? */
	public int getTotalActualLength(String seqName) {
		int count = 0;

		Iterator i = col_lengths.keySet().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				count+= seq.getActualLength();
		}

		return count;
	}
	

	/**
	 * 	Returns the 'length' of a particular column. These lengths
	 * 	can't, or shouldn't be allowed, to be changed.
	 */
	public int getColumnLength(String colName) {
		Integer i = (Integer) col_lengths.get(colName);
		if(i == null) return -1;
		return i.intValue();
	}

	/**
	 * 	Resort the table into the order specified by the constants.
	 */
	public void resort(int sort) {
		rowSortMethod = sort;
		updateDisplay();
	}
//
// 	X.	SETTERS
//
	/**
	 *	Toggles 'cancelled' state on the specified sequence.
	 */
	public void toggleCancelled(String colName, String seqName) {
		Hashtable h = (Hashtable) hash_cols.get(colName);
		if(h == null) return;
		Sequence seq = (Sequence) h.get(seqName);

		if(seq == null)
			return;

		if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled") == null)
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", new Object());
		else
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", null);
		
		updateDisplay();		// TODO: indicate ONLY a single-cell change
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
		hash_cols.clear();
		col_lengths.clear();
		seq_names.clear();
		System.gc();
		updateDisplay();
	}

//
//	X.	CHANGE COLUMNS. Change SequenceList/column related information. 
//
	/**
	 * Add a new sequence list to this dataset. It's sorted into place, unfortunately.
	 */
	public void addSequenceList(SequenceList sl) { 
		sl.lock();

		// 1. Figure out the column name.
		String colName = "Unknown";
		if(sl.getFile() != null)
			colName = sl.getFile().getName();

		int x = 2;
		while(hash_cols.get(colName) != null) {
			colName = colName + "_" + x;

			x++;
		}

		addSequenceList(colName, sl);
		sl.unlock();
	}

	/**
	 * Add a new sequence list to this dataset. It's sorted into place, unfortunately.
	 */
	public void addSequenceList(String colName, SequenceList sl) { 	
		sl.lock();

		// And add a hashtable to it.
		hash_cols.put(colName, new Hashtable());

		// 2. Set the new total width
		col_lengths.put(colName, new Integer(sl.getMaxLength()));
		total_length += sl.getMaxLength();

		// 3. Actually add the SequenceList 
		int useWhichName = matrix.getPrefs().getUseWhichName();

		// set up to do the addition
		Iterator i = sl.iterator();
		boolean speciesNameList_modified = false;
		StringBuffer droppedSequences = new StringBuffer("");
		int col_length = sl.getMaxLength();

		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();
			String name = "";
			
			// figure out the 'name'
			switch(matrix.getPrefs().getUseWhichName()) {
				case Preferences.PREF_USE_SPECIES_NAME:
					name = seq.getSpeciesName();

					if(name.equals("")) {
						// no species name? use full name
						name = seq.getFullName();
					}
					break;

				default:
				case Preferences.PREF_USE_FULL_NAME:
					name = seq.getFullName();
					break;
			}

			// load up the column
			Hashtable hash_seqs = (Hashtable) hash_cols.get(colName);

			// is there already a sequence with this name?
			if(hash_seqs.get(name) != null) {
				// there's already an entry!
				// figure out which one is bigger
				Sequence seq2 = (Sequence) hash_seqs.get(name);

				// if the new one is bigger, REPLACE the old one in the hashtable
				if(seq2.getActualLength() > seq.getActualLength())
					seq = seq2;
				
				// and make a note of it
				droppedSequences.append("\t" + name + ": Multiple sequences with the same name found, only the largest one is being used\n");
			}

			// is it the right size?
			if(seq.getLength() != col_length) {
				droppedSequences.append("\t" + name + ": It is too short (" + seq.getLength() + " bp, while the column is supposed to be " + col_length + " bp)\n");
				continue;
			}

			// name has been fixed, it's the right size: add it!
			hash_seqs.put(name, seq);
			
			// is this a new name?
			if(seq_names.get(name) == null) {
				// add it
				speciesNameList_modified = true;
				seq_names.put(name, new Object());
			}
		}
			
		if(speciesNameList_modified) {
			// do some sort of notify everything-was-modified thing here
		}

		// communicate the droppedSequences list to the user
		if(droppedSequences.length() > 0) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Warning: Sequences were dropped!",
					"Some sequences were not added to the dataset. These are:\n" + droppedSequences.toString()
				);

			mb.go();
		}
	
		// 5. Cleanup time
		sl.unlock();
		updateDisplay();
	}

	/**
	 * A sort Comparator which sorts a collection of Strings by - of all things - their SECOND name. Such is life.
	 */
	private class SortByName implements Comparator {
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
	 * Our private let-everybody-know-we've-changed function.
	 * Since only one object cares (TableModel), this is
	 * ridiculously easy.
	 */
	private void updateDisplay() {
		// figure out cols
		vec_cols = new Vector(hash_cols.keySet());
		Collections.sort(vec_cols);

		// figure out rows
		vec_rows = new Vector(seq_names.keySet());
		switch(rowSortMethod) {
			case SORT_BYSECONDNAME:
				Collections.sort(vec_rows, new SortByName());
				break;
			case SORT_BYNAME:
			default:
				Collections.sort(vec_rows);
		}

		// update the display on the screen
		matrix.updateDisplay();	
	}

	/**
	 * Rename sequence seqOld to seqNew,
	 * and update everything.
	 */
	public void renameSequence(String seqOld, String seqNew) {
		StringBuffer buff_replaced = new StringBuffer();

		// actually rename things in the Master Hash
		Iterator i_cols = hash_cols.keySet().iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();	
			Hashtable hash_seqs = (Hashtable) hash_cols.get(colName);

			if(getSequence(colName, seqOld) != null) {
				// replace
				Sequence old = getSequence(colName, seqOld);
				hash_seqs.remove(seqOld);
				
				if(getSequence(colName, seqNew) != null) {
					// the new name already exists!
					Sequence seq = getSequence(colName, seqNew);
					
					if(old.getActualLength() > seq.getActualLength()) {
						buff_replaced.append("\tIn column '" + colName + ": " + seqNew + " was replaced by the sequence formerly known as " + seqOld + ", since it is longer.\n");
						hash_seqs.put(seqNew, old);
					} else {
						buff_replaced.append("\tIn column '" + colName + ": " + seqOld + " was removed, since " + seqNew + " is longer than it is.\n");
						// don't do anything - the sequence known as seqNew wins
					}
				} else {
					// the new name does NOT exist
					hash_seqs.put(seqNew, old); 
				}

				// did we just get rid of the last sequence in this column?
				if(hash_seqs.isEmpty()) {
					// get rid of this column
					i_cols.remove();
					// and its length
					int length = getColumnLength(colName);
					col_lengths.remove(colName);
					// and subtract this from the total length
					total_length -= length;
				}
			}
		}

		// remove the name from the sequences record
		seq_names.remove(seqOld);						

		// does the new name have an entry?
		// if not, we need to provide it one!
		if(seq_names.get(seqNew) == null)
			seq_names.put(seqNew, new Object());


		if(buff_replaced.length() > 0) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Some name collisions occured!",
					"Renaming '" + seqOld + "' to '" + seqNew + "' was tricky because there is already a '" + seqNew + "' in the dataset. The following was carried out:\n" + buff_replaced.toString()
					);
			mb.go();
		}

		updateDisplay();
	}

	/**
	 * Export the current matrix as Nexus. Note that this function might
	 * change or move somewhere else -- I haven't decided yet.
	 *
	 * The way the data is structured (at the moment, haha) is:
	 * 1.	Hashtable[colName] --&gt; Hashtable[seqName] --&gt; Sequence
	 * 2.	We can get seqName lists, sorted.
	 *
	 * The way it works is fairly simple:
	 * 1.	If PREF_NEXUS_BLOCKS:
	 * 		for every column:
	 * 			write the column name in comments
	 * 			for every sequence:
	 * 				write the column name
	 * 				write the sequence
	 * 				write the length
	 * 			;
	 * 			write the column name in comments
	 * 		;
	 * 2.	If PREF_NEXUS_SINGLE_LINE:
	 * 		for every sequence name:
	 * 			for every column:
	 * 				see if an entry occurs in the column
	 * 				if not write in a 'blank'
	 * 			;
	 * 		;
	 * 
	 * 3.	If PREF_NEXUS_INTERLEAVED:
	 * 		create a new sequence list
	 *
	 * 		for every sequence name:
	 * 			for every column:
	 * 				if column has sequence:
	 * 					add sequence
	 * 				else
	 * 					add blank sequence
	 * 				;
	 * 			;
	 * 		;
	 *
	 * 		use NexusFile to spit out the combined file on the sequence list.
	 *
	 * @throws IOException if there was a problem writing this file
	 */
	public void exportAsNexus(File f, DelayCallback delay) throws IOException, DelayAbortedException {
		// how do we have to do this?
		int how = matrix.getPrefs().getNexusOutput();

		// set up delay 
		if(how != Preferences.PREF_NEXUS_INTERLEAVED && delay != null)
			delay.begin();

		// let's get this party started, etc.
		// we begin by obtaining the Taxonsets (if any).
		Taxonsets tx = matrix.getTaxonsets(); 
		StringBuffer buff_sets = new StringBuffer();		// used to store the 'SETS' block
		buff_sets.append("BEGIN SETS;\n");
		if(tx.getTaxonsetList() != null) {
			Vector v = tx.getTaxonsetList();
			Iterator i = v.iterator();
			while(i.hasNext()) {
				String taxonsetName = (String) i.next();
				// Nexus has offsets from '1'
				String str = tx.getTaxonset(taxonsetName, 1);
				if(str != null)
					buff_sets.append("\tTAXSET " + taxonsetName + " = " + str + ";\n");
			}
		}

		// we begin by calculating the SETS block,
		// since:
		// 1.	we need to coordinate the names right from the get-go
		// 2.	INTERLEAVED does not have to write the Nexus file
		// 	at all, but DOES need the SETS block.
		//

		// Calculate the SETS blocks, with suitable widths etc.	
		int widthThusFar = 0;
		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String columnName = (String)i.next();

			// write out a CharSet for this column, and adjust the widths
			buff_sets.append("\tCHARSET " + fixColumnName(columnName) + " = " + (widthThusFar + 1) + "-" + (widthThusFar + getColumnLength(columnName)) + ";\n");
			widthThusFar += getColumnLength(columnName);
		}

		// end and write the SETS block
		buff_sets.append("END;");
		
		// Now that the blocks are set, we can get down to the real work: writing out
		// all the sequences. This is highly method specific.
		//
		// First, we write out the header, unless it's going to use NexusFile to
		// do the writing.
		PrintWriter writer = null;
		if(how == Preferences.PREF_NEXUS_BLOCKS || how == Preferences.PREF_NEXUS_SINGLE_LINE) {
			writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));

			writer.println("#NEXUS");
			writer.println("[Written by " + matrix.getName() + " on " + new Date() + "]");

			writer.println("");

			writer.println("BEGIN DATA;");
			writer.println("\tDIMENSIONS NTAX=" + getSequencesCount() + " NCHAR=" + total_length + ";");

			writer.print("\tFORMAT DATATYPE=DNA GAP=- MISSING=? ");
			if(how == Preferences.PREF_NEXUS_BLOCKS)
				writer.print("INTERLEAVE");
			writer.println(";");

			writer.println("MATRIX");
		}

		SequenceList list = null;
		if(how == Preferences.PREF_NEXUS_INTERLEAVED) {
			list = new SequenceList();
		}

		// Now, there's a loop over either the column names or the sequence list
		//
		if(how == Preferences.PREF_NEXUS_BLOCKS) {
			// loop over column names
			Vector colNames = getColumns();
			Iterator i_cols = colNames.iterator();

			while(i_cols.hasNext()) {
				String colName = (String) i_cols.next();
				Hashtable hash_seqs = (Hashtable) hash_cols.get(colName);
				int colLength = getColumnLength(colName);
				
				// first of all, write the column name in as a comment (if in block mode)
				writer.println("[beginning " + fixColumnName(colName) + "]");

				// then loop over all the sequences
				int interval = getSequencesCount();
				Iterator i_seqs = getSequences().iterator();
				while(i_seqs.hasNext()) {
					String seqName = (String) i_seqs.next();
					Sequence seq = getSequence(colName, seqName); 

					if(seq == null)
						seq = Sequence.makeEmptySequence(seqName, colLength);

					writer.println(getNexusName(seqName) + " " + seq.getSequence() + " [" + colLength + " bp]"); 
				}
				
				writer.println("[end of " + fixColumnName(colName) + "]");
				writer.println("");	// leave a blank line
			}

		} else if(how == Preferences.PREF_NEXUS_SINGLE_LINE || how == Preferences.PREF_NEXUS_INTERLEAVED) {
			// loop over sequence names

			Iterator i_rows = getSequences().iterator();
			while(i_rows.hasNext()) {
				String seqName = (String) i_rows.next();
				Sequence seq_interleaved = null;
				int length = 0;

				if(how == Preferences.PREF_NEXUS_SINGLE_LINE)
					writer.print(getNexusName(seqName) + " ");
				else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
					seq_interleaved = new Sequence();

				Iterator i_cols = getColumns().iterator();
				while(i_cols.hasNext()) {
					String colName = (String) i_cols.next();
					Hashtable hash_seqs = (Hashtable) hash_cols.get(colName);
					Sequence seq = getSequence(colName, seqName);

					if(seq == null)
						seq = Sequence.makeEmptySequence(colName, getColumnLength(colName));

					length += seq.getLength();

					if(how == Preferences.PREF_NEXUS_SINGLE_LINE)
						writer.print(seq.getSequence());
					else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
						seq_interleaved.appendSequence(seq);
					else
						throw new RuntimeException("'how' makes no sense in SequenceGrid.exportAsNexus()! [how = " + how + "]");
				}

				if(how == Preferences.PREF_NEXUS_INTERLEAVED)
					seq_interleaved.changeName(seqName);

				if(how == Preferences.PREF_NEXUS_SINGLE_LINE)
					writer.println(" [" + length + " bp]");
				else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
					list.add(seq_interleaved);
			}
		}

		// close up the file ... if there WAS a file to close, that is.
		if(how == Preferences.PREF_NEXUS_BLOCKS || how == Preferences.PREF_NEXUS_SINGLE_LINE) {
			// end the DATA block
			writer.println(";");
			writer.println("END;");
		
			writer.println(buff_sets);

			writer.close();
		}

		// otherwise, err ... actually write the darn file out to begin with :p
		if(how == Preferences.PREF_NEXUS_INTERLEAVED) {
			NexusFile nf = new NexusFile();
			nf.writeNexusFile(f, list, matrix.getPrefs().getNexusInterleaveAt(), buff_sets.toString(), delay);
		}
		
		// shut down delay 
		if(how != Preferences.PREF_NEXUS_INTERLEAVED && delay != null)
			delay.end();
	}

	private String getNexusName(String x) {
		// we don't worry about duplicates because:
		// 1.	we don't particularly care about taxon name lengths (atleast, not right now)
		// 2.	
		//
		return x.replaceAll("'", "''").replace(' ', '_');
	}	

	/**
	 * Export the current matrix as TNT. Note that this function might
	 * change or move somewhere else -- I haven't decided yet.
	 *
	 * TODO: interleaved: we really ought to output this as [ACTG], etc.
	 *
	 * @throws IOException if there was a problem writing this file
	 */
	public void exportAsTNT(File f, DelayCallback delay) throws IOException, DelayAbortedException {
		boolean writeAnyway = true;

		// we begin by obtaining the Taxonsets (if any).
		Taxonsets tx = matrix.getTaxonsets(); 
		StringBuffer buff_taxonsets = new StringBuffer();
		if(tx.getTaxonsetList() != null) {
			if(tx.getTaxonsetList().size() > 32) {
				MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Too many taxonsets!",
					"According to the manual, TNT can only handle 32 taxonsets. You have " + tx.getTaxonsetList().size() + " taxonsets. Would you like me to write out all the taxonsets anyway? TNT might not be able to read this file.\n\nClick 'No' to not write out any taxonsets.",
					MessageBox.MB_YESNO);

				writeAnyway = false;
				if(mb.showMessageBox() == MessageBox.MB_YES)
					writeAnyway = true;
			}

			if(writeAnyway) {
				buff_taxonsets.append("agroup\n");

				Vector v = tx.getTaxonsetList();
				Iterator i = v.iterator();
				int x = 0;
				while(i.hasNext()) {
					String taxonsetName = (String) i.next();
					// TNT has offsets from '0'
					String str = tx.getTaxonset(taxonsetName, 0);
					if(str != null) 
					{
						buff_taxonsets.append("=" + x + " (" + taxonsetName + ") " + str + "\n");
						x++;
					}
				}

				buff_taxonsets.append(";\n\n\n");
			}
		}

		writeAnyway = true;
		
		// set up the 'sets' buffer
		if(getColumns().size() > 32) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Too many files!",
					"According to the manual, TNT can only handle 32 groups. You have " + getColumns().size() + " groups. Would you like me to write all the groups out anyway? TNT might not be able to read this file.\n\nClick 'No' to not write out any sets.",
					MessageBox.MB_YESNO);

			writeAnyway = false;
			if(mb.showMessageBox() == MessageBox.MB_YES)
				writeAnyway = true;
		}
		
		StringBuffer buff_sets = new StringBuffer();
		if(writeAnyway) {
			buff_sets.append("xgroup\n");

			Iterator i = getColumns().iterator();	
			int at = 0;
			int colid = 0;
			while(i.hasNext()) {
				String colName = (String) i.next();

				buff_sets.append("=" + colid + " (" + fixColumnName(colName) + ")\t");
				colid++;

				for(int x = 0; x < getColumnLength(colName); x++) {
					buff_sets.append(at + " ");
					at++;
				}
				
				buff_sets.append("\n");
			}
			
			buff_sets.append("\n;\n\n");
		}

		if(delay != null)
			delay.begin();		

		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));

		writer.print("nstates dna;");
		writer.print("xread\n'Exported by " + matrix.getName() + " on " + new Date() + "'\n");
		writer.println(total_length + " " + getSequencesCount());

		Iterator i_rows = getSequences().iterator();
		int count_rows = 0;
		while(i_rows.hasNext()) {
			if(delay != null)
				delay.delay(count_rows, getSequencesCount());

			count_rows++;

			String seqName = (String) i_rows.next();
			Sequence seq_interleaved = null;
			int length = 0;

			writer.print(getNexusName(seqName) + " ");

			Iterator i_cols = getColumns().iterator();
			while(i_cols.hasNext()) {
				String colName = (String) i_cols.next();
				Hashtable hash_seqs = (Hashtable) hash_cols.get(colName);
				Sequence seq = getSequence(colName, seqName); 
				
				if(seq == null)
					seq = Sequence.makeEmptySequence(colName, getColumnLength(colName));

				length += seq.getLength();

				writer.print(seq.getSequence());
			}

			writer.println();
		}

		writer.println(";\n");
		
		writer.println(buff_sets);
		writer.println(buff_taxonsets);

		writer.flush();
		writer.close();

		// shut down delay 
		if(delay != null)
			delay.end();
	}	

	private String getTNTName(String x) {
		// we don't worry about duplicates because:
		// 1.	we don't particularly care about taxon name lengths (atleast, not right now)
		// 2.	
		//
		return x.replaceAll("'", "''").replace(' ', '_');
	}

	private String fixColumnName(String columnName) {
		columnName = columnName.replaceAll("\\.nex", "");
		columnName = columnName.replace('.', '_');
		columnName = columnName.replace(' ', '_');
		columnName = columnName.replace('-', '_');
		columnName = columnName.replace('\\', '_');
		columnName = columnName.replace('/', '_');
		return columnName;
	}
}
