
/*
 *
 *  TaxonDNA
 *  Copyright (C) 2010 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.DtClusters.gui;

import com.ggvaidya.TaxonDNA.DtClusters.*;

import java.awt.*;
import javax.swing.*;

/**
 * The main frame creates a main frame for us to use to get DtClusters stuff
 * done.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class MainFrame extends JFrame {
	public MainFrame() {
		super(DtClusters.getName() + "/" + DtClusters.getVersion());
		setDefaultCloseOperation(MainFrame.DISPOSE_ON_CLOSE);
	}

	public void setContent(JComponent component) {
		add(new JScrollPane(component));
	}
}
