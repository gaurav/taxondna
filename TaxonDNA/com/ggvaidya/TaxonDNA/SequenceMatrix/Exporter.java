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

public class Exporter implements SequencesHandler {
	private SequenceMatrix matrix;

	public Exporter(SequenceMatrix matrix) {
		this.matrix = matrix;

		// register us with SequencesFile
		SequencesFile.addSequencesHandler(this);
	}

	//
	// Processing functions
	//
	/**
	 * Exports the table as a tab delimited file. This is a pretty
	 * brainless, dump-everything-on-the-table-to-file function.
	 */
	public void exportTableAsTabDelimited(File file) throws IOException {
		TableModel tableModel = matrix.getTableManager().getTableModel();
		
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

		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Export successful!",
				"This table has been successfully exported to '" + file + "' as a tab-delimited file.");
		mb.go();
	}

	/**
	 * Exports the entire table as individual gene files (one per column) into a particular directory.
	 * This really is kinda convoluted ... no, VERY convoluted. Hatred.
	 */
	public void exportSequencesByColumn(File dir, FormatHandler fh, boolean writeNASequences, DelayCallback delay) throws IOException, DelayAbortedException {
		if(delay != null)
			delay.begin();

		DataStore store = matrix.getTableManager().getDataStore();

		Vector vec_sequences = new Vector( (Collection) store.getSequences());
		int count_columns = store.getColumns().size();
		Iterator i = store.getColumns().iterator();

		int count = 0;
		while(i.hasNext()) {
			if(delay != null)
				delay.delay(count, count_columns);
			count++;

			String colName = (String) i.next();

			int colLength = store.getColumnLength(colName);
			SequenceList sl = new SequenceList();

			Iterator i2 = vec_sequences.iterator();	
			while(i2.hasNext()) {
				String seqName = (String) i2.next();
				Sequence seq = store.getSequence(colName, seqName);
	
				if(seq == null) {
					if(writeNASequences) {
						sl.add(Sequence.makeEmptySequence(seqName, colLength));
					} else {
						// seq == null ... ignore it
					}
				} else {
					// seq != null
					// write it!
					sl.add(seq);
				}
			}

			File writeTo = new File(dir, makeFileName(colName) + "." + fh.getExtension());
			if(writeTo.exists()) {
				// TODO: We need to do something sensible here!
				if(delay != null)
					delay.end();

				throw new IOException("Can't create file '" + writeTo + "' - it already exists!");
			}

			if(writeTo.exists() && !writeTo.canWrite()) {
				if(delay != null)
					delay.end();
				throw new IOException("Couldn't open '" + writeTo + "' for writing. Are you sure you have permissions to write into this directory?");
			}

			try {
				fh.writeFile(writeTo, sl, null);
			} catch(IOException e) {
				if(delay != null)
					delay.end();
				throw e;
			}
		}

		if(delay != null)
			delay.end();
	}

	private String makeFileName(String name) {
		return name.replace(' ', '_').replace('.', '_');
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
		DataStore dataStore = matrix.getTableManager().getDataStore();
		List columns = new Vector(dataStore.getColumns());
		List sequences = new Vector(dataStore.getSequences());

		// 1. Figure out what is being talked about here
		if(name.startsWith(Taxonsets.prefix_Length)) {
			// it's a length!
			int length = -1;
			name = name.replaceFirst(Taxonsets.prefix_Length, "");

			try {
				length = Integer.parseInt(name);
			} catch(NumberFormatException e) {
				throw new RuntimeException("Can't figure out length for " + name + " in Exporter.getTaxonset()");
			}

			// now figure out a list of all taxa with atleast 'length' total length
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x); 
				int myLength = matrix.getTableManager().getDataStore().getCombinedSequenceLength(seqName);

				if(myLength >= length)
					buff.append((x + offset) + " ");
			}

		} else if(name.startsWith(Taxonsets.prefix_TaxonsHaving)) {
			name = name.replaceFirst(Taxonsets.prefix_TaxonsHaving, "");

			// so ... we've got a name now
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x);

				if(matrix.getTableManager().getDataStore().getSequence(name, seqName) != null)
					buff.append((x + offset) + " ");
			}


		} else if(name.startsWith(Taxonsets.prefix_CharSets)) {
			// it's a charset!
			int charsets = -1;	
			name = name.replaceFirst(Taxonsets.prefix_CharSets, "");

			try {
				charsets = Integer.parseInt(name);
			} catch(NumberFormatException e) {
				throw new RuntimeException("Can't figure out charset count for " + name + " in Exporter.getTaxonset()");
			}

			// now figure out a list of all taxa with atleast 'charsets' number of charsets
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x);
				int myCharsetCount = matrix.getTableManager().getDataStore().getCharsetsCount(seqName);

				if(myCharsetCount >= charsets)
					buff.append((x + offset) + " ");
			}

		} else {
			throw new RuntimeException("Unknown taxonset " + name + " in Exporter.getTaxonset()");
		}

		return buff.toString();
	}	

	/**
	 * Exports the current matrix as a Sequences file. I wish we could use the main system to pull off
	 * this particular trick, but it really is a whole lot easier to just write it out. Atleast I can
	 * say that's because the output format is so incredibly well done :P.
	 */
	public void exportAsSequences(File f, DelayCallback delay) throws IOException, DelayAbortedException {
		if(delay != null)
			delay.begin();

		PrintWriter writer = new PrintWriter(f);
		writer.println("#sequences (nucleotide sequencematrix)");
		writer.println();

		List colNames = new Vector(matrix.getTableManager().getDataStore().getColumns());
		List seqNames = new Vector(matrix.getTableManager().getDataStore().getSequences());

		Iterator i_cols = colNames.iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();

			Iterator i_seqs = seqNames.iterator();
			while(i_seqs.hasNext()) {
				String seqName = (String) i_seqs.next();

				Sequence seq = matrix.getTableManager().getDataStore().getSequence(colName, seqName);
				boolean cancelled = false;
				if(seq == null) {
					if(matrix.getTableManager().getDataStore().isSequenceCancelled(colName, seqName)) {
						seq = matrix.getTableManager().getDataStore().getCancelledSequence(colName, seqName);
						cancelled = true;
					} else 
						continue;
				}

				writer.println("> " + seq.getFullName());
				writer.println("^sequencematrix.colname " + colName);
				writer.println("^sequencematrix.seqname " + seqName);
				if(cancelled)
					writer.println("^sequencematrix.cancelled");
				writer.println(seq.getSequenceWrapped(70));

				writer.println();
			}
		}

		writer.flush();
		writer.close();

		if(delay != null)
			delay.end();
	}

//
// EXPERIMENTAL SEQUENCES HANDLER
//
	public boolean readLocalCommand(String cmdLine, Sequence seq) throws FormatException { 
		String[] ret = new String[2];

		if(SequencesFile.isCommand(cmdLine, ret)) {
			String key = ret[0];
			String val = ret[1];

			if(key.equalsIgnoreCase("sequencematrix.colname")) {
				// ooooooooooh kay .. so how ah?
				seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialColName", val);

				return true;
			} else if(key.equalsIgnoreCase("sequencematrix.seqname")) {
				seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.initialSeqName", val);

				return true;
			} else if(key.equalsIgnoreCase("sequencematrix.cancelled")) {
				// it's an ugly hack, but it'll do for now
				seq.setProperty("com.ggvaidya.TaxonDNA.SequenceMatrix.SequenceGrid.cancelled", new Object());

				return true;
			}
		}

		return false; 
	}
	public boolean readGlobalCommand(String cmdLine, SequenceList list) throws FormatException { return false; }
	public String writeLocalCommand(Sequence seq) { return null; }
	public String writeGlobalCommand(SequenceList list) { return null; }
	public String getSequencesHandlerName() {
		return "sequencematrix";
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
	public void exportAsNexus(File f, int exportAs, int interleaveAt, DelayCallback delay) throws IOException, DelayAbortedException {
		DataStore dataStore = matrix.getTableManager().getDataStore();
		int countThisLoop = 0;

		// how do we have to do this?
		int how = exportAs;

		// set up delay 
		if(delay != null)
			delay.begin();

		// let's get this party started, etc.
		// we begin by obtaining the Taxonsets (if any).
		Taxonsets tx = matrix.getTaxonsets(); 
		StringBuffer buff_sets = new StringBuffer();		// used to store the 'SETS' block
		buff_sets.append("BEGIN SETS;\n");
		if(tx.getTaxonsetList() != null) {
			Vector v = tx.getTaxonsetList();
			Iterator i = v.iterator();

			countThisLoop = 0;
			while(i.hasNext()) {
				countThisLoop++;
				if(delay != null)
					delay.delay(countThisLoop, v.size());

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

		countThisLoop = 0;
		while(i.hasNext()) {
			countThisLoop++;
			if(delay != null)
				delay.delay(countThisLoop, dataStore.getColumns().size());

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
			writer.println("\tDIMENSIONS NTAX=" + dataStore.getSequencesCount() + " NCHAR=" + dataStore.getCompleteSequenceLength() + ";");

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

			countThisLoop = 0;
			while(i_cols.hasNext()) {
				if(delay != null)
					delay.delay(countThisLoop, dataStore.getColumns().size());
				countThisLoop++;

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
			countThisLoop = 0;
			while(i_rows.hasNext()) {
				if(delay != null)
					delay.delay(countThisLoop, dataStore.getSequences().size());
				countThisLoop++;

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
		
		// shut down delay 
		if(delay != null)
			delay.end();

		// otherwise, err ... actually write the darn file out to begin with :p
		if(how == Preferences.PREF_NEXUS_INTERLEAVED) {
			NexusFile nf = new NexusFile();
			nf.writeNexusFile(f, list, interleaveAt, buff_sets.toString(), 
					new ProgressDialog(
						matrix.getFrame(),
						"Please wait, writing file ...",
						"Writing out the compiled sequences. Sorry for not warning you about this before. Almost done!"));
		}
		
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
		DataStore dataStore = matrix.getTableManager().getDataStore();

		// We want to put some stuff into the title
		StringBuffer buff_title = new StringBuffer();

		// we begin by obtaining the Taxonsets (if any).
		Taxonsets tx = matrix.getTaxonsets(); 
		StringBuffer buff_taxonsets = new StringBuffer();
		if(tx.getTaxonsetList() != null) {
			if(tx.getTaxonsetList().size() >= 32) {
				new MessageBox(
					matrix.getFrame(),
					"Too many taxonsets!",
					"According to the manual, TNT can only handle 32 taxonsets. You have " + tx.getTaxonsetList().size() + " taxonsets. I will write the remaining taxonsets into the file title, from where you can copy it into the correct position in the file as needed.").go();
			}

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
					if(x == 31)
						buff_title.append("@agroup\n");

					if(x <= 31)
						buff_taxonsets.append("=" + x + " (" + taxonsetName + ") " + str + "\n");
					else
						buff_title.append("=" + x + " (" + taxonsetName + ") " + str + "\n");
					x++;
				}
			}

			buff_taxonsets.append(";\n\n\n");

			if(x >= 32)
				buff_title.append(";\n\n");
		}
		
		// set up the 'sets' buffer
		if(dataStore.getColumns().size() >= 32) {
			new MessageBox(
					matrix.getFrame(),
					"Too many character sets!",
					"According to the manual, TNT can only handle 32 character sets. You have " + dataStore.getColumns().size() + " character sets. I will write out the remaining character sets into the file title, from where you can copy it into the correct position in the file as needed.").go();

		}
		
		StringBuffer buff_sets = new StringBuffer();
		buff_sets.append("xgroup\n");

		Iterator i = dataStore.getColumns().iterator();	
		int at = 0;
		int colid = 0;
		while(i.hasNext()) {
			String colName = (String) i.next();

			if(colid == 32)
				buff_title.append("@xgroup\n");

			if(colid <= 31)
				buff_sets.append("=" + colid + " (" + fixColumnName(colName) + ")\t");
			else
				buff_title.append("=" + colid + " (" + fixColumnName(colName) + ")\t");

			for(int x = 0; x < dataStore.getColumnLength(colName); x++) {
				if(colid <= 31)
					buff_sets.append(at + " ");
				else
					buff_title.append(at + " ");
				at++;
			}

			if(colid <= 31)
				buff_sets.append("\n");
			else
				buff_title.append("\n");
			
			// increment the column id
			colid++;
		}
		
		buff_sets.append("\n;\n\n");

		if(colid > 31)
			buff_title.append("\n;");

		// go!
		if(delay != null)
			delay.begin();		

		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));

		writer.println("nstates dna;");
		writer.println("xread\n'Exported by " + matrix.getName() + " on " + new Date() + ".");
		if(buff_title.length() > 0) {
			writer.println("Additional taxonsets and character sets will be placed below this line.");
			writer.println(buff_title.toString());
			writer.println("Additional taxonsets and character sets end here.");
		}
		writer.println("'");
		writer.println(dataStore.getCompleteSequenceLength() + " " + dataStore.getSequencesCount());

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
