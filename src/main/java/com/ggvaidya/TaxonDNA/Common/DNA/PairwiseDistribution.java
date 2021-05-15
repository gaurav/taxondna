/**
 * A pairwise distribution. And this is how it works: 1. You call it in a "mode". Yes, this means
 * two loops per sequence list, but this makes our code _much_ easier to deal with and prevents the
 * irritating copy-around we had going on in the last loop. (p.s. it's not that slow!) 2. We still
 * store all the distances. While this does get flushed when the PairwiseDistribution object goes
 * out of context, this means a LOT of memory spent. There's no real way around this, but now we'll
 * be smart about it, using a Vector to hide our array and using floats directly.
 *
 * <p>We use floats, since in Java we are guaranteed 6-7 digits of accuracy. This also much
 * simplifies code.
 *
 * <p>NOTE: This class is very, very thread-unsafe during creation (i.e. all functions will return
 * weird values if you run them before the constructor has finished running). If you need to access
 * it from more than one thread, it's up to you to make the magic happen.
 */

/*
    TaxonDNA
    Copyright (C) 2005	Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Common.DNA;

import com.ggvaidya.TaxonDNA.Common.*;
import java.util.*;

public class PairwiseDistribution {
  public static final int PD_INTRA = 0;
  public static final int PD_INTER = 1;

  public static final char CUMUL_FORWARD = 'F';
  public static final char CUMUL_BACKWARD = 'B';

  // how many new 'floats' should we add to distances array
  // every time we run out of memory in it?
  private static final int INCREASE_SIZE = 500;

  // distance, and other vars needed to handle it
  private float[] distances;
  private int size = 0;
  private int memory = 0;

  // statistics we might need to report to the user
  private int count_sequences = 0;

  /*
   * The following functions handle 'this.distances'.
   * Basically, it's a one-way stack: no pop, only push.
   * On a push, we must check to see if there is space
   * for the new number; otherwise, we just push the
   * number at the end.
   */
  private int distances_size() {
    return size;
  }

  /*	Don't really need this, do we?
  private int distances_memory() {
  	return memory;
  }
  */

  private void distances_push(float new_distance) throws OutOfMemoryError {
    // We're not stupid, '-1' distances are USELESS!
    if (new_distance == -1) return;

    if (size > memory) {
      // wtf?
      throw new RuntimeException(
          "Programming error in PairwiseDistribution.class: Please inform the developer!");
    } else if (size == memory) {
      // allocate new memory first
      //
      float temp[] = new float[size + INCREASE_SIZE];

      if (size != 0) System.arraycopy(distances, 0, temp, 0, size);
      distances = temp;

      memory += INCREASE_SIZE;
      //			System.err.println("Debug: PairwiseDistribution expanded to " + memory + " floats");
    }

    // so now we have memory
    distances[size] = new_distance;
    //		System.err.println("ADD: " + distances[size]);
    size++;
  }

  /**
   * Constructor. Give it a list and what kind of distribution you want it to be, and watch it go to
   * work!
   */
  public PairwiseDistribution(SequenceList list, int type, DelayCallback delay)
      throws DelayAbortedException {
    list.lock();

    if (delay != null) delay.begin();

    int interval = list.count() / 100;
    if (interval == 0) interval = 1;

    // go thru the list, calculating all the distances in this category.
    // we use private "helper" functions to help (and make the code less painful)
    Iterator i = list.iterator();
    while (i.hasNext()) {
      Sequence query = (Sequence) i.next();

      if (type == PD_INTRA) _addIntra(list, query);
      else if (type == PD_INTER) _addInter(list, query);
      else
        throw new RuntimeException(
            "Programmer Error in PairwiseDistribution: Please inform the programmer!");

      if (delay != null && count_sequences % interval == 0) {
        try {
          delay.delay(count_sequences, list.count());
        } catch (DelayAbortedException e) {
          list.unlock();
          throw e; // get outta here
        }
      }

      count_sequences++;
    }

    //		for(int x = 0; x < size; x++) {
    //			System.err.println("ADD: " + distances[x]);
    //		}

    // Sort it up, before we ship it out
    if (size > 0) Arrays.sort(distances, 0, size);

    if (delay != null) delay.end();

    list.unlock();
  }

  /*
   * These private "helper functions" will help out with generating the pairwise distribution
   */
  /**
   * Calculate all intraspecific pairwise distances for 'query' in SequenceList 'list', and add it
   * to this pairwise distrib.
   */
  private void _addIntra(SequenceList list, Sequence query) {
    if (query.getSpeciesName() == null) return;

    Iterator i = list.conspecificIterator(query.getSpeciesName());

    while (i.hasNext()) {
      Sequence seq = (Sequence) i.next();

      if (seq.equals(query)) continue;

      // only half table
      if (seq.getFullName().compareTo(query.getFullName()) < 0) continue;

      distances_push((float) query.getPairwise(seq));
      //			System.err.println("DEBUG - Intra: " + query + " with " + seq);
    }
  }

  /**
   * Calculate all interspecific pairwise distances for 'query' in SequenceList 'list', and add it
   * to this pairwise distrib.
   */
  private void _addInter(SequenceList list, Sequence query) {
    Iterator i = list.iterator();

    while (i.hasNext()) {
      Sequence seq = (Sequence) i.next();

      if (seq.equals(query)) continue;

      if (query.getGenusName().equals(seq.getGenusName())) {
        // identical genera
        if (!query.getSpeciesNameOnly().equals(seq.getSpeciesNameOnly())) {
          // but non identical species
          //
          // however, only do it one way (half-table only)
          if (query.getSpeciesNameOnly().compareTo(seq.getSpeciesNameOnly()) < 0)
            distances_push((float) query.getPairwise(seq));
          //					System.err.println("DEBUG - Inter: " + query + " with " + seq);
        }
      }
    }
  }

  /** Number of sequences in this pairwise distribution. */
  public int countSequences() {
    return count_sequences;
  }

  /** Number of valid comparisons (i.e. non-negative comparisons) */
  public int countValidComparisons() {
    return distances_size();
  }

  /** Number of comparisons which return a distance of exactly zero */
  public int getZero() {
    int count = 0;
    for (int x = 0; x < countValidComparisons(); x++) {
      if (identical(distances[x], 0.0f)) count++;
      else
        // non-zero! let's get out!
        break;
    }
    return count;
  }

  /** Number of comparisons which return a distance of exactly one */
  public int getOne() {
    int count = 0;
    for (int x = countValidComparisons() - 1; x >= 0; x--) {
      if (identical(distances[x], 1.0f)) count++;
      else
        // non-one! let's get out!
        break;
    }
    return count;
  }

  /**
   * Produces a String version of part of the distribution. This goes from d_from to d_to at
   * intervals d_interval. d_interval can be negative, if you want.
   *
   * <p>There is one more argument: cumulDirection, the direction in which the cumulative frequency
   * should count. If cumulDirection == CUMUL_FORWARD, then it increases, and if it is
   * CUMUL_BACKWARD, it decreases (begins at 1.0 and tends to 0.0). Everything else is undefined.
   */
  public String getDistributionAsString(
      double d_from, double d_to, double d_interval, char cumulDirection) {
    boolean cumul_forward = true;
    if (cumulDirection == CUMUL_BACKWARD) cumul_forward = false;

    StringBuffer str = new StringBuffer();

    // print the title
    appendTableEntry(str, "Distances", "Freq.", "Perc.", "Cumulative");

    // Do the 'less than d_from' distance
    int count = distances_size();
    int f_from = getBetweenIncl(0, (float) d_from);
    float cumul = ((float) f_from / count);
    if (!cumul_forward) cumul = 1.0f - cumul;
    appendTableEntry(
        str,
        "<= " + percentage(d_from, 1.0) + "%",
        f_from,
        percentage(f_from, count),
        percentage(cumul, 1.0));

    // start 'stepping'
    for (double x = d_from + 0.000001; x < d_to; x += d_interval) {
      double d_next = x + d_interval;
      f_from = getBetweenIncl((float) x, (float) d_next);
      if (cumul_forward) cumul += ((float) f_from / count);
      else cumul -= ((float) f_from / count);

      appendTableEntry(
          str,
          percentage(x, 1) + "% to " + percentage(d_next, 1) + "%",
          f_from,
          percentage(f_from, count),
          percentage(cumul, 1.0));
    }

    // and the final distance
    f_from = getBetweenIncl((float) (d_to + 0.000001), (float) 1.0);
    if (cumul_forward) cumul += ((float) f_from / count);
    else cumul -= ((float) f_from / count);

    appendTableEntry(
        str,
        "> " + percentage(d_to, 1.0) + "%",
        f_from,
        percentage(f_from, count),
        percentage(cumul, 1.0));

    return str.toString();
  }

  /** Helper function, writes out a line in the 'table' */
  private void appendTableEntry(
      StringBuffer buff, String label, int freq, double perc, double cumul) {
    appendTableEntry(
        buff, label, String.valueOf(freq), String.valueOf(perc), String.valueOf(cumul));
  }

  /** Helper function, writes out a line in the 'table' */
  private void appendTableEntry(
      StringBuffer buff, String distances, String freq, String perc, String cumul) {
    appendFixedLength(buff, distances, 20);
    appendFixedLength(buff, freq, 10);
    appendFixedLength(buff, perc, 10);
    appendFixedLength(buff, cumul, 20);
    buff.append('\n');
  }

  /**
   * Helper function, prints only the first 'len' characters of the string, and inserts spaces to
   * make the columns line up. Smart enough to use '...' if anything is cut off, etc.
   */
  private void appendFixedLength(StringBuffer buff, String str, int len) {
    if (1 == 1) {
      buff.append(str + "\t");
      return;
    }

    if (str.length() < len) {
      // add spaces to make it add up
      buff.append(str);
      for (int x = str.length(); x < len; x++) buff.append(' ');
    } else {
      buff.append(str.substring(0, len - 3) + "...");
    }
  }

  /** Helper function, calculates percentages. */
  private double percentage(double x, double y) {
    return com.ggvaidya.TaxonDNA.Common.DNA.Settings.percentage(x, y);
  }

  /**
   * Returns the number of distances in between the two float ranges. Remember that the range is
   * from &lt; this &lt;= to, so don't be surprised if the lower edge of the range doesn't turn up.
   * This allows the ranges to fit into each other for printing, and the first range is then those
   * with distance = 0, an important value in taxonomy.
   */
  public int getBetween(double from, double to) {
    return getDistancesBetween(from, to - 0.000001).size();
  }

  /**
   * Get between - Inclusive. So the 'from' values are counted too. (i.e. from &lt;= this &lt;= to)
   *
   * <p>"People who like this sort of thing, will find this the sort of thing they like." -- Abraham
   * Lincoln
   */
  public int getBetweenIncl(float from, float to) {
    return getDistancesBetween(from, to).size();
  }

  /** Return the largest distance in this pairwise distribution */
  public float getMaximumDistance() {
    if (size > 0) {
      return distances[size - 1];
    }
    return 0;
  }

  /** Return the smallest distance in this pairwise distribution. */
  public float getMinimumDistance() {
    if (size > 0) return distances[0];
    return 0;
  }

  /**
   * Returns a Vector, containing all the distances between d_from and d_to. Since we assume you
   * must want ALL the distances, it is inclusive both ends.
   */
  public Vector getDistancesBetween(double d_from, double d_to) {
    Vector vec = new Vector();
    boolean count = false;

    for (int x = 0; x < size; x++) {
      if (distances[x] >= d_from) count = true;
      if (distances[x] > d_to) count = false;

      if (count) vec.add(new Float(distances[x]));
    }
    return vec;
  }

  /** Compares two floats for 'identicality'. */
  private boolean identical(float x, float y) {
    if ((y - x) < 0.0000001) return true;
    return false;
  }

  /** Quickie: returns a percentage */
  private double percentage(int x, int y) {
    return percentage((double) x, (double) y);
  }
}
