/**
 * Allows you provide a list of species names, and all
 * sequences with that species name is exported into a
 * separate file.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2008 Gaurav Vaidya
    
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

import java.util.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.*;					// For the BufferedReader, mostly

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class ExportBySpeciesName extends Panel implements UIExtension, ActionListener {
	private SpeciesIdentifier	seqId = null;

	// This is where we'll get our species list.
	private TextArea	text_main = new TextArea();

	private Button		btn_Calculate = new Button("Export sequences with the following species names");
	private Button		btn_Copy;

	public ExportBySpeciesName(SpeciesIdentifier seqId) {
		this.seqId = seqId;
		
		setLayout(new BorderLayout());

		Panel top = new Panel();
		RightLayout rl = new RightLayout(top);
		top.setLayout(rl);

		btn_Calculate.addActionListener(this);
		rl.add(btn_Calculate, RightLayout.NEXTLINE | RightLayout.FILL_4);

		add(top, BorderLayout.NORTH);
		
		add(text_main);

		text_main.setText("Enter species list here");

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Copy = new Button("Copy to Clipboard");
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);		
	
		add(buttons, BorderLayout.SOUTH);
	}

	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if(cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException ex) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to Clipboard");
		}

		if(e.getSource().equals(btn_Calculate)) {
			exportSequences();
		}
	}
	
	public void exportSequences() {
		// Step 1. Make a list of 'chosen' species
		SequenceList list = seqId.lockSequenceList();
		try {
			String line;
			Vector vec_species_names = new Vector();
			
			// Read the names off text_main
			BufferedReader reader = new BufferedReader(new StringReader(text_main.getText()));	
			while((line = reader.readLine()) != null) {
				line = line.trim();
				if(line.length() == 0) {
					// blank line, ignore
				} else {
					vec_species_names.add(line);
				}
			}
			
			int[] sequence_counts = new int[vec_species_names.size()];
			
			// Go through the sequence list
			SequenceList to_export = new SequenceList();
			
			Iterator i = list.iterator();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();
				
				Iterator i_names = vec_species_names.iterator();
				int x = 0;
				while(i_names.hasNext()) {
					String name = (String) i_names.next();
				
					if(seq.getSpeciesName().equalsIgnoreCase(name)) {
						// match!
						to_export.add(seq);
						sequence_counts[x]++;
						break;
					}
					x++;
				}
			}
			
			// Now: export!
			FileDialog fd = new FileDialog(seqId.getFrame(), "Where would you like me to extract " + to_export.count() + " sequences as FASTA?", FileDialog.SAVE);
			fd.setVisible(true);
			
			File f_output;
			if(fd.getFile() == null) {
				// cancel
				return;
			}
			
			// go for it!
			if(fd.getDirectory() != null)
				f_output = new File(fd.getDirectory(), fd.getFile());
			else
				f_output = new File(fd.getFile());
				
			// export
			com.ggvaidya.TaxonDNA.Model.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.Model.formats.FastaFile();
			ff.writeFile(f_output, to_export, null);
			
			// yay done!
			// write out stuff to tell the user what happened, etc.
			StringBuilder results = new StringBuilder();
			results.append("Export successful!\n\n");
			
			i = vec_species_names.iterator();
			int x = 0;
			while(i.hasNext()) {
				String name = (String) i.next();
				
				results.append("\t" + name + "\t" + sequence_counts[x] + "\tsequences exported.\n");
				x++;
			}
			
			text_main.setText(results.toString());
		} catch(Exception e) {
			new MessageBox(seqId.getFrame(), "Error: could not export sequences!", "There was a problem exporting sequences. The technical description is: " + e.getMessage()); 
		} finally {
			seqId.unlockSequenceList();
		}
	}
	
	// DataChanged()? Not like we care.
	public void dataChanged() {
		return;
	}

	public String getShortName() {		return "Export by Species Name"; 	}
	public String getDescription() {	return "Allows you to export species by species name"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
