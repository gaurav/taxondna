
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

import java.awt.*;
import java.util.*;
import java.util.Map.Entry;

import javax.swing.*;

/**
 * A RulerLayout is a layout manager where components need to be placed so
 * that their X-dimension is located on a horizontal ruler. Y-dimensions
 * are wrapped to "lines" for now.
 * 
 * This was basically written for DtClusters to be able to visualise clusters,
 * but I figure it might make sense to abstract this.
 * 
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class RulerLayout implements LayoutManager2 {
	public static final int RULER_HEIGHT = 100;
	// We store all the components in a LinkedList of LinkedLists.
	// That way it's really easy to add as many lines as possible, and
	// not so hard to find a particular component on a particular line,
	// if necessary.
	LinkedList<TreeMap<Integer, Component>> lines = new LinkedList<TreeMap<Integer, Component>>();
	
	// For speed, we also maintain an independent list of components mapped to their line.
	HashMap<Component, TreeMap<Integer, Component>> components = new HashMap<Component, TreeMap<Integer, Component>>();
	
	int leftmost_value =	-1;
	int rightmost_value =	-1;
	int columns =			0;
	double col_span =		-1;
	
	private JPanel rulerPanel = null;
	
	public RulerLayout(int leftmost, int rightmost, int cols) {
		leftmost_value =	leftmost;
		rightmost_value =	rightmost;
		columns =			cols;
		
		col_span =			((double)rightmost - leftmost)/cols;
	}
	
	public void addLayoutComponent(Component comp, Object constraints) {
		if(constraints == null) {
			// we don't need to bother about laying it out.
			return;
		}
		
		if(!RulerLayoutConstraint.class.isAssignableFrom(constraints.getClass())) {
			throw new RuntimeException("Constraints value is not a RulerLayoutConstraint object: " + constraints);
		}
		
		RulerLayoutConstraint cons = (RulerLayoutConstraint) constraints;
		
		if(cons.getRow() == RulerLayoutConstraint.SAMELINE) {
			cons.row = lines.size();
		}
		if(cons.getRow() == RulerLayoutConstraint.NEXTLINE) {
			cons.row = lines.size() + 1;
		}
		
		// Find the row to add this object to.
		if(cons.getRow() >= lines.size()) {
			for(int diff = (cons.getRow() - lines.size() + 1); diff > 0; diff--) {
				lines.add(new TreeMap<Integer, Component>());
			}
		}
		
		TreeMap<Integer, Component> line = lines.get(cons.getRow());
		if(line.get(cons.getValue()) == null) {
			System.err.println("Putting component " + comp + " on line " + cons.getRow() + ", value " + cons.getValue() + ".");
			line.put(cons.getValue(), comp);
			components.put(comp, line);
		} else {
			System.err.println("Replacing component " + line.get(cons.getValue())  + " with component " + comp + " on line " + cons.getRow() + ", value " + cons.getValue() + ".");
			line.put(cons.getValue(), comp);
			components.put(comp, line);
		}
	}
	
	public void removeLayoutComponent(Component component) {
		TreeMap<Integer, Component> line = components.get(component);
		
		// I'm assuming that we will never have many components
		// on a row; otherwise, this will need optimization!
		boolean found = false;
		for(Entry<Integer, Component> entry: line.entrySet()) {
			if(entry.getValue().equals(component)) {
				line.remove(entry.getKey());
				components.remove(component);
				found = true;
				break;
			}
		}
		
		if(!found) {
			// Ignore for now, we'll reinstate when we get the duplicates sorted.
			// throw new RuntimeException("Could not find component to remove: " + component + ". Are you sure it has been added?");
			System.err.println("Could not find component to remove: " + component + ". Are you sure it has been added?");
		}
		
		// Component has been removed! Do we need to redraw? I guess not.
	}
	
	public float getLayoutAlignmentX(Container target) {
		return 0.5f;
	}

	public float getLayoutAlignmentY(Container target) {
		return 0f;
	}

	public void invalidateLayout(Container target) {
		// No cached information, so nothing to invalidate!
	}
	
	public void addLayoutComponent(String name, Component comp) {
		addLayoutComponent(comp, new RulerLayoutConstraint(RulerLayoutConstraint.NEXTLINE, leftmost_value));
	}

	// Layout sizes.
	public static final int		BUTTON_WIDTH =		300;
	public static final int		BUTTON_HEIGHT =		150;
	
	public Dimension maximumLayoutSize(Container target) {
		return new Dimension(BUTTON_WIDTH * (columns + 1), BUTTON_HEIGHT * lines.size() + RULER_HEIGHT);
	}

	public Dimension preferredLayoutSize(Container parent) {
		return maximumLayoutSize(parent);
	}

	public Dimension minimumLayoutSize(Container parent) {
		return new Dimension(50, 50);
	}

	// Time for some layouting!
	public void layoutContainer(Container parent) {
		int ruler_height = 0;
		if(rulerPanel != null)
			ruler_height = RULER_HEIGHT;
		
		for(Component c: parent.getComponents()) {
			int row = -1;
			int value = -1;
			
			// Put the rulerPanel on top.
			if(c == rulerPanel) {
				rulerPanel.setBounds(0, 0, parent.getWidth(), RULER_HEIGHT);
				continue;
			}
			
			if(components.containsKey(c)) {
				TreeMap<Integer, Component> location = components.get(c);
				
				row = lines.indexOf(location);
				value = -1;
				for(Entry<Integer, Component> e: location.entrySet()) {
					// System.err.println("Comparing " + c + " with " + e.getValue() + ": " + e.getValue().equals(c));
					if(e.getValue().equals(c)) {
						value = e.getKey().intValue();
					}
				}
				
				if(value != -1) {
					int col = (int)Math.floor((value - leftmost_value)/col_span);
					c.setBounds(col * BUTTON_WIDTH, row * BUTTON_HEIGHT + ruler_height, BUTTON_WIDTH - 50, BUTTON_HEIGHT - 50);
					
					continue;
				}
			} else {
				throw new RuntimeException("Could not layout component " + c + ": it doesn't exist in the components table at all.");
			}
			
			// Ignore for now: we'll look into this later.
			// throw new RuntimeException("Couldn't layout component " + c + ": not found in RulerLayout! (last placed at row=" + row + ", col=" + col + ")");
			System.err.println("Couldn't layout component " + c + ": not found in RulerLayout!");
		}
	}

	public int getRightmostValue() {
		return rightmost_value;
	}
	
	public int getColumnCount() {
		return columns;
	}
	
	/**
	 * Once you're done adding in all your components, you can ask to add a "ruler",
	 * which is merely a panel containing labels marking out the position of each
	 * 
	 * 
	 * @return 
	 */
	public Component getRuler() {
		rulerPanel = new JPanel();
		rulerPanel.setLayout(null);
		
		for(int x = 0; x <= columns; x++) {
			JButton btn_label = new JButton("" +  (x * col_span));
			btn_label.setBounds(BUTTON_WIDTH * x, 0, BUTTON_WIDTH - 50, BUTTON_HEIGHT - 50);
			rulerPanel.add(btn_label);
		}
		
		return rulerPanel;
	}
}
