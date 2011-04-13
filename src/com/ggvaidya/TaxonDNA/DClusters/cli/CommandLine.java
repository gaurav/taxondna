
/*
 *
 *  CommandLine
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

package com.ggvaidya.TaxonDNA.DClusters.cli;

import java.util.*;
import java.io.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;
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

		// Variables.
		double		threshold =		0.03;
		Linkage		linkage =		new Linkages.SingleLinkage();

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
					// A readable file! Process it.
					try {
						processFile(f, threshold, linkage);
					} catch(DelayAbortedException e) {
						System.err.println("\nOperation cancelled by user.");
					}
				}
			}
		}
	}

	/**
	 * Process a file, reporting results to standard output/error.
	 * 
	 * @param f			A file to process.
	 * @param theshold	The threshold at which to cluster.
	 * @param linkage	The Linkage to use in clustering.
	 */
	private static void processFile(File f, double threshold, Linkage linkage) throws DelayAbortedException {
		SequenceList	sl;
		SpeciesDetails	species;

		/* Load this file */
		try {
			System.out.println("Loading: " + f);
			sl =			SequenceList.readFile(f, null);
			species =		sl.getSpeciesDetails(null);
		} catch(SequenceListException sle) {
			System.err.println("Could not load " + f + ": " + sle);
			return;

		} catch(DelayAbortedException e) {
			System.err.println("Unexpected exception: " + e);
			return;
			
		}

		System.out.println(
			sl.count() + " sequences for " + 
			species.count() + " species " +
			"loaded successfully."
		);
		System.out.println();

		/* File loaded! Set up a ClusterJob. */
		System.out.println(
				"Setting up a cluster job with " + linkage +
				", threshold of " + (threshold * 100) + "%; minimum overlap is set to " +
				Sequence.getMinOverlap() + "bp."
		);
		ClusterJob job = new ClusterJob(sl, linkage, threshold);

		System.out.print("Job ready, executing now:");
		job.execute(new CmdLineDelay());

		System.out.println("\n" + job.count() + " clusters identified.");

		List<Cluster> clusters = job.getClusters();
		Collections.sort(clusters);
		for(Cluster cluster: clusters) {
			System.out.println("\t" + cluster);
		}
	}

	/**
	 * A private command line delay to implement DelayCallback.
	 */
	private static class CmdLineDelay implements DelayCallback {
		public void begin() {
			System.out.print("\t");
		}

		public void delay(int done, int total) throws DelayAbortedException {
			double percentage = (((double)done/total) * 100);

			// Only display 0, 10, 20, ... 100%
			if(percentage % 10 == 0)
				System.out.print("[" + ((int)percentage) + "%] ");
		}

		public void end() {
			System.out.println("Finished.");
		}

		public void addWarning(String warning) {
			System.err.println("\nWARNING: " + warning + "\n");
		}
	}
}
