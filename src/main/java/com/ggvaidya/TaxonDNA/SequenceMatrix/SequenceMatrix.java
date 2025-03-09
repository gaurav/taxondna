/**
 * SequenceMatrix represents datasets in a matrix of sequences versus genes. This gives it an edge
 * against SpeciesIdentifier, which really sees datasets as a sequence-to-polypeptide map.
 * SequenceMatrix can therefore *really* import in GenBank and Nexus formats (it's import will, I
 * suppose, be something to behold). God only knows if it will 'live' within TaxonDNA forever: one
 * supposes that it'll eventually break free, but while a lot of the code (DNA.*, for a big
 * instance) is living together, it probably makes a lot of sense to keep them joined at the hip.
 *
 * <p>SequenceMatrix will, hopefully, be a much SMALLER program than TaxonDNA. We will also
 * experiment with Swing, just to see how that goes (and because JTable would save work like
 * nobody's business).
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
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

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.DNA.formats.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;
import java.awt.*;
import java.awt.datatransfer.*; // for drag-n-drop
import java.awt.dnd.*; // for drag-n-drop
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*; // "Come, thou Tortoise, when?"

public class SequenceMatrix
        implements WindowListener, ActionListener, ItemListener, DropTargetListener {
    // The following variables create and track our user interface
    private Frame mainFrame = new Frame(); // A frame
    private JTable mainTable = null; // with a table

    // Other components of SequenceMatrix
    // information
    //	private DataStore	dataStore		= new DataStore(this);	// the datastore: stores data
    private Preferences prefs = new Preferences(this); // preferences are stored here
    private Exporter exporter = new Exporter(this); // and the actual exports go thru exporter

    // managers
    private FileManager fileManager =
            new FileManager(this); // file manager: handles files coming in and outgoing
    private TableManager tableManager = null; // the table manager handles mid-level UI

    // additional functionality
    private Taxonsets taxonSets = new Taxonsets(this); // and taxonsets are set (on the UI) here

    // analyses
    public FindDistances findDistances = new FindDistances(this); // helps you find distances

    // CheckboxMenuItems corresponding to the possible 'views' the program can be in.
    private CheckboxMenuItem chmi_displaySequences = null;
    private CheckboxMenuItem chmi_displayDistances = null;
    private CheckboxMenuItem chmi_displayCorrelations = null;

    private JToolBar toolBar = null;
    private JPanel statusBar = null;

    //
    //	1.	ENTRYPOINT. The entrypoint is where the entire SequenceMatrix system starts up.
    //		As usual, you can just create a new SequenceMatrix to 'do everything'.
    //
    /** SequenceMatrix's entry point. Our arguments are the files we must open initially. */
    public static void main(String[] args) {
        new SequenceMatrix(Arrays.asList(args));
    }

    //
    // 	2.	CONSTRUCTORS.
    //
    /**
     * Creates a new SequenceMatrix object. We create a new toplevel. Be warned that we do NOT spawn
     * a thread: if TaxonDNA wants to create a SequenceMatrix for whatever reason, it will have to
     * do this IN a thread - otherwise when TaxonDNA exits, we will automatically exit as well.
     *
     * @param commands A list of commands to execute.
     */
    public SequenceMatrix(java.util.List cmds) {
        // Set up DNA.Sequence to use 0 bp as the min overlap.
        Sequence.setMinOverlap(0);

        createUI(); // create our user interface
        resetFrameTitle();

        if (cmds.size() > 0) {
            // Commands to run!
            Commands.executeCmdLine(this, cmds);
        }

        resetFrameTitle();
    }

    //
    // GETTERS: Returns values of possible interest, etc.
    //
    /** Returns the current Frame object. */
    public Frame getFrame() {
        return mainFrame;
    }

    /** Returns the Preferences object (so you can see what the user wants). */
    public Preferences getPrefs() {
        return prefs;
    }

    /** Returns the Exporter. */
    public Exporter getExporter() {
        return exporter;
    }

    /** Returns the DataStore object, should you want it. */
    //	public DataStore getDataStore() {
    //		return dataStore;
    //	}

    /** Returns the FileManager. */
    public FileManager getFileManager() {
        return fileManager;
    }

    /**
     * Returns the DataModel. Now, in Reality, this is just the dataStore object, suitable casted.
     * But since it's an abstraction, I figured, hey, why not.
     */
    //	public TableModel getTableModel() {
    //		return (TableModel) dataStore;
    //	}

    /** Returns the TableManager */
    public TableManager getTableManager() {
        return tableManager;
    }

    /** Returns the Table. */
    public JTable getJTable() {
        return mainTable;
    }

    /** Returns the Taxonsets component. */
    public Taxonsets getTaxonsets() {
        return taxonSets;
    }

    /** Returns the citation used by SequenceMatrix. */
    public String getCitation() {
        return "Vaidya, G., D. J. Lohman, R. Meier. SequenceMatrix: concatenation software for the"
                + " fast assembly of multigene datasets with character set and codon"
                + " information. Cladistics, accepted.\n"
                + "\tAccessible at: http://dx.doi.org/10.1111/j.1096-0031.2010.00329.x";
    }

    //
    // 	4.	EVENT PROCESSING. Handles stuff which happens to the main frame, mostly.
    // 		This involves menu, window and drop listening.
    //
    /** Handles ActionEvents for the file menu and help menu. */
    public void actionPerformed(ActionEvent evt) {
        String cmd = evt.getActionCommand();

        //
        // File -> New. Just close the present file.
        //
        if (cmd.equals("Clear all sequences")) tableManager.clear();

        //
        // File -> Open. Tries to load the file specified.
        // The current file is closed before anything
        // further is done.
        //
        if (cmd.equals("Add sequences")) fileManager.addFile();

        //
        // File -> Save. Effectively an 'export as #sequences'.
        //
        if (cmd.equals("Save")) fileManager.exportAsSequences();

        //
        // File -> Exit. Calls our exit() way out.
        //
        if (cmd.equals("Exit")) exit();

        // Analyses -> Zero Percent Distances
        if (cmd.equals("Find all zero percent distances")) {
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
        if ((cmd.length() > 7) && (cmd.substring(0, 7).equals("Import_"))) {
            int index = Integer.parseInt(cmd.substring(7));
            FormatHandler handler = (FormatHandler) SequenceList.getFormatHandlers().get(index);

            FileDialog fd =
                    new FileDialog(
                            getFrame(),
                            "Choose filename to import as " + handler.getShortName(),
                            FileDialog.LOAD);

            fd.setVisible(true);

            if (fd.getFile() != null) {
                String filename = fd.getFile();

                if (fd.getDirectory() != null) {
                    filename = fd.getDirectory() + filename;
                }
                fileManager.addFile(new File(filename), handler);
            }
        }

        // Export -> Export table as tab delimited.
        if (cmd.equals("Export table as tab-delimited")) fileManager.exportTableAsTabDelimited();

        // Export -> One file per column.
        if (cmd.equals("Split concatenated dataset into individual loci"))
            fileManager.exportSequencesByColumn();

        // Export -> Group columns randomly in groups of X
        if (cmd.equals("Export columns grouped randomly"))
            fileManager.exportSequencesByColumnsInGroups();

        //
        // Export -> Export as NEXUS.
        //
        if (cmd.equals("Export dataset in interleaved Nexus format (1000 bp)"))
            fileManager.quickExportAsNexus();

        if (cmd.equals("Export dataset in non-interleaved Nexus format"))
            fileManager.quickExportAsNexusNonInterleaved();

        if (cmd.equals("Export dataset in simplified \"naked\" Nexus format"))
            fileManager.quickExportAsNakedNexus();

        // Here if it's ever needed.
        // if(cmd.equals("Export sequences as NEXUS (advanced)"))
        //	fileManager.exportAsNexus();

        //
        // Export -> Export as TNT.
        //
        if (cmd.equals("Export dataset in TNT format")) fileManager.exportAsTNT();

        //
        // Export -> Export as PHYLIP
        if (cmd.equals("Export dataset in Phylip format")) fileManager.quickExportAsPhylip();

        //
        // Settings -> Taxonsets. Allows you to manipulate taxonsets.
        //
        if (cmd.equals("Taxonset settings")) taxonSets.go();

        //
        // Citation -> Citing SequenceMatrix
        //
        if (cmd.equals("Citing SequenceMatrix")) {
            MessageBox mb =
                    new MessageBox(
                            mainFrame,
                            "Citing SequenceMatrix",
                            "You may cite this program as follows:\n\t" + getCitation());
            mb.showMessageBox();
        }

        // Help -> About. We should put something
        // up here, once we get proper documentation
        // working in the Help -> * menu.
        //
        if (cmd.equals("About SequenceMatrix")) {
            String copyrightNotice =
                    new String(
                            "You may cite this program as follows:\n\t"
                                    + getCitation()
                                    + "\n---\n"
                                    + getName()
                                    + ", Copyright (C) 2006-07 Gaurav Vaidya. \n"
                                    + "SequenceMatrix comes with ABSOLUTELY NO WARRANTY. This is"
                                    + " free software, and you are welcome to redistribute it under"
                                    + " certain conditions; check the COPYING file you should have"
                                    + " recieved along with this package.\n");

            String memoryUsage = SequenceMatrix.summarizeMemory();

            MessageBox mb =
                    new MessageBox(
                            mainFrame,
                            "About this program",
                            copyrightNotice
                                    + "\n"
                                    + "\n---\n"
                                    + memoryUsage
                                    + "\n"
                                    + "---\n"
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

    private static String summarizeMemory() {
        StringBuffer buffer = new StringBuffer();
        Runtime runtime = Runtime.getRuntime();

        buffer.append(
                "Number of processors available: "
                        + runtime.availableProcessors()
                        + " processors\n");
        buffer.append("\n");
        buffer.append(
                "Maximum memory available for use:             "
                        + printMemory(runtime.maxMemory())
                        + "\n");
        buffer.append(
                "Total memory available for use at the moment: "
                        + printMemory(runtime.totalMemory())
                        + "\n");
        buffer.append(
                "Free memory available for use at the moment:  "
                        + printMemory(runtime.freeMemory())
                        + " ("
                        + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(
                                runtime.freeMemory(), runtime.totalMemory())
                        + "%)\n");

        long totalUsedMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalFreeMemory = runtime.maxMemory() - totalUsedMemory;
        buffer.append("\n");
        buffer.append(
                "Total memory used: "
                        + printMemory(totalUsedMemory)
                        + " ("
                        + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(
                                totalUsedMemory, runtime.maxMemory())
                        + "%)\n");
        buffer.append(
                "Total memory free: "
                        + printMemory(totalFreeMemory)
                        + " ("
                        + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(
                                totalFreeMemory, runtime.maxMemory())
                        + "%)\n");

        return buffer.toString();
    }

    private static String printMemory(long memory) {
        // return DNA.Settings.percentage(memory, 1024) + " KB, or " +
        // DNA.Settings.percentage(memory,
        // 1024 * 1024) + " MB";
        if (memory == Long.MAX_VALUE) return "No limit";

        return com.ggvaidya.TaxonDNA.Common.DNA.Settings.roundOff(memory / (1024 * 1024))
                + " MB\t("
                + com.ggvaidya.TaxonDNA.Common.DNA.Settings.roundOff(memory / 1024)
                + " KB)";
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

    /** If somebody tries to close the window, call our local exit() to shut things down. */
    public void windowClosing(WindowEvent e) {
        exit();
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

                fileManager.addFile(file);
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
        if (label.equals("As sequences")) switchView(TableManager.DISPLAY_SEQUENCES);

        if (label.equals("As pairwise distances")) switchView(TableManager.DISPLAY_DISTANCES);

        if (label.equals("As correlations")) switchView(TableManager.DISPLAY_CORRELATIONS);
    }

    public void switchView(int mode) {
        switchView(mode, null);
    }

    public void switchView(int mode, String arg) {
        if (last_chmi != null) last_chmi.setState(false);

        tableManager.changeDisplayMode(mode, arg);

        switch (mode) {
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

    /** Creates the user interface of SequenceMatrix. */
    private void createUI() {
        // main frame
        mainFrame = new Frame("SequenceMatrix");
        mainFrame.addWindowListener(this);
        mainFrame.setLayout(new BorderLayout());
        mainFrame.setMenuBar(createMenuBar());
        mainFrame.setBackground(SystemColor.control);

        // main table
        mainTable = new JTable();
        mainTable.setColumnSelectionAllowed(true); // why doesn't this work?
        mainTable.getTableHeader().setReorderingAllowed(false); // don't you dare!
        mainTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // ha-ha!

        tableManager = new TableManager(this, mainTable);
        mainTable.setModel(tableManager.getTableModel());
        tableManager.updateDisplay();

        // jtoolbar
        toolBar = tableManager.getToolbar();
        mainFrame.add(toolBar, BorderLayout.NORTH);

        // status bar
        statusBar = tableManager.getStatusBar();
        mainFrame.add(statusBar, BorderLayout.SOUTH);

        // put the maintable into a scroll pane
        JScrollPane scrollPane = new JScrollPane(mainTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        mainFrame.add(scrollPane);

        // Start the mainFrame!
        mainFrame.pack();
        mainFrame.setVisible(true);

        // set up the DropTarget
        // Warning: this will need to be changed if table is ever not
        // completely covering our client area
        DropTarget dropTarget = new DropTarget(mainFrame, this);
        dropTarget.setActive(true);

        DropTarget dropTargetTable = new DropTarget(mainTable, this);
        dropTargetTable.setActive(true);
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
        MenuBar menubar = new MenuBar();

        // File menu
        Menu file = new Menu("File");
        file.add(new MenuItem("Clear all sequences"));

        // file.add(new MenuItem("Save"));

        // Import submenu
        Menu imports = new Menu("Import");

        Iterator i = SequenceList.getFormatHandlers().iterator();
        int count = 0;
        while (i.hasNext()) {
            FormatHandler handler = (FormatHandler) i.next();
            MenuItem menuItem = new MenuItem(handler.getShortName() + ": " + handler.getFullName());
            menuItem.setActionCommand("Import_" + count);
            menuItem.addActionListener(this);
            imports.add(menuItem);
            count++;
        }

        imports.addActionListener(this);
        // Turn off imports menu.
        // file.add(imports);

        // And back to the File menu proper.
        file.add(new MenuItem("Exit", new MenuShortcut(KeyEvent.VK_X)));
        file.addActionListener(this);
        menubar.add(file);

        // Citation menu
        Menu citation = new Menu("Citation");
        citation.add("Citing SequenceMatrix");
        citation.addActionListener(this);
        menubar.add(citation);

        // Import menu
        Menu importMenu = new Menu("Import");
        importMenu.add(new MenuItem("Add sequences"));
        importMenu.addActionListener(this);
        menubar.add(importMenu);

        // Export menu
        Menu export = new Menu("Export");
        export.add(new MenuItem("Taxonset settings"));

        export.addSeparator();

        export.add(new MenuItem("Export dataset in TNT format"));
        export.add(
                new MenuItem(
                        "Export dataset in interleaved Nexus format (1000 bp)",
                        new MenuShortcut(KeyEvent.VK_N)));
        export.add(new MenuItem("Export dataset in non-interleaved Nexus format"));
        export.add(new MenuItem("Export dataset in simplified \"naked\" Nexus format"));
        export.add(new MenuItem("Export dataset in Phylip format"));

        export.addSeparator();

        export.add(new MenuItem("Split concatenated dataset into individual loci"));

        export.addSeparator();

        export.add(new MenuItem("Export table as tab-delimited"));

        // export.add(new MenuItem("Export columns grouped randomly"));

        // export.add(new MenuItem("Export sequences as NEXUS (advanced)"));
        export.addActionListener(this);
        menubar.add(export);

        // View menu
        Menu view = new Menu("View");

        // New view menu
        CheckboxMenuItem chmi = new CheckboxMenuItem("As sequences", true);
        chmi_displaySequences = chmi;
        chmi.addItemListener(this);
        view.add(chmi);
        last_chmi = chmi;

        chmi = new CheckboxMenuItem("As pairwise distances", false);
        chmi_displayDistances = chmi;
        chmi.addItemListener(this);
        view.add(chmi);

        // TODO: remove this if we need corelation mode back.
        /*
        		chmi = new CheckboxMenuItem("As correlations", false);
        		chmi_displayCorrelations = chmi;
        		chmi.addItemListener(this);

        		analyses.add(chmi);
        */
        view.addActionListener(this);
        menubar.add(view);

        // Help menu
        Menu help = new Menu("Help");
        help.add("About SequenceMatrix");
        help.addActionListener(this);
        menubar.add(help);
        menubar.setHelpMenu(help);

        return menubar;
    }

    /**
     * Clean up anything needing cleanup-ing, then kill this program with all the power of
     * System.exit(0).
     */
    private void exit() {
        tableManager.clear();
        mainFrame.dispose(); // this "closes" this window; whether or not this
        // terminates the application depends on whether
        // other stuff is running.
        System.exit(0); // until we run this command. *then*, it's dead, all right.
    }

    /**
     * Returns the name of this program, i.e. "SequenceMatrix" with appropriate versioning
     * information.
     */
    public String getName() {
        return "SequenceMatrix " + Versions.getTaxonDNA();
    }
}
