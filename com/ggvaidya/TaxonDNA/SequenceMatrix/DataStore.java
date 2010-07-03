/**
 * The DataStore is the underlying datastore for in SequenceMatrix. 
 * After the Great Creation of the Table Manager (circa early evening, 
 * Dec 5, 2006), the DataStore was forked to be ENTIRELY the backend, 
 * mindlessly storing (colName, seqName)-located information for retrieval
 * and usage. 
 *
 * We work closely with the TableManager on UI based events. However, all
 * actual display/etc. is handled by the TableManager. We don't TOUCH all
 * that crap. We do one thing (manage sequences in columns) and we do it
 * WELL. Everybody else is free to pick up on whatever they want.
 *
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

import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.Model.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class DataStore extends SequenceGrid {
	// Defines: should we ignore differently sized sequences in the same sequence list?
	// Note that exports will be severelly retarded!
	//
	public static final boolean IGNORE_SIZES = false;

	// The property value we use for Sequence.getProperty(...)
	/** 
	 * CANCELLED_PROPERTY:
	 * If this property is set to ANY non-null value (we use a new Object()), it means that 
	 * particular sequence has been cancelled. You can uncancel it by setting this property 
	 * to null. 
	 */
	public static final String CANCELLED_PROPERTY =	"com.ggvaidya.TaxonDNA.SequenceMatrix.DataStore.cancelled";

	/** INITIAL_COLNAME_PROPERTY: If this property is set during an addSequenceList(), then 
	 * the name specified is used as the column name instead of the column specified to the 
	 * addSequenceList() call. 
	 */
	public static final String INITIAL_COLNAME_PROPERTY = "com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialColName";

	/** INITIAL_SEQNAME_PROPERTY: If this property is set during an addSequenceList(), then 
	 * the name specified is used as the sequence name (the seqName) instead of the full 
	 * sequence name. This allows us to reimplement [[Use Species Name/Use Sequence Name]] 
	 * options without actually pissing anybody off.
	 */
	public static final String INITIAL_SEQNAME_PROPERTY = "com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialSeqName";

	/** The actual data itself. */
	private Hashtable hash_master = new Hashtable();	// the master hash:
								// Hashtable[colName] = Hashtable[seqName] = Sequence
								// Hashtable[colName] = Hashtable[""] = Integer(max_length)
								// Hashtable[""] = Hashtable[seqName] = Integer(count_sequence)
								// (my Perl roots are showing)
								//
								// Please don't use this hash directly. That just makes
								// life more fun for the debuggers. Please use the
								// helper functions below.

	// Counting the number of cancelled sequences (so we know while exporting, etc.)
	private int	count_cancelledSequences = 0;

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

		if(seq.getProperty(CANCELLED_PROPERTY) != null)
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
	 * Returns a set of all sequence names in our datastore, unsorted.
	 */
	public Set getSequences() {
		Hashtable ht = (Hashtable) hash_master.get("");

		if(ht == null)
			return new HashSet();	// just an empty Set

		return ht.keySet();
	}

	/**
	 * Returns a set of all column names in this dataStore, unsorted.
	 */
	public Set getColumns() {
		Set set = new HashSet(hash_master.keySet());	// copy this
		set.remove("");	// get rid of the 'meta' data
		return set;
	}

	/**
	 * Returns a set of all sequence names in column 'colName'. No order guaranteed,
	 * although you WILL get all the cancelled sequences as well (i.e. ALL sequence
	 * names). 
	 *
	 * This is required, to avoid a situation where a column with NO sequences
	 * exists - as it cannot actually exist; such a column ought to have been deleted
	 * automatically once the last column was deleted.
	 */
	public Set getSequenceNamesByColumn(String colName) {
		Set set = (Set) new HashSet();

		validateColName(colName);

		if(!isColumn(colName))
			return set;

		Iterator i = getSequences().iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();

			if(getCancelledSequence(colName, seqName) != null)
				set.add(seqName);
		}

		return set;
	}

	/**
	 * Returns a SequenceList containing all the sequences in a column.
	 * No order guaranteed, which makes me wonder why you'd want to do
	 * this at all.
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
			return 0;	// just an empty DataStore

		return ht.size();
	}
	
	/**
	 * Checks whether the sequence at (colName, seqName) is 'cancelled'.
	 * A cancelled sequence cannot be accessed via getSequence(), which
	 * will pretend it doesn't exist (returning null as it does). You
	 * *can* access cancelled sequences via getCancelledSequence();
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

		if(seq.getProperty(CANCELLED_PROPERTY) != null)
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
			seq.setProperty(CANCELLED_PROPERTY, new Object());
		} else {
			seq.setProperty(CANCELLED_PROPERTY, null);
		}

		// count it up
		if(cancelled)
			count_cancelledSequences++;
		else
			count_cancelledSequences--;
	}

	/**
	 *	Toggles 'cancelled' state on the specified sequence.
	 *	i.e. if it is cancelled, it is now uncancelled; and
	 *	vice versa.
	 *
	 *	I'd optimize this (by unrolling the function calls)
	 *	but you know what they say about premature 
	 *	optimization!
	 *
	 *	That's actually a pretty good point, since we're
	 *	not likely to toggle cancelled very frequently
	 *	or anything.
	 */
	public void toggleCancelled(String colName, String seqName) {
		if(isSequenceCancelled(colName, seqName))
			setSequenceCancelled(colName, seqName, false);
		else
			setSequenceCancelled(colName, seqName, true);
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
			// column doesn't exist: MAKE IT!
			col = new Hashtable();
			hash_master.put(colName, col);
			col.put("", new Integer(seq.getLength()));
		} else {
			// test the column length
			if(!IGNORE_SIZES && seq.getLength() != getColumnLength(colName))
				throw new IllegalArgumentException("Column " + colName + " has a length of " + getColumnLength(colName) + ", but you are trying to set a sequence '" + seqName + "' with a length of " + seq.getLength());
		}
		col.put(seqName, seq);
		
		// by now, the sequence has been 'inserted'.
		// now, we need to update the master index
		Hashtable ht = (Hashtable) hash_master.get("");
		if(ht == null) {
			// we don't have a master index: MAKE IT!
			ht = new Hashtable();
			hash_master.put("", ht);
		}

		if(ht.get(seqName) == null)
			ht.put(seqName, new Integer(1));
		else {
			int oldCount = ((Integer)ht.get(seqName)).intValue();

			ht.put(seqName, new Integer(oldCount+1));
		}
	}

	/**	
	 * 	Deletes the sequence mentioned from the column mentioned. It also 
	 * 	gets rid of the column itself, if it's empty.
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
				} else {
					ht.put(seqName, new Integer(oldCount));
				}
			}
		}
	}

	/**
	 * Returns the 'width' (or length, whatever) of column colName. This
	 * is the basepair width that new sequences must be the same size
	 * as.
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
	 * 
	 * TODO: Rewrite this to be resilient to column length changes?
	 * 	By padding every sequence up to the newLength? It's worth
	 * 	a think.
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
	 * for a specified seqName. Note the distinction between a CombinedSequence
	 * and a CompleteSequence: a CombinedSequence consists of all the sequences
	 * which exist, while CompleteSequence consists of all the sequences required
	 * to recreate the line in SequenceMatrix - i.e., INCLUDING the gaps.
	 */
	public Sequence getCombinedSequence(String seqName) {
		Sequence result = new Sequence();

		validateSeqName(seqName);

		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			Sequence seq = getSequence(colName, seqName);
			if(seq != null)
				result = result.concatSequence(seq);
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
				result = result.concatSequence(seq);
			else
				result = result.concatSequence(Sequence.makeEmptySequence(seqName, getColumnLength(colName)));
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

	/**
	 * How many sequences are currently cancelled?
	 */
	public int getCancelledSequencesCount() {
		return count_cancelledSequences;
	}

// 
// 1. 	CONSTRUCTOR. We need a SequenceMatrix object to talk to the user with.
//
	/** 
	 * In this Brave New World, DataStores are bound to nobody at all. Really.
	 * We just store data. What more do you want? 
	 */
	public DataStore() {}

//
// 2.	ADD COMMANDS. Add a SequenceList, add a file, that sort of thing.	
//
	/**
	 * Add a new sequence list to this dataset.
	 * This one is really just a wrapper; it figures out the name the
	 * column ought to have, and then calls addSequenceList(colName, sl, delay); 
	 */
	public void addSequenceList(SequenceList sl, StringBuffer complaints, DelayCallback delay) throws IndexOutOfBoundsException { 
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
				sl.unlock();

				throw new IndexOutOfBoundsException("SequenceMatrix can only create 999,999 columns with the same column name. Sorry! Please let us know if this limit is too low for you.");
			}
		}

		// use this column name, and add the sequence list
		addSequenceList(newColName, sl, complaints, delay);

		sl.unlock();
	}

	/**
	 * Add a new sequence list to this dataset.
	 * @param colName The name of the column to insert this SequenceList into.
	 * @param sl The sequence list to add under this colName 
	 *
	 * Important steps: create a new column, check for pre-existing sequences,
	 * add all the sequences, and report sequences which got written over to
	 * the user.
	 *
	 * Note that we don't check the column name: all the level 0 functions are
	 * validating it, and we don't touch any of the tables without them. If the
	 * column name is a duplicate, we'll insert the sequence list into the pre-existing
	 * column you specify, which is pretty much guaranteed to be a disaster unless 
	 * the columns have the same length, in which case there will be much overwriting
	 * of sequences and gnashing of teeth, etc.
	 *
	 * If you've used this function earlier, you'll notice we DON'T do name processing
	 * now. That code has moved into TableManager. We're not responsible!
	 * 
	 * NOTE: We will NOT throw a DelayAbortedException. We will swallow it without
	 * any effect, mostly because I don't see the point of loading _half_ a sequence
	 * list.
	 */
	public void addSequenceList(String colName, SequenceList sl, StringBuffer complaints, DelayCallback delay) { 	
		sl.lock();
		
		// let's roll!
		if(delay != null)
			delay.begin();

		// Stringbuffer droppedSequences records which sequences were dropped, so we can let the user know.
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
			seqName = seq.getFullName();

			// Check if we have cues as to what to call this sequence
			if(seq.getProperty(INITIAL_SEQNAME_PROPERTY) != null)
				seqName = (String) seq.getProperty(INITIAL_SEQNAME_PROPERTY);

			// TODO: Cleanup this bit.
			// 
			// As a bit of a HACK, we can put this sequence into another
			// column ENTIRELY if it wants. This allows the #sequences format 
			// to work the way it does, cueing us on where the sequence is
			// supposed to go. It's not very nice, but we can pull off this
			// hack without confusing things much, or ruining things for other
			// people, which - at the end of the day - really is all that 
			// matters.
			//
			if(seq.getProperty(INITIAL_COLNAME_PROPERTY) != null)
				colName = (String) seq.getProperty(INITIAL_COLNAME_PROPERTY);

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
			if(!IGNORE_SIZES) {
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
			}

			// All done: lay it on!
			setSequence(colName, seqName, seq);
		}

		// close the progressbar, so we can talk to the user if need be
		if(delay != null)
			delay.end();

		// fill up the complaints area with our complaints
		complaints.append(droppedSequences);	

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
		//
		// I have no idea why we allocate a new HashSet at this point, and
		// I'm too scared to try and figure it out.
		Iterator i = new HashSet(getSequenceNamesByColumn(colName)).iterator();
		while(i.hasNext()) {
			String seqName = (String) i.next();

			deleteSequence(colName, seqName);
		}
	}

	/**
	 * Deletes the row named 'seqName'.
	 *
	 * Once again, having an efficient, useful and workhorseable deleteSequence() is half the battle.
	 */
	public void deleteRow(String seqName) {
		Iterator i = getColumns().iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();

			deleteSequence(colName, seqName);
		}
	}

	/**
	 * Clear *everything*.
	 */
	public void clear() {
		// I'm guessing we allocate a HashSet for the
		// same reason we allocate one in deleteColumns(),
		// but once again, I'm clueless. Sorry.
		//
		Set set = new HashSet(getColumns());
		Iterator i = set.iterator();
		while(i.hasNext()) {
			String colName = (String) i.next();
		
			deleteColumn(colName);
		}
	}

//
// 7.	RENAMING OF SEQUENCES AND OTHER SUCHLIKE MATTERS.
//

	/**
	 * Fake renaming sequence seqOld to seqNew. We don't actually
	 * DO anything - just figure out which changes need to be 
	 * made, and report this back to the callee.
	 */
	public String testRenameSequence(String seqOld, String seqNew) {
		StringBuffer buff_replaced = new StringBuffer();

		// actually rename things in the Master Hash
		Iterator i_cols = getColumns().iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();	
			Sequence seq = getCancelledSequence(colName, seqOld);

			if(seq != null) {
				Sequence replacing = getCancelledSequence(colName, seqNew);
				
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
	 * and update everything. This will do
	 * it's job, barring an exception - to
	 * see what consequences this move might
	 * have, run testRenameSequence(...)
	 */
	public void forceRenameSequence(String seqOld, String seqNew) {
		if(seqOld.equals(seqNew))
			return;

		// actually rename things in the Master Hash
		Iterator i_cols = getColumns().iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();	

			if(getCancelledSequence(colName, seqOld) != null) {
				// replace
				Sequence seq = getCancelledSequence(colName, seqOld);
				deleteSequence(colName, seqOld);

				Sequence replacing = getCancelledSequence(colName, seqNew);
				
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
	}
}
