/**
 * Creates a default button: a button which will kill the dialog box
 * it's in, and will try to override the DialogBox's keyboard handling
 * to pick up any 'Enter's. Note that having two DefaultButtons in 
 * a Dialog or Frame is undefined, but I'm guessing the first one -
 * chronologically - will fire.
 */

package com.ggvaidya.TaxonDNA.UI;

import java.awt.*;
import java.awt.event.*;

public class DefaultButton extends Button implements ActionListener, KeyListener {
	private Window	parentWindow = null;
	private boolean	wasClicked = false;

	/**
	 * Makes the specified button 'default' - clicking on it
	 * will close the window it's in, and pressing 'Enter'
	 * in that window will cause us to fire, closing it.
	 */
	public void makeButtonDefault(Window parent) {
		if(parent == null)		// can't do this unless we've got a parent!
			throw new IllegalArgumentException("Cannot make default button: parent window specified as 'null'!");

		// now, we need to store the Window we found ON the Button
		parentWindow = (Window) parent;

		// and register this class with all the handlers we need
		addActionListener(this);
		parentWindow.addKeyListener(this);
	}

	/** Creates a default button named "OK". */
	public DefaultButton(Window parent) {
		super("OK");
		makeButtonDefault(parent);
	}

	/** Creates a default button with the specified label */
	public DefaultButton(Window parent, String label) {
		super(label);
		makeButtonDefault(parent);
	}

	/**
	 * Sometimes, you need to know whether the window closed because of the dialog window closing,
	 * or whether this button was clicked to close. Returns true if it was this button.
	 */
	public boolean wasClicked() {
		return wasClicked;
	}

	/** 
	 * This action listener listens for clicks, and if recieved,
	 * calls setVisible(false) on the parent window.
	 */
	public void actionPerformed(ActionEvent e) {
		if(e.getSource().equals(this)) {
			// that's us!
			if(parentWindow != null) {
				wasClicked = true;
				parentWindow.setVisible(false);
				parentWindow.dispose();
			}
		}
	}

	/**
	 * This KeyListener listens for 'Enter', and generates 
	 * a click on this button if it is detected.
	 */
	public void keyTyped(KeyEvent e) {
		if(e.getKeyCode() == KeyEvent.VK_ENTER) {
			processActionEvent(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand()));
		}	
	}
	
	/** KeyListener detritus. */
	public void keyReleased(KeyEvent e) {}
	
	/** KeyListener detritus. */
	public void keyPressed(KeyEvent e) {}

}
