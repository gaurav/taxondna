/**
 * Generate a subset of all the data we have at the present moment.
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
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;


public class GenerateSubSet extends Panel implements UIExtension, ActionListener {
	private static final long serialVersionUID = -6186706083485183839L;
	private TaxonDNA	taxonDNA = null;
	private SequenceList	set = null;
	private TextArea	text_main = new TextArea();
	private TextField	text_overlap = new TextField(5);
	private Button		btn_generate = new Button("Generate");

	public GenerateSubSet(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
	
		// layouting
		setLayout(new BorderLayout());	

		Panel input = new Panel();
		input.add(new Label("Please enter the minimum size required:"));
		input.add(text_overlap);
		add(input, BorderLayout.NORTH);
		
		btn_generate.addActionListener(this);
		input.add(btn_generate);
	       	
		text_main.setEditable(false);
		add(text_main);
	}

	public void itemStateChanged(ItemEvent e) {
	}

	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(btn_generate)) {
			StringBuffer str = new StringBuffer();
			int minSize = Integer.valueOf(text_overlap.getText()).intValue();
			
			if(minSize < 0)
				minSize = 0;

			set = taxonDNA.lockSequenceList();
			if(set == null) {
				taxonDNA.unlockSequenceList();
				return;
			}

			Iterator i = set.iterator();
			while(i.hasNext()) {
				Sequence seq = (Sequence) i.next();

				if(seq.getActualLength() >= minSize) {
					str.append("> " + seq.getFullName() + "\n" + seq.getSequenceWrapped(80) + "\n\n");
				}
			}

			text_main.setText(str.toString());
			taxonDNA.unlockSequenceList();
		}
	}
	
	public void dataChanged()	{

		set = taxonDNA.lockSequenceList();
		if(set == null) {
			text_main.setText("");
			text_overlap.setText("");
		}
		taxonDNA.unlockSequenceList();
	}

	public String getShortName() {		return "Generate subsets"; 	}
	public String getDescription() {	return "Generates a subset of the present dataset with some given criterion"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
