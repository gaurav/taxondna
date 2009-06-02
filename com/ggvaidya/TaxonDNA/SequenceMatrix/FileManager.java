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

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for drag-n-drop
import java.awt.dnd.*;		// drag-n-drop
import java.io.*;
import java.util.*;

import javax.swing.*;
import javax.swing.table.*;	// for TableModel, which we use to slurp the info straight out the table

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class FileManager implements FormatListener {
	private SequenceMatrix		matrix;
	private Vector 		filesToLoad = new Vector();
	private Hashtable 		hash_sets = new Hashtable();

	// Should we use the full name or the species name?
	//
	public static final int		PREF_NOT_SET_YET	=	-1;	
	public static final int		PREF_USE_FULL_NAME	=	0;	
	public static final int		PREF_USE_SPECIES_NAME	=	1;
	private static int		pref_useWhichName 	=	PREF_NOT_SET_YET;

//
//	0.	OUR CLASSES, WHO ART IN CORE
//
	private class FromToPair implements Comparable {
		public int from;
		public int to;

		public FromToPair(int from, int to) {
			this.from = from;
			this.to = to;
		}

		public int compareTo(Object o) {
			FromToPair ftp = (FromToPair) o;

			return (this.from - ftp.from);
		}
	}

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
	 * Get a File from the user
	 */
	private File getFile(String title, int flag) {
		File file = null;

		FileDialog fd = new FileDialog(matrix.getFrame(), title, FileDialog.SAVE);

		fd.setVisible(true);

		if(fd.getFile() != null) {
			if(fd.getDirectory() != null) {
				file = new File(fd.getDirectory() + fd.getFile());
			} else {
				file = new File(fd.getFile());
			}
		}

		return file;
	}

	/**
	 * Reports an IOException to the user
	 */
	private static final int IOE_READING = 		0;
	private static final int IOE_WRITING = 		1;
	private void reportIOException(IOException e, File f, int type) {
		String verb = "";

		if(type == IOE_READING)
			verb = "reading";
		else
			verb = "writing";

		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Error while " + verb + " file",
				"There was an error while " + verb + " this set to '" + f + "'. Please ensure that you have adequate permissions and that your hard disk is not full.\n\nThe technical description of this error is: " + e);
		mb.go();
	}

	/**
	 * Reports an DelayAbortedException to the user
	 */
	private void reportDelayAbortedException(DelayAbortedException e, String task) {
		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				task + " cancelled",
				task + " was cancelled midway. The task might be incomplete, and any files being generated might be incomplete.");
		mb.go();
	}
	

	/**
	 * Adds the file to this dataset, using the specified handler.
	 * How this works is a little non-logical: addFile() immediately
	 * queues up the file for addition, spawns a thread
	 * to handle the actual file loading, and returns immediately.
	 *
	 * The upshot of this is that addFile() is non-blocking:
	 * you are NOT guaranteed that the file will be loaded
	 * once addFile() returns.
	 *
	 * We will lock the interface, and only unlock it once the
	 * files are loaded. This means that if you're waiting for
	 * input (i.e. the user clicking on a menu option), you're
	 * okay. But do NOT begin calculations immediately after
	 * you've called addFile().
	 *
	 * This is a convenience function, since there are a lot
	 * of functions (particularly the drag-drop system)
	 * which needs asynchronous addition. They are also slightly
	 * faster, since the difference SequenceLists can be
	 * created independently.
	 *
	 * If you really need a synchronous add(), let me know
	 * and I'll write one.
	 *
	 */
	public void addFile(File file, FormatHandler handler) {
		synchronized(filesToLoad) {
			//filesToLoad.add(file);
			//filesToLoad.add(handler);	// primitive two-variable list

			//Thread t = new Thread(this);
			//t.start();
			try {
				addNextFile(file, handler);
			} catch(DelayAbortedException e) {
				return;
			}
		}
	}

	/** Returns either PREF_USE_FULL_NAME or PREF_USE_SPECIES_NAME */
	public void checkNameToUse(String str_sequence_name, String str_species_name) {
		if(str_sequence_name == null)	str_sequence_name = "(No sequence name provided)";
		if(str_species_name == null)	str_species_name =  "(No species name provided)";
		
		if(pref_useWhichName == PREF_NOT_SET_YET) {
			Dialog dg = new Dialog(
					matrix.getFrame(),
					"Species names or sequence names?",
					true);	// modal!
			dg = (Dialog) CloseableWindow.wrap(dg);	// wrap it as a Closeable window

			dg.setLayout(new BorderLayout());

			TextArea ta = new TextArea("", 9, 60, TextArea.SCROLLBARS_VERTICAL_ONLY);
			ta.setEditable(false);
			ta.setText("Would you like to use the full sequence name? Sequence names from this file look like this:\n\t" + str_sequence_name + "\n\nI can also try to guess the species name, which looks like this:\n\t" + str_species_name +"\n\nNote that the species name might not be guessable for every sequence in this dataset.");
			dg.add(ta);

			Panel buttons = new Panel();
			buttons.setLayout(new FlowLayout(FlowLayout.CENTER));

			DefaultButton btn_seq = new DefaultButton(dg, "Use sequence names");
			buttons.add(btn_seq);

			// err ... wtf?
			DefaultButton btn_species = new DefaultButton(dg, "Use species names");
			buttons.add(btn_species);
			dg.add(buttons, BorderLayout.SOUTH);

			dg.pack();
			dg.setVisible(true);
		
			if(btn_seq.wasClicked())
				pref_useWhichName = PREF_USE_FULL_NAME;
			else
				pref_useWhichName = PREF_USE_SPECIES_NAME;
		}
	}	

	/**
	 * Checks whether we:
	 *	(1) leave the gaps as is
	 *	(2) convert the external gaps to '?'s
	 */
	private void checkGappingSituation(String colName, SequenceList sl) {
		MessageBox mb = new MessageBox(
			matrix.getFrame(),
			"Replace external gaps with missing characters during import?",
			"Would you like to recode external gaps as question marks?",
			MessageBox.MB_YESNOTOALL | MessageBox.MB_TITLE_IS_UNIQUE
		);
		if(mb.showMessageBox() == MessageBox.MB_YES) {
			// yes! do it!
			Iterator i = sl.iterator();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				seq.convertExternalGapsToMissingChars();
			}
		}
	}

	/**
	 * Sets up names to use by filling in INITIAL_SEQ_NAME in the
	 * Sequence preferences, in-place. You ought to call this on 
	 * a SequenceList before you send it in. Note that this function
	 * works IN PLACE, but won't change names or anything - only
	 * remove the old INITIAL_SEQ_NAME, and replace it with what
	 * we think is the right initial seq name NOW. 
	 */
	private void setupNamesToUse(SequenceList list) {
	    if (list.count() == 0) return;     // Don't handle empty sequence lists
	    
	    String str_sequence_name = "(No sequence name in this set contains a sequence name)";
	    String str_species_name = "(No sequence name in this set contains a species name)";

	    Iterator i_find_example = list.iterator();
	    while (i_find_example.hasNext()) {
		Sequence seq = (Sequence) i_find_example.next();
		if (
			seq.getFullName() != null &&
			!seq.getFullName().equals("")
                ) {
		    // we have a full name
		    str_sequence_name = seq.getFullName();
		    
		    if(
			seq.getSpeciesName() != null &&
                        !seq.getSpeciesName().equals("")
		    )
		    {
			// we've got both!
			str_species_name = seq.getSpeciesName();
		    }
		}
	    }

            if(pref_useWhichName == PREF_NOT_SET_YET)
			checkNameToUse(
                                    str_sequence_name,
				    str_species_name
                                );			

		Iterator i = list.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();
			String seqName = seq.getFullName();

			if(seq.getProperty(DataStore.INITIAL_SEQNAME_PROPERTY) != null)
				continue;

			if(pref_useWhichName == PREF_USE_SPECIES_NAME)
				seqName = seq.getSpeciesName();

			seq.setProperty(DataStore.INITIAL_SEQNAME_PROPERTY, seqName);
		}
	}

	/**
	 * Loads the file and handler. This is *very* synchronous, so please
	 * call only from the Thread.
	 */
	private void addNextFile(File file, FormatHandler handler) throws DelayAbortedException {
		SequenceList sequences = null;
		boolean sets_were_added = false;

		try {
			if(handler != null) {
				handler.addFormatListener(this);

				sequences = new SequenceList(
					file,	
					handler,
					new ProgressDialog(
						matrix.getFrame(), 
						"Loading file ...", 
						"Loading '" + file + "' (of format " + handler.getShortName() + " into memory. Sorry for the delay!",
						ProgressDialog.FLAG_NOCANCEL
						)
				);
			} else {
				sequences = SequenceList.readFile(
					file, 
					new ProgressDialog(
						matrix.getFrame(), 
						"Loading file ...", 
						"Loading '" + file + "' into memory. Sorry for the delay!",
						ProgressDialog.FLAG_NOCANCEL
						),
					this		// use us as the format listener
					);
			}

		} catch(SequenceListException e) {
			MessageBox mb = new MessageBox(matrix.getFrame(), "Could not read file!", "I could not understand a sequence in this file. Please fix any errors in the file.\n\nThe technical description of the error is: " + e);
			mb.go();

			return;
		} catch(DelayAbortedException e) {
			// pass it on, no returns
			throw e;
		}

		// now, we're almost done with this file ... bbbut ... before we do
		// do we have sets?
		synchronized(hash_sets) {
			if(hash_sets.size() > 0) {
				// we do!
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"I see sets!",
						"The file " + file + " contains character sets. Do you want me to split the file into character sets?",
						MessageBox.MB_YESNOTOALL | MessageBox.MB_TITLE_IS_UNIQUE);

				if(mb.showMessageBox() == MessageBox.MB_YES) {
//					pd.begin();

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
					sequences.lock();

					Iterator i_sets = hash_sets.keySet().iterator();
					int set_count = 0;
					while(i_sets.hasNext()) {
						//pd.delay(set_count, hash_sets.size());
						set_count++;
						
						String name = (String) i_sets.next();
						Vector v = (Vector) hash_sets.get(name);

						ProgressDialog pd = new ProgressDialog(
							matrix.getFrame(),
							"Please wait, separating out set '" + name + "' ...",
							"Please wait while I separate out '" + name + "'. Sorry for the wait!");
						pd.begin();		// and the addSequenceList(..., pd) call will
									// eventually call pd.end(). It's hacky, I know!
									// I'm sorry, Grandpa!


						Collections.sort(v);	// we sort the fromToPairs so that they are in left-to-right order.

						SequenceList sl = new SequenceList();

						Iterator i_seq = sequences.iterator();
						while(i_seq.hasNext()) {
							Sequence seq = (Sequence) i_seq.next();
							Sequence seq_out = new Sequence();
							seq_out.changeName(seq.getFullName());

							Iterator i_coords = v.iterator();
							while(i_coords.hasNext()) {
								FromToPair ftp = (FromToPair)(i_coords.next());
								int from = ftp.from; 
								int to = ftp.to;

								try {
									System.err.println("Cutting [" + name + "] " + seq.getFullName() + " from " + from + " to " + to + ": " + seq.getSubsequence(from, to) + ";");										
									Sequence s = BaseSequence.promoteSequence(seq.getSubsequence(from, to));
									seq_out = seq_out.concatSequence(s);
								} catch(SequenceException e) {
									pd.end();

									MessageBox mb_2 = new MessageBox(
											matrix.getFrame(),
											"Uh-oh: Error forming a set",
											"According to this file, character set " + name + " extends from " + from + " to " + to + ". While processing sequence '" + seq.getFullName() + "', I got the following problem:\n\t" + e.getMessage() + "\nI'm skipping this file.");
									mb_2.go();
									sequences.unlock();
									hash_sets.clear();
									sets_were_added = true;

									// we're done here. I honestly don't remember why.
									// but I have FAITH, and that is what matters.
									return;
								}
							}

							// WARNING: note that this will eliminate any deliberately gapped regions!
							// (which is, I guess, okay)
							//System.err.println("Final sequence: " + seq_out + ", " + seq_out.getActualLength());
							if(seq_out.getActualLength() > 0)
								sl.add(seq_out);
						}
						pd.end();

						setupNamesToUse(sl);
						checkGappingSituation(name, sl);

						StringBuffer buff_complaints = new StringBuffer();
						matrix.getTableManager().addSequenceList(name, sl, buff_complaints, pd);
						if(buff_complaints.length() > 0) {
							new MessageBox(	matrix.getFrame(),
									name + ": Some sequences weren't added!",
									"Some sequences in the taxonset " + name + " weren't added. These are:\n" + buff_complaints.toString()
							).go();
						}
					}

					sequences.unlock();

					hash_sets.clear();
					sets_were_added = true;
				}
			}
		}

		// if we're here, we've only got one 'sequences'.
		// so add it!
		if(!sets_were_added) {
			setupNamesToUse(sequences);
			checkGappingSituation(file.toString(), sequences);

			StringBuffer buff_complaints = new StringBuffer();
			matrix.getTableManager().addSequenceList(sequences,
					buff_complaints,
					new ProgressDialog(
						matrix.getFrame(),
						"Please wait, adding sequences ...",
						"The new sequences are being added to the table now. Sorry for the delay!"
					)
				);

			if(buff_complaints.length() > 0) {
				new MessageBox(	
					matrix.getFrame(),
					file + ": Some sequences weren't added!",
					"Some sequences in the file " + file + " weren't added. These are:\n" + buff_complaints.toString()
				).go();
			}
		}
	}

	/**
	 * The Thread responsible for the asynchronous addFile().
	 
	public void run() {
		Thread.yield();		// wait until we've got nothing better to do ... i think =/
		
		synchronized(filesToLoad) {
			Iterator i = filesToLoad.iterator();
			while(i.hasNext()) {
				File file = null;
				FormatHandler handler = null;

				file = (File) i.next();
				i.remove();
				handler = (FormatHandler) i.next();
				i.remove();

				try {
					addNextFile(file, handler);
				} catch(DelayAbortedException e) {
					// stop right here.
					return;
				}
			}
		}
	}
	*/

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

		if(fd.getFile() != null) {
			if(fd.getDirectory() != null) {
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
		if(!checkCancelledBeforeExport())
			return;

		Dialog frame = (Dialog) CloseableWindow.wrap(new Dialog(matrix.getFrame(), "Export as Nexus ...", true));
		RightLayout rl = new RightLayout(frame);
		frame.setLayout(rl);

		rl.add(new Label("File to save to:"), RightLayout.NONE);
		FileInputPanel finp = new FileInputPanel(
					null, 
					FileInputPanel.MODE_FILE_WRITE,	
					frame
				);
		rl.add(finp, RightLayout.BESIDE | RightLayout.STRETCH_X);

		rl.add(new Label("Export as:"), RightLayout.NEXTLINE);
		Choice choice_exportAs = new Choice();
		choice_exportAs.add("Interleaved");
		choice_exportAs.add("Non-interleaved");
		rl.add(choice_exportAs, RightLayout.BESIDE | RightLayout.STRETCH_X);

		rl.add(new Label("Interleave at:"), RightLayout.NEXTLINE);
		TextField tf_interleaveAt = new TextField();
		rl.add(tf_interleaveAt, RightLayout.BESIDE | RightLayout.STRETCH_X);

		DefaultButton btn = new DefaultButton(frame, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_exportAs.select(matrix.getPrefs().getPreference("exportAsNexus_exportAs", 0));
		tf_interleaveAt.setText(new Integer(matrix.getPrefs().getPreference("exportAsNexus_interleaveAt", 1000)).toString());
		finp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumn_fileName", "")));

		frame.pack();
		frame.setVisible(true);

		File file = null;
		if(btn.wasClicked() && ((file = finp.getFile()) != null)) {
			matrix.getPrefs().setPreference("exportAsNexus_exportAs", choice_exportAs.getSelectedIndex());

			int exportAs = Preferences.PREF_NEXUS_INTERLEAVED;
			switch(choice_exportAs.getSelectedIndex()) {
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
			} catch(NumberFormatException e) {
				if(new MessageBox(matrix.getFrame(), 
						"Warning: Couldn't interpret 'Interleave At'",
						"I can't figure out which number you mean by '" + tf_interleaveAt.getText() + "'. I'm going to use '1000' as a default length. Is that alright with you?",
						MessageBox.MB_YESNO).showMessageBox() == MessageBox.MB_NO)
					return;
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
						new ProgressDialog(
							matrix.getFrame(),
							"Please wait, exporting sequences ...",
							"All your sequences are being exported as a single Nexus file into '" + file + "'. Sorry for the wait!")
				);
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e
						);
				mb.go();

				return;
			} catch(DelayAbortedException e) {
				return;
			}

			new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into '" + file + "' as a Nexus file.").go();
		}
	}

	/**
	 * 	Export the current set as a TNT file. Be warned that File 'f' will be
	 * 	completely overwritten.
	 *
	 * 	Right now we're outsourcing this to the SequenceGrid; we might bring it in here in the
	 * 	future.
	 */
	public void exportAsTNT() {
		if(!checkCancelledBeforeExport())
			return;

		File f = getFile("Where would you like to export this set to?", FileDialog.SAVE);
		if(f == null)
			return;

		try {
			matrix.getExporter().exportAsTNT(f, 
					new ProgressDialog(
						matrix.getFrame(), 
						"Please wait, exporting dataset ...",
						"The dataset is being exported. Sorry for the delay."					
					)
			);

			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Success!",
					"This set was successfully exported to '" + f + "' in the TNT format."
					);
			mb.go();

		} catch(IOException e) {
			reportIOException(e, f, IOE_WRITING);
		} catch(DelayAbortedException e) {
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

		File f = getFile("Where would you like to export this set to?", FileDialog.SAVE);
		if(f == null)
			return;

		try {
			matrix.getExporter().exportAsSequences(f, 
					new ProgressDialog(
						matrix.getFrame(), 
						"Please wait, exporting dataset ...",
						"The dataset is being exported. Sorry for the delay."					
					)
			);

			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Success!",
					"This set was successfully exported to '" + f + "' in the Sequences format."
					);
			mb.go();

		} catch(IOException e) {
			reportIOException(e, f, IOE_WRITING);
		} catch(DelayAbortedException e) {
			reportDelayAbortedException(e, "Export of Sequence file");
		}
	}

	/**
	 * Export the table itself as a tab delimited file.
	 */
	public void exportTableAsTabDelimited() {
		// export the table to the file, etc.
		File file = getFile("Where would you like to export this table to?", FileDialog.SAVE);
		if(file == null)
			return;

		try {
			matrix.getExporter().exportTableAsTabDelimited(file);
		} catch(IOException e) {
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
					dg
				);
		rl.add(dinp, RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Label("Write files with format:"), RightLayout.NEXTLINE);

		Choice choice_formats = new Choice();
		Vector fhs = SequenceList.getFormatHandlers();
		Iterator i = fhs.iterator();
		while(i.hasNext()) {
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
		for(int x = 1; x <= colCount; x++) {
			choice_per_group.add("" + x);
		}

		if(colCount == 0)
			choice_per_group.add("No columns loaded!");

		rl.add(choice_per_group, RightLayout.BESIDE);

		rl.add(new Label("Number of randomizations to perform: "), RightLayout.NEXTLINE);
		TextField tf_rands = new TextField();
		rl.add(tf_rands, RightLayout.BESIDE);

		rl.add(new Label("Number of taxa to remove from each group: "), RightLayout.NEXTLINE);
		Choice choice_random_taxa = new Choice();	
		int taxaCount = matrix.getTableManager().getSequenceNames().size();	// this sort of statement should not be allowed
		for(int x = 1; x <= taxaCount; x++)
			choice_random_taxa.add("" + x);

		if(taxaCount == 0)
			choice_random_taxa.add("No taxons present!");

		rl.add(choice_random_taxa, RightLayout.BESIDE);

		rl.add(new Label("Taxon to never delete: "), RightLayout.NEXTLINE);
		Choice choice_ref_taxon = new Choice();	
		choice_ref_taxon.add("None");
		java.util.List list_seqNames = matrix.getTableManager().getSequenceNames();
		i = list_seqNames.iterator();	
		while(i.hasNext()) {
			String seqName = (String) i.next();
			choice_ref_taxon.add(seqName);
		}
		rl.add(choice_ref_taxon, RightLayout.BESIDE);
		
		// Okay, done, back to your regularly scheduled programming
		
		DefaultButton btn = new DefaultButton(dg, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_formats.select(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choice", 2)); // 2 == NexusFile, at some point of time
		dinp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_fileName", "")));
		if(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_writeNASequences", 0) == 0)
			check_writeNASequences.setState(false);
		else 
			check_writeNASequences.setState(true);
		tf_rands.setText(matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_noOfRands", "10"));
		choice_per_group.select("" + (matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choicePerGroup", 0)));
		choice_random_taxa.select("" + (matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choiceRandomTaxa", 0)));
		int index_sel = list_seqNames.indexOf(	
					matrix.getPrefs().getPreference("exportSequencesByColumnsInGroups_choiceRefTaxon", "None")
				);
				
		if(index_sel > 0)
			choice_ref_taxon.select(index_sel + 1);

		dg.pack();
		dg.setVisible(true);

		if(btn.wasClicked()) {
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choice", choice_formats.getSelectedIndex());
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_fileName", dinp.getFile().getParent());
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_writeNASequences", check_writeNASequences.getState() ? 1 : 0);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choicePerGroup", choice_per_group.getSelectedIndex() + 1);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choiceRandomTaxa", choice_random_taxa.getSelectedIndex() + 1);
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_choiceRefTaxon", choice_ref_taxon.getSelectedItem());

			int rands = 0;
			try {
				rands = Integer.parseInt(tf_rands.getText());
			} catch(NumberFormatException e) {
				rands = 10;	
			}
			
			matrix.getPrefs().setPreference("exportSequencesByColumnsInGroups_noOfRands", String.valueOf(rands));

			String ref_taxon = choice_ref_taxon.getSelectedItem();
			if(ref_taxon.equals("None"))
				ref_taxon = null;

			// phew ... go!
			try {
				matrix.getExporter().exportColumnsInGroups(
					rands,
					choice_per_group.getSelectedIndex() + 1,
					choice_random_taxa.getSelectedIndex() + 1,
					dinp.getFile(), 
					ref_taxon,	
					(FormatHandler) fhs.get(choice_formats.getSelectedIndex()), 
					check_writeNASequences.getState(),
					new ProgressDialog(
						matrix.getFrame(),
						"Please wait, exporting sequences ...",
						"All your sequences are being exported as individual files into '" + dinp.getFile() + "'. Please wait!")
				);
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e
						);
				mb.go();

				return;
			} catch(DelayAbortedException e) {
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
					dg
				);
		rl.add(dinp, RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Label("Write files with format:"), RightLayout.NEXTLINE);

		Choice choice_formats = new Choice();
		Vector fhs = SequenceList.getFormatHandlers();
		Iterator i = fhs.iterator();
		while(i.hasNext()) {
			FormatHandler fh = (FormatHandler) i.next();
			choice_formats.add(fh.getFullName());
		}
		rl.add(choice_formats, RightLayout.BESIDE | RightLayout.STRETCH_X);

		Checkbox check_writeNASequences = new Checkbox("Write 'N/A' sequences into the files as well");
		rl.add(check_writeNASequences, RightLayout.NEXTLINE | RightLayout.FILL_2);

		DefaultButton btn = new DefaultButton(dg, "Write files");
		rl.add(btn, RightLayout.NEXTLINE | RightLayout.FILL_2);

		choice_formats.select(matrix.getPrefs().getPreference("exportSequencesByColumn_choice", 0));
		dinp.setFile(new File(matrix.getPrefs().getPreference("exportSequencesByColumn_fileName", "")));
		if(matrix.getPrefs().getPreference("exportSequencesByColumn_writeNASequences", 0) == 0)
			check_writeNASequences.setState(false);
		else 
			check_writeNASequences.setState(true);

		dg.pack();
		dg.setVisible(true);

		if(btn.wasClicked()) {
			matrix.getPrefs().setPreference("exportSequencesByColumn_choice", choice_formats.getSelectedIndex());
			matrix.getPrefs().setPreference("exportSequencesByColumn_fileName", dinp.getFile().getParent());
			matrix.getPrefs().setPreference("exportSequencesByColumn_writeNASequences", check_writeNASequences.getState() ? 1 : 0);

			// phew ... go!
			try {
				matrix.getExporter().exportSequencesByColumn(
					dinp.getFile(), 
					(FormatHandler) fhs.get(choice_formats.getSelectedIndex()), 
					check_writeNASequences.getState(),
					new ProgressDialog(
						matrix.getFrame(),
						"Please wait, exporting sequences ...",
						"All your sequences are being exported as individual files into '" + dinp.getFile() + "'. Please wait!")
				);
			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"Error writing sequences to file!",
						"The following error occured while writing sequences to file: " + e
						);
				mb.go();

				return;
			} catch(DelayAbortedException e) {
				return;
			}

			new MessageBox(matrix.getFrame(), "All done!", "All your sequences were exported into individual files in '" + dinp.getFile() + "'.").go();
			// TODO: Would be nice if we have an option to open this Window using Explorer/Finder
		}
	}

	/**
	 * A format Listener for listening in to CHARACTER_SETS ... sigh.
	 */
	public boolean eventOccured(FormatHandlerEvent evt) throws FormatException {
		switch(evt.getId()) {
			case FormatHandlerEvent.CHARACTER_SET_FOUND:
				String name = evt.name;
				int from = evt.from;
				int to = evt.to;

				// so now we know ... and knowing is half the battle!
				// G-I-Joe! [tm]

				// celebration over, things are about to get somewhat hairy:
				// 1.	we are in a synchronized, so it's okay to expect
				// 	simplicity - nobody else should/would be doing
				// 	*anything* at this point. so, we know which
				// 	file/set we're talking about
				//
				// 2.	we need to let the loader know, so it can split
				// 	up the sequence set.
				//
				// 3.	we need code to actually split up the sequence set
				//
				//
				// All this will come.
				//
				synchronized(hash_sets) {
					if(hash_sets.get(name) != null) {
						Vector v = (Vector) hash_sets.get(name);
						v.add(new FromToPair(from, to));
					} else {
						Vector v = new Vector();
						v.add(new FromToPair(from, to));
						hash_sets.put(name, v);
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

		if(cancelled == 0)
			return true;

		MessageBox mb = new MessageBox(
				matrix.getFrame(),
				"Warning: Cancelled sequences aren't exported!",
				"You have " + cancelled + " cancelled sequence(s) in your dataset. These sequences will not be exported! Are you sure you are okay with losing the data in these sequences?",
				MessageBox.MB_YESNO | MessageBox.MB_TITLE_IS_UNIQUE);
		if(mb.showMessageBox() == MessageBox.MB_YES)
			return true;
		else
			return false;
	}
}
