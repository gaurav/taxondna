/**
 * DirectoryInputPanel
 *
 * As the name suggests, this is a Panel containing a TextField
 * for you to pick a directory with. Eventually, we might be
 * crazy enough to implement a whole in-AWT copy of a normal
 * DirectoryDialog - yeah, right - but until then, it'll be
 * guaranteed to return a *directory* - or null - when you
 * call getDirectory()
 *
 */

/*
    TaxonDNA
    Copyright (C) 2006	Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.UI;

import java.io.*;
import java.awt.*;
import java.awt.event.*;

public class DirectoryInputPanel extends Panel implements TextListener {
	Frame		frame 	= null;
	String		label 	= null;
	int		mode 	= 0;
	TextField 	tf 	= new TextField();

	public static final int MODE_DIR_SELECT		=	0x01;	//	select a directory itself (to delete, rename, etc.)
	public static final int MODE_DIR_MODIFY_FILES	=	0x02;	//	select a directory to do stuff inside the dir
									//	e.g. write files into, move files out of, etc.
	public static final int MODE_DIR_CREATE		=	0x03;	//	create a NEW directory	

	/**
	 * Set us up a very simple file input panel. This is just a big 
	 * TextField, with a "Browse ..." button on one side.
	 */
	public DirectoryInputPanel(String label, int mode, Frame frame) {
		this.frame = frame;
		this.mode = mode;
		this.label = label;

		if(label == null) {
			setLayout(new BorderLayout());
		} else {
			setLayout(new BorderLayout(5,0));
			add(new Label(label), BorderLayout.WEST); 
		}

		tf.addTextListener(this);
		add(tf);
	}

	/**
	 * We should atleast _try_ and look presentable
	 */
	public Insets getInsets() {
		if(label == null)
			return new Insets(0, 0, 0, 0);
		return new Insets(3, 3, 3, 3);
	}

	/**
	 * Returns the current text content as a File (which is really a Directory, etc.) 
	 */
	public File getDirectory() {

		// if the file is empty, you get NOTHING!
		if(tf.getText().trim().equals(""))
			return null;

		// if the file is NOT empty, things get very tricky very quickly:
		File f = new File(tf.getText());
		tf.setText(f.getAbsolutePath());
		f = new File(f.getAbsolutePath());	// ha-ha!

		// 0.	First, we praise the Greater Powers (if any) that this is not half as
		// 	complicated as FileInputPanel. At the user's expense, unfortunately,
		//	but this is the best we can do at short notice without including in
		//	Swing or SWT.
		//
		// 1.	If we are a SELECT_DIR:
		// 	(a)	The directory must exist. MUST. No two ways about it. We'll
		// 		return a 'null', but not a non-existant directory.		
		//
		if(mode == MODE_DIR_SELECT || mode == MODE_DIR_MODIFY_FILES) {
			if(f.exists() && f.isDirectory()) {
				// all good!
				return f;
			} else {
				MessageBox mb = new MessageBox(
							frame,
							"Warning: the specified directory does NOT exist!",
							"You need to provide a valid directory name in the '" + label + "' field!\n\nPlease enter one in now.",
							0);
				mb.go();
				return null;
			}

		// 2.	If we are a CREATE:
		// 	(a)	The directory must NOT exist. Here, we'll allow them to
		// 		override our dialog, saying to use that directory ANYWAY.
		//
		} else if(mode == MODE_DIR_CREATE) {
			if(!f.exists()) {
				// yay!
				return f;
			} else {
				// err .. wops!
				MessageBox mb = new MessageBox(
						frame,
						"Warning: the specified directory exists!",
						"There is already a directory named '" + f + "'! Would you like to use it anyway?",
						MessageBox.MB_YESNO);
				if(mb.showMessageBox() == MessageBox.MB_YES) {
					return f;
				} else {
					return null;
				}
			}
		} else {
			// unknown mode!
			// just let the user have 'f'?
			return f;
		}
	}

	/**
	 *	Listens for changes to the textfield. This way, we KNOW if somebody
	 *	modified the field outside of the browse dialog.
	 */
	public void	textValueChanged(TextEvent e) {
	}
}
