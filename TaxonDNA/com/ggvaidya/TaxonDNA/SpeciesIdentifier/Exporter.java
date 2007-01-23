/**
 * Exporter.java
 * 
 * Allows you to export sequences from SpeciesIdentifier,
 * with a set of options to export them to as
 * many different systems as possible.
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


package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Exporter extends Panel implements Runnable, UIExtension, ActionListener, ItemListener {	
	private SpeciesIdentifier	seqId;

	// Choices: what to do, what to say, etc.  
	private Choice		choice_whatToOutput = 		new Choice();
	private Choice		choice_howToOutput =		new Choice();
	private TextField	tf_sizeRestriction =		new TextField();
	private Choice		choice_sizeRestrictions =	new Choice();

	private TextField	tf_taxonIndicatingCharacter =	new TextField();
	private Choice		choice_taxonIndicatingCharacters = new Choice();
	private Choice		choice_insertHeaders =		new Choice();

	// Checkboxen: flag options, basically
	private Checkbox	check_spacesToUnderscore =	new Checkbox("Change spaces to underscore in the taxon name");
	private Checkbox 	check_summaryOnTop =		new Checkbox("Write a summary (number of sequences) at the top");
	private Checkbox	check_setSpeciesMatrix =	new Checkbox("Write the species matrix instead.");
	private Checkbox	check_exportIntoMultiple = 	new Checkbox("Export into multiple by ");
	private Checkbox	check_insertHeaders =		new Checkbox("Insert headers/footers for ");

	// Multiple exporting etc.
	private Choice		choice_multipleExport = 	new Choice();
	
	// tf_status will tell the user how everything went
	private TextField	tf_status =			new TextField();

	// TextArea: where we spit out the output. Will this cause angst on the memory?
	private TextArea	text_main	=	new TextArea();

	// Buttons: Try, Copy to clipboard, Export to file
	private Button		btn_Go		=	new Button("Go!");
	private Button		btn_Copy	=	new Button("Copy to clipboard");
	private Button		btn_Export	=	new Button("Export to file");	

	/**
	 * Add commands to the SpeciesIdentifier menu. We only add one option:
	 * called rather oddly "Custom export"
	 */
	public boolean addCommandsToMenu(Menu menu) {
		MenuItem mi = new MenuItem("Custom export", new MenuShortcut(KeyEvent.VK_E));
		mi.addActionListener(this);
		menu.add(mi);
	
		return true;
	}

	private String getHeader(int count_sequences, int max_length) {
		String format = choice_insertHeaders.getSelectedItem();

		if(format.equals("MEGA"))
			return "#mega\nTITLE SpeciesIdentifier Exporter: " + count_sequences + " exported, of maximum length " + max_length + " bp\n";
		
		if(format.equals("NEXUS"))
			return "#NEXUS\nBEGIN DATA;\n\tDIMENSIONS  NTAX="+count_sequences+" NCHAR="+max_length+";\n\tFORMAT DATATYPE=DNA  MISSING=? GAP=- ;\nMATRIX\n";

		if(format.equals("TNT"))
			return "xread\n" + max_length + " " + count_sequences + "\n";

		return "";
	}

	private String getFooter() {
		String format = choice_insertHeaders.getSelectedItem();

		if(format.equals("NEXUS"))
			return "\n;\nEND;";

		if(format.equals("TNT"))
			return "\n;\nccode -.;\nproc/;";

		return "";
	}
 
	public Exporter(SpeciesIdentifier view) {
		super();

		seqId = view;
		
		// create the panel
		setLayout(new BorderLayout());

		// the options
		Panel options = new Panel();
		RightLayout rl = new RightLayout(options);	// less pain this way

		rl.add(new Label("Please select the options you would like to use, then press the 'Go!' button to see the output."), RightLayout.FILL_3);

		rl.add(new Label("Which values would you like me to output?"), RightLayout.NEXTLINE);
		choice_whatToOutput.add("Full names, as present in the input file");
		choice_whatToOutput.add("Genus, species and GI number");
		choice_whatToOutput.add("GI number, followed by genus and species");
		rl.add(choice_whatToOutput, RightLayout.BESIDE | RightLayout.STRETCH_X | RightLayout.FILL_2);

		rl.add(new Label("How would you like me to arrange names and sequences?"), RightLayout.NEXTLINE);
		choice_howToOutput.add("Taxon name, followed by the sequence name on a new line (like Fasta)");
		choice_howToOutput.add("Taxon name, followed by the sequence name on the same line (like Nexus)");
		rl.add(choice_howToOutput, RightLayout.BESIDE | RightLayout.STRETCH_X | RightLayout.FILL_2);

		rl.add(new Label("How long can I make sequence names?"), RightLayout.NEXTLINE);
		tf_sizeRestriction.setText("9999");
		rl.add(tf_sizeRestriction, RightLayout.BESIDE);

		// Note that the following code assumes that the FIRST '[xxxx ' is the number of
		// characters you want to use! This is very hacky but convenient. So to add new
		// types, just add them into the list below, with the phrase "[ddd letters]"
		// before any other '['s, and All Will Be Well.
		//
		choice_sizeRestrictions.addItemListener(this);
		choice_sizeRestrictions.add("As long as possible [9999 letters]");

		// PAUP* sizes as per http://paup.csit.fsu.edu/Cmd_ref_v2.pdf (last modified Feb 2002)
		choice_sizeRestrictions.add("PAUP* 4.0 unique [16 letters]");
		choice_sizeRestrictions.add("PAUP* 4.0 maximum allowed [127 letters]");

		// done!
		rl.add(choice_sizeRestrictions, RightLayout.BESIDE| RightLayout.STRETCH_X);		
	
		// Taxon Indicator Character
		rl.add(new Label("Which character should I use to indicate taxon names?"), RightLayout.NEXTLINE);
		tf_taxonIndicatingCharacter.setText(">");
		rl.add(tf_taxonIndicatingCharacter, RightLayout.BESIDE);

		// Note that, like the previous hack, this one is pretty brutal as well.
		// Here, the 'character' is surrounded by square brackets, like this: [>]
		choice_taxonIndicatingCharacters.add("Like Fasta [>]");
		choice_taxonIndicatingCharacters.add("Like Mega [#]");
		choice_taxonIndicatingCharacters.add("No characters []");

		choice_taxonIndicatingCharacters.addItemListener(this);

		rl.add(choice_taxonIndicatingCharacters, RightLayout.BESIDE);

		// FLAG OPTIONS
		// Export into multiple files?
		rl.add(check_exportIntoMultiple, RightLayout.NEXTLINE);

		choice_multipleExport.add("one file per family"); 
		rl.add(choice_multipleExport, RightLayout.BESIDE | RightLayout.STRETCH_X | RightLayout.FILL_2);		
	
		// And the header/footer stuff
		rl.add(check_insertHeaders, RightLayout.NEXTLINE);

		choice_insertHeaders.add("MEGA");
		choice_insertHeaders.add("NEXUS");
		choice_insertHeaders.add("TNT");		
		rl.add(choice_insertHeaders, RightLayout.BESIDE | RightLayout.STRETCH_X | RightLayout.FILL_2);
		
		rl.add(check_spacesToUnderscore, RightLayout.NEXTLINE | RightLayout.FILL_3);
		rl.add(check_summaryOnTop, RightLayout.BELOW | RightLayout.FILL_3);
		rl.add(check_setSpeciesMatrix, RightLayout.BELOW | RightLayout.FILL_3);

		// Finally, the button!
		btn_Go.addActionListener(this);
		rl.add(btn_Go, RightLayout.NEXTLINE | RightLayout.FILL_3 | RightLayout.CENTER);

		tf_status.setEditable(false);
		rl.add(new Label("Status of export: "), RightLayout.NEXTLINE);
		rl.add(tf_status, RightLayout.BESIDE | RightLayout.FILL_2 | RightLayout.STRETCH_X);

		add(options, BorderLayout.NORTH);

		// the output
		text_main.setEditable(false);
		text_main.setFont(new Font("Monospaced", Font.PLAIN, 12));
		add(text_main, BorderLayout.CENTER);
		
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Export.addActionListener(this);
		buttons.add(btn_Export);
		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);
		
		add(buttons, BorderLayout.SOUTH);
	}

	/* 
	 * If the data changed, we should update. At any rate, this will keep us in sync
	 * with everybody else.
	 */
	public void dataChanged() {
		text_main.setText("");
	}

	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	private String getSpeciesMatrix(String speciesName, SpeciesDetails sd) {
		if(sd == null)
			return "";

		ArrayList names = new ArrayList(sd.getSpeciesNamesWithMultipleValidSequencesList());
		Collections.sort(names);

		StringBuffer matrix = new StringBuffer();

		// special behavior
		// if speciesName == null, we output a list of characters
		// 	mapping against the corresponding species
		//
		if(speciesName == null) {
			Iterator i = names.iterator();
			int count = 1;
			while(i.hasNext()) {
				String currentName = (String)i.next();
	
				SpeciesDetail sdd = sd.getSpeciesDetailsByName(currentName);
				matrix.append("\t" + count + "\t" + currentName + "\t" + sdd.getSequencesCount() + "\t(" + sdd.getSequencesWithValidMatchesCount() + " valid)\n");
				count++;
			}

			return matrix.toString();
		}

		// basically, this is ridiculously easy:
		// 1.	figure out the total number of sequences
		// 2.	see where (if anywhere) our speciesName figures
		// 3.	turn that bit 'on' (1)

		// 1.	so, the matrix begins "empty"
		for(int x = 0; x < names.size(); x++) {
			matrix.append('0');
		}

		// 2.	so where is our speciesName in this hashtable?
		//
		// WARNING: this will only work if enumerating over the
		// same hashtable has consistent results, which appears
		// to be the case.
		//
		int index = 0;
		Iterator i = names.iterator();
		while(i.hasNext()) {
			String currentName = (String)i.next();
			if(currentName.equals(speciesName)) {
				// 3. turn bit on ... for Greater Justice! etc.
				matrix.setCharAt(index, '1');
				break;
			}

			index++;
		}

		return matrix.toString();
	}

	/**
	 * Does the actual sequence exporting
	 */
	private Hashtable names = new Hashtable();
	private String status = "";
	public void run() {
		boolean		setSpeciesMatrix 	= false;
		int 		multipleMode 		= 0;		// 0: no multiple mode

		setSpeciesMatrix = check_setSpeciesMatrix.getState();

		if(check_exportIntoMultiple.getState()) {
			multipleMode = choice_multipleExport.getSelectedIndex() + 1;
			// 1 = split by family
		}

		// if setSpeciesMatrix is on, we need to calculate SpeciesSummary
		SpeciesDetails 	sd 			= null;		
		if(setSpeciesMatrix) {
			// you CAN'T use this with check_exportIntoMultiple at the moment
			// since it's very hard for us to track the number of species
			// in each family etc.
			if(multipleMode != 0) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Mea culpa!",
						"Sorry, but at present you can't export into multiple files *and* use the 'add species matrix' options. Please unselect one of them.\n\nThis is entirely my fault: it's doable, but very complicated, and I haven't gotten around to doing it yet. Sorry!");
				mb.go();
				return;
			}
		}

		SequenceList sl = seqId.lockSequenceList();
		if(sl == null)
			return;

		if(setSpeciesMatrix) {
			try {
				sd = sl.getSpeciesDetails(
					new ProgressDialog(
						seqId.getFrame(),
						"Please wait, calculating the species details ...",
						"I'm calculating the species details for this sequence set. This might take a while. Sorry!"
						)
					);
			} catch(DelayAbortedException e) {
				seqId.unlockSequenceList();
				return;
			}
			
		}

		seqId.unlockSequenceList();

		ProgressDialog pd = new ProgressDialog(
				seqId.getFrame(),
				"Please wait, writing sequences ...",
				"Please wait, I am writing out the sequences as per your instructions. This shouldn't take too long, but what ARE reusable components for if you can't pop them at the user any time something might take too long to run?"
				);
	
		pd.begin();

		names = new Hashtable();	// forget all the names we used to know ...
		StringBuffer buffer = new StringBuffer();

		// Everything is *completely* different if we are in multiple mode :(
		// Can you smell the hacks?

		int oldsort = -1;
		if(multipleMode == 1) {	// split by family
			oldsort = sl.resort(SequenceList.SORT_BYFAMILY);
		}

		if(check_summaryOnTop.getState()) {
			// we needs a summary!
			buffer.append("No of sequences =\t" + sl.count() + "\n");
			buffer.append("Maximum length of sequences =\t" + sl.getMaxLength() + "\n");		
			buffer.append('\n');
		}
		
		int howToDisplay = choice_howToOutput.getSelectedIndex();
			// 0	=	one below the other
			// 1	=	next to the taxon name

		int maxLength = sl.getMaxLength();

		int count = 0;
		Iterator i = sl.iterator();
		String lastFamily = "";
		StringBuffer current_file = new StringBuffer();		// only used in multiple mode
		int current_file_sequence_count = 0;

		if(setSpeciesMatrix) {
			// we need to increase length to incorporate the species matrix
			maxLength = getSpeciesMatrix("Nonsense name", sd).length();
			current_file.append("\n\nCharacter/species matrix\n" + 
					getSpeciesMatrix(null, sd) +
					"\n\n");
		}

		try {
			while(i.hasNext()) {
				Sequence s = (Sequence)i.next();

				count++;
				try {
					pd.delay(count, sl.count());
				} catch(DelayAbortedException e) {
					tf_status.setText("Aborted by user");
					return;
				}

				// multipleMode hacks
				if(multipleMode == 1) {
					if(!s.getFamilyName().equals(lastFamily)) {
						lastFamily = s.getFamilyName();
						String familyName = lastFamily.replace(' ', '_');
						if(familyName.equals(""))
							familyName = "Unknown"; 

						// dump current_file onto the buffer, unless it's empty
						if(current_file.length() > 0) {
							if(check_insertHeaders.getState())
								buffer.append(getHeader(current_file_sequence_count, maxLength));
						
							buffer.append(current_file);
							current_file_sequence_count = 0;
							current_file = new StringBuffer();
							
							if(check_insertHeaders.getState())
								buffer.append(getFooter());
						}
							
						buffer.append("[taxondna_begin_new_file:" + familyName + ".txt]\n");
					}
				}

				String name = getSequenceName(s);

				String taxonIndicatingCharacter = tf_taxonIndicatingCharacter.getText();

				current_file.append(taxonIndicatingCharacter);
				current_file.append(name);

				current_file_sequence_count++;

				if(setSpeciesMatrix) {
					current_file.append("\t" + getSpeciesMatrix(s.getSpeciesName(), sd));
				} else if(howToDisplay == 1) 
					// one beside the other
					current_file.append("\t" + s.getSequence());
				else
					// one below the other
					current_file.append("\n" + s.getSequenceWrapped(70));

				current_file.append('\n');
			}
		} finally {
			if(multipleMode == 1) {
				// unsort the list
				sl.resort(oldsort);
			} else if(multipleMode == 0) {
				// current_file is the ENTIRE buffer
				if(check_insertHeaders.getState())
					buffer.append(getHeader(sl.count(), maxLength));

				buffer.append(current_file);
	
				if(check_insertHeaders.getState())
					buffer.append(getFooter());			
			}
		}

		text_main.setText(buffer.toString());

		if(status.equals(""))
			tf_status.setText("No problems encountered!");
		else
			tf_status.setText("I had to change some things. For instance: " + status);

		// forget all the new names we have found
		names = null;

		pd.end();

		seqId.unlockSequenceList();
	}

	/**
	 * This function processes the name of a sequence
	 * (based on the settings provided) and returns it.
	 */
	private String getSequenceName(Sequence seq) {
		String name = "";
		int whatToOutput = choice_whatToOutput.getSelectedIndex();
			// 0 = full name
			// 1 = genus species gi
			// 2 = gi genus species
			
		switch(whatToOutput) {
			case 0:
				name = seq.getFullName();
				break;
			case 1:
				name = seq.getSpeciesName() + " " + seq.getGI();
				break;
			case 2:
			default:
				name = seq.getGI() + " " + seq.getSpeciesName();
				break;
		}

		// check for size and uniqueness
		int max_size = 0;

		try {
			max_size = Integer.parseInt(tf_sizeRestriction.getText(), 10);
		} catch(NumberFormatException e) {
			max_size = 999;
			status = "Could not understand size restriction: " + tf_sizeRestriction.getText();
		}

		String genus = seq.getGenusName();
		int genus_length = genus.length();

		String species = seq.getSpeciesNameOnly();
		int species_length = species.length();

		String gi = seq.getGI();
		if(gi == null)
			gi = "";
		int gi_length = gi.length();
		
			String potential = name;	// potential name

			if(potential.length() > max_size) {
				// we'll have to shrink it
				if(max_size >= (1 + 1 + species_length + 1 + gi_length)) {
					// we're okay if we just shrink the genus name
					int diff = max_size - (0 + 1 + species_length + 1 + gi_length);
					if(diff > genus.length())
						diff = genus.length();
					genus = genus.substring(0, diff);
					if(whatToOutput == 0) {
						potential = genus + " " + species + " " + gi;
						status = "Had to truncate full information to genus/species/gi";
					} else if(whatToOutput == 1) {
						potential = genus + " " + species + " " + gi;
					} else if(whatToOutput == 2) {
						potential = gi + " " + genus + " " + species;
					}
				} else if(max_size >= (1 + 1 + 1 + 1 + gi_length)) {
					// we're okay if we shrink species name AND genus name
					genus = genus.substring(0, 1);
					int diff = max_size - (1 + 1 + 0 + 1 + gi_length);
					if(diff > species.length())
						diff = species.length();
					species = species.substring(0, diff);

					if(whatToOutput == 0) {
						potential = genus + " " + species + " " + gi;
						status = "Had to truncate full information to genus/species/gi";
					} else if(whatToOutput == 1) {
						potential = genus + " " + species + " " + gi;
					} else if(whatToOutput == 2) {
						potential = gi + " " + genus + " " + species;
					}					
				} else if(max_size >= gi_length) {
					// okay, drop everything, go with GI only
					
					potential = gi;
				} else if(max_size == 0) {
					potential = "";
				} else if(max_size < gi_length) {
					// err, nothing we can do; let's go with 
					
					potential = gi.substring(0, gi_length - (gi_length - max_size) - 1) + "_";  
					status = "GI numbers have been truncated";
				}


				if(potential.length() > max_size)
					throw new RuntimeException("I shrunk the name from " + name.length() + " to " + potential.length() + " but it's still not " + max_size + "!");
			}

			// shrinking done, we have one definite "potential" name now
			if(names.get(name) != null) {
				// this name already exists in the database!
				Integer i = (Integer) names.get(name);
				int x = i.intValue();
				x++;
				String identifier = String.valueOf(x);

				if(potential.length() + 1 + identifier.length() > max_size) {
					name = potential + "_" + identifier;
					names.put(name, new Integer(x));
				} else {
					// uh oh ...
					// so we're going to have to rather painfully shrink it
					name = potential.substring(0, potential.length() - 3) + "_" + identifier;
					status = "I had to truncate and number some names to ensure uniqueness";
					names.put(name, new Integer(x));	
				}
			} else {
				names.put(potential, new Integer(1));
				name = potential;
			}


		if(check_spacesToUnderscore.getState())
			name = name.replace(' ', '_');

		return name;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getActionCommand().equals("Custom export")) {
			seqId.goToExtension(getShortName());	
		} else if(evt.getSource().equals(btn_Go))
			new Thread(this).start();
		else if(evt.getSource().equals(btn_Copy)) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
					
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Couldn't copy the sequences!",
						seqId.getMessage(Messages.COPY_TO_CLIPBOARD_FAILED, e)
						);

				mb.go();
				return;
			}
		} else if(evt.getSource().equals(btn_Export)) {
			File f = null;

			if(check_exportIntoMultiple.getState()) {
				MessageBox mb = new MessageBox(
					seqId.getFrame(),
					"Multiple Mode File Export",
					"You have selected multiple mode. Please select a single file in which to save summary information. Other files will be created in the same directory. ANY FILE WITH THE SAME NAME IN THIS DIRECTORY WILL BE OVERWRITTEN."
				);
				
				mb.go();
			}

			FileDialog fd = new FileDialog(
					seqId.getFrame(),
					"Where would you like to export these sequences?",
					FileDialog.SAVE
				);

			fd.setVisible(true);

			if(fd.getFile() == null)
				return;
			else if(fd.getDirectory() == null)
				f = new File(fd.getFile());
			else
				f = new File(fd.getDirectory() + fd.getFile());

			ProgressDialog pd = new ProgressDialog(
					seqId.getFrame(),
					"Please wait, exporting sequences ...",
					"Sequences are being exported into the file(s) you requested.",
					ProgressDialog.FLAG_NOCANCEL
				);

			pd.begin();

			SequenceList sl = seqId.lockSequenceList();

			try {


				PrintWriter p = new PrintWriter(new FileWriter(f));

				// we do magic things if we find a \[taxondna_begin_new_file:.*\]
				//
				if(check_exportIntoMultiple.getState()) {
					BufferedReader br = new BufferedReader(new StringReader(text_main.getText()));
					Pattern pattern_new_file = Pattern.compile("^\\[taxondna_begin_new_file:(.*)\\]$");
					File fileParent = f.getParentFile();
					File fileOriginal = f;

					int count = 0;
					while(br.ready()) {
						String s = br.readLine();

						if(s == null)
							break;

						Matcher m = pattern_new_file.matcher(s);
						
						if(m.matches()) {
							String filename = m.group(1); 
													
							p.close();

							if(filename.equals("*")) {	// magic symbol
								f = fileOriginal;
							} else {
								f = new File(fileParent, filename);
							}

							p = new PrintWriter(new FileWriter(f));
						} else  {
							p.println(s);
						}

					}
				} else {
					p.print(text_main.getText());
				}

				p.close();

				return;

			} catch(IOException e) {
				MessageBox mb = new MessageBox(
						seqId.getFrame(),
						"Error while exporting file " + f,	
						seqId.getMessage(Messages.IOEXCEPTION_WRITING, f, e)
						);

				mb.go();
				return;
			} finally {
				seqId.unlockSequenceList();
				pd.end();
			}
		}

	}

	// item listener
	public void itemStateChanged(ItemEvent evt) {
		if(evt.getSource().equals(choice_sizeRestrictions)) {
			String str = (String)evt.getItem();

			int index = str.indexOf("[");
			if(index != -1) {
				index++;

				int index_until = str.substring(index).indexOf(' ') + index;
				tf_sizeRestriction.setText(String.valueOf(Integer.parseInt(str.substring(index, index_until))));
			}
		}

		if(evt.getSource().equals(choice_taxonIndicatingCharacters)) {
			String str = (String)evt.getItem();

			int index = str.indexOf("[");
			if(index != -1) {
				index++;

				int index_until = str.substring(index).indexOf(']') + index;
				tf_taxonIndicatingCharacter.setText(str.substring(index, index_until));
			}
		}
	}

	// UIExtension stuff
	public String getShortName() { return "Export this dataset"; }
	
	public String getDescription() { return "Allows you to export this dataset in a custom format"; }
	
	public Frame getFrame() { return null; }
	
}
