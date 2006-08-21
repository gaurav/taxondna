/**
 * The TableModel for the main gene/sequence list thing.
 * This is where all the clearinghouse/backoffice stuff happens;
 * if needed, we'll make a larger clearinghouse area. Hopefully,
 * this will hook straight into the necessary DNA.* classes
 * and make things happen. But, you know, it might not.
 *
 * I don't know why I love phrasing/phrases like this right now.
 */

/*
 *
 *  SpeciesMatrix
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

package com.ggvaidya.TaxonDNA.SpeciesMatrix;

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

public class MatrixModel implements TableModel {
	SpeciesMatrix matrix = null;
	Vector listeners = new Vector();
	Vector columns = new Vector();
	Vector columnNames = new Vector();
	Vector speciesNames = new Vector();		// change this every time we update 'speciesList'
	Hashtable speciesList = new Hashtable();

	public MatrixModel(SpeciesMatrix matrix) {
		this.matrix = matrix;

		columns.add(null);
		columnNames.add("");
	}
		
	public void addSequenceList(SequenceList sl) {
		sl.lock();

		// 1. Add a column into 'columns'.
		columns.add(sl);

		// 2. Add the column name.
		if(sl.getFile() != null) {
			columnNames.add(sl.getFile().getName());
		} else {
			columnNames.add("Unknown");
		}

		// 3. Modify the speciesNames vector to match the new combined species lists.
		try {
			SpeciesDetails dets = sl.getSpeciesDetails(new ProgressDialog(
					matrix.getFrame(),
					"Please wait, calculating species information ...",
					"I'm calculating species information for your newly added sequence list. Sorry for the wait!"
					));

			Iterator i = dets.getSpeciesNamesIterator();
			boolean modified = false;

			while(i.hasNext()) {
				String name = (String)i.next();
				
				if(speciesList.get(name) == null) {
					// add it
					modified = true;
					speciesList.put(name, new Object());
					speciesNames.add(name);
				}
			}

			updateDisplay();
		} catch(DelayAbortedException e) {
			updateDisplay();
		} finally {	
			sl.unlock();	
		}
	}

	private void updateDisplay() {
		// resort the speciesNames
		Collections.sort(speciesNames);

		// let everybody know
		Iterator i = listeners.iterator();
		while(i.hasNext()) {
			TableModelListener l = (TableModelListener)i.next();	

			System.err.println("Sending notification to " + l);
			l.tableChanged(new TableModelEvent(this, TableModelEvent.HEADER_ROW));	// TODO: Optimize this! Let's not redraw EVERYTHING, can?
		}
	}

	public void addTableModelListener(TableModelListener l) {
		listeners.add(l);
	}
	
	public void removeTableModelListener(TableModelListener l) {
		listeners.remove(l);
	}	

	public Class getColumnClass(int columnIndex) {
		return String.class;
	}

	public int getColumnCount() {
		System.err.println("Request for col count: " + columns.size());
		return columns.size();
	}

        public String getColumnName(int columnIndex) {
		System.err.println("Request for col name: " + columnIndex + ": " + columnNames.get(columnIndex));
		return (String) columnNames.get(columnIndex);
	}

	public int getRowCount() {
		return speciesList.keySet().size();
	}

        public Object getValueAt(int rowIndex, int columnIndex) {
		String speciesName = (String) speciesNames.get(rowIndex);

		if(columnIndex == 0) {
			// reserved for the speciesName!
			return speciesName;
		}
		
		SpeciesDetail det = null;

		SequenceList list = (SequenceList)(columns.get(columnIndex));
		list.lock();
		try {
			det = list.getSpeciesDetails(null).getSpeciesDetailsByName(speciesName);
		} catch(DelayAbortedException e) {
			// wtf?
			return null;
		} finally {
			list.unlock();
		}

		if(det == null)
			return "0";

		return String.valueOf(det.getSequencesCount());
	}

        public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		return;
	}
}
