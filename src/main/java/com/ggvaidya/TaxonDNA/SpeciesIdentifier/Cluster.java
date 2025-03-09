/** A UIExtension which generates all possible clusters for a particular dataset. */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005, 2007-08, 2010

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

package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.UI.*;
import java.awt.*;
import java.awt.datatransfer.*; // for clipboard
import java.awt.event.*;
import java.util.*;

public class Cluster extends Panel implements UIExtension, ActionListener, ItemListener, Runnable {
    private boolean flag_skipIndivEntries = false;
    private Checkbox check_skipIndivEntries =
            new Checkbox("Generate individual information on every cluster");

    private SpeciesIdentifier seqId;
    private SequenceList set = null;

    private Button btn_MakeClusters = new Button(" Make clusters now! ");
    private TextField text_threshold = new TextField("03.000");
    private java.awt.List list_clusters = new java.awt.List();
    private TextArea text_main = new TextArea();

    private double max_pairwise = 0.03;

    private Vector clusters;

    private Button btn_Copy = new Button("Copy to Clipboard");

    private SequenceList list_consensuses_lumped;
    private SequenceList list_consensuses_perfect;
    private SequenceList list_consensuses_split;

    private static final int CHAR_LIMIT_ON_CLUSTER_NAMES =
            30; // err ... hard to explain. go look it up :p

    // helper function
    private double percentage(double x, double y) {
        return com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(x, y);
    }

    public Cluster(SpeciesIdentifier view) {
        super();

        seqId = view;

        // create the panel
        setLayout(new BorderLayout());

        Panel settings = new Panel();
        RightLayout rl = new RightLayout(settings);
        settings.setLayout(rl);

        rl.add(new Label("Please select the threshold at which to cluster:"), RightLayout.NONE);
        rl.add(text_threshold, RightLayout.BESIDE);
        rl.add(new Label("%"), RightLayout.BESIDE);

        rl.add(check_skipIndivEntries, RightLayout.NEXTLINE);

        btn_MakeClusters.addActionListener(this);
        rl.add(btn_MakeClusters, RightLayout.BESIDE);
        add(settings, BorderLayout.NORTH);

        Panel main = new Panel();
        main.setLayout(new BorderLayout());
        list_clusters.add("                                  ");
        list_clusters.addItemListener(this);
        main.add(list_clusters);
        text_main.setEditable(false);
        main.add(text_main, BorderLayout.EAST);
        add(main);

        Panel buttons = new Panel();
        buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
        btn_Copy.addActionListener(this);
        buttons.add(btn_Copy);

        add(buttons, BorderLayout.SOUTH);
    }

    /* Item listener, to display the stuffs */
    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            selectItem(((Integer) e.getItem()).intValue());
        }
    }

    /*
     * Somebody selected something from our list; either the "summary" (i == 0) or one of the selected
     * clusters.
     */
    private String item_strings[];

    public void selectItem(int i) {
        if (i < item_strings.length) {
            text_main.setText(item_strings[i]);
        }
    }

    /*
     * Just don't ask.
     */
    private Sequence makeConsensusOfBin(Vector bin) throws SequenceException {
        // Step 1. Go through all sequences, construct a consensus.
        Iterator i_seqs = bin.iterator();
        Sequence consensus = null;
        while (i_seqs.hasNext()) {
            Sequence seq = (Sequence) i_seqs.next();

            if (consensus == null) consensus = seq;
            else {
                consensus = consensus.getConsensus(seq);
                // might throw exception
            }
        }

        return consensus;
    }

    public void writeupItemStrings(DelayCallback delay) throws DelayAbortedException {
        int delaySteps = clusters.size() + clusters.size();

        System.err.println("1-G");

        if (set != null)
            delaySteps += set.count() * 2; // try to get as accurate a guess as possible

        item_strings = new String[1 + clusters.size()];

        if (delay != null) delay.begin();

        System.err.println("1-H");

        // summary
        StringBuffer str_final = new StringBuffer("Summary of results\n\n");
        int no_threshold_violations = 0;
        int no_clusters_with_one_species = 0;
        int largest_no_of_species_in_a_cluster = 0;
        double largest_pairwise_distance_observed = 0;
        SpeciesSummary speciesSummary = null;
        int no_of_clusters_with_all_sequences_for_a_species = 0;

        SpeciesDetails sd = null;
        try {
            sd =
                    set.getSpeciesDetails(
                            ProgressDialog.create(
                                    seqId.getFrame(),
                                    "Please wait, calculating the species details ...",
                                    "I'm calculating the species details for this sequence set."
                                            + " This might take a while. Sorry!"));
        } catch (DelayAbortedException e) {
            seqId.unlockSequenceList();
            return;
        }

        System.err.println("1-I");

        StringBuffer str = new StringBuffer();
        Hashtable hash_species = new Hashtable();

        str.append(
                "Cluster\tNo of sequences\tNo of species\tMax pairwise distance\tPercentage of"
                        + " valid comparisons over "
                        + max_pairwise
                        + "\n");
        int delay_count = 0;
        for (int x = 0; x < clusters.size(); x++) {
            Vector bin = (Vector) clusters.get(x);
            Iterator i1 = bin.iterator();

            double largest_pairwise = 0;
            int valid_comparisons = 0;
            int valid_comparisons_over = 0;
            HashMap<String, Integer> hash_species_counts_in_this_cluster =
                    new HashMap<String, Integer>();
            boolean bool_containsAllSequencesOfOneSpecies = false;

            str.append((x + 1) + "\t");

            while (i1.hasNext()) {
                Sequence seq = (Sequence) i1.next();

                try {
                    if (delay != null) delay.delay(delay_count, delaySteps);
                    delay_count++;
                } catch (DelayAbortedException e) {
                    item_strings[0] = "Clustering incomplete; please recluster.";
                    return;
                }

                // What if this sequence doesn't HAVE a species name?
                String spName = seq.getSpeciesName();
                if (spName == null) spName = "{" + seq.getFullName() + "}";

                if (hash_species_counts_in_this_cluster.get(spName) == null) {
                    hash_species_counts_in_this_cluster.put(spName, new Integer(1));

                    if (hash_species.get(spName) == null) {
                        hash_species.put(spName, new Integer(1));
                    } else {
                        hash_species.put(
                                spName,
                                new Integer(
                                        ((Integer) hash_species.get(seq.getSpeciesName()))
                                                        .intValue()
                                                + 1));
                    }

                } else {
                    hash_species_counts_in_this_cluster.put(
                            spName,
                            new Integer(
                                    ((Integer) hash_species.get(seq.getSpeciesName())).intValue()
                                            + 1));
                }

                Iterator i2 = bin.iterator();
                while (i2.hasNext()) {
                    Sequence seq2 = (Sequence) i2.next();

                    if (seq2.getFullName().equals(seq.getFullName())) {
                        continue;
                    }

                    valid_comparisons++;

                    double pairwise = seq.getPairwise(seq2);

                    if (pairwise < 0) continue;

                    if (pairwise > largest_pairwise) {
                        largest_pairwise = pairwise;
                    }

                    if (pairwise > max_pairwise) {
                        valid_comparisons_over++;
                    }
                }
            }

            double percentage = 0;
            if (valid_comparisons != 0) {
                percentage = percentage(valid_comparisons_over, valid_comparisons);
            }

            if (largest_pairwise > max_pairwise) no_threshold_violations++;

            if (largest_pairwise > largest_pairwise_distance_observed)
                largest_pairwise_distance_observed = largest_pairwise;

            if (hash_species_counts_in_this_cluster.keySet().size() == 1) {
                no_clusters_with_one_species++;

                // now, compare no of seq found for
                // this one species with the no of
                // seq available for this species
                // in the database.

                String speciesName =
                        ((String) (hash_species_counts_in_this_cluster.keySet().toArray())[0]);
                // obviously, we can't do this for unknown species
                if (sd.getSpeciesDetailsByName(speciesName) != null
                        && sd.getSpeciesDetailsByName(speciesName).getSequencesCount() == bin.size()
                // Used to be:
                // sd.getSpeciesDetailsByName(speciesName).getSequencesWithValidConspecificsCount()
                // ==
                // bin.size()
                // but obviously makes no sense at all. Thanks, Michael!
                ) {
                    bool_containsAllSequencesOfOneSpecies = true;
                    no_of_clusters_with_all_sequences_for_a_species++;
                }
            }

            if (hash_species_counts_in_this_cluster.keySet().size()
                    > largest_no_of_species_in_a_cluster)
                largest_no_of_species_in_a_cluster =
                        hash_species_counts_in_this_cluster.keySet().size();

            String cluster_status = "";
            SequenceList list_of_consensus = null;
            String cluster_type = null;
            String species_name = null;

            if (hash_species_counts_in_this_cluster.keySet().size() == 1) {
                // A single species in this cluster!

                // it's either:
                // 1.	'perfect': all seqs of 1 sp, and EVERY seq of that sp
                // 2.	'technically split': all seqs of 1 sp, some seqs of that
                //	species in another cluster, overlap between clusters is
                //	< min_overlap
                // 3.	'biologically split': all seqs of 1 sp, some seqs of that
                //	species in another cluster, overlap between clusters is
                //	>= min_overlap
                // 4. 	'technically AND biologically split' all seqs of 1 sp, some
                //	seqs of that species in another cluster with < min_overlap
                // 	to this cluster, some in another cluster with >= min_overlap.
                // 5.	'no species names': none of the sequences in this cluster
                //	have a species name.
                //
                if (bool_containsAllSequencesOfOneSpecies) {
                    cluster_status = "Perfect\t(contains all sequences of one species)";
                    list_of_consensus = list_consensuses_perfect;
                    cluster_type = "Perfect cluster single species";

                    species_name =
                            ((String) (hash_species_counts_in_this_cluster.keySet().toArray())[0]);
                } else {
                    species_name =
                            ((String) (hash_species_counts_in_this_cluster.keySet().toArray())[0]);
                    // so ... now we need to find other clusters containing sp_Name.
                    // which is pretty hard, considering. sigh.
                    cluster_status = "Split only"; // One way or another
                    list_of_consensus = list_consensuses_split;
                    cluster_type = "Split cluster single species";
                }
            } else {
                // More than one species in this cluster.

                boolean any_species_has_sequences_missing = false;

                for (String speciesName : hash_species_counts_in_this_cluster.keySet()) {
                    SpeciesDetail sdet = sd.getSpeciesDetailsByName(speciesName);

                    int count_speciesName_sequences_in_this_cluster =
                            hash_species_counts_in_this_cluster.get(speciesName).intValue();
                    int count_speciesName_sequences_in_total = sdet.getSequencesCount();

                    if (count_speciesName_sequences_in_this_cluster
                            != count_speciesName_sequences_in_total) {
                        any_species_has_sequences_missing = true;
                        break;
                    }
                }

                if (!any_species_has_sequences_missing) {
                    // All the sequences for all the species in this cluster
                    // are in this cluster.
                    cluster_status = "Lumped only\t(contains multiple species)";
                    list_of_consensus = list_consensuses_lumped;
                    cluster_type = "Lumped cluster multiple species";
                    species_name = "Multiple species";
                } else {
                    // Atleast one sequence for atleast one species in this
                    // cluster is outside this cluster.
                    cluster_status = "Lumped/Split\t(contains multiple species)";
                    list_of_consensus = list_consensuses_lumped;
                    cluster_type = "Lumped cluster multiple species";
                    species_name = "Multiple species";
                }
            }

            System.err.println("1-N");

            Sequence consensus;
            try {
                consensus = makeConsensusOfBin(bin);
            } catch (SequenceException e) {
                item_strings[0] = "Clustering incomplete; consensus failed: " + e;
                return;
            }

            String consensusName =
                    cluster_type.replace(' ', '~')
                            + " "
                            + species_name
                            + " (cluster #"
                            + (x + 1)
                            + ")";
            try {
                list_of_consensus.add(new Sequence(consensusName, consensus.getSequence()));
            } catch (SequenceException e) {
                item_strings[0] =
                        "Clustering incomplete; please recluster: could not process split consensus"
                                + " sequence for "
                                + consensusName;
                return;
            }

            str.append(
                    bin.size()
                            + "\t"
                            + hash_species_counts_in_this_cluster.keySet().size()
                            + "\t"
                            + percentage(largest_pairwise, 1)
                            + "%\t"
                            + (percentage)
                            + "%\t"
                            + cluster_status
                            + "\n");
        }

        str.append(
                "\n\n"
                    + "Summary of species\n\n"
                    + "SPECIES\tSEQUENCES\tFOUND IN HOW MANY CLUSTERS?\tFOUND WITH HOW MANY OTHER"
                    + " SPECIES?\n");

        Enumeration enu = hash_species.keys();
        while (enu.hasMoreElements()) {
            String name = (String) enu.nextElement();
            int count_sequences = 0;
            int species_found_with = 0;

            Iterator i = set.iterator();
            while (i.hasNext()) {
                Sequence seq = (Sequence) i.next();

                if (seq.getSpeciesName() != null && seq.getSpeciesName().equals(name))
                    count_sequences++;
            }

            int count_extremes = 0;
            Vector extremes_index = new Vector();
            Vector extremes_left = new Vector();
            Vector extremes_right = new Vector();

            int x = 1;
            Iterator iter1 = clusters.iterator();
            while (iter1.hasNext()) {
                Vector bin = (Vector) iter1.next();

                int extreme_left = -1; // for this particular bin
                int extreme_right = -1;

                int contains_species = 0;
                Iterator iter2 = bin.iterator();
                boolean containsThisOne = false;
                Hashtable species = new Hashtable();
                while (iter2.hasNext()) {
                    Sequence seq = (Sequence) iter2.next();
                    String speciesName = seq.getSpeciesName();

                    // now, back to normal processing ...
                    if (speciesName == null) continue;

                    if (speciesName.equals(name)) {
                        containsThisOne = true;

                        // also, we need to figure out the 'extreme' values on this particular
                        // 'bin'.
                        if (extreme_left == -1
                                || (seq.getFirstRealCharacter() != -1
                                        && seq.getFirstRealCharacter() < extreme_left))
                            extreme_left = seq.getFirstRealCharacter();

                        if (extreme_right == -1
                                || (seq.getLastRealCharacter() != -1
                                        && seq.getLastRealCharacter() > extreme_right))
                            extreme_right = seq.getLastRealCharacter();

                        //						System.err.println("extremes: " + extreme_left + ", " +
                        // extreme_right);

                    } else if (species.get(speciesName) == null) {
                        species.put(speciesName, new Integer(1));
                        contains_species++;
                    }
                }

                if (containsThisOne) {
                    containsThisOne = false;
                    species_found_with += contains_species;
                    // TODO: insert code to determine which species are split
                    // and to calculate the overlap (extreme points, NOT full
                    // consensus). Species which are found in multiple clusters
                    // which have NO significant overlap need to be highlighted.
                    //
                    // now we know that species 'name' is found in cluster 'bin'.
                    // So we need to save extreme points of cluster 'bin'.
                    if (extreme_left != -1 && extreme_right != -1) {
                        extremes_index.add(new Integer(x));
                        extremes_left.add(new Integer(extreme_left));
                        extremes_right.add(new Integer(extreme_right));
                        count_extremes++;
                    }
                }

                x++;
            }

            System.err.println("1-P");

            str.append(
                    name
                            + "\t"
                            + count_sequences
                            + "\t"
                            + hash_species.get(name)
                            + "\t"
                            + species_found_with
                            + "\n");

            for (int index_outer = 0; index_outer < count_extremes; index_outer++) {
                for (int index_inner = 0; index_inner < count_extremes; index_inner++) {
                    if (index_inner == index_outer) continue;

                    int left_in = ((Integer) extremes_left.get(index_inner)).intValue();
                    int right_in = ((Integer) extremes_right.get(index_inner)).intValue();

                    int left_out = ((Integer) extremes_left.get(index_outer)).intValue();
                    int right_out = ((Integer) extremes_right.get(index_outer)).intValue();
                    int minOverlap = Sequence.getMinOverlap();

                    int leftmost_right = (right_out <= right_in ? right_out : right_in);
                    int rightmost_left = (left_out <= left_in ? left_out : left_in);
                    int overlap = rightmost_left - leftmost_right;

                    if (overlap < 0) overlap = 0;

                    if (overlap == 0 || overlap < minOverlap)
                        str.append(
                                "\tClusters \t"
                                        + extremes_index.get(index_inner)
                                        + "\t and \t"
                                        + extremes_index.get(index_outer)
                                        + "\t have an overlap of \t"
                                        + overlap
                                        + "\t bp.\n");
                }
            }
        }

        // add stuff to str_final
        str_final.append("Clustering at:\t" + percentage(max_pairwise, 1) + "%\n");
        str_final.append("Number of clusters:\t" + clusters.size() + "\n");
        str_final.append(
                "Number of clusters with threshold violations:\t"
                        + no_threshold_violations
                        + " ("
                        + percentage(no_threshold_violations, clusters.size())
                        + "%)\n");
        str_final.append(
                "Largest pairwise distance:\t"
                        + percentage(largest_pairwise_distance_observed, 1)
                        + "%\n");
        str_final.append("Profiles with only one species:\t" + no_clusters_with_one_species + "\n");
        str_final.append(
                "Profiles corresponding to traditional taxonomy:\t"
                        + no_of_clusters_with_all_sequences_for_a_species
                        + "\n");
        str_final.append(
                "Largest number of species in a cluster:\t"
                        + largest_no_of_species_in_a_cluster
                        + "\n");

        str_final.append("\n");
        str_final.append(str);

        item_strings[0] = str_final.toString();

        delay_count++;
        for (int i = 1; !flag_skipIndivEntries && i <= clusters.size(); i++) {
            // information on that particular clusters
            Vector bin = (Vector) clusters.get(i - 1); //
            str = new StringBuffer();
            Iterator i1;
            Hashtable species = new Hashtable();

            str.append("Cluster " + (i) + " consists of " + bin.size() + " sequences ");
            // count species?
            int nSpecies = 0;
            int nValidComparisons = 0;
            int nComparisonsOverLimit = 0;
            StringBuffer first_line = new StringBuffer();
            StringBuffer pairwise_table = new StringBuffer();
            i1 = bin.iterator();
            while (i1.hasNext()) {
                Sequence seq1 = (Sequence) i1.next();

                try {
                    if (delay != null) delay.delay(delay_count, delaySteps);
                } catch (DelayAbortedException e) {
                    item_strings[0] = "Clustering incomplete; please recluster.";
                    return;
                }

                if (seq1.getSpeciesName() != null && species.get(seq1.getSpeciesName()) == null) {
                    species.put(seq1.getSpeciesName(), new Integer(1));
                    nSpecies++;
                }

                first_line.append(seq1.getFullName() + "\t");

                pairwise_table.append(seq1.getFullName());

                Iterator i2 = bin.iterator();
                while (i2.hasNext()) {
                    double pairwise = 0;
                    Sequence seq2 = (Sequence) i2.next();

                    pairwise = seq2.getPairwise(seq1);
                    if (seq2.getPairwise(seq1) < 0) {
                        pairwise_table.append("\t(inadequate overlap)");
                    } else {
                        pairwise_table.append("\t" + percentage(pairwise, 1) + "%");

                        if (!seq2.equals(seq1)) nValidComparisons++;

                        if (pairwise > max_pairwise) nComparisonsOverLimit++;
                    }
                }
                pairwise_table.append("\n");
            }

            str.append(
                    "with "
                            + nComparisonsOverLimit
                            + " valid comparisons ("
                            + percentage(nComparisonsOverLimit, nValidComparisons)
                            + "%) over "
                            + percentage(max_pairwise, 1)
                            + "%\n");

            item_strings[i] =
                    (str.toString()
                            + "\n\t"
                            + first_line.toString()
                            + "\n"
                            + pairwise_table.toString());
        }

        /*
         * I honestly have no idea why, but allowing delay.end() to
         * be called here causes the program to hang. So.
         *
        if(delay != null)
        	delay.end();
         *
         */
    }

    /* Data changed: in our case, SequenceSet changed */
    public void dataChanged() {
        text_threshold.setText("3");
        list_clusters.removeAll();
        text_main.setText("");

        // btn_MakeClusters.setLabel("THE DATA HAS CHANGED SINCE THE LAST CLUSTERING. Recluster?");
    }

    /* Creates the actual Panel */
    public Panel getPanel() {
        return this;
    }

    public boolean addCommandsToMenu(Menu menu) {
        return false;
    }

    // action listener
    public void actionPerformed(ActionEvent evt) {
        String cmd = evt.getActionCommand();

        if (cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
            try {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(text_main.getText());

                clip.setContents(selection, selection);
            } catch (IllegalStateException e) {
                btn_Copy.setLabel("Oops, try again?");
            }
            btn_Copy.setLabel("Copy to Clipboard");
        }

        if (evt.getSource().equals(btn_MakeClusters)) {
            flag_skipIndivEntries = !check_skipIndivEntries.getState();

            try {
                Double d = new Double(text_threshold.getText());
                max_pairwise = d.doubleValue() / 100;
            } catch (NumberFormatException e) {
                list_clusters.removeAll();
                list_clusters.add("Could not process");
                text_main.setText("Please enter a valid number for the threshold");
                return;
            }

            new Thread(this, "Cluster").start();
        }
    }

    /**
     * Generate the clusters based on the provided parameters. Note that these clusters are defined
     * PURELY based on the pairwise distance between sequences -- it is NOT in any way hierarchical
     * or representative of phylogenetic relationships.
     *
     * <p>The algorithm we use can be simplified to a maximum pairwise threshold of `max_pairwise`:
     * 1. Start with an empty vector of clusters, `clusters`. 2. For every sequence `seq` loaded
     * into SpeciesIdentifier, assign it to the first cluster that includes a sequence `compare`
     * that has a valid pairwise distance less than or equal to the threshold `max_pairwise`. - If
     * there are multiple clusters that meet this property, merge all of them into the first
     * matching cluster.
     *
     * <p>A more detailed description of the algorithm for a maximum pairwise threshold of
     * `max_pairwise`: 1. Make an empty list of clusters called `clusters`. 2. For every sequence
     * `seq` loaded into SpeciesIdentifier: 1. Create an uninitialized vector called
     * `accumulating_cluster`. 2. For every vector `current_cluster`, which is a cluster inside
     * `clusters` at index `cluster_index`: 1. For every sequence `compare` inside cluster
     * `current_cluster`: 1. If sequence `seq` and `compare` do not have a valid pairwise distance
     * (e.g. if they have insufficient overlap, or if one of the sequences in invalid), then
     * continue on to the next sequence. 2. If sequence `seq` and `compare` have a pairwise distance
     * less than or equal to `max_pairwise`: 1. If `accumulating_cluster` has not yet been set: 1.
     * Add `seq` to `current_cluster`. 2. Set `accumulating_cluster` to `current_cluster`. 3. Skip
     * the remaining sequences in this cluster (by setting `current_sequence` to 0) and continue on
     * to the next cluster as `current_cluster`. 2. Else if `accumulating_cluster` has been set: 1.
     * Add every sequence in `current_cluster` into `accumulating_cluster`. 2. Delete
     * `current_cluster` from the vector `clusters`. 3. Skip the remaining sequences in this cluster
     * (by setting `current_sequence` to 0) and continue on to the next cluster as
     * `current_cluster`. 3. If by this point the vector `accumulating_cluster` has not been set,
     * that means that none of the clusters in `clusters` is within the pairwise distance threshold
     * of `seq`. 1. Create a new cluster for this sequence and add it to the end of `clusters`. 2.
     * Continue on to the next sequence as `seq`.
     *
     * <p>This is not a very elegant algorithm, but I was only an undergraduate biology student at
     * the time.
     */
    public void run() {
        set = seqId.lockSequenceList();

        if (set == null) {
            seqId.unlockSequenceList();

            text_main.setText("No sequences loaded!");

            return;
        }

        System.err.println("1-A");

        ProgressDialog pb =
                ProgressDialog.create(
                        seqId.getFrame(),
                        "Clustering sequences at " + (max_pairwise * 100) + "% ...",
                        "All your sequences are being clustered, please wait ...",
                        0);

        pb.begin();

        text_main.setText("");

        clusters = new Vector();
        list_consensuses_lumped = new SequenceList();
        list_consensuses_perfect = new SequenceList();
        list_consensuses_split = new SequenceList();

        if (set != null) {
            Iterator iter = set.iterator();
            int c = 0;
            while (iter.hasNext()) {
                // Go to the next sequence in our sequence list.
                Sequence seq = (Sequence) iter.next();

                // As we go through the clusters, we'll designate the first one within the
                // max_pairwise threshold as
                // `accumulating_cluster` -- if we find others, we'll merge them into this cluster
                // by (1) adding all
                // their sequences to `accumulating_cluster`, and then (2) deleting them from
                // `clusters`.
                Vector accumulating_cluster = null;

                // Let the ProgressDialog know about our progress.
                try {
                    pb.delay(c, set.count());
                } catch (DelayAbortedException e) {
                    seqId.unlockSequenceList();
                    return;
                }

                c++; // only used to drive the pb.delay

                // Iterate through all the clusters in `clusters`.
                int cluster_index = clusters.size();
                while (cluster_index > 0) {
                    cluster_index--;

                    // This is the current cluster in `clusters` that we're iterating through.
                    Vector current_cluster = (Vector) clusters.get(cluster_index);

                    // Go through every sequence in `current_cluster`. Note that we iterate
                    // backwards through
                    // this list. This allows us to break out of it very easily by setting
                    // current_sequence = 0.
                    // Why not just use `break`? You'll have to ask 2005 me if you can find me, but
                    // probably there
                    // was another loop in here at some point.
                    int current_sequence = current_cluster.size();
                    while (current_sequence > 0) {
                        current_sequence--;

                        Sequence compare = (Sequence) current_cluster.get(current_sequence);

                        // Ignore this comparison if there isn't a valid pairwise difference (e.g.
                        // if there is insufficient
                        // overlap, or one of the sequences is invalid).
                        if (seq.getPairwise(compare) < 0) continue;

                        // If `compare` is within the max_pairwise threshold of `seq`:
                        if (seq.getPairwise(compare) <= max_pairwise) {
                            if (accumulating_cluster == null) {
                                // This is the first cluster we have come across that is within the
                                // pairwise distance
                                // threshold. We therefore designate it as the accumulating_cluster
                                // and add the current
                                // sequence to it.
                                current_cluster.add(seq);
                                accumulating_cluster = current_cluster;

                                // Break to the next cluster.
                                current_sequence = 0;
                            } else {
                                // We have already found an accumulating cluster for this sequence,
                                // but now we've
                                // found another that is within the threshold! Since this sequence
                                // connects these
                                // two clusters together, we will copy all of its sequences into the
                                // accumulating_cluster
                                // and then delete the `current_cluster` from `clusters`.
                                Iterator i = current_cluster.iterator();

                                while (i.hasNext()) {
                                    accumulating_cluster.add(i.next());
                                }

                                clusters.remove(current_cluster);

                                // Break to the next cluster.
                                current_sequence = 0; // i.e. break out of inner loop
                            }
                        }
                    }
                }

                if (accumulating_cluster == null) {
                    // If we've reached here, then `seq` was not within the pairwise distance
                    // threshold of any of the
                    // clusters we have at present. We therefore create a new cluster just for it,
                    // and add it to the
                    // end of `clusters`.
                    Vector vec = new Vector();
                    vec.add(seq);
                    clusters.add(vec);
                }
            }

            // now all the sequences have been clustered
            list_clusters.removeAll();
            list_clusters.add("Summary");

            System.err.println("1-B");

            Iterator i = clusters.iterator();
            int x = 0;
            while (i.hasNext()) {
                try {
                    pb.delay(x, clusters.size());
                } catch (DelayAbortedException e) {
                    seqId.unlockSequenceList();
                    return;
                }

                Vector v = (Vector) i.next();

                StringBuffer name = new StringBuffer("Cluster " + (++x) + " (");
                Iterator i2 = v.iterator();

                int countChars = 0;
                int numSequences = v.size();
                while (i2.hasNext()) {
                    Sequence seq = (Sequence) i2.next();

                    String seqName = seq.getDisplayName();
                    if (seqName == null || seqName.isEmpty()) seqName = "{No name}";

                    countChars += seqName.length();

                    if (countChars
                            > CHAR_LIMIT_ON_CLUSTER_NAMES) { // if the count of the names is more
                        // than 40 chars,
                        // stop it and say "and more")
                        if (seqName.length() > CHAR_LIMIT_ON_CLUSTER_NAMES)
                            name.append(numSequences + " sequences");
                        else name.append("and " + numSequences + " more");
                        break;
                    }
                    numSequences--;

                    name.append(seqName);
                    if (i2.hasNext()) name.append(", ");
                }

                name.append(")");

                if (!flag_skipIndivEntries) list_clusters.add(name.toString());
            }
        }

        pb.end();

        System.err.println("1-C");

        pb =
                ProgressDialog.create(
                        seqId.getFrame(),
                        "Writing up information ...",
                        "Formatting and writing the results, please wait.",
                        0);

        System.err.println("1-D");

        try {
            writeupItemStrings(pb);
            System.err.println("1-E");
        } catch (DelayAbortedException e) {
            System.err.println("1-F");
            seqId.unlockSequenceList();
            return;
        }

        System.err.println("1-END");

        /*
        FileDialog fd_saveConsensuses = new FileDialog(
        		seqId.getFrame(),
        		"Please select a file to save the consensuses into",
        		FileDialog.SAVE
        );
        fd_saveConsensuses.setVisible(true);

        java.io.File file_to;
        if(fd_saveConsensuses.getFile() != null) {
        	if(fd_saveConsensuses.getDirectory() != null) {
        		file_to = new java.io.File(
        				fd_saveConsensuses.getDirectory(),
        				fd_saveConsensuses.getFile()
        		);
        	} else {
        		file_to = new java.io.File(fd_saveConsensuses.getFile());
        	}

        	// Save file somewhere
        	com.ggvaidya.TaxonDNA.Common.DNA.formats.FastaFile ff = new com.ggvaidya.TaxonDNA.Common.DNA.formats.FastaFile();
        	try {
        		ff.writeFile(
        			new java.io.File(file_to.getAbsolutePath() + "_lumped.txt"),
        			list_consensuses_lumped,
        			null
        		    );

        		ff.writeFile(
        			new java.io.File(file_to.getAbsolutePath() + "_split.txt"),
        			list_consensuses_split,
        			null
        		);

        		ff.writeFile(
        			new java.io.File(file_to.getAbsolutePath() + "_perfect.txt"),
        			list_consensuses_perfect,
        			null
        		);

        	} catch(DelayAbortedException e) {
        		return;
        	} catch(java.io.IOException e) {
        		MessageBox mb = new MessageBox(seqId.getFrame(), "Couldn't write to file!", "Error occured: " + e);
        		mb.showMessageBox();
        		return;
        	}

        	MessageBox mb_done = new MessageBox(seqId.getFrame(), "Done!", "All consensus sequences were exported.");
        	mb_done.showMessageBox();
        }
         *
         */

        System.err.println("Here");

        selectItem(0);
        seqId.unlockSequenceList();

        System.err.println("There");
    }

    // UIExtension stuff
    public String getShortName() {
        return "Cluster";
    }

    public String getDescription() {
        return "Identifies clusters of similar sequences";
    }

    public Frame getFrame() {
        return null;
    }
}
