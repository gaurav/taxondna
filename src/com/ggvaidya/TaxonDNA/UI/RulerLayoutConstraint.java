
/*
 *
 *  TaxonDNA
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

/**
 * The Constraint object which should be used when laying objects out
 * on a RulerLayout.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class RulerLayoutConstraint {
		int			row;
		int			value;
		
		public static final int	SAMELINE =		-1;
		public static final int	NEXTLINE =		-2;
		
		/**
		 * Create a Constraint to be used to configure RulerLayouts.
		 * 
		 * @param row		The row this component should be added to.
		 *		Note that rows are zero-based!
		 *		Use Constraint.NEXTLINE to indicate that it should be
		 *		on the next line to the last object, and Constraint.SAMELINE
		 *		to indicate that it should be on the same line.
		 * @param value		The 'value' that this object ought to be placed
		 *		on. If this isn't within the leftmost/rightmost values specified
		 *		to the RulerLayout, it won't be displayed at all.
		 */
		public RulerLayoutConstraint(int row, int value) {
			this.row =		row;
			this.value =	value;
		}
		
		public int getRow()		{	return row;		}
		public int getValue()	{	return value;	}
		public int value()		{	return value;	}
}
