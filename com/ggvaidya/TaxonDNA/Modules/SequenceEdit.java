/**
 * 
 * A UIExtension which allows for sequence viewing and editing. This
 * is tricky, because this will actually need to talk to TaxonDNA 
 * to figure out a lot of the stuff (since TaxonDNA controls the
 * All Sequences list)
 *
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
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.Others.BrowserLauncher;
import com.ggvaidya.TaxonDNA.UI.*;


public class SequenceEdit extends Panel implements UIExtension, ActionListener, ItemListener, FocusListener {	
	private static final long serialVersionUID = -5611046287558975084L;
	private TaxonDNA	taxonDNA;
	private SequenceList	set = null;
	
	private Sequence	currentSequence = new Sequence();
	private boolean		weEditedTheSet = false;	// set to true when a dataChanged() was caused by us

	private SequencePanel	sequencePanel = null;

	// Sequence View
	private TextField	text_name = new TextField(); 
	private TextField	text_family = new TextField();
	private TextField	text_species = new TextField();
	private TextField	text_gi = new TextField();
	private Button		button_gi = new Button("Go to this GI number on NCBI");
	private TextField	text_len = new TextField();
	private TextArea	text_sequence = new TextArea();		

	private Button		btn_Copy = new Button("Copy to Clipboard");
	private Button		button_query = new Button("Query against others");
	
	/**
	 * Constructor: creates the interface on a panel
	 */
	public SequenceEdit(TaxonDNA view) {
		super();

		taxonDNA = view;
		sequencePanel = taxonDNA.getSequencePanel();

		// create the panel
		setLayout(new GridBagLayout());
		GridBagConstraints c =		new GridBagConstraints();
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(5, 5, 5, 5);
	
		c.gridx = 0;	c.gridy = 0;
		add(new Label("Sequence Name:"), c);
		
		c.gridx = 0;	c.gridy = 1;
		add(new Label("Family Name:"), c);	
		
		c.gridx = 0;	c.gridy = 2;
		add(new Label("Genus/Species Name:"), c);

		c.gridx = 0;	c.gridy = 3;
		add(new Label("GI number:"), c);

		c.gridx = 0;	c.gridy = 4;
		add(new Label("Length:"), c);

		c.gridx = 0;	c.gridy = 5;
		add(new Label("Sequence:"), c);

		// the following items can stretch
		c.weightx = 1;  c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 2;	// all of these spread for '2' units, so that we can squeeze in extra buttons

		c.gridx = 1;	c.gridy = 0;
		add(text_name, c);
		text_name.addFocusListener(this);
		text_name.setEditable(true);

		c.gridx = 1;	c.gridy = 1;
		add(text_family, c);
		text_family.setEditable(false);

		c.gridx = 1;	c.gridy = 2;
		add(text_species, c);
		text_species.setEditable(false);		
		
		c.gridwidth = 1;		// need to squeeze in subspecies and a button for 'gi'
		
		c.gridx = 1;	c.gridy = 3;
		add(text_gi, c);
		text_gi.setEditable(false);		

		c.fill = GridBagConstraints.NONE;
		c.weightx = 0;
		c.gridx = 2;	c.gridy = 3;
		add(button_gi, c);
		button_gi.addActionListener(this);
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;		

		c.gridwidth = 2;		// done squeezing
		c.gridx = 1;	c.gridy = 4;
		add(text_len, c);
		text_len.setEditable(false);
		
		// stretch, and fill
		c.gridx = 1;	c.gridy = 5;
		c.weightx = 1; 	c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;

		text_sequence.setEditable(true);
		text_sequence.setFont(new Font("Monospaced", Font.PLAIN, 12));
		text_sequence.addFocusListener(this);

		add(text_sequence, c);	

		// finally, the buttons panel 
		Panel buttons = new Panel();
		c.gridx = 0;	c.gridy = 6;
		c.weightx = 0; 	c.weighty = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 3;
		c.anchor = GridBagConstraints.EAST;

		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);
	
		button_query.addActionListener(this);
		buttons.add(button_query);		

		add(buttons, c);
		
		// add ourselves to the the sequencePanel's ItemListener ... so we know when something
		// is selected
		sequencePanel.addItemListener(this);
	}

	/* Our ItemListener. This will listen ONLY to the taxonDNA Sequences list, and let us know when things change. */
	public void itemStateChanged(ItemEvent e) {
		set = taxonDNA.lockSequenceList();
		if(set != null) {
			Sequence seq = (Sequence) e.getItem();
			displaySequence(seq);			
			taxonDNA.goToExtension(getShortName());
		}
		taxonDNA.unlockSequenceList();
	}

	/** Data changed: in our case, SequenceSet changed */
	public void dataChanged() {
		if(weEditedTheSet)
			return;

		text_name.setText("");
		text_family.setText("");
		text_species.setText("");
		text_gi.setText("");
		text_len.setText("");
		text_sequence.setText("");

		currentSequence = null;
	}

	/**
	 * Returns the Sequence() indicated. Note that you are entirely responsible for
	 * ensuring that the taxonDNA list stays in sync. Maybe a method in taxonDNA?
	 */
	public void displaySequence(Sequence seq) {
		if(seq != null) {
			currentSequence = seq;
			text_name.setText(seq.getFullName().trim());
			text_sequence.setText(seq.getSequenceWrapped(50).trim());
		} else {
			currentSequence = new Sequence();
			text_name.setText("");
			text_sequence.setText("");
		}

		text_name.setText(seq.getFullName().trim());

		updateInfo(currentSequence);
	}

	/**
	 * Updates name related information. (i.e. based on the full name, it sets 
	 * family, species, gi and the like.
	 */
	private void updateInfo(Sequence seq) {
		if(seq != null) {
			text_family.setText(seq.getFamilyName().trim());
			String subspecies = seq.getSubspeciesName().trim();
			if(subspecies.equals("")) {
				text_species.setText(seq.getSpeciesName().trim());
			} else {
				text_species.setText(seq.getSpeciesName().trim() + " (" + subspecies + ")");				
			}
			text_gi.setText(seq.getGI().trim());
			text_len.setText(seq.getActualLength() + " bp, entire sequence (including gaps): " + seq.getLength() + " bp");
		} else {
			text_family.setText("");
			text_species.setText("");
			text_gi.setText("");
			text_len.setText("");
		}
	}
	
	/** Returns the actual Panel */
	public Panel getPanel() {
		return this;
	}

	/** We have no commands to add to the menu */
	public boolean addCommandsToMenu(Menu menu) {
		// no commands for us, thanks
		return false;
	}

	/** Our action listener */
	public void actionPerformed(ActionEvent evt) {
		String cmd = evt.getActionCommand();
		
		// Button events
		if(cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
			if(set == null)
				return;
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				Sequence seq = taxonDNA.getSequencePanel().getSelectedSequence();
				String copy = "> " + seq.getFullName() + "\n" + seq.getSequence();
				StringSelection selection = new StringSelection(copy);
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");
		} else if(evt.getSource().equals(button_query)) {
			// Query against others
			QuerySequence qs = (QuerySequence) taxonDNA.getExtension("Query against sequences");
			if(set != null && qs != null) {
				qs.setSequence(taxonDNA.getSequencePanel().getSelectedSequence().getSequence());
				taxonDNA.goToExtension("Query against sequences");
			}
			
		} else if(evt.getSource().equals(button_gi)) {
			// go to the GI number!
			int gi = 0;
			if((gi = Integer.parseInt(text_gi.getText())) != 0) {
				String url = new String("http://www.ncbi.nlm.nih.gov/entrez/viewer.fcgi?db=nucleotide&val=" + gi);
				try {
					BrowserLauncher.openURL(url);
				} catch(java.io.IOException e) {
					MessageBox mb = new MessageBox(taxonDNA.getFrame(), "Could not open NCBI website!", "This program couldn't open your web browser to access NCBI. You can access this entry at " + url);
					mb.go();
				}
			}
		}
	}

	// Key listener
	/** We save the text in the 'name' and 'sequence' field when we gain focus.
	 * Then, when we loose it, we can check to see if anything changed.
	 */ 
	private String 	focusGained_name = "";
	private String 	focusGained_sequence = "";
	public void 	focusGained(FocusEvent e) {
		if(e.getSource().equals(text_name))
			focusGained_name = text_name.getText().trim();

		if(e.getSource().equals(text_sequence))
			focusGained_sequence = text_sequence.getText().trim();
	}
	/** When our editable fields loose foci, we need to update the information */	
	public void 	focusLost(FocusEvent e) {
		if(currentSequence == null)
			return;

		if(e.getSource().equals(text_name)) {
			if(text_name.getText().trim().equals(focusGained_name)) {
				// text_name didn't change
				focusGained_name = "";
				return;
			}

			set = taxonDNA.lockSequenceList();
			
			// sequence name changed
			String newName = text_name.getText().trim();
			currentSequence.changeName(newName);
			updateInfo(currentSequence);

			set.modified();
			
			taxonDNA.unlockSequenceList();

		} else if(e.getSource().equals(text_sequence)) {
			if(text_sequence.getText().trim().equals(focusGained_sequence)) {
				// text_sequence didn't change
				focusGained_sequence = "";
				return;
			}

			set = taxonDNA.lockSequenceList();
			
			// sequence changed
			String newSequence = text_sequence.getText().replaceAll("\\d", "").replaceAll("\\s", "");
				// if you're wondering '\d' is being eliminated so that you can copy straight out of NCBI
				// 1	ACTG
				// 5	ATTG
				// 9	ACCF ... format
			try {
				currentSequence.changeSequence(newSequence);
				updateInfo(currentSequence);	// this is actually the easiest way to do this
		
				set.modified();			
			} catch(SequenceException ex) {
				MessageBox mb = new MessageBox(taxonDNA.getFrame(), "Error in sequence!", "There is an error in this sequence: " + ex);
				mb.go();

				// change the text back to the old one
				text_sequence.setText(focusGained_sequence);
			}

			taxonDNA.unlockSequenceList();
		}

		
	}
	
	// UIExtension stuff
	public String getShortName() { return "Sequences"; }
	
	public String getDescription() { return "Used to edit sequences. An integral part of TaxonDNA"; }
	
	public Frame getFrame() { return null; }
	
}
