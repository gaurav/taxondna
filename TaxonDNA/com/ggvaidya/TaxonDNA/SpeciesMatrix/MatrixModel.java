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

	int totalWidth = 0;

	public MatrixModel(SpeciesMatrix matrix) {
		this.matrix = matrix;

		columns.add(null);
		columnNames.add("");
	}

	public void clear() {
		columns = new Vector();
		columnNames = new Vector();
		speciesNames = new Vector();		// change this every time we update 'speciesList'
		speciesList = new Hashtable();

		totalWidth = 0;
		
		updateDisplay();
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

		// 2.1. set the new total width
		totalWidth += sl.getMaxLength();

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

	public void exportAsNexus(File f) throws IOException {
		PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(f)));
		StringBuffer buff_sets = new StringBuffer();

		buff_sets.append("BEGIN SETS;\n");


		writer.println("#NEXUS");
		writer.println("[Written by SpeciesMatrix on " + new Date() + "]");

		writer.println("");

		writer.println("BEGIN DATA;");
		writer.println("\tDIMENSIONS NTAX=" + speciesNames.size() + " NCHAR=" + totalWidth + ";");
		writer.println("\tFORMAT DATATYPE=DNA GAP=- MISSING=? INTERLEAVE;");
		writer.println("MATRIX");

		int widthThusFar = 0;
		for(int x = 1; x < columnNames.size(); x++) {
			String columnName = (String)columnNames.get(x);
			writer.println("[beginning " + columnName + "]");	
		
			columnName = columnName.replaceAll("\\.nex", "");
			columnName = columnName.replace('.', '_');
			columnName = columnName.replace(' ', '_');

			SequenceList sl = (SequenceList) columns.get(x);
			if(sl == null)
				break;
			
			buff_sets.append("\tCHARSET " + columnName + " = " + (widthThusFar + 1) + "-" + (widthThusFar + sl.getMaxLength()) + ";\n");
			widthThusFar += (sl.getMaxLength());

			SpeciesDetails det = null;
			try {
				det = sl.getSpeciesDetails(null);
			} catch(DelayAbortedException e) {
				// ignore
			}

			Iterator i = speciesNames.iterator();
			while(i.hasNext()) {
				String name = (String) i.next();
				String name_for_output = nexusName(name);

				// TODO: figure out proper, unique names etc.

				if(det.getSpeciesDetailsByName(name) == null) {
					// it doesn't exist!
					writer.print(name_for_output + " ");
					for(int c = 0; c < sl.getMaxLength(); c++) {
						writer.print('-');
					}
					writer.println("");
				} else {
					Sequence seq = null;
					Sequence seq_longest = null;
					Iterator i2 = sl.iterator();

					while(i2.hasNext()) {
						seq = (Sequence) i2.next();

						if(seq.getSpeciesName().equals(name)) {
							seq_longest = seq;
							sl.lock();
							i2 = sl.conspecificIterator(seq);

							while(i2.hasNext()) {
								seq = (Sequence) i2.next();

								if(seq.getActualLength() > seq_longest.getActualLength())
									seq_longest = seq;
							}
							
							sl.unlock();
							break;
						}
					}
								
					writer.println(nexusName(seq_longest.getSpeciesName()) + " " + seq_longest.getSequence());
				}
			}

			writer.println("[end of " + (String)columnNames.get(x) + "]");	
			writer.println("");	
		}

		// end the DATA block
		writer.println(";");
		writer.println("END;");
		
		// end and write the SETS block
		buff_sets.append("END;");
		writer.println(buff_sets);

		writer.close();
	}

	private String nexusName(String x) {
		return x.replaceAll("'", "''").replace(' ', '_');
	}
}
