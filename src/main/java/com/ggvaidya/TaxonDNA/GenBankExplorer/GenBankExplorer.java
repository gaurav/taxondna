/**
 * GenBankExplorer lets you open and 'look through' GenBank-format files, extract certain features
 * (or all of 'em), and anything else which comes up.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
 *
 *  TaxonDNA
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

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;
import java.awt.*;
import java.awt.datatransfer.*; // for drag-n-drop
import java.awt.dnd.*; // for drag-n-drop
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;

public class GenBankExplorer implements ActionListener, ItemListener, DropTargetListener {
    // Shared between all GBXs
    private static int count_genBankExplorers = 0;

    // Subcomponents
    private Preferences prefs = new Preferences(this);
    private ViewManager viewManager = new ViewManager(this);
    private Exporter exporter = new Exporter(this);
    private ClustalMapping clustalMapping = new ClustalMapping(this);

    // The following variables create and track our user interface
    private JFrame mainFrame = new JFrame(); // A frame

    private JToolBar toolBar = new JToolBar();

    private JPanel statusBar = new JPanel();
    private JTextField tf_status = new JTextField("Please wait, loading ...");

    //
    //	1.	ENTRYPOINT. The entrypoint is where the entire GenBankExplorer system starts up.
    //		As usual, you can just create a new GenBankExplorer to 'do everything'.
    //
    /**
     * GenBankExplorer's entry point. Our argument is the file to open initially. Dunno if we should
     * support command line options or what.
     */
    public static void main(String[] args) {
        if (args.length == 0) // none?
        new GenBankExplorer(); //	okay
        else
            for (int x = 0; x < args.length; x++) {
                new GenBankExplorer(new File(args[x])); // dirty little shortcut
            }
    }

    //
    // 	2.	CONSTRUCTORS.
    //
    /** Creates a new GenBankExplorer object, without any initial file. */
    public GenBankExplorer() {
        count_genBankExplorers++;
        createUI();

        resetFrameTitle();
    }

    /**
     * Creates a new GenBankExplorer object. We create a new toplevel. Be warned that we do NOT
     * spawn a thread: if TaxonDNA wants to create a GenBankExplorer for whatever reason, it will
     * have to do this IN a thread - otherwise when TaxonDNA exits, we will automatically exit as
     * well.
     */
    public GenBankExplorer(File file) {
        count_genBankExplorers++;
        createUI(); // create our user interface

        // now load up this file
        viewManager.loadFile(file);

        resetFrameTitle();
    }

    //
    // GETTERS: Returns values of possible interest, etc.
    //
    /** Returns the current Frame object. */
    public JFrame getFrame() {
        return mainFrame;
    }

    /** Returns the Preferences object (so you can see what the user wants). */
    public Preferences getPrefs() {
        return prefs;
    }

    /** Returns the ViewManager */
    public ViewManager getViewManager() {
        return viewManager;
    }

    /** Returns the citation used by GenBankExplorer. */
    public String getCitation() {
        return "Meier, R., Kwong, S., Vaidya, G., Ng, Peter K. L. DNA Barcoding and Taxonomy in"
                + " Diptera: a Tale of High Intraspecific Variability and Low Identification"
                + " Success. Systematic Biology, 55: 715-728.";
    }

    //
    // 	4.	EVENT PROCESSING. Handles stuff which happens to the main frame, mostly.
    // 		This involves menu, window and drop listening.
    //
    /** Helper function; retrieves a file name from the user and displays it. */
    public File getFileToLoad(String title) {
        FileDialog fd = new FileDialog(mainFrame, title, FileDialog.LOAD);
        fd.setVisible(true); // go!

        File f = null;
        if (fd.getFile() != null) {
            String fileName = fd.getFile();
            if (fd.getDirectory() != null) {
                fileName = fd.getDirectory() + fileName;
            }
            f = new File(fileName);
        }

        return f;
    }

    /** Helper function; retrieves a file name from the user and displays it. */
    public File getFileToSave(String title) {
        FileDialog fd = new FileDialog(mainFrame, title, FileDialog.SAVE);
        fd.setVisible(true); // go!

        File f = null;
        if (fd.getFile() != null) {
            String fileName = fd.getFile();
            if (fd.getDirectory() != null) {
                fileName = fd.getDirectory() + fileName;
            }
            f = new File(fileName);
        }

        return f;
    }

    public boolean save() {
        GenBankFile gbf = viewManager.getGenBankFile();
        if (gbf != null) {
            File f = gbf.getFile();

            try {
                PrintWriter pw = new PrintWriter(new FileWriter(f));

                ProgressDialog pd =
                        ProgressDialog.create(
                                mainFrame,
                                "Please wait, exporting file ...",
                                "I'm exporting the current file to '"
                                        + f.getAbsolutePath()
                                        + "'. Sorry for making you wait!");
                gbf.writeAsGenBank(pw, pd);
                pw.close();

                MessageBox mb =
                        new MessageBox(
                                mainFrame,
                                "File written successfully!",
                                "The file '" + f.getAbsolutePath() + "' was written successfully!");
                mb.go();

                return true;

            } catch (DelayAbortedException e) {
                return false;

            } catch (IOException e) {
                MessageBox mb =
                        new MessageBox(
                                mainFrame,
                                "Sorry, couldn't write file!",
                                "I couldn't write to '"
                                        + f.getAbsolutePath()
                                        + "'. Are you sure you have the necessary permissions, and"
                                        + " that the disk isn't full?",
                                MessageBox.MB_ERROR);
                mb.go();

                return false;
            }
        }

        return false;
    }

    /** Handles ActionEvents for the file menu and help menu. */
    public void actionPerformed(ActionEvent evt) {
        String cmd = evt.getActionCommand();

        // File -> New. Clear the current file,
        // and all view information with it.
        if (cmd.equals("New")) {
            viewManager.clear();
            return;
        }

        // File -> Open. Clears the current file,
        // then tries to open a new one.
        if (cmd.equals("Open")) {
            File f = getFileToLoad("Please select a GenBank file to load ...");
            viewManager.loadFile(f);
            return;
        }

        // File -> Save. Saves the current file
        // under its pre-existing name.
        if (cmd.equals("Save")) {
            save();
        }

        if (cmd.equals("Save As")) {
            File f_old = viewManager.getGenBankFile().getFile();
            File f = getFileToSave("Where would you like to save this file?");
            if (f != null) {
                GenBankFile gbf = viewManager.getGenBankFile();
                gbf.setFile(f);
                if (save()) viewManager.updateFileInfo();
                else gbf.setFile(f_old);
            }
        }

        // Export -> Export all features as multiple FASTA files
        //
        if (cmd.equals("Export all features as multiple FASTA files (no minimum)")) {
            exporter.exportMultipleFasta(0);
        }

        // Export -> Export all ... multiple ... 5
        if (cmd.equals("Export all features as multiple FASTA files (minimum 5 species)")) {
            exporter.exportMultipleFasta(5);
        }

        // Export -> Export all ... multiple ... 10
        if (cmd.equals("Export all features as multiple FASTA files (minimum 10 species)")) {
            exporter.exportMultipleFasta(10);
        }

        // Export -> Export selected features as FASTA.
        //
        if (cmd.equals("Export selected features as FASTA")) {
            exporter.exportAsFasta(viewManager.getSelectedContainers());
        }

        // Export -> Export selected features as Clustal-compatible FASTA.
        if (cmd.equals("Export selected features as Clustal-compatible FASTA")) {
            clustalMapping.go();
        }

        // Help -> About. We should put something
        // up here, once we get proper documentation
        // working in the Help -> * menu.
        //
        if (cmd.equals("About GenBankExplorer")) {
            String copyrightNotice =
                    new String(
                            "You may cite this program as follows:\n\t"
                                    + getCitation()
                                    + "\n---\n"
                                    + getName()
                                    + ", Copyright (C) 2007 Gaurav Vaidya. \n"
                                    + "GenBankExplorer comes with ABSOLUTELY NO WARRANTY. This is"
                                    + " free software, and you are welcome to redistribute it under"
                                    + " certain conditions; check the COPYING file you should have"
                                    + " recieved along with this package.\n\n");

            MessageBox mb =
                    new MessageBox(
                            mainFrame,
                            "About this program",
                            copyrightNotice
                                    + "Written by Gaurav Vaidya\n"
                                    + "If I had time to put something interesting here, there'd be"
                                    + " something in the help menu too. All apologies.\n\n"
                                    + "This program was written with Vim (http://vim.org) with"
                                    + " occasional help from Eclipse (http://eclipse.org/)."
                                    + " Compilation was handled by Ant (http://ant.apache.org/).");
            mb.showMessageBox();
        }

        //
        // END OF MAIN MENU
        //

    }

    //
    // The following functions handle drag and drop.
    //
    public void dragEnter(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            // A list of files!
            // Yummy!
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
        } else {
            dtde.rejectDrag();
        }
    }

    public void dragExit(DropTargetEvent dte) {}

    public void dragOver(DropTargetDragEvent dtde) {}

    public void drop(DropTargetDropEvent dtde) {
        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            // A list of files!
            // Yummy!
            dtde.acceptDrop(DnDConstants.ACTION_COPY);

            Transferable tf = dtde.getTransferable();
            java.util.List list = null;
            try {
                list = (java.util.List) tf.getTransferData(DataFlavor.javaFileListFlavor);
            } catch (UnsupportedFlavorException e) {
                dtde.rejectDrop();
                return;
            } catch (IOException e) {
                dtde.rejectDrop();
                return;
            }
            dtde.dropComplete(true); // so long and thanks for all the fish!

            Iterator i = list.iterator();
            while (i.hasNext()) {
                File file = (File) i.next();

                new GenBankExplorer(file); // uh-oh
            }
        } else {
            dtde.rejectDrop();
        }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {}

    /** This really ought to be a static variable in the itemStateChanged function. Ah, well. */
    private CheckboxMenuItem last_chmi = null;
    /** An item listener, used for the sort sub-menu. */
    public void itemStateChanged(ItemEvent e) {
        CheckboxMenuItem chmi = (CheckboxMenuItem) e.getSource();

        //
        // HANDLER CODE FOR CHECKBOX ITEMS IN THE MENU (STUPID SUN BUG #4024569)
        //
        /*
        if(chmi.getLabel().equals("Display pairwise distances")) {
        	// note: this is actually the NEW state
        	if(chmi.getState() == true) {
        		tableManager.changeDisplayMode(ViewManager.DISPLAY_DISTANCES);
        		chmi.setState(true);
        	} else {
        		tableManager.changeDisplayMode(ViewManager.DISPLAY_SEQUENCES);
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
        if (last_chmi != null) last_chmi.setState(false);

        String label = chmi.getLabel();
        if (label.equals("By loci")) viewManager.switchDisplayMode(ViewManager.DM_LOCI);

        if (label.equals("By gene")) viewManager.switchDisplayMode(ViewManager.DM_FEATURES);

        chmi.setState(true);
        last_chmi = chmi;

        /*
        String label = chmi.getLabel();
        if(label.equals("By name"))
        	tableManager.resort(DataStore.SORT_BYNAME);

        if(label.equals("By species epithet"))
        	tableManager.resort(DataStore.SORT_BYSECONDNAME);

        if(label.equals("By number of character sets"))
        	tableManager.resort(DataStore.SORT_BYCHARSETS);

        if(label.equals("By total length"))
        	tableManager.resort(DataStore.SORT_BYTOTALLENGTH);
        */
    }

    public void exit() {
        count_genBankExplorers--;
        if (count_genBankExplorers <= 0) {
            viewManager.clear();
            mainFrame.dispose();
            System.exit(0);
        }
    }

    //
    //	5.	FUNCTIONAL CODE. It does things.
    //
    //		Mostly, it's used by us to call bits of code to 'do' things: exit GenBankExplorer,
    //		exportAsNexus, etc. Public functions can be called by other components to do
    //		things, such as poke a sequence straight onto the screen.

    /** Creates the user interface of GenBankExplorer. */
    private void createUI() {
        // main frame
        mainFrame = new JFrame("GenBankExplorer");
        mainFrame.addWindowListener(
                new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        GenBankExplorer.this.exit(); // omfg
                    }
                });
        mainFrame.setMenuBar(createMenuBar());
        mainFrame.setBackground(SystemColor.control);

        // set us up the viewManager
        mainFrame.getContentPane().setLayout(new BorderLayout());
        mainFrame.getContentPane().add(viewManager.getPanel());

        // jtoolbar
        // toolBar = n;
        mainFrame.getContentPane().add(toolBar, BorderLayout.NORTH);

        // status bar
        //		statusBar = tableManager.getStatusBar();
        tf_status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tf_status.setEditable(false);
        statusBar.setLayout(new BorderLayout());
        statusBar.add(tf_status);
        mainFrame.getContentPane().add(statusBar, BorderLayout.SOUTH);

        // Start the mainFrame!
        mainFrame.pack();
        mainFrame.setVisible(true);

        // set up the DropTarget
        // Warning: this will need to be changed if table is ever not
        // completely covering our client area
        DropTarget dropTarget = new DropTarget(mainFrame, this);
        dropTarget.setActive(true);

        updateStatusBar("Welcome to GenBankExplorer!");
    }

    /**
     * Resets the title of the main frame. This is from the old SpeciesIdentifier code, but it
     * doesn't actually do anything much any more. Maybe we'll eventually tie this into FileManager
     * or DataStore and put up the number of charsets or something, but for now, we really don't
     * need that. At all.
     */
    private void resetFrameTitle() {
        mainFrame.setTitle(getName());
    }

    /** Creates and returns the main menubar. */
    private MenuBar createMenuBar() {
        Iterator i;
        MenuBar menubar = new MenuBar();
        int count = 0;

        // File menu
        Menu file = new Menu("File");
        file.add(new MenuItem("New"));
        file.add(new MenuItem("Open"));
        file.addSeparator();
        file.add(new MenuItem("Save", new MenuShortcut(KeyEvent.VK_S)));
        file.add(new MenuItem("Save As", null));
        // new MenuShortcut(KeyEvent.VK_V))); --> Gets in the way of Ctrl-C Ctrl-V
        // new MenuShortcut(KeyEvent.VK_A)) --> deleting the shortcut since it gets in the way of
        // Ctrl-A
        // on Windows
        file.addSeparator();
        file.add(new MenuItem("Exit", new MenuShortcut(KeyEvent.VK_X)));
        file.addActionListener(this);
        menubar.add(file);

        // View menu
        Menu view = new Menu("View");

        // New view menu
        CheckboxMenuItem chmi = new CheckboxMenuItem("By loci", true);
        chmi.addItemListener(this);
        view.add(chmi);
        last_chmi = chmi;

        chmi = new CheckboxMenuItem("By gene", false);
        chmi.addItemListener(this);
        view.add(chmi);

        view.addActionListener(this);
        menubar.add(view);

        /*
        // Analysis menu
        Menu	analyses	= 	new Menu("Analyses");
        analyses.add(new MenuItem("Find all zero percent distances"));

        chmi = new CheckboxMenuItem("As pairwise distances", false);
        chmi.addItemListener(this);
        analyses.add(chmi);

        chmi = new CheckboxMenuItem("As correlations", false);
        chmi.addItemListener(this);
        analyses.add(chmi);

        analyses.addActionListener(this);
        menubar.add(analyses);
        */

        // Export menu
        Menu export = new Menu("Export");
        export.add(new MenuItem("Export all features as multiple FASTA files (no minimum)"));
        export.add(new MenuItem("Export all features as multiple FASTA files (minimum 5 species)"));
        export.add(
                new MenuItem("Export all features as multiple FASTA files (minimum 10 species)"));
        export.add(
                new MenuItem("Export selected features as FASTA", new MenuShortcut(KeyEvent.VK_F)));
        export.add(
                new MenuItem(
                        "Export selected features as Clustal-compatible FASTA",
                        new MenuShortcut(KeyEvent.VK_C)));
        export.addActionListener(this);
        menubar.add(export);

        /*
        // Settings menu
        Menu 	settings	=	new Menu("Settings");
        settings.add(new MenuItem("Taxonsets"));
        settings.addActionListener(this);
        menubar.add(settings);
        */

        // Help menu
        Menu help = new Menu("Help");
        help.add("About GenBankExplorer");
        help.addActionListener(this);
        menubar.add(help);
        menubar.setHelpMenu(help);

        return menubar;
    }

    /**
     * Returns the name of this program, i.e. "GenBankExplorer" with appropriate versioning
     * information.
     */
    public String getName() {
        return "TaxonDNA/GenBankExplorer " + Versions.getTaxonDNA();
    }

    public void updateStatusBar(String x) {
        tf_status.setText(x);
    }

    /** Returns a sequence list containing the current selection. */
    public SequenceList getSelectedSequenceList(DelayCallback delay)
            throws SequenceException, DelayAbortedException {
        java.util.List list = viewManager.getSelectedContainers();
        if (list == null || list.size() == 0) return null;

        return exporter.combineContainers(list, delay);
    }
}
