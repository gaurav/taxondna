/**
 * The FeaturesDisplayMode displays features. Initially, it will only display a 'list' (really a tree of CheckBoxes)
 * using which you can select specific features you'd like to export (maybe grouped by type?).
 *
 * Eventually, it will be able to figure out the 'natural ordering' of features (i.e. which features are *inside*
 * other features, etc.) and order it that-a-way, but that's way too much computer science for me, man.  
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
import javax.swing.tree.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class FeaturesDisplayMode extends DisplayMode implements MouseListener {
	private FeaturesCellRenderer renderer = new FeaturesCellRenderer(this);

	public FeaturesDisplayMode(ViewManager man) {
		super(man);
	}

	private int old_toggleClickCount = 0;
	public void activateMode() {
		super.activateMode();
		viewManager.getTree().setCellRenderer(renderer);
		old_toggleClickCount = viewManager.getTree().getToggleClickCount();
		viewManager.getTree().setToggleClickCount(99);
		viewManager.getTree().addMouseListener(this);
	}
	
	public void deactivateMode() {
		viewManager.getTree().removeMouseListener(this);
		viewManager.getTree().setToggleClickCount(old_toggleClickCount);
		viewManager.getTree().setCellRenderer(null);
		super.deactivateMode();
	}
	
	// DATA HANDLING
	Hashtable ht_features = new Hashtable();

	public void setGenBankFile(GenBankFile genBankFile) {
		super.setGenBankFile(genBankFile);

		if(genBankFile != null) {
			viewManager.setFileText("Current file: " + genBankFile.getFile().getAbsolutePath() + "\nNumber of loci in file: " + genBankFile.getLocusCount());

			ht_features = new Hashtable();
			Iterator i_loci = genBankFile.getLoci().iterator();
			while(i_loci.hasNext()) {
				GenBankFile.Locus l = (GenBankFile.Locus) i_loci.next();

				GenBankFile.Section s = l.getSection("FEATURES");
				if(s != null) {
					GenBankFile.FeaturesSection fs = (GenBankFile.FeaturesSection) s;

					Iterator i = fs.getFeatures().iterator();
					while(i.hasNext()) {
						GenBankFile.Feature f = (GenBankFile.Feature) i.next();
						String name = f.getName();

						if(ht_features.get(name) != null)
							((Vector)ht_features.get(name)).add(f);
						else {
							Vector v = new Vector();
							v.add(f);
							ht_features.put(name, v);
						}
					}
				}
			}
		} else {
			viewManager.setFileText("No file loaded.");
		}
	}

	public Object getRoot() {
		if(genBankFile == null)
			return "No file loaded";

		return "Current file (" + genBankFile.getFile().getAbsolutePath() + ")";
	}

	protected java.util.List getSubnodes(Object node) {
		if(genBankFile == null)
			return null;

		if(node.equals(getRoot())) {
			Vector v = new Vector(ht_features.keySet());
			Collections.sort(v);
			return v;
		}

		if(GenBankFile.Feature.class.isAssignableFrom(node.getClass())) {
			GenBankFile.Feature f = (GenBankFile.Feature) node;

			Vector v = new Vector(f.getKeys());
			Collections.sort(v);
			return v;
		}
		
		if(String.class.isAssignableFrom(node.getClass())) {
			Object o = ht_features.get((String) node);
			if(o == null) {
				// okay, it's not a FEATURE
				// it's probably a Feature.key
				// 
				// err ... ignore for now?
				return null;
			} else
				return (Vector) o;
		}


		return null;
	}

	public void pathSelected(TreePath p) {
		Object obj = p.getLastPathComponent();
		Class cls = obj.getClass();

		if(cls.equals(String.class)) {
			viewManager.setSelectionText("");

		} else if(cls.equals(GenBankFile.Locus.class)) {
			GenBankFile.Locus l = (GenBankFile.Locus) obj;

			StringBuffer buff = new StringBuffer();
			Iterator i_sec = l.getSections().iterator();
			while(i_sec.hasNext()) {
				GenBankFile.Section sec = (GenBankFile.Section) i_sec.next();

				buff.append(sec.getName() + ": " + sec.entry() + "\n");
			}

			viewManager.setSelectionText("Currently selected: locus " + l.toString() + "\n" + buff);

		} else if(GenBankFile.Section.class.isAssignableFrom(cls)) {
			GenBankFile.Section sec = (GenBankFile.Section) obj;

			String additionalText = "";
			
			if(GenBankFile.OriginSection.class.isAssignableFrom(cls)) {
				GenBankFile.OriginSection ori = (GenBankFile.OriginSection) obj;
				Sequence seq = null;

				try {
					seq = ori.getSequence();
					additionalText = "Sequence:\n" + seq.getSequenceWrapped(70);

				} catch(SequenceException e) {
					additionalText = "Sequence: Could not be extracted.\nThe following error occured while parsing sequence: " + e.getMessage();
				}

			}

			viewManager.setSelectionText("Currently selected: section " + sec.getName() + " of locus " + sec.getLocus() + "\nValue: " + sec.entry() + "\n" + additionalText);
		}
	}

	// 'SELECTION' tracking
	Hashtable ht_selected = new Hashtable();

	// TODO FIXME
	public void selectOrUnselectObject(Object obj) {
		if(ht_selected.get(obj) == null) {
			// not selected
			ht_selected.put(obj, new Object());
		} else {
			ht_selected.remove(obj);
		}
	}

	public boolean isSelected(Object obj) {
		return (ht_selected.get(obj) != null);
	}

	// MOUSE LISTENER
	//
	public void mouseClicked(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		JTree tree = viewManager.getTree();
		int selRow = tree.getRowForLocation(e.getX(), e.getY());
		TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
		if(selRow != -1) {
			if(e.getClickCount() == 1) {
				// single clicks
			}
			else if(e.getClickCount() == 2) {
				// double click!
				
				Object target = selPath.getLastPathComponent();
				selectOrUnselectObject(target);
				updateNode(selPath);
			}
		}
	}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mouseReleased(MouseEvent e)	{}
}

class FeaturesCellRenderer extends DefaultTreeCellRenderer {
	FeaturesDisplayMode fdm = null;

	public FeaturesCellRenderer(FeaturesDisplayMode fdm) {
		this.fdm = fdm;
	}

	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
		if(leaf) {
			return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		} else {
			JCheckBox checkBox = new JCheckBox(value.toString());

			checkBox.setBorderPaintedFlat(true);

			if(selected)
				checkBox.setBackground(SystemColor.textHighlight);
			else
				checkBox.setBackground(tree.getBackground());

			if(fdm.isSelected(value))
				checkBox.setSelected(true);

			return checkBox;
		}
	}
}
