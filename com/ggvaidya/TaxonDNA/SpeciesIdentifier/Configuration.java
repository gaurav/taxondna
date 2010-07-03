/**
 * A UIExtension which allows you to set up the basic settings we will use for
 * everything.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005-06

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


package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.awt.*;
import java.awt.event.*;

import java.util.prefs.*;		// for Preferences

import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class Configuration extends Panel implements UIExtension, ActionListener, FocusListener {
	SpeciesIdentifier 	seqId;
	Panel 			settings = 	new Panel();	// Settings panel

	Label			warningLabel = new Label("Please wait, loading SpeciesIdentifier ...");	// warning: please set up BEFORE loading files
	
	TextField		tfMinOverlap = new TextField("", 5);
	Choice			choice_ambiguity = new Choice();
	Choice			choice_pairwiseMethod = new Choice();
	
	Button			lock_button = new Button("Lock Settings");
	boolean			locked = false;

	static java.util.Vector	configs = new java.util.Vector();
	static boolean		lockDown_in_effect = false;
	
	public Configuration(SpeciesIdentifier seqId) {
		this.seqId = seqId;

		// add us to our self-maintained list!
		configs.add(this);

		// activate the focus listeners
		//
		choice_pairwiseMethod.addFocusListener(this);
		tfMinOverlap.addFocusListener(this);
		choice_ambiguity.addFocusListener(this);
		
		// Layouting the UI for the settings 
		//
		RightLayout rl = new RightLayout(settings);
		settings.setLayout(rl);
		
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

		rl.add(warningLabel, RightLayout.NONE);		
		lock_button.addActionListener(this);
		rl.add(lock_button, RightLayout.BESIDE | RightLayout.FLUSHRIGHT | RightLayout.FILL_2);

		// add the pairwiseMethod prompt
		rl.add(new Label("Calculate pairwise distances as"), RightLayout.NEXTLINE | RightLayout.STRETCH_X);
		choice_pairwiseMethod.add("Uncorrected pairwise distances");
		choice_pairwiseMethod.add("Kimura 2-parameter corrected distances");
		rl.add(choice_pairwiseMethod, RightLayout.BESIDE | RightLayout.FILL_2);
		
		// add the minimum overlap setting
		//
		rl.add(new Label("Do not compare two sequences unless they have atleast "), RightLayout.NEXTLINE | RightLayout.STRETCH_X);

		tfMinOverlap.setText(String.valueOf(Sequence.getMinOverlap()));
		rl.add(tfMinOverlap, RightLayout.BESIDE);

		rl.add(new Label("bp in common."), RightLayout.BESIDE);

		// ambiguity codons
		//
		rl.add(new Label("How should this program treat ambiguous codons?"), RightLayout.NEXTLINE);
		choice_ambiguity.add("Ambiguous codons are used ('H' is treated as a combination of 'W' and 'C')"); 
		choice_ambiguity.add("Ambiguous codons NOT used (they are all converted into 'N')"); 
		rl.add(choice_ambiguity, RightLayout.BESIDE | RightLayout.FILL_2);

		// note about how we treat leading and lagging sequences
		rl.add(new Label("Note that differences in leading and trailing gaps are ignored, while differences in internal gaps will be counted."), RightLayout.NEXTLINE | RightLayout.FILL_3);
		
		// set up the default values to the values set in Prefs
		loadFromPrefs();

		// add settings into the main panel
		//
		setLayout(new BorderLayout());
		add(settings, BorderLayout.NORTH);

		if(lockDown_in_effect)
			lockDown(lockDown_in_effect);
	}

	public void lock() {
		tfMinOverlap.setEditable(false);
		choice_ambiguity.setEnabled(false);
		choice_pairwiseMethod.setEnabled(false);

		locked = true;
	}

	public void unlock() {
		tfMinOverlap.setEditable(true);
		choice_ambiguity.setEnabled(true);
		choice_pairwiseMethod.setEnabled(true);

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
		
		if(e.getSource().equals(choice_pairwiseMethod)) {
			if(choice_pairwiseMethod.getSelectedIndex() == 0) {
				// index == 0: uncorrected
				Sequence.setPairwiseDistanceMethod(Sequence.PDM_UNCORRECTED);
			} else {
				Sequence.setPairwiseDistanceMethod(Sequence.PDM_K2P);
			}
		}

		// save these values for future reference
		saveToPrefs();
	}

	/**
	 * From UIExtension, our short name. Our short name is "Configuration".
	 */
	public String getShortName() 	{ return "Configuration"; }

	/**
	 * From UIExtension, our description.
	 */
	public String getDescription() 	{ return "Change the settings used by all parts of this program"; }

	/**
	 * From UIExtension, our panel
	 */
	public Panel getPanel() { return this; }

	/**
	 * From UIExtension. We'd like to know whether a new file is
	 * loaded (and we should be LOCKED) or if a 'null' was loaded,
	 * in which case we should be UNLOCKED.
	 */
	public void dataChanged() {
		SequenceList list = seqId.lockSequenceList();
		
		if(list == null) {
			unlock();
			lock_button.setEnabled(true);
		} else {
			lock();
			lock_button.setEnabled(false);
			
			warningLabel.setText("There is a file loaded into this program. You cannot change these settings unless you reload it.");
			lock_button.setLabel("Unlock Settings");
		}
		
		// despite all this, if it says no ...
		if(lockDown_in_effect)
			lockDown(lockDown_in_effect);

		seqId.unlockSequenceList();

		return;
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
			seqId.goToExtension(getShortName());
		}

		if(e.getSource().equals(lock_button)) {
			if(locked)
				unlock();
			else {
				lock();
				warningLabel.setText("You have locked these settings. Please click on the \"Unlock\" button to unlock it.");
				lock_button.setLabel("Unlock Settings");
			}
		}
	}

	/**
	 * Loads all configuration options from the Preferences store. This
	 * will update Configuration items as well, so please only call after
	 * initialisation.
	 */
	public void loadFromPrefs() {
		Preferences prefs = Preferences.userNodeForPackage(com.ggvaidya.TaxonDNA.SpeciesIdentifier.Configuration.class);

		// minimum overlap
		Sequence.setMinOverlap(prefs.getInt("minimumOverlap", 300));
		tfMinOverlap.setText(String.valueOf(Sequence.getMinOverlap()));

		// ambiguous bases allowed
		Sequence.ambiguousBasesAllowed(prefs.getBoolean("ambiguousBasesAllowed", true));
		if(Sequence.areAmbiguousBasesAllowed()) {
			choice_ambiguity.select(0);
		} else {
			choice_ambiguity.select(1);
		}

		// pairwise distance method
		Sequence.setPairwiseDistanceMethod(prefs.getInt("pairwiseDistanceMethod", Sequence.PDM_UNCORRECTED));
		if(Sequence.getPairwiseDistanceMethod() == Sequence.PDM_UNCORRECTED)
			choice_pairwiseMethod.select(0);
		else if(Sequence.getPairwiseDistanceMethod() == Sequence.PDM_K2P)
			choice_pairwiseMethod.select(1);
	}

	/**
	 * 
	 */
	public void saveToPrefs() {	
		Preferences prefs = Preferences.userNodeForPackage(com.ggvaidya.TaxonDNA.SpeciesIdentifier.Configuration.class);
		prefs.putInt("minimumOverlap", Sequence.getMinOverlap());
		prefs.putBoolean("ambiguousBasesAllowed", Sequence.areAmbiguousBasesAllowed());
		prefs.putInt("pairwiseDistanceMethod", Sequence.getPairwiseDistanceMethod());
	}

	private boolean pre_lockDownState = false;
	/**
	 * Starts or stops a 'lockdown'. This puts up a special
	 * message, and is otherwise identical to lock()/unlock().
	 */
	private void lockDown(boolean state) {
		if(state) {	// lock down!
			pre_lockDownState = locked;
			lock();

			// secret lockdown message
			warningLabel.setText("You cannot modify the Configuration if more than one SpeciesIdentifier windows are open. Please close all but one window if you need to change these settings.");
			lock_button.setEnabled(false);
		} else { 	// unlock down!
			unlock();
			if(pre_lockDownState) {
				lock();		// but a normal one, this time.
			} else {
				lock_button.setEnabled(true);
			}
		}
	}

	/**
	 * Set up a 'lockdown'. This is a simple binary, so please don't
	 * try locking and unlocking multiple times. It's also a static
	 * method: it will unlock or lockup EVERY SINGLE Configuration
	 * in existance.
	 *
	 * Pretty cool, eh?
	 */
	public static void setLockDown(boolean state) {
		// okay, guys, message EVERYBODY
		java.util.Iterator i = configs.iterator();
		while(i.hasNext()) {
			Configuration c = (Configuration) i.next();
			c.lockDown(state);
		}
		lockDown_in_effect = state;
	}
	  
}


