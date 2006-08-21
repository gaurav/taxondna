/**
 * A UIExtension which *might* (eventually) allow for more fine-grained
 * control of CDS and gene imports. Or something.
 *
 * Right now, it functions as a graphical front end to DNA.formats.GenBankFile,
 * which needs to know which genes to ignore or export, etc.
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2006

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
*/


package com.ggvaidya.TaxonDNA.Modules;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class CDSExaminer implements UIExtension, ActionListener {
	// hackahackahackahack!
	//
	// It's not *too* bad: the "modal" Dialog (atleast on Windows)
	// blocks the entire application, so both TaxonDNA windows
	// are unusable when the dialog is activated.
	//
	private static TaxonDNA	taxonDNA = null;

	private Dialog		dialog;
	private Label		label_explanation = new Label();
	private java.awt.List	list_genes = new java.awt.List(20, true);
	private Choice 		choice_deleteOrKeep = new Choice();
	private Button		button_OK = new Button("OK");

	public CDSExaminer(TaxonDNA view) {
		if(view != null)
			taxonDNA = view;
	}

	private Dialog createDialogBox(String name) {
		dialog = new Dialog(
				taxonDNA.getFrame(),
				"Please select the " + name + " you'd like",		// we set title later
				true
				);
		
		dialog.setLayout(new BorderLayout());
		
		dialog.add(new Label("Please select the " + name + " you'd like from the following list:"), BorderLayout.NORTH);
		dialog.add(list_genes);

		Panel buttons = new Panel();
		buttons.setLayout(new BorderLayout());

		choice_deleteOrKeep.add("Keep sequences without selected CDSs");
		choice_deleteOrKeep.add("Delete sequences without selected CDSs ");	
		choice_deleteOrKeep.select(1);
		buttons.add(choice_deleteOrKeep, BorderLayout.NORTH);
	
		button_OK.addActionListener(this);
		buttons.add(button_OK);

		dialog.add(buttons, BorderLayout.SOUTH);

		dialog.pack();

		return dialog;
	}

	private void fillInHash(Hashtable hash) {		
		Set data = hash.keySet();
		TreeSet set = new TreeSet(data);
		Iterator i = set.iterator();
		int x = 0;
		while(i.hasNext()) {
			String str = (String) i.next();

			list_genes.add(str + " (with " + hash.get(str) + " sequences)");	

			x++;
		}
	}

	private Hashtable getSelectedHash() {
		String[] selected = list_genes.getSelectedItems();
		Hashtable hash = new Hashtable();

		for(int x = 0; x < selected.length; x++) {
			String name = selected[x].substring(0, selected[x].indexOf(" (with"));
			//System.err.println("Selected: " + name);
			hash.put(name, new Object());
		}

		return hash;
	}

	private boolean keepSequencesWithoutCDS() {
		return (choice_deleteOrKeep.getSelectedIndex() == 0);
	}

	/**
	 * Our only really important function (thus far)
	 */
	public static Object[] checkHashOfCDSs(String name, Hashtable hash) {
		CDSExaminer e = new CDSExaminer(null);	

		Dialog d = e.createDialogBox(name);
		e.fillInHash(hash);
		d.setVisible(true);

		Object obj[] = new Object[2];
		obj[0] = new Boolean(e.keepSequencesWithoutCDS());
		obj[1] = e.getSelectedHash();
		return obj;
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(button_OK)) {
			dialog.setVisible(false);
		}
	}

	/* Data changed: couldn't care less */
	public void dataChanged() {
	}

	// UIExtension stuff
	public String getShortName() { return "CDS Examiner"; }
	
	public String getDescription() { return "Allows you to examine CDSs and so on in datasets which support this. Mostly just here to provide support for format handlers."; }

	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}
	
	public Frame getFrame() { return null; }
	public Panel getPanel() { return null; }
	
}
