/**
 * A simple modal dialog for communicating a single message (or requesting an option) from the user.
 * Create an object of this class, then call the showMessageBox() function to display it. You can also
 * call go() if you are not interested in the response. 
 * 
 * The static function messageBox() can also be used to do it all in one step. 
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
 */

/*
    TaxonDNA
    Copyright (C) 2005	Gaurav Vaidya

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

import java.util.*;	// for Hashtable

public class MessageBox extends Dialog implements ActionListener {
	private String		title = "";
	private String		message = "";
	private int		flags = 0;
	private int		clicked = 0;

	private static Hashtable hash_setForAll = new Hashtable();
	
	// constants
	// both for type and for return by show()
	/** The "OK" button was pressed */
	public static final int	MB_OK = 	0x0001;
	/** The "YES" button was pressed */		
	public static final int	MB_YES =	0x0002;
	/** The "NO" button was pressed */	
	public static final int	MB_NO =		0x0004;
	/** The "CANCEL" button was pressed */	
	public static final int	MB_CANCEL =	0x0008;
	/** The "Yes to all" button was pressed */
	public static final int MB_YESTOALL =	0x000F;
	/** The "No to all" button was pressed */
	public static final int MB_NOTOALL =	0x0010;

	// flags
	/** 
	 * Specify that the title is unique to the entire program,
	 * and can be used to uniquely identify this particular message. 
	 */
	public static final int MB_TITLE_IS_UNIQUE = 0x0100;
	/** 
	 * Specify that the message is unique to the entire program,
	 * and can be used to uniquely identify this particular message. 
	 */
	public static final int MB_MESSAGE_IS_UNIQUE = 0x0200;

	/** Create an "simple" messageBox, with a single "OK" button. */
	public static final int	MB_SIMPLE = 	0x1000;
	/** Create an "error" messageBox, with a single "OK" button. */
	public static final int	MB_ERROR = 	0x1000;
	/** Create a yes/no messageBox */
	public static final int	MB_YESNO =	0x2000;
	/** Create a yes/no messageBox, with a single "CANCEL" button. */
	public static final int	MB_YESNOCANCEL= 0x4000;
	/** Create a yes/no/yes to all/no to all messageBox. You must set
	 * MB_TITLE_IS_UNIQUE or MB_MESSAGE_IS_UNIQUE if you use this
	 * option */
	public static final int	MB_YESNOTOALL = 0x8000;


	/** The default flags (if you use the flagless constructors) */
	private static final int DEFAULT_FLAGS = MB_OK | MB_ERROR;

	/*
	 * <rant>
	 * 	The following code is kinda screwed up, thanks to the fact
	 * 	that in the AWT, a Dialog is not a Frame, and a Frame is
	 * 	not a Dialog. However, EITHER can be used to create a
	 * 	Dialog, through duplicated constructors.
	 *	
	 *	So, we duplicate our constructor. One set will use Frame,
	 *	the other will use Dialog. A static function called
	 *	'createFromWindow(...)' will accept Window, cast it 
	 *	appropriately, then create the MessageBox and return
	 *	*that*.
	 *
	 *	Since most of the old code uses Frame, frames will continue
	 *	working fine. If any of the newer code needs to use Window,
	 *	they'll have to use the static and go from there.
	 * </rant>
	 *
	 */

	// Frame constructors
	/**
	 * Creates a messagebox, with the given parent frame, title and message.
	 * The messagebox is an "Error" box, by default. 
	 */
	public MessageBox(Frame parent, String title, String message) {
		this(parent, title, message, DEFAULT_FLAGS);	// call the One True Constructor
	}

	/**
	 * Creates a messagebox, with the given parent frame, title, message
	 * and flags. The flags can be used to change the type of the message
	 * box, and/or the buttons displayed.
	 */
	public MessageBox(Frame parent, String title, String message, int flags) {
		super(parent, title, true);	// true -> we need to be modal
		this.title = title;
		this.message = message;
		this.flags = flags;
	}

	// Dialog constructors
	/**
	 * Creates a messagebox, with the given parent frame, title and message.
	 * The messagebox is an "Error" box, by default. 
	 */
	public MessageBox(Dialog parent, String title, String message) {
		this(parent, title, message, DEFAULT_FLAGS);	// call the One True Constructor
	}

	/**
	 * Creates a messagebox, with the given parent frame, title, message
	 * and flags. The flags can be used to change the type of the message
	 * box, and/or the buttons displayed.
	 */
	public MessageBox(Dialog parent, String title, String message, int flags) {
		super(parent, title, true);	// true -> we need to be modal
		this.title = title;
		this.message = message;
		this.flags = flags;

		if((flags & MB_YESNOTOALL) != 0) {
			if(
				((flags & MB_TITLE_IS_UNIQUE) == 0) && 
			       	((flags & MB_MESSAGE_IS_UNIQUE) == 0)
			) {
				throw new IllegalArgumentException("MessageBox creation attempted with MB_YESNOTOALL set, but without either MB_TITLE_IS_UNIQUE or MB_MESSAGE_IS_UNIQUE set!");
			}
		}
	}

	/**
	 * A static 'constructor', if all you've got is a Window. We'll figure out
	 * whether it's a Dialog or a Frame, and fire the appropriate constructor.
	 */
	public static MessageBox createFromWindow(Window parent, String title, String message, int flags) {
		MessageBox mb = null;

		if(parent.getClass().equals(Frame.class))
			mb = new MessageBox((Frame)parent, title, message, flags);
		else if(parent.getClass().equals(Dialog.class))
			mb = new MessageBox((Dialog)parent, title, message, flags);
		else
			throw new IllegalArgumentException("The parent window (" + parent + ") is neither a Frame nor a Dialog!");

		return mb;
	}

	/**
	 * A static 'constructor', if all you've got is a Window, without
	 * a flags field. We'll figure out
	 * whether it's a Dialog or a Frame, and fire the appropriate constructor.
	 */
	public static MessageBox createFromWindow(Window parent, String title, String message) {
		return createFromWindow(parent, title, message, DEFAULT_FLAGS);
	}
	
	
	/**
	 * I don't see why anybody would be interested, but okay ...
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Our internal draw function, which actually creates the messagebox when it
	 * needs to be.
	 */
	private void draw() {
		removeAll();

		setLayout(new BorderLayout());	

		TextArea ta = 	new TextArea(message, 5, 50, TextArea.SCROLLBARS_VERTICAL_ONLY);
		ta.setEditable(false);
		add(ta);

		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.LEFT));

		Button btn;
		if((flags & MB_YESNO) != 0) {
			btn = new Button("   Yes   ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button("   No    ");
			btn.addActionListener(this);
			buttons.add(btn);
		} else if(((flags & MB_OK) != 0) || ((flags & MB_SIMPLE) != 0)) {
			btn = new Button("   OK   ");
			btn.addActionListener(this);
			buttons.add(btn);
		} else if((flags & MB_YESNOCANCEL) != 0) {
			btn = new Button("   Yes   ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button("   No    ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button(" Cancel ");
			btn.addActionListener(this);
			buttons.add(btn);
		} else if((flags & MB_YESNOTOALL) != 0) {
			btn = new Button(" Yes to all ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button("   Yes   ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button("   No    ");
			btn.addActionListener(this);
			buttons.add(btn);

			btn = new Button(" No to all ");
			btn.addActionListener(this);
			buttons.add(btn);			
		}
		add(buttons, BorderLayout.SOUTH);

		pack();
	}

	/**
	 * ActionListener for the buttons.
	 */
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand().trim();
		
		if(cmd.equals("OK")) {
			clicked = MB_OK;
			dispose();
		} else if(cmd.equals("Yes")) {
			clicked = MB_YES;
			dispose();
		} else if(cmd.equals("No")) {
			clicked = MB_NO;
			dispose();
		} else if(cmd.equals("Cancel")) {
			clicked = MB_CANCEL;
			dispose();
		} else if(cmd.equals("Yes to all")) {
			clicked = MB_YESTOALL;
			dispose();
		} else if(cmd.equals("No to all")) {
			clicked = MB_NOTOALL;
			dispose();
		}
	}

	/**
	 * Shows the messageBox.
	 * will return one of the MB_OK, MB_CANCEL, etc. constants
	 */
	public int showMessageBox() {
		if((flags & MB_YESNOTOALL) != 0 && hash_setForAll.get(this.getUniqueIdentifier()) != null) {
			// we've already got a stored up option!
			Integer i = (Integer) hash_setForAll.get(this.getUniqueIdentifier());
			return i.intValue();
		}

		draw();

		setVisible(true); // this will block until everything's done

		if((flags & MB_YESNOTOALL) != 0) {
			if(clicked == MB_YESTOALL) {
				hash_setForAll.put(this.getUniqueIdentifier(), new Integer(MB_YES));
				return MB_YES;
			} else if(clicked == MB_NOTOALL) {
				hash_setForAll.put(this.getUniqueIdentifier(), new Integer(MB_NO));
				return MB_NO;
			} else	// either MB_YES or MB_NO
				return clicked;
		}

		return clicked;
	}

	/**
	 * Returns the UniqueIdentifier for this message box
	 */
	public String getUniqueIdentifier() {
		if((flags & MB_MESSAGE_IS_UNIQUE) != 0)
			return message;
		else
			return title;
	}

	/**
	 * If you don't need to retrieve the clicked button,
	 * you can use this instead.
	 */
	public void go() {
		showMessageBox();
	}

	/**
	 * Creates a messagebox, displays it, and returns the result.
	 */
	public static int messageBox(Frame parent, String title, String message, int flags) {
		MessageBox mb = new MessageBox(parent, title, message, flags);
		return mb.showMessageBox();
	}

	/**
	 * Creates an error messagebox and displays it.
	 */
	public static void messageBox(Frame parent, String title, String message) {
		MessageBox mb = new MessageBox(parent, title, message, MB_SIMPLE);
		mb.showMessageBox();
	}

	/**
	 * Resets any session-based information we carry (right now, that's only the hash_setForAll used
	 * by MB_YESTOALL)
	 */
	public static void resetSession() {
		hash_setForAll = new Hashtable();
	}
	
	/*
	 * Dummy test 
	public static void main(String args[]) {
		String message =
			"I've got a word or two,\nTo say about the things that you do\nYou telling all those lies\nAbout the good things that we can have if we close our eyes\nDo what you want to do, go where you're going to, think for yourself for I'm not going to be there with you";
		Window 	frame = new Window("Test");
		frame.setSize(200, 200);
		frame.show();
		MessageBox mb	=	new MessageBox(frame, "Test", message);
		System.out.println(mb.showMessageBox());
	}
	*/
}	
