/**
 * The Exporter is responsible for exporting sequences (and statistics), 
 * generally after checking with matrix.getPrefs() over which preferences
 * have been selected, and how the user would like the files.
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006-07, 2009, 2010 Gaurav Vaidya
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
         *
         * Note: because of that bug where the "please wait" dialog opens and closes too quickly, I'm just
         * going to ignore the 'delay' argument. Please reinsert if needed.
	 */
	public void exportSequencesByColumn(File dir, FormatHandler fh, boolean writeNASequences, DelayCallback delay) throws IOException, DelayAbortedException {
		TableManager tm = matrix.getTableManager();

		Vector<String> vec_sequences = new Vector<String>( (Collection) tm.getSequenceNames());
		int count_columns = tm.getCharsets().size();
		Iterator i = tm.getCharsets().iterator();

		int count = 0;
		while(i.hasNext()) {
			count++;

			String colName = (String) i.next();

			int colLength = tm.getColumnLength(colName);
			SequenceList sl = new SequenceList();

			Iterator i2 = vec_sequences.iterator();	
			while(i2.hasNext()) {
				String seqName = (String) i2.next();
				// okay, this is a little odd, so listen up
				// we can't just 'get the Sequence, put it into a List, and export it'
				// because then it assumes it's TRUE name, NOT the name specified in the
				// left-most column.
				//
				//  This fixes issue #112, if you're interested.
				Sequence seq = tm.getSequence(colName, seqName);
	
				if(seq == null) {
					if(writeNASequences) {
						sl.add(Sequence.makeEmptySequence(seqName, colLength));
					} else {
						// seq == null ... ignore it
					}
				} else {
					// seq != null
					// write it!
					seq = new Sequence(seq);	// clone 'seq'
					seq.changeName(seqName);	// and rename it to the 'correct' row name.
					sl.add(seq);
				}
			}

			int attempts = 1;	// Start with x_1.txt.
			String offset;

			offset = "";
			File writeTo;
			do {
				writeTo = new File(dir, makeFileName(colName) + offset + "." + fh.getExtension());
				if(writeTo.exists()) {
					writeTo = null;
					offset = "_" + attempts;

					if(attempts > 100)
						throw new IOException("Can't create file '" + writeTo + "' - it already exists!");
					
					attempts++;
				}
			} while(writeTo == null);

			if(writeTo.exists() && !writeTo.canWrite()) {
				throw new IOException("Couldn't open '" + writeTo + "' for writing. Are you sure you have permissions to write into this directory?");
			}

			try {
				fh.writeFile(writeTo, sl, null);
			} catch(IOException e) {
				throw e;
			}
		}
	}

	private String makeFileName(String name) {
		return name.replace(' ', '_').replace('.', '_');
	}

	public void exportColumnsInGroups(int total_randomizations, int per_group, int taxa_to_randomly_delete, File f_directory_to_export_to, String taxonToNeverDelete, FormatHandler handler, boolean bool_includeNAs, DelayCallback delay) throws DelayAbortedException, IOException {
		//new MessageBox(matrix.getFrame(), "Dude", "Okay, check this out: " + total_randomizations + ", " + per_group).go();
		
		// Step 1: Get stuff ready
		TableManager tm = matrix.getTableManager();
		List list_columns = tm.getCharsets();
		Random rand = new Random();

		if(f_directory_to_export_to == null || !f_directory_to_export_to.exists() || !f_directory_to_export_to.canWrite()) {
			new MessageBox(matrix.getFrame(),
					"Folder doesn't exist, or isn't writable!",
					"You are trying to write columns in groups into " + f_directory_to_export_to + ", but it either doesn't exist, or you don't have permission to write to it."
			).go();
			return;
		}

		if(list_columns == null) {
			new MessageBox(matrix.getFrame(),
					"No columns to export!",
					"You have no columns to export. Please import some data and try again. If that doesn't help, this is probably a programming problem, and should be reported to the developers.").go();
			return;
		}
		
		// Step 2: Go!
		for(int randomization = 0; randomization < total_randomizations; randomization++) {
			LinkedList list_to_pick = new LinkedList(); 	// so we get O(1) deletion
			list_to_pick.addAll(list_columns);

			// we're in randomization #..., so let's create the directory!
			File f_dir = new File(f_directory_to_export_to, "" + (randomization+1));
			try {
				f_dir.mkdir();
			} catch(SecurityException e) {
				new MessageBox(matrix.getFrame(),
						"Can't create subfolder for randomization: " + f_dir,
						"I'm trying to create '" + f_dir + "' to store the results of randomization #" + (randomization + 1) + ", but I don't seem to have adequate permissions to do this.").go();
				return;
			}

			int group_id = -1;	// so we'll be '0' on the first iteration, for symmetry with randomization
			while(list_to_pick.size() > 0) {
				// pick per_group columns
				LinkedList list_to_add = new LinkedList();
				group_id++;

				for(int x = 0; x < per_group; x++) {
					int max = list_to_pick.size();
					if(max == 0)		// nobody else to pick!
						break;
					int selection = rand.nextInt(max); // random no between 0 and (max - 1);

					// now, move it out of one group, and into the other.
					Object o = list_to_pick.get(selection);
					list_to_pick.remove(o);
					list_to_add.add(o);
				}

				// okay, we now have a list_to_add. We need to add it.
				// wait, do we?
				if(list_to_add.size() == 0)	// the for loop didn't run, because we perfectly picked the size ... or something. In any case, nothing to add, what.
					break;

				// as a reminder, we need to write the cluster to: $folder/$rand/yeast_$rand_$no.txt
				SequenceGrid sg = new SequenceGrid();
				
				Iterator i = list_to_add.iterator();
				while(i.hasNext()) {
					String colName = (String) i.next();
					sg.addColumnFromDataStore(tm.getDataStore(), colName, bool_includeNAs);	// add this column to the SequenceGrid.
				}

				// now, remove 'taxa_to_randomly_delete' taxa from this DataStore.
				// DO YOU HAVE ANY IDEA HOW FRIGGIN EASY THIS IS AFTER THE LAST
				// PUSHAROUND?!?!
				Vector<String> v = new Vector<String>(sg.getSequences());
				if(taxonToNeverDelete != null && taxonToNeverDelete.length() > 0)
					v.remove(taxonToNeverDelete);

				if(v.size() != 0)
					for(int x = 0; x < taxa_to_randomly_delete; x++) {
						int index = rand.nextInt(v.size());
						String seqName = (String) v.get(index);
						sg.deleteRow(seqName);
						v.remove(seqName);
					}
				
				// we now have a DataStore we'd like to Write Out.
				// the easiest way to do that: rewrite the Nexus and TNT
				// exports to handle DataStores directly.
				//
				// Fun.
				//

				File f = new File(f_dir, "group_" + (randomization + 1) + "_" + (group_id + 1) + "." + handler.getExtension());

				handler.writeFile(f, sg.getSequenceList(), delay);
			}
		}
	}

	////////////////////////////
	// NOTE
	///////////////////////////
	// FUNKY COMBINATION EXPORTS END HERE
	// WHOLE-TABLE-TO-ONE-FIlE-FORMAT EXPORTS START HERE
	/////////////////////////

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
		TableManager tm = matrix.getTableManager();
		List columns = tm.getCharsets();
		List sequences = tm.getSequenceNames();

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
				int myLength = tm.getSequenceLength(seqName);

				if(myLength >= length)
					buff.append((x + offset) + " ");
			}

		} else if(name.startsWith(Taxonsets.prefix_TaxonsHaving)) {
			name = name.replaceFirst(Taxonsets.prefix_TaxonsHaving, "");

			// so ... we've got a name now
			for(int x = 0; x < sequences.size(); x++) {
				String seqName = (String) sequences.get(x);

				if(tm.getSequence(name, seqName) != null)
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
				int myCharsetCount = tm.getCharsetsCount(seqName);

				if(myCharsetCount >= charsets)
					buff.append((x + offset) + " ");
			}

		} else {
			throw new RuntimeException("Unknown taxonset " + name + " in Exporter.getTaxonset()");
		}

		// do we have *anything*?
		String str_tmp = buff.toString().trim();
		if(str_tmp.length() == 0)
			return null;

		return str_tmp;
	}	

	/**
	 * Exports the current matrix as a Sequences file. I wish we could use the main system to pull off
	 * this particular trick, but it really is a whole lot easier to just write it out. Atleast I can
	 * say that's because the output format is so incredibly well done :P.
	 */
	public void exportAsSequences(File f, DelayCallback delay) throws IOException, DelayAbortedException {
		TableManager tm = matrix.getTableManager();

		if(delay != null)
			delay.begin();

		PrintWriter writer = new PrintWriter(new FileWriter(f));
		writer.println("#sequences (nucleotide sequencematrix)");
		writer.println();

		List colNames = tm.getCharsets();
		List seqNames = tm.getSequenceNames();

		Iterator i_cols = colNames.iterator();
		while(i_cols.hasNext()) {
			String colName = (String) i_cols.next();

			Iterator i_seqs = seqNames.iterator();
			while(i_seqs.hasNext()) {
				String seqName = (String) i_seqs.next();

				Sequence seq = tm.getSequence(colName, seqName);
				boolean cancelled = false;
				if(seq == null) {
					if(tm.isSequenceCancelled(colName, seqName)) {
						seq = tm.getCancelledSequence(colName, seqName);
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

	public void exportAsPhylip(File f, DelayCallback delay) throws IOException, DelayAbortedException {
		TableManager tm = matrix.getTableManager();

		// Construct a SequenceList of concatenated sequences to output,
		// then let the PhylipFile routines handle it.
		SequenceList list = new SequenceList();

		// Make sure we add the sequences in the correct order.
		List seqNames = tm.getSequenceNames();
		for(Object o_seqName: seqNames) {
			String sequenceName = o_seqName.toString();

			Sequence seq_row = new Sequence();
			seq_row.changeName(sequenceName);
			for(Object o_charsetName: tm.getCharsets()) {
				String charsetName = o_charsetName.toString();

				Sequence seq = tm.getSequence(charsetName, sequenceName);
				if(seq == null)
					seq = Sequence.makeEmptySequence(sequenceName, tm.getColumnLength(charsetName));

				seq_row.concatSequence(seq);
			}

			list.add(seq_row);
		}

		// 'list' is now ready for export!
		PhylipFile pf = new PhylipFile();
		pf.writePhylipFile(f, list, delay);
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
				seq.setProperty(DataStore.INITIAL_COLNAME_PROPERTY, val);

				return true;
			} else if(key.equalsIgnoreCase("sequencematrix.seqname")) {
				seq.setProperty(DataStore.INITIAL_SEQNAME_PROPERTY, val);

				return true;
			} else if(key.equalsIgnoreCase("sequencematrix.cancelled")) {
				// it's an ugly hack, but it'll do for now
				seq.setProperty(DataStore.CANCELLED_PROPERTY, new Object());

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
         * 1.   Hashtable[colName] --&gt; Hashtable[seqName] --&gt; Sequence
         * 2.   We can get seqName lists, sorted.
         *
         * The way it works is fairly simple:
         * 1.   If PREF_NEXUS_BLOCKS:
         *              for every column:
         *                      write the column name in comments
         *                      for every sequence:
         *                              write the column name
         *                              write the sequence
         *                              write the length
         *                      ;
         *                      write the column name in comments
         *              ;
         * 2.   If PREF_NEXUS_SINGLE_LINE:
         *              for every sequence name:
         *                      for every column:
         *                              see if an entry occurs in the column
         *                              if not write in a 'blank'
         *                      ;
         *              ;
         * 
         * 3.   If PREF_NEXUS_INTERLEAVED:
         *              create a new sequence list
         *
         *              for every sequence name:
         *                      for every column:
         *                              if column has sequence:
         *                                      add sequence
         *                              else
         *                                      add blank sequence
         *                              ;
         *                      ;
         *              ;
         *
         *              use NexusFile to spit out the combined file on the sequence list.
         *
         * @throws IOException if there was a problem writing this file
         */
        public void exportAsNexus(
			File	f,
			int		exportAs,
			int		interleaveAt,
			DelayCallback delay)
		throws IOException, DelayAbortedException
		{
			boolean nakedNexusMode = false;

			// Check for the 'NAKED_FORMAT' flag, and if so,
			// activate our own flag.
			if((exportAs & Preferences.PREF_NEXUS_NAKED_FORMAT) != 0) {
				nakedNexusMode = true;

				// Important: get RID of the NAKED_FORMAT bit!
				exportAs -= Preferences.PREF_NEXUS_NAKED_FORMAT;

			}

                TableManager tm = matrix.getTableManager();
                int countThisLoop = 0;


                // how do we have to do this?
                int how = exportAs;


                // set up delay 
                if(delay != null)
                        delay.begin();


                // let's get this party started, etc.
                // we begin by obtaining the Taxonsets (if any).
                Taxonsets tx = matrix.getTaxonsets();

                StringBuffer buff_sets = new StringBuffer();            // used to store the 'SETS' block
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

                                // Sanitize taxonsetNames, just in case.
                                taxonsetName = taxonsetName.replaceAll("[^\\w]", "_");

                                if(str != null)
                                        buff_sets.append("\tTAXSET " + taxonsetName + " = " + str + ";\n");
                        }
                }


                // we begin by calculating the SETS block,
                // since:
                // 1.   we need to coordinate the names right from the get-go
                // 2.   INTERLEAVED does not have to write the Nexus file
                //      at all, but DOES need the SETS block.
                //


                // Calculate the SETS blocks, with suitable widths etc. 
                int widthThusFar = 0;
                Iterator i = tm.getCharsets().iterator();

                countThisLoop = 0;
                while(i.hasNext()) {
                        countThisLoop++;
                        if(delay != null)
                                delay.delay(countThisLoop, tm.getCharsets().size());


                        String columnName = (String)i.next();


                        // write out a CharSet for this column, and adjust the widths
                        buff_sets.append("\tCHARSET " + fixColumnName(columnName) + " = " + (widthThusFar + 1) + "-" + (widthThusFar + tm.getColumnLength(columnName)) + ";\n");
                        widthThusFar += tm.getColumnLength(columnName);
                }


                // end and write the SETS block
                buff_sets.append("END;");

                // It's easier if we have a SequenceGrid to deal with.
                SequenceGrid grid = (SequenceGrid) (matrix.getTableManager().getDataStore());

                // Here's the thing: each sequence has its own positional
                // information, which (we assume) is consistent within a
                // column (which is a pretty safe assumption from now,
                // as we only accept positional data from Nexus and TNT,
                // neither of which support per-sequence positional data).

                // Unfortunately, we'd like to produce only a single set
                // of positional data for the entire dataset. To simplify
                // things, we create three strings, gradually build them
                // up, and then combine them at the end.
                
                // Note (this being an important point): we only use the
                // FIRST taxon in the table to determine CODONPOSSET
                // information to be emitted.

                // This is very likely indeed to work!
                StringBuffer[] array_strbuff_positions = new StringBuffer[4];
                array_strbuff_positions[0] = new StringBuffer();
                array_strbuff_positions[1] = new StringBuffer();
                array_strbuff_positions[2] = new StringBuffer();
                array_strbuff_positions[3] = new StringBuffer();

                String position_names[] = { "N", "1", "2", "3" };

				StringBuffer buff_pergene_codonpossets = new StringBuffer();

                i = matrix.getTableManager().getCharsets().iterator(); 
                int horzOffset = 0;
                while(i.hasNext()) {
                    String colName = (String) i.next();

                    Set seqNames = grid.getSequenceNamesByColumn(colName);
                    if(seqNames.size() > 0) {
                        // get the first sequence
                        Sequence seq = grid.getSequence(colName, (String) seqNames.toArray()[0]);

                        String str_end = ",\n";

						// NOTE: If you start with x = 0, you'll get the 'N'
						// as well. But we don't want them. Turn them on, any
						// time.
                        for(int x = 1; x <= 3; x++) {
							Vector v = (Vector) seq.getProperty("position_" + x);

							if(v == null)
								continue;

							StringBuffer buff_this_gene = new StringBuffer();

							// 'v' is now a List of FromToPairs.
							// Let's combine them up if possible.
							if (x == 0) {
								v = new Vector(combineFTPs(v, 1));
							} else {
								v = new Vector(combineFTPs(v, 3));
							}

							Collections.sort(v);

							Iterator i_v = v.iterator();
							while (i_v.hasNext()) {
								FromToPair ftp = (FromToPair) i_v.next();
								// buff_nexus_positions.append("[" + horzOffset + "] (" + ftp.from + ") - (" + ftp.to + ")" + str_end);

								if (x == 0) {
									str_end = " ";
								} else if (x == 1 || x == 2 || x == 3) {
									str_end = "\\3 ";
								}

								if (ftp.from == ftp.to) {
									array_strbuff_positions[x].append(
											(horzOffset + ftp.from) + " ");
									buff_this_gene.append((horzOffset + ftp.from) + " ");
								} else {
									array_strbuff_positions[x].append(
											(horzOffset + ftp.from) + "-" + (horzOffset + ftp.to) + str_end);
									buff_this_gene.append((horzOffset + ftp.from) + "-" + (horzOffset + ftp.to) + str_end);
								}
							}

							if(buff_this_gene.length() > 0) {
								// Woah! Content!
								buff_this_gene.insert(0, "\tCHARSET " + fixColumnName(colName) + "_pos" + position_names[x] + " = ");
								buff_this_gene.append(";\n");

								buff_pergene_codonpossets.append(buff_this_gene);
							}
                        }
                    }

                    // Add to the horizontal offset.
                    horzOffset += grid.getColumnLength(colName);
                }

                // Let's see if we can't calculate the nexus positions.
                StringBuffer buff_nexus_positions = new StringBuffer();
                
                buff_nexus_positions.append("BEGIN CODONS;\n\tCODONPOSSET * CodonPositions = \n");

                // Combine these bits in a way that makes sure the commas go into
                // the right places (and ONLY the right places).
                boolean flag_display_nexus_positions = false;

                // Change zero-length strings to null.
                for(int x = 0; x <= 3; x++) {
                    if(array_strbuff_positions[x].length() == 0)
                        array_strbuff_positions[x] = null;
                }


                for(int x = 0; x <= 3; x++) {
                    String str_end = "";

                    // Check if there is any other string left; if not,
                    // a comma is unnecessary.
                    for(int y = x + 1; y <= 3; y++) {
                        if(array_strbuff_positions[y] != null) {
                            str_end = ",";
                            break;
                        }
                    }
                
                    if(array_strbuff_positions[x] != null) {
                        buff_nexus_positions.append("\t\t" + position_names[x] + ": " + array_strbuff_positions[x] + str_end + "\n");
                        flag_display_nexus_positions = true;
                    }
                }

                buff_nexus_positions.append("\t;\n");
                //buff_nexus_positions.append("\tCODESET * UNTITLED = Universal: all ;\n");
                buff_nexus_positions.append("END;\n\n");

                if(flag_display_nexus_positions)
                    buff_sets.insert(0, buff_nexus_positions);

				if(buff_pergene_codonpossets.length() > 0) {
					buff_sets.append("\n\nBEGIN ASSUMPTIONS;\n\t[ This section contains " +
							"per-charset positional data. It will not be read by " +
							"SequenceMatrix when this file is read. ]\n" +
							buff_pergene_codonpossets +
							"\nEND;\n\n"
					);
				}
                                    
                // Now that the blocks are set, we can get down to the real work: writing out
                // all the sequences. This is highly method specific.
                //
                // First, we write out the header, unless it's going to use NexusFile to
                // do the writing.
                PrintWriter writer = null;
                if(how == Preferences.PREF_NEXUS_BLOCKS || how == Preferences.PREF_NEXUS_SINGLE_LINE) {
                        writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));


                        writer.println("#NEXUS");
						if(!nakedNexusMode)
							writer.println("[Written by " + matrix.getName() + " on " + new Date() + "]");

                        writer.println("");

                        writer.println("BEGIN DATA;");
                        writer.println("\tDIMENSIONS NTAX=" + tm.getSequencesCount() + " NCHAR=" + tm.getSequenceLength() + ";");


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
                        Iterator i_cols = tm.getCharsets().iterator();


                        countThisLoop = 0;
                        while(i_cols.hasNext()) {
                                if(delay != null)
                                        delay.delay(countThisLoop, tm.getCharsets().size());
                                countThisLoop++;


                                String colName = (String) i_cols.next();
                                int colLength = tm.getColumnLength(colName);
                                
                                // first of all, write the column name in as a comment (if in block mode)
								if(!nakedNexusMode)
									writer.println("[beginning " + fixColumnName(colName) + "]");


                                // then loop over all the sequences
                                Iterator i_seqs = tm.getSequenceNames().iterator();
                                while(i_seqs.hasNext()) {
                                        String seqName = (String) i_seqs.next();
                                        Sequence seq = tm.getSequence(colName, seqName); 


                                        if(seq == null)
                                                seq = Sequence.makeEmptySequence(seqName, colLength);


										// NOTE: There's no guarantee that all the names generated
										// will be unique! However, this is fairly unlikely, and
										// uniqueness is not necessary for Nexus, so YAGNI and don't
										// bother for now.
									
										if(nakedNexusMode) {
											// Sanitize the name.
											String name = seqName.replaceAll("[^0-9A-Za-z]", "_");

											// Write the sequence out.
											writer.print(name + " " + seq.getSequence());
										} else {
											writer.print("'" + getNexusName(seqName) + "' " + seq.getSequence());
											writer.print(" [" + colLength + " bp]");
										}

										writer.println("");	// Blank line to end the row.
                                }

								if(!nakedNexusMode)
									writer.println("[end of " + fixColumnName(colName) + "]");
                                writer.println("");     // leave a blank line
                        }


                } else if(how == Preferences.PREF_NEXUS_SINGLE_LINE || how == Preferences.PREF_NEXUS_INTERLEAVED) {
                        // loop over sequence names


                        Iterator i_rows = tm.getSequenceNames().iterator();
                        countThisLoop = 0;
                        while(i_rows.hasNext()) {
                                if(delay != null)
                                        delay.delay(countThisLoop, tm.getSequenceNames().size());
                                
                                countThisLoop++;


                                String seqName = (String) i_rows.next();
                                Sequence seq_interleaved = null;
                                int length = 0;


                                if(how == Preferences.PREF_NEXUS_SINGLE_LINE) {
									if(nakedNexusMode)
										writer.print(seqName.replaceAll("[^0-9A-Za-z]", "_") + " ");
									else
                                        writer.print("'" + getNexusName(seqName) + "' ");
								} else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
                                        seq_interleaved = new Sequence();


                                Iterator i_cols = tm.getCharsets().iterator();
                                while(i_cols.hasNext()) {
                                        String colName = (String) i_cols.next();
                                        Sequence seq = tm.getSequence(colName, seqName);


                                        if(seq == null)
                                                seq = Sequence.makeEmptySequence(colName, tm.getColumnLength(colName));


                                        length += seq.getLength();


                                        if(how == Preferences.PREF_NEXUS_SINGLE_LINE)
                                                writer.print(seq.getSequence());
                                        else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
                                                seq_interleaved = seq_interleaved.concatSequence(seq);
                                        else
                                                throw new RuntimeException("'how' makes no sense in SequenceGrid.exportAsNexus()! [how = " + how + "]");
                                }


                                if(how == Preferences.PREF_NEXUS_INTERLEAVED)
                                        seq_interleaved.changeName(seqName);

                                if(how == Preferences.PREF_NEXUS_SINGLE_LINE) {
									if(nakedNexusMode)
										writer.println("");
									else
                                        writer.println(" [" + length + " bp]");
								}
                                else if(how == Preferences.PREF_NEXUS_INTERLEAVED)
                                        list.add(seq_interleaved);
                        }
                }


                // close up the file ... if there WAS a file to close, that is.
                if(how == Preferences.PREF_NEXUS_BLOCKS || how == Preferences.PREF_NEXUS_SINGLE_LINE) {
                        // end the DATA block
                        writer.println(";");
                        writer.println("END;");

						if(!nakedNexusMode)
							writer.println(buff_sets);


                        writer.close();
                }
                
                // shut down delay 
                if(delay != null)
                        delay.end();


                // otherwise, err ... actually write the darn file out to begin with :p
                if(how == Preferences.PREF_NEXUS_INTERLEAVED) {
					if(nakedNexusMode)
						buff_sets = new StringBuffer();	// Blank out the sets.

					NexusFile nf = new NexusFile();
					nf.writeNexusFile(f, list, interleaveAt, buff_sets.toString(),
									ProgressDialog.create(
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
		return x.replaceAll("'", "''");
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
		// who's the TableManager?
		TableManager tm = matrix.getTableManager();

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

                                // Sanitize taxonsetNames, just in case.
                                taxonsetName = taxonsetName.replaceAll("[^\\w]", "_");

				if(str != null) 
				{
					if(x == 31)
						buff_title.append("@agroup\n");

					if(x <= 31)
						buff_taxonsets.append("=" + x + " (" + taxonsetName + ") " + str + "\n");
					else
						buff_title.append("=(" + taxonsetName + ") " + str + "\n");
					x++;
				}
			}

			buff_taxonsets.append(";\n\n\n");

			if(x >= 32)
				buff_title.append(";\n\n");
		}
		
		// Time to do character sets!
		StringBuffer buff_sets = new StringBuffer();

                // Here's the thing: each sequence has its own positional
                // information, which (we assume) is consistent within a
                // column (which is a pretty safe assumption from now,
                // as we only accept positional data from Nexus and TNT,
                // neither of which support per-sequence positional data).

                // Unfortunately, we'd like to produce only a single set
                // of positional data for the entire dataset. To simplify
                // things, we create three strings, gradually build them
                // up, and then combine them at the end.

                // Note (this being an important point): we only use the
                // FIRST taxon in the table to determine CODONPOSSET
                // information to be emitted.

                // It's easier if we have a SequenceGrid to deal with.
                SequenceGrid grid = (SequenceGrid) (matrix.getTableManager().getDataStore());

                // This is very likely indeed to work!
                String position_names[] = { "N", "1", "2", "3" };
				HashMap<String, StringBuffer> positionalData = new HashMap<String, StringBuffer>();

                Iterator i = matrix.getTableManager().getCharsets().iterator();
                int horzOffset = 0;
                while(i.hasNext()) {
                    String colName = (String) i.next();

                    Set seqNames = grid.getSequenceNamesByColumn(colName);
                    if(seqNames.size() > 0) {
                        // get the first sequence
                        Sequence seq = grid.getSequence(colName, (String) seqNames.toArray()[0]);

						// Note: if you change this x = 0, you'll get 'N' as
						// well. Since we don't want this right now, we're
						// turning it off. Turn it on whenever.
                        for(int x = 1; x <= 3; x++) {
                            Vector v = (Vector) seq.getProperty("position_" + x);

                            if(v != null) {
								String charsetName = fixColumnName(colName) + "_pos" + position_names[x];
								positionalData.put(charsetName, new StringBuffer());

                                Iterator i_v = v.iterator();
                                while(i_v.hasNext()) {
                                    FromToPair ftp = (FromToPair) i_v.next();
                                    // buff_nexus_positions.append("[" + horzOffset + "] (" + ftp.from + ") - (" + ftp.to + ")" + str_end);

                                    int increment_by = 1;
                                    if(x == 1 || x == 2 || x == 3)
                                        increment_by = 3;

                                    // Note those -1s! They're to change our 1-index based calculations
                                    // into 0-based TNT coordinates.

                                    if(ftp.from == ftp.to) {
                                        positionalData.get(charsetName).append(
                                            (horzOffset + ftp.from - 1) + " "
                                        );
                                    } else {

                                        // Iterate, iterate.
                                        for(int y = (horzOffset + ftp.from); y <= (horzOffset + ftp.to); y += increment_by) {
                                            positionalData.get(charsetName).append((y-1) + " ");
                                        }
                                    }
                                }

								// Wipe it out if there's nothing there.
								if(positionalData.get(charsetName).length() == 0)
									positionalData.put(charsetName, null);
                            }
                        }
                    }

                    // Add to the horizontal offset.
                    horzOffset += grid.getColumnLength(colName);
                }

		// Get ready for the upcoming sets.
		buff_sets.append("xgroup\n");

		List cols = tm.getCharsets();

		int codonsets_go_into_title_at = 32;

		// Store the positional information into the first three xgroups.
		// Then let everything else fall below them.
		int colid = 0;
		Object[] charsetNames = (Object[]) positionalData.keySet().toArray();
		Arrays.sort(charsetNames);

		// Are we in the title yet.
		boolean now_in_the_title = false;

		// TODO: I assume, eventually, charsets will move into the
		// positionalData structure too, and then we'll just have a
		// single loop emitting them. Not for now, though. Not for
		// now.
		for (Object a : charsetNames) {
			String x = a.toString();
			
			if (positionalData.get(x) == null)
				continue;

			if (colid < codonsets_go_into_title_at) {
				buff_sets.append("=" + colid + " (" + x + ") " + positionalData.get(x) + "\n");
			} else {
				if (!now_in_the_title) {
					buff_title.append("@xgroup\n");
				}
				now_in_the_title = true;

				buff_title.append("=(" + x + ") " + positionalData.get(x) + "\n");
			}

			// increment the column id
			if (colid < codonsets_go_into_title_at) {
				colid++;
			}
		}

		int number_of_columns = cols.size() + colid;
		if(number_of_columns >= 32) {
		    new MessageBox(
			matrix.getFrame(),
			"Too many character sets!",
			"According to the manual, TNT can only handle 32 character sets. You have " + number_of_columns + " character sets. I will write out the remaining character sets into the file title, from where you can copy it into the correct position in the file as needed."
		    ).go();
		}

                //buff_tnt_positions.append(";\n");
                //buff_tnt_positions.append("*** Positional data for this dataset ends here. ***'\n");


		// Now for normal sets
		i = cols.iterator();
		int at = 0;
		while (i.hasNext()) {
			String colName = (String) i.next();


			if (colid < codonsets_go_into_title_at) {
				buff_sets.append("=" + colid + " (" + fixColumnName(colName) + ")\t");
			} else {
				if (!now_in_the_title) {
					buff_title.append("@xgroup\n");
				}
				now_in_the_title = true;

				buff_title.append("=(" + fixColumnName(colName) + ")\t");
			}

			// Actually spell out charsets here.
			for (int x = 0; x < tm.getColumnLength(colName); x++) {
				if (colid < codonsets_go_into_title_at) {
					buff_sets.append(at + " ");
				} else {
					buff_title.append(at + " ");
				}
				at++;
			}

			// Generate positional data for

			if (colid < codonsets_go_into_title_at) {
				buff_sets.append("\n");
			} else {
				buff_title.append("\n");
			}

			// increment the column id
			if (colid < codonsets_go_into_title_at) {
				colid++;
			}
		}

		buff_sets.append("\n;\n\n");

		if(colid > (codonsets_go_into_title_at - 1))
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
		writer.println(tm.getSequenceLength() + " " + tm.getSequencesCount());

		Iterator i_rows = tm.getSequenceNames().iterator();
		int count_rows = 0;
		while(i_rows.hasNext()) {
			if(delay != null)
				delay.delay(count_rows, tm.getSequencesCount());

			count_rows++;

			String seqName = (String) i_rows.next();
			Sequence seq_interleaved = null;
			int length = 0;

			writer.print(getTNTName(seqName) + " ");

			Iterator i_cols = cols.iterator();
			while(i_cols.hasNext()) {
				String colName = (String) i_cols.next();
				Sequence seq = tm.getSequence(colName, seqName); 
				
				if(seq == null)
					seq = Sequence.makeEmptySequence(colName, tm.getColumnLength(colName));

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

	private String getTNTName(String name) {
		int len = TNTFile.MAX_TAXON_LENGTH;

// Rule #1: the name must start with '[A-Za-z0-9\-\+\.]'
		char first = name.charAt(0);
		if(
		 	(first >= 'A' && first <= 'Z') ||
			(first >= 'a' && first <= 'z') ||
			(first >= '0' && first <= '9') ||
			(first == '_')
		) {
			// it's all good!
		} else {
			name = "_" + name;
		}

		// Rule #2: strange characters we'll turn into '_'
		name = name.replaceAll("[^a-zA-Z0-9\\-\\+\\.\\_\\*\\:\\(\\)\\|\\\\\\/]", "_");

		// Rule #3: spaces we'll turn into '_'
		name = name.replace(' ', '_');

		// Done! We would ordinarily truncate here, but
		// that would be complicated.
		return name;
	}

	private String fixColumnName(String columnName) {
		columnName = columnName.replaceAll("\\.nex", "");
		columnName = columnName.replaceAll("[^A-Za-z0-9\\_]", "_");
		return columnName;
	}

	/** Combines the list of FromToPairs.
	 * @param movesIn Should be '0' for sequences which move in 3s, and '3' for
	 * sequences which move in 3s.
	 */
	private List<FromToPair> combineFTPs(List<FromToPair> list, int movesIn) {
		Stack<FromToPair> results = new Stack<FromToPair>();

		Collections.sort(list);

		FromToPair current_sequence = null;
		for(FromToPair ftp: list) {

			if(current_sequence != null) {

				// A sequence is in progress
				if(ftp.from == (current_sequence.to + movesIn)) {
					// This FTP is also in the sequence.
					current_sequence.to = ftp.to;
				} else {
					// End the last sequence, start a new sequence.
					results.add(current_sequence);
					current_sequence = ftp;
				}
			} else {
				// A sequence is not in progress.
				current_sequence = ftp;
			}
		}

		if(current_sequence != null)
			results.add(current_sequence);

		return results;
	}
}
