/**
 * The DataStore is the underlying datastore for
 * _everything_ in SequenceMatrix. The UI talks to
 * us, and we do the needful. Unlike the DNA.* family,
 * we aren't UI-independent at all, although we will
 * talk mostly to other objects rather than actually
 * *doing* things ourselves.
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

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DataStore implements TableModel {
	// We use a metric TON of constants. Consistant naming is the only real way
	// to make sure these get sorted out properly.

	//
	// Variables we'll need to track
	//
	SequenceMatrix matrix = 	null;	
	Vector tableModelListeners = 	new Vector();		// a vector of all the TableModelListeners 
								// who are listening to us

	// The, err, Data. That we Store. You know.
	//
	private Hashtable hash_master = new Hashtable();	// the master hash:
								// Hashtable[colName] = Hashtable[seqName] = Sequence
								// Hashtable[colName] = Hashtable[""] = Integer(max_length)
								// Hashtable[""] = Hashtable[seqName] = Integer(count_sequence)
								// (my Perl roots are showing)
								//
								// Please don't use this hash directly. That just makes
								// life more fun for the debuggers. Please use the
								// helper functions below.

	// How to sort the sequences
	public static final int		SORT_BYNAME =		0;		// sort names alphabetically
	public static final int		SORT_BYSECONDNAME =	1;		// sort names by species epithet 
	public static final int		SORT_BYCHARSETS =	2;		// sort names by character sets
	public static final int		SORT_BYTOTALLENGTH =	3;		// sort names by total length
	private int			sortBy 	= 		SORT_BYNAME;	// By default, we sort by name

	private Vector			sortedSequenceNames;
	private Vector			sortedColumnNames;

	// For the display
	public static final int		additionalColumns =	3;		// The number of 'extra columns'
										// col(0) = 
//
// 0.	INTERNAL ACCESS FUNCTIONS. hash_columns does a lot of pretty strange shit in a very small space.
// 	To sort this out, please do NOT use hash_columns directly; use the functions defined here.
//

	/**
	 * Checks whether 'colName' MIGHT BE a valid column name. Note that we don't actually check
	 * whether colName *is* a valid column name. Mainly, this is to enforce the rule that colName
	 * might not be equal to "", since we use that key in the hashtable to store metadata.
	 */
	private void validateColName(String colName) {
		// da rulez: a colName can't be "" or null. Everything else is okay.
		if(colName == null || colName.equals(""))
			throw new IllegalArgumentException("DataStore.validateColName(): Column name '"+ colName +"' is invalid!");
	}

	/**
	 * Checks whether 'seqName' might be a valid sequence name. Mostly to make sure "" can't be
	 * used as a sequence name, as this would break our internals.
	 */
	private void validateSeqName(String seqName) {
		// da rulez: a seqName can't be "" or null. Everything else is okay.
		if(seqName == null || seqName.equals(""))
			throw new IllegalArgumentException("DataStore.validateSeqName(): Sequence name '"+ seqName +"' is invalid!");
	}

	/**
	 * Returns the sequence in column colName and with the sequence name seqName.
	 * This will also return null if the sequence is present but 'cancelled'. You
	 * can use isSequenceCancelled(colName, seqName) to check whether a sequence
	 * exists here or not.
	 *
	 * @return null, if either the column or sequence name does not exist (or the sequence name does not exist in this column)
	 */
	public Sequence getSequence(String colName, String seqName) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			return null;

		Sequence seq = (Sequence) col.get(seqName);

		if(seq == null)
			return null;

		if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled") != null)
			return null;

		return seq;
	}

	/**
	 * Returns a list of all sequence names in our datastore. This is easy, since we
	 * keep just such a list for easy retrieval. Indexing, you know. All the cool kids
	 * are doing it.
	 */
	public Set getSequences() {
		Hashtable ht = (Hashtable) hash_master.get("");


		if(ht == null)
			return new HashSet();	// just an empty Set

		return ht.keySet();
	}

	public List getSortedSequences() {
		return (List) sortedSequenceNames;
	}

	public List getSortedColumns() {
		return (List) sortedColumnNames;
	}

	/**
	 * Counts all sequence names in our datastore.
	 */
	public int getSequencesCount() {
		Hashtable ht = (Hashtable) hash_master.get("");

		if(ht == null)
			return -1;	// just an empty Set

		return ht.size();
	}
	

	/**
	 * Checks whether the sequence at (colName, seqName) is 'cancelled'.
	 * A cancelled sequence cannot be accessed via getSequence(), which
	 * will pretend it doesn't exist (returning null as it does).
	 */
	public boolean isSequenceCancelled(String colName, String seqName) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			return false;

		Sequence seq = (Sequence) col.get(seqName);

		if(seq == null)
			return false;

		if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled") != null)
			return true;

		return false;
	}

	/**
	 * Sets (or unsets) the 'cancelled' flag on a sequence.
	 * @param cancelled The 'cancel' flag: true indicates that the sequence is to be cancelled, false that it should be ignored.
	 *
	 */
	public void setSequenceCancelled(String colName, String seqName, boolean cancelled) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			throw new IllegalArgumentException("Can't find column '" + colName + "'.");

		Sequence seq = (Sequence) col.get(seqName);

		if(seq == null)
			throw new IllegalArgumentException("Column '" + colName + "' doesn't have a sequence named '" + seqName + "'");

		if(cancelled) {
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", new Object());
		} else {
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", null);
		}
	}

	/**
	 * Overwrites or creates a sequence entry, and puts 'seq' there.
	 *
	 * Warning: this will add a (colName, seqName) entry no matter what - even if that
	 * involves overwriting an old sequence in that slot, or if it has to create a new
	 * column for this.
	 *
	 */
	public void setSequence(String colName, String seqName, Sequence seq) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null) {
			col = new Hashtable();
			hash_master.put(colName, col);
			col.put("", new Integer(seq.getLength()));
		}
		col.put(seqName, seq);
		
		// update hash_master.get("")
		Hashtable ht = (Hashtable) hash_master.get("");
		if(ht == null) {
			ht = new Hashtable();
			hash_master.put("", ht);
		}

		if(ht.get(seqName) == null)
			ht.put(seqName, new Integer(1));
		else {
			int oldCount = ((Integer)ht.get(seqName)).intValue();

			ht.put(seqName, new Integer(oldCount++));
		}
	}

	/**	
	 * 	Deletes the sequence mentioned from the column mentioned. It also gets rid of the column itself, if
	 * 	it's empty.
	 */
	public void deleteSequence(String colName, String seqName) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			throw new IllegalArgumentException("Column '" + colName + "' does not exist.");

		col.remove(seqName);

		if(col.size() == 0) {
			// if there are no keys left is the '""' key, we can get rid of the column entirely	
			hash_master.remove(colName);
		}

		// Remove the sequence count from the 'sequences' hash;
		// if there are no more sequences with this name, remove
		// the name from this hash as well.
		Hashtable ht = (Hashtable) hash_master.get("");
		if(ht != null) {
			// we don't really care all that much
			// if there's no ht declared (shouldn't happen!) we just ignore it
			// and walk calmly on
			// in to the sunset
			
			if(ht.get(seqName) != null) {
				int oldCount = ((Integer)ht.get(seqName)).intValue();

				oldCount--;

				if(oldCount == 0) {
					// get rid of the sequence!
					ht.remove(seqName);
				} else {
					ht.put(seqName, new Integer(oldCount));
				}
			}
		}
	}

	/**
	 * Returns a set of all sequence names in column 'colName'.
	 */
	public Set getSequenceNamesByColumn(String colName) {
		validateColName(colName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			return null;
		
		Set set = col.keySet();
		set.remove("");
		return set;
	}

	/**
	 * Returns a set of all column names in this dataStore.
	 */
	public Set getColumns() {
		Set set = new HashSet(hash_master.keySet());	// copy this
		set.remove("");	// get rid of the 'meta' data
		return set;
	}

	/**
	 * Returns the 'width' (or length, whatever) of a column. This
	 * is the basepair width that new sequences must be the same size
	 * as.
	 *
	 * TODO: Put in an option to turn this off?
	 */
	public int getColumnLength(String colName) {
		validateColName(colName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			throw new IllegalArgumentException("Column '" + colName + "' doesn't exist.");
		
		return ((Integer)col.get("")).intValue();
	}

	/**	
	 * How many columns have a non-N/A value for sequence seqName? 
	 */
	public int getCharsetsCount(String seqName) {
		int count = 0;

		validateSeqName(seqName);

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				count++;
		}

		return count;
	}
	
	/**
	 * Directly sets the 'width' (or length, whatever) of a column.
	 * This is used by the addSequence functions to add a new
	 * sequence.
	 */
	public void setColumnLength(String colName, int newLength) {
		validateColName(colName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null) {
			// if there isn't a length already, set one up
			col = new Hashtable();
			hash_master.put(colName, col);
		}
		
		col.put("", new Integer(newLength));
	}

	/**
	 * Returns true if colName is a valid, real, existing column name in
	 * this dataStore. If it is valid, but no such column exists,
	 * isColumn() will return false.
	 */
	public boolean isColumn(String colName) {
		validateColName(colName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			return false;

		return true;
	}

	/**	
	 * Constructs a sequence consisting of all non-cancelled sequences
	 * for a specified seqName.
	 */
	public Sequence getCompleteSequence(String seqName) {
		Sequence result = new Sequence();

		validateSeqName(seqName);

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				result.appendSequence(seq);
		}

		return result;
	}	

	/**	
	 * Determine the total length of a sequence consisting of all 
	 * non-cancelled sequences for a specified seqName.
	 */
	public int getCompleteSequenceLength(String seqName) {
		int total = 0;

		validateSeqName(seqName);

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				total += seq.getActualLength();	
		}

		return total;
	}	
	
	/**
	 * Return the complete sequence with gaps - essentially
	 * identical to getCompleteSequence, except that we insert
	 * full length gaps into empty slots.
	 */
	public Sequence getCompleteSequenceWithGaps(String seqName) {
		Sequence result = new Sequence();

		validateSeqName(seqName);

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				result.appendSequence(seq);
			else
				result.appendSequence(Sequence.makeEmptySequence(seqName, getColumnLength(colName)));
		}

		return result;
	}

	/**
	 * Return the length of the complete sequence with gaps - 
	 * essentially identical to getCompleteSequenceLength, except 
	 * that we insert full length gaps into empty slots, and
	 * *then* measure them.
	 */
	public int getCompleteSequenceLengthWithGaps() {
		int count = 0;

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			count += getColumnLength(colName);
		}

		return count;
	}
	

// 
// 1. 	CONSTRUCTOR. We need a SequenceMatrix object to talk to the user with.
//
	/** 
	 * We need to know the SequenceMatrix we're serving, so that we can talk
	 * to the user. All else is vanity. Vanity, vanity, vanity.
	 */
	public DataStore(SequenceMatrix sm) {
		matrix = sm;
		updateSort(SORT_BYNAME);	// this will do nothing, but will prime the sort Vectors for future use
	}

//
// 2.	ADD COMMANDS. Add a SequenceList, add a file, that sort of thing.	
//
	/**
	 * Add a new sequence list to this dataset.
	 * This one is really just a wrapper; it figures out the name the
	 * column ought to have, and then calls addSequenceList(colName, sl); 
	 */
	public void addSequenceList(SequenceList sl) { 
		sl.lock();

		// 1. Figure out the column name.
		String colName = "Unknown";
		if(sl.getFile() != null)
			colName = sl.getFile().getName();

		// 2. Is this a duplicate column name?
		int x = 2;
		String newColName = colName;
		while(isColumn(newColName)) {
			newColName = colName + "_" + x;

			x++;

			if(x > 999999) {
				new MessageBox(
						matrix.getFrame(),
						"Sorry: I can only count up to 999,999!",
						"SequenceMatrix can only create 999,999 columns with the same column name. Sorry! Please let us know if this limit is too low for you."
						).go();
				sl.unlock();
				return;
			}
		}

		addSequenceList(newColName, sl);
		sl.unlock();
	}

	/**
	 * Add a new sequence list to this dataset.
	 * @param colName The name of the new column to create for this SequenceList.
	 *
	 * Important steps: create a new column, check for pre-existing sequences,
	 * add all the sequences, and report sequences which got written over to
	 * the user.
	 */
	public void addSequenceList(String colName, SequenceList sl) { 	
		sl.lock();

		// Set up us the new total width
		setColumnLength(colName, sl.getMaxLength());

		// String buffer droppedSequences records which sequences were dropped, so we can let the user know.
		StringBuffer droppedSequences = new StringBuffer("");

		Iterator i = sl.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();
			String seqName = null;
			
			// Figure out the 'seqName'
			switch(matrix.getPrefs().getUseWhichName()) {
				case Preferences.PREF_USE_SPECIES_NAME:
					seqName = seq.getSpeciesName();

					if(seqName == null) {
						// no species name? use full name
						seqName = seq.getFullName();
					}

					break;

				default:
				case Preferences.PREF_USE_FULL_NAME:
					seqName = seq.getFullName();
					break;
			}

			// Is there already a sequence with this name?
			Sequence seq_old = getSequence(colName, seqName);
			if(seq_old != null) {
				// there's already an entry!
				// but which one is bigger?

				// if the old one is bigger, "replace" the old one with the new one ...
				if(seq_old.getActualLength() > seq.getActualLength())
					seq = seq_old;
				
				// ... and make a note of it
				droppedSequences.append("\t" + seqName + ": Multiple sequences with the same name found, only the largest one is being used\n");
			}

			// Is it the right size?
			if(seq.getLength() < sl.getMaxLength()) { 
				droppedSequences.append("\t" + seqName + ": It is too short (" + seq.getLength() + " bp, while the column is supposed to be " + sl.getMaxLength() + " bp)\n");
				continue;
			} else if(seq.getLength() > sl.getMaxLength()) {
				droppedSequences.append("\t" + seqName + ": It is too long (" + seq.getLength() + " bp, while the column is supposed to be " + sl.getMaxLength() + " bp)\n");
				continue;
			}

			// All done: lay it on!
			setSequence(colName, seqName, seq);
		}

		// Communicate the droppedSequences list to the user
		if(droppedSequences.length() > 0) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Warning: Sequences were dropped!",
					"Some sequences were not added to the dataset. These are:\n" + droppedSequences.toString()
				);

			mb.go();
		}
	
		// Cleanup time
		sl.unlock();
		updateDisplay();

	}

//
// 3.	REMOVE COLUMNS AND SEQUENCES.
//		
	/**
	 * Delete the column named 'colName'.
	 *
	 * This involves:
	 * 1.	Deleting the actual column, including all sequences. (this is handled by deleteSequence)
	 * 2.	Deleting the sequence name, if it is not used once this column is deleted. (also handled by deleteSequence)
	 *
	 * When you've got friends like deleteSequence, life is so much easier.
	 */
	public void deleteColumn(String colName) {
		if(!isColumn(colName)) {
			// this column doesn't exist.
			throw new RuntimeException("Attempt to delete non-existant column '" + colName + "'. Are the threads okay?");
		}

		// deleteSequence() every sequence in this column
		Iterator i = new HashSet(getSequenceNamesByColumn(colName)).iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();

			deleteSequence(colName, seqName);
		}

		// aaaaaaaaaaaand ... done!
		updateDisplay();
	}

	/**
	 * Clear *everything*.
	 */
	public void clear() {
		Set set = new HashSet(getColumns());
		Iterator i = set.iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			deleteColumn(colName);
		}
	}

//
// 4.	THE 'TOGGLE' MECHANISM.
//
	/**
	 *	Toggles 'cancelled' state on the specified sequence.
	 *	i.e. if it is cancelled, it is now uncancelled; and
	 *	vice versa.
	 */
	public void toggleCancelled(String colName, String seqName) {
		if(isSequenceCancelled(colName, seqName))
			setSequenceCancelled(colName, seqName, false);
		else
			setSequenceCancelled(colName, seqName, true);

		updateDisplay();
	}

//
// 5.	SORTING. Obviously the next section down ought to be the display code.
// 	But we'll need to sort before we display, in order to avoid disappointment.
//
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
		private DataStore store = null;

		public SortByCharsets(DataStore store) {
			this.store = store;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

			int countCharsets1 = store.getCharsetsCount(str1);
			int countCharsets2 = store.getCharsetsCount(str2);

			return (countCharsets2 - countCharsets1);
		}
	}

	/**
	 * A sort Comparator which sorts a collection of Strings (really taxon names) by the total actual count of bases.
	 */
	private class SortByTotalActualLength implements Comparator {
		private DataStore store = null;

		public SortByTotalActualLength(DataStore store) {
			this.store = store;
		}

		public int 	compare(Object o1, Object o2) {
			String str1 = (String) o1;
			String str2 = (String) o2;

			int count1 = store.getCompleteSequenceLength(str1);
			int count2 = store.getCompleteSequenceLength(str2);

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
	 */
	private void resort(int sort) {
		sortedColumnNames = new Vector(getColumns());
		Collections.sort(sortedColumnNames);

		sortedSequenceNames = new Vector(getSequences());

		switch(sortBy) {
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
				Collections.sort(sortedSequenceNames);
		}

		sortBy = sort;
	}	

//
// 6.	DISPLAY THE SEQUENCE. This code will 'update' the code to screen. Except that
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

	public void fireTableModelEvent(TableModelEvent e) {
		Iterator i = tableModelListeners.iterator();
		while(i.hasNext()) {
			TableModelListener l = (TableModelListener)i.next();	

			l.tableChanged(e);
		}		
	}

	public void updateSort(int sortBy) {
		resort(sortBy);
		updateNoSort();
	}
	
	public void updateDisplay() {
		resort(sortBy);
		updateNoSort();
	}

	public void updateNoSort() {
		fireTableModelEvent(new TableModelEvent(this));
		fireTableModelEvent(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
	}

//
// 6.2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//

	/**
	 * Tells us what *class* of object to expect in columns. We can safely expect Strings.
	 * I don't think the world is ready for transferable Sequences just yet ...
	 */
	public Class getColumnClass(int columnIndex) {
		return String.class;
	}

	/**
	 * Gets the number of columns.
	 */
	public int getColumnCount() {
		return getColumns().size() + additionalColumns; 
	}
	
	/**
	 * Gets the number of rows.
	 */
	public int getRowCount() {
		return getSequencesCount();
	}

	/**
	 * Gets the name of column number 'columnIndex'.
	 */
        public String getColumnName(int columnIndex) {
		if(columnIndex == 0)
			return "";		// upper left hand box

		if(columnIndex == 1)
			return "Total length";

		if(columnIndex == 2)
			return "Number of sets";

		return (String) sortedColumnNames.get(columnIndex - additionalColumns);
	}

	/**
	 * Convenience function.
	 */
	public String getRowName(int rowIndex) {
		return (String) getValueAt(rowIndex, 0);
	}

	/**
	 * Gets the value at a particular column. The important
	 * thing here is that two areas are 'special':
	 * 1.	Row 0 is reserved for the column names.
	 * 2.	Column 0 is reserved for the row names.
	 * 3.	(0, 0) is to be a blank box (new String("")).
	 */
        public Object getValueAt(int rowIndex, int columnIndex) {
		if(sortedSequenceNames.size() == 0)
			return "No sequences loaded";

		if(columnIndex == 0) {
			// columnZero == names
			return (String) sortedSequenceNames.get(rowIndex);
		}

		String seqName = (String) sortedSequenceNames.get(rowIndex); 
		if(seqName == null)
			throw new IllegalArgumentException("Either rowIndex is out of range (rowIndex="+rowIndex+"), or sortedSequenceNames isn't primed.");

		if(columnIndex == 1) {
			// total length	
			return getCompleteSequenceLength(seqName) + " bp";
		}

		if(columnIndex == 2) {
			// no of charsets
			return getCharsetsCount(seqName) + "";
		}

		String colName  = (String) sortedColumnNames.get(columnIndex - additionalColumns);
		if(isSequenceCancelled(colName, seqName))
			return "(CANCELLED)";
		Sequence seq 	= getSequence(colName, seqName);

		// is it perhaps not defined for this column?
		if(seq == null)
			return "(N/A)";	

		return String.valueOf(seq.getActualLength());
	}

	/**
	 * Determines if you can edit anything. Which is only the sequences column.
	 */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
		if(columnIndex == 0)
			return true;
		return false;
	}

	/** 
	 * Allows the user to set the value of a particular cell. That is, the
	 * sequences column. 
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		if(columnIndex == 0) {
			String strOld = (String) getValueAt(rowIndex, columnIndex);
			String strNew = (String) aValue;

			renameSequence(strOld, strNew);
		}
	}

//
// 7.	RENAMING OF SEQUENCES AND OTHER SUCHLIKE MATTERS.
//

	/**
	 * Rename sequence seqOld to seqNew,
	 * and update everything.
	 */
	public void renameSequence(String seqOld, String seqNew) {
		StringBuffer buff_replaced = new StringBuffer();

		// actually rename things in the Master Hash
		Iterator i_cols = getColumns().iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();	

			if(getSequence(colName, seqOld) != null) {
				// replace
				Sequence seq = getSequence(colName, seqOld);
				deleteSequence(colName, seqOld);

				Sequence replacing = getSequence(colName, seqNew);
				
				if(replacing != null) {
					// the new name already exists!
					
					if(seq.getActualLength() > replacing.getActualLength()) {
						buff_replaced.append("\tIn column '" + colName + ": " + seqNew + " was replaced by the sequence formerly known as " + seqOld + ", since it is longer.\n");
						setSequence(colName, seqNew, seq);
					} else {
						buff_replaced.append("\tIn column '" + colName + ": " + seqOld + " was removed, since " + seqNew + " is longer than it is.\n");
						// don't do anything - the sequence known as seqNew wins
					}
				} else {
					// the new name does NOT exist
					setSequence(colName, seqNew, seq);
				}
			}
		}

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
}
