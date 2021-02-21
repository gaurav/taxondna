/**
 * RightLayout.java
 *
 * A rectilinear layout is one with lots of rectangles. In this case,
 * it's a hack resembling a much more flexible GridLayout hacked over
 * the real underlying class (which is the intensively complicated 
 * GridBagLayout).
 *
 * The way it works is this:
 * 1.	You add objects. Every time you add an object, you tell me
 * 	whether you want it BESIDE or UNDER the last one. Thus,
 * 	to make following layout:
 * 		A	B
 * 		C	D
 * 	you would use:
 * 		add(A);		// Default is BESIDE
 * 		add(B, RightLayout.BESIDE);
 * 		add(C, RightLayout.UNDER);
 * 		add(D, RightLayout.BESIDE);
 * 	Yes, it's a bit simplistic, but it's all I need. If you
 * 	need more, you can always ring up a proper GridBagLayout. 
 */

/*
    TaxonDNA
    Copyright (C) 2006	Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Common.UI;

import java.awt.*;

public class RightLayout implements LayoutManager2 {
	//	32-bit int:	00	00	00	00
	//					stretch location
	//	location:
	//		BESIDE the last object:		0x0001
	//		BELOW the last object:		0x0002
	//		FLUSHLEFT:			0x0004
	//		FLUSHRIGHT:			0x0008
	//		NEXTLINE:			BELOW | FLUSHLEFT
	//
	//	stretch
	//		STRETCH_X:			0x0100
	//		STRETCH_Y:			0x0200
	//		STRETCH_BOTH:			STRETCH_X | STRETCH_Y
	//
	//	anchors
	//		CENTER				0x0010
	//		RIGHT				0x0020
	//		LEFT				0x0040
	//
	//	fill extra:
	//		FILL_2:				0x0400		Fills up 2,
	//		FILL_3:				0x0500		3,
	//		FILL_4:				0x0600		and 4 columns respectively.
	//
	//	hack bit
	//		ALREADY_ADDED:			0xF000
	//			Means that this component has already been
	//			added to the specified object. If this bit
	//			is NOT set, we assume we're coming from
	//			RightLayout.add(), which means we have
	//			to add it. Also, if addComponentLayout
	//			sees this bit, it'll silent ignore the
	//			call.
	//
	public static final int NO_FLAGS	=	0x0000;
	public static final int NONE		=	NO_FLAGS;

	public static final int BESIDE		=	0x0001;
	public static final int BELOW		=	0x0002;
	public static final int FLUSHLEFT	=	0x0004;
	public static final int FLUSHRIGHT	=	0x0008;
	public static final int NEXTLINE	=	BELOW | FLUSHLEFT;

	public static final int CENTER		=	0x0010;
	public static final int RIGHT		=	0x0020;
	public static final int LEFT		=	0x0040;

	public static final int STRETCH_X 	=	0x0100;
	public static final int STRETCH_Y 	=	0x0200;
	public static final int STRETCH_BOTH	=	STRETCH_X | STRETCH_Y;

	public static final int FILL_2		=	0x0400;
	public static final int FILL_3		=	0x0800;
	public static final int FILL_4		=	0x1000;
	
	public static final int DEBUG		=	0x2000;

	public static final int ALREADY_ADDED	=	0xF000;

	private GridBagLayout gb = new GridBagLayout();			// underlying layout engine
	private GridBagConstraints gbc = new GridBagConstraints();

	private Container container;

	public RightLayout(Container container) {
		this.container = container;
		container.setLayout(this);

		gbc.anchor = GridBagConstraints.NORTHWEST;	
		gbc.fill = GridBagConstraints.HORIZONTAL;

		gbc.gridheight = 1;
		gbc.gridwidth = 1;

		gbc.gridx = 0;
		gbc.gridy = 0;

		gbc.insets = new Insets(5, 5, 5, 5);

		gbc.ipadx = 0;
		gbc.ipady = 0;

		gbc.weightx = 0;
		gbc.weighty = 0;
	}

	/*
	 * From LayoutManager2
	 */
	public void addLayoutComponent(Component comp, Object constraints) {
		// We only use Integer contraints, so let's cast
		// (and trigger off CastExceptions left, right
		// and center if somebody doesn't pass us what
		// we want)
		int flags;
		if(constraints != null && constraints.getClass().equals(Integer.class))
			flags = ((Integer)constraints).intValue();
		else
			// default settings
			flags = BELOW | STRETCH_X; 


		// flags with permanent effects
		if((flags & BESIDE) != 0) {
			gbc.gridx++;

			if(gbc.gridx > right_most)
				right_most = gbc.gridx;
		}

		if((flags & BELOW) != 0) {
			// we need to move down one in the constrains.
			gbc.gridy++;
		}

		if((flags & FLUSHLEFT) != 0) {
			gbc.gridx = 0;
		}

		if((flags & FLUSHRIGHT) != 0) {
			gbc.gridx = right_most;
		}

		// flags with temporary effects
		if((flags & STRETCH_X) != 0) {
			gbc.weightx = 1;
		}

		if((flags & STRETCH_Y) != 0) {
			gbc.weighty = 1;
		}

		if((flags & FILL_2) != 0) {
			gbc.gridwidth = 2;
		}
		
		if((flags & FILL_3) != 0) {
			gbc.gridwidth = 3;
		}

		if((flags & FILL_4) != 0) {
			gbc.gridwidth = 4;
		}

		if((flags & CENTER) != 0) {
			gbc.anchor = GridBagConstraints.CENTER;
		}

		if((flags & LEFT) != 0) {
			gbc.anchor = GridBagConstraints.WEST;
		}

		if((flags & RIGHT) != 0) {
			gbc.anchor = GridBagConstraints.EAST;
		}

		if((flags & DEBUG) != 0)
			System.err.println("Laying out " + comp + " at " + gbc.gridx + ", " + gbc.gridy + ", gridwidth = " + gbc.gridwidth);

		// do it
		gb.addLayoutComponent(comp, gbc);

		// then undo temporary effects
		gbc.weightx = 0;
		gbc.weighty = 0;

		gbc.anchor = GridBagConstraints.WEST;

		gbc.gridwidth = 1;
	}

	/**
	 * Our custom add-with-flags. You can use addLayoutComponent
	 * (via Container.add(component, constraints), but this is
	 * really so much easier, as you don't ahve to keep defining
	 * an 'Integer' to handle the flags
	 */
	private int right_most = 0;	// right_most -> right most 'gridx'
	public void add(Component comp, int flags) {
		// add it to the container first
		// this assumes that container is
		// laid out with a RightLayout.
		//
		// Otherwise, great confusion ensues.
		container.add(comp, new Integer(flags));
	}

	public float	getLayoutAlignmentX(Container target) {
		return gb.getLayoutAlignmentX(target);
	}

	public float getLayoutAlignmentY(Container target) {
		return gb.getLayoutAlignmentY(target);
	}

	public void invalidateLayout(Container target) {
		gb.invalidateLayout(target);
	}
	
	public Dimension maximumLayoutSize(Container target) {
		return gb.maximumLayoutSize(target);
	}

	/*
	 * From LayoutManager
	 */
	// We don't do this, so we'll pretend like the String is the constraint (whatever sense that makes) 
	// p.s. it doesn't - addLayoutComponent will screw up when it sees an Object instead of an Int.
	// 	Oh well.
	public void addLayoutComponent(String name, Component comp) {
		addLayoutComponent(comp, (Object)name);	
	}
	public void layoutContainer(Container parent) {
		gb.layoutContainer(parent);
	}
	public Dimension minimumLayoutSize(Container parent) {
		return gb.minimumLayoutSize(parent);
	}
        public Dimension preferredLayoutSize(Container parent) {
		return gb.preferredLayoutSize(parent);
	}
	public void removeLayoutComponent(Component comp) {
		gb.removeLayoutComponent(comp);
	}
}
