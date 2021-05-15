/** PhylipFile allows you to write Phylip files. */

/*
    TaxonDNA
    Copyright (C) 2010 Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Common.DNA.formats;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import java.io.*;
import java.util.*;

public class PhylipFile extends BaseFormatHandler {

  /** Returns the extension. We'll go with '.fas' as our semi-official DOS-compat extension */
  public String getExtension() {
    return "phy";
  }

  /** Returns the short name of this file format. */
  public String getShortName() {
    return "Phylip";
  }

  /**
   * Returns the full name of this file format handler. E.g. "Nexus file format v2 and below". You
   * ought to put in something about what versions of the software you support. But not too long:
   * think about whether you could display it in a list.
   */
  public String getFullName() {
    return "Phylip format";
  }

  /**
   * Read this file into the specified SequenceList. This will read all the files straight into this
   * sequence list, in the correct order.
   *
   * @throws IOException if there was an error doing I/O
   * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
   * @throws FormatException if there was an error in the format of the file.
   * @throws DelayAbortedException if the DelayCallback was aborted by the user.
   */
  public SequenceList readFile(File file, DelayCallback delay)
      throws IOException, SequenceException, FormatException, DelayAbortedException {
    throw new RuntimeException("Phylip files cannot currently be read by this program");
  }

  /**
   * Append this file to the specified SequenceList. This will read in all the sequences from the
   * file and append them directly onto the end of this SequenceList.
   *
   * @throws IOException if there was an error doing I/O
   * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
   * @throws FormatException if there was an error in the format of the file.
   * @throws DelayAbortedException if the DelayCallback was aborted by the user.
   */
  public void appendFromFile(SequenceList appendTo, File fileFrom, DelayCallback delay)
      throws IOException, SequenceException, FormatException, DelayAbortedException {
    throw new RuntimeException("Phylip files cannot currently be read by this program");
  }

  public FormatException formatException(StreamTokenizer tok, String message) {
    return new FormatException("Error on line " + tok.lineno() + ": " + message);
  }

  private int atoi(String word, StreamTokenizer tok) throws FormatException {
    try {
      return Integer.parseInt(word);
    } catch (NumberFormatException e) {
      throw formatException(tok, "Could not convert word '" + word + "' to a number: " + e);
    }
  }

  /**
   * Writes the content of this sequence list into a file. The file is overwritten. The order of the
   * sequences written into the file is guaranteed to be the same as in the list.
   *
   * @throws IOException if there was a problem creating/writing to the file.
   * @throws DelayAbortedException if the DelayCallback was aborted by the user.
   */
  public void writeFile(File file, SequenceList set, DelayCallback delay)
      throws IOException, DelayAbortedException {
    writePhylipFile(file, set, delay);
  }

  /**
   * A species TNTFile-only method to have a bit more control over how the Nexus file gets written.
   *
   * @param interleaveAt Specifies where you want to interleave. Note that TNTFile.INTERLEAVE_AT
   *     will be entirely ignored here, and that if the sequence is less than interleaveAt, it will
   *     not be interleaved at all. '-1' turns off all interleaving (flatline), although you can
   *     obviously use a huge value (999999) to get basically the same thing.
   * @param otherBlocks We put this into the file at the very bottom. It should be one or more
   *     proper 'BLOCK's, unless you really know what you're doing.
   */
  public void writePhylipFile(File file, SequenceList set, DelayCallback delay)
      throws IOException, DelayAbortedException {
    set.lock();

    // it has begun ...
    if (delay != null) delay.begin();

    // write out a 'preamble'
    PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));

    /*
    writer.println("nstates 32;");		// give our data the best possible chance
    writer.println("xread");		// begin sequence output
    writer.println("'Written by TaxonDNA " + Versions.getTaxonDNA() + " on " + new Date() + "'");
    					// commented string
    // write the maxlength/sequence count
     *
     * Headers should go in here, but I don't know how to do Phylip comments.
     */
    writer.println(set.count() + " " + set.getMaxLength());

    /*
     * The following piece of code has to:
     * 1.	Figure out VALID, UNIQUE names to output.
     * 2.	Without hitting up against PAUP* and MacClade's specs (we'll
     * 	assume 32 chars for now - see MAX_TAXON_LENGTH - and work
     * 	around things when we need to).
     *
     * Interleaving will be handled later.
     */
    Hashtable names = new Hashtable(); // Hashtable(Strings) -> Sequence
    //	hash of all the names currently in use
    Vector vec_names = new Vector(); // Vector(String)
    Iterator i = set.iterator();
    while (i.hasNext()) {
      Sequence seq = (Sequence) i.next();

      String name = seq.getFullName();
      // seq.getFullName(MAX_TAXON_LENGTH), MAX_TAXON_LENGTH);
      // TODO: This is a bad idea when we're generating custom
      // sets or whatever. In SequenceMatrix, though, this is
      // perfectly fine.
      name = name.replaceAll("[^\\w]+", "_");
      // name = name.replace(' ', '_');		// we do NOT support ' '. Pfft.

      int no = 2;
      while (names.get(name) != null) {
        int digits = 5;
        if (no > 0 && no < 10) digits = 1;
        if (no >= 10 && no < 100) digits = 2;
        if (no >= 100 && no < 1000) digits = 3;
        if (no >= 1000 && no < 10000) digits = 4;

        name = seq.getFullName();
        name = name.replaceAll("[^\\w]+", "_"); // we do NOT support '. Pfft.
        name += "_" + no;

        no++;

        if (no == 10000) {
          // this has gone on long enough!
          throw new IOException(
              "There are 9999 sequences named '"
                  + seq.getFullName()
                  + "', which is the most I can handle. Sorry. This is an arbitary limit: please let us know if you think we set it too low.");
        }
      }

      // System.err.println("In TNTFile export: replaced '" + seq.getFullName() + "' with '" + name
      // + "'");

      names.put(name, seq);
      vec_names.add(name);
    }

    Iterator i_names = vec_names.iterator();

    int x = 0;
    while (i_names.hasNext()) {
      // report the delay
      if (delay != null) {
        try {
          delay.delay(x, vec_names.size());
        } catch (DelayAbortedException e) {
          writer.close();
          set.unlock();
          throw e;
        }
      }

      String name = (String) i_names.next();
      Sequence seq = (Sequence) names.get(name);

      writer.println(name + " " + seq.getSequence());

      x++;
    }

    writer.close();

    // it's over ...
    if (delay != null) delay.end();

    set.unlock();
  }

  /**
   * Checks to see if this file *might* be of this format. Good for internal loops.
   *
   * <p>No exceptions: implementors, please swallow them all up. If the file does not exist, it's
   * not very likely to be of this format, is it?
   */
  public boolean mightBe(File file) {
    return false;
  }

  private String fixColumnName(String columnName) {
    columnName = columnName.replaceAll("\\.nex", "");
    columnName = columnName.replaceAll("\\.tnt", "");
    columnName = columnName.replace('.', '_');
    columnName = columnName.replace(' ', '_');
    columnName = columnName.replace('-', '_');
    columnName = columnName.replace('\\', '_');
    columnName = columnName.replace('/', '_');
    return columnName;
  }
}
