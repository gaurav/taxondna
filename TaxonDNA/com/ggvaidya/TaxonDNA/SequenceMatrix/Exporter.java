/**
 * The Exporter is responsible for exporting sequences (and statistics), 
 * generally after checking with matrix.getPrefs() over which preferences
 * have been selected, and how the user would like the files.
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

public class Exporter {
	private SequenceMatrix matrix;

	public Exporter(SequenceMatrix matrix) {
		this.matrix = matrix;
	}

	//
	// Processing functions
	//
	/**
	 * Exports the table as a tab delimited file. This is a pretty
	 * brainless, dump-everything-on-the-table-to-file function.
	 */
	public void exportTableAsTabDelimited(File file) throws IOException {
		TableModel tableModel = matrix.getTableModel();
		
		PrintWriter writer = new PrintWriter(new FileWriter(file));

		// intro
		writer.println("Exported by " + matrix.getName() + " at " + new Date());

		// print columns
		int cols = tableModel.getColumnCount();
		for(int x = 0; x < cols; x++) {
			writer.print(tableModel.getColumnName(x) + "\t");
		}
		writer.println();

		// print table 
		int rows = tableModel.getRowCount();	
		for(int y = 0; y < rows; y++) {
			for(int x = 0; x < cols; x++) {
				writer.print(tableModel.getValueAt(y, x) + "\t");
			}
			writer.println();
		}

		writer.flush();
		writer.close();
	}

	/**
	 * Returns a String with the taxonset named 'name'.
	 * This is a string describing the taxonset as numbers
	 * from (zero + offset) to (N + offset), where 'offset'
	 * is the second argument.
	 *
	 * @param name The name of the Taxonset
	 * @param offset The offset of the taxon indexes. If this is zero, the first taxon will be zero, and if this is one, the first taxon will be one.
	 */
	public String getTaxonset(String name, int offset) {
		StringBuffer buff = new StringBuffer();
		DataStore dataStore = matrix.getDataStore();
		List columns = new Vector(dataStore.getColumns());
		List sequences = new Vector(dataStore.getSequences());

		// 1. Figure out what is being talked about here
		if(name.startsWith(Taxonsets.prefix_Length)) {
			// it's a length!
			int length = -1;
			name = name.replaceAll(Taxonsets.prefix_Length, "");

			try {
				length = Integer.parseInt(name);
			} catch(NumberFormatException e) {
				throw new RuntimeException("Can't figure out length for " + name + " in Exporter.getTaxonset()");
			}

			// now figure out a list of all taxa with atleast 'length' total length
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x); 
				int myLength = matrix.getDataStore().getCompleteSequenceLength(seqName);

				if(myLength >= length)
					buff.append((x + offset) + " ");
				x++;
			}

			if(buff.length() == 0)
				return null;

		} else if(name.startsWith(Taxonsets.prefix_CharSets)) {
			// it's a charset!
			int charsets = -1;	
			name = name.replaceAll(Taxonsets.prefix_CharSets, "");

			try {
				charsets = Integer.parseInt(name);
			} catch(NumberFormatException e) {
				throw new RuntimeException("Can't figure out charset count for " + name + " in Exporter.getTaxonset()");
			}

			// now figure out a list of all taxa with atleast 'charsets' number of charsets
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x);
				int myCharsetCount = matrix.getDataStore().getCharsetsCount(seqName);

				if(myCharsetCount >= charsets)
					buff.append((x + offset) + " ");
			}

			if(buff.length() == 0)
				return null;
		} else {
			throw new RuntimeException("Unknown taxonset " + name + " in Exporter.getTaxonset()");
		}

		return buff.toString();
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
		DataStore dataStore = matrix.getDataStore();

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
				String str = getTaxonset(taxonsetName, 1);
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
		Iterator i = dataStore.getColumns().iterator();
		while(i.hasNext()) {
			String columnName = (String)i.next();

			// write out a CharSet for this column, and adjust the widths
			buff_sets.append("\tCHARSET " + fixColumnName(columnName) + " = " + (widthThusFar + 1) + "-" + (widthThusFar + dataStore.getColumnLength(columnName)) + ";\n");
			widthThusFar += dataStore.getColumnLength(columnName);
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
			writer.println("\tDIMENSIONS NTAX=" + dataStore.getSequencesCount() + " NCHAR=" + dataStore.getCompleteSequenceLengthWithGaps() + ";");

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
			Iterator i_cols = dataStore.getColumns().iterator();

			while(i_cols.hasNext()) {
				String colName = (String) i_cols.next();
				int colLength = dataStore.getColumnLength(colName);
				
				// first of all, write the column name in as a comment (if in block mode)
				writer.println("[beginning " + fixColumnName(colName) + "]");

				// then loop over all the sequences
				Iterator i_seqs = dataStore.getSequences().iterator();
				while(i_seqs.hasNext()) {
					String seqName = (String) i_seqs.next();
					Sequence seq = dataStore.getSequence(colName, seqName); 

					if(seq == null)
						seq = Sequence.makeEmptySequence(seqName, colLength);

					writer.println(getNexusName(seqName) + " " + seq.getSequence() + " [" + colLength + " bp]"); 
				}
				
				writer.println("[end of " + fixColumnName(colName) + "]");
				writer.println("");	// leave a blank line
			}

		} else if(how == Preferences.PREF_NEXUS_SINGLE_LINE || how == Preferences.PREF_NEXUS_INTERLEAVED) {
			// loop over sequence names

			Iterator i_rows = dataStore.getSequences().iterator();
			while(i_rows.hasNext()) {
				String seqName = (String) i_rows.next();
				Sequence seq_interleaved = null;
				int length = 0;

				if(how == Preferences.PREF_NEXUS_SINGLE_LINE)
					writer.print(getNexusName(seqName) + " ");
				else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
					seq_interleaved = new Sequence();

				Iterator i_cols = dataStore.getColumns().iterator();
				while(i_cols.hasNext()) {
					String colName = (String) i_cols.next();
					Sequence seq = dataStore.getSequence(colName, seqName);

					if(seq == null)
						seq = Sequence.makeEmptySequence(colName, dataStore.getColumnLength(colName));

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
		DataStore dataStore = matrix.getDataStore();
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
					String str = getTaxonset(taxonsetName, 0);
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
		if(dataStore.getColumns().size() > 32) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Too many files!",
					"According to the manual, TNT can only handle 32 character sets. You have " + dataStore.getColumns().size() + " character sets. Would you like me to write all the groups out anyway? TNT might not be able to read this file.\n\nClick 'No' to not write out any sets.",
					MessageBox.MB_YESNO);

			writeAnyway = false;
			if(mb.showMessageBox() == MessageBox.MB_YES)
				writeAnyway = true;
		}
		
		StringBuffer buff_sets = new StringBuffer();
		if(writeAnyway) {
			buff_sets.append("xgroup\n");

			Iterator i = dataStore.getColumns().iterator();	
			int at = 0;
			int colid = 0;
			while(i.hasNext()) {
				String colName = (String) i.next();

				buff_sets.append("=" + colid + " (" + fixColumnName(colName) + ")\t");
				colid++;

				for(int x = 0; x < dataStore.getColumnLength(colName); x++) {
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
		writer.println(dataStore.getCompleteSequenceLengthWithGaps() + " " + dataStore.getSequencesCount());

		Iterator i_rows = dataStore.getSequences().iterator();
		int count_rows = 0;
		while(i_rows.hasNext()) {
			if(delay != null)
				delay.delay(count_rows, dataStore.getSequencesCount());

			count_rows++;

			String seqName = (String) i_rows.next();
			Sequence seq_interleaved = null;
			int length = 0;

			writer.print(getNexusName(seqName) + " ");

			Iterator i_cols = dataStore.getColumns().iterator();
			while(i_cols.hasNext()) {
				String colName = (String) i_cols.next();
				Sequence seq = dataStore.getSequence(colName, seqName); 
				
				if(seq == null)
					seq = Sequence.makeEmptySequence(colName, dataStore.getColumnLength(colName));

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
