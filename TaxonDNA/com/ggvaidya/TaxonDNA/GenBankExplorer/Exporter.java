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
		reportException("Error: Could not read or write file.",
				"I could not access the file '" + f.getAbsolutePath() + "'! Are you sure you have permissions to read from or write to this file?\n\nThe technical description of the error I got is: " + e.getMessage());
	}

	public void reportException(String title, String message) {
		MessageBox mb = new MessageBox(
				explorer.getFrame(),
				title,
				message);
		mb.go();
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

		try {
			SequenceList sl = combineContainers(containers);
			int count = sl.count();

			ProgressDialog pd = new ProgressDialog(
				explorer.getFrame(),
				"Please wait, exporting " + count + " features ...",
				"I am exporting " + count + " features to '" + f.getAbsolutePath() + "' in the " + fh.getShortName() + " format. Sorry for the wait!");
			
			fh.writeFile(f, sl, pd);

			MessageBox mb = new MessageBox(
					explorer.getFrame(),
					"Export successful!",
					count + " features were successfully exported to '" + f.getAbsolutePath() + "' in the " + fh.getShortName() + " format.");
			mb.go();

		} catch(SequenceException e) {
			reportException("Error exporting sequences!", "The following error occured while combining the sequences: " + e.getMessage() + ". This is probably an error in the program itself.");
		} catch(IOException e) {
			reportIOException(e, f);
		} catch(DelayAbortedException e) {
			return;
		}
	}

	public SequenceList combineContainers(java.util.List list) throws SequenceException {
		SequenceList sl = new SequenceList();
		Iterator i = list.iterator();

		while(i.hasNext()) {
			SequenceContainer container = (SequenceContainer) i.next();

			sl.addAll(container.getAsSequenceList());

			if(container.alsoContains().size() > 0)
				sl.addAll(combineContainers(container.alsoContains()));
		}

		return sl;
	}

}
