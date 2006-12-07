/**
 * A frame which allows the user to select options for taxonsets.
 * We also actually *determine* those taxonsets. There are also
 * other evil hacky things we will do with all of this. Cheers.
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2006 Gaurav Vaidya
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *  
 */

package com.ggvaidya.TaxonDNA.SequenceMatrix;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Taxonsets implements WindowListener, ItemListener, ActionListener {
	private SequenceMatrix 		matrix 	= null;			// the SequenceMatrix object
	private Dialog 			dialog 	= null;			// the Dialog which we need to display

	// Constants for the export
	//
	public static final String	prefix_Length		=	"LENGTH_ATLEAST_";	// I hope the BP is understood =/
	public static final String	prefix_CharSets		=	"CHARSETS_ATLEAST_";	// I hope this is understandable =/
	public static final String 	prefix_TaxonsHaving		=	"TAXONS_HAVING_";	// For gene-based taxonsets

	// Our variables
	// keeping track of values, etc.
	private Vector			vec_Length		=	new Vector();
	private Vector			vec_CharSets		=	new Vector();

	// 
	// Our User Interface
	//
	private TextField 		tf_Length		=	new TextField("1000");
	private Button			btn_Length		=	new Button("Add");
	private java.awt.List		list_totalLength	=	new java.awt.List(5);
	private Button			btn_totalLength_Delete	=	new Button("Delete this taxonset");

	private TextField		tf_CharSets		=	new TextField("5");
	private Button			btn_CharSets		=	new Button("Add");
	private java.awt.List		list_totalCharSets	=	new java.awt.List(5);
	private Button			btn_totalCharSets_Delete=	new Button("Delete this taxonset");

	private Checkbox		check_generateGeneSets 	=	new Checkbox("Generate taxonsets containing particular genes");

	private Button 			btn_Ok 			=	new Button("OK");

	/**
	 * Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet)
	 * and *TBD*
	 */
	public Taxonsets(SequenceMatrix matrix) {
		// set up the SequenceMatrix
		this.matrix = matrix;

		// set up 'dialog'
		dialog = new Dialog(matrix.getFrame(), "Taxonsets", true);

		Panel options = new Panel();
		RightLayout rl = new RightLayout(options);
		options.setLayout(rl);

		// set up the 'buttons' bar ... which is really just the 'OK' button
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		// set up the totalLength part
		rl.add(new Label("Create a taxonset consisting of only those taxons which have atleast "), RightLayout.FILL_3);
		rl.add(tf_Length, RightLayout.NEXTLINE | RightLayout.STRETCH_X);
		rl.add(new Label(" bp of combined length"), RightLayout.BESIDE);

		btn_Length.addActionListener(this);
		rl.add(btn_Length, RightLayout.BESIDE);

		list_totalLength.addItemListener(this);
		rl.add(list_totalLength, RightLayout.NEXTLINE | RightLayout.STRETCH_X | RightLayout.STRETCH_Y | RightLayout.FILL_3);

		btn_totalLength_Delete.addActionListener(this);
		btn_totalLength_Delete.setEnabled(false);
		rl.add(btn_totalLength_Delete, RightLayout.NEXTLINE | RightLayout.FLUSHRIGHT);

		// set up the totalCharSets part
		rl.add(new Label("Create a taxonset consisting of only those taxons which have atleast "), RightLayout.NEXTLINE | RightLayout.FILL_3);
		rl.add(tf_CharSets, RightLayout.NEXTLINE | RightLayout.STRETCH_X);
		rl.add(new Label(" character sets"), RightLayout.BESIDE);

		btn_CharSets.addActionListener(this);
		rl.add(btn_CharSets, RightLayout.BESIDE);

		list_totalCharSets.addItemListener(this);
		rl.add(list_totalCharSets, RightLayout.NEXTLINE | RightLayout.STRETCH_X | RightLayout.STRETCH_Y | RightLayout.FILL_3);

		btn_totalCharSets_Delete.addActionListener(this);
		btn_totalCharSets_Delete.setEnabled(false);
		rl.add(btn_totalCharSets_Delete, RightLayout.NEXTLINE | RightLayout.FLUSHRIGHT);

		rl.add(check_generateGeneSets, RightLayout.NEXTLINE | RightLayout.FILL_3);
		
		dialog.add(options);

		// set up the okay button
		btn_Ok.addActionListener(this);
		buttons.add(btn_Ok);

		dialog.add(buttons, BorderLayout.SOUTH);

		// register us as a 'listener'
		dialog.addWindowListener(this);

		// Set up the defaults
		setupDefaults();
	}

	public void setupDefaults() {
		// set up us the length
		list_totalLength.removeAll();
		vec_Length.clear();
		Vector v = new Vector();
		v.add(new Integer(400));
		v.add(new Integer(600));
		v.add(new Integer(800));
		v.add(new Integer(1000));		
		v.add(new Integer(1200));		
		v.add(new Integer(1400));		
		v.add(new Integer(1600));
		v.add(new Integer(2000));		
		v.add(new Integer(3000));
		v.add(new Integer(4000));

		Iterator i = v.iterator();
		while(i.hasNext()) {
			int x = ((Integer)i.next()).intValue();

			list_totalLength.add(prefix_Length + x + ": Containing atleast " + x + " bp combined length");
			vec_Length.add(new Integer(x));
		}

		// set up us the charsets 
		list_totalCharSets.removeAll();
		vec_CharSets.clear();
		v = new Vector();
		int c = 8;
		while(c > 1) {
			v.add(new Integer(c));
			c--;
		}

		i = v.iterator();
		while(i.hasNext()) {
			int x = ((Integer)i.next()).intValue();

			list_totalCharSets.add(prefix_CharSets + x + ": Containing atleast " + x + " character sets");
			vec_CharSets.add(new Integer(x));
		}

		// turn genesets on
		check_generateGeneSets.setState(true);
	}

	/**
	 * Our proxy for dialog's setVisible(). Please use this to
	 * 'activate' the dialog.
	 */
	public void setVisible(boolean state) {
		if(state) {
			dialog.pack();
			dialog.setVisible(true);
		} else {
			if(verify()) {
				dialog.setVisible(false);
				dialog.dispose();
			}
		}
	}
	
	//
	// Listeners
	// 

	/**
	 * Handles Action events (such as the 'OK' button).
	 */
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();

		if(src.equals(btn_Ok))
			setVisible(false);

		if(src.equals(btn_Length) || src.equals(btn_CharSets)) {
			String newValue = null;
			String prefix = null;
			String atleast = null;
			Vector addTo = null;
			java.awt.List list = null;
			String name = null;
			
			if(src.equals(btn_Length)) {
				atleast = "atleast";
				name = "bp combined length";
				prefix = prefix_Length;
				newValue = tf_Length.getText();
				list = list_totalLength;
				addTo = vec_Length;
			} else {
				atleast = "atleast";
				name = "character sets";
				prefix = prefix_CharSets;
				newValue = tf_CharSets.getText();
				list = list_totalCharSets;
				addTo = vec_CharSets;
			}

			// get an 'int' from newValue
			int x = 0;
			try {
				x = Integer.parseInt(newValue);
			} catch(NumberFormatException ex) {
				MessageBox mb = new MessageBox(
						matrix.getFrame(),
						"I can't understand!",
						"You specify that the new taxonset should have '" + newValue + "' " + name + ", but '" + newValue + "' isn't a valid number! Please lets use a number I can understand, thanks.",
						MessageBox.MB_OK);

				mb.go();
				return;
			}

			// if we already have an entry, silently ignore
			if(!addTo.contains(new Integer(x))) {
				list.add(prefix + x + ": Containing " + atleast + " " + x + " " + name);
				addTo.add(new Integer(x));
			}
		}

		if(src.equals(btn_totalLength_Delete) || src.equals(btn_totalCharSets_Delete)) {
			java.awt.List list = null;
			Vector vec = null;
			
			if(src.equals(btn_totalLength_Delete)) {
				list = list_totalLength;
				vec = vec_Length;
			} else {
				list = list_totalCharSets;
				vec = vec_CharSets;
			}

			int selected = list.getSelectedIndex();

			vec.remove(selected);
			list.remove(selected);

			if(list.getItemCount() == 0)
				((Button)src).setEnabled(false);
		}
	}

	/**
	 * Handles Item events (such as whether the *_Delete buttons should be enabled or not
	 * should be enabled or not).
	 */
	public void itemStateChanged(ItemEvent e) {
		Object src = e.getSource();

		if(e.getStateChange() == ItemEvent.DESELECTED) {
			if(src.equals(list_totalLength))
				btn_totalLength_Delete.setEnabled(false);

			if(src.equals(list_totalCharSets))
				btn_totalCharSets_Delete.setEnabled(false);
		} else if(e.getStateChange() == ItemEvent.SELECTED) {
			if(src.equals(list_totalLength))
				btn_totalLength_Delete.setEnabled(true);

			if(src.equals(list_totalCharSets))
				btn_totalCharSets_Delete.setEnabled(true);
		}
	}

	/**
	 * Check to make sure that all input 'makes sense'.
	 * @return true, if it's okay to exit.
	 */
	private boolean verify() {
		/*
		if(false) {
			MessageBox mb = new MessageBox(
					matrix.getFrame(),
					"Error in Nexus 'Interleave At' value",
					"You specified an invalid or un-understandable value for the Nexus 'Interleave at' argument.\n\nI am going to set it to interleave at 1000bp instead. Is this okay?",
					MessageBox.MB_YESNO);
			if(mb.showMessageBox() == MessageBox.MB_YES)
				//tf_nexusOutputInterleaved.setText("1000");
				;
			else
				// MB_NO
				return false;
		}
*/
		return true;
	}

	/**
	 * Returns a Vector of all the taxonSets we have (as String).
	 * You can then query this list against Exporter.getTaxonset(String) to
	 * get the Taxonset in question (as a single line, etc.)
	 */
	public Vector getTaxonsetList() {
		Vector result = new Vector();

		// give him the length taxonsets
		Iterator i = vec_Length.iterator();
		while(i.hasNext()) {
			Integer it = (Integer) i.next();

			result.add(Taxonsets.prefix_Length + it);
		}

		// give him Charset taxonsets
		i = vec_CharSets.iterator();
		while(i.hasNext()) {
			Integer it = (Integer) i.next();
			
			result.add(Taxonsets.prefix_CharSets + it);
		}

		// give him Geneset taxonsets
		if(check_generateGeneSets.getState()) {
			i = matrix.getTableManager().getDataStore().getColumns().iterator();
			while(i.hasNext()) {
				String colName = (String)i.next();
				result.add(Taxonsets.prefix_TaxonsHaving + colName);
			}
		}
			

		return result;
	}	

	// 
	// WindowListener methods
	//
	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {
		setVisible(false);
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}

	public void go() {
		setVisible(true);
	}
}
