
/*
 *
 *  CommandLine
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

package com.ggvaidya.TaxonDNA.DClusters.cli;

import java.io.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DClusters.*;

/**
 * This class handles command line requests, managing a stream of input
 * commands to process and display the results of clustering.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class CommandLine {

	/**
	 * This method reads the command line arguments (or stream of commands)
	 * provided, and executes them.
	 *
	 * Note that this can be used as the MainClass, if you are so inclined.
	 * It probably makes more sense to have another MainClass which can
	 * decide whether to use a textual interface or GUI, and then send it on
	 * to us if the former.
	 *
	 * @param args The command line arguments provided (or a stream of
	 *	commands).
	 */
	public static void main(String[] args) {
		System.out.println(DClusters.getCopyrightNotice());
		System.out.println();

		for(String arg: args) {
			/* Identify arguments */
			boolean flag_argument = false;

			if(arg.startsWith("--"))	{
				arg = arg.substring(2);
				flag_argument = true;
			}
			
			if(arg.startsWith("-"))	 {
				arg = arg.substring(1);
				flag_argument = true;
			}

			/* Process common arguments: */
			if(flag_argument) {
				/* Version */
				if(arg.equalsIgnoreCase("version") || arg.equalsIgnoreCase("v")) {
					System.out.print("This is " + DClusters.getName() + " version " + DClusters.getVersion());
					System.out.println(" based on TaxonDNA/" + Versions.getTaxonDNA() + ".");
					System.out.println();
				}
				/* Help */
				else if(arg.equalsIgnoreCase("h") || arg.equalsIgnoreCase("help")) {
					System.out.println(
						"The following command line arguments are accepted:\n" +
						"    --version, -v       Version information\n" +
						"    --help, -h          Help information"
					);
					System.out.println();
				} else {
					System.err.println("Unknown command: " + arg +", ignoring.");
				}
			} else {
				/* Probably a file name */
				File f = new File(arg);
				if(!f.canRead() || !f.isFile()) {
					System.err.println("File '" + f + "' could not be read");
				} else {
					System.err.println("Going to load file " + f + " now.");
				}
			}
		}
	}
}
