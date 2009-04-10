/**
 * A UIExtension which Tests SpeciesIdentifier functionality. A built-in
 * Test harness, in short. It acts at the TestController for
 * all the Testable objects, stores a list of testable objects,
 * and let's you test any particular or ALL objects.
 */
/*
    SpeciesIdentifier
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

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;	// for clipboard

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class Tester extends Panel implements UIExtension, ActionListener, Runnable, TestController {	
	private SpeciesIdentifier	taxonDNA;

	// Sequence View
	private Choice		choice_Test = new Choice(); 
	private Button		btn_Test = new Button("Start the tests!");
	
	private TextArea	text_main = new TextArea();

	private Button		btn_Copy = new Button("Copy to clipboard");

	// To allow tests to be carried out without actually re-instancing
	// the objects, as of right now 'testables' is a LinkedList
	// of CLASSES. Like, java.lang.Class.
	private LinkedList	testables = new LinkedList();

	/**
	 * Helper function to initialize the Vector of testable objects.
	 */
	private void initTestables() {
		testables.clear();
		testables.add(new com.ggvaidya.TaxonDNA.DNA.Sequence());
		testables.add(new com.ggvaidya.TaxonDNA.DNA.SequenceList());
		testables.add(new com.ggvaidya.TaxonDNA.DNA.formats.FastaFile());
		testables.add(new com.ggvaidya.TaxonDNA.DNA.formats.MegaFile());
	}
	
	/**
	 * Constructor.
	 * Get the UI ready and started, check off the list of testable objects,
	 * and go with the flow!
	 */
	public Tester(SpeciesIdentifier view) {
		super();

		taxonDNA = view;
		
		// init testables
		initTestables();

		choice_Test.add("All");
		Iterator i = testables.iterator();
		while(i.hasNext()) {
			Testable t = (Testable)i.next();
			choice_Test.add(t.getClass().getName());
		}
		
		// create the panel
		setLayout(new BorderLayout());
		
		// create the top panel
		Panel top = new Panel();
		top.setLayout(new BorderLayout());

		top.add(new Label("Please select the component you wish to test: "), BorderLayout.WEST);
		
		top.add(choice_Test);

		btn_Test.addActionListener(this);
		top.add(btn_Test, BorderLayout.EAST);

		add(top, BorderLayout.NORTH);

		text_main.setEditable(false);
		text_main.setFont(new Font("Monospaced", Font.PLAIN, 12));
		add(text_main);

		// buttons!
		Panel buttons = new Panel();
		buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

		btn_Copy.addActionListener(this);
		buttons.add(btn_Copy);

		add(buttons, BorderLayout.SOUTH);
	}

	/* Data changed: couldn't care less */
	public void dataChanged() {
	}

	public void run() {
		taxonDNA.lockSequenceList();
		ProgressDialog delay = new ProgressDialog(
				taxonDNA.getFrame(),
				"Please wait, running tests ...",
				"I am running all the tests now. The indicator will jump back to zero in between tests. This does not mean that there is an error in the tests.\n\nTake it easy. This might take a while.",
				0
				);
		
		text_main.setText("");
		
		int index = choice_Test.getSelectedIndex();

		if(index == 0) {
			println("Beginning testing of all testable components ...\n");
		
			total_successful	= 0;
			total_failed		= 0;
			
			Iterator i = testables.iterator();
			while(i.hasNext()) {
				Testable t = (Testable)i.next();
				try {
					t.test(this, delay);
				} catch(DelayAbortedException e) {
					// bail out completely
					break;
				}
				
			}

			println("Testing of all testable components completed: " + total_successful + " successful, " + total_failed + " failures.");
		} else {
			Testable t = (Testable)testables.get(index - 1);
			try {
				t.test(this, delay);
			} catch(DelayAbortedException e) {
				return;
			}
		}

		taxonDNA.unlockSequenceList();
	}

	/* Returns the percentage of a fraction upto 2 decimal digits */
	private double percentage(double x, double y) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(x, y);
	}

	/* Print it onto text_main */
	private void print(String x) {
		text_main.append(x);
	}
	
	/* Print it onto text_main */
	private void println(String x) {
		text_main.append(x + "\n");
	}
	
	/* Creates the actual Panel */
	public Panel getPanel() {
		return this;
	}

	// action listener
	public void actionPerformed(ActionEvent evt) {
		if(evt.getSource().equals(btn_Test) || evt.getActionCommand().equals("Run test suite")) {
			taxonDNA.goToExtension(this.getShortName());
			new Thread(this, "Tester").start();
			return;
		}
		
		if(evt.getSource().equals(btn_Copy)) {
			try {
				Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
				StringSelection selection = new StringSelection(text_main.getText());
				
				clip.setContents(selection, selection);
			} catch(IllegalStateException e) {
				btn_Copy.setLabel("Oops, try again?");
			}
			btn_Copy.setLabel("Copy to clipboard");
		}
	}

	/**
	 * Test controller
	 */
	private String lastTitle = "";
	private int total_successful = 0;
	private int total_failed = 0;	
	private int successful = 0;
	private int failed = 0;

	public void begin(String title) {
		println("Beginning tests on '" + title + "'.");
		lastTitle = title;
	}
	public void done() {
		println("Tests on '" + lastTitle + "' done: " + successful + " successful, " + failed + " failed.\n");
		lastTitle = "";
		total_successful+=successful;
		total_failed+=failed;
		failed = 0;
		successful = 0;
	}
	
	private String lastTestName = "";
	
	public void beginTest(String testName) {
		print("\t" + testName + ": ");
		lastTestName = testName;
	}

	public void failed(String description) {
		println("\n\t\tFAILED: " + description);	
		failed++;
	}
	public void succeeded() {
		println("successful.");	
		successful++;
	}

	public void information(String info) {
		println("[" + info + "]");
	}

	public File file(String name) {
		// I'm hard-coding this to a 'Test' directory, which will have to be included from SpeciesIdentifier 0.9.2
		return new File("../Tests/", name.toLowerCase());
	}
	public File tempfile() {
		File temp;
		try {
			temp = File.createTempFile("SpeciesIdentifier_test_", ".tmp");
		} catch(IOException e) {
			return null;	
		}
		temp.deleteOnExit();
		return temp;
	}

	public boolean isIdentical(File x, File y) {
		try {
			BufferedReader buff1 = new BufferedReader(new FileReader(x));
			BufferedReader buff2 = new BufferedReader(new FileReader(y));

			while(true) {
				String x1 = buff1.readLine();
				String x2 = buff2.readLine();
					
				if(x1 == null) { // end of stream for one!
					if(x2 == null) { // but is the end of the line for both?
						buff1.close();
						buff2.close();
						return true;
					} else {
						// no, only one
						break;
					}
				} else if(x2 == null) {
					break;
				}

				if(!x1.equals(x2))	// die-quick 
					break;
			}
			return false;
		}
		catch(IOException e) {
			failed("Error in SpeciesIdentifier.Tester: Tester failed (IOException: " + e + " while comparing files " + x + " and " + y + ".");
			return false;
		}
	}
	
	// UIExtension stuff
	public String getShortName() { return "Test SpeciesIdentifier components"; }
	
	public String getDescription() { return "Tests all testable components to ensure that SpeciesIdentifier is working correctly"; }

	public boolean addCommandsToMenu(Menu menu) {
		MenuItem mi = new MenuItem("Run test suite", new MenuShortcut(KeyEvent.VK_T));
		
		mi.addActionListener(this);

		menu.add(mi);	
		return true;
	}
	
	public Frame getFrame() { return null; }
	
}
