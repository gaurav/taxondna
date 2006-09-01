/**
 * TaxonDNA is a program for interacting with DNA sequences 
 * in a taxonomy-intelligent way. Once finished (sometime before 
 * the heat death of the universe, we hope), this program
 * will deal with DNA sequences, use them to help construct 
 * taxonomies, identify species, and do a whole load of other 
 * cool things we haven't even thought about yet.
 *
 * How cool is that?
 * Very cool.
 *
 */

/*
 *
 *  TaxonDNA
 *  Copyright (C) 2005 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.Modules;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class TaxonDNA implements WindowListener, ActionListener, ItemListener {
	// the current TaxonDNA version number 
	private static String 	version 		= "0.9.4.4";

	// the following is information specific to this particular TaxonDNA.
	private SequenceList 	sequences		= null;
	private boolean		modified		= false;
	
	// the following variables create and track our AWT interface
	private Frame		mainFrame 		= new Frame();

	// This is the 'left hand side' of our screen - the action list,
	// the sequence list, and a label with a count of the number of
	// sequences.
	private java.awt.List	list_actions 		= new java.awt.List(8, false);	
	private SequencePanel	list_sequences 		= new SequencePanel(this);
	private Label		label_sequencecount 	= new Label();

	// The 'cardLayout' will be used to 'flip' the various UIExtensions
	// in and out of the mainView.
	private CardLayout	cardLayout 		= new CardLayout();
	private Panel		mainView 		= new Panel();
	private Label		label_moduleTitle	= new Label();
	private TextArea	ta_moduleInfo		= new TextArea();

	// all TaxonDNAs have a list of uiExtensions which they display, and
	// this is where they're saved. There's a chance that eventually,
	// we'll have customization of some sort on this (i.e. TaxonDNA's with
	// their own set of uiExtensions, depending on what task they have
	// to perform for the TaxonDNA which spawned them). Until then,
	// this code just can just sit around here. 
	private Vector		uiExtensions 		= new Vector();

//
//	0.	STATIC FUNCTIONS. Handles the TaxonDNA.Messages stuff.
//	
	public static String getMessage(int code) {
		return Messages.getMessage(code);
	}

	public static String getMessage(int code, Object arg) {
		return Messages.getMessage(code, arg);
	}
	
	public static String getMessage(int code, Object arg, Object arg2) {
		return Messages.getMessage(code, arg, arg2);
	}
	
//
//	1.	ENTRYPOINT. The entrypoint is where the entire TaxonDNA system starts up.
//		Right now, it only loads sequence files; depending on need, we might have some kind
//		of command line arguments eventually. Note that only the *application*
//		should enter from here; a new TaxonDNA window will be spawned entirely
//		by using 'new TaxonDNA(file)'. 
//
	/**
	 * TaxonDNA's main entrypoint. We check for command line arguments (in this case, all
	 * of which must be files) and we start them using a call to 'new TaxonDNA(file)'. 
	 */
	public static void main(String[] args) {
		if(args.length == 0)
			new TaxonDNA();
		else
			CommandLine.processCommandLine(args);
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
	public TaxonDNA() {
		lockSequenceList();
		registerUIExtensions();		// register the required UIExtensions
		createUI();			// create our user interface
		unlockSequenceList(null);
	}

	/**
	 * Create a new TaxonDNA object. Each TaxonDNA is essentially
	 * a class to deal with a particular SequenceList. We will
	 * figure out the name of the class, etc. from the SequenceList.
	 * The SequenceList can be 'null'.
	 *
	 * @param list The SequenceList to display in this TaxonDNA
	 */
	public TaxonDNA(SequenceList list) {
		this();

		if(list != null) {
			lockSequenceList();
			unlockSequenceList(list);
		}
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

			if(fd.getFile() != null)
				loadFile(new File(fd.getDirectory() + fd.getFile()));
		}

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
		
		//
		// File -> Exit. Close the present file,
		// then dispose of the main window.
		//
		if(cmd.equals("Exit"))
			exitTaxonDNA();

		// Modules -> *
		//
		if((cmd.length() > 7) && (cmd.substring(0, 7).equals("Module_"))) {
			String uiExtName = cmd.substring(7);

			goToExtension(uiExtName);
		}

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
		
		//
		// Help -> Citing TaxonDNA. Gives information
		// for citing TaxonDNA
		//
		if(cmd.equals("Citing TaxonDNA")) {
			MessageBox mb = new MessageBox(mainFrame,
					"Citing TaxonDNA",
					"You should cite TaxonDNA as follows:\n\t" + getCitation()
			);

			mb.go();
		}

		//
		// Help -> About. We should put something
		// up here, once we get proper documentation
		// working in the Help -> * menu.
		//
		if(cmd.equals("About TaxonDNA")) {
			String copyrightNotice = new String("TaxonDNA " + version + ", Copyright (C) 2005 Gaurav Vaidya. \nTaxonDNA comes with ABSOLUTELY NO WARRANTY. This is free software, and you are welcome to redistribute it under certain conditions; check the COPYING file you should have recieved along with this package.\n\n");
					
			MessageBox mb = new MessageBox(mainFrame, "About this program", copyrightNotice 
					+ "Written by Gaurav Vaidya\nIf I had time to put something interesting here, there'd be something in the help menu too. All apologies.\n\n"
					+ "Thanks to Guanyang and Prof Rudolf Meier for extensively testing.\n\n"
					+ "Thanks to Kathy for single-handedly figuring out the reason for the 'Cluster' bug, now fixed!\n\n"
					+ "This program was written with Vim (http://vim.org), with some refactoring assistance from Eclipse (http://eclipse.org/). Compilation was handled by Ant (http://ant.apache.org/). I use BrowserLauncher (http://browserlauncher.sf.net/) to handle opening a website in a cross-platform way."
			);
			mb.go();
		}
	}

	/**
	 * We monitor the list of possible actions,
	 * (from the list_actions, in the upper left hand corner?)
	 * and handle clicks on that list (against the UIExtensions)
	 */
	public void itemStateChanged(ItemEvent e) {
		if(e.getSource().equals(list_actions)) {
			int item = ((Integer)e.getItem()).intValue();

			if(item > -1) {
				goToExtension(((UIExtension) uiExtensions.get(item)).getShortName());
			}
		}
	}	
	
	/**
	 * If somebody tries to close the window, call closeFile() before closing the Window
	 */
	public void windowClosing(WindowEvent e) {
		exitTaxonDNA();
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

	/**
	 * Saves the current file.
	 *
	 * @return true, if the file has been successfully saved. 
	 */
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
		lockSequenceList();
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

		} catch(SequenceListException e) {
			MessageBox mb = new MessageBox(getFrame(), "Could not read file " + file + "!", e.toString());
			mb.go();

			unlockSequenceList();
			return false;
			
		} catch(DelayAbortedException e) {
			unlockSequenceList();
			return false;
			
		}
		
		// so now that we've LOADED up the sequences, let's close the file
		// (so if the "load" failed, we would have returned without calling
		// closeFile at all!)
		closeFile();
		unlockSequenceList(sequences);
		modified = false;
		resetFrameTitle();
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
	}

	public void exitTaxonDNA() {
		lockSequenceList();
		closeFile();
		// tricky case: if the sequences != null here,
		// that means the user canceled out of closeFile.
		if(lockSequenceList() != null) {
			unlockSequenceList();
			unlockSequenceList();
			return;
		} else {
			mainFrame.dispose();
			unlockSequenceList();
		}	

		// A simple, if elegant solution to the entire
		// 'TaxonDNA won't close because a thread is
		// still running somewhere'. Note that this
		// will NOT solve all the other thread related
		// issues will have, although it will save
		// the user *some* angst.
		System.exit(0);
	}

//
//	5.	Sequence locks/unlocks
//
	/**
	 * Locks and returns a copy of the SequenceList. This function will block until all
	 * previous locks on the SequenceList are cleared. 
	 */
	public SequenceList	lockSequenceList() {
		if(sequences != null)
			sequences.lock();
		return sequences;
	}

	/**
	 * Unlock sequence list. Data MIGHT have been changed internally, so to be on the safe
	 * side, we resetFrameTitle() -- this would be unnecessary if the number of sequences
	 * weren't in the frame title.
	 */
	public void unlockSequenceList() {
		if(sequences != null)
			sequences.unlock();
		resetFrameTitle();
	}

	/**
	 * Unlock sequence list, changing the ENTIRE underlying list as we unlock it.
	 * Remember: if you want to change entries, please use methods on SequenceList.
	 * This function is STRICTLY for when you want to (and the user is expecting)
	 * having the ENTIRE SequenceList changed from under his feet. Technically,
	 * saveFile() should be the only who does this.
	 *
	 * To reiterate: TaxonDNA will respond to this, badly. Everything will flip
	 * around. Things will change. It could be MINUTES, even HOURS before this
	 * function returns. Don't do this unless you KNOW what you're doing.
	 *
	 */
	public void unlockSequenceList(SequenceList list) {
		if(list != null) {
			if(sequences != null)
				sequences.unlock();
			sequences = list;
			modified = true;
			// We don't need to unlock 'list' since list was never locked by us.
			// Since list is in all likelihood only used by one thread, it
			// probably doesn't even need locking. If it does, it's upto the
			// caller to lock/unlock it properly.
		} else {
			sequences = null;
			// don't need to unlock because ... who cares?
		}			
		
		// tell everybody we've changed
		list_sequences.dataChanged();
		
		Iterator i = uiExtensions.iterator();
		while(i.hasNext()) {
			UIExtension ext = (UIExtension) i.next();

			ext.dataChanged();
		}

		// and tell the user we've changed
		resetFrameTitle();
	}

//
//	6.	COMMANDS. This is things that other classes might call
//		to make things happen within/to TaxonDNA. 
//
	/**
	 * Switch to the extension mentioned by name. The extension will be selected in the main view.
	 */
	public void goToExtension(String extName) {
		int x = 0;
		Iterator i = uiExtensions.iterator();
		while(i.hasNext()) {
			UIExtension ex = (UIExtension) i.next();
			if(ex.getShortName().equals(extName)) {
				list_actions.select(x);
				cardLayout.show(mainView, extName);

				label_moduleTitle.setText(ex.getShortName());
				ta_moduleInfo.setText(ex.getDescription());

				return;
			}
			x++;
		}

		// if all else fails, display an error
		MessageBox.messageBox(mainFrame, "Extension not found!", "This part of this program needs to use component '" + extName + "', which does not exist.\n\nThis is most likely a programming error. Please contact the programmer.");
	}
	
//
//	5.	GETTERS. Used to retrive values/objects from TaxonDNA.
//	
	/**
	 * Returns the current Frame object.
	 */
	public Frame		getFrame() {
		return mainFrame;
	}
	
	/**
	 * Returns our current sequence panel. Anybody who needs to know, or play around with this
	 * (I'm looking at you, SequenceEdit) can just pick it up and talk directly to 
	 * SequencePanel.
	 */
	public SequencePanel getSequencePanel() {
		return list_sequences;
	}
	
	/**
	 * Retrieve the extension associated with the index (where 0 is the first extension loaded).
	 */
	public UIExtension getExtension(int i) {
		return (UIExtension) uiExtensions.get(i);
	}

	/**
	 * Retrieve the extension by name from the vector of loaded extensions. Since it returns a null
	 * if the extension doesn't exist, this can also be used to check if extensions have been loaded.
	 * It's all a bit nuts though, because the code will still need the class to be checked!
	 * Dependency hell, baby.
	 */
	public UIExtension getExtension(String name) {
		int x = 0;

		Iterator i = uiExtensions.iterator();
		while(i.hasNext()) {
			UIExtension ex = (UIExtension) i.next();
			if(ex.getShortName().equals(name)) {
				return ex;
			}
			x++;
		}

		return null;
	}	

	/**
	 *	Returns the citation for the program this code is found in.
	 */
	public String getCitation() {
		return "TODO: Insert citation here";
	}

//
//	X.	CONFIGURATION ETC.
//
	
	/**
	 * Add UIExtensions to be loaded in this function.
	 */
	private void registerUIExtensions() {
		// remember: the first handler to be added will be the first one open when stuff changes
		uiExtensions.add((UIExtension)new Configuration(this));
		uiExtensions.add((UIExtension)new SequenceEdit(this));
		uiExtensions.add((UIExtension)new QuerySequence(this));
		uiExtensions.add((UIExtension)new SpeciesSummary(this));
		uiExtensions.add((UIExtension)new PairwiseSummary(this));
		uiExtensions.add((UIExtension)new AllPairwiseDistances(this));
		uiExtensions.add((UIExtension)new BestMatch(this));
		uiExtensions.add((UIExtension)new BlockAnalysis(this));
		uiExtensions.add((UIExtension)new Cluster(this));
		uiExtensions.add((UIExtension)new CompleteOverlap(this));
		uiExtensions.add((UIExtension)new BarcodeGenerator(this));
		uiExtensions.add((UIExtension)new Exporter(this));
//		uiExtensions.add((UIExtension)new Randomizer(this));
		uiExtensions.add((UIExtension)new AlignmentHelperPlugin(this));
		uiExtensions.add((UIExtension)new Tester(this));
		uiExtensions.add((UIExtension)new SystemUsage(this));
//		uiExtensions.add((UIExtension)new CombineDatasets(this));
//		uiExtensions.add((UIExtension)new GenerateSubSet(this));
//		uiExtensions.add((UIExtension)new EditDataSet(this));

		// Hidden extensions
		new CDSExaminer(this); 
	}

//
//	4. 	PRIVATE FUNCTIONS, used by TaxonDNA to do stuff.
//		Unless you're meddling with TaxonDNA itself, you
//		should never need to look below this line.
//
	/**
	 * Creates the user interface of TaxonDNA.
	 */
	private void createUI() {
		// main frame
		mainFrame = new Frame("TaxonDNA");
		mainFrame.addWindowListener(this);
		mainFrame.setLayout(new BorderLayout());
		mainFrame.setMenuBar(createMenuBar());
		mainFrame.setBackground(SystemColor.control);

		// Sidebar with sequence names, 
		Panel 	combined = 	new Panel();
		combined.setLayout(new BorderLayout());

		// Action list in the upper part of the sidebar
		Panel	actionSelect =	new Panel();
		actionSelect.setLayout(new BorderLayout());
		actionSelect.add(new Label("Actions and Views"), BorderLayout.NORTH);
		
		list_actions.addItemListener(this);
		actionSelect.add(list_actions);

		combined.add(actionSelect, BorderLayout.NORTH);

		// Sequences list in the sidebar
		Panel	sequences = 	new Panel();
		sequences.setLayout(new BorderLayout());
		sequences.add(new Label("Sequences"), BorderLayout.NORTH);
		sequences.add(label_sequencecount, BorderLayout.SOUTH);	
		list_sequences.addItemListener(this);
		sequences.add(list_sequences);
		
		combined.add(sequences);
		
		// finally, add the sidebar into the main frame
		mainFrame.add(combined, BorderLayout.WEST);

		// Set up the mainView, where the panels are swapped about
		mainView.setLayout(cardLayout);
		Iterator i = uiExtensions.iterator();
		while(i.hasNext()) {
			UIExtension ext = (UIExtension) i.next();

			list_actions.add(ext.getShortName());
			mainView.add(ext.getPanel(), (Object)ext.getShortName());
		}
		cardLayout.first(mainView);

		// Put the mainView into a Panel (the rightPanel) which also provides
		// information on the currently selected 'action'
		label_moduleTitle = new Label("Module");
		label_moduleTitle.setFont(new Font("Serif", Font.PLAIN, 20));
		ta_moduleInfo = new TextArea("Module information", 2, 80, TextArea.SCROLLBARS_NONE);
		ta_moduleInfo.setEditable(false);

		Panel info = new Panel();
		info.setLayout(new BorderLayout());
		info.add(label_moduleTitle, BorderLayout.NORTH);
		info.add(ta_moduleInfo);

		Panel rightLayout = new Panel();
		rightLayout.setLayout(new BorderLayout());
		rightLayout.add(info, BorderLayout.NORTH);
		rightLayout.add(mainView);
		
		mainFrame.add(rightLayout);

		// Start the mainFrame!
		goToExtension(((UIExtension)uiExtensions.get(0)).getShortName());
		mainFrame.pack();
		mainFrame.setVisible(true);
	}
	
	/**
	 * Resets the title of the main frame. You don't need to know this: you can safely
	 * unlockSequenceList(sequences) and this will be handled.
	 */
	private void resetFrameTitle() {
		StringBuffer title = new StringBuffer("TaxonDNA " + version);

		if(sequences != null) {
			File file = sequences.getFile();
		
			if(file != null)
				title.append(": " + file.getAbsolutePath());

			if(sequences.count() > 0)
				title.append(" (" + sequences.count() + " sequences)");
		
			if(modified)
				title.append(" (modified)");
		}

		mainFrame.setTitle(title.toString());

		if(sequences == null) {
			label_sequencecount.setText("No sequences loaded");
			mainFrame.getMenuBar().getMenu(2).setEnabled(false);	// turn off 'export as' menu
		} else {
			label_sequencecount.setText(sequences.count() + " sequences");
			mainFrame.getMenuBar().getMenu(2).setEnabled(true);	// turn on 'export as' menu
		}
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
		file.addSeparator();
		file.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
		file.add(new MenuItem("Save As", null));
				// new MenuShortcut(KeyEvent.VK_V))); --> Gets in the way of Ctrl-C Ctrl-V
				// new MenuShortcut(KeyEvent.VK_A)) --> deleting the shortcut since it gets in the way of Ctrl-A on Windows
		file.addSeparator();
		file.add(new MenuItem("Exit", new MenuShortcut(KeyEvent.VK_X)));
		file.addActionListener(this);
		menubar.add(file);
		
		// Modules menu
		Menu	modules	=	new Menu("Modules");

		i = uiExtensions.iterator();	
		count = 0;
		while(i.hasNext()) {
			UIExtension ui = (UIExtension) i.next();
			
			MenuItem menuItem = new MenuItem(ui.getShortName() + ": " + ui.getDescription());
			menuItem.setActionCommand("Module_" + ui.getShortName());
			menuItem.addActionListener(this);
			modules.add(menuItem);

			count++;
		}
		
		menubar.add(modules);


		// Import from menu
		Menu	importMenu	=	new Menu("Import");

		i = SequenceList.getFormatHandlers().iterator();	
		count = 0;
		while(i.hasNext()) {
			FormatHandler handler = (FormatHandler) i.next();
			MenuItem menuItem = new MenuItem(handler.getShortName() + ": " + handler.getFullName());
			menuItem.setActionCommand("Import_" + count);
			menuItem.addActionListener(this);
			importMenu.add(menuItem);

			count++;
		}
		
		menubar.add(importMenu);		

		// Export to menu
		Menu	export		=	new Menu("Export");

		i = SequenceList.getFormatHandlers().iterator();	
		count = 0;
		while(i.hasNext()) {
			FormatHandler handler = (FormatHandler) i.next();
			MenuItem menuItem = new MenuItem(handler.getShortName() + ": " + handler.getFullName());
			menuItem.setActionCommand("Export_" + count);
			menuItem.addActionListener(this);
			export.add(menuItem);

			count++;
		}
		
		menubar.add(export);

		// Commands
		Menu		commands	=	new Menu("Commands");
		
		// Iterate through all the UIExtensions, checking to see
		// if anybody needs to add commands to the menu.
		i = uiExtensions.iterator();
		while(i.hasNext()) {
			UIExtension ext = (UIExtension) i.next();
			ext.addCommandsToMenu(commands);
			// implement the add-separator thing in another way ... or not at all ...
		}
		menubar.add(commands);
		
		// Help menu
		Menu	help		=	new Menu("Help");
		help.add("Citing TaxonDNA");
		help.add("About TaxonDNA");
		help.addActionListener(this);
		menubar.add(help);
		menubar.setHelpMenu(help);

		return menubar;
	}
}
