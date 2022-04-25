/**
 * Perform an analysis of the distances within genera.
 *
 * <p>Figure out the closest allospecific (congeneric) distance, and the mean allospecific
 * (congeneric) distance. Then summarise this as average per species, then average per genus. Final
 * output should be like this: Genus name | # species | avg smallest inter distance | avg mean inter
 * distance
 *
 * <p>For avg smallest inter distance: - For each sequence, find the closest interspecific distance
 * - For each species, find the *average* of the CID for each sequence - For each genus, find the
 * *average* of the CID for each species
 *
 * <p>For avg mean inter distance: - For each sequence, find the mean interspecific distance (sum of
 * all inter/count of all inter) - For each species, find the average of the MID - For each genus,
 * find the average of the MID
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2007 Gaurav Vaidya

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
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;

public class DistanceAnalysis extends Panel
        implements UIExtension, ActionListener, ItemListener, Runnable {
    private SpeciesIdentifier seqId = null;
    private SequenceList list = null;

    private TextArea text_main = new TextArea();

    private Button btn_Calculate =
            new Button("Calculate smallest/mean interspecific, congeneric sequences per genus");
    private Button btn_Copy;

    private String display_strings[];

    public DistanceAnalysis(SpeciesIdentifier seqId) {
        this.seqId = seqId;

        setLayout(new BorderLayout());

        Panel top = new Panel();
        RightLayout rl = new RightLayout(top);
        top.setLayout(rl);

        btn_Calculate.addActionListener(this);
        rl.add(btn_Calculate, RightLayout.NEXTLINE | RightLayout.FILL_4);

        add(top, BorderLayout.NORTH);

        text_main.setEditable(false);
        add(text_main);

        text_main.setText("No data loaded.");

        Panel buttons = new Panel();
        buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));

        btn_Copy = new Button("Copy to Clipboard");
        btn_Copy.addActionListener(this);
        buttons.add(btn_Copy);

        add(buttons, BorderLayout.SOUTH);
    }

    public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            int item = ((Integer) e.getItem()).intValue();

            if (item <= display_strings.length) {
                text_main.setText(display_strings[item]);
            } else {
                text_main.setText("Invalid item");
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();

        if (cmd.equals("Copy to Clipboard") || cmd.equals("Oops, try again?")) {
            try {
                Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
                StringSelection selection = new StringSelection(text_main.getText());

                clip.setContents(selection, selection);
            } catch (IllegalStateException ex) {
                btn_Copy.setLabel("Oops, try again?");
            }
            btn_Copy.setLabel("Copy to Clipboard");
        }

        if (e.getSource().equals(btn_Calculate)) {
            new Thread(this, "BlockAnalysis").start();
        }
    }

    public void dataChanged() {
        list = seqId.lockSequenceList();

        if (list == null) {
            text_main.setText("No sequences loaded.");
        } else {
            text_main.setText(
                    "Please press the 'Calculate' button to conduct a distance analysis.");
        }

        seqId.unlockSequenceList();
    }

    private double average(Vector v) {
        double sum = 0.0;
        int count = 0;

        Iterator i = v.iterator();
        while (i.hasNext()) {
            Double d = (Double) i.next();
            double val = d.doubleValue();
            if (val > -1) {
                sum += d.doubleValue();
                count++;
            }
        }

        return (sum / count);
    }

    private void append_to_hash_table(Hashtable ht, Object key, Object value) {
        if (ht.get(key) == null) {
            Vector v = new Vector();
            v.add(value);
            ht.put(key, v);
        } else ((Vector) ht.get(key)).add(value);
    }

    public void run() {
        // Okay we need to figure out a lot of Vectors to keep track of numbers as we go, and then
        // average them out.

        // Init!
        SequenceList list = seqId.lockSequenceList();

        // First off, we need SSLs. Lots of SSLs.
        // That means PairwiseDistances
        ProgressDialog delay =
                ProgressDialog.create(
                        seqId.getFrame(),
                        "Please wait, calculating distances ...",
                        "Distances for this dataset are being calculated. Sorry for the delay!");

        try {
            // for each sequence in the dataset, we need:
            // 1.	a smallest inter/congen distance
            // 2.	an average inter/congen distance
            delay.begin();

            // data structures
            SortedSequenceList ssl = new SortedSequenceList(list);
            Hashtable ht_species = new Hashtable();
            Hashtable ht_species_all = new Hashtable();
            Hashtable ht_species_smallest = new Hashtable();
            Hashtable ht_genera_all = new Hashtable(); // ha! perl thinking!
            Hashtable ht_genera_smallest = new Hashtable(); // ha! perl thinking!

            int current_seq = 0;
            Iterator i = list.iterator();
            while (i.hasNext()) {
                delay.delay(current_seq, list.count());
                current_seq++;

                Sequence query = (Sequence) i.next();
                ht_species.put(query.getSpeciesName(), new Object());

                ssl.sortAgainst(query, null);

                double closest = -1;
                Vector vec_inters = new Vector();
                for (int x = 0; x < ssl.count(); x++) {
                    Sequence compare = ssl.get(x);

                    if (compare.getSpeciesName().equals(query.getSpeciesName())) {
                        // conspecific, ignore!
                        continue;
                    }

                    if (compare.getGenusName().equals(query.getGenusName())) {
                        // congeneric, but NOT conspecific
                        // (i.e. congeneric interspecific)
                        // (i hate mondays)
                        //
                        double dist = compare.getPairwise(query);
                        if (closest < 0) closest = dist;
                        vec_inters.add(new Double(dist));
                    }
                }

                // now, sequence 'query' has (we hope) a closest (or -1, indicating NO
                // congen/intersp)
                // as well as a Vector of interspecific distances.
                if (closest != -1) {
                    if (ht_species_all.get(query) == null) {
                        query = new Sequence(query); // make an exact copy of the query
                        // so we can distinquish between the two
                        // for the purposes of this analysis
                    }
                    ht_species_all.put(query, new Double(average(vec_inters)));
                    ht_species_smallest.put(query, new Double(closest));
                }
            }

            // so now we've got per-sequence numbers.
            // we need to change this to the following format:
            // $ht_sequence{Genus_name} = \@distances_for_this_sequence.
            i = ht_species.keySet().iterator();
            while (i.hasNext()) {
                String spName = (String) i.next();
                String genusName = null;

                Vector distances_all = new Vector();
                Vector distances_smallest = new Vector();

                Iterator i_seqs = ht_species_all.keySet().iterator();
                while (i_seqs.hasNext()) {
                    Sequence seq = (Sequence) i_seqs.next();

                    if (seq.getSpeciesName().equals(spName)) {
                        // it's the avg distance for one particular thing!
                        genusName = seq.getGenusName();
                        distances_all.add(ht_species_all.get(seq));
                        distances_smallest.add(ht_species_smallest.get(seq));
                    }
                }

                if (genusName == null) {
                    // make one up
                    int indexOf = spName.indexOf(' ');
                    genusName = spName.substring(0, indexOf);
                }

                if (distances_all.size() > 0) {
                    append_to_hash_table(
                            ht_genera_all, genusName, new Double(average(distances_all)));
                    append_to_hash_table(
                            ht_genera_smallest, genusName, new Double(average(distances_smallest)));
                } else {
                    append_to_hash_table(ht_genera_all, genusName, new Double(-1));
                    append_to_hash_table(ht_genera_smallest, genusName, new Double(-1));
                }
            }

            // so now we've got per-species numbers.
            // so let's go and generate real genus numbers.
            // arrrrrgh
            StringBuffer buff = new StringBuffer();

            buff.append("Genus\t\t\tAvg. Avg. Smallest Inter\t\tAvg. Avg. Avg. Inter\n");

            Vector v_genera = new Vector(ht_genera_all.keySet());
            Collections.sort(v_genera);
            i = v_genera.iterator();

            while (i.hasNext()) {
                String genus = (String) i.next();

                Vector distances_all = (Vector) ht_genera_all.get(genus);
                double avg_inter = average(distances_all);

                Vector distances_smallest = (Vector) ht_genera_smallest.get(genus);
                double avg_smallest = average(distances_smallest);

                if (avg_inter < 0)
                    buff.append(genus + "\t\t\tNo comparisions\t\tNo comparisions\n");
                else
                    buff.append(
                            genus
                                    + "\t\t\t"
                                    + percentage(avg_smallest, 1)
                                    + "\t\t"
                                    + percentage(avg_inter, 1)
                                    + "\n");
            }

            text_main.setText(buff.toString());

            delay.end();
            delay = null;
        } catch (DelayAbortedException e) {
            return;
        } finally {
            seqId.unlockSequenceList();
            if (delay != null) // this will actually work! trust me.
            delay.end();
        }

        return;
    }

    private double percentage(double x, double y) {
        return com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(x, y);
    }

    public String getShortName() {
        return "Distance Analysis";
    }

    public String getDescription() {
        return "Calculates parameters to study the distance distribution";
    }

    public boolean addCommandsToMenu(Menu commandMenu) {
        return false;
    }

    public Panel getPanel() {
        return this;
    }
}
