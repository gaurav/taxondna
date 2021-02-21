/**
 * Displays current system usage, just so you know where you are.
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

package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.awt.*;
import java.awt.event.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;


public class SystemUsage extends Panel implements UIExtension, ActionListener, ComponentListener {
	private TextArea	text_main = new TextArea();
	private Button		button_Refresh = new Button("Optimize and refresh");

	public SystemUsage(SpeciesIdentifier seqId) {
		// layouting
		setLayout(new BorderLayout());	
		
		button_Refresh.addActionListener(this);
		add(button_Refresh, BorderLayout.NORTH);
	       	
		text_main.setEditable(false);
		text_main.setFont(new Font("Monospaced", Font.PLAIN, 12));
		addComponentListener(this);
		add(text_main);
	}

	public void itemStateChanged(ItemEvent e) {
	}

	public void actionPerformed(ActionEvent evt) {
		// collect garbage!
		Runtime.getRuntime().gc();
		resetMemory();
	}

	public void 	componentHidden(ComponentEvent e) {}
	public void 	componentMoved(ComponentEvent e) {}
	public void 	componentResized(ComponentEvent e) {}
	public void 	componentShown(ComponentEvent e) {
		resetMemory();
	}

	public void 	resetMemory() {
		// listener only set on the TextArea
		StringBuffer buffer = new StringBuffer();
		Runtime runtime = Runtime.getRuntime();

		buffer.append("Number of processors available: " + runtime.availableProcessors() + " processors\n");
		buffer.append("\n");
		buffer.append("Maximum memory available for use:             " + printMemory(runtime.maxMemory()) + "\n");
		buffer.append("Total memory available for use at the moment: " + printMemory(runtime.totalMemory()) + "\n");
		buffer.append("Free memory available for use at the moment:  " + printMemory(runtime.freeMemory()) + " (" + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(runtime.freeMemory(), runtime.totalMemory()) + "%)\n");

		long totalUsedMemory = runtime.totalMemory() - runtime.freeMemory();
		long totalFreeMemory = runtime.maxMemory() - totalUsedMemory;
		buffer.append("\n");
		buffer.append("Total memory used: " + printMemory(totalUsedMemory) + " (" + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(totalUsedMemory, runtime.maxMemory()) + "%)\n");
		buffer.append("Total memory free: " + printMemory(totalFreeMemory) + " (" + com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(totalFreeMemory, runtime.maxMemory()) + "%)\n");

		text_main.setText(buffer.toString());
	}

	private String printMemory(long memory) {
		//return DNA.Settings.percentage(memory, 1024) + " KB, or " + DNA.Settings.percentage(memory, 1024 * 1024) + " MB";
		if(memory == Long.MAX_VALUE)
			return "No limit";

		return com.ggvaidya.TaxonDNA.Common.DNA.Settings.roundOff(memory / (1024 * 1024)) + " MB\t(" + com.ggvaidya.TaxonDNA.Common.DNA.Settings.roundOff(memory / 1024) + " KB)";
	}
	
	public void dataChanged()	{
		return;
	}

	public String getShortName() {		return "System Usage"; 	}
	public String getDescription() {	return "Displays the current system usage"; }
	public boolean addCommandsToMenu(Menu commandMenu) {	return false; }
	public Panel getPanel() {
		return this;
	}
}
