/**
 * A Closeable Dialog wraps a normal dialog, adding code to close it
 * when a windowClosing() event is fired.
 */
/*
    TaxonDNA
    Copyright (C) 2006 Gaurav Vaidya
    
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

import java.awt.*;
import java.awt.event.*;

public class CloseableWindow implements WindowListener, KeyListener {
	private static CloseableWindow 	singleton = null;

	private CloseableWindow() {
	}

	private static CloseableWindow getInstance() {
		if(singleton == null)
			singleton = new CloseableWindow();
		return singleton;
	}

	/** Creates a default button named "OK". */
	public static Window wrap(Window wrap) {
		wrap.addWindowListener(CloseableWindow.getInstance());
		//wrap.addKeyListener(CloseableWindow.getInstance());	-- DOESN'T WORK!
		return wrap;
	}

	public void windowActivated(WindowEvent e) {}
	public void windowClosed(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {
		Window w = (Window) e.getSource();
		w.setVisible(false);
	}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}

	public void keyPressed(KeyEvent e) {
		if(Window.class.isAssignableFrom(e.getSource().getClass())) {
			if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {
				((Window) e.getSource()).setVisible(false);
			}
		}

	}
	public void keyReleased(KeyEvent e) {}
	public void keyTyped(KeyEvent e) {}

}
