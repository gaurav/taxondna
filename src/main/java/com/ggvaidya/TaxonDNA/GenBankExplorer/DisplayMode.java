/**
 * The DisplayMode (as in SequenceMatrix) is the 'view' of our MVC layout. All true DisplayModes
 * inherit from DisplayMode, and remember to call their parents in DisplayMode (unless they really
 * know what they're doing).
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

import java.util.*;
import javax.swing.event.*;
import javax.swing.tree.*;

public abstract class DisplayMode implements TreeModel, TreeSelectionListener {
    protected ViewManager viewManager = null; // the ViewManager object
    protected GenBankFile genBankFile = null; // the GenBankFile object

    // Internal information
    private Vector treeListeners = new Vector();

    // constructors
    public DisplayMode(ViewManager man) {
        viewManager = man;
    }

    // interfacings
    public void setGenBankFile(GenBankFile gbf) {
        this.genBankFile = gbf;
    }

    public void activateMode() {
        viewManager.getTree().setModel(this);
        viewManager.getTree().addTreeSelectionListener(this);
    }

    public void deactivateMode() {
        // can't just get rid of tree model like that
        viewManager.getTree().removeTreeSelectionListener(this);
    }

    // 	TREE MODEL CODE
    //
    public void addTreeModelListener(TreeModelListener l) {
        treeListeners.add(l);
    }

    public void removeTreeModelListener(TreeModelListener l) {
        treeListeners.remove(l);
    }
    /**
     * Fires a tree event at all listening TreeModelListeners. Warning: this will ONLY fire the
     * event as a treeStructureChanged(TreeModelEvent). If you need a less powerful event to be
     * fired, err ... update this code?
     */
    private void fireTreeEvent(TreeModelEvent e) {
        Iterator i = treeListeners.iterator();
        while (i.hasNext()) {
            TreeModelListener l = (TreeModelListener) i.next();

            l.treeStructureChanged(e);
        }
    }

    // Here is where we get an abstract 'tree' model out of the current
    // DisplayMode, and convert it into the root-index format so much
    // in vogue amongst TreeModels these days.
    //
    // The current plan is to have a single, overloadable function
    // which is:
    protected abstract java.util.List getSubnodes(Object node);
    // Calling getSubnodes() will return a List of
    // all the sub-objects of node, in the order required. It can
    // also return 'null' to indicate that it's on a node.
    //
    // The ONLY requirement is that you cannot have duplicate objects
    // in the tree ANYWHERE. If node1.equals(node2) is true, node1 and
    // node2 CANNOT BE IN THE SAME TREE. Kapish?
    //
    // Of course, we need to start somewhere, so you'll also have to
    // define:
    public abstract Object getRoot();
    //
    // And that's it!
    //
    // Now, here is the complexity we are sweeping under the metaphorical
    // carpet.
    //
    private Hashtable subNodes = null; // Object -> Vector

    private void updateHashtable() {
        subNodes = new Hashtable(); // clear our the hash!

        Object root = getRoot();
        addAllSubnodes(root);
    }

    // WARNING: do NOT use unless you know what you're doing!
    private void addAllSubnodes(Object node) {
        java.util.List currentSubnodes = getSubnodes(node);
        if (currentSubnodes == null) return;

        if (subNodes.get(node) != null) {
            // TODO: Better error, pls
            throw new RuntimeException("Duplicate object '" + node + "' found in tree!");
        }

        Vector v = new Vector();
        Iterator i = currentSubnodes.iterator();
        while (i.hasNext()) {
            Object o = (Object) i.next();

            v.add(o);
            subNodes.put(node, v); // replacing the previous 'v'
            addAllSubnodes(o);
        }

        subNodes.put(node, v);
    }

    public Object getChild(Object parent, int index) {
        Vector v = (Vector) subNodes.get(parent);

        if (v == null) return null;

        return v.get(index);
    }

    public int getChildCount(Object parent) {
        Vector v = (Vector) subNodes.get(parent);

        if (v == null) return 0;

        return v.size();
    }

    public int getIndexOfChild(Object parent, Object child) {
        Vector v = (Vector) subNodes.get(parent);

        if (v == null) return -1;

        return v.indexOf(child);
    }

    public boolean isLeaf(Object node) {
        if (genBankFile == null) return true;

        Vector v = (Vector) subNodes.get(node);

        if (v == null) return true;

        return false;
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        // hmmm!
    }

    // UPDATEs
    public void updateTree() {
        updateHashtable();
        fireTreeEvent(new TreeModelEvent(viewManager.getTree(), new TreePath(getRoot())));
    }

    public void updateNode(TreePath path) {
        if (path.getLastPathComponent().equals(getRoot())) updateHashtable();
        fireTreeEvent(new TreeModelEvent(viewManager.getTree(), path));
    }

    // TREE SELECTION LISTENER
    // The tree selection listener handles the nitty gritty of a TreeSelectionEvent.
    // If you don't need nitty-gritty-handling, you can directly overload:
    public void valueChanged(TreeSelectionEvent e) {
        TreePath[] treePaths = e.getPaths();

        for (int x = 0; x < treePaths.length; x++) {
            TreePath t = treePaths[x];

            if (e.isAddedPath(x)) pathSelected(t);
            else pathRemoved(t);
        }
    }

    public void pathSelected(TreePath t) {}

    public void pathRemoved(TreePath t) {}
}
