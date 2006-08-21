/**
 * A SequencePanel is a rather complex tool for manipulating sequence lists.
 * We work sort-of like a bizarre java.awt.List/something out of frankenstein
 * hybrid. With any luck, it will be seriously powerful.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

package com.ggvaidya.TaxonDNA.Modules;

import java.util.*;
import java.awt.*;
import java.awt.event.*;

import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class SequencePanel extends Panel implements UIExtension, ActionListener, ItemListener, ItemSelectable {
	private TaxonDNA	taxonDNA = null;
	
	private SequenceList	list = null;

	private Panel		listPanel = new Panel();
	private java.awt.List	list_sequences = new java.awt.List(20, false);
	private java.awt.List	backbuffer = new java.awt.List(20, false);
		// no, you're not misreading that
		// because list.addItem is so friggin' slow, we are going to BACKBUFFER IT
		// then flip the panel. back-friggin-buffer. like a friggin video game.
		//
		// god almighty.

	private Vector		itemListeners = new Vector();

	private Button		button_Sort = new Button("Sort");
	private Button		button_Select = new Button("Select");
	private Button		button_Export = new Button("Export selected");
	private Button		button_Remove = new Button("Remove selected");

	private PopupMenu	popup_Sort = new PopupMenu("Sort");
	private PopupMenu	popup_Select = new PopupMenu("Select");

	public static final int	BY_NAME = 1;
	public static final int	BY_SIZE = 2;
	public static final int	BY_GI = 3;
	public static final int BY_FAMILY = 4;
	public static final int BY_AMBIGUOUS = 5;

	private	int		sort =	BY_NAME;

	// limits
	private static final int MAX_DISPLAY_NAME = 30;		// use only the first 30 characters of display names
	
	/**
	 * A SequencePanel is born!
	 */
	public SequencePanel(TaxonDNA taxonDNA) {
		this.taxonDNA = taxonDNA;
		
		setLayout(new BorderLayout());

		// Create a panel of buttons
		Panel buttonPanel = new Panel();
		buttonPanel.setLayout(new GridLayout(1, 2));

		buttonPanel.add(button_Sort);
		button_Sort.addActionListener(this);
		
		buttonPanel.add(button_Select);
		button_Select.addActionListener(this);
		
		add(buttonPanel, BorderLayout.NORTH);

		// set up our listPanel (which is going to flip between the backbuffer and the actual list itself)
		listPanel.setLayout(new CardLayout());
		
		// Add our list itself
		list_sequences.add("                          ");
		list_sequences.addItemListener(this);
		listPanel.add(list_sequences, "list");

		// and our backbuffer
		backbuffer.add("                          ");
		backbuffer.addItemListener(this);
		listPanel.add(backbuffer, "backbuffer");

		// and finally, add it all
		add(listPanel);

		// And then ... the southern button panel
		Panel pSouth = new Panel();
		pSouth.setLayout(new BorderLayout());

		button_Export.addActionListener(this);
		//pSouth.add(button_Export, BorderLayout.NORTH);

		button_Remove.addActionListener(this);
		pSouth.add(button_Remove, BorderLayout.SOUTH);

		add(pSouth, BorderLayout.SOUTH);

		// set up the popups
		// #1: popup_Sort
		button_Sort.add(popup_Sort); 
		popup_Sort.add("Sort by name");
		popup_Sort.add("Sort by size (bp)");
		popup_Sort.add("Sort by GI number");
		popup_Sort.add("Sort by family");
		popup_Sort.add("Sort by ambiguous (%)");
		popup_Sort.addActionListener(this);

		// #2: popup_Select
		button_Select.add(popup_Select);
		popup_Select.add("Select all");
		popup_Select.add("Select by genus");
		popup_Select.add("Select by species");
		popup_Select.addActionListener(this);
	}


	private Sequence savedListState = null;
	/**
	 * Stores the selected sequence of the SequenceSet internally.
	 * The next call to restoreState() will restore this state.
	 */
	private void saveState() {
		savedListState = getSelectedSequence();	
	}	

	/**
	 * Restores the selected sequence of the SequenceSet.
	 */
	private void restoreState() {
		if(savedListState != null)
			selectSequence(savedListState);
		savedListState = null;
	}

	/**
	 * Flips the lists. The way it works is:
	 * -	We have 'list', which is where all the event capturing, etc. go on.
	 *  	'list' is always in the front.
	 * - 	We have 'backbuffer', which is where all edit operations are carried out.
	 *	When completed, we call flipLists() to flip them around.
	 *
	 * So flipLists:
	 * 	Flips the listPanel over, so that backbuffer comes to the fore
	 * 	Swaps the values in list and backbuffer, so that the variables
	 * 		everybody else is using works out
	 */
	private void flipLists() {
		// make sure the backbuffer knows what the list is doing ... 
		int visible = list_sequences.getVisibleIndex();

		CardLayout cl = (CardLayout) listPanel.getLayout();
		cl.next(listPanel);
		
		java.awt.List temp = list_sequences;
		list_sequences = backbuffer;
		backbuffer = temp;

		if(list_sequences.getItemCount() > 0)
			list_sequences.makeVisible(visible);
	}

	/**
	 * Selects the specified sequence. Since I'm in a hurry, and am working in school on a sunday
	 * AGAINST MY WILL i'm going to be quick about this, which unfortunately means O(n) time.
	 * Bite me.
	 *
	 * Hang on ... that's not so bad ... never mind ...
	 */
	public void selectSequence(Sequence seq) {
		for(int x = 0; x < list.count(); x++) {
			if(list.get(x).equals(seq)) {
				list_sequences.select(x);

				// since we're selecting somebody, might
				// as well let everybody else know, eh?
				itemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, new Integer(x), ItemEvent.SELECTED));	
				
				return;
			}
		}
	}
	
	/**
	 * Dataset has changed.
	 */
	public void dataChanged() {
		list = taxonDNA.lockSequenceList();
		
		if(list == null) {
			// it's the null set: reset everything!
			this.list = null;

			backbuffer.removeAll();

			flipLists();

			return;
		}

		// now, we need to restore the sequence which we are at right this
		// minute, so:
		saveState();
			
		prepareSequences();
		rewriteAll();

		// okay, all done: back to our last saved state!
		restoreState();

		taxonDNA.unlockSequenceList();
	}

	private int getSelectedIndex() {
		return list_sequences.getSelectedIndex();
	}
	
	public Sequence getSelectedSequence() {
		if(getSelectedIndex() == -1)
			return null;

		return (Sequence) list.get(getSelectedIndex());
	}

	public void replaceCurrentSequence(Sequence seq) {
		if(getSelectedIndex() == -1)
			return;
		

		list.set(getSelectedIndex(), seq);
		backbuffer.replaceItem(getListName(seq), getSelectedIndex());
		flipLists();
		taxonDNA.unlockSequenceList();
	}

	private void prepareSequences() {
		int sequence_list_sort = 0;

		switch(sort) {
			case BY_NAME:
				sequence_list_sort = SequenceList.SORT_BYNAME;
				break;
			case BY_GI:
				sequence_list_sort = SequenceList.SORT_BYGI;
				break;
			case BY_FAMILY:
				sequence_list_sort = SequenceList.SORT_BYFAMILY;
				break;
			case BY_SIZE:
				sequence_list_sort = SequenceList.SORT_BYSIZE;
				break;
			case BY_AMBIGUOUS:
				sequence_list_sort = SequenceList.SORT_PROPORTION_AMBIGUOUS;
				break;
		} 

		list.resort(sequence_list_sort);
	}

	private void rewriteAll() {
		backbuffer.removeAll();
		for(int x = 0; x < list.count(); x++)
			backbuffer.add(getListName((Sequence)list.get(x)));

		flipLists();
	}

	private String getListName(Sequence seq) {
		// KLUDGE!
		String name = "";
		if(seq.getSubspeciesName().equals("")) {
			name = seq.getSpeciesName();
		} else {
			name = seq.getSpeciesName() + " " + seq.getSubspeciesName();
		}

		if(seq.getWarningFlag())
			name = "{" + seq.getFullName() + "}";

		if(name.length() > MAX_DISPLAY_NAME) {
			name = name.substring(0, MAX_DISPLAY_NAME - 3) + "...";
		}

		// add extra stuff
		if(sort == BY_NAME || sort == BY_GI) {
			String giNumber = "";
			if(seq.getGI() != "") {
				giNumber = " (gi:" + seq.getGI() + ")";
			}
			return name + giNumber; 
		} else if(sort == BY_FAMILY) {
			String giNumber = "";

			if(seq.getGI() != "") {
				giNumber = " (gi:" + seq.getGI() + ")";
			}

			if(seq.getFamilyName().equals(""))
				return "Unknown: " + name + giNumber;
			else
				return seq.getFamilyName() + ": " + name + giNumber;
		} else if(sort == BY_SIZE) {
			return name + " [" + seq.getActualLength() + " bp]";
		} else if(sort == BY_AMBIGUOUS) {
			double percentage = Settings.percentage((double)seq.getAmbiguous() / seq.getActualLength(), 1); 
			return name + " [" + percentage + "% ambiguous - " + seq.getAmbiguous() + "/" + seq.getActualLength() + "]";
		} else {
			return "Undefined";
		}
	}

	/**
	 * Somebody hit one of our buttons or popups!
	 */
	public void actionPerformed(ActionEvent e) {
		// button_Remove
		if(e.getSource().equals(button_Remove)) {
			Sequence seq = getSelectedSequence();

			if(seq == null)
				return;

			// seq will now have to be .. eliminated
			list = taxonDNA.lockSequenceList();
			list.remove(seq);

			// since this needs to be responsive, let's just delete
			// it immediately ...
			int index = getSelectedIndex(); 
			int indexNext = index;
			if(indexNext == list_sequences.getItemCount() - 1)
				indexNext--;
				
			// change list_sequences to represent new state
			list_sequences.remove(index);
			list_sequences.select(indexNext);
			list_sequences.makeVisible(indexNext);

			// select the next one on the right as well
			Sequence seqNext = getSelectedSequence();
			selectSequence(seqNext);
			
			// and ... we've changed!
			taxonDNA.unlockSequenceList();
		}
		
		// button_Sort
		if(e.getSource().equals(button_Sort)) {
			// activate the popup
			popup_Sort.show(button_Sort, 0, 0);
		}

		// popup_Sort
		if(e.getSource().equals(popup_Sort)) {
			// we can't do anything if there's no data ...
			if(list == null)
				return;
			
			String cmd = e.getActionCommand();

			if(sort != BY_NAME && cmd.equals("Sort by name")) {
				sort = BY_NAME;
				prepareSequences();
				rewriteAll();
			}

			if(sort != BY_SIZE && cmd.equals("Sort by size (bp)")) {
				sort = BY_SIZE;
				prepareSequences();
				rewriteAll();
			}

			if(sort != BY_GI && cmd.equals("Sort by GI number")) {
				sort = BY_GI;
				prepareSequences();
				rewriteAll();
			}

			if(sort != BY_FAMILY && cmd.equals("Sort by family")) {
				sort = BY_FAMILY;
				prepareSequences();
				rewriteAll();
			}

			if(sort != BY_AMBIGUOUS && cmd.equals("Sort by ambiguous (%)")) {
				sort = BY_AMBIGUOUS;
				prepareSequences();
				rewriteAll();
			}
		}
	}
	
	/**
	 * Somebody selected something!
	 */
	public void itemStateChanged(ItemEvent e) {
		if(list == null)
			return;

		Sequence seqTarget = (Sequence) list.get(((Integer)e.getItem()).intValue());
		
		// propagate it downstream
		ItemEvent eOnward = new ItemEvent(list_sequences, e.getID(), seqTarget, e.getStateChange());
		Iterator i = itemListeners.iterator();
		while(i.hasNext()) {
			((ItemListener)i.next()).itemStateChanged(eOnward);
		}
	}

	/**
	 * Add an item listener
	 */
	public void addItemListener(ItemListener l) {
		itemListeners.add(l);
	}

	public Object[] getSelectedObjects() {return null;}
	

	public void 	removeItemListener(ItemListener l) {
		itemListeners.remove(l);
	}

	// UIExtension stuff
	public Frame getFrame() {	return null; 	}
	public Panel getPanel() {	return this;	}
	public String getShortName() {	
		return "SequencePanel"; 
	}
	public String getDescription() {	
		return "The SequencePanel is an editable panel containing a list of sequences."; 
	}
	public boolean addCommandsToMenu(Menu m) {	return false; }
}
