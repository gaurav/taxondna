/**
 * Preferences is a common store for preferences. 'Preferences' preferences are stored
 * into the java.util.prefs store, and are thus persistant across sessions (heh heh -
 * ain't I the software engineer!). Everybody can check with us through a generic
 * preferences system (getPreference/setPreference), as well as to check some of the
 * variables we ourselves maintain.
 *
 * Some behaviour (and the underlying behaviour) we control ourselves; this includes:
 * 1.	The *first* time a file is loaded, we pop up a dialog to check whether the
 * 	user wants to use species names or sequence names. We then save this setting
 * 	for the entire session, but do NOT save it to the prefs store; so it needs
 * 	to be set once a session.
 *
 * 	I suppose ideally, we ought to reset it when the all columns are cleared, but
 * 	that's kind of complicated. But we'll think about it in the future!
 *
 */

/*
 *
 *  SequenceMatrix
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

package com.ggvaidya.TaxonDNA.SequenceMatrix;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Preferences implements ActionListener {
	private SequenceMatrix 		matrix 	= null;			// the SequenceMatrix object

	//
	// Options: note that we don't actually store these anymore; we're just a
	// proxy for the java.util.prefs interface
	//
	
	// How should Nexus output be formatted?
	//
	/** Nexus output should be interleaved. Returned by getNexusOutput(). */
	public static final int		PREF_NEXUS_INTERLEAVED	=	0;
	/** Nexus output should be in blocks. Returned by getNexusOutput(). */
	public static final int		PREF_NEXUS_BLOCKS	=	1;
	/** Nexus output should be in single, long lines. Returned by getNexusOutput(). */
	public static final int		PREF_NEXUS_SINGLE_LINE	=	2;

	// Should we use the full name or the species name?
	//
	public static final int		PREF_NOT_SET_YET	=	-1;	
	public static final int		PREF_USE_FULL_NAME	=	0;	
	public static final int		PREF_USE_SPECIES_NAME	=	1;
	private static int		prefName =			PREF_NOT_SET_YET;

	/**
	 * Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet)
	 * and 
	 */
	public Preferences(SequenceMatrix matrix) {
		// set up the SequenceMatrix
		this.matrix = matrix;
	}

	// 
	// the general Preference functions
	//
	/**
	 * Sets the preference specified 
	 */
	public void setPreference(String key, String value) {
		java.util.prefs.Preferences.userNodeForPackage(getClass()).put(key, value);
	}

	/**
	 * Returns the preference specified
	 * @param def default value for this key
	 */
	public String getPreference(String key, String def) {
		return java.util.prefs.Preferences.userNodeForPackage(getClass()).get(key, def);
	}

	/**
	 * Sets the preference specified (as int)
	 */
	public void setPreference(String key, int value) {
		java.util.prefs.Preferences.userNodeForPackage(getClass()).putInt(key, value);
	}

	/**
	 * Returns the preference specified (as int)
	 * @param def default value for this key
	 */
	public int getPreference(String key, int def) {
		return java.util.prefs.Preferences.userNodeForPackage(getClass()).getInt(key, def);
	}

	/** 
	 * Returns either PREF_USE_FULL_NAME or PREF_USE_SPECIES_NAME 
	 * @deprecated FileManager is handling this. Why are you?
	 */
	public int getUseWhichName() {
		if(prefName == PREF_NOT_SET_YET) {
			Dialog dg = new Dialog(
					matrix.getFrame(),
					"Species names or sequence names?",
					true);	// modal!
			dg.setLayout(new BorderLayout());

			TextArea ta = new TextArea("", 4, 40, TextArea.SCROLLBARS_VERTICAL_ONLY);
			ta.setEditable(false);
			ta.setText("Would you like to use the full sequence name? I could also try to determine the species name from the sequence name, and use that instead.");
			dg.add(ta);

			Panel buttons = new Panel();
			buttons.setLayout(new FlowLayout(FlowLayout.CENTER));
			Button btn = new Button("Use sequence names");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button("Use species names");
			btn.addActionListener(this);
			buttons.add(btn);
			dg.add(buttons, BorderLayout.SOUTH);

			dg.pack();
			dg.setVisible(true);

			// you are not going to believe this:
			// 1. 	The actionPerformed code will set prefName for us.
			// 	It will then close the dialog, at which point code
			// 	will continue executing. We will then return the
			// 	prefName to the system.
			// 	
			// 	Sigh.
			//
			return prefName;
		} else 
			return prefName;
	}

	/**
	 * Handles Action events (such as the 'OK' button).
	 */
	public void actionPerformed(ActionEvent e) {
		boolean close_parent_dialog = false;

		Object src = e.getSource();

		if(e.getActionCommand().equals("Use sequence names")) {
			prefName = PREF_USE_FULL_NAME;
			close_parent_dialog = true;

		} else if(e.getActionCommand().equals("Use species names")) {
			prefName = PREF_USE_SPECIES_NAME;
			close_parent_dialog = true;
		}

		if(close_parent_dialog) {
			Button btn = (Button) src;
			Panel p = (Panel) btn.getParent();
			Dialog dg = (Dialog) p.getParent();

			dg.setVisible(false);
		}
	}
	

	public void beginNewSession() {
		// clear all session-based variables
		// right now, this is only PREF_NOT_SET_YET.
		prefName = PREF_NOT_SET_YET;
		MessageBox.resetSession();		// reset all MB_YESNOTOALL
	}
}
