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

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for drag-n-drop
import java.awt.dnd.*;		// drag-n-drop
import java.io.*;
import java.util.*;

import javax.swing.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class FileManager implements Runnable {
	private SequenceMatrix	matrix;
	private Vector 		filesToLoad = new Vector();

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
	 * How this works is a little non-logical: add() immediately
	 * queues up the file for addition, spawns a thread
	 * to handle the actual file loading, and returns immediately.
	 *
	 * The upshot of this is that add() is non-blocking:
	 * you are NOT guaranteed that the file will be loaded
	 * once add() returns.
	 *
	 * We will lock the interface, and only unlock it once the
	 * files are loaded. This means that if you're waiting for
	 * input (i.e. the user clicking on a menu option), you're
	 * okay. But do NOT begin calculations immediately after
	 * you've called add().
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
	public void add(File file, FormatHandler handler) {
		synchronized(filesToLoad) {
			filesToLoad.add(file);
			filesToLoad.add(handler);	// primitive two-variable list

			Thread t = new Thread(this);
			t.start();
		}
	}

	/**
	 * The Thread responsible for the asynchronous add().
	 *
	 */
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
		
			SequenceList sequences = null;
		
			try {
				if(handler != null)
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
				else 
					sequences = SequenceList.readFile(
						file, 
						new ProgressDialog(
							matrix.getFrame(), 
							"Loading file ...", 
							"Loading '" + file + "' into memory. Sorry for the delay!",
							ProgressDialog.FLAG_NOCANCEL
							)
						);

				matrix.getSequenceGrid().addSequenceList(sequences);

			} catch(SequenceListException e) {
				MessageBox mb = new MessageBox(matrix.getFrame(), "Could not read file!", "I could not understand a sequence in this file. Please fix any errors in the file.\n\nThe technical description of the error is: " + e);
				mb.go();

				return;
			
			} catch(DelayAbortedException e) {
				return;
			}

			matrix.updateDisplay();
		}
		}
	}

	/**
	 * Tries to load a particular file into the present SequenceList. If successful, it will sequences the present
	 * file to be whatever was specified. If not successful, it will leave the external situation (file, sequences)
	 * unchanged.
	 */
	public void add(File file) {
		add(file, null);
	}
	
	/**
	 * Ask the user for the file, even.
	 */
	public void add() {
		FileDialog fd = new FileDialog(matrix.getFrame(), "Which file would you like to open?", FileDialog.LOAD);
		fd.setVisible(true);

		if(fd.getFile() != null) {
			if(fd.getDirectory() != null) {
				add(new File(fd.getDirectory() + fd.getFile()));
			} else {
				add(new File(fd.getFile()));
			}
		}
	}

	//
	// X.	EXPORTERS.
	//

	/**
	 * Closes everything. The entire sequence grid is cleared.
	 */
	public void clear() {
		matrix.getSequenceGrid().clear();
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
		File f = getFile("Where would you like to export this set to?", FileDialog.SAVE);
		if(f == null)
			return;

		try {
			matrix.getSequenceGrid().exportAsNexus(f, 
					new ProgressDialog(
						matrix.getFrame(), 
						"Please wait, exporting dataset ...",
						"The dataset is being exported. Sorry for the delay."
						)
			);

			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Success!",
					"This set was successfully exported to '" + f + "' in the Nexus format."
					);
			mb.go();

		} catch(IOException e) {
			reportIOException(e, f, IOE_WRITING);
		} catch(DelayAbortedException e) {
			reportDelayAbortedException(e, "Export of Nexus file");
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
		File f = getFile("Where would you like to export this set to?", FileDialog.SAVE);
		if(f == null)
			return;

		try {
			matrix.getSequenceGrid().exportAsTNT(f, 
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
	 * Export the table itself as a tab delimited file.
	 */
	public void exportTableAsTabDelimited() {
		// export the table to the file, etc.
		File file = getFile("Where would you like to export this table to?", FileDialog.SAVE);
		if(file == null)
			return;

		TableModel tableModel = matrix.getTableModel();
		
		try {
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

		} catch(IOException e) {
			reportIOException(e, file, IOE_WRITING);
		}
	}

}
