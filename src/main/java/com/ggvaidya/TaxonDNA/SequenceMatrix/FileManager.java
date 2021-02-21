/**
 * This module manages the files for SequenceMatrix. Since each column 
 * is really a SequenceList file, we keep track of source, handler, 
 * and so on for everybody. We also handle exports.
 *
 * It's most important call-to-fame is the asynchronous file loader,
 * handled by spawning a thread which loading the file up. We ensure
 * that:
 * 1.	Two files are never loaded simultaneously.
 * 2.	The interface is locked up while files are being loaded.
 * 
 * Note that after commit e13ef06a09be this file was cleaned up for 
 * pretty much the first time ever. This is directly to make it
 * possible to make charactersets and codonpositions to work together
 * in sensible ways; but this might well broken things in horrible,
 * horrible ways. Let's see.
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006-07, 2009-10 Gaurav Vaidya
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
import java.io.*;
import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.DNA.formats.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;

public class FileManager implements FormatListener {

	private SequenceMatrix matrix;
	private final HashMap<String, ArrayList<FromToPair>> hashmap_codonsets = new HashMap<String, ArrayList<FromToPair>>();
//
//      0.      CONFIGURATION OPTIONS
//
	// Should we use the full name or the species name?
	public static final int PREF_NOT_SET_YET = -1;
	public static final int PREF_USE_FULL_NAME = 0;
	public static final int PREF_USE_SPECIES_NAME = 1;
	private static int pref_useWhichName = PREF_NOT_SET_YET;
	/**
	 * Once we've displayed the Nexus warning, we'll set this flag to true
	 * and never display that warning, ever again.
	 */
	private static boolean f_displayed_nexus_warning = false;

//
// 	1.	CONSTRUCTORS.
//
	/**
	 * Creates a FileManager. We need to know
	 * where the SequenceMatrix is, so we can
	 * talk to the user.
	 */
	public FileManager(SequenceMatrix matrix) {
		this.matrix = matrix;
	}

//
//	2.	COMMON CODE
//
	/**
	 * Ask the user to pick a file to save to.
	 * @return The File object selected, or null if the user cancelled.
	 */
	private File getFile(String title) {
		FileDialog fd = new FileDialog(matrix.getFrame(), title, FileDialog.SAVE);
		fd.setVisible(true);

		if (fd.getFile() != null) {
			if (fd.getDirectory() != null) {
				return new File(fd.getDirectory() + fd.getFile());
			} else {
				return new File(fd.getFile());
			}
		}

		return null;
	}
	// Types to use in reportIOException().
	private static final int IOE_READING = 0;
	private static final int IOE_WRITING = 1;

	/**
	 * Report an IOException to the user. Since IOExceptions are so common
	 * in our code, this is a real time saver.
	 */
	private void reportIOException(IOException e, File f, int type) {
		String verb, preposition;

		if (type == IOE_READING) {
			verb = "reading";
			preposition = "from";
		} else {
			verb = "writing";
			preposition = "to";
		}

		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Error while " + verb + " file",
				"There was an error while " + verb + " this set " + preposition + " '" + f + "'. "
				+ "Please ensure that you have adequate permissions and that your hard disk is not full.\n\n"
				+ "The technical description of this error is: " + e);

		mb.go();
	}

	/**
	 * Reports an DelayAbortedException to the user
	 */
	private void reportDelayAbortedException(DelayAbortedException e, String task) {
		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				task + " cancelled",
				task + " was cancelled midway. The task might be incomplete, and any files being generated might have been partially generated.");

		mb.go();
	}

	/**
	A dialog box to check whether the user would like to use sequence names
	or species names during file import.

	@return either PREF_USE_FULL_NAME or PREF_USE_SPECIES_NAME
	 */
	public int checkNameToUse(String str_sequence_name, String str_species_name) {
		if (str_sequence_name == null) {
			str_sequence_name = "(No sequence name identified)";

		}
		if (str_species_name == null) {
			str_species_name = "(No species name identified)";

			// There's already a preference: return that.

		}
		if (pref_useWhichName != PREF_NOT_SET_YET) {
			return pref_useWhichName;
		}

		// Ask the user what they'd like to use.
		Dialog dg = new Dialog(
				matrix.getFrame(),
				"Species names or sequence names?",
				true // modal!
				);

		dg = (Dialog) CloseableWindow.wrap(dg);	// wrap it as a Closeable window

		dg.setLayout(new BorderLayout());

		TextArea ta = new TextArea("", 9, 60, TextArea.SCROLLBARS_VERTICAL_ONLY);
		ta.setEditable(false);
		ta.setText(
				"Would you like to use the full sequence name? Sequence names from this file look like "
				+ "this:\n\t" + str_sequence_name + "\n\nI can also try to guess the species name, which "
				+ "looks like this:\n\t" + str_species_name + "\n\nNote that the species name might not be "
				+ "guessable for every sequence in this dataset.");
		dg.add(ta);

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.CENTER));

		// Why two default buttons? Because DefaultButtons know
		// when they've been clicked. If I'm reading this correctly,
		// if _neither_ button is clicked (if the Window is closed
		// directly), then _neither_ of the buttons will know that
		// they've been clicked. In that case, we pick USE_FULL_NAME.
		DefaultButton btn_seq = new DefaultButton(dg, "Use sequence names");
		buttons.add(btn_seq);

		DefaultButton btn_species = new DefaultButton(dg, "Use species names");
		buttons.add(btn_species);
		dg.add(buttons, BorderLayout.SOUTH);

		dg.pack();
		dg.setVisible(true);

		if (btn_species.wasClicked()) {
			pref_useWhichName = PREF_USE_SPECIES_NAME;
			
		} else {
			pref_useWhichName = PREF_USE_FULL_NAME;

			
		}
		return pref_useWhichName;
	}

	/**
	 * Checks whether the user would like to convert all external
	 * gaps to question marks, and - if so - does it.
	 */
	private void checkGappingSituation(String colName, SequenceList sl) {
		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Replace external gaps with missing characters during import?",
				"Would you like to recode external gaps as question marks?",
				MessageBox.MB_YESNOTOALL | MessageBox.MB_TITLE_IS_UNIQUE);

		if (mb.showMessageBox() == MessageBox.MB_YES) {
			// yes! do it!
			Iterator i = sl.iterator();
			while (i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				seq.convertExternalGapsToMissingChars();
			}
		}
	}

	/**
	 * Sets up names to use by filling in INITIAL_SEQNAME_PROPERTY in the
	 * Sequence preferences, in-place. You ought to call this on
	 * a SequenceList before you process it. Note that this function
	 * works IN PLACE, but won't change names or anything - only
	 * replace the INITIAL_SEQNAME_PROPERTY with what we think it
	 * we think is the right initial seq name NOW.
	 *
	 * Important note: if the INITIAL_SEQNAME_PROPERTY for a sequence
	 * has already been set, we skip processing that sequence altogether.
	 * The idea is that if the input file format already knows what
	 * name the user likes, we can go ahead and use that.
	 */
	private void setupNamesToUse(SequenceList list) {
		if (list.count() == 0) {
			return;     // Don't handle empty sequence lists

			
		}
		Iterator i = list.iterator();
		while (i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			String sequence_name = seq.getFullName();
			String species_name = seq.getSpeciesName();
			String name;

			// If the loader says there's a definite name which goes
			// with this sequence, use that instead.
			if (seq.getProperty(DataStore.INITIAL_SEQNAME_PROPERTY) != null) {
				continue;

				// We should never get this, but we might as well catch it
				// here rather than letting it progress indefinitely.

			}
			if (sequence_name == null || sequence_name.equals("")) {
				throw new RuntimeException("Sequence name not defined or blank. This should never happen.");
			}

			// Note that if we can't determine the species name, we have
			// to use the sequence name. No two ways around that.
			if (species_name == null || species_name.equals("")) {
				name = sequence_name;
				continue;
			}

			// Check if the sequence name is different from the
			// species name.
			if (sequence_name.equals(species_name)) {
				name = sequence_name;
			} else {
				while (pref_useWhichName == PREF_NOT_SET_YET) {
					checkNameToUse(
							sequence_name,
							species_name);
				}

				// By this point, we should have a value in pref_useWhichName.
				if (pref_useWhichName == PREF_USE_SPECIES_NAME) {
					name = species_name;
				} else {
					name = sequence_name;
				}
			}

			// Now we have a name. Use that.
			seq.setProperty(DataStore.INITIAL_SEQNAME_PROPERTY, name);
		}
	}

	/**
	 * Load the specified file with the provided FormatHandler (or null) and
	 * returns the loaded SequenceList (or null, if there was an error). This
	 * is really part of the addFile() system, so it will provide its own
	 * errors to the user if things go wrong.
	 *
	 * @return The SequenceList loaded, or null if it couldn't be loaded.
	 */
	private SequenceList loadFile(File file, FormatHandler handler) {
		SequenceList list;

		try {
			// Apparently, there's two completely different ways of creating sequence lists,
			// depending on whether we have a known handler or not. That's just bad API
			// design, if you ask me.

			if (handler != null) {
				handler.addFormatListener(this);        // Set up FormatHandler hooks.

				list = new SequenceList(
						file,
						handler,
						ProgressDialog.create(
						matrix.getFrame(),
						"Loading file ...",
						"Loading '" + file + "' (of format " + handler.getShortName() + ") into memory. Sorry for the delay!",
						ProgressDialog.FLAG_NOCANCEL));

			} else {
				list = SequenceList.readFile(
						file,
						ProgressDialog.create(
						matrix.getFrame(),
						"Loading file ...",
						"Loading '" + file + "' into memory. Sorry for the delay!",
						ProgressDialog.FLAG_NOCANCEL),
						this // use us as the FormatListener
						);
			}

		} catch (SequenceListException e) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Could not read file!",
					"I could not understand a sequence in this file. Please fix any errors in the file.\n\n"
					+ "The technical description of the error is: " + e);

			mb.go();

			// Clear any incomplete hashmap_codonsets which might have been set.
			synchronized(hashmap_codonsets) {
				hashmap_codonsets.clear();
			}

			return null;

		} catch (DelayAbortedException e) {
			// Clear any incomplete hashmap_codonsets which might have been set.
			synchronized(hashmap_codonsets) {
				hashmap_codonsets.clear();
			}

			return null;
		}

		if (handler == null)
			handler = list.getFormatHandler();

		if (handler.getShortName().equals("NEXUS")) {
			// A Nexus file! Once per session, we should display the Nexus
			// standard warning.
			if (!f_displayed_nexus_warning) {
				MessageBox.messageBox(
					matrix.getFrame(),
					"Warning: Not all blocks are read from Nexus files!",
					"SequenceMatrix only parses the data and the character set " +
					"and codon set blocks.",
					MessageBox.MB_OK
				);

				f_displayed_nexus_warning = true;
			}
		}

		return list;
	}

	/**
	 * A helper method to remap global-coordinates (relative to the entire sequence) to sequence-only
	 * coordinates (relative to this sequence). This requires much mathematical thinking which is
	 * helpfully provided by this method.
	 *
	 * One caveat: we assume ALL from-tos where 'goesInThirds' is true goes in thirds, which means we
	 * need to do some additional calculations to make sense. This is a little presumptive, but we'll
	 * do it anyway.
	 */
	private Vector<FromToPair> positionalInformationFor(ArrayList<FromToPair> positionData, int index_in_sequence, int from, int to, boolean goesInThirds) {
		Vector<FromToPair> results = new Vector<FromToPair>();

		// I wonder if from-to has any positional information?
		for (FromToPair to_check : positionData) {
			// Check for any overlap whatsoever.
			if (to_check.to >= from && to_check.from <= to) {

				int start_at, end_at;

				if (!goesInThirds) {
					// If we're not going in thirds, this is dead easy.
					start_at = from;
					if (start_at < to_check.from) {
						start_at = to_check.from;

						
					}
					end_at = to;				// End as far right as you can.
					if (end_at > to_check.to) {
						end_at = to_check.to;

						
					}
				} else {
					// If we're going in thirds, this is much more complicated.

					start_at = to_check.from;
					if (to_check.from < from) {
						// If to_check starts up before from,
						// we need to come up with the first
						// starting position for this position
						// which comes after the position at
						// which we end.
						int offset = (from - to_check.from);
						offset = (3 - (offset % 3)) % 3;	 // What madness is this?
						start_at = from + offset;
					}

					end_at = to_check.to;		// Don't go past the end.
					if (to_check.to > to) {
						// If to_check ends after we do,
						// we need to come up with the first
						// ending position for this position
						// which comes before the position
						// at which we end.

						// This is exactly identical to the 'start_at'
						// calculation, except that we floor instead of
						// ceil.
						int offset = (to - start_at);
						offset = -(offset % 3);
						end_at = to + offset;
					}

					// Sanity checks should go here. But what is truely sane
					// in this insane world of ours.
				}

				// System.err.println("Match while comparing [" + from + " to " + to + "] against to_check [" + to_check.from + " to " + to_check.to + "]");

				// System.err.println("\tOffset is wrong; expected 1, obtained " + offset + " by subtracting " + from + " from " + to_check.from + "\n");

				// System.err.println("\tWe obtain a " + (goesInThirds ? "goes in thirds" : "goes sequentially" ) + " sequence from " + start_at + " to " + end_at + " from " + from + " to " + to + " comparing against " + to_check.from + " to " + to_check.to + ".");

				// BUT! This needs to be relative to the start of the current sequence.
				// Hang on, don't we return per-sequence data? So why rebase?
				start_at += index_in_sequence - from + 1;
				end_at += index_in_sequence - from + 1;

				FromToPair ftp_new = new FromToPair(
						start_at, // from
						end_at // to
						);

				// System.err.println("Added position data: " + ftp_new);

				results.add(ftp_new);
			}
		}

		return results;
	}

	/** Reads the sets stored in hashmap_codonsets and splits the datasets into individual sequencelists
	for each non-overlapping character sets.

	(2009-Nov-28) I want to see codonposset information to be read into the file, and I'd love
	to see it recognize overlapping datasets. Will I live to see my dreams realized? Only time
	will tell.

	(2009-Nov-28) These comments are in the code. I'm not sure if they're still relevant, but
	being dire warnings of the downfall of all we hold dear, I suppose we should atleast keep
	it lying around.

	/////////////////////////////////////////////////////////////////////////////////
	// TODO: This code is busted. Since we're not sure what ORDER the messages come
	// in (or that they get entered into the vectors), we need to make sure we SORT
	// the vectors before we start anything funky. Of course, we can't sort them as
	// is, which means another layer of hacks.
	// TODO.
	/////////////////////////////////////////////////////////////////////////////////

	// do stuff
	//
	// this is how it works:
	// 1.	we will NEVER let anybody else see 'sequences' (our main SequenceList)
	// 2.	we split sequences into subsequencelists. These, we will let people see.
	// 3.	any characters remaining 'unmatched' are thrown into 'no sets'
	// 4.	we split everything :(. this is the best solution, but, err, that
	// 	means we have to figure out a delete column. Sigh.
	//

	@return -1 if there was a fatal error (which has already been shown to
	the user), otherwise the number of character sets which have been successfully
	extracted.
	 */
	private int incorporateSets(File file, SequenceList sequences) {
		// charset count.
		int count_charsets = 0;

		// Storage areas
		ArrayList<FromToPair> positions_N = null;
		ArrayList<FromToPair> positions_1 = null;
		ArrayList<FromToPair> positions_2 = null;
		ArrayList<FromToPair> positions_3 = null;

		// We want to make sure nobody touches hashmap_codonsets while we're working. This
		// Should Never Happen (tm), but better safe than sorry.
		synchronized (hashmap_codonsets) {
			HashMap<String, ArrayList<FromToPair>> sets = hashmap_codonsets;

			// Don't let anybody change these sequences, either.
			sequences.lock();

			// Step one: extract the positional sets. These are named ':<position>'.
			// We also remove them from hashmap_codonsets, since this makes subsequent
			// processing a whole lot easier.
			positions_N = sets.get(":0");
			sets.remove(":0");
			positions_1 = sets.get(":1");
			sets.remove(":1");
			positions_2 = sets.get(":2");
			sets.remove(":2");
			positions_3 = sets.get(":3");
			sets.remove(":3");

			// To simplify code, we'll set the positional data to empty datasets
			// (so we don't have to keep testing for 'null', you understand).
			if (positions_N == null) {
				positions_N = new ArrayList<FromToPair>();

			}
			if (positions_1 == null) {
				positions_1 = new ArrayList<FromToPair>();

			}
			if (positions_2 == null) {
				positions_2 = new ArrayList<FromToPair>();

			}
			if (positions_3 == null) {
				positions_3 = new ArrayList<FromToPair>();

			}

			// It would be nice if we could go through these sets and "compress"
			// them down (i.e. 'position 1 = 1, 2, 3' => 'position 1 = 1-3'). But
			// hopefully we'll be fast enough without.
			//
			// If not, you know what TODO.

			// Okay, so now we have a set of positional data sets. Rest of this
			// kinda runs the way it's always run.

			// Okay, now proceed with the rest of our algorithm.
			Iterator<String> i_sets = hashmap_codonsets.keySet().iterator();
			while (i_sets.hasNext()) {
				String charset_name = i_sets.next();
				ArrayList<FromToPair> charset_fromtos = hashmap_codonsets.get(charset_name);
				SequenceList sl_charset = new SequenceList();

				// If the dire comment above is correct, then this next bit is likely quite important.
				// We sort the fromToPairs so that they are in left-to-right order.
				Collections.sort(charset_fromtos);

				// You can move this out of this method quite easily
				// if you really want to.
				DelayCallback pd = ProgressDialog.create(
					matrix.getFrame(),
					"Splitting file by character set",
					"The character set " + charset_name + " is being split from " +
					file + ". We apologize for the delay."
				);

				if(pd != null)
					pd.begin();

				// Our goal here is to create a single SequenceList which consists of
				// all the bits mentioned in charsets_fromtos. Note that these could be
				// incomplete bits (0 .. 12, 14 .. 16) or, in an extreme case, (1, 2, 3, 4).
				// We reassemble them into a sequence, figure out a name for it, and
				// Our Job Here Is Done.
				Iterator i_seq = sequences.iterator();
				int total = sequences.count();
				int count = 0;
				while (i_seq.hasNext()) {
					Sequence seq = (Sequence) i_seq.next();

					try {
						if(pd != null)
							pd.delay(count, total);
					} catch(DelayAbortedException e) {
						return -1;	// A fatal error already shown to the user.
					}

					count++;

					// The new, synthesized sequence we're going to generate.
					Sequence seq_out = new Sequence();
					seq_out.changeName(seq.getFullName());

					// Set up a vector to keep track of coordinates within this
					// sequence which have particular locational information.

					// For simplicity's sake, we'll just store this as integers.
					// So: these are zero-index based indexes into positional
					// information for this sequence.
					Vector<FromToPair> seq_positions_N = new Vector<FromToPair>();
					Vector<FromToPair> seq_positions_1 = new Vector<FromToPair>();
					Vector<FromToPair> seq_positions_2 = new Vector<FromToPair>();
					Vector<FromToPair> seq_positions_3 = new Vector<FromToPair>();

					int index_assembled_sequence = 0;

					// For every FromToPair, pull a single sequence.
					// This is why we sort it above - otherwise the
					// "assembled" sequence will be in the wrong order.
					Iterator<FromToPair> i_coords = charset_fromtos.iterator();
					while (i_coords.hasNext()) {
						FromToPair ftp = i_coords.next();

						int from = ftp.from;
						int to = ftp.to;

						try {
							// System.err.println("Cutting [" + name + "] " + seq.getFullName() + " from " + from + " to " + to + ": " + seq.getSubsequence(from, to) + ";");

							Sequence subseq = seq.getSubsequence(from, to);

							// This is the ONLY point at which we know where subseq came from.
							// So this is the only place where we can correctly impose positional
							// data onto the sequence.

							seq_positions_N.addAll(positionalInformationFor(positions_N, index_assembled_sequence, from, to, false));
							seq_positions_1.addAll(positionalInformationFor(positions_1, index_assembled_sequence, from, to, true));
							seq_positions_2.addAll(positionalInformationFor(positions_2, index_assembled_sequence, from, to, true));
							seq_positions_3.addAll(positionalInformationFor(positions_3, index_assembled_sequence, from, to, true));

							Sequence s = BaseSequence.promoteSequence(subseq);
							seq_out = seq_out.concatSequence(s);

							// Increment the index along.
							index_assembled_sequence += subseq.getLength();

						} catch (SequenceException e) {
							if(pd != null)
								pd.end();

							MessageBox mb_2 = new MessageBox(
									matrix.getFrame(),
									"Uh-oh: Error forming a set",
									"According to this file, character set " + charset_name + " extends from " + from + " to " + to + ". "
									+ "While processing sequence '" + seq.getFullName() + "', I got the following problem:\n"
									+ "\t" + e.getMessage() + "\nI'm skipping this file.");
							mb_2.go();

							sequences.unlock();
							hashmap_codonsets.clear();

							return -1;
						} catch (RuntimeException e) {
							if(pd != null)
								pd.end();

							new MessageBox(matrix.getFrame(),
								"Fatal internal error",
								"A fatal internal error occured while processing " + charset_name + ", extending from " + from + " to " + to + ": " + e.getMessage()).go();

							sequences.unlock();
							hashmap_codonsets.clear();

							return -1;
						}
					}

					// Null out any seq_positions_N without information.
					if (seq_positions_N.size() == 0) {
						seq_positions_N = null;

					}
					if (seq_positions_1.size() == 0) {
						seq_positions_1 = null;

					}
					if (seq_positions_2.size() == 0) {
						seq_positions_2 = null;

					}
					if (seq_positions_3.size() == 0) {
						seq_positions_3 = null;

						// Add any position information in.
						
					}
					seq_out.setProperty("position_0", seq_positions_N);
					seq_out.setProperty("position_1", seq_positions_1);
					seq_out.setProperty("position_2", seq_positions_2);
					seq_out.setProperty("position_3", seq_positions_3);

					// seq_out is ready for use! Add it to the sequence list.
					if (seq_out.getActualLength() > 0) {
						sl_charset.add(seq_out);
						
					}
				}

				if(pd != null)
					pd.end();

				// Sequence list is ready for use!
				addSequenceListToTable(charset_name, sl_charset);
				count_charsets++;
			}

			hashmap_codonsets.clear();
			sequences.unlock();
		}

		return count_charsets;
	}

	/**
	 * Adds a sequence list to Sequence Matrix's table.
	 *
	 * @return Nothing. We report errors directly to the user.
	 */
	private void addSequenceListToTable(String name, SequenceList sl) {
		setupNamesToUse(sl);
		checkGappingSituation(name, sl);

		StringBuffer buff_complaints = new StringBuffer();
		matrix.getTableManager().addSequenceList(name, sl, buff_complaints, null);

		if (buff_complaints.length() > 0) {
			new MessageBox(matrix.getFrame(),
					name + ": Some sequences weren't added!",
					"Some sequences in the taxonset " + name + " weren't added. These are:\n" + buff_complaints.toString()).go();
		}
	}

	/**
	 * Adds a new file to the table. All file loading for SequenceMatrix goes through here.
	 * This method should mostly coordinate the other methods around to get its work done.
	 *
	 * There used to be dragons here, but they've moved out into other methods now.
	 *
	 * This method throws no exceptions; any errors are displayed to the user directly.
	 */
	public void addFile(File file, FormatHandler handler) {

		// Load the files.
		SequenceList sequences = loadFile(file, handler);
		if (sequences == null) {
			return;

			// Figure out the sets.

		}
		synchronized (hashmap_codonsets) {

			// If a file has CODONPOSSETs, but no CODONSETs, we
			// CANNOT allow incorporateSets() to fire, because it
			// won't have any codonsets to extract and we'll end
			// up with nothing being added at all.
			int no_of_sets = hashmap_codonsets.size();

			if (hashmap_codonsets.get(":0") != null) {
				no_of_sets--;

			}
			if (hashmap_codonsets.get(":1") != null) {
				no_of_sets--;

			}
			if (hashmap_codonsets.get(":2") != null) {
				no_of_sets--;

			}
			if (hashmap_codonsets.get(":3") != null) {
				no_of_sets--;


			}
			if (no_of_sets > 0) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"I see sets!",
						"The file " + file + " contains character sets. Do you want me to split the file into character sets?",
						MessageBox.MB_YESNOTOALL | MessageBox.MB_TITLE_IS_UNIQUE);

				if (mb.showMessageBox() == MessageBox.MB_YES) {
					int count = incorporateSets(file, sequences);

					hashmap_codonsets.clear();

					if (count <= -1) {
						return;     // We're not adding this file.
					}

					// Otherwise, by this point, sets have *already been incorporated*.
					return;
				}
			}
		}

		// If we're here, the user chose not to incorporate sets.
		// However, we need to add in any CODONPOSSET information
		// which might still be hanging around.
		synchronized (hashmap_codonsets) {
			HashMap<String, ArrayList<FromToPair>> sets = hashmap_codonsets;

			// Don't let anybody change these sequences, either.
			sequences.lock();

			// Step one: extract the positional sets. These are named ':<position>'.
			// We also remove them from hashmap_codonsets, since this makes subsequent
			// processing a whole lot easier.
			Vector<FromToPair> positions_N = new Vector<FromToPair>();
			Vector<FromToPair> positions_1 = new Vector<FromToPair>();
			Vector<FromToPair> positions_2 = new Vector<FromToPair>();
			Vector<FromToPair> positions_3 = new Vector<FromToPair>();

			boolean nothing_to_do = true;

			if (sets.containsKey(":0")) {
				positions_N.addAll(sets.get(":0"));
				sets.remove(":0");
				nothing_to_do = false;
			}

			if (sets.containsKey(":1")) {
				positions_1.addAll(sets.get(":1"));
				sets.remove(":1");
				nothing_to_do = false;
			}

			if (sets.containsKey(":2")) {
				positions_2.addAll(sets.get(":2"));
				sets.remove(":2");
				nothing_to_do = false;
			}

			if (sets.containsKey(":3")) {
				positions_3.addAll(sets.get(":3"));
				sets.remove(":3");
				nothing_to_do = false;
			}

			// Do we have ANY sort of codonposset information?
			if (!nothing_to_do) {
				// For every sequence in this file, we need to apply the CODONPOSSET
				// information. Rather handily, this information doesn't need to be
				// recalculated or anything and can go right in.
				Iterator i_seq = sequences.iterator();
				while (i_seq.hasNext()) {
					Sequence seq = (Sequence) i_seq.next();

					// Apply the positional information.
					seq.setProperty("position_0", positions_N);
					seq.setProperty("position_1", positions_1);
					seq.setProperty("position_2", positions_2);
					seq.setProperty("position_3", positions_3);
				}
			}

			// We're done here.
			sequences.unlock();
		}

		// Reformat the name so we know what to call this sequencelist.
		String filename = file.getName();

		// Remove the extension, if there is one.
		// Note that this does almost-Windows style extensions
		// (one to FOUR characters at the end, separated
		// from the rest with a single fullstop), so this
		// should leave funky names like "test.sequences"
		// alone.
		filename = filename.replaceFirst("\\.\\w{1,4}$", "");

		addSequenceListToTable(filename, sequences);
	}

	/**
	 * Tries to load a particular file into the present SequenceList. If successful, it will sequences the present
	 * file to be whatever was specified. If not successful, it will leave the external situation (file, sequences)
	 * unchanged.
	 */
	public void addFile(File file) {
		addFile(file, null);
	}

	/**
	 * Ask the user for the file, even.
	 */
	public void addFile() {
		FileDialog fd = new FileDialog(matrix.getFrame(), "Which file would you like to open?", FileDialog.LOAD);
		fd.setVisible(true);

		if (fd.getFile() != null) {
			if (fd.getDirectory() != null) {
				addFile(new File(fd.getDirectory() + fd.getFile()));
			} else {
				addFile(new File(fd.getFile()));
			}
		}
	}

//
//	X.	EXPORTERS.
//
	/**
	 * 	Export the current set as a Nexus file into file 'f'. Be warned that File 'f' will be
	 * 	completely overwritten.
	 *
	 * 	Right now we're outsourcing this to the SequenceGrid; we might bring it in here in the
	 * 	future.
	 */
	public void exportAsNexus() {
		if (!checkCancelledBeforeExport()) {
			return;

			
		}
		Dialog frame = (Dialog) CloseableWindow.wrap(new Dialog(matrix.getFrame(), "Export as Nexus ...", true));
		RightLayout rl = new RightLayout(frame);
		frame.setLayout(rl);

		rl.add(new Label("Save as:"), RightLayout.NONE);
		FileInputPanel finp = new FileInputPanel(
				null,
				FileInputPanel.MODE_FILE_WRITE,
				frame);
		rl.add(finp, RightLayout.BESIDE | RightLayout.STRETCH_X);

		rl.add(new Label("Export as:"), RightLayout.NEXTLINE);
		Choice choice_exportAs = new Choice();
		choice_exportAs.add("Interleaved");
		choice_exportAs.add("Non-interleaved");
		rl.add(choice_exportAs, RightLayout.BESIDE | RightLayout.STRETCH_X);

		rl.add(new Label("Interleave at:"), RightLayout.NEXTLINE);
		TextField tf_interleaveAt = new TextField(40);
		rl.add(tf_interleaveAt, RightLayout.BESIDE | RightLayout.STRETCH_X);

		DefaultButton btn = new DefaultButton(frame, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_exportAs.select(matrix.getPrefs().getPreference("exportAsNexus_exportAs", 0, 0, 1));
		tf_interleaveAt.setText(new Integer(matrix.getPrefs().getPreference("exportAsNexus_interleaveAt", 1000, 0, 1000000)).toString());
		finp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumn_fileName", "")));

		frame.pack();
		frame.setVisible(true);

		File file = null;
		if (btn.wasClicked() && ((file = finp.getFile()) != null)) {
			matrix.getPrefs().setPreference("exportAsNexus_exportAs", choice_exportAs.getSelectedIndex());

			int exportAs = Preferences.PREF_NEXUS_INTERLEAVED;
			switch (choice_exportAs.getSelectedIndex()) {
				default:
				case 0:
					exportAs = Preferences.PREF_NEXUS_INTERLEAVED;
					break;
				case 1:
					exportAs = Preferences.PREF_NEXUS_SINGLE_LINE;
					break;
			}

			int interleaveAt = 1000;
			try {
				interleaveAt = Integer.parseInt(tf_interleaveAt.getText());
			} catch (NumberFormatException e) {
				if (new MessageBox(matrix.getFrame(),
						"Warning: Couldn't interpret 'Interleave At'",
						"I can't figure out which number you mean by '" + tf_interleaveAt.getText() + "'. I'm going to use '1000' as a default length. Is that alright with you?",
						MessageBox.MB_YESNO).showMessageBox() == MessageBox.MB_NO) {
					return;
					
				}
				interleaveAt = 1000;
			}
			matrix.getPrefs().setPreference("exportAsNexus_interleaveAt", interleaveAt);
			matrix.getPrefs().setPreference("exportSequencesByColumn_fileName", file.getParent());

			// phew ... go!
			try {
				matrix.getExporter().exportAsNexus(
						file,
						exportAs,
						interleaveAt,
						ProgressDialog.create(
						matrix.getFrame(),
						"Please wait, exporting sequences ...",
						"All your sequences are being exported as a single Nexus file into '" + file + "'. Sorry for the wait!"));
			} catch (IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e);
				mb.go();

				return;
			} catch (DelayAbortedException e) {
				return;
			}

			new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a Nexus file.").go();
		}
	}

	/**
	 * Quick-export as a plain Nexus file.
	 */
	public void quickExportAsNexus() {
		if (!checkCancelledBeforeExport())
			return;

		File file = getFile("Export as Nexus ...");
		if (file == null)
			return;

		try {
			matrix.getExporter().exportAsNexus(
					file,
					Preferences.PREF_NEXUS_INTERLEAVED,
					1000,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting sequences ...",
					"All your sequences are being exported as a single Nexus file into '" + file + "'. Sorry for the wait!"));
		} catch (IOException e) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error writing sequences to file!",
					"The following error occured while writing sequences to file: " + e);
			mb.go();

			return;
		} catch (DelayAbortedException e) {
			return;
		}

		new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a Nexus file.").go();
	}

	/**
	 * Quick-export as a plain Nexus file.
	 */
	public void quickExportAsPhylip() {
		if (!checkCancelledBeforeExport())
			return;

		File file = getFile("Export for RAxML analyses on CIPRES ...");
		if (file == null)
			return;

		try {
			matrix.getExporter().exportAsPhylip(file,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting sequences ...",
					"All your sequences are being exported for RAxML analyses as '" + file + "'. Sorry for the wait!")
			);
		} catch (IOException e) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error writing sequences to file!",
					"The following error occured while writing sequences to file: " + e);
			mb.go();

			return;
		} catch (DelayAbortedException e) {
			return;
		}

		new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a Phylip file.").go();
	}

		/**
	 * Quick-export as a plain Nexus file.
	 */
	public void quickExportAsNexusNonInterleaved() {
		if (!checkCancelledBeforeExport())
			return;

		File file = getFile("Export as Nexus (non-interleaved) ...");
		if (file == null)
			return;

		try {
			matrix.getExporter().exportAsNexus(
					file,
					Preferences.PREF_NEXUS_SINGLE_LINE,
					100,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting sequences ...",
					"All your sequences are being exported as a single, non-interleaved Nexus file into '" + file + "'. Sorry for the wait!"));
		} catch (IOException e) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error writing sequences to file!",
					"The following error occured while writing sequences to file: " + e);
			mb.go();

			return;
		} catch (DelayAbortedException e) {
			return;
		}

		new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a non-interleaved Nexus file.").go();
	}

			/**
	 * Quick-export as a plain Nexus file.
	 */
	public void quickExportAsNakedNexus() {
		if (!checkCancelledBeforeExport())
			return;

		File file = getFile("Export as naked Nexus ...");
		if (file == null)
			return;

		try {
			matrix.getExporter().exportAsNexus(
					file,
					Preferences.PREF_NEXUS_SINGLE_LINE | Preferences.PREF_NEXUS_NAKED_FORMAT,
					100,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting sequences ...",
					"All your sequences are being exported as a naked Nexus file into '" + file + "'. Sorry for the wait!"));
		} catch (IOException e) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error writing sequences to file!",
					"The following error occured while writing sequences to file: " + e);
			mb.go();

			return;
		} catch (DelayAbortedException e) {
			return;
		}

		new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a naked Nexus file.").go();
	}

	/**
	 * 	Export the current set as a TNT file. Be warned that File 'f' will be
	 * 	completely overwritten.
	 *
	 * 	Right now we're outsourcing this to the SequenceGrid; we might bring it in here in the
	 * 	future.
	 */
	public void exportAsTNT() {
		if (!checkCancelledBeforeExport()) {
			return;

			
		}
		File f = getFile("Export as TNT ...");
		if (f == null) {
			return;

			
		}
		try {
			matrix.getExporter().exportAsTNT(f,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting dataset ...",
					"The dataset is being exported. Sorry for the delay."));

			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Success!",
					"This set was successfully exported to '" + f + "' in the TNT format.");
			mb.go();

		} catch (IOException e) {
			reportIOException(e, f, IOE_WRITING);
		} catch (DelayAbortedException e) {
			reportDelayAbortedException(e, "Export of TNT file");
		}
	}

	/**
	 * 	Export the current set as a TNT file. Be warned that File 'f' will be
	 * 	completely overwritten.
	 *
	 * 	Right now we're outsourcing this to the SequenceGrid; we might bring it in here in the
	 * 	future.
	 */
	public void exportAsSequences() {
		//if(!checkCancelledBeforeExport())
		//	return;
		//
		// we don't need to check, since #sequences handles cancelled sequences fine.

		File f = getFile("Export as Sequences ...");
		if (f == null) {
			return;

			
		}
		try {
			matrix.getExporter().exportAsSequences(f,
					ProgressDialog.create(
					matrix.getFrame(),
					"Please wait, exporting dataset ...",
					"The dataset is being exported. Sorry for the delay."));

			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Success!",
					"This set was successfully exported to '" + f + "' in the Sequences format.");
			mb.go();

		} catch (IOException e) {
			reportIOException(e, f, IOE_WRITING);
		} catch (DelayAbortedException e) {
			reportDelayAbortedException(e, "Export of Sequence file");
		}
	}

	/**
	 * Export the table itself as a tab delimited file.
	 */
	public void exportTableAsTabDelimited() {
		// export the table to the file, etc.
		File file = getFile("Where would you like to export this table to?");
		if (file == null) {
			return;

			
		}
		try {
			matrix.getExporter().exportTableAsTabDelimited(file);
		} catch (IOException e) {
			reportIOException(e, file, IOE_WRITING);
		}
	}

	/**
	 * Export all the sequences, randomly picking columns in groups of X, into a single directory.
	 * Fun for all, and all for fun.
	 */
	public void exportSequencesByColumnsInGroups() {
		// first, we need to figure out which format the person'd like to use:
		Dialog dg = (Dialog) CloseableWindow.wrap(new Dialog(matrix.getFrame(), "Please choose your output options ...", true));
		RightLayout rl = new RightLayout(dg);
		dg.setLayout(rl);

		rl.add(new Label("Write all the files into:"), RightLayout.NONE);
		DirectoryInputPanel dinp = new DirectoryInputPanel(
				null,
				DirectoryInputPanel.MODE_DIR_SELECT,
				dg);
		rl.add(dinp, RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Label("Write files with format:"), RightLayout.NEXTLINE);

		Choice choice_formats = new Choice();
		Vector fhs = SequenceList.getFormatHandlers();
		Iterator i = fhs.iterator();
		while (i.hasNext()) {
			FormatHandler fh = (FormatHandler) i.next();
			choice_formats.add(fh.getFullName());
		}
		rl.add(choice_formats, RightLayout.BESIDE | RightLayout.STRETCH_X);

		Checkbox check_writeNASequences = new Checkbox("Write 'N/A' sequences into the files as well");
		rl.add(check_writeNASequences, RightLayout.NEXTLINE | RightLayout.FILL_2);

		// additional 'random' options
		// TODO: Put in some sort of element grouping; this will presumably
		// have to be implemented by whatisface RightLayout.

		rl.add(new Label("Number of columns in each group: "), RightLayout.NEXTLINE);
		Choice choice_per_group = new Choice();
		int colCount = matrix.getTableManager().getCharsets().size();
		for (int x = 1; x <= colCount; x++) {
			choice_per_group.add("" + x);
		}

		if (colCount == 0) {
			choice_per_group.add("No columns loaded!");

			
		}
		rl.add(choice_per_group, RightLayout.BESIDE);

		rl.add(new Label("Number of randomizations to perform: "), RightLayout.NEXTLINE);
		TextField tf_rands = new TextField();
		rl.add(tf_rands, RightLayout.BESIDE);

		rl.add(new Label("Number of taxa to remove from each group: "), RightLayout.NEXTLINE);
		Choice choice_random_taxa = new Choice();
		int taxaCount = matrix.getTableManager().getSequenceNames().size();	// this sort of statement should not be allowed
		for (int x = 1; x <= taxaCount; x++) {
			choice_random_taxa.add("" + x);


		}
		if (taxaCount == 0) {
			choice_random_taxa.add("No taxons present!");

			
		}
		rl.add(choice_random_taxa, RightLayout.BESIDE);

		rl.add(new Label("Taxon to never delete: "), RightLayout.NEXTLINE);
		Choice choice_ref_taxon = new Choice();
		choice_ref_taxon.add("None");
		java.util.List list_seqNames = matrix.getTableManager().getSequenceNames();
		i = list_seqNames.iterator();
		while (i.hasNext()) {
			String seqName = (String) i.next();
			choice_ref_taxon.add(seqName);
		}
		rl.add(choice_ref_taxon, RightLayout.BESIDE);

		// Okay, done, back to your regularly scheduled programming

		DefaultButton btn = new DefaultButton(dg, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_formats.select(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choice", 2, 0, choice_formats.getItemCount() - 1)); // 2 == NexusFile, at some point of time
		dinp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_fileName", "")));
		if (matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_writeNASequences", 0, 0, 1) == 0) {
			check_writeNASequences.setState(false);
			
		} else {
			check_writeNASequences.setState(true);
			
		}
		tf_rands.setText(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_noOfRands", "10"));
		choice_per_group.select("" + (matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choicePerGroup", 0, 0, choice_per_group.getItemCount() - 1)));
		choice_random_taxa.select("" + (matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choiceRandomTaxa", 0, 0, choice_random_taxa.getItemCount() - 1)));
		int index_sel = list_seqNames.indexOf(
				matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choiceRefTaxon", "None"));

		if (index_sel > 0) {
			choice_ref_taxon.select(index_sel + 1);

			
		}
		dg.pack();
		dg.setVisible(true);

		if(dinp.getFile() == null)
			return;

		if (btn.wasClicked()) {
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choice", choice_formats.getSelectedIndex());
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_fileName", dinp.getFile().getParent());
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_writeNASequences", check_writeNASequences.getState() ? 1 : 0);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choicePerGroup", choice_per_group.getSelectedIndex() + 1);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choiceRandomTaxa", choice_random_taxa.getSelectedIndex() + 1);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choiceRefTaxon", choice_ref_taxon.getSelectedItem());

			int rands = 0;
			try {
				rands = Integer.parseInt(tf_rands.getText());
			} catch (NumberFormatException e) {
				rands = 10;
			}

			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_noOfRands", String.valueOf(rands));

			String ref_taxon = choice_ref_taxon.getSelectedItem();
			if (ref_taxon.equals("None")) {
				ref_taxon = null;

				// phew ... go!
				
			}
			try {
				matrix.getExporter().exportColumnsInGroups(
						rands,
						choice_per_group.getSelectedIndex() + 1,
						choice_random_taxa.getSelectedIndex() + 1,
						dinp.getFile(),
						ref_taxon,
						(FormatHandler) fhs.get(choice_formats.getSelectedIndex()),
						check_writeNASequences.getState(),
						ProgressDialog.create(
						matrix.getFrame(),
						"Please wait, exporting sequences ...",
						"All your sequences are being exported as individual files into '" + dinp.getFile() + "'. Please wait!"));
			} catch (IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e);
				mb.go();

				return;
			} catch (DelayAbortedException e) {
				return;
			}

			new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into individual files in '" + dinp.getFile() + "'.").go();
			// TODO: Would be nice if we have an option to open this Window using Explorer/Finder
		}
	}

	/**
	 * Export all the sequences by column: one file per column.
	 */
	public void exportSequencesByColumn() {
		// first, we need to figure out which format the person'd like to use:
		Dialog dg = (Dialog) CloseableWindow.wrap(new Dialog(matrix.getFrame(), "Please choose your output options ...", true));
		RightLayout rl = new RightLayout(dg);
		dg.setLayout(rl);

		rl.add(new Label("Write all the files into:"), RightLayout.NONE);
		DirectoryInputPanel dinp = new DirectoryInputPanel(
				null,
				DirectoryInputPanel.MODE_DIR_SELECT,
				dg);
		rl.add(dinp, RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Label("Write files with format:"), RightLayout.NEXTLINE);

		Choice choice_formats = new Choice();
		Vector fhs = SequenceList.getFormatHandlers();
		Iterator i = fhs.iterator();
		while (i.hasNext()) {
			FormatHandler fh = (FormatHandler) i.next();
			choice_formats.add(fh.getFullName());
		}
		rl.add(choice_formats, RightLayout.BESIDE | RightLayout.STRETCH_X);

		Checkbox check_writeNASequences = new Checkbox("Write 'N/A' sequences into the files as well");
		rl.add(check_writeNASequences, RightLayout.NEXTLINE | RightLayout.FILL_2);

		DefaultButton btn = new DefaultButton(dg, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_formats.select(matrix.getPrefs().getPreference("exportSequencesByColumn_choice", 0, 0, choice_formats.getItemCount() - 1));
		dinp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumn_fileName", "")));
		if (matrix.getPrefs().getPreference("exportSequencesByColumn_writeNASequences", 0, 0, 1) == 0) {
			check_writeNASequences.setState(false);
			
		} else {
			check_writeNASequences.setState(true);

			
		}
		dg.pack();
		dg.setVisible(true);

		// Can't do anything if no directory was selected.
		if (btn.wasClicked()) {
			if(dinp.getFile() == null) {
				MessageBox.messageBox(
					matrix.getFrame(),
					"No file selected",
					"No file was selected for this export. This is probably an " +
					"internal problem: in particular, SequenceMatrix has problems " +
					"writing to the root folder on Windows drives. Please choose " +
					"another folder, or report this bug to us."
				);
				return;
			}

			matrix.getPrefs().setPreference("exportSequencesByColumn_choice", choice_formats.getSelectedIndex());
			matrix.getPrefs().setPreference("exportSequencesByColumn_fileName", dinp.getFile().toString());
			matrix.getPrefs().setPreference("exportSequencesByColumn_writeNASequences", check_writeNASequences.getState() ? 1 : 0);

			// phew ... go!
			try {
				matrix.getExporter().exportSequencesByColumn(
						dinp.getFile(),
						(FormatHandler) fhs.get(choice_formats.getSelectedIndex()),
						check_writeNASequences.getState(),
						ProgressDialog.create(
						matrix.getFrame(),
						"Please wait, exporting sequences ...",
						"All your sequences are being exported as individual files into '" + dinp.getFile() + "'. Please wait!"));
			} catch (IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e);
				mb.go();

				return;
			} catch (DelayAbortedException e) {
				return;
			}

			new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into individual files in '" + dinp.getFile() + "'.").go();
			// TODO: Would be nice if we have an option to open this Window using Explorer/Finder
		}
	}

	/**
	 * A format Listener for listening in on character sets. Every time the FormatHandler
	 * sees a characterset it calls this method, which then stores the characterset into
	 * hashmap_codonsets. Once the file has finished loading, incorporateSets() will use
	 * this data to subdivide the input file.
	 */
	public boolean eventOccured(FormatHandlerEvent evt) throws FormatException {
		switch (evt.getId()) {

			// What happened?
			case FormatHandlerEvent.CHARACTER_SET_FOUND:
				String name = evt.name;
				int from = evt.from;
				int to = evt.to;
				FromToPair ftp = new FromToPair(from, to);

				synchronized (hashmap_codonsets) {
					// Make sure characters aren't double-counted. This is a
					// somewhat complex set of rules:
					//	1.	CodonPosSets can overlap with all other datasets
					//		*except* other CPSs.
					//	2.	Other characters cannot overlap with any other
					//		non-CPS datasets.
					//	3.	'N' can overlap with anything (TODO: make this compare).
					if(name.startsWith(":") && !name.equals(":0")) {
						// CodonPosSet! Compare against all other codonpossets.

						for(String compare_to: hashmap_codonsets.keySet()) {
							// Only compare against other codonpossets.
							if(!compare_to.startsWith(":") || compare_to.equals(":0"))
								continue;

							ArrayList<FromToPair> compare_list =
									hashmap_codonsets.get(compare_to);
							for(FromToPair compare_ftp: compare_list) {
								//System.err.println("Comparing position " + name + ":" + ftp + " with position " + compare_to + ":" + compare_ftp + " -> " + ftp.overlapsMovesInThrees(compare_ftp));
								if(compare_ftp.overlapsMovesInThrees(ftp)) {
									String pos = name.substring(1);				// Eliminate the leading ':'.
									String compare_pos = compare_to.substring(1);		// Eliminate the leading ':'.

									throw new FormatException(
										"Sequence Matrix cannot handle overlapping character sets, including " +
										"in codon positional data. In this file, " +
										"position #" + pos + " (" + ftp + ") overlaps with " +
										"position #" + compare_pos + " (" + compare_ftp + ").\n\n" +
										"Please rectify this in the input file, or report a bug if " +
										"there is no overlap between these two codon positions."
									);
								}
							}
						}

					} else {
						// Not a codonposset. Compare against all other
						// non-codonpossets.
						for(String compare_to: hashmap_codonsets.keySet()) {
							// Don't compare against codonpossets.
							if(compare_to.startsWith(":"))
								continue;

							ArrayList<FromToPair> compare_list =
									hashmap_codonsets.get(compare_to);
							for(FromToPair compare_ftp: compare_list) {
								//System.err.println("Comparing " + name + ":" + ftp + " with " + compare_to + ":" + compare_ftp + " -> " + ftp.overlaps(compare_ftp));
								if(compare_ftp.overlaps(ftp)) {
									throw new FormatException(
										"Sequence Matrix cannot handle overlapping datasets. In this file, " +
										"character set " + name + " (" + ftp + ") overlaps with " +
										"character set " + compare_to + " (" + compare_ftp + ").\n\n" +
										"Please rectify this in the input file, or report a bug if " +
										"there is no overlap between these character sets."
									);
								}
							}
						}
					}

					// If there's an ArrayList already in the dataset under this name,
					// add to it. If there isn't, make it.

					// This is all very Perlish.

					if (hashmap_codonsets.get(name) != null) {
						ArrayList<FromToPair> al = hashmap_codonsets.get(name);
						al.add(ftp);
					} else {
						ArrayList<FromToPair> al = new ArrayList<FromToPair>();
						al.add(ftp);
						hashmap_codonsets.put(name, al);
					}
				}

				// consumed, but we don't mind if anybody else knows
				return false;
		}

		// not consumed
		return false;
	}

	/**
	 * Check to see whether any cancelled sequences exist; any data in such sequences
	 * will be completely LOST on export.
	 */
	public boolean checkCancelledBeforeExport() {
		int cancelled = matrix.getTableManager().countCancelledSequences();

		if (cancelled == 0) {
			return true;

			
		}
		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Warning: Cancelled sequences aren't exported!",
				"You have " + cancelled + " cancelled sequence(s) in your dataset. These sequences will not be exported! Are you sure you are okay with losing the data in these sequences?",
				MessageBox.MB_YESNO | MessageBox.MB_TITLE_IS_UNIQUE);
		if (mb.showMessageBox() == MessageBox.MB_YES) {
			return true;
			
		} else {
			return false;
			
		}
	}
}
