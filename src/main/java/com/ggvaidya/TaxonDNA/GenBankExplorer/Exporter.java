/**
 * The Exporter is ultimately in charge of all exports. To make things just a *little*
 * bit easier, the Exporter will be independent of the rest of the system. You pass
 * the Exporter java.util.List of SequenceContainers, and it'll export them IN THAT
 * ORDER into any format you like. As in SequenceMatrix, we'll have multiple format 
 * functions to handle this.
 *
 */

/*
 *
 *  GenBankExplorer 
 *  Copyright (C) 2007 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.GenBankExplorer;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Exporter {
	private GenBankExplorer 		explorer = null;			// the GenBankExplorer object

	/**
	 * Constructor. Stores the GenBankExplorer object for future use.
	 */
	public Exporter(GenBankExplorer exp) {
		// set up the GenBankExplorer
		explorer = exp;
	}

	/**
	 * Checks that a list of SequenceContainers is 'okay'. Eventually,
	 * this means we'll check to make sure you're not exporting 
	 * identical sequences, overlapping sequences, etc. For now, not
	 * much.
	 */
	public boolean verifyExport(java.util.List containers) {
		return true;	
	}

	/**
	 * Runs the FileDialog to get a File.
	 */
	public File getSaveFile(String title) {
		FileDialog fd = new FileDialog(
				explorer.getFrame(),
				title,
				FileDialog.SAVE);
		fd.setVisible(true);

		if(fd.getFile() != null) {
			if(fd.getDirectory() != null) {
				return new File(fd.getDirectory() + fd.getFile());
			} else
				return new File(fd.getFile());
		}
		return null;
	}

	public void reportIOException(IOException e, File f) {
		reportException("Error: Could not access/write to file.",
				"I could not access/write to the file '" + f.getAbsolutePath() + "'! Are you sure you have permissions to read from or write to this file?\n\nThe technical description of the error I got is: " + e.getMessage());
	}

	public void reportException(String title, String message) {
		MessageBox mb = new MessageBox(
				explorer.getFrame(),
				title,
				message);
		mb.go();
	}

	/**
	 * Export to multiple Fasta files. Note that this means we
	 * don't HAVE a container set. So, instead:
	 * 1.	We need to ask the user where he wants the file to go.
	 * 2.	We need to get our hands on the current FeatureBin.
	 * 3.	Iterate through all the categories, then the subcategories.
	 * 4.	Come up with valid names ($name_$type.txt) in the directory specified.
	 * 5.	And, err ... that's it.
	 *
	 * @param min_species the minimum number of species to export. Files with less than min_species species will not be exported at all.
	 */
	public void exportMultipleFasta(int min_species) {
		// do we have anything?
		if(explorer.getViewManager().getGenBankFile() == null) {
			new MessageBox(explorer.getFrame(),
					"No file loaded!",
					"There's no file to export (or a bug in the program). Either way, you need to open a file.").go();
		}

		JFileChooser jfc = new JFileChooser();
		jfc.setDialogTitle("Please choose a directory to export to ...");
		jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		jfc.showSaveDialog(explorer.getFrame());

		File f_base = jfc.getSelectedFile();
		if(f_base == null)
			return;		// cancelled
		
		// I don't like JFCs, so I'm going to ASK.
		MessageBox mb = new MessageBox(
				explorer.getFrame(),
				"Are you sure you want to export multiple files to this directory?",
				"Are you sure you want to export all files to " + f_base + "? I will refuse to overwrite any files in this directory, but if the export aborts, you might be left with an incomplete set of files.",
				MessageBox.MB_TITLE_IS_UNIQUE | MessageBox.MB_YESNOTOALL);
		if(mb.showMessageBox() == MessageBox.MB_YES) {
			// okay, we have permission.
			// Go, go, go!
			//
			FeatureBin fb = new FeatureBin(explorer.getViewManager().getGenBankFile());

			ProgressDialog pd = ProgressDialog.create(explorer.getFrame(),
					"Please wait, processing features ...",
					"I'm processing features; I'll start writing them in a bit. Sorry for the wait!");

			java.util.List l = null;
			try {
				l = fb.getGenes(pd);
			} catch(DelayAbortedException e) {
				return;
			}

			Iterator i = l.iterator();
			while(i.hasNext()) {
				FeatureBin.FeatureList list = (FeatureBin.FeatureList) i.next();

				File f = new File(f_base, file_system_sanitize(list.getName() + ".txt"));
				int number = 1;
				while(f.exists())
					f = new File(f_base, file_system_sanitize(list.getName() + "_" + number + ".txt"));

				try {
					_export(list, new FastaFile(), f, ProgressDialog.create(
								explorer.getFrame(),
								"Exporting '" + f + "' ...",
								"Currently exporting sequences to '" + f + "'"),
							true,	// no_overwrite
							min_species
					);
				} catch(SequenceException e) {
					reportException("Error exporting sequences!", "The following error occured while combining the sequences: " + e.getMessage() + ". This is probably an error in the program itself.");
				} catch(IOException e) {
					reportIOException(e, f);
					return;
				} catch(DelayAbortedException e) {
					return;
				}
			}
		}
	}

	// given a string, return the same string with all possibly controversial characters replaced with '_'
	private String file_system_sanitize(String filename) {
		// Everybody stand back!
		// I know regular expressions!
		return filename.replaceAll("[^a-zA-Z0-9_()\\.]", "_");
	}

	/**
	 * Export the java.util.List of SequenceContainers as a FASTA file. 
	 * As you can see, we throw no exceptions: any errors are reported
	 * to the user before we return.
	 */
	public void exportAsFasta(java.util.List containers) {
		export(containers, new FastaFile());
	}

	public void export(java.util.List containers, FormatHandler fh) {
		// Okay, folks, here's how we work:
		// 1.	Get a File (presumably out of a FileDialog).
		// 2.	Go over the containers. Check for
		// 	alsoContains() - though we really shouldn't
		// 	have any. Not yet, anyway.
		// 3.	For every SequenceContainer, we 'simply'
		// 	getSequenceList(), concatenate it all together.
		// 4.	Then ship it to a FastaFile object.
		// 
		// *BRILLANT* [yes, i know]
		if(!verifyExport(containers))
			return;

		File f = getSaveFile("Select " + fh.getShortName() + " file to export to ...");
		if(f == null)
			return;		// cancelled

		int count = 0;
		try {
			count = _export(containers, fh, f, ProgressDialog.create(
							explorer.getFrame(),
							"Please wait, assembling sequences for export ...",
							"I am assembling all selected sequences in preparation for export. Please give me a second!"
						),
					false,	// no_overwrite; we don't need this, since they've already acknowledged file existance, etc.
					0	// there is no min_species.
				);
		} catch(SequenceException e) {
			reportException("Error exporting sequences!", "The following error occured while combining the sequences: " + e.getMessage() + ". This is probably an error in the program itself.");
			return;
		} catch(IOException e) {
			reportIOException(e, f);
			return;
		} catch(DelayAbortedException e) {
			return;
		}
			
		MessageBox mb = new MessageBox(
				explorer.getFrame(),
				"Export successful!",
				count + " features were successfully exported to '" + f.getAbsolutePath() + "' in the " + fh.getShortName() + " format.");
		mb.go();
	}

	public int _export(java.util.List containers, FormatHandler fh, File f, DelayCallback delay, boolean no_overwrite, int min_species) throws IOException, DelayAbortedException, SequenceException {
		if(f.exists() && no_overwrite)
			throw new IOException("The file '" + f + "' exists! I will not overwrite it.");

			SequenceList sl = combineContainers(containers, delay);
			int count = sl.count();

			if(min_species > 0) {
				if(sl.getSpeciesDetails(null).getSpeciesCount() < min_species)
					return 0;
			}

			ProgressDialog pd = ProgressDialog.create(
				explorer.getFrame(),
				"Please wait, exporting " + count + " features ...",
				"I am exporting " + count + " features to '" + f.getAbsolutePath() + "' in the " + fh.getShortName() + " format. Sorry for the wait!");
			
			fh.writeFile(f, sl, pd);
			return count;
	}

	public SequenceList combineContainers(java.util.List list, DelayCallback delay) throws SequenceException, DelayAbortedException {
		SequenceList sl = new SequenceList();
		Iterator i = list.iterator();

		if(delay != null)
			delay.begin();

		int x = 0;
		while(i.hasNext()) {
			if(delay != null)
				delay.delay(x, list.size());
			x++;

			SequenceContainer container = (SequenceContainer) i.next();

			sl.addAll(container.getAsSequenceList());

			if(container.alsoContains().size() > 0)
				sl.addAll(combineContainers(container.alsoContains(), null));
		}

		if(delay != null)
			delay.end();

		return sl;
	}

}
