/**
 * Barcode generator attempts to generate species-level barcodes for everybody. 
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

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class BarcodeGenerator extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {
	private TaxonDNA	taxonDNA = null;
	private SequenceList	set = null;

	private Choice		choice_overlap = new Choice();
	private Button		calc;

	public BarcodeGenerator(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
	

		// layouting
		setLayout(new BorderLayout());	
	       	
		Panel settings = new Panel(); 
		settings.setLayout(new GridBagLayout());
		GridBagConstraints cons = new GridBagConstraints();
		
		cons.gridwidth = 1;
		cons.gridheight = 1;
		cons.weightx = 0;
		cons.weighty = 0;
		cons.anchor = GridBagConstraints.NORTHWEST;
		cons.fill = GridBagConstraints.HORIZONTAL;
		cons.insets = new Insets(5, 5, 5, 5);
		cons.ipadx = 0;
		cons.ipady = 0;
		
		// overlap
		cons.gridx = 0;
		cons.gridy = 0;
		cons.weightx = 1;

		settings.add(new Label("Only consider base pairs for which:"), cons);
		choice_overlap.add("information exists in any sequence");
		choice_overlap.add("information exists for two or more sequences");
		choice_overlap.add("information exists for one or more sequences");
		choice_overlap.select(1);
		choice_overlap.addItemListener(this);

		cons.gridx = 1;
		cons.gridy = 0;
		settings.add(choice_overlap, cons);

		cons.gridx = 0;
		cons.gridy = 1;
		cons.gridwidth = 2;
		settings.add(new Label("We will use the inclusivity algorithm to generate consensus sequences. Thus, A+T will become W, and W+C will become H."), cons);
		
		calc = new Button("Generate");
		calc.addActionListener(this);

		cons.gridx = 0;
		cons.gridy = 2;
		cons.gridwidth = 2;
		settings.add(calc, cons);

		add(settings, BorderLayout.NORTH);

		/*
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
*/

/*
		buttons.add(calc);
		add(buttons, BorderLayout.SOUTH);
		*/
	}

	public void itemStateChanged(ItemEvent e) {
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(calc)) {
			new Thread(this, "BarcodeGenerator").start();	
		}
	}
	
	public void dataChanged()	{
		return;
	}

	public void run() {
		set = taxonDNA.lockSequenceList();

		if(set == null) {
			taxonDNA.unlockSequenceList();
			return;
		}
			

		int 	overlap = choice_overlap.getSelectedIndex();
		int	num_sequences_should_match = 2;
		int 	count = 0;

		if(overlap == 0) { // all sequences
			num_sequences_should_match = -1;
		} else if(overlap == 1) { // min 2
			num_sequences_should_match = 2;
		} else if(overlap == 2) { // min 1
			num_sequences_should_match = 1;
		}

//		System.err.println("Number of sequences which should match: " + num_sequences_should_match);
		
		ProgressDialog delay = new ProgressDialog(taxonDNA.getFrame(), "Please wait, generating sequence barcodes ...", "Please wait, sequence barcodes are being generated. This may take a while.", 0);

		delay.begin();

		// first, collate into species groups
		Vector groups = new Vector(); 
		Iterator i = set.iterator();
		count = 0;
		while(i.hasNext()) {
			boolean found = false;
			Sequence seq = (Sequence) i.next();

			count++;
			try {
				delay.delay(count, set.count() * 2);
			} catch(DelayAbortedException e) {
				delay.end();
				taxonDNA.unlockSequenceList();
				return;
			}

			
			Iterator group_i = groups.iterator();
			while(group_i.hasNext()) {
				Vector	v	= (Vector) group_i.next();
				Sequence seq2	= (Sequence) v.get(0);
	
				if(seq.getSpeciesName().equals(seq2.getSpeciesName())) {
					v.add(seq);
					found = true;
					break;
				}
			}

			if(found)
				continue;

			Vector vec = new Vector();
			vec.add(seq);
			groups.add(vec);
		}

//		System.err.println("Number of groups: " + groups.size());

		//
		// Important stuff: this SequenceSet is NOT the nice,
		// locked, SequenceSet. Because of the way Sequence
		// now caches our pairwise distances, you can NOT
		// calculate distances on this SequenceSet. Luckily,
		// you don't have to - we just make it up, write it
		// out into SequenceSet, and it's all good. 
		// 
		SequenceList results = new SequenceList();

		// now, we compress each group into a single sequence.
		i = groups.iterator();		
		
		count = 0;
		try {
		while(i.hasNext()) {
			Vector group = (Vector) i.next();

			count++;
			delay.delay(set.count() + count, set.count() * 2);
			
			if(group.size() == 1) {
				results.add((Sequence)group.get(0));	
			} else {
				int count_sequences = group.size();
				int length = set.getMaxLength();
				StringBuffer buff = new StringBuffer();
				String name = "";

				for(int x = 0; x < length; x++) {
					int found_in = 0;
					char ch = '?';
					char ch2 = '?';
					
					for(int y = 0; y < count_sequences; y++) {
						String str = ((Sequence)group.get(y)).getSequence();

						if(name.equals("")) {
							name = ((Sequence)group.get(y)).getSpeciesName();
						}
						
						if(x < str.length())
							ch2 = str.charAt(x);
						else
							ch2 = '?';
						
						if(ch == '?')
							ch = ch2;
						
						if(Sequence.identical(ch, ch2)) {
							found_in++;
						} 
						
						ch = Sequence.consensus(ch, ch2);
						if(ch == '_')
							ch = '-';
					}

					if(num_sequences_should_match == -1) {
						// everything goes!
						buff.append(ch);
					} else if(found_in >= num_sequences_should_match) {
						buff.append(ch);
					} else {
						// not enough matched!
						//
						// we make it 'N' here, but later on
						// leading and lagging 'N's are converted into gaps 
						buff.append('N');
					}
				}

				for(int x = 0; x < buff.length(); x++) {
					if(buff.charAt(x) == '-') {
						// go on
					} else if(buff.charAt(x) == 'N') {
						// turn into gap!
						buff.setCharAt(x, '-');
					} else {
						// lead over!
						break;
					}
				}

				for(int x = buff.length() - 1; x >= 0; x--) {
					if(buff.charAt(x) == '-') {
						// go on
					} else if(buff.charAt(x) == 'N') {
						// turn into gap!
						buff.setCharAt(x, '-');
					} else {
						// lead over!
						break;
					}
				}

				String str = buff.toString().replace('_', '-');
//				System.err.println("Consensus of " + name + " (" + count_sequences + "): " + str);
				results.add(new Sequence(name + " (barcode of " + count_sequences + " sequences)", str));
			}
		}

		// now, we either spawn a new instance of TaxonDNA, or
		// export into Fasta file.
		// 
		// we export. SOOOOO much simpler 
		FileDialog fd = new FileDialog(taxonDNA.getFrame(), "Save species barcodes as FASTA file ...", FileDialog.SAVE);
		fd.setVisible(true);

		if(fd.getDirectory() != null && fd.getFile() != null) {
			File file = new File(fd.getDirectory() + fd.getFile());

			com.ggvaidya.TaxonDNA.DNA.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile();
			ff.writeFile(file, new SequenceList(results), null);
			file = null;
		}

		results = null;
		
		} catch(SequenceException e) {
			MessageBox.messageBox(taxonDNA.getFrame(), "Error: sequence incorrect", "An incorrect sequence was generated. This is an error in the program. Please inform the programmer.\n\nTechnical description: " + e);
			e.printStackTrace();
		} catch(IOException e) {
			MessageBox.messageBox(taxonDNA.getFrame(), "Error: could not write file", "The following error was reported while trying to write the Fasta file: " + e);
		} catch(DelayAbortedException e) {
			MessageBox.messageBox(taxonDNA.getFrame(), "Calculation aborted", "This calculation has been aborted as requested.");
		} finally {
			delay.end();
			taxonDNA.unlockSequenceList();
		}
	}
	
	public String getShortName() {		return "Consensus Barcode Generator"; 	}
	public String getDescription() {	return "Generates a consensus sequence for every species in the dataset"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
