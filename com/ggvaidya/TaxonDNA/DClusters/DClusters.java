
/*
 *
 *  DClusters
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

package com.ggvaidya.TaxonDNA.DClusters;

import com.ggvaidya.TaxonDNA.DClusters.cli.*;

/**
 * The main class of the DClusters menu. This checks for command-line
 * instructions, ultimately handing control over to MainFrame.
 *
 * @author Gaurav Vaidya
 */
public class DClusters {
	
	/**
	 * Kicks off this program.
	 * 
	 * @param args Command line arguments.
	 */
	public static void main(String[] args) {
		// We don't have a GUI yet!
		CommandLine.main(args);
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
			DClusters.getName() + " version " + DClusters.getVersion() + "\n" +
			"Copyright (C) 2010 Gaurav Vaidya\n" +
			DClusters.getName() + " comes with ABSOLUTELY NO WARRANTY.\n" +
			"\n" +
			"This is free software, and you are welcome to redistribute\n" +
			"it under the terms of either the GPL 2.0 or GPL 3.0 license.\n" +
			"\n" +
			"For more details, please see: " + DClusters.getHomepage()
		;
	}

	public static String getName() {
		return "DistanceClusters";
	}

	public static String getHomepage() {
		return "http://code.google.com/p/taxondna/";
	}
}
