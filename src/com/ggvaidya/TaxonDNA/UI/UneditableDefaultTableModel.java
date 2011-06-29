
/*
 *
 *  UneditableDefaultTableModel
 *  Copyright (C) 2011 Gaurav Vaidya
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
package com.ggvaidya.TaxonDNA.UI;

import javax.swing.table.*;

/**
 * This class extends the DefaultTableModel class, with one crucial
 * advantage: it prevents users from modifying data in this table.
 * This is primarily designed for display-only tables.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class UneditableDefaultTableModel extends DefaultTableModel {
	int type = TURN_OFF_EDITING;
	public static final int TURN_OFF_EDITING = 1;
	public static final int UNDO_EDITING = 2;
	
	public UneditableDefaultTableModel(int flag) { 
		super();
		type = flag;
	}
	
	@Override public void setValueAt(Object aValue, int row, int col) {
		if((type & UNDO_EDITING) != 0) {
			Object oldValue = getValueAt(row, col);
			super.setValueAt(oldValue, row, col);
		}
	}
	
	@Override public boolean isCellEditable(int row, int col) {
		if((type & TURN_OFF_EDITING) != 0) {
			return false;
		}
		return super.isCellEditable(row, col);
	}
}