/**
 * ClustalMapping.java
 *	'Maps' sequence names into Clustal-acceptable names, then back
 *	from Clustal (once processing is completed).
 *
 * How this works:
 * 1.	The exported AlignmentHelper input file is actually a complete FASTA file, with 
 * 	fullNames changed.
 * 		fullName(new) = "seq" + uniqueId;
 * 	Here, the uniqueId is the GI, unless no GI exists, in which case
 * 	we use the Sequences' UUID (note that the UUID contains a '_').
 * 	Furthermore, the GI will NOT include the sequence information
 * 	(i.e., if the reported GI is '10241:&lt;1034-1234', SpeciesIdentifier
 * 	will use only consider the '10241' as the GI number)
 * 	
 * 	Example names include:
 * 		&gt;seq12930110 (GI number)
 * 		&gt;seqU11129439_103 (UUID)
 * 		
 * 	Yes, this does mean complete duplication of the dataset, but the only
 * 	alternative (feed in the original file again) will involve a further
 * 	complication of an already cluttered user interface, we won't go there.	
 *
 * 	Note that where UUIDs are used, the original file is REPLACED (and saved
 * 	back in its new position), with '[uniqueid:11129439_103]' added to the
 * 	end of the sequence. IF THIS HAPPENS, WE WILL RESAVE THIS FILE.
 * 
 * 2.	To avoid confusion, we ALWAYS close the present file before we do anything
 * 	else. This is also confusing, but hopefully less so. Oh well.
 * 	
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2007

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

package com.ggvaidya.TaxonDNA.GenBankExplorer;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class ClustalMapping {
	private GenBankExplorer	explorer	=	null;	
	private SequenceList	list_final	=	null;
	private SequenceList	list_missing	=	null;

	private JDialog		dialog		=	null;

	public ClustalMapping(GenBankExplorer exp) {
		explorer = exp;
	}

	public void createUI() {
		dialog = new JDialog(explorer.getFrame(), "Clustal Mapping exports ...", true);		// modal
		JPanel p = new JPanel(); 

		RightLayout rl = new RightLayout(p);
		p.setLayout(rl);

		rl.add(new Button("list 1"), RightLayout.NONE);
		rl.add(new Button("infor text"), RightLayout.BESIDE | RightLayout.STRETCH_X);
		rl.add(new Button("list 2"), RightLayout.BESIDE);
		rl.add(new Button("list 3"), RightLayout.BESIDE);

		dialog.getContentPane().setLayout(new BorderLayout());
		dialog.getContentPane().add(p);
	}

	private Frame getFrame() {
		return explorer.getFrame();
	}

	private File getExportedFile() {
		// TODO
		return null;
	}

	public void go() {
		createUI();

		dialog.pack();
		dialog.setVisible(true);
	}

	//
	// Ancient, 2005-era SpeciesIdentifier code lies ahead.
	// Here be dragons.
	//

	/**
	 * Imports all the data in inputFile (combined with the present file, which acts as
	 * a mapfile) into a NEW sequenceSet, which is spawned off into it's own SpeciesIdentifier.
	 * (it's the only way i can think of of doing this without confusing the user, or
	 * obliterating his dataset without any complaints). Missing sequences will have
	 * the warning flag set, so they "fall" to the bottom of the list.
	 *
	 * @return null; the final sequences are stored in list_final, and the missing sequences in list_missing.
	 */
	public void importSequenceSet(SequenceList set_initial, File inputFile) {
		SequenceList set_final = null;
		SequenceList set_map = new SequenceList(set_initial);	
								// we need to work on set_map,
								// but can't rewrite the original
								// (particularly not iMap.remove!)
								// so cloning it for future use

		// what if we have NO set_map? (i.e. no files loaded into SpeciesIdentifier)
		// go with nothing at all :) [or an empty list, to be fractionally less poetic]
		if(set_map == null || set_map.count() == 0) 
			set_map = new SequenceList();

		FastaFile ff = new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile();
		String error_occured_in = "";

		// Load up the datafile 
		error_occured_in = inputFile.toString();
		try {
			set_final = new SequenceList(inputFile, ff,
					new ProgressDialog(
						getFrame(),
						"Loading '" + inputFile + "' ...",
						"Loading the sequences from '" + inputFile + "', please wait."
					)
				);
		} catch(SequenceListException e) {
			MessageBox mb = new MessageBox(getFrame(), "There is an error in '" + error_occured_in + "'!", "The following error occured while trying to read '" + error_occured_in + "'. Please make sure that it has been formatted correctly."); 
			mb.go();
			return;
		} catch(DelayAbortedException e) {
			// go on
			return;
		}

		if(set_final == null)		// what happened? probably aborted.
			return;			// can't be a problem with the file,
						// because then we would have thrown
						// a SequenceListException
		
		// the following is a three-step process:
		// 1.	Go through set_map, fixing the names as you go.
		// 		i.e. 	see if $no exists in set and fix
		// 			set a Vector based on what's done
		// 2.	Go through vector, adding the missing into set_missing
		// 		(from set_map)
		// Note that this:
		// 1.	Leaves unfixed sequence names as is in set. Which is fine.
		// 2.	? Can't think of any other problems. Let's see.
		//
		Pattern pSequenceDDD = Pattern.compile("^seq(.*)$");
		Iterator i = set_final.iterator();
		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();
			String name = seq.getFullName();

			Matcher m = pSequenceDDD.matcher(name);
			if(m.matches() && m.groupCount() == 1) {
				String no = m.group(1);

				// no == 0 if there was an error in parseInt
				// but no cannot be zero (unless ... UUID is
				// really zero, which would be odd).
				if(!no.equals("")) {
					// it's valid; so let's change.

					Iterator iMap = set_map.iterator();

					while(iMap.hasNext()) {
						Sequence seq2 = (Sequence) iMap.next();
						
						// gi number? 
						String compareTo = "gi|" + no + "|";
						if(seq2.getFullName().indexOf(compareTo) != -1) {
//							System.err.println("Match!: " + seq + ", " + seq2);
							seq.changeName(seq2.getFullName());
							iMap.remove();	// get rid of this particular map
							break;
						}
						
						// gi number, now with '_'?
						compareTo = "gi|" + no + ":";
						if(seq2.getFullName().indexOf(compareTo) != -1) {
//							System.err.println("Match!: " + seq + ", " + seq2);
							seq.changeName(seq2.getFullName());
							iMap.remove();	// get rid of this particular map
							break;
						}

						
						// uniqueid
						if(no.length() > 0 && no.charAt(0) == 'U') {
//							System.err.println("Match!: " + seq + ", " + seq2);
							compareTo = "[uniqueid:" + no.substring(1) + "]";
							if(seq2.getFullName().indexOf(compareTo) != -1) {
								seq.changeName(seq2.getFullName());
								iMap.remove();	// get rid of this particular map
								break;
							}
						}
					}
				}
			}
		}

		// chuck in set_map
		// wait, no.
		// i'm assuming it's stupid and kinda foolhardy to quash these two together like this:
		// 1.	we used the map to fix sequences
		// 2.	now, the map should have the "fixed" sequences
		// 3.	let's just chuck the "mapped" sequences elsewhere?
		if(set_map.count() > 0) {
			String path = "";
			File f = getExportedFile();
			if(f != null)
				path = f.getParent() + File.separator;
			
			set_map.setFile(new File(path + "missing_sequences.txt"));
			list_missing = set_map;	// save a copy in the 'outside world'
		} else
			list_missing = null;	// nuffink missing

		list_final = set_final;
	}

	/**
	 * This method checks to see if every sequence in the specified 
	 * SequenceList has a (hopefully unique, and we DO test this)
	 * identifier. We assume that GIs are completely unique. We also
	 * create an arbitrary value called the '[uniqueid:([\d\-]+)]', which
	 * uniquely identifies the sequence. Since we're the only one
	 * who uses the 'uniqueid', we'll work it entirely out of here.
	 * Nobody else needs unique id's at this point, but in case
	 * they do ... err ... code's going to move.
	 *
	 * @return true, iff unique IDs were generated. If we return false, we do NOT guarantee every Sequence will return a valid GI or uniqueid. We have already informed the user of this.
	 */
	public boolean createUniqueIds (SequenceList list)  {
		boolean warned = false;
		MessageBox mb = null;
		Hashtable unique = new Hashtable();
		Iterator i = list.iterator();

		while(i.hasNext()) {
			Sequence seq = (Sequence) i.next();

			String id = seq.getGI();
			if(id == null || id.equals("")) {
				// no id? no problem! we 'get' the id from the seq
				// assuming we've given it a uniqueId before ...
				Pattern p = Pattern.compile("\\[uniqueid:(.*)\\]");
				Matcher m = p.matcher(seq.getFullName());
				if(m.find())
					id = m.group(1);
			}

			// now, let's see what this here id is *really* made of
			if(id == null || id.equals("")) {
				// there is no GI
//				if(!warned) {
//					mb = new MessageBox(getFrame(), "Sequences without GI numbers detected!", "Some of the sequences in this dataset do not have GI numbers. To create Clustal input files, I'm going to have to allocate unique identifiers to these sequences. Doing so will not cause problems with this dataset, but it WILL rewrite the original file. THE CURRENT FILE WILL BE OVERWRITTEN, AND ANY CHANGES YOU'VE MADE WILL BE SAVED PERMANENTLY TO DISK. Are you sure you want to do this?", MessageBox.MB_YESNO);
//					if(mb.showMessageBox() == MessageBox.MB_YES) {
//						// yes! go ahead
//						warned = true;
//					} else {
//						// no ... so, don't process at all.
//						mb = new MessageBox(getFrame(), "Clustal export failed.", "I can't export files for Clustal without assigning unique identifiers to each sequence. Please re-run if you would like to export files for Clustal.");
//						mb.go();
//
//						return false;
//					}
//				}
				// if we're here, we have warned the user, and he's okay with rewriting files. So ...
				seq.changeName(seq.getFullName() + " [uniqueid:" + seq.getId().toString() + "]");

				unique.put(seq.getId(), new Boolean(true));
			} else if(unique.get(id) != null) {
				// there is a GI, but it's non identical
				// barf biggly and noisily
				mb = new MessageBox(getFrame(), "You have duplicate sequences!", "Two sequences in this dataset have identical GI numbers. I can't use this for a mapfile. Please delete one of the duplicate pair of sequences. The duplicate pair which caused this error was:\n\t" + seq.getFullName() +"\nand\nGI:\t" + id);
				mb.go();

				return false;
			} else {
				// we have a GI, and it looks okay ...
				// so ... do nothing!
				unique.put(seq.getId(), new Boolean(true)); 
			}
//			System.err.println("Sequence: " + seq);
		}

		return true;
	}

	/**
	 * Exports all the data in 'set' into an output file. This means we check to
	 * see if the present dataset is "mappable", and change it if it isn't. The 
	 * output file will contain a list of all the sequences, in appropriate FASTA 
	 * format (good for passing into AlignmentHelper). See docs for this class on 
	 * how this works.
	 */
	public void exportSequenceSet(SequenceList set, File outputFile) {
		// our output writers
		PrintWriter	output = null;
		Hashtable 	uniques = new Hashtable();

		if(set == null)		// nothing to do!
			return;

		try {
			createUniqueIds(set);
		} catch(RuntimeException e) {
			// something went wrong with createUniqueIds() 
			// but it's already been reported to the reader
			if(e.getMessage() != null && e.getMessage().equals("Duplicate:notified"))
				return;
			// err ... no? A *real* RuntimeException?
			//
			// *gulp*
			throw e;
		}

		try {
			output = 		new PrintWriter(new FileWriter(outputFile));
			
			Iterator i = set.iterator();
			int no = 0;			
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();
				String id = seq.getGI();

				if(id == null || id.equals("")) {
					// since we've run it thru createUniqueIds(), this is guaranteed to work ...
					Pattern p = Pattern.compile("\\[uniqueid:(.*)\\]");
					Matcher m = p.matcher(seq.getFullName());
					if(m.find())
						id = "U" + m.group(1);
					else {
//						System.err.println("Sequence: " + seq.getFullName());
						throw new RuntimeException("Something wrong with this program in Clustal:exportSequenceSet()");
					}
				}

				// by this point, 'id' should be pointing at a valid id
				// note that id might be GI (number) or non-GI (preceded by 'U')
				if(uniques.get(id) != null) {
					// our ID is non unique? Die with extreme prejudice!

					MessageBox mb = new MessageBox(getFrame(), "You have duplicate sequences!", "Two sequences in this dataset have identical GI numbers. I can't use this for a mapfile. Please delete one of the duplicate pair of sequences. The duplicate pair which caused this error was:\n\t" + seq.getFullName() +"\nand\nGI:\t" + id);
					mb.go();
				
					return;	
				}
				uniques.put(id, new Boolean(true));
				
				// write this one into the output file ...
				output.println(">seq" + id);
				output.println(seq.getSequenceWrapped(80));
				
				// next!
				no++;
			}
		
			MessageBox mb = new MessageBox(getFrame(), "Success!", no + " sequences were exported successfully. You may now run Clustal on the sequences you specified. Once that is done, please follow step 2 to retrieve the original sequences.");
			mb.go();

		} catch(IOException e) {
			MessageBox mb = new MessageBox(getFrame(), "Error while writing to file", "There was an error writing to '" + outputFile + "'. Are you sure you have write permissions to both the Clustal input and the map file? The technical description of this error is: " + e);
			mb.go();
			return;
		} finally {
			if(output != null)
				output.close();
		}

	}
}
