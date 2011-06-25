
/*
 *
 *  DtClusters
 *  Copyright (C) 2010-11 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.DtClusters;

import com.ggvaidya.TaxonDNA.DtClusters.cli.*;
import com.ggvaidya.TaxonDNA.DtClusters.gui.*;

/**
 * The main class of the DtClusters menu. This checks for command-line
 * instructions, ultimately handing control over to MainFrame.
 *
 * @author Gaurav Vaidya
 */
public class DtClusters {
	
	/**
	 * Kicks off this program.
	 * 
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		// Parse the command line.
		CommandLine cl = new CommandLine(args);
		if(cl.startGUI()) {
			MainFrame mf = new MainFrame();
			mf.setVisible(true);
		} else {
			cl.run();
		}
	}

	/**
	 * Returns the version of this program.
	 * Eventually we'll get this from Common.Versions,
	 * but for now we've got our own.
	 *
	 * @return The version as a string.
	 */
	public static String getVersion() {
		return "0.01_01";
	}

	public static String getCopyrightNotice() {
		return
			DtClusters.getName() + " version " + DtClusters.getVersion() + "\n" +
			"Copyright (C) 2010-11 Gaurav Vaidya\n" +
			DtClusters.getName() + " comes with ABSOLUTELY NO WARRANTY.\n" +
			"\n" +
			"This is free software, and you are welcome to redistribute\n" +
			"it under the terms of either the GPL 2.0 or GPL 3.0 license.\n" +
			"\n" +
			"For more details, please see: " + DtClusters.getHomepage()
		;
	}

	public static String getName() {
		return "DistanceClusters";
	}

	public static String getHomepage() {
		return "http://code.google.com/p/taxondna/";
	}
}
