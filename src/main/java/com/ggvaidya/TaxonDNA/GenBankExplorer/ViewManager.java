/**
 * The View Manager translates what's in the data model (GenBankFile) into visible and processable
 * information on the screen. Most of the really good magic happens in GenBankFile, but a lot of the
 * grunt stuff happens here. That's a good thing. Really!
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

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.DNA.formats.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*; // "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;

public class ViewManager implements MouseListener {
    private GenBankExplorer explorer = null; // the GenBankExplorer object

    // public information
    // DisplayModes
    public static final int DM_LOCI = 1;
    public static final int DM_FEATURES = 2;

    // Internal information
    private DisplayMode currentDisplayMode = null;
    private java.util.List list_selected = new LinkedList();
    SelectionListModel slm = new SelectionListModel(); // inner class

    // UI objects
    private JPanel panel = null; // the 'view' itself
    private JTree tree = new JTree(); // the tree
    private JTextArea ta_file = new JTextArea(); // the text area for file information
    private JTextArea ta_selected = new JTextArea(); // the text area for info on currently selected
    private JList ls_selection = new JList(slm); // the list area for selection info

    // Data objects
    private GenBankFile genBankFile = null; // the currently loaded file

    /** Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet) and */
    public ViewManager(GenBankExplorer explorer) {
        // set up the GenBankExplorer
        this.explorer = explorer;

        initDisplayModes();
        switchDisplayMode(DM_LOCI);

        createUI();
    }

    /** Create the UI we will use for interacting with the user. */
    public void createUI() {
        panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // okay, boys
        // lessgo!
        //
        // we'll 'create' UI objects first, then create the intricate set of split panes
        // which lay them out.
        //
        ta_file.setEditable(false);
        ta_selected.setEditable(false);

        // layout time!
        JSplitPane p_textareas =
                new JSplitPane(
                        JSplitPane.VERTICAL_SPLIT,
                        new JScrollPane(ta_file),
                        new JScrollPane(ta_selected));
        p_textareas.setResizeWeight(0.5); // half way (by default)
        JSplitPane split =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), p_textareas);
        split.setResizeWeight(0.3); // bit more right by default

        ls_selection.addMouseListener(this);
        JSplitPane full =
                new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, new JScrollPane(ls_selection));
        full.setResizeWeight(0.6);

        full.setPreferredSize(new Dimension(400, 500));

        // err ... I don't think this is the best way to do this
        // but it's certainly the quickest, so.
        new DropTarget(panel, explorer).setActive(true);
        new DropTarget(tree, explorer).setActive(true);
        new DropTarget(ta_file, explorer).setActive(true);
        new DropTarget(ta_selected, explorer).setActive(true);
        new DropTarget(ls_selection, explorer).setActive(true);

        panel.add(full);
    }

    /** Clear the currently loaded file. */
    public void clear() {
        clearSelection();

        if (genBankFile != null) {
            genBankFile = null;
            updateTree();
        }
    }

    /** Loads the new file, after clearing the previous one. */
    public void loadFile(File f) {
        clear();

        try {
            ProgressDialog pd =
                    ProgressDialog.create(
                            explorer.getFrame(),
                            "Please wait, loading file ...",
                            "Loading GenBank file "
                                    + f.getAbsolutePath()
                                    + ". Sorry for the delay!");
            genBankFile = new GenBankFile(f, pd);
        } catch (IOException e) {
            displayIOExceptionWhileWriting(e, f);
            return;
        } catch (FormatException e) {
            displayException(
                    "Error while reading file '" + f + "'",
                    "The file '"
                            + f
                            + "' could be read as a GenBank file. Are you sure it is a properly"
                            + " formatted GenBank file?\n"
                            + "The following error occured while trying to read this file: "
                            + e.getMessage());
            return;
        } catch (DelayAbortedException e) {
            return;
        }

        // update the entire tree
        updateTree();
    }

    /** Returns the panel to anyone interested. */
    public JPanel getPanel() {
        return panel;
    }

    public GenBankFile getGenBankFile() {
        return genBankFile;
    }

    // 	ERROR HANDLING AND DISPLAY CODE
    //
    public void displayIOExceptionWhileWriting(IOException e, File f) {
        new MessageBox(
                        explorer.getFrame(),
                        "Error while writing file '" + f + "'!",
                        "The following error was encountered while writing to file "
                                + f
                                + ": "
                                + e.getMessage()
                                + "\n\n"
                                + "Please ensure that you have the permissions to write to this"
                                + " file, that the disk is not full, and that the file is not"
                                + " write-protected.",
                        MessageBox.MB_ERROR)
                .go();
    }

    public void displayIOExceptionWhileReading(IOException e, File f) {
        new MessageBox(
                        explorer.getFrame(),
                        "Error while reading file '" + f + "'!",
                        "The following error was encountered while trying to read from file "
                                + f
                                + ": "
                                + e.getMessage()
                                + "\n\n"
                                + "Please ensure that the file exists, and that you have the"
                                + " permissions to read from it.",
                        MessageBox.MB_ERROR)
                .go();
    }

    public void displayException(String title, String message) {
        new MessageBox(explorer.getFrame(), title, message, MessageBox.MB_ERROR).go();
    }

    // UI GET/SETs AND SO ON
    //
    public JTree getTree() {
        return tree;
    }

    public void setFileText(String text) {
        Caret c = ta_file.getCaret();
        ta_file.setCaret(null);
        ta_file.setText(text);
        ta_file.setCaret(c);
    }

    public void setSelectionText(String text) {
        // Thank you, Sun bug #4227520!
        Caret c = ta_selected.getCaret();
        ta_selected.setCaret(null);
        ta_selected.setText(text);
        ta_selected.setCaret(c);
    }

    public ProgressDialog makeProgressDialog(String title, String message) {
        return ProgressDialog.create(explorer.getFrame(), title, message);
    }

    // 	DISPLAY MODE SWITCHING/HANDLING CODE
    //
    private Vector vec_displayModes = new Vector();

    public void initDisplayModes() {
        vec_displayModes.add(new LociDisplayMode(this));
        vec_displayModes.add(new FeaturesDisplayMode(this));
    }

    public void switchDisplayMode(int mode) {
        if (currentDisplayMode != null) currentDisplayMode.deactivateMode();

        switch (mode) {
            case DM_LOCI:
                currentDisplayMode = (DisplayMode) vec_displayModes.get(0);
                break;

            case DM_FEATURES:
                currentDisplayMode = (DisplayMode) vec_displayModes.get(1);
                break;
        }

        currentDisplayMode.activateMode();
        updateTree();
    }

    public void updateTree() {
        currentDisplayMode.setGenBankFile(genBankFile);
        currentDisplayMode.updateTree();
    }

    public void updateNode(TreePath path) {
        currentDisplayMode.setGenBankFile(genBankFile);
        currentDisplayMode.updateNode(path);
    }

    public void updateFileInfo() {
        // the file information changed (more sequences, file name change, etc.)
        currentDisplayMode.setGenBankFile(genBankFile);
    }

    // Selection system
    //
    // The selection system is fairly simple at this stage: all actual
    // work needs to be handled 'underground' (i.e. in the DisplayModes).
    // For us, it's really simple in three ways:
    // 1.	Somebody notifies us when an Object gets selected
    // 2.	Somebody notifies us when an Object gets unselected
    // 3.	Somebody notifies us when we need to clear the entire selection
    // 	list.
    // 4.	We display all selected Objects on a List on the display.
    // 	If not, we use Object.toString() to figure out what to call it.
    // 5.	We will return a List of all selected Objects to anybody
    // 	anytime.
    // 6.	As a bonus, we will collage all SequenceContainers into one
    // 	huge SequenceList, and return that if you want! Wouldn't you
    // 	like that? Yes, you would, yes, you would!

    public void selectContainer(SequenceContainer o) {
        if (list_selected.contains(o)) return;
        list_selected.add(o);
        Iterator i = o.alsoContains().iterator();
        while (i.hasNext()) {
            selectContainer((SequenceContainer) i.next());
        }
        updateSelectionList();
    }

    public void unselectContainer(SequenceContainer o) {
        if (!list_selected.contains(o)) return;

        list_selected.remove(o);
        Iterator i = o.alsoContains().iterator();
        while (i.hasNext()) {
            unselectContainer((SequenceContainer) i.next());
        }
        updateSelectionList();
    }

    public void selectOrUnselectContainer(SequenceContainer o) {
        if (isContainerSelected(o)) unselectContainer(o);
        else selectContainer(o);
    }

    public boolean isContainerSelected(SequenceContainer o) {
        return list_selected.contains(o);
    }

    public void clearSelection() {
        list_selected = new LinkedList();
        updateSelectionList();
    }

    public void updateSelectionList() {
        slm.update();
        int count = list_selected.size();
        if (count == 0) explorer.updateStatusBar("No features selected for export.");
        else explorer.updateStatusBar("Currently selected " + count + " features for export.");
    }

    public java.util.List getSelectedContainers() {
        return list_selected;
    }

    // LISTMODEL
    // For managing list_selected.
    //
    private class SelectionListModel implements ListModel {
        private Vector v_listener = new Vector();

        public SelectionListModel() {}

        public Object getElementAt(int index) {
            return list_selected.get(index);
        }

        public int getSize() {
            return list_selected.size();
        }

        public void addListDataListener(ListDataListener l) {
            v_listener.add(l);
        }

        public void removeListDataListener(ListDataListener l) {
            v_listener.remove(l);
        }

        public void update() {
            Iterator i = v_listener.iterator();
            while (i.hasNext()) {
                ListDataListener l = (ListDataListener) i.next();

                l.contentsChanged(
                        new ListDataEvent(ls_selection, ListDataEvent.CONTENTS_CHANGED, -1, -1));
            }
        }
    }

    //
    // MouseListener
    // Mostly for listening in on ls_selection
    public void mouseClicked(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}

    public void mousePressed(MouseEvent e) {
        if (e.getSource().equals(ls_selection) && e.getClickCount() >= 2) {
            int index = ls_selection.locationToIndex(e.getPoint());

            if (index != -1) {
                list_selected.remove(index); // get rid of it
                updateSelectionList();
            }
        }
    }

    public void mouseReleased(MouseEvent e) {}
}
