/**
 * A handler for the 'commands' used by SequencesFile. A SequencesFile can contain arbitary
 * commands, either at the global level (e.g. '@seqlength 10353') or at the local level (e.g.
 * '^charset cds (203 - 402)'). The actual set of commands is extensible by use of the file
 * declaration on top of the file itself, which is in the format '#sequences (nucleotide
 * cancelledsequences)', implying that we are going to use nucleotide commands, as well as the
 * 'cancelledsequences' set of commands. This allows a loader to warn the user upfront about loosing
 * information: "This file contains 'cancelledsequences' information, which I cannot process. If you
 * export this file again, I cannot export the 'cancelledsequences' information again. Please use a
 * Sequences loader which can understand 'cancelledsequences'.
 *
 * <p>I can still load 'nucleotide' information from this file."
 *
 * <p>This is technically easy while reading a file, but much harder while writing a file, when we
 * won't know which commands we used until right at the very end. The solution is probably to just
 * cheat while writing the file: either buffer the whole thing in memory, then write it out at the
 * end, or use the old create-write-close-create-append technique (create a temp file, write to it,
 * close it, create the actual file, write in the first line, then write out the rest).
 *
 * <p>Either way, CommandHandlers are registered with the SequencesFile system. Unlike other parts
 * of TaxonDNA, COMMAND HANDLERS ARE REQUIRED TO BE SYMMETRICAL AND COMPLETE: any command read by
 * the file must be written back, unchanged, unless it WAS changed. Note that this only incorporates
 * commands the handler understands, so un-understood commands are dropped unceremoniously (we
 * should probably warn the user: but do we need to if we've already warned him about not supporting
 * 'cancelledsequences'?)
 *
 * <p>Another major problem: we cannot handle multiple command sets having the same commands! Only
 * one of the two will actually get the command, so we'll be missing information without knowing any
 * better. Since the only comprehensive solution is to actually disambiguate them (com.fresh...),
 * I'll settle right now for just keeping track of command lists. I'll leave a master list in the
 * repository which tracks this (look for sequences-commands.txt in the same directory as this
 * file).
 *
 * <p>Since fullstops are allowed in key names, for now, you can use a custom name (sequencematrix)
 * followed by a '.', and the rest of the stuff (e.g. 'sequencematrix.colname'). Using a full
 * com.... name would be really nice, too =)
 */

/*
    TaxonDNA
    Copyright (C) 2005, 2006 Gaurav Vaidya

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

import com.ggvaidya.TaxonDNA.Common.DNA.*;

public interface SequencesHandler {
  /**
   * A 'local command' line was read by SequencesFile.
   *
   * @param cmdLine The command line which was read in, including the initial '^'
   * @param seq The sequence currently being processed. Note that the sequence is guaranteed
   *     complete: local commands are only processed AFTER the entire sequence is read (i.e. once
   *     the next sequence begins), IN the order that they appeared.
   * @return true, if you understood this command and 'consumed' it.
   */
  public boolean readLocalCommand(String cmdLine, Sequence seq) throws FormatException;

  /**
   * A 'global' command line was read by SequencesFile.
   *
   * @param cmdLine The command line which was read in, including the initial '@'
   * @param list The SequenceList on which this command is expected to work. Note that this is the
   *     complete sequence list, as global commands are processed after loading is complete.
   * @return true, if you understood this command and 'consumed' it.
   */
  public boolean readGlobalCommand(String cmdLine, SequenceList list) throws FormatException;

  /**
   * An opportunity to write a local command line for a particular sequence.
   *
   * @return The local command line to write, or 'null' if you don't have one. Try not to send back
   *     zero-length strings, as we might write them into the file as a blank line, not to mention
   *     adding them to the header. You can write multiple commands by separating them with
   *     newlines.
   */
  public String writeLocalCommand(Sequence seq);

  /**
   * An opportunity to write a global command line for a particular sequence.
   *
   * @return The global command line to write, or 'null' if you don't have one. Try not to send back
   *     zero-length strings, as we might write them into the file as a blank line, not to mention
   *     adding them to the header. You can write multiple commands by separating them with
   *     newlines.
   */
  public String writeGlobalCommand(SequenceList list);

  /**
   * Return the 'name' used by this module. This is the same name which we'll use in the header, so
   * no spaces. All spaces will be summarily turned into '_'.
   */
  public String getSequencesHandlerName();
}
