/**
 * SpeciesMatrix represents datasets in a matrix of sequences versus genes.
 * This gives it an edge against TaxonDNA, which really sees datasets as
 * a sequence-to-polypeptide map. SpeciesMatrix can therefore *really*
 * import in GenBank and Nexus formats (it's import will, I suppose, be
 * something to behold). God only knows if it will 'live' within TaxonDNA
 * forever: one supposes that it'll eventually break free, but while a
 * lot of the code (DNA.*, for a big instance) is living together, it
 * probably makes a lot of sense to keep them joined at the hip.
 *
 * SpeciesMatrix will, hopefully, be a much SMALLER program than TaxonDNA.
 * We will also experiment with Swing, just to see how that goes (and because
 * JTable would save work like nobody's business).
 *
 */

/*
 *
 *  SpeciesMatrix
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

package com.ggvaidya.TaxonDNA.SpeciesMatrix;

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for drag-n-drop
import java.awt.dnd.*;		// drag-n-drop
import java.io.*;
import java.util.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class SpeciesMatrix implements WindowListener, ActionListener, DropTargetListener, Runnable {
	// SpeciesMatrix version number 
	private static String 	version 		= "0.2";
	
	// The following variables create and track our AWT interface
	private Frame		mainFrame 		= new Frame();
	private JTable		mainTable		= null;	
	private DropTarget	dropTarget		= null;
	private DropTarget	dropTargetTable		= null;

	// Other components of SpeciesMatrix
	private SequenceGrid	seqgrid			= new SequenceGrid(this);
	private Preferences	prefs			= new Preferences(this);
	private TableModel	tableModel		= new TableModel(this);

	// Our variables
	private Vector		filesToLoad		= new Vector();
	private boolean		isInterfaceLocked	= false;

//
//	1.	ENTRYPOINT. The entrypoint is where the entire SpeciesMatrix system starts up.
//		As usual, you can just create a new SpeciesMatrix to 'do everything'.
//
	/**
	 * SpeciesMatrix's entry point. Our arguments are the files we must open
	 * initially.
	 */
	public static void main(String[] args) {
		Vector files	=	new Vector();

		for(int x = 0; x < args.length; x++) {
			files.add(new File(args[x]));	
		}

		new SpeciesMatrix( files );
	}
	
//
// 	2.	CONSTRUCTORS.
//
	/**
	 * Creates a new SpeciesMatrix object. We create a new toplevel.
	 * Be warned that we do NOT spawn a thread: if TaxonDNA wants
	 * to create a SpeciesMatrix for whatever reason, it will have
	 * to do this IN a thread - otherwise when TaxonDNA exits,
	 * we will automatically exit as well.
	 *
	 * @param files A vector of files to load in.
	 */
	public SpeciesMatrix(Vector files) {
		createUI();			// create our user interface

		// now load up all the files
		Iterator i = files.iterator();
		while(i.hasNext()) {
			File f = (File) i.next();

			addFile(f);
		}

		resetFrameTitle();
	}

//
// 	3.	EVENT PROCESSING. Handles stuff which happens to the main frame, mostly.
//
	/**
	 * Handles ActionEvents for the file menu and help menu.
	 */
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();

		if(isInterfaceLocked)
			return;

		//
		// File -> New. Just close the present file. 
		// 
		if(cmd.equals("Clear all"))
			closeFile();

		//
		// File -> Open. Tries to load the file specified.
		// The current file is closed before anything 
		// further is done.
		//
		if(cmd.equals("Add sequences")) {
			// if you were expecting a 'closeFile()' here - addFile will call that
			// once the file has been successfully loaded.
			
			FileDialog fd = new FileDialog(mainFrame, "Which file would you like to open?", FileDialog.LOAD);
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
		// File -> Exit. Calls our exit() way out.
		//
		if(cmd.equals("Exit"))
			exit();		

		//
		// Export -> Export as NEXUS. Since TaxonDNA doesn't understand
		// complex sequences, we do all of these manually. Thankfully,
		// there's not a lot that needs to be done.
		//
		// We basically just 'export' this on to the MatrixModel,
		// who knows all about such things.
		//
		if(cmd.equals("Export as NEXUS")) {
			FileDialog fd = new FileDialog(mainFrame, "Where would you like to export this set to?", FileDialog.SAVE);
			fd.setVisible(true);

			if(fd.getFile() != null) {
				if(fd.getDirectory() != null) {
					exportAsNexus(new File(fd.getDirectory() + fd.getFile()));
				} else {
					exportAsNexus(new File(fd.getFile()));
				}
			}
		}

		//
		// Settings -> Preferences. Allows you to change how SpeciesMatrix
		// works.
		//
		if(cmd.equals("Preferences")) {
			prefs.setVisible(true);	// modal!
		}

		//
		// Help -> About. We should put something
		// up here, once we get proper documentation
		// working in the Help -> * menu.
		//
		if(cmd.equals("About")) {
			String copyrightNotice = new String("SpeciesMatrix " + version + ", Copyright (C) 2006 Gaurav Vaidya. \nSpeciesMatrix comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it under certain conditions; check the COPYING file you should have recieved along with this package.\n\n");
					
			MessageBox mb = new MessageBox(mainFrame, "About this program", copyrightNotice 
					+ "Written by Gaurav Vaidya\nIf I had time to put something interesting here, there'd be something in the help menu too. All apologies.\n\n"
					+ "This program was written with Vim (http://vim.org) with occasional help from Eclipse (http://eclipse.org/). Compilation was handled by Ant (http://ant.apache.org/)." 
			);
			mb.showMessageBox();
		}		
		

		//
		// The following are OBSOLETE options
		// I've commented them out in case I 
		// need them later on.
		//
		/*
		//
		// File -> Save As. Tries to write this SequenceList
		// into a user-specified file.
		// 
		if(cmd.equals("Save As")) {
			String filename = "";
			
			FileDialog fd = new FileDialog(mainFrame, "Where would you like to save this file?", FileDialog.SAVE);
			fd.setVisible(true);

			if(fd.getFile() != null) {
				filename = fd.getFile();
				if(fd.getDirectory() != null)
					filename = fd.getDirectory() + filename;

				sequences.setFile(new File(filename));
				saveFile();
			}
		}
		
		*/
		/*
		//
		// Export -> *
		//
		if((cmd.length() > 7) && (cmd.substring(0, 7).equals("Export_"))) {
			int index = Integer.parseInt(cmd.substring(7));
			FormatHandler handler = (FormatHandler) SequenceList.getFormatHandlers().get(index); 

			FileDialog fd = new FileDialog(
					getFrame(), 
					"Choose filename to export as " + handler.getShortName(), 
					FileDialog.SAVE);

			fd.setVisible(true);

			if(fd.getFile() != null) {
				String filename = fd.getFile();
				
				if(fd.getDirectory() != null) {
					filename = fd.getDirectory() + filename;
				}

				SequenceList sequences = lockSequenceList();
				
				try {
					handler.writeFile(new File(filename),
							sequences,
							new ProgressDialog(mainFrame,
								"Exporting to '" + filename + "' ...",
								getMessage(Messages.SAVE_FILE_FORMAT, filename, handler.getShortName()), 
								0)
							);
				
				} catch(IOException e) {
						MessageBox mb = new MessageBox(
								getFrame(), 
								"Could not write file!", 
								getMessage(Messages.IOEXCEPTION_WRITING, e)
							);
						mb.go();
				} catch(DelayAbortedException e) {
					unlockSequenceList();

					// continue unimpeded
				}

				unlockSequenceList();

				MessageBox mb = new MessageBox(
						getFrame(), 
						"File exported successfully!", 
						"All current sequences were successfully exported to " + handler.getShortName() + ", and stored in '" + filename + "'.");

				mb.go();	
			}
		}

		//
		// Import -> *
		//
		if((cmd.length() > 7) && (cmd.substring(0, 7).equals("Import_"))) {
			int index = Integer.parseInt(cmd.substring(7));
			FormatHandler handler = (FormatHandler) SequenceList.getFormatHandlers().get(index); 

			FileDialog fd = new FileDialog(
					getFrame(), 
					"Choose filename to import as " + handler.getShortName(), 
					FileDialog.LOAD);

			fd.setVisible(true);

			if(fd.getFile() != null) {
				String filename = fd.getFile();
				
				if(fd.getDirectory() != null) {
					filename = fd.getDirectory() + filename;
				}

				addFile(new File(filename), handler);
			}
		}
		*/
		
	}

	//
	// The following functions are part of the WindowListener stuff.
	// Hook if needed.
	//
	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}	
	/**
	 * If somebody tries to close the window, call our local exit() before closing the Window
	 */
	public void windowClosing(WindowEvent e) {
		exit();
	}

	//
	// The following functions are part of the Drop target listener stuff.
	//
	public void dragEnter(DropTargetDragEvent dtde) {
		if(dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			// A list of files!
			// Yummy!
			dtde.acceptDrag(DnDConstants.ACTION_COPY);
		} else {
			dtde.rejectDrag();
		}
	}
	public void dragExit(DropTargetEvent dte) {}
	public void dragOver(DropTargetDragEvent dtde) {
	}
	public void drop(DropTargetDropEvent dtde) {
		if(dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
			// A list of files!
			// Yummy!
			dtde.acceptDrop(DnDConstants.ACTION_COPY);

			Transferable tf = 		dtde.getTransferable();
			java.util.List list = null;
			try {
				list =		(java.util.List) tf.getTransferData(DataFlavor.javaFileListFlavor);
			} catch(UnsupportedFlavorException e) {
				dtde.rejectDrop();
				return;	
			} catch(IOException e) {
				dtde.rejectDrop();
				return;
			}

			Iterator i = list.iterator();
			while(i.hasNext()) {
				File file = 	(File) i.next();

				addFile(file);
			}

			dtde.dropComplete(true);
		} else {
			dtde.rejectDrop();
		}
	}
	public void dropActionChanged(DropTargetDragEvent dtde) {}

//
//	4.	FUNCTIONAL CODE. It does things.
//
//		Mostly, it's used by us to call bits of code to 'do' things: exit SpeciesMatrix,
//		exportAsNexus, etc. Public functions can be called by other components to do
//		things, such as poke a sequence straight onto the screen.
//		
	/**
	 * Updates the display. In our case, we just need to call the TableModel
	 * and tell it about this.
	 */
	public void updateDisplay() {
		tableModel.updateDisplay();
	}

	/**
	 * Creates the user interface of SpeciesMatrix.
	 */
	private void createUI() {
		// main frame
		mainFrame = new Frame("SpeciesMatrix");
		mainFrame.addWindowListener(this);
		mainFrame.setLayout(new BorderLayout());
		mainFrame.setMenuBar(createMenuBar());
		mainFrame.setBackground(SystemColor.control);

		tableModel = new TableModel(this);
		mainTable = new JTable(tableModel);
		JScrollPane scrollPane = new JScrollPane(mainTable);
		// i get these dimensions straight from the java docs, so
		// don't blame me and change them if you like, etc.
		mainTable.setPreferredScrollableViewportSize(new Dimension(500, 200));
		mainFrame.add(scrollPane);

		// Start the mainFrame!
		mainFrame.setVisible(true);		
		mainFrame.pack();

		// set up the DropTarget
		// Warning: this will need to be changed if table is ever not
		// completely covering our client area
		dropTarget = new DropTarget(mainFrame, this);
		dropTarget.setActive(true);
		
		dropTargetTable = new DropTarget(mainTable, this);
		dropTargetTable.setActive(true);
	}
	
	/**
	 * Resets the title of the main frame. You don't need to know this: you can safely
	 * unlockSequenceList(sequences) and this will be handled.
	 */
	private void resetFrameTitle() {
		StringBuffer title = new StringBuffer("SpeciesMatrix " + version);

		mainFrame.setTitle(title.toString());
	}

	/**
	 * Creates and returns the main menubar.
	 */
	private MenuBar createMenuBar() {
		Iterator i;
		MenuBar menubar 	=	new MenuBar();
		int count = 0;

		// File menu
		Menu 	file		=	new Menu("File");
		file.add(new MenuItem("Clear all"));
		file.add(new MenuItem("Add sequences"));
//		file.addSeparator();
//		file.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
//		file.add(new MenuItem("Save As", null));
				// new MenuShortcut(KeyEvent.VK_V))); --> Gets in the way of Ctrl-C Ctrl-V
				// new MenuShortcut(KeyEvent.VK_A)) --> deleting the shortcut since it gets in the way of Ctrl-A on Windows
		file.addSeparator();
		file.add(new MenuItem("Exit", new MenuShortcut(KeyEvent.VK_X)));
		file.addActionListener(this);
		menubar.add(file);

		// Export menu
		Menu	export 		= 	new Menu("Export");
		export.add(new MenuItem("Export as NEXUS", new MenuShortcut(KeyEvent.VK_N)));
		export.addActionListener(this);
		menubar.add(export);

		// Settings menu
		Menu 	settings	=	new Menu("Settings");
		settings.add(new MenuItem("Preferences"));
		settings.addActionListener(this);
		menubar.add(settings);

		// Help menu
		Menu	help		=	new Menu("Help");
		help.add("About");
		help.addActionListener(this);
		menubar.add(help);
		menubar.setHelpMenu(help);

		return menubar;
	}

	/**
	 *	Clean up anything needing cleanup-ing, then kill this program with all
	 *	the power of System.exit(0).
	 */
	private void exit() {
		closeFile(); 
		mainFrame.dispose();		// this "closes" this window; whether or not this 
						// terminates the application depends on whether 
						// other stuff is running.
		System.exit(0);			// until we run this command. then it's all dead, all right.

	}

	/**
	 * 	Export the current set as a Nexus file into file 'f'. Be warned that File 'f' will be
	 * 	completely overwritten.
	 *
	 * 	@param f	The file where the Nexus file should be saved.
	 */
	public void exportAsNexus(File f) {
		try {
			seqgrid.exportAsNexus(f, 
					new ProgressDialog(
						mainFrame, 
						"Please wait, exporting dataset ...",
						"The dataset is being exported. Sorry for the delay."
						)
			);
			MessageBox mb = new MessageBox(
					mainFrame,
					"Success!",
					"This set was successfully exported to '" + f + "' in the Nexus format."
					);
			mb.go();

		} catch(IOException e) {
			MessageBox mb = new MessageBox(
					mainFrame,
					"Error while exporting file",
					"There was an error while exporting this set to '" + f + "'. Please ensure that you have adequate permissions and that your hard disk is not full.\n\nThe technical description of this error is: " + e);
			mb.go();
		} catch(DelayAbortedException e) {
			MessageBox mb = new MessageBox(
					mainFrame,
					"File export cancelled",
					"Your file export was cancelled. The file '" + f + "' has probably be left incomplete.");
			mb.go();
		}
	}

	/*
	 * Saves the current file.
	 *
	 * @return true, if the file has been successfully saved. 
	 */
	/*
	public boolean saveFile() {
		lockSequenceList();
		File file = sequences.getFile();

		if(file == null) {
			FileDialog fd = new FileDialog(
					mainFrame, 
					"What name would you like to save this file under?", 
					FileDialog.SAVE
				);
			fd.setVisible(true);

			if(fd.getFile() != null) {
				file = new File(fd.getDirectory() + fd.getFile());
				sequences.setFile(file);
			} else {
				unlockSequenceList();
				return false;
			}
		}

		if(sequences.getFormatHandler() == null)
			sequences.setFormatHandler(new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile());

		try {
			sequences.writeToFile(new ProgressDialog(
						mainFrame,
						"Please wait, saving file ...",
						"Saving sequences into '" + sequences.getFile().getAbsolutePath() + "' in " + sequences.getFormatHandler().getShortName() + " format",
						0
						)
					);
			
			modified = false;
			unlockSequenceList();

			return true;	

		} catch(IOException e) {
			MessageBox mb = new MessageBox(
					mainFrame, 
					"Could not save file", 
					getMessage(Messages.IOEXCEPTION_WRITING, sequences.getFile(), e)
				);
			mb.go();

			unlockSequenceList();

			return false;
		} catch(DelayAbortedException e) {
			// go on
			unlockSequenceList();

			return false;
		}
	}
	*/

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
	 * If you really need a synchronous addFile(), let me know
	 * and I'll write one.
	 *
	 */
	public void addFile(File file, FormatHandler handler) {

		synchronized(filesToLoad) {
			filesToLoad.add(file);
			filesToLoad.add(handler);	// primitive two-variable list

			Thread t = new Thread(this);
			t.start();
		}
	}

	/**
	 * The Thread responsible for the asynchronous addFile().
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
							getFrame(), 
							"Loading file ...", 
							"Loading '" + file + "' (of format " + handler.getShortName() + " into memory. Sorry for the delay!",
							ProgressDialog.FLAG_NOCANCEL
							)
					);
				else 
					sequences = SequenceList.readFile(
						file, 
						new ProgressDialog(
							getFrame(), 
							"Loading file ...", 
							"Loading '" + file + "' into memory. Sorry for the delay!",
							ProgressDialog.FLAG_NOCANCEL
							)
						);

				seqgrid.addSequenceList(sequences);

			} catch(SequenceListException e) {
				MessageBox mb = new MessageBox(getFrame(), "Could not read file " + file + "!", e.toString());
				mb.go();

				return;
			
			} catch(DelayAbortedException e) {
				return;
			}

			updateDisplay();
		}
		}
	}

	/**
	 * Tries to load a particular file into the present SequenceList. If successful, it will sequences the present
	 * file to be whatever was specified. If not successful, it will leave the external situation (file, sequences)
	 * unchanged.
	 *
	 * @return true, if the file was successfully loaded.
	 */
	public void addFile(File file) {
		addFile(file, null);
	}
	

	/**
	 * Closes the current file. Program is reset to a waiting state, with
	 * SequenceList in particular being set back to null.
	 */
	private void closeFile() {
		seqgrid.clear();

		/*
		lockSequenceList();
		
		if(sequences != null && modified) {
			MessageBox mb = new MessageBox(
					mainFrame, 
					"Save file now?", 
					"This file has been changed since the last time you saved it. Would you like to save it now?", 
					MessageBox.MB_YESNOCANCEL
				);

			switch(mb.showMessageBox()) {
				case MessageBox.MB_YES:
					saveFile();
					break;

				case MessageBox.MB_CANCEL:
					unlockSequenceList();
					return;
			}
		}

		unlockSequenceList(null);
		*/
	}

//
//	X.	GETTERS.	Returns values relevant to this object.
//
	/**
	 * Returns the current Frame object.
	 */
	public Frame		getFrame() {
		return mainFrame;
	}
	
	/**
	 * Returns the Preferences object (so you can see what the user wants).
	 */
	public Preferences getPrefs() {
		return prefs;
	}

	/**
	 * Returns the SequenceGrid object, if anyone wants it.
	 */
	public SequenceGrid getSequenceGrid() {
		return seqgrid;
	}
}
