/**
 * An UndoTask is a task which can be undoed. It is used by UndoStack to
 * keep track of what's going on, and allows us to be a little more flexible
 * with our tasks.
 *
 */
/*
    TaxonDNA
    Copyright (C) 2005 Gaurav Vaidya
    
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

public class UndoTask {
	// use these or make your own!
	public static final int	UNDO_ADD	=	0;
	public static final int	UNDO_DELETE	=	0;

	private int type = 0;
	private Object target = null;
	
	public UndoTask(int type, Object target) {
		this.type = type;
		this.target = target;
	}

	public int getType() {
		return type;
	}

	public Object getTarget() {
		return target;
	}
}
