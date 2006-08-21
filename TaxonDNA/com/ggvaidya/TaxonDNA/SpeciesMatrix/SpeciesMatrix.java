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
import java.io.*;
import java.util.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class SpeciesMatrix implements WindowListener, ActionListener, ItemListener {
	// the current SpeciesMatrix version number 
	private static String 	version 		= "0.1";

	// the following variables create and track our AWT interface
	private Frame		mainFrame 		= new Frame();

	private MatrixModel	matrixModel		= new MatrixModel(this);

//
//	1.	ENTRYPOINT. The entrypoint is where the entire SpeciesMatrix system starts up.
//		Right now, it DOESN'T EVEN load sequence files; depending on need, we might have some kind
//		of command line arguments eventually. Note that only the *application*
//		should enter from here; a new TaxonDNA window will be spawned entirely
//		by using 'new TaxonDNA(file)'. 
//
	/**
	 * TaxonDNA's main entrypoint. We check for command line arguments (in this case, all
	 * of which must be files) and we start them using a call to 'new TaxonDNA(file)'. 
	 */
	public static void main(String[] args) {
		new SpeciesMatrix();
	}
	
//
// 	2.	CONSTRUCTORS.
//
	/**
	 * Create a new TaxonDNA object. Each TaxonDNA is essentially
	 * a window to deal with a particular SequenceList. We will
	 * start off by having an empty, unset SequenceList.
	 *
	 */
	public SpeciesMatrix() {
		createUI();			// create our user interface
	}

//
// 	3.	EVENT PROCESSING. Handles stuff which happens to the main frame, mostly.
//
	/**
	 * Handles ActionEvents for the file menu and help menu.
	 */
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();

		//
		// File -> New. Just close the present file. 
		// 
		if(cmd.equals("New"))
			closeFile();

		//
		// File -> Open. Tries to load the file specified.
		// The current file is closed before anything 
		// further is done.
		//
		if(cmd.equals("Open")) {
			// if you were expecting a 'closeFile()' here - loadFile will call that
			// once the file has been successfully loaded.
			
			FileDialog fd = new FileDialog(mainFrame, "Which file would you like to open?", FileDialog.LOAD);
			fd.setVisible(true);

			if(fd.getFile() != null) {
				if(fd.getDirectory() != null) {
					loadFile(new File(fd.getDirectory() + fd.getFile()));
				} else {
					loadFile(new File(fd.getFile()));
				}
			}
		}

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

		/*
		//
		// File -> Save. Tries to save this SequenceList
		// to its own file.
		//
		if(cmd.equals("Save"))
			saveFile();

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
		//
		// File -> Exit. Close the present file,
		// then dispose of the main window.
		//
		if(cmd.equals("Exit")) {
			closeFile(); 
			mainFrame.dispose();		// this "closes" this window; whether or not this 
							// terminates the application depends on whether 
							// other stuff is running.
		}

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

				loadFile(new File(filename), handler);
			}
		}
		*/
		
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
	}

	/**
	 * We monitor the list of possible actions,
	 * (from the list_actions, in the upper left hand corner?)
	 * and handle clicks on that list (against the UIExtensions)
	 */
	public void itemStateChanged(ItemEvent e) {
	}	
	
	/**
	 * If somebody tries to close the window, call closeFile() before closing the Window
	 */
	public void windowClosing(WindowEvent e) {
		mainFrame.dispose();
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

//
//	4.	FUNCTIONAL CODE. Let the functional programmers cry over that one. I mean that
//		it *does* stuff - saveFile, closeFile, etc. Other classes can call these if
//		they need to pull a unlockSequenceList(list), and want to ensure that the
//		last file is closed properly.
//
	public void exportAsNexus(File f) {
		try {
			matrixModel.exportAsNexus(f);
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
		}
	}

	/**
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

	
	/**
	 * Tries to load a particular file into the present SequenceList. If successful, it will sequences the present
	 * file to be whatever was specified. If not successful, it will leave the external situation (file, sequences)
	 * unchanged.
	 *
	 * @return true, if the file was successfully loaded.
	 */
	public boolean loadFile(File file, FormatHandler handler) {
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

			matrixModel.addSequenceList(sequences);

		} catch(SequenceListException e) {
			MessageBox mb = new MessageBox(getFrame(), "Could not read file " + file + "!", e.toString());
			mb.go();

			return false;
			
		} catch(DelayAbortedException e) {
			return false;
			
		}
		
		return true; 
	}

	/**
	 * Tries to load a particular file into the present SequenceList. If successful, it will sequences the present
	 * file to be whatever was specified. If not successful, it will leave the external situation (file, sequences)
	 * unchanged.
	 *
	 * @return true, if the file was successfully loaded.
	 */
	public boolean loadFile(File file) {
		return loadFile(file, null);
	}
	

	/**
	 * Closes the current file. Program is reset to a waiting state, with
	 * SequenceList in particular being set back to null.
	 */
	private void closeFile() {
		matrixModel.clear();

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

	/**
	 * Returns the current Frame object.
	 */
	public Frame		getFrame() {
		return mainFrame;
	}
	
//
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

		JTable table = new JTable(matrixModel);
		JScrollPane scrollPane = new JScrollPane(table);
		// i get these dimensions straight from the java docs, so
		// don't blame me and change them if you like, etc.
		table.setPreferredScrollableViewportSize(new Dimension(500, 70));
		mainFrame.add(scrollPane);

		// Start the mainFrame!
		mainFrame.setVisible(true);		
		mainFrame.pack();
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
		file.add(new MenuItem("New", new MenuShortcut(KeyEvent.VK_N)));
		file.add(new MenuItem("Open", new MenuShortcut(KeyEvent.VK_O)));
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
		export.add(new MenuItem("Export as NEXUS", new MenuShortcut(KeyEvent.VK_X)));
		export.addActionListener(this);
		menubar.add(export);

		// Help menu
		Menu	help		=	new Menu("Help");
		help.add("About");
		help.addActionListener(this);
		menubar.add(help);
		menubar.setHelpMenu(help);

		return menubar;
	}
}
