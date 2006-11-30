/**
 * The DataStore is the underlying datastore for
 * _everything_ in SequenceMatrix. The UI talks to
 * us, and we do the needful. Unlike the DNA.* family,
 * we aren't UI-independent at all, although we will
 * talk mostly to other objects rather than actually
 * *doing* things ourselves.
 *
 * TABLE MODELS: We are now 'outsourcing' Table Models
 * entirely. DataStore will handle communication to
 * and from the TableModels, and will act as a single
 * major TableModel itself. What this means is:
 *
 * JTable ------------&gt; DataStore.getValueAt(...), etc. ------------&gt; Display...Model.getValueAt(...), etc.
 * Display...Model.fireTableModelEvent() ------&gt; DataStore.fireT... ----&gt; TableModelListeners
 *
 * In a nutshell: nobody needs to know about this at all.
 * Everybody can just act as if DataStore is the one and
 * only TableModel. We figure out which display mode we're
 * in, and route the incoming messages appropriately.
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

import java.awt.Color;		// for Color
import java.awt.Component;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import java.awt.MenuItem;
import java.awt.Menu;
import java.awt.PopupMenu;
import java.awt.event.*;

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

	private int			sortBy 	= 		SORT_BYNAME;
	private boolean			sortBroken =		true;		// assume the sort is 'broken'

	private Vector			sortedSequenceNames;
	private Vector			sortedColumnNames;

	// For the outgroup selection and display, etc.
	private String			outgroupName =		null;

	// Counting the number of cancelled sequences (so we know while exporting, etc.)
	private int			count_cancelledSequences = 0;

	// For the table-model-switcheroo thingie
	private TableModel		currentTableModel =	null;
	private int			additionalColumns =	0;
	private String			pdm_colName =		null;

	// For controlling updates
	private boolean			suppressUpdates =	false;		// we don't actually use this,
										// because a suprisingly large amount
										// of code depends on being able to
										// know what everybody's doing at
										// every instant, or something like
										// that. Maybe after some refactoring?

//
// 0.	INTERNAL ACCESS FUNCTIONS. hash_columns does a lot of pretty strange shit in a very small space.
// 	To sort this out, please do NOT use hash_columns directly; use the functions defined here.
//

	/**
	 * Checks whether 'colName' MIGHT BE a valid column name. Note that we don't actually check
	 * whether colName *is* a valid column name. Mainly, this is to enforce the rule that colName
	 * must not be equal to "", since we use that key in the hashtable to store metadata.
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
	 * Returns a cancelled sequence in column colName and with the sequence name seqName.
	 * As a bonus, it works for ANY sequence, but don't tell 'em I told you that.
	 *
	 * @return null, if either the column or sequence name does not exist (or the sequence name does not exist in this column)
	 */
	public Sequence getCancelledSequence(String colName, String seqName) {
		validateColName(colName);
		validateSeqName(seqName);

		Hashtable col = (Hashtable) hash_master.get(colName);
		if(col == null)
			return null;

		Sequence seq = (Sequence) col.get(seqName);

		if(seq == null)
			return null;

		return seq;
	}	

	/**
	 * Returns a list of all sequence names in our datastore. This is easy, since we
	 * keep just such a list for easy retrieval. Indexing, you know. All the cool kids
	 * are doing it.
	 */
	public List getSequences() {
		if(sortBroken)
			resort(sortBy);

		return (List) sortedSequenceNames;
	}

	public Set getSequencesUnsorted() {
		Hashtable ht = (Hashtable) hash_master.get("");

		if(ht == null)
			return new HashSet();	// just an empty Set

		return ht.keySet();
	}

	/**
	 * Returns a set of all column names in this dataStore, uns
	 */
	public List getColumns() {
		if(sortBroken)
			resort(sortBy);

		return (List) sortedColumnNames;
	}

	/**
	 * Returns a set of all column names in this dataStore.
	 */
	public Set getColumnsUnsorted() {
		Set set = new HashSet(hash_master.keySet());	// copy this
		set.remove("");	// get rid of the 'meta' data
		return set;
	}

	/**
	 * Returns a set of all sequence names in column 'colName',
	 * IN ORDER.
	 */
	public List getSequenceNamesByColumn(String colName) {
		LinkedList ll = new LinkedList();

		validateColName(colName);

		if(!isColumn(colName))
			return ll;

		Iterator i = getSequences().iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();

			if(getCancelledSequence(colName, seqName) != null)
				ll.add(seqName);
		}

		return (List) ll;
	}


	/**
	 * Returns a SequenceList containing all the sequences in a column,
	 * IN ORDER.
	 */
	public SequenceList getSequenceListByColumn(String colName) {
		SequenceList sl = new SequenceList();

		validateColName(colName);

		if(!isColumn(colName))
			return null;

		Iterator i = getSequences().iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();
			Sequence seq = getSequence(colName, seqName);

			if(seq != null)
				sl.add(seq);
		}

		return sl;
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

		// if seq doesn't exist, just ignore it;
		// it's hard for anybody but us to check whether
		// a certain sequence is N/A or CANCELLED.
		if(seq == null)
			return;

			// throw new IllegalArgumentException("Column '" + colName + "' doesn't have a sequence named '" + seqName + "'");

		if(cancelled) {
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", new Object());
		} else {
			seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", null);
		}

		// count it up
		if(cancelled)
			count_cancelledSequences++;
		else
			count_cancelledSequences--;

		// sort order has been broken
		sortBroken = true;
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

			ht.put(seqName, new Integer(oldCount+1));
		}

		// okay, by this point, the sort has been broken
		sortBroken = true;
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

		if(col.size() == 1) {
			// if there are no keys left 
			// (the only key left is the '""' key), 
			// we can get rid of the column entirely
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

				//System.err.println("For " + seqName + ": oldCount was " + oldCount + ", it's now " + (oldCount - 1));
				oldCount--;

				if(oldCount == 0) {
					// get rid of the sequence!
					ht.remove(seqName);
					if(outgroupName != null && seqName.equalsIgnoreCase(outgroupName)) {
						outgroupName = null;	
					}
				} else {
					ht.put(seqName, new Integer(oldCount));
				}
			}
		}

		// sort order was broken
		sortBroken = true;
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
			return -1;
			// throw new IllegalArgumentException("Column '" + colName + "' doesn't exist.");
		
		return ((Integer)col.get("")).intValue();
	}

	/**	
	 * How many columns have a non-N/A value for sequence seqName? 
	 */
	public int getCharsetsCount(String seqName) {
		int count = 0;

		validateSeqName(seqName);

		Iterator i = getColumnsUnsorted().iterator();
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

		// just to be on the safe side, assume this broke sort too
		sortBroken = true;
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
	public Sequence getCombinedSequence(String seqName) {
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
	public int getCombinedSequenceLength(String seqName) {
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
	public Sequence getCompleteSequence(String seqName) {
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
	public int getCompleteSequenceLength() {
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
		currentTableModel = (TableModel) new DisplayCountsModel(matrix, this);
		additionalColumns = 3;
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
	public void addSequenceList(SequenceList sl, DelayCallback delay) { 
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

		addSequenceList(newColName, sl, delay);
		sl.unlock();
	}

	/**
	 * Add a new sequence list to this dataset.
	 * @param colName The name of the new column to create for this SequenceList.
	 *
	 * Important steps: create a new column, check for pre-existing sequences,
	 * add all the sequences, and report sequences which got written over to
	 * the user.
	 * 
	 * NOTE: We will NOT throw a DelayAbortedException. We will swallow it without
	 * any effect, mostly because I don't see the point of loading _half_ a sequence
	 * list.
	 */
	public void addSequenceList(String colName, SequenceList sl, DelayCallback delay) { 	
		sl.lock();
		
		// we need to call getUseWhichName() early
		// because we can't call it after the
		// delay goes off. 
		matrix.getPrefs().getUseWhichName();

		// ookay, now that that's done ...
		if(delay != null)
			delay.begin();

		// String buffer droppedSequences records which sequences were dropped, so we can let the user know.
		StringBuffer droppedSequences = new StringBuffer("");

		Iterator i = sl.iterator();
		int count = 0;
		while(i.hasNext()) {
			try {
				if(delay != null)
					delay.delay(count, sl.count());
			} catch(DelayAbortedException e) {
				// ignore!
			}

			count++;

			Sequence seq = (Sequence) i.next();
			String seqName = null;

			// ignore sequences whose actualLength is zero
			if(seq.getActualLength() == 0)
				continue;
			
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

			// HACK/TODO: For use by #sequences loading
			// (see Exporter)
			if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialColName") != null)
				colName = (String) seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialColName");

			if(seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialSeqName") != null)
				seqName = (String) seq.getProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialSeqName");

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
			int colLength = getColumnLength(colName);
			if(colLength == -1) {
				// not defined yet
			} else if(seq.getLength() < colLength) { 
				droppedSequences.append("\t" + seqName + ": It is too short (" + seq.getLength() + " bp, while the column is supposed to be " + colLength + " bp)\n");
				continue;
			} else if(seq.getLength() > colLength) {
				droppedSequences.append("\t" + seqName + ": It is too long (" + seq.getLength() + " bp, while the column is supposed to be " + colLength + " bp)\n");
				continue;
			}

			// All done: lay it on!
			setSequence(colName, seqName, seq);
		}

		// update the data
		updateDisplay();

		// close the progressbar, so we can talk to the user if need be
		if(delay != null)
			delay.end();

		// Communicate the droppedSequences list to the user
		if(droppedSequences.length() > 0) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Warning: Sequences were dropped!",
					"Some sequences in the column '" + colName + "' were not added to the dataset. These are:\n" + droppedSequences.toString()
				);

			mb.go();
		}

		// Cleanup time
		sl.unlock();

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

		// Since PDM can't be used with one column, we need to turn PDM off if
		// we just deleted the PDM column
		if(DisplayPairwiseModel.class.isAssignableFrom(currentTableModel.getClass())) {
			if(colName.equals(pdm_colName))
				exitPairwiseDistanceMode();
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

		// reset the session preferences
		matrix.getPrefs().beginNewSession();
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
	 * A convenience function which checks to see if either name1 or name2 are the
	 * outgroupName, in which case they'll get sorted up.
	 */
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
	 */
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
	 */
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
	 */
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
	 */
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
	 */
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

	public void fireTableModelEvent(TableModelEvent e) {
		Iterator i = tableModelListeners.iterator();
		while(i.hasNext()) {
			TableModelListener l = (TableModelListener)i.next();	

			l.tableChanged(e);
		}		
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
		Hashtable widths = new Hashtable();
		JTable j = matrix.getJTable();
		if(j == null)
			return;
		

		// save all widths
		TableColumnModel tcm = j.getColumnModel();
		if(tcm == null)
			return;

		Enumeration e = tcm.getColumns();
		while(e.hasMoreElements()) {
			TableColumn tc = (TableColumn) e.nextElement();
			widths.put(tc.getIdentifier(), new Integer(tc.getWidth()));
		}

		fireTableModelEvent(new TableModelEvent(this, TableModelEvent.HEADER_ROW));
		
		e = tcm.getColumns();
		while(e.hasMoreElements()) {
			TableColumn tc = (TableColumn) e.nextElement();

			Integer oldWidth = (Integer) widths.get(tc.getIdentifier());
			if(oldWidth != null)
				tc.setPreferredWidth(oldWidth.intValue());
		}
	}

//
// 6.2. THE TABLE MODEL SYSTEM. This is how the JTable talks to us ... and we talk back.
//
	/**
	 * Tells us what *class* of object to expect in columns. We can safely expect Strings.
	 * I don't think the world is ready for transferable Sequences just yet ...
	 */
	public Class getColumnClass(int columnIndex) {
		return currentTableModel.getColumnClass(columnIndex);  
	}

	/**
	 * Gets the number of columns.
	 */
	public int getColumnCount() {
		return currentTableModel.getColumnCount();
	}
	
	/**
	 * Gets the number of rows.
	 */
	public int getRowCount() {
		return currentTableModel.getRowCount(); 
	}

	/**
	 * Gets the name of column number 'columnIndex'.
	 */
        public String getColumnName(int columnIndex) {
		return currentTableModel.getColumnName(columnIndex);
	}

	/**
	 * Convenience function.
	 */
	public String getRowName(int rowIndex) {
		return (String) currentTableModel.getValueAt(rowIndex, 0);
	}

	/**
	 * Gets the value at a particular column. The important
	 * thing here is that two areas are 'special':
	 * 1.	Row 0 is reserved for the column names.
	 * 2.	Column 0 is reserved for the row names.
	 * 3.	(0, 0) is to be a blank box (new String("")).
	 */
        public Object getValueAt(int rowIndex, int columnIndex) {
		return currentTableModel.getValueAt(rowIndex, columnIndex);
	}

	/**
	 * Determines if you can edit anything. Which is only the sequences column.
	 */
        public boolean isCellEditable(int rowIndex, int columnIndex) {
		return currentTableModel.isCellEditable(rowIndex, columnIndex);
	}

	/** 
	 * Allows the user to set the value of a particular cell. That is, the
	 * sequences column. 
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		currentTableModel.setValueAt(aValue, rowIndex, columnIndex);
	}

//
// 7.	RENAMING OF SEQUENCES AND OTHER SUCHLIKE MATTERS.
//

	/**
	 * Fake renaming sequence seqOld to seqNew. We don't actually
	 * DO anything - just figure out which changes need to be 
	 * made, and report this back to the callee.
	 */
	public String fakeRenameSequence(String seqOld, String seqNew) {
		StringBuffer buff_replaced = new StringBuffer();

		// actually rename things in the Master Hash
		Iterator i_cols = getColumns().iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();	
			Sequence seq = getSequence(colName, seqOld);

			if(getSequence(colName, seqOld) != null) {
				Sequence replacing = getSequence(colName, seqNew);
				
				if(replacing != null) {
					// the new name already exists!
					
					if(seq.getActualLength() > replacing.getActualLength()) {
						buff_replaced.append("\tIn column '" + colName + ": " + seqNew + " will be replaced by the sequence formerly known as " + seqOld + ", since it is longer.\n");
					} else {
						buff_replaced.append("\tIn column '" + colName + ": " + seqOld + " will be removed, since " + seqNew + " is longer than it is.\n");
					}
				}
			}
		}

		if(buff_replaced.length() > 0)
			return buff_replaced.toString();
		else
			return null;
	}

	/**
	 * Rename sequence seqOld to seqNew,
	 * and update everything.
	 */
	public void renameSequence(String seqOld, String seqNew) {
		if(seqOld.equals(seqNew))
			return;

		String strWarning = fakeRenameSequence(seqOld, seqNew);

		if(strWarning != null) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Are you sure?",
					"Renaming '" + seqOld + "' to '" + seqNew + "' will be tricky because there are already sequences named '" + seqNew + "' in the dataset. Are you sure you want to make these replacements?\n\nThe following replacements will be carried out:\n" + strWarning,
					MessageBox.MB_YESNO
					);

			if(mb.showMessageBox() == MessageBox.MB_NO)
				return;
		}

		if(outgroupName != null && seqOld.equals(outgroupName)) {
			outgroupName = seqNew;
			sortBroken = true;
		}

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
						setSequence(colName, seqNew, seq);
					} else {
						// don't do anything - the sequence known as seqNew wins
					}
				} else {
					// the new name does NOT exist
					setSequence(colName, seqNew, seq);
				}
			}
		}

		updateDisplay();
	}

//
// UI over the table
//
	/**
	 * Event: somebody right clicked in the mainTable somewhere
	 */
	public void rightClick(MouseEvent e, int col, int row) {
		/*
		popupMenu.show((Component)e.getSource(), e.getX(), e.getY());
		*/
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
	}

	/**
	 * Event: somebody double clicked in the mainTable somewhere
	 */
	public void doubleClick(MouseEvent e, int col, int row) {
		if(row > 0 && col != -1 && col >= additionalColumns) {
			// it's, like, valid, dude.
			toggleCancelled(getColumnName(col), getRowName(row));
		}
	}

	/**
	 * Get the current outgroup.
	 * @return null, if there is no current outgroup.
	 */
	public String getOutgroupName() {
		return outgroupName;
	}

	/**
	 * Set the current outgroup. Changes the current outgroup
	 * to the name mentioned.
	 */
	public void setOutgroupName(String newName) {
		outgroupName = newName;
		sortBroken = true;
	}

	/**
	 * How many sequences are currently cancelled?
	 */
	public int getCancelledSequencesCount() {
		return count_cancelledSequences;
	}

	/** 
	 * Activate PDM. Normally, we'd ask the user
	 * at this point which column he wants to use, 
	 * but for now we can just ignore it and get
	 * on with life.
	 */
	public boolean enterPairwiseDistanceMode() {
		if(getColumns().size() == 0)
			return false;

		return enterPairwiseDistanceMode((String)getColumns().get(0));
	}

	/** Activate PDM */
	public boolean enterPairwiseDistanceMode(String colNameOfInterest) {
		// are we already in PDM? In which case, we just need to
		// swap the colNameOfInterest around
		if(DisplayPairwiseModel.class.isAssignableFrom(currentTableModel.getClass())) {
			// already in PDM, need to turn this off
			DisplayPairwiseModel dpm = (DisplayPairwiseModel) currentTableModel;

			if(isColumn(colNameOfInterest)) {
				if(dpm.resortPairwiseDistanceMode(colNameOfInterest))
					updateDisplay();
				else
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
	 */
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
}


