/**
 * Randomizer.java
 *
 * Generates a random dataset from the present one.
 * This might well be the last major module to make
 * it into TaxonDNA 1.0 (eventually!). So yay coding, etc.!
 * I am pooped though.
 *
 * This is a somewhat complicated module, since it's
 * the only one which needs to load in TWO datasets
 * simultaneously. Thus:
 * 1.	Dataset '1' is taxonDNA's current dataset
 * 	(i.e. what taxonDNA.lockSequenceList() returns)
 * 2.	Dataset '2' is loaded up by us here.
 * 3.	Output is piped into files specified in the
 * 	lower portion of our input system here. 
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

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class Randomizer extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {	
	private TaxonDNA	taxonDNA;

	private TextField 	tf_dataset1 = new TextField();
	private FileInputPanel	finp_dataset2 = null;
	private Button		btn_Go = new Button("Go!");

	private TextField	tf_noOfReplicates = new TextField("100");
	private DirectoryInputPanel	dinp_output = null;
	private Button		btn_Randomize = new Button("Randomize");
	private Choice		choice_format = new Choice();

	private TextArea 	text_results = new TextArea();

	private SequenceList	sl1	=	null;
	private SequenceList	sl2	=	null;	
	
	private Hashtable 	hash_seq_counts =	null;	//	Hash of Species name (String) => Count (Integer)

	/**
	 * No, no commands to add, thank you very much.
	 */
	public boolean addCommandsToMenu(Menu menu) {
		return false;
	}
 
	public Randomizer(TaxonDNA view) {
		super();

		taxonDNA = view;

		// funky stuff happens with the 'rl' variable here.
		// Three different RightLayouts are created (one for each panel)
		// but all of them are temporarily stored in 'rl'.
		//
		// If you understand pointers, this should all make perfect sense.
		// If not, err ... well.
		RightLayout rl = null;
			
		// Top panel
		// contains all the options and files and whatnot
		Panel top = new Panel();
		rl = new RightLayout(top);
		top.setLayout(rl);

		// dataset A: we get this from TaxonDNA (see dataChanged(), etc.)
		tf_dataset1.setEditable(false);
		rl.add(new Label("Please select dataset A: "), RightLayout.LEFT);
		rl.add(tf_dataset1, RightLayout.BESIDE | RightLayout.STRETCH_X);

		// dataset B: this is a FileInputPanel
		finp_dataset2 = new FileInputPanel("Please select dataset B: ", FileInputPanel.MODE_FILE_READ, taxonDNA.getFrame());
		rl.add(finp_dataset2, RightLayout.FILL_2 | RightLayout.NEXTLINE | RightLayout.STRETCH_X);
		
		btn_Go.addActionListener(this);
		rl.add(btn_Go, RightLayout.NEXTLINE | RightLayout.FILL_2 | RightLayout.STRETCH_X);

		// Output panel
		// graphically shows you what's going on. Good for debugging, etc.
		Panel output = new Panel();
		output.setLayout(new BorderLayout());

		text_results.setEditable(false);
		text_results.setFont(new Font("Monospaced", Font.PLAIN, 12));
		output.add(text_results);

		// Results panel
		// allows you to export the results any which way you like (including automatically etc.)
		Panel results = new Panel();
		rl = new RightLayout(results);
		results.setLayout(rl);

		rl.add(new Label("Number of replicates:"), RightLayout.LEFT);
		rl.add(tf_noOfReplicates, RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Label("Choose directory:"), RightLayout.NEXTLINE);
		dinp_output = new DirectoryInputPanel(null, DirectoryInputPanel.MODE_DIR_MODIFY_FILES, taxonDNA.getFrame());
		rl.add(dinp_output, RightLayout.BESIDE | RightLayout.FILL_2 | RightLayout.STRETCH_X);
		rl.add(new Label("In this format:"), RightLayout.NEXTLINE | RightLayout.LEFT);

		Iterator i = SequenceList.getFormatHandlers().iterator();
		while(i.hasNext()) {
			FormatHandler fh = (FormatHandler) i.next();
			choice_format.add(fh.getShortName());
		}
		rl.add(choice_format, RightLayout.BESIDE | RightLayout.STRETCH_X);
		btn_Randomize.addActionListener(this);
		rl.add(btn_Randomize, RightLayout.NEXTLINE | RightLayout.LEFT | RightLayout.STRETCH_X | RightLayout.FILL_2);

		// put the Panels 'top', 'output' and 'results' together
		setLayout(new BorderLayout());

		add(top, BorderLayout.NORTH);
		add(output);
		add(results, BorderLayout.SOUTH);
	}

	/* 
	 * If the data changed, we should update. At any rate, this will keep us in sync
	 * with everybody else.
	 */
	public void dataChanged() {
		sl1 = null;
		sl2 = null;

		hash_seq_counts = null;

		text_results.setText("No results to display");
		finp_dataset2.setFile(null);

		String fileName = "";
		SequenceList sl = taxonDNA.lockSequenceList();

		if(sl == null) {
			fileName = "None specified";
		} else if(sl.getFile() != null)
			fileName = sl.getFile().getAbsolutePath();
		else
			fileName = "None specified";

		tf_dataset1.setText(fileName);

		taxonDNA.unlockSequenceList();
	}

	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_Go)) {
			new Thread(this, "Randomizer_load").start();
		}
		else if(evt.getSource().equals(btn_Randomize)) {
			new Thread(this, "Randomizer_write").start();
		}
	}		

	public void run() {
		// we need to do two Threads - thankfully, not at the same time.
		if(Thread.currentThread().getName().equals("Randomizer_load")) {
			loadAndSetupSpeciesDetails();
		} else {
			randomizeLists();
		}
	}

	public void randomizeLists() {
		if(sl2 == null || hash_seq_counts == null) {
			// we don't care, etc.
			return;		
		}

		// how many replicates?
		int replicates = 0;
		replicates = Integer.parseInt(tf_noOfReplicates.getText());
		if(replicates == 0) { 
			// no, i will _not_ do 0 replicates.
			// how stupid do you think i am?
			return;
		}
		tf_noOfReplicates.setText(String.valueOf(replicates));

		// which formatHandler?
		// note: this is actually slightly imperfect, it will
		// screw up if formatHandler order changes while the
		// program is running. This SHOULD NEVER HAPPEN, but
		// y'know, programming and all that.
		//
		// Today's comments, I'm going to remember for a while.
		int format = choice_format.getSelectedIndex();
		FormatHandler fh = (FormatHandler)SequenceList.getFormatHandlers().get(format);
		if(fh == null) {
			// again, should NEVER happen.
			// but i can't be buggered to create a proper error for this
			// we'll pretend you want Fasta.
			fh = new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile();
		}

		// it is worth mentioning that this is not thread safe
		// at all, and not worth the error: unless we can be SURE
		// nobody else on the SYSTEM is going to jump in and use
		// dinp_output.getDirectory() + Date.now() as a filename,
		// it's not worth the effort. And no, the flock family
		// won't help.
		//
		// It's a risk worth taking.

		File output_dir = null;
		File dir = dinp_output.getDirectory();
		int n = 1000;

		do {
			output_dir = new File(dir, String.valueOf(new Date().getTime()));
			n--;			// this will jmp us out of this loop after 1,000 tries;
						// no infinite loops for us!
						//
						// (this is okay, because the mkdir will fail, and we'll send a convenient enough
						// error to the user. All good!)
						//
						// 		      ^--------	the optimism of computer programmers can be
						// 		      		scary on occasion.	
		} while(n > 0 && output_dir.exists());

		if(!output_dir.mkdir()) {
			MessageBox mb = new MessageBox(
				taxonDNA.getFrame(),
				"Couldn't create a directory for the randomizations!",
				"I tried to create '" + output_dir + "' to store the results of the randomizations, but an error occured. Please ensure that you have permissions to create this directory, and try again, or try another directory."
				);
			mb.go();
			return;
		}

		// we've got everything the interface can provide
		// without locking sl1
		sl1 = taxonDNA.lockSequenceList();

		//
		// Algo for calculating the short name to use:
		// 1.	Get the 'name' of both files.
		// 2.	Use 3 letters from each. Keep increasing this until the names are unique.
		// 3.	One we run off the end of either name, keep adding different numbers until we have a unique.
		// 	(ideally, two identical names will become "blah_blah_blah_whatever_the_length_etc_1" and "..._2")
		// 4.	If either SequenceList doesn't have a File(), we'll call it "rando". *shrugs*
		//
		// Possible failures:
		// 	1.	If one name EXACTLY matches current_index counting; fr'instance,
		// 			test_13579
		// 		against
		//			test_
		//		But even this will eventually work, UNLESS test_.... doesn't end before
		//		the maximum size of the characters.
		//
		//		Now, a cursory google reveals no way of checking this. So I'm randomly
		//		assuming the limit is 255 chars. This will be right two times a day.
		//						  ^---- egregarious crap, but i'm high on
		//						  	coffee, so it's All Good.	
		//
		String name_a_full =	"";
		if(sl1.getFile() == null)
			name_a_full = "rando";
		else
			name_a_full = sl1.getFile().getName();

		String	name_b_full =	"";
		if(sl2.getFile() == null)
			name_b_full = "rando";
		else
			name_b_full = sl2.getFile().getName();

		int current_size = 3;
		int current_index = 1;
		String name_a = name_a_full.substring(0, current_size);
		String name_b = name_b_full.substring(0, current_size);

		while(name_a.equals(name_b)) {
			current_size++;

			if(current_size > name_a_full.length()) {
				name_a = name_a + current_index;
				current_index++;
			} else { 
				name_a = name_a_full.substring(0, current_size);
			}

			if(current_size > name_b_full.length()) {
				name_b = name_b + current_index;
				current_index++;
			} else {
				name_b = name_b_full.substring(0, current_size);
			}

			current_index++;

			if(current_size >= 255) {
				// stop. right now. please.
				name_a = "rando_1";
				name_b = "rando_2";
			}
		}

		// Now ... we just perform $replicates replicates
		// on sl1 and sl2, and spit out the results to
		// output_dir/name_a_$replicate.txt and 
		// output_dir/name_b_$replicate.txt with
		// the kind help if $formatHandler
		//
		// To do this, we have to look up the species counts
		// safely stored up in $hash_seq_counts.
		//
		// What could be simpler?
		Iterator i;
		ProgressDialog pd = new ProgressDialog(
				taxonDNA.getFrame(),
				"Please wait, randomizing sequence lists ...",
				"I'm randomizing down where required, and writing " + replicates + " into " + output_dir + " using the " + fh.getShortName() + " format. Please wait!",
				0
				);
		pd.begin();
		for(int x = 1; x <= replicates; x++) {
				try {
					pd.delay(x, replicates);
				} catch(DelayAbortedException e) {
					pd.end();
					taxonDNA.unlockSequenceList();
					return;
				}
			
			File file_a = new File(output_dir, name_a + "_" + x + ".txt");
			File file_b = new File(output_dir, name_b + "_" + x + ".txt");

			// sl_a and sl_b are NOT locked/unlocked
			// which means that threading should NOT
			// get complicated down here.
			//
			SequenceList sl_a = null;
			SequenceList sl_b = null;
			try {
				sl_a = randomizeDown(sl1);
				sl_b = randomizeDown(sl2);
			} catch(Exception e) {
				pd.end();
				MessageBox mb = new MessageBox(
						taxonDNA.getFrame(),
						"Error during randomizations",
						"An error occured during randomization. This is an internal error, so it's more likely to be a programming problem than anything else.\n\nThe technical description is as follows: " + e
						);
				mb.go();
				taxonDNA.unlockSequenceList();
				return;
			}	

			try {
				fh.writeFile(file_a, sl_a, null);
				fh.writeFile(file_b, sl_b, null);
			} catch(IOException e) {
				pd.end();
				MessageBox mb =	new MessageBox(
					taxonDNA.getFrame(),
					"Couldn't write file!",
					"I couldn't write a file (either '" + file_a + "' or '" + file_b + "'. Make sure you have permissions to create and write those files, and there's enough space on that drive.\n\nThe exact error which came up was: " + e
					);
				mb.go();	
				return;
			} catch(DelayAbortedException e) {
				// this will never happen.
				pd.end();
				taxonDNA.unlockSequenceList();
				return;
			}
		}
		pd.end();

		taxonDNA.unlockSequenceList();
	}

	private SequenceList randomizeDown(SequenceList sl) throws Exception {
		SequenceList results = new SequenceList();

		// sort it, then put the elements out in a first-out way
		sl.resort(SequenceList.SORT_RANDOM_WITHIN_SPECIES);
			
		Iterator i = sl.iterator();
		Sequence seq = (Sequence) i.next();

		while(i.hasNext()) {
			if(hash_seq_counts.get(seq.getSpeciesName()) == null) {
				// oh, noes!
				throw new Exception("Species name '" + seq.getSpeciesName() + "' does not exist in records!");
			}

			int count_original = ((Integer)hash_seq_counts.get(seq.getSpeciesName())).intValue();
			int count = count_original;
			String sp_name = seq.getSpeciesName();
			
			do {	
				// do-while so that we add in 'seq' as defined above.
				if(seq.getSpeciesName().equals(sp_name)) {
					// add seq, it's awwite
					if(count > 0) {
						//System.err.println("ADD: " + seq);
						results.add(seq);
						count--;
					}
				} else {
					// it's not the same! we've run into the next species name.
					// as long as we're out of 'count', we break here.
					if(count > 0) {
						// but we need to add more ... ?!
						// wops!
						throw new Exception("I should add '" + count_original + "' sequences of species '" + sp_name + "', but there are only '" + (count_original - count) + "' sequences of this species name, and I've reached the first " + seq.getSpeciesName() + "!");
					} else {
						// we've added all the 'sp_name'd sequences we need, and now
						// we've hit the next species name. 
						break;
							// note: 	NOT break; break will force us to read in the next seq, skipping one
							// 		continue does NOT skip.
					}
				}
			} while(i.hasNext() && (seq = (Sequence) i.next()) != null);
		}

		return results;
	}

	public void loadAndSetupSpeciesDetails() {
		ProgressDialog pd;
		sl1 	=	taxonDNA.lockSequenceList();
		sl2	=	null;

		text_results.setText("No results to display");

		SpeciesDetails sd1	=	null;
		SpeciesDetails sd2	=	null;

		// Step #1: Load up the second file.
		{
			File file = finp_dataset2.getFile();

			if(file == null) {
				taxonDNA.unlockSequenceList();
				return;
			}
	
			pd = new ProgressDialog(
				taxonDNA.getFrame(),
				"Please wait, loading file ...",
				"I'm loading the other file (" + file + ") into memory now. Sorry for the wait!"
			);

			try {
				sl2 = SequenceList.readFile(file, pd);
			} catch(SequenceListException e) {
				new MessageBox(
						taxonDNA.getFrame(),
						"Problem reading file",
						e.toString()).go();
				taxonDNA.unlockSequenceList();
				return;
			} catch(DelayAbortedException e) {
				taxonDNA.unlockSequenceList();
				return;
			}
		}

		// Step #2: Calculate SpeciesDetails for both files.
		try {
			pd = new ProgressDialog(
				taxonDNA.getFrame(),
				"Please wait, calculating species summaries ...",
				"The species summaries for both sequence lists are being calculated. Sorry for the wait!"
				);
			
			sd1 = sl1.getSpeciesDetails(pd);
			sd2 = sl2.getSpeciesDetails(pd);
		} catch(DelayAbortedException e) {
			taxonDNA.unlockSequenceList();
			return;
		}

		hash_seq_counts	= new Hashtable();

		// Step #3: Display both species details
		{
			StringBuffer text = new StringBuffer();

			Hashtable names = new Hashtable();	// Species name (String) -> new Object (sigh)
			Hashtable h1 = new Hashtable();		// Species name (String) -> Count of ALL sequences  (Integer)
			Hashtable h2 = new Hashtable();		// Species name (String) -> Count of ALL sequences  (Integer)

			Iterator i1 = sd1.getSpeciesNamesIterator();

			while(i1.hasNext()) {
				String name = (String) i1.next();

				h1.put(name, new Integer(sd1.getSpeciesDetailsByName(name).getSequencesCount()));
				names.put(name, new Object());
			}
			
			Iterator i2 = sd2.getSpeciesNamesIterator();
			
			while(i2.hasNext()) {
				String name = (String) i2.next();

				h2.put(name, new Integer(sd2.getSpeciesDetailsByName(name).getSequencesCount()));
				names.put(name, new Object());
			}

			Iterator i = names.keySet().iterator();
			while(i.hasNext()) {
				String name = (String) i.next();

				Integer integ1 = (Integer) h1.get(name);
				Integer integ2 = (Integer) h2.get(name);

				int int1 = 0;
				int int2 = 0;
				if(integ1 != null)
					int1 = integ1.intValue();
				if(integ2 != null)
					int2 = integ2.intValue();


				int fincount = 0;
				if(int2 > int1)
					fincount = int1;
				else
					fincount = int2;
				
				hash_seq_counts.put(name, new Integer(fincount));

				text.append(
						cutTextTo(name, 40) 
						+ cutTextTo("Dataset A: " + int1, 20) 
						+ cutTextTo("Dataset B: " + int2, 20)
						+ cutTextTo("Finally: " + fincount, 20)
						+ "\n");
			}

			text_results.setText(text.toString());
		} 

		taxonDNA.unlockSequenceList();
	}

	private String cutTextTo(String text, int len) {
		if(text.length() < len) {
			StringBuffer padding = new StringBuffer();
			for(int x = 0; x < len - text.length(); x++)
				padding.append(' ');
			return text + padding.toString();
		} else if(text.length() == len) {
			return text;
		} else if(text.length() > len) {
			return text.substring(0, len - 3) + "...";
		} else {
			return "";
		}
	}

	/*
	 * ItemListener
	 */
	public void itemStateChanged(ItemEvent e) {
	}
	
	// UIExtension stuff
	public String getShortName() { return "Randomizer"; }
	
	public String getDescription() { return "Randomizes two datasets together to remove the effect of differential species distributions to affect results"; }
	
	public Frame getFrame() { return null; }
	
}
