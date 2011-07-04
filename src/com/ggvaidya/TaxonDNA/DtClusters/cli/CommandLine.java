
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

package com.ggvaidya.TaxonDNA.DtClusters.cli;

import java.util.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.cluster.*;
import com.ggvaidya.TaxonDNA.DtClusters.*;
import com.ggvaidya.TaxonDNA.DtClusters.gui.*;

/**
 * This class handles command line requests, managing a stream of input
 * commands to process and display the results of clustering.
 * 
 * TODO: This class needs a *lot* of cleanup.
 *
 * @author Gaurav Vaidya <gaurav@ggvaidya.com>
 */
public class CommandLine {
	/** The list of files to process. */
	protected List<File>	filesToProcess =	new LinkedList<File>();
	protected boolean		start_gui =			true;
	
	public List<File>	filesToProcess()	{	return new LinkedList(filesToProcess);	}
	public boolean		startGUI()			{	return start_gui;	}
	
	/**
	 * Set up a CommandLine object with the arguments provided.
	 * 
	 * @param args The command-line arguments to use.
	 */
	public CommandLine(String[] args) {
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
					System.err.print("This is " + DtClusters.getName() + " version " + DtClusters.getVersion());
					System.err.println(" based on TaxonDNA/" + Versions.getTaxonDNA() + ".");
					System.err.println();
				}
				/* Help */
				else if(arg.equalsIgnoreCase("h") || arg.equalsIgnoreCase("help")) {
					System.err.println(
						"The following command line arguments are accepted:\n" +
						"    --version, -v       Version information\n" +
						"    --help, -h          Help information"
					);
					System.err.println();
				} else {
					System.err.println("Unknown command: " + arg +", ignoring.");
				}
			} else {
				/* Probably a file name */
				File f = new File(arg);
				if(!f.canRead() || !f.isFile()) {
					System.err.println("File '" + f + "' could not be read.");
				} else {
					// A readable file!
					filesToProcess.add(f);
				}
			}
		}
	}

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
	public void run() {
		System.err.println(DtClusters.getCopyrightNotice());
		System.err.println();

		// Variables.
		double		threshold =		0.03;
		Linkage		linkage =		new Linkages.SingleLinkage();
		
		if(filesToProcess.isEmpty()) {
			System.err.println("No files to process; quitting.");
			System.exit(0);
		}
		
		// Process all the input files.
		for(File f: filesToProcess) {
			try {
				processFile(f, threshold, linkage);
			} catch(DelayAbortedException e) {
				System.err.println("\nOperation cancelled by user.");
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
			System.err.println("Loading: " + f);
			sl =			SequenceList.readFile(f, null);
			species =		sl.getSpeciesDetails(null);
		} catch(SequenceListException sle) {
			System.err.println("Could not load " + f + ": " + sle);
			return;

		} catch(DelayAbortedException e) {
			System.err.println("Unexpected exception: " + e);
			return;
			
		}

		System.err.println(
			sl.count() + " sequences for " + 
			species.count() + " species " +
			"loaded successfully."
		);
		System.err.println();

		/* File loaded! Set up a ClusterJob.
		System.err.println(
				"Setting up a cluster job with " + linkage +
				", threshold of " + (threshold * 100) + "%; minimum overlap is set to " +
				Sequence.getMinOverlap() + "bp."
		);
		ClusterJob job = new ClusterJob(sl, linkage, threshold);

		System.err.print("Job ready, executing now:");
		job.execute(new CmdLineDelay());

		System.err.println("\n" + job.count() + " clusters identified.");

		List<Cluster> clusters = job.getClusters();
		Collections.sort(clusters);
		int x = 0;
		for(Cluster cluster: clusters) {
			x++;
			System.out.println("C" + x + ":\t" + cluster);
		}
		*/

		/* Print out the distance matrix. */
		//System.err.println("Writing out clusters vs sequences");
		// speciesVsClusters(clusters, x, sl);

		/*
		System.err.println("Testing countClustersSharedWith");
		testCountClustersSharedWith(sl);
		System.err.println("Testing done, processing time!\n");
		 *
		 */
		
		//System.err.println("Writing out cluster stability information:");
		//determineClusterStability(sl, linkage, 0.03, 0.10, 0.005);

		System.err.println("Doing the distance walk:");
		doTheDistanceWalk(sl, linkage, 0.03, 0.05);
	}

	private static void doTheDistanceWalk(SequenceList sl, Linkage linkage, double from, double to) {
		System.err.println(" Determining cluster information at " + from + "% (" + linkage + ", " + Sequence.getMinOverlap() + " bp minimum overlap)");

		ClusterJob job = new ClusterJob(sl, linkage, from);
		System.err.print("  Processing: ");
		try {
			job.execute(new CmdLineDelay());
		} catch(DelayAbortedException e) {
			System.err.println("\nAborted.");
			System.exit(0);
		}

		// Uh-oh UI!
		MainFrame mf = new MainFrame();
		JPanel panel = new JPanel();
		mf.add(panel);
		mf.pack();
		
		AgglomerateClusters ac = new AgglomerateClusters();
		panel.add(ac);
		ac.changeInitialState(job);
		mf.setVisible(true);

		System.err.println(" Walking from " + percentage(from) + "% to " + percentage(to) + "%");
		List<ClusterNode> walkToDistance = new LinkedList<ClusterNode>();
		try {
			walkToDistance = ac.agglomerateClusters(to);
		} catch (DelayAbortedException ex) {
			Logger.getLogger(CommandLine.class.getName()).log(Level.SEVERE, null, ex);
		}
		System.err.println(" Obtained " + walkToDistance.size() + " clusters at " + percentage(to) + "%");

		//System.err.println("Results follow.");
		//ClusterNode.visualizeFrame(walkToDistance);
		//System.err.println("Results completed.");
	}

	private static void testCountClustersSharedWith(SequenceList sl) {
		System.err.println("# Testing that countClustersSharedWith works");
		System.err.println("1..13");

		Cluster c1_20 = new Cluster(null);
		Cluster c2_20 = new Cluster(null);
		Cluster c3_5 = new Cluster(null);
		Cluster c4_5 = new Cluster(null);
		Cluster c5_10 = new Cluster(null);
		Cluster c6_10 = new Cluster(null);
		Cluster c7_7 = new Cluster(null);

		// So we should have:
		//	c1_20, c2_20:	1-20
		//	c3_5, c4_5:		1-5
		//	c5_10, c6_10:	1-10
		//	c7_7:			1-7

		for(int x = 1; x <= 20; x++) {
			Sequence seq = (Sequence)	sl.get(x - 1);
			if(x >= 1 && x <= 20)		{ c1_20.add(seq);	c2_20.add(seq);	}
			if(x >= 1 && x <= 5)		{ c3_5.add(seq);	c4_5.add(seq);	}
			if(x >= 1 && x <= 10)		{ c5_10.add(seq);	c6_10.add(seq);	}
			if(x >= 1 && x <= 7)		c7_7.add(seq);
		}

		// Check counts
		is_n(c1_20.count(),	20,		"Checking c1_20 sequence count");
		is_n(c2_20.count(),	20,		"Checking c2_20 sequence count");
		is_n(c3_5.count(),	5,		"Checking c3_5 sequence count");
		is_n(c4_5.count(),	5,		"Checking c4_5 sequence count");
		is_n(c5_10.count(),	10,		"Checking c5_10 sequence count");
		is_n(c6_10.count(),	10,		"Checking c6_10 sequence count");
		is_n(c7_7.count(),	7,		"Checking c7_7 sequence count");

		// Generate some ClusterJobs.
		ClusterJob job_A = new ClusterJob(null, null, 0);
		job_A.createFakeResults();
		job_A.getResults().add(c1_20);
		job_A.getResults().add(c2_20);
		job_A.getResults().add(c3_5);

		ClusterJob job_B = new ClusterJob(null, null, 0);
		job_B.createFakeResults();
		job_B.getResults().add(c1_20);
		job_B.getResults().add(c2_20);
		job_B.getResults().add(c5_10);

		// Basic check.
		is_n(job_B.countClustersSharedWith(job_A), 2, "Comparing (c1, c2, c3) with (c1, c2, c5)");

		// Note that it doesn't care about cluster identity, only contents!
		job_B.getResults().remove(c2_20);
		is_n(job_B.countClustersSharedWith(job_A), 2, "Comparing (c1, c2, c3) with (c1, c2, c6)");

		// Successfully handles 0 and MAX
		job_A.getResults().clear();
		job_A.getResults().add(c1_20);
		job_A.getResults().add(c3_5);
		job_A.getResults().add(c5_10);

		job_B.getResults().clear();
		job_B.getResults().add(c7_7);

		is_n(job_A.countClustersSharedWith(job_B), 0, "Comparing (c1, c3, c5) with (c7)");

		// Any job measured against itself matches perfectly.
		is_n(job_A.countClustersSharedWith(job_A), 3, "Comparing (c1, c3, c5) with (c1, c3, c5)");
		is_n(job_B.countClustersSharedWith(job_B), 1, "Comparing (c7) with (c7)");

		job_B.getResults().clear();
		job_B.getResults().add(c2_20);
		job_B.getResults().add(c4_5);
		job_B.getResults().add(c6_10);

		is_n(job_A.countClustersSharedWith(job_B), 3, "Comparing (c1, c3, c5) with (c2, c4, c6), which are identical");

		
	}

	private static void is_n(int x, int y, String message) {
		if(x == y) {
			System.err.println("ok - " + message);
		} else {
			System.err.println("not ok - " + message + " (got " + x + ", expected " + y + ")");
			System.exit(1);
		}
	}

	private static void speciesVsClusters(List<Cluster> clusters, int x, SequenceList sl) throws DelayAbortedException {
		System.out.print("Cluster\tTotal sequences");
		x = 0;
		for (Cluster inner : clusters) {
			x++;
			System.out.print("\t'" + inner + "'");
			//System.out.print("\tC" + x);
		}
		System.out.println();
		x = 0;
		Map<String, SpeciesDetail> sd = sl.getSpeciesDetails(null).getDetails();
		List<String> species_names = new ArrayList(sd.keySet());
		Collections.sort(species_names);
		for (String species_name : species_names) {
			x++; // Start with 1.
			System.out.print("'" + species_name + "'\t" + sd.get(species_name).getSequencesCount());
			//System.out.print("C"+x);
			for (Cluster cluster : clusters) {
				Map<String, SpeciesDetail> cluster_details = cluster.getSpeciesDetails(null).getDetails();
				System.out.print("\t");
				if (cluster_details.containsKey(species_name)) {
					System.out.print(cluster_details.get(species_name).getSequencesCount());
				} else {
					System.out.print("Absent");
				}
			}
			System.out.println();
		}
	}

	/**
	 * Checks for cluster stability: we start at 'from' and calculate clusters for it.
	 * We then calculate clusters at (from + 1 * step), and see how many clusters are
	 * identical between the two cluster jobs. Then we do this for (from + 2 * step),
	 * and so on and so forth until we hit 'to'.
	 *
	 * @param sl		List of sequences to work on.
	 * @param from		Pairwise distance to start from.
	 * @param to		Pairwise distance to end at.
	 * @param step		How much to increase the threshold at each iteration.
	 */
	private static void determineClusterStability(SequenceList sl, Linkage linkage, double from, double to, double step) {
		System.err.println("Calculating the list of clusters at " + percentage(from) + "% (" + linkage + ", " + Sequence.getMinOverlap() + " minimum overlap)");
	
		ClusterJob original = new ClusterJob(sl, linkage, percentage(from)/100);
		System.err.print(" Executing job: ");
		try {
			original.execute(new CmdLineDelay());
		} catch (DelayAbortedException ex) {
			System.err.println("User requested termination.");
			System.exit(1);
		}

		System.err.println(original.count() + " clusters obtained.");

		// Get ready to output the results.
		System.out.println(
			"# Cluster stability between " +
			percentage(from) + "% and " + percentage(to) + "% on " +
			new Date() + " upon " + sl.count() + " sequences"
		);
		System.out.println("Threshold\tClusters\tShared clusters");
		System.out.println(percentage(from) + "\t" + original.count() + "\t" + original.count());

		for(double x = from + step; x <= to; x += step) {
			System.err.println("Preparing to cluster at " + percentage(x) + "% (" + linkage + ", " + Sequence.getMinOverlap() + " minimum overlap)");

			ClusterJob current = new ClusterJob(sl, linkage, percentage(x)/100);

			System.err.print(" Executing: ");
			try {
				current.execute(new CmdLineDelay());
			} catch (DelayAbortedException ex) {
				System.err.println("User requested termination.");
				System.exit(1);
			}

			int common_clusters = original.countClustersSharedWith(current);

			System.out.println(percentage(x) + "\t" + current.count() + "\t" + common_clusters);
			System.err.println(" Result for " + percentage(x) + "%: " + current.count() + " clusters obtained, including " + common_clusters + " shared with cluster at " + percentage(from) + "%");
		}

		// This is basically tending to '100%' threshold, at which point we'd have 1 cluster with everything.
	}

	private static double percentage(double d) {
		return com.ggvaidya.TaxonDNA.DNA.Settings.percentage(d, 1);
	}

	/**
	 * A private command line delay to implement DelayCallback.
	 */
	private static class CmdLineDelay implements DelayCallback {
		public void begin() {
			System.err.print("\t");
		}

		public void delay(int done, int total) throws DelayAbortedException {
			double percentage = (((double)done/total) * 100);

			// Only display 0, 10, 20, ... 100%
			if(percentage % 10 == 0)
				System.err.print("[" + ((int)percentage) + "%] ");
		}

		public void end() {
			System.err.println("Finished.");
		}

		public void addWarning(String warning) {
			System.err.println("\nWARNING: " + warning + "\n");
		}
	}
}
