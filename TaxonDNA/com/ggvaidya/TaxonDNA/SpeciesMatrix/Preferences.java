/**
 * A frame which allows the user to select his preferences. It also
 * stores these preferences, and can be queried for the same.
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
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Preferences implements WindowListener, ItemListener, ActionListener {
	private SpeciesMatrix 		matrix 	= null;			// the SpeciesMatrix object
	private Dialog 			dialog 	= null;			// the Dialog which we need to display

	//
	// Options
	//
	/** Nexus output should be interleaved. Returned by getNexusOutput(). */
	public static final int		PREF_NEXUS_INTERLEAVED	=	0;
	/** Nexus output should be in blocks. Returned by getNexusOutput(). */
	public static final int		PREF_NEXUS_BLOCKS	=	1;

	// 
	// Our User Interface
	//
	private Choice 			choice_nexusOutput 	= 	new Choice();
	private TextField		tf_nexusOutputInterleaved =	new TextField("1000");
	private Button 			btn_Ok 			=	new Button("OK");

	/**
	 * Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet)
	 * and 
	 */
	public Preferences(SpeciesMatrix matrix) {
		// set up the SpeciesMatrix
		this.matrix = matrix;

		// set up 'dialog'
		dialog = new Dialog(matrix.getFrame(), "Preferences", true);

		Panel options = new Panel();
		RightLayout rl = new RightLayout(options);
		options.setLayout(rl);

		// Output Nexus files as: (choice_nexusOutput)
		choice_nexusOutput.add("Interleaved");
		choice_nexusOutput.add("Blocks (NOT usable on Macintosh versions of PAUP* and MacClade!)");

		choice_nexusOutput.addItemListener(this);
		rl.add(new Label("Output Nexus files as: "), RightLayout.LEFT);
		rl.add(choice_nexusOutput, RightLayout.BESIDE | RightLayout.STRETCH_X);

		rl.add(new Label("Interleave Nexus files at (in base pairs): "), RightLayout.NEXTLINE);
		rl.add(tf_nexusOutputInterleaved, RightLayout.BESIDE | RightLayout.STRETCH_X);

		dialog.add(options);

		// set up the 'buttons' bar ... which is really just the 'OK' button
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		// set up the okay button
		btn_Ok.addActionListener(this);
		buttons.add(btn_Ok);

		dialog.add(buttons, BorderLayout.SOUTH);

		// register us as a 'listener'
		dialog.addWindowListener(this);
	}

	/**
	 * Our proxy for dialog's setVisible(). Please use this to
	 * 'activate' the dialog.
	 */
	public void setVisible(boolean state) {
		if(state) {
			dialog.pack();
			dialog.setVisible(true);
		} else {
			if(verify()) {
				dialog.setVisible(false);
				dialog.dispose();
			}
		}
	}
	
	//
	// get the Preferences themselves
	//
	/** Returns either PREF_NEXUS_INTERLEAVED or PREF_NEXUS_BLOCKS */
	public int getNexusOutput() {
		return choice_nexusOutput.getSelectedIndex();
	}

	/** Returns the length of the blocks you'd like Nexus to spit out */
	public int getNexusInterleaveAt() {
		if(getNexusOutput() == PREF_NEXUS_BLOCKS)
			return 0;
		try {
			int x = Integer.parseInt(tf_nexusOutputInterleaved.getText());
			if(x < 1)
				return -1;
			return x;
		} catch(NumberFormatException e) {
			// shouldn't happen (see verify()), but just in case
			return -1;
		}
	}

	//
	// Listeners
	// 

	/**
	 * Handles Action events (such as the 'OK' button).
	 */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();

		if(src.equals(btn_Ok))
			setVisible(false);
	}

	/**
	 * Handles Item events (such as whether tf_nexusOutputInterleaved
	 * should be enabled or not).
	 */
	public void itemStateChanged(ItemEvent e) {
		Object src = e.getSource();

		if(src.equals(choice_nexusOutput)) {
			if(getNexusOutput() == PREF_NEXUS_INTERLEAVED)
				tf_nexusOutputInterleaved.setEnabled(true);
			else
				tf_nexusOutputInterleaved.setEnabled(false);
		}
	}

	/**
	 * Check to make sure that all input 'makes sense'.
	 * @return true, if it's okay to exit.
	 */
	private boolean verify() {
		// check getNexusInterleave
		if(getNexusInterleaveAt() == -1) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error in Nexus 'Interleave At' value",
					"You specified an invalid or un-understandable value for the Nexus 'Interleave at' argument.\n\nI am going to set it to interleave at 1000bp instead. Is this okay?",
					MessageBox.MB_YESNO);
			if(mb.showMessageBox() == MessageBox.MB_YES)
				tf_nexusOutputInterleaved.setText("1000");
			else
				// MB_NO
				return false;
		}

		return true;
	}

	// 
	// WindowListener methods
	//
	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {
		setVisible(false);
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}

}
