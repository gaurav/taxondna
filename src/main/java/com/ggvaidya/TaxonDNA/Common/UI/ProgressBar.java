/**
 * A ProgressBar is a canvas which gradually fills up when the changeIndicator()
 * is called. It may have a String (text) written across it as well.
 *
 * As a hack, we use the inactiveCaption/activeCaption contrast to power our bar.
 * As the two must contrast sharply, it might look ugly (probably not) but will
 * be very flexible across OSs. On the other hand, we're assuming both colours
 * are dark, so we can write the text ("Please wait ... (5%)") in white over them.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com 
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

package com.ggvaidya.TaxonDNA.Common.UI;

import com.ggvaidya.TaxonDNA.Common.*;
import java.awt.*;

public class ProgressBar extends Canvas implements DelayCallback {	
	private int	done = 0;
	private int	total = 100;
	private String	text = "";
	
	/**
	 * Creates a ProgressBar with no text.
	 */
	public ProgressBar() {
		setBackground(SystemColor.inactiveCaption);
		setSize(20, 20);
	}

	/**
	 * Creates a ProgressBar with a specified text.
	 */
	public ProgressBar(String text) {
		this();
		this.text = text;
	}

	/**
	 * Changes the "indicator" of the bar. The change
	 * should be continually increasing, or the bar
	 * will stay "stuck" at the higher point until the
	 * values catch up again.
	 */
	public void changeIndicator(int done, int total) {
		this.done = done;
		this.total = total;

		invalidate();
		if(isShowing()) {
			paint(getGraphics());
		}
	}

	public void setText(String text) {
		this.text = text;
	}

	/**
	 * Paints the progress bar
	 */
	public void paint(Graphics g) {
		int width = (int)((float)getWidth() * ((float)done/total));
		
		g.setColor(SystemColor.inactiveCaption);
		g.fillRect(width, 0, getWidth(), getHeight());		
		g.setColor(SystemColor.activeCaption);
		g.fillRect(0, 0, width, getHeight());
		
		if(!text.equals("")) {
			String display = text + " (" + (int)((float)done/total * 100) + "%)";
			
			int stringWidth = g.getFontMetrics().stringWidth(display);
			int stringX = (getWidth() - stringWidth) / 2;
			int stringY = g.getFontMetrics().getHeight() + (getHeight() - g.getFontMetrics().getHeight()) / 4;

//			g.setColor(SystemColor.activeCaption);
//			g.setXORMode(SystemColor.inactiveCaption);
			g.setColor(Color.WHITE);
			g.drawString(display, stringX, stringY); 
		}
	}

	// Delay callback methods
	//
	public void begin() {
		setText("Please wait ...");
	}

	public void delay(int done, int total) {
		changeIndicator(done, total);
	}

	public void end() {
		setText("All done!");
		changeIndicator(100, 100);
	}
	
	public void addWarning(String x) {
		throw new RuntimeException("ProgressBars cannot indicate warnings to users, sorry!");
	}
}
