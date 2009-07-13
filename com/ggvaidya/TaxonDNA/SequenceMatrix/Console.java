/**
 * Console provides a command-line console to the SequenceMatrix functionality.
 * You still need Swing, because it'll probably pop up occasionally. It's main
 * mission in life is to provide a "fake" SequenceMatrix which can work the rest
 * of the code, providing easy access for testing interface into the code.
 * 
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2009 Gaurav Vaidya
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

// AWT Stuff
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.awt.dnd.*;		// for drag-n-drop
import java.awt.datatransfer.*;	// for drag-n-drop

// Swing stuff
import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.table.*;

// TaxonDNA stuff
import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Console extends SequenceMatrix {
	// The following variables create and track our user interface
	private Frame		mainFrame 		= new Frame();		// A frame
	private JTable		mainTable		= null;			// with a table

	// Other components of SequenceMatrix
	// information
//	private DataStore	dataStore		= new DataStore(this);	// the datastore: stores data
	private Preferences	prefs			= new Preferences(this); // preferences are stored here
	private Exporter	exporter		= new Exporter(this);	// and the actual exports go thru exporter
	
	// managers
	private FileManager	fileManager		= new FileManager(this); // file manager: handles files coming in and outgoing
	private TableManager	tableManager		= null;			// the table manager handles mid-level UI
	
	// additional functionality
	private Taxonsets	taxonSets		= new Taxonsets(this);	// and taxonsets are set (on the UI) here

	// analyses
	public FindDistances	findDistances		= new FindDistances(this); // helps you find distances

	// CheckboxMenuItems corresponding to the possible 'views' the program can be in.
	private CheckboxMenuItem chmi_displaySequences	= null;
	private CheckboxMenuItem chmi_displayDistances 	= null;
	private CheckboxMenuItem chmi_displayCorrelations = null;
	
	private JToolBar	toolBar			= null;
	private JPanel		statusBar		= null;

//
//	1.	ENTRYPOINT. The entrypoint is where the entire SequenceMatrix system starts up.
//		As usual, you can just create a new SequenceMatrix to 'do everything'.
//
	/**
	 * SequenceMatrix's entry point. Our arguments are the files we must open
	 * initially.
	 *
	 */
	public static void main(String[] args) {
            Sequence.setMinOverlap(0);

            // fileManager.addFile(f);

            System.out.println("SequenceMatrix Console ready to go!");
            Console c = new Console();

            System.exit(0);     // Go.
	}

        private Console() {
            super(null);

            findDistances.go();
        }

//
// GETTERS: Returns values of possible interest, etc.
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
	 * Returns the Exporter.
	 */
	public Exporter getExporter() {
		return exporter;
	}

	/**
	 * Returns the DataStore object, should you want it.
	 */
//	public DataStore getDataStore() {
//		return dataStore;
//	}

	/**
	 * Returns the FileManager.
	 */
	public FileManager getFileManager() {
		return fileManager;
	}

	/**
	 * Returns the DataModel. Now, in Reality, this is
	 * just the dataStore object, suitable casted. But
	 * since it's an abstraction, I figured, hey, why
	 * not.
	 */
//	public TableModel getTableModel() {
//		return (TableModel) dataStore;
//	}

	/**
	 * Returns the TableManager
	 */
	public TableManager getTableManager() {
		return tableManager;
	}

	/**
	 * Returns the Table.
	 */
	public JTable getJTable() {
		return mainTable;
	}

	/**
	 * Returns the Taxonsets component.
	 */
	public Taxonsets getTaxonsets() {
		return taxonSets;
	}
	
	/**
	 * Returns the citation used by SequenceMatrix.
	 */
	public String getCitation() {
		return "Meier, R., Kwong, S., Vaidya, G., Ng, Peter K. L. DNA Barcoding and Taxonomy in Diptera: a Tale of High Intraspecific Variability and Low Identification Success. Systematic Biology, 55: 715-728.";
	}

//
// 	4.	EVENT PROCESSING. Handles stuff which happens to the main frame, mostly.
// 		This involves menu, window and drop listening.
//
	/**
	 * Handles ActionEvents for the file menu and help menu.
	 */
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();

		//
		// File -> New. Just close the present file. 
		// 
		if(cmd.equals("Clear all"))
			tableManager.clear();

		//
		// File -> Open. Tries to load the file specified.
		// The current file is closed before anything 
		// further is done.
		//
		if(cmd.equals("Add sequences"))
			fileManager.addFile();

		//
		// File -> Save. Effectively an 'export as #sequences'.
		//
		if(cmd.equals("Save"))
			fileManager.exportAsSequences();

		//
		// File -> Exit. Calls our exit() way out.
		//
		if(cmd.equals("Exit"))
			System.exit(0);	

		// Analyses -> Zero Percent Distances
		if(cmd.equals("Find all zero percent distances")) {
			findDistances.go();
		}

		/*
		// Analyses -> Calculate the rank table
		if(cmd.equals("Calculate the rank table")) {
			// do something;
		}
		*/

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
				fileManager.addFile(new File(filename), handler);
			}
		}


		// Export -> Export table as tab delimited.
		if(cmd.equals("Export table as tab-delimited"))
			fileManager.exportTableAsTabDelimited();
		
		// Export -> One file per column. 
		if(cmd.equals("Export table as sequences (one file per column)"))
			fileManager.exportSequencesByColumn();

		// Export -> Group columns randomly in groups of X
		if(cmd.equals("Export columns grouped randomly"))
			fileManager.exportSequencesByColumnsInGroups();

		//
		// Export -> Export as NEXUS.
		//
		if(cmd.equals("Export sequences as NEXUS"))
			fileManager.exportAsNexus();

		//
		// Export -> Export as TNT.
		//
		if(cmd.equals("Export sequences as TNT"))
			fileManager.exportAsTNT();

		//
		// Settings -> Taxonsets. Allows you to manipulate taxonsets. 
		//
		if(cmd.equals("Taxonsets"))
			taxonSets.go();

		//
		// Help -> About. We should put something
		// up here, once we get proper documentation
		// working in the Help -> * menu.
		//
		if(cmd.equals("About SequenceMatrix")) {
			String copyrightNotice = new String(
					"You may cite this program as follows:\n\t" + getCitation() + "\n---\n" +
					getName() + ", Copyright (C) 2006-07 Gaurav Vaidya. \nSequenceMatrix comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it under certain conditions; check the COPYING file you should have recieved along with this package.\n\n");
					
			MessageBox mb = new MessageBox(mainFrame, "About this program", copyrightNotice 
					+ "Written by Gaurav Vaidya\nIf I had time to put something interesting here, there'd be something in the help menu too. All apologies.\n\n"
					+ "This program was written with Vim (http://vim.org) with occasional help from Eclipse (http://eclipse.org/). Compilation was handled by Ant (http://ant.apache.org/)." 
			);
			mb.showMessageBox();
		}		

		//
		// END OF MAIN MENU
		//

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
	 * If somebody tries to close the window, call our local exit()
	 * to shut things down. 
	 */
	public void windowClosing(WindowEvent e) {
            System.exit(0);
	}

	//
	// The following functions handle drag and drop.
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
			dtde.dropComplete(true);		// so long and thanks for all the fish!

			Iterator i = list.iterator();
			while(i.hasNext()) {
				File file = 	(File) i.next();

				fileManager.addFile(file);
			}
		} else {
			dtde.rejectDrop();
		}
	}
	public void dropActionChanged(DropTargetDragEvent dtde) {}

	/** This really ought to be a static variable in the itemStateChanged function. Ah, well. */
	private CheckboxMenuItem last_chmi = null;
	/**
	 * An item listener, used for the sort sub-menu. 
	 */
	public void	itemStateChanged(ItemEvent e) {
		CheckboxMenuItem chmi = (CheckboxMenuItem) e.getSource();

		// 
		// HANDLER CODE FOR CHECKBOX ITEMS IN THE MENU (STUPID SUN BUG #4024569)
		//
		/*
		if(chmi.getLabel().equals("Display pairwise distances")) {
			// note: this is actually the NEW state
			if(chmi.getState() == true) {
				tableManager.changeDisplayMode(TableManager.DISPLAY_DISTANCES);
				chmi.setState(true);
			} else {
				tableManager.changeDisplayMode(TableManager.DISPLAY_SEQUENCES);
				chmi.setState(false);
			}

			return;
		}
		*/

		// 
		// HANDLER CODE FOR THE "SORT BY ..." MENU
		// Note that the following code is pretty badly
		// tuned specifically for SORT BY. If you're not
		// using sort by, you really should have returned
		// by this point.
		//
		String label = chmi.getLabel();
		if(label.equals("As sequences"))
			switchView(TableManager.DISPLAY_SEQUENCES);

		if(label.equals("As pairwise distances"))
			switchView(TableManager.DISPLAY_DISTANCES);

		if(label.equals("As correlations"))
			switchView(TableManager.DISPLAY_CORRELATIONS);
	}

	public void switchView(int mode) {
		switchView(mode, null);
	}
	
	public void switchView(int mode, String arg) {
		if(last_chmi != null)
			last_chmi.setState(false);

		tableManager.changeDisplayMode(mode, arg);
			
		switch(mode) {
			default:
			case TableManager.DISPLAY_SEQUENCES:
				chmi_displaySequences.setState(true);	
				last_chmi = chmi_displaySequences;
				break;

			case TableManager.DISPLAY_DISTANCES:
				chmi_displayDistances.setState(true);
				last_chmi = chmi_displayDistances;
				break;

			case TableManager.DISPLAY_CORRELATIONS:
				chmi_displayCorrelations.setState(true);
				last_chmi = chmi_displayCorrelations;
				break;
		}
	}

//
//	5.	FUNCTIONAL CODE. It does things.
//
//		Mostly, it's used by us to call bits of code to 'do' things: exit SequenceMatrix,
//		exportAsNexus, etc. Public functions can be called by other components to do
//		things, such as poke a sequence straight onto the screen.

	/**
	 * Creates the user interface of SequenceMatrix.
	 */
	private void createUI() {
		tableManager = new TableManager(this, mainTable);
		mainTable.setModel(tableManager.getTableModel());
		tableManager.updateDisplay();
	}
	
	/**
	 * Resets the title of the main frame. This is from the old SpeciesIdentifier
	 * code, but it doesn't actually do anything much any more. Maybe we'll 
	 * eventually tie this into FileManager or DataStore and put up the number
	 * of charsets or something, but for now, we really don't need that. At all.
	 */
	private void resetFrameTitle() {
		mainFrame.setTitle(getName());
	}
	
	/**
	 * Returns the name of this program, i.e. "SequenceMatrix" with appropriate versioning information.
	 */
	public String getName() {
		return "TaxonDNA/SequenceMatrix " + Versions.getTaxonDNA();
	}
}
