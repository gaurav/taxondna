/**
 * Displays all pairwise distances, for your own use/etc. 
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

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class AllPairwiseDistances extends Panel implements UIExtension, Runnable, ActionListener {
	private TaxonDNA	taxonDNA = null;
	private SequenceList	set = null;

	private FileInputPanel	finp_allDistances 	= null;
	private FileInputPanel	finp_intraDistances 	= null;
	private FileInputPanel	finp_interDistances 	= null;

	private Button		btn_Calculate		= new Button("Calculate");

	public AllPairwiseDistances(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
	
		// layouting
		RightLayout rl = new RightLayout(this);
		setLayout(rl);

		rl.add(new Label("You can leave any of the following fields blank if you do not need that output"),
				RightLayout.FILL_2 | RightLayout.STRETCH_X);
		finp_allDistances = new FileInputPanel("Print all pairwise distances: ", FileInputPanel.MODE_FILE_WRITE, taxonDNA.getFrame());
		rl.add(finp_allDistances, RightLayout.NEXTLINE | RightLayout.FILL_2 | RightLayout.STRETCH_X);
		finp_intraDistances = new FileInputPanel("Print all intraspecific pairwise distances: ", FileInputPanel.MODE_FILE_WRITE, taxonDNA.getFrame());
		rl.add(finp_intraDistances, RightLayout.NEXTLINE | RightLayout.FILL_2 | RightLayout.STRETCH_X);
		finp_interDistances = new FileInputPanel("Print all interspecific pairwise distances: ", FileInputPanel.MODE_FILE_WRITE, taxonDNA.getFrame());
		rl.add(finp_interDistances, RightLayout.NEXTLINE | RightLayout.FILL_2 | RightLayout.STRETCH_X);
		btn_Calculate.addActionListener(this);
		rl.add(btn_Calculate, RightLayout.NEXTLINE | RightLayout.FILL_2); 
		rl.add(new Label(""), RightLayout.NEXTLINE | RightLayout.STRETCH_Y);	// hack: it expands to push the above stuff to the top of the Panel
											// a bit like 'margin-bottom: auto' :p
	}

	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_Calculate)) {
			new Thread(this, "AllPairwiseDistances").start();
		}
	}
	
	public void dataChanged()	{
		set = taxonDNA.lockSequenceList();
		if(set == null) {
			finp_allDistances.setFile(null);
			finp_intraDistances.setFile(null);
			finp_interDistances.setFile(null);
		}
		taxonDNA.unlockSequenceList();
	}

	private void print(PrintWriter pw, String str) throws IOException {
		if(pw != null) {
			pw.print(str);

			if(pw.checkError())
				throw new IOException("An error occurred while printing '" + str + "' to print writer '" + pw + "'.");
		}
	}

	private void println(PrintWriter pw, String str) throws IOException {
		if(pw != null) {
			pw.println(str);

			if(pw.checkError())
				throw new IOException("An error occurred while printing '" + str + "' to print writer '" + pw + "'.");
		}
	}

	public void run() {
		int x, y;
		Sequence seq, seq2;
		ProgressDialog pd = 
			new 	ProgressDialog(
					taxonDNA.getFrame(), 
					"Writing all pairwise distances", 
					"We are calculating and preparing all pairwise distances, please wait.", 
				0);

		StringBuffer str = new StringBuffer();

		set = taxonDNA.lockSequenceList();

		if(set == null) {
			taxonDNA.unlockSequenceList();
			return;
		}

		// Make/open the files
		PrintWriter	pwAll	=	null;
		PrintWriter	pwIntra =	null;
		PrintWriter	pwInter =	null;	

		try {
			File f = null;

			if((f = finp_allDistances.getFile()) != null)
				pwAll =	new PrintWriter(new BufferedWriter(new FileWriter(f)));

			if((f = finp_intraDistances.getFile()) != null)
				pwIntra = new PrintWriter(new BufferedWriter(new FileWriter(f)));

			if((f = finp_interDistances.getFile()) != null)
				pwInter = new PrintWriter(new BufferedWriter(new FileWriter(f)));

		} catch(IOException e) {
			MessageBox	mb	=	new MessageBox(
					taxonDNA.getFrame(),
					"Couldn't open file!",
					"I could open a file for writing. Are you sure you have adequate permissions?");
			mb.go();
			taxonDNA.unlockSequenceList();
			return;
		}

		if(pwAll == null && pwIntra == null && pwInter == null) {
			// err ... nothing to do!
			taxonDNA.unlockSequenceList();
			return;
		}

		try {
			pd.begin();

			print(pwAll, "All pairwise distances:\t");

			Iterator i = set.iterator();
			while(i.hasNext()) {
				seq = (Sequence) i.next();
				print(pwAll, seq.getFullName() + "\t");
			}
			println(pwAll, "");
			
			x = 0;
			i = set.iterator();
			int interval = set.count() / 100;
			if(interval == 0)
				interval = 1;
			while(i.hasNext()) {
				if(x % interval == 0)
					pd.delay(x, set.count());

				seq = (Sequence) i.next(); 
				print(pwAll, seq.getFullName() + "\t");

				Iterator i2 = set.iterator();
				y = 0;
				while(i2.hasNext()) {
					seq2 = (Sequence) i2.next();

					if(y >= x)
						break;

					if(!seq.equals("") && !seq2.equals("") && seq2.getSpeciesName().equals(seq.getSpeciesName())) {
						println(pwIntra, seq.getFullName() + "\t" + seq2.getFullName() + "\t" + seq.getPairwise(seq2)); 
					} else if(seq2.getGenusName().equals(seq.getGenusName())) {
						println(pwInter, seq.getFullName() + "\t" + seq2.getFullName() + "\t" + seq.getPairwise(seq2)); 
					}

					print(pwAll, seq.getPairwise(seq2) + "\t");

					y++;
				}
				println(pwAll, "");

				x++;
			}
		
			if(pwAll != null)
				pwAll.close();

			if(pwIntra != null)
				pwIntra.close();

			if(pwInter != null)
				pwInter.close();

		} catch(DelayAbortedException e) {
			set = null;
			taxonDNA.unlockSequenceList();
			return;
		} catch(IOException e) {
			MessageBox	mb	=	new MessageBox(
					taxonDNA.getFrame(),
					"Couldn't open file!",
					"I could open a file for writing. Are you sure you have adequate permissions?");
			mb.go();
			pd.end();
			taxonDNA.unlockSequenceList();
			return;
		}

		MessageBox	mb	= 	new MessageBox(
							taxonDNA.getFrame(),
							"All done!",
							"All pairwise distances have been written out as you requested."
						);
			mb.go();

		pd.end();
		taxonDNA.unlockSequenceList();
		return;
	}

	public String getShortName() {		return "All Pairwise Distances"; 	}
	public String getDescription() {	return "Displays all pairwise distances"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
