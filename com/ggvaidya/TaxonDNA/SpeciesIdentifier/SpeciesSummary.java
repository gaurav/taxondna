/**
 * Summarise species information. 
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005, 2007

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
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;
import com.ggvaidya.TaxonDNA.Model.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class SpeciesSummary extends Panel implements UIExtension, Runnable, ActionListener, ItemListener {
	private SpeciesIdentifier	seqId = null;
	private TextArea	text_main = new TextArea();

	private java.awt.List	list_species = new java.awt.List();
	private Button		btn_Calculate = new Button("Calculate now!");
	private Button		btn_Delete = new Button("Remove selected species");
	private Button		btn_export_multiple = new Button("Export species with multiple sequences");
	private Button		btn_export_with_cons = new Button("Export sequences with conspecifics");
	private Button		btn_Copy = new Button("Copy species summary");

	private Button		btn_exportRandomly = new Button("Export a random subset");
	
	private Vector		vec_Species =		null;

	private boolean		flag_weChangedTheData = false;

	/**
	 * Constructor. Needs one seqId object.
	 */
	public SpeciesSummary(SpeciesIdentifier seqId) {
		this.seqId = seqId;
	
		// layouting
		setLayout(new BorderLayout());

		Panel top = new Panel();
		top.setLayout(new BorderLayout());

		btn_Calculate.addActionListener(this);
		top.add(btn_Calculate, BorderLayout.NORTH);

		text_main.setEditable(false);
		top.add(text_main);

		add(top, BorderLayout.NORTH);

		list_species.addItemListener(this);
		list_species.addActionListener(this);
		add(list_species);

		// the 'south' panel on the main BorderLayout
		// is known simply as 'below'
		Panel below = new Panel();
		below.setLayout(new BorderLayout());

		// 'below' consists of two Panels of its own:
		// a 'north' panel, called actions
		// and a 'south' panel called functions
		//
		// 'actions' contains a list of things you can do
		// to specific entries in the SpeciesSummary list.
		Panel actions = new Panel();
		actions.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Delete.addActionListener(this);
		btn_Delete.setEnabled(false);
		actions.add(btn_Delete);

		btn_exportRandomly.addActionListener(this);
		actions.add(btn_exportRandomly);
		
		btn_Copy.addActionListener(this);
		actions.add(btn_Copy);
		
		below.add(actions, BorderLayout.NORTH);

		// while 'functions' contains a list of
		// things you can do to the entire 'species summary'
		// list.
		Panel functions = new Panel();
		functions.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_export_multiple.addActionListener(this);
		functions.add(btn_export_multiple);

		btn_export_with_cons.addActionListener(this);
		functions.add(btn_export_with_cons);

		below.add(functions, BorderLayout.SOUTH);
		add(below, BorderLayout.SOUTH);
	}

	/**
	 * actionListener. We're listening to events as they come in.
	 */
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(btn_exportRandomly)) {
			exportRandomly();
			return;	
		} else if(e.getSource().equals(btn_Calculate)) {
			new Thread(this, "SpeciesSummary").start();
			return;
		}
		if(e.getSource().equals(btn_export_with_cons)) {
			// export sequences with conspecific sequences
			SequenceList sl = seqId.lockSequenceList();

			if(sl == null)
				return;
				
			try {
				SpeciesDetails sd = sl.getSpeciesDetails(ProgressDialog.create(
						seqId.getFrame(),
						"Please wait, determining sequences with conspecifics ...",
						"Please wait, I'm looking through the sequences to determine which ones have conspecific sequences. Give me a second!"));

				SequenceList sl_results = sd.getSequencesWithValidConspecifics();
				if(sl_results == null) // cancelled?
					return;

				if(sl_results.count() == 0) {
					new MessageBox(seqId.getFrame(),
							"No sequences found!",
							"This dataset does not contain any sequences with valid conspecific matches!").go();
					return;
				}

				// okay, done!
				// export time!
				FileDialog fd = new FileDialog(seqId.getFrame(), "Export sequences with valid conspecifics as Fasta files to ...", FileDialog.SAVE);
				
				fd.setVisible(true);	// go!
				
				File f = null;
				if(fd.getFile() != null) {
					if(fd.getDirectory() != null)	
						f = new File(fd.getDirectory(), fd.getFile());
					else
						f = new File(fd.getFile());

					// assume that 'f' has already been tested for existance, and overwriting verified by FileDialog.

					try {
						com.ggvaidya.TaxonDNA.Model.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.Model.formats.FastaFile();
						ff.writeFile(f, new SequenceList(sl_results), null);
					} catch(Exception ex) {
						new MessageBox(seqId.getFrame(),
							"Error!",
							"There was a problem while exporting species with multiple sequences. The technical description of the error is: " + ex.getMessage() + "\n\nAre you sure you have adequate permissions to write to that file, and the disk isn't empty? If not, it's probably a programming problem. Please let the developers know!",
							MessageBox.MB_ERROR).go();

						return;
					}

					new MessageBox(seqId.getFrame(),
							"Success!",
							sl_results.count() + " sequences containing atleast one valid conspecific sequence exported to " + f + " in the FASTA format.").go();
				}

			} catch(DelayAbortedException ex) {
				return;
			} finally {
				seqId.unlockSequenceList();	
			}
		} 
		if(e.getSource().equals(btn_export_multiple)) {
			SequenceList list = seqId.lockSequenceList();
				
			if(list == null)
				return;

			SequenceList result = new SequenceList();
			
			Iterator i = list.iterator();
			Hashtable species = new Hashtable();
			while(i.hasNext()) {
				DNASequence seq = (DNASequence) i.next();
	
				Integer integer = (Integer) species.get(seq.getSpeciesName());
				if(integer == null) {
					integer = new Integer(1);
					species.put(seq.getSpeciesName(), integer);
				} else {
					species.put(seq.getSpeciesName(), new Integer(integer.intValue() + 1));
				}	
			}

			i = list.iterator();
			while(i.hasNext()) {
				DNASequence seq = (DNASequence) i.next();

				Integer integ = (Integer) species.get(seq.getSpeciesName());

				if(integ.intValue() > 1)
				{
					try {
						result.add(seq);
					} catch(Exception ex) {
						ex.printStackTrace();
					}
				}
			}

			FileDialog fd = new FileDialog(seqId.getFrame(), "Export sequences to Fasta file ...", FileDialog.SAVE);
			fd.setVisible(true);

			File file = null;
			if(fd.getFile() != null) {
				if(fd.getDirectory() != null)
					file = new File(fd.getDirectory() + fd.getFile());
				else
					file = new File(fd.getFile());

				try {
					com.ggvaidya.TaxonDNA.Model.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.Model.formats.FastaFile();
					ff.writeFile(file, new SequenceList(result), null);
				} catch(Exception ex) {
					seqId.unlockSequenceList();
					
					new MessageBox(seqId.getFrame(),
							"Error!",
							"There was a problem while exporting species with multiple sequences. The technical description of the error is: " + ex.getMessage() + "\n\nAre you sure you have adequate permissions to write to that file, and the disk isn't empty? If not, it's probably a programming problem. Please let the developers know!",
							MessageBox.MB_ERROR).go();
					return;
				}
			}

			seqId.unlockSequenceList();
			new MessageBox(seqId.getFrame(),
					"Done!",
					result.count() + " sequences were successfully exported to " + file + " in the Fasta format.").go();
		}

		if(e.getSource().equals(btn_Copy)) {
			StringBuffer text_use = new StringBuffer();

			text_use.append(text_main.getText() + "\n\n");

			if(list_species.getItemCount() != 0) {
				for(int x = 0; x < list_species.getItemCount(); x++) {
					text_use.append(list_species.getItem(x) + "\n");
				}
			}
			
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_use.toString());
				
				clip.setContents(selection, selection);
			
				btn_Copy.setLabel("Copy species summary");
			} catch(IllegalStateException ex) {
				btn_Copy.setLabel("Oops, try again?");
			}
		}

		if(e.getSource().equals(btn_Delete)) {
			if(vec_Species == null)
				return;

			int selected = list_species.getSelectedIndex();
			if(selected == -1)
				return;

			String sp_name = (String) vec_Species.get(selected);
			SequenceList list = seqId.lockSequenceList();
			SpeciesDetail det = null;
			try {
				det = list.getSpeciesDetails(null).getSpeciesDetailsByName(sp_name);
			} catch(DelayAbortedException ex) {
				// wtf
				return;
			}
			seqId.unlockSequenceList();

			if(det == null) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"This species does not exist!",
						"I cannot find any sequences with the species '" + sp_name + "'! Are you sure you have't already removed or renamed sequences?\n\nTry recalculating the Species Summary. If this species still appears, there might be a programming error in this program.");
				mb.go();
				
				return;
			}
			int count = det.getSequencesCount();

			MessageBox mb = new MessageBox(
				seqId.getFrame(),
				"Are you sure?",
				"Are you sure you want to delete all " + count + " sequences belonging to the species '" + sp_name + "'?\n\nThis cannot be undone!",
				MessageBox.MB_YESNO);
			if(mb.showMessageBox() == MessageBox.MB_YES) {
				list = seqId.lockSequenceList();
				Iterator i = list.conspecificIterator(sp_name);
				int x = 0;
				while(i.hasNext()) {
					i.next();
					i.remove();
					x++;
				}
				System.err.flush();

				vec_Species.remove(sp_name);
				list_species.remove(selected);

				flag_weChangedTheData = true;
				seqId.sequencesChanged();
				seqId.unlockSequenceList();

				mb = new MessageBox(
						seqId.getFrame(),
						"Sequences deleted!",
						x + " sequences were successfully deleted."
						);
				mb.go();
			}
		}

		if(e.getSource().equals(list_species)) {
			String seqName = (String) vec_Species.get(list_species.getSelectedIndex());
			if(seqName == null)
				return;

			// TODO: Make this faster, if I ever get the time to.
			SequenceList list = seqId.lockSequenceList();
			list.resort(SequenceList.SORT_BYNAME);
			DNASequence seq = null;
			Iterator i = list.iterator();
			while(i.hasNext()) {
				DNASequence seq_x = (DNASequence) i.next();

				if(seq_x.getSpeciesName().equals(seqName)) {
					seq = seq_x;
					break;
				}
			}

			if(seq != null)
				seqId.getSequencePanel().selectSequence(seq);
			seqId.unlockSequenceList();
		}
	}

	/**
	 * Somebody selected something in list_species.
	 */
	public void  itemStateChanged(ItemEvent e) {
		if(e.getSource().equals(list_species)) {
			switch(e.getStateChange()) {
				case ItemEvent.DESELECTED:
					btn_Delete.setEnabled(false);
					break;
				case ItemEvent.SELECTED:
					btn_Delete.setEnabled(true);
					break;
			}
		}
	}
	
	/**
	 * Data got changed. We just reset everything and wait.
	 */
	public void dataChanged() {
		if(flag_weChangedTheData) {
			flag_weChangedTheData = false;
			return;
		}

		text_main.setText("");
		list_species.removeAll();
		vec_Species = null;
	}

	/**
	 * Data processing and calculations happen in here.
	 */
	public void run() {
		SequenceList list = seqId.lockSequenceList();
		if(list == null) {
			text_main.setText("No sequences loaded!");
			seqId.unlockSequenceList();
			return;
		}
		SpeciesDetails species = null;

		try {
			species = list.getSpeciesDetails(
					ProgressDialog.create(seqId.getFrame(), "Please wait, calculating species information ...", "Species summary information is being calculated. Sorry for the wait.", 0)
				);
		} catch(DelayAbortedException e) {
			seqId.unlockSequenceList();
			return;
		}

		vec_Species = new Vector();		

		// now we use information from 'info' to populate stuff up.
		//
		StringBuffer str = new StringBuffer();

		str.append("Number of sequences: " + list.count() + "\n");			// check
		str.append("Number of species: " + species.count() + "\n\n");			// check
		str.append("Number of sequences without a species name: " + species.getSequencesWithoutASpeciesNameCount()+ "\n\n");
												// check
		str.append("Number of sequences shorter than " + DNASequence.getMinOverlap() + " base pairs: " + species.getSequencesInvalidCount() + "\n");	// check
		
		str.append("Number of sequences with atleast one valid conspecific sequence: " + species.getSequencesWithValidConspecificsCount() + " (" + com.ggvaidya.TaxonDNA.Model.Settings.percentage(species.getSequencesWithValidConspecificsCount(), list.count()) + "%)\n");
		str.append("Number of species with valid conspecifics: " + species.getValidSpeciesCount() + " (" + com.ggvaidya.TaxonDNA.Model.Settings.percentage(species.getValidSpeciesCount(), species.count()) + "% of all species)\n");
		// set up list_species
		//
		list_species.removeAll();

		Iterator i = species.getSpeciesNamesIterator();
		int index = 0;
		while(i.hasNext()) {
			String name = (String) i.next();
			SpeciesDetail det = species.getSpeciesDetailsByName(name);

			int count_total = det.getSequencesCount();
			int count_valid = det.getSequencesWithValidMatchesCount();
			int count_invalid = det.getSequencesWithoutValidMatchesCount();
			String gi_list = det.getIdentifiersAsString();
		
			index++;
			list_species.add(index + ". " + name + " (" + count_total + " sequences, " + count_valid + " valid, " + count_invalid + " invalid): " + gi_list);

			vec_Species.add(name);
		}

		text_main.setText(str.toString());
		seqId.unlockSequenceList();
	}

	// OUR USUAL UIINTERFACE CRAP
	public String getShortName() {		return "Species Summary"; 	}
	public String getDescription() {	return "Information on the species present in this dataset"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}

	/**
	 * exportRandomly() lets you export species (or, hopefully, sequences) at random.
	 * It pops up a little dialog box, which lets you pick the number of sequences (or species)
	 * to export, the format to export the remaining sequences in, and the directory into
	 * which to spit them out. 
	 */
	public void exportRandomly() {
		if(vec_Species == null)
			return;

		// now, we pop up the dialog box
		Dialog w = new Dialog(seqId.getFrame(), "Which species would you like to export?", true);
		CloseableWindow.wrap(w);

		RightLayout rl = new RightLayout(w);
		w.setLayout(rl);

		DirectoryInputPanel dinp = new DirectoryInputPanel("Directory to export random subsets to:", DirectoryInputPanel.MODE_DIR_SELECT, w);
		rl.add(dinp, RightLayout.FILL_2);

		rl.add(new Label("Please choose either ..."), RightLayout.NEXTLINE | RightLayout.FILL_2);

		CheckboxGroup check_group = new CheckboxGroup();

		Checkbox ch_species = new Checkbox("Number of species to export:", check_group, true);
		rl.add(ch_species, RightLayout.NEXTLINE);
		Choice ch_no_of_species = new Choice();
		for(int x = 0; x < vec_Species.size(); x++) {
			ch_no_of_species.add((x + 1) + " species (" + percentage(x + 1, vec_Species.size()) + "%)");
		}
		rl.add(ch_no_of_species, RightLayout.BESIDE);

		SequenceList sl = seqId.lockSequenceList();
		Checkbox ch_specimens = new Checkbox("Number of specimens to export:", check_group, false);
		rl.add(ch_specimens, RightLayout.NEXTLINE);
		Choice ch_no_of_specimens = new Choice();
		for(int x = 0; x < sl.count(); x++) {
			ch_no_of_specimens.add((x + 1) + " specimens (" + percentage(x + 1, sl.count()) + "%)");
		}
		rl.add(ch_no_of_specimens, RightLayout.BESIDE);
		seqId.unlockSequenceList();

		rl.add(new Label("Number of randomizations:"), RightLayout.NEXTLINE);

		TextField tf_rands = new TextField();
		rl.add(tf_rands, RightLayout.BESIDE);

		DefaultButton 	btn_go = 	new DefaultButton(w, "OK");
		DefaultButton 	btn_cancel = 	new DefaultButton(w, "Cancel");

		rl.add(btn_go, RightLayout.NEXTLINE | RightLayout.STRETCH_X);
		rl.add(btn_cancel, RightLayout.BESIDE | RightLayout.STRETCH_X);

		w.pack();
		w.setVisible(true);

		if(btn_go.wasClicked()) {
			// go go go!
			int rands = 10;
			int species = 1;
			int specimens = 1;

			try {
				rands = Integer.parseInt(tf_rands.getText());	
			} catch(NumberFormatException e) {
				rands = 10;
			}

			species = ch_no_of_species.getSelectedIndex() + 1;
			specimens = ch_no_of_specimens.getSelectedIndex() + 1;
			
			File dir = dinp.getFile();

		//	System.err.println("Try, dir = " + dir);
			if(dir == null)
				// goto, goto damnit!
				// TODO: fix up DINP so it doesn't return up 'null' directories.
				exportRandomly();
		//	System.err.println("Done, dir = " + dir)

			try {
				Checkbox ch_selected = check_group.getSelectedCheckbox();
				if(ch_selected == ch_species) {
					exportSpeciesRandomly(dir, species, rands, ProgressDialog.create(
							seqId.getFrame(),
							"Please wait, exporting species ...",
							"Now exporting species to " + dir + ", sorry for the delay!"));
					new MessageBox(seqId.getFrame(),
						"All done!",
						rands + " randomizations of " + species + " species each were exported to " + dir).go();

				}
				else {
					exportSpecimensRandomly(dir, specimens, rands, ProgressDialog.create(
							seqId.getFrame(),
							"Please wait, exporting specimens ...",
							"Now exporting specimens to " + dir + ", sorry for the delay!"));
					new MessageBox(seqId.getFrame(),
						"All done!",
						rands + " randomizations of " + specimens + " specimens each were exported to " + dir).go();
					
				}

			} catch(IOException e) {

			} catch(DelayAbortedException e) {

			}

		}
	}

	public void exportSpeciesRandomly(File dir, int species, int rands, ProgressDialog pd) throws IOException, DelayAbortedException {
		if(vec_Species == null)
			return;

		if(pd != null)
			pd.begin();

		for(int x = 1; x <= rands; x++) {
		//	System.err.println("x = " + x);

			if(pd != null)
				pd.delay(x, rands);

			File f = null;
			f = new File(dir, species + "_species_randomization_" + x + ".txt");
			
			Vector v_from = new Vector();
			Vector v_to = new Vector();

			v_from.addAll(vec_Species);
			Random r = new Random();

			for(int c = 0; c < species; c++) {
				int index = r.nextInt(v_from.size());

				Object o = v_from.get(index);
				v_to.add(o);
				v_from.remove(o);
			}

			SequenceList sl_exp = new SequenceList();

			Iterator i_to = v_to.iterator();
			SequenceList sl = seqId.lockSequenceList();
			int tmp_count = 0;
			while(i_to.hasNext()) {
				tmp_count++;
			//	System.err.println("tmp_count = " + tmp_count);

				String spName = (String) i_to.next();

				// export spName to a file
				Iterator i_sp = sl.conspecificIterator(spName);

				System.err.println("cons found");

				while(i_sp.hasNext()) {
					DNASequence seq = (DNASequence) i_sp.next();
					sl_exp.add(seq);
				}

			//	System.err.println("sec copied");
			}
			seqId.unlockSequenceList();
			
			FastaFile ff = new FastaFile();

			try {
				ff.writeFile(f, sl_exp, null);
			} catch(IOException e) {
				if(pd != null)
					pd.end();
				throw e;
			}
		}

		if(pd != null)
			pd.end();
	}

	public void exportSpecimensRandomly(File dir, int specimens, int rands, ProgressDialog pd) throws IOException, DelayAbortedException {
		if(pd != null)
			pd.begin();

		for(int x = 1; x <= rands; x++) {
		//	System.err.println("x = " + x);

			if(pd != null)
				pd.delay(x, rands);

			File f = null;
			f = new File(dir, specimens + "_specimens_randomization_" + x + ".txt");
			
			Vector v_from = new Vector();
			Vector v_to = new Vector();

			SequenceList sl = seqId.lockSequenceList();

			v_from.addAll(sl);
			Random r = new Random();
			
			seqId.unlockSequenceList();

			for(int c = 0; c < specimens; c++) {
				int index = r.nextInt(v_from.size());

				Object o = v_from.get(index);
				v_to.add(o);
				v_from.remove(o);
			}

			SequenceList sl_exp = new SequenceList();
			sl_exp.addAll(v_to);

			FastaFile ff = new FastaFile();

			try {
				ff.writeFile(f, sl_exp, null);
			} catch(IOException e) {
				if(pd != null)
					pd.end();
				throw e;
			}
		}

		if(pd != null)
			pd.end();
	}

	public double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.Model.Settings.percentage(x, y);
	}
}
