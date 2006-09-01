/**
 * A UIExtension which allows you to set up the basic settings we will use for
 * everything.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005

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

import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class Configuration extends Panel implements UIExtension, ActionListener, FocusListener {
	TaxonDNA 		taxonDNA;
	SequenceList		set;
	Panel 			settings = 	new Panel();	// Settings panel

	Label			warningLabel;			// warning: please set up BEFORE loading files
	
	TextField		tfMinOverlap = new TextField("", 5);
	Choice			choice_ambiguity = new Choice();
	
	Button			lock_button = new Button(" Lock Settings ");
	boolean			locked = false;
	
	public Configuration(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;

		// prime the overlaps
		//
		tfMinOverlap.addFocusListener(this);
		
		// Layouting the UI for the settings 
		//
		settings.setLayout(new GridBagLayout());
		GridBagConstraints	cons = new GridBagConstraints();

		// default arguments
		//
		cons.gridwidth = 1;
		cons.gridheight = 1;
		cons.weightx = 0;
		cons.weighty = 0;
		cons.anchor = GridBagConstraints.NORTHWEST;
		cons.fill = GridBagConstraints.BOTH;
		cons.insets = new Insets(5, 5, 5, 5);
		cons.ipadx = 0;
		cons.ipady = 0;	
		
		// put in a title
		//
		/*
		cons.gridx = 0;
		cons.gridy = 0;
		cons.gridwidth = 3;
		Label label = new Label("Configuration");
		label.setFont(new Font("Serif", Font.PLAIN, 24));
		settings.add(label, cons);
		*/

		cons.gridx = 0;
		cons.gridy = 1;
		cons.gridwidth = 3;
		warningLabel = new Label();
		settings.add(warningLabel, cons);
		
		// add the minimum overlap setting
		//
		cons.gridx = 0;
		cons.gridy = 2;
		cons.gridwidth = 1;		
		cons.weightx = 1;
		settings.add(new Label("Do not compare two sequences unless they have atleast these many basepairs in common: "), cons);

		cons.gridx = 1;
		cons.gridy = 2;
		cons.weightx = 0;
		cons.fill = GridBagConstraints.NONE;
		tfMinOverlap.setText(String.valueOf(Sequence.getMinOverlap()));
		settings.add(tfMinOverlap, cons);

		cons.gridx = 2;
		cons.gridy = 2;
		settings.add(new Label("bp"), cons);

		// ambiguity codons
		//
		cons.gridx = 0;
		cons.gridy = 3;
		cons.fill = GridBagConstraints.HORIZONTAL;
		settings.add(new Label("How should this program treat ambiguous codons?"), cons);
		choice_ambiguity.add("Ambiguous codons are used ('H' is treated as a combination of 'W' and 'C')"); 
		choice_ambiguity.add("Ambiguous codons NOT used (they are all converted into 'N')"); 

		cons.gridx = 1;
		cons.gridy = 3;
		cons.fill = GridBagConstraints.NONE;
		settings.add(choice_ambiguity, cons);

		// note about how we treat leading and lagging sequences
		cons.gridx = 0;
		cons.gridy = 4;
		cons.gridwidth = 2;
		settings.add(new Label("Note that differences in leading and trailing gaps are ignored, while differences in internal gaps will be counted."), cons);
		

		// add settings into the main panel
		//
		setLayout(new BorderLayout());
		add(settings, BorderLayout.NORTH);		
		
		// Buttons
		//
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		lock_button.addActionListener(this);
		buttons.add(lock_button);
		
		add(buttons, BorderLayout.SOUTH);
	}

	public void lock() {
		tfMinOverlap.setEditable(false);
		choice_ambiguity.setEnabled(false);

		if(set != null)
			warningLabel.setText("There is a file loaded into this program. You cannot change these settings unless you reload it.");
		else
			warningLabel.setText("You have locked these settings. Please click on the \"Unlock\" button to unlock it.");
		lock_button.setLabel("Unlock Settings");
		
		locked = true;
	}

	public void unlock() {
		tfMinOverlap.setEditable(true);
		choice_ambiguity.setEnabled(true);

		warningLabel.setText("Please change your settings BEFORE loading a file into this program.");		
		lock_button.setLabel(" Lock Settings ");

		locked = false;
	}	
	
	public void focusGained(FocusEvent e) {
		// quite frankly, my dear
		// i don't give a damn ...
	}

	public void focusLost(FocusEvent e) {
		if(e.getSource().equals(tfMinOverlap)) {
			int i = new Integer(tfMinOverlap.getText()).intValue();

			if(i < 0)
				i = 0;

			Sequence.setMinOverlap(i);

			tfMinOverlap.setText(String.valueOf(i));

		}

		if(e.getSource().equals(choice_ambiguity)) {
			if(choice_ambiguity.getSelectedIndex() == 1) {
				Sequence.ambiguousBasesAllowed(false);
			} else {
				Sequence.ambiguousBasesAllowed(true);
			}
		}
	}

	/**
	 * From UIExtension, our short name. Our short name is "Configuration".
	 */
	public String getShortName() 	{ return "Configuration"; }

	/**
	 * From UIExtension, our description.
	 */
	public String getDescription() 	{ return "Allows you to configure the basic settings of this program, such as minimum overlap, base pair comparison, etc."; }

	/**
	 * From UIExtension, our panel
	 */
	public Panel getPanel() { return this; }

	/**
	 * From UIExtension, we don't care if data changes.
	 */
	public void dataChanged() {
		if(set == null) { 	// data is changing from null to ... ?
					// in any case, this means we set from what we've got
			Sequence.setMinOverlap(new Integer(tfMinOverlap.getText()).intValue());
			
			if(choice_ambiguity.getSelectedIndex() == 1) {
				Sequence.ambiguousBasesAllowed(false);
			} else {
				Sequence.ambiguousBasesAllowed(true);
			}
		}
		
		set = taxonDNA.lockSequenceList();
		
		tfMinOverlap.setText(String.valueOf(Sequence.getMinOverlap()));

		if(Sequence.areAmbiguousBasesAllowed()) {
			choice_ambiguity.select(0);
		} else {
			choice_ambiguity.select(1);
		}

		if(set == null) {
			unlock();
			lock_button.setEnabled(true);
		} else {
			lock();
			lock_button.setEnabled(false);			
		}

		taxonDNA.unlockSequenceList();
	}

	/**
	 * From UIExtension, add commands. We'll add one called "Configuration.
	 */
	public boolean addCommandsToMenu(Menu menu) {
		menu.add("Configuration");
		menu.addActionListener(this);

		return true;
	}

	/**
	 * Our ActionListener.
	 */
	public void actionPerformed(ActionEvent e) {
		if(e.getActionCommand().equals("Change Settings")) {
			// our menu option has been activated!
			taxonDNA.goToExtension(getShortName());
		}

		if(e.getSource().equals(lock_button)) {
			if(locked)
				unlock();
			else
				lock();
		}
	}
}


