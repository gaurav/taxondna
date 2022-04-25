/**
 * TNTFile allows you to read and write TNT files. I'm going to be guesstimating the format from, of
 * all things, my own TNT exporter. I'm also going to be using TNT's own (fairly limited)
 * documentation.
 *
 * <p>This module really is pretty messed up, since I made the insanely great design decision of
 * writing the square-bracket-handling code in here (thereby practically inverting an entire
 * function) instead of just rewriting Sequence to handle it. Stupid, stupid, stupid, and a great
 * example of why I shouldn't code when sleepy or writer's-blocked.
 *
 * <p>TODO: Okay, 'nstates 32' will work out well enough, BUT all bases will then have to be
 * converted into fully written out format (which, as an interesting aside, converts it into
 * BaseSequence ...), i.e. W to [AC] or whatever. Then, everything will be Just Fine.
 */

/*
    TaxonDNA
    Copyright (C) 2006-07, 2010 Gaurav Vaidya

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

public class TNTFile extends BaseFormatHandler {
    public static final int MAX_TAXON_LENGTH =
            31; // TNT truncates at 32 characters, but it gives a warning at 32
    // I don't like warnings
    private static final int INTERLEAVE_AT = 80; // default interleave length

    // group constants
    private static final int GROUP_CHARSET = 1;
    private static final int GROUP_TAXONSET = 2;

    /** Returns the extension. We'll go with '.fas' as our semi-official DOS-compat extension */
    public String getExtension() {
        return "tnt";
    }

    /** Returns a valid OTU (Operation Taxonomic Unit); that is, a taxon name. */
    public String getTNTName(String name) {
        // Rule #1: the name must start with '[A-Za-z0-9\-\+\.]'
        char first = name.charAt(0);
        if ((first >= 'A' && first <= 'Z')
                || (first >= 'a' && first <= 'z')
                || (first >= '0' && first <= '9')
                || (first == '_')) {
            // it's all good!
        } else {
            name = "_" + name;
        }

        // Rule #2: strange characters we'll turn into '_'
        name = name.replaceAll("[^a-zA-Z0-9\\-\\+\\.\\_\\*\\:\\(\\)\\|\\\\\\/]", "_");

        // Rule #3: spaces we'll turn into '_'
        name = name.replace(' ', '_');

        // Rule #4: truncate to 'len'
        return name;
    }

    /** Returns the short name of this file format. */
    public String getShortName() {
        return "TNT";
    }

    /**
     * Returns the full name of this file format handler. E.g. "Nexus file format v2 and below". You
     * ought to put in something about what versions of the software you support. But not too long:
     * think about whether you could display it in a list.
     */
    public String getFullName() {
        return "TNT/Hennig86 support";
    }

    /**
     * Read this file into the specified SequenceList. This will read all the files straight into
     * this sequence list, in the correct order.
     *
     * @throws IOException if there was an error doing I/O
     * @throws SequenceException if a Sequence is malformed - incorrect bases, etc.
     * @throws FormatException if there was an error in the format of the file.
     * @throws DelayAbortedException if the DelayCallback was aborted by the user.
     */
    public SequenceList readFile(File file, DelayCallback delay)
            throws IOException, SequenceException, FormatException, DelayAbortedException {
        SequenceList sl = new SequenceList();
        sl.lock(); // retarded.
        appendFromFile(sl, file, delay);
        sl.unlock();
        return sl;
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
        FormatHandlerEvent evt = new FormatHandlerEvent(fileFrom, this, appendTo);

        // set up the delay
        if (delay != null) delay.begin();

        appendTo.lock();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(fileFrom));

            // count lines
            int count_lines = 0;
            int count_data = 0;
            long file_length = fileFrom.length();
            while (reader.ready()) {
                if (delay != null)
                    delay.delay((int) ((float) count_data / file_length * 100), 1000);
                // note that we're really going from 0% to 10%. This will
                // make the user less confused when we jump from 10% to 0% and
                // start over.
                count_data += reader.readLine().length();
                count_lines++;
            }

            // let's go!
            reader = new BufferedReader(new FileReader(fileFrom));
            StreamTokenizer tok = new StreamTokenizer(reader);

            // okay, here's how it's going to work:
            // 1. 	we will ONLY handle xread for now. i.e. NO other commands will be
            // 	processed at ALL. If we see any other command, we will just quietly
            // 	wait for the terminating semicolon.
            // 2.	well, okay - we will also handle nstates. But only to check for
            // 	'nstates dna'. Anything else, and we barf with extreme prejudice.
            // 3.	we will also handled comments 'perfectly' ... err, assuming that
            // 	anything in single quotes is a comment.	Which we can't really
            // 	assume. Ah well.
            // 4.	A LOT of the naming rules are just being imported in en toto from
            // 	NEXUS. This is probably a stupid idea, but I hate giving up on the
            // 	rigid flexibility they allowed. If any of these rules do NOT work
            // 	in TNT, lemme know.

            tok.ordinaryChar(
                    '/'); // this is the default StringTokenizer comment char, so we need to set it
            // back to
            // nothingness
            tok.wordChars('@', '@'); // this is a special command we look out for in the title

            // turn off parseNumbers()
            tok.ordinaryChar('.');
            tok.ordinaryChar('-');
            tok.ordinaryChars('0', '9');
            tok.wordChars('.', '.');
            tok.wordChars('-', '-');
            tok.wordChars('0', '9');

            tok.wordChars(
                    '|', '|'); // this is officially allowed in Nexus, and pretty convenient for us
            tok.wordChars('_', '_');
            // we need to replace this manually. they form one 'word' in NEXUS.

            tok.ordinaryChar(
                    '\''); // We need this to be an 'ordinary' so that we can use it to discriminate
            // comments
            tok.ordinaryChar(
                    ';'); // We need this to be an 'ordinary' so that we can use it to distinguish
            // commands

            // states
            int commentLevel = 0;
            boolean newCommand = true;

            // WARNING:	newCommand is reset at the BOTTOM of the while loop
            // 		Only use 'continue' if you've slurped up an ENTIRE
            // 		command (including the ';')
            while (true) {
                /* Before anything else, do the delay */
                if (delay != null) delay.delay(tok.lineno(), count_lines);

                /* Now ... to business! */
                int type = tok.nextToken();

                // break at end of file
                if (type == StreamTokenizer.TT_EOF) break;

                // is it a comment?
                if (type == '\'') {
                    if (commentLevel == 0) commentLevel = 1;
                    else commentLevel = 0;

                    continue;
                }

                // if we're in a comment, skip normal stuff
                if (commentLevel > 0) continue;

                // semi-colons indicate end of line.
                // some commands use this to determine the end of command
                if (type == ';') {
                    newCommand = true;
                    continue;
                }

                // Words in here are guaranteed to be a 'command'
                if (newCommand && type == StreamTokenizer.TT_WORD) {
                    String str = tok.sval;

                    // nstates {we only understand 'nstates tnt'}
                    if (str.equalsIgnoreCase("nstates")) {
                        int token = tok.nextToken();

                        if ((token == StreamTokenizer.TT_WORD)
                                && (tok.sval.equalsIgnoreCase("dna"))) {
                            // nstates dna! we can handle this ...
                        } else {
                            // well, okay we can baseSequence it, UNLESS:
                            if (tok.sval.equalsIgnoreCase("cont"))
                                throw formatException(
                                        tok,
                                        "This program can currently only load files which contain"
                                            + " discrete sequences. This file does not (it contains"
                                            + " continuous data, as indicated by 'nstates "
                                                + tok.sval
                                                + "').");
                        }
                    }

                    // xread {okay, we need to actually read the matrix itself}
                    else if (str.equalsIgnoreCase("xread")) {
                        xreadBlock(appendTo, tok, evt, delay, count_lines);
                        newCommand = true;
                        continue;
                    }

                    // xgroup/agroup
                    // 	{xgroup: character sets}
                    // 	{agroup: taxonsets}
                    // since the format is essentially identical, we'll use the
                    // same function to handle them
                    else if (str.equalsIgnoreCase("xgroup")) {
                        groupCommand(GROUP_CHARSET, appendTo, tok, evt, delay, count_lines);
                        newCommand = true;
                        continue;
                    } else if (str.equalsIgnoreCase("agroup")) {
                        groupCommand(GROUP_TAXONSET, appendTo, tok, evt, delay, count_lines);
                        newCommand = true;
                        continue;
                    } else {
                        // okay, we're 'in' a command
                        // but thanks to newCommand, we can just ignore
                        // all the crap; the program will just loop
                        // around until it sees the ';', newCommand
                        // is activated, and we look for the next
                        // block.
                    }

                } else {
                    // strange symbol (or ';') found
                    // since we're probably in a 'strange' block, we can just
                    // ignore this.
                }

                newCommand = false; // this will be reset at the end of the line -- i.e, by ';'
            }
        } finally {
            if (delay != null) delay.end();
            appendTo.unlock();
        }

        appendTo.setFile(fileFrom);
        appendTo.setFormatHandler(this);
    }

    public FormatException formatException(StreamTokenizer tok, String message) {
        return new FormatException("Error on line " + tok.lineno() + ": " + message);
    }

    /**
     * Parses an 'xread' command. This is going to be a pretty simple interpretation, all things
     * considered: we'll ignore amperstands, and we'll barf quickly and early if we think we're
     * going in over our head. Pretty much, we are just targeting trying to be able to open files we
     * spit out. Can't be _that_ hard, can it?
     *
     * <p>Implementation note: the string '[]' in the sequence will be converted into a single '-'
     */
    public void xreadBlock(
            SequenceList appendTo,
            StreamTokenizer tok,
            FormatHandlerEvent evt,
            DelayCallback delay,
            int count_lines)
            throws FormatException, DelayAbortedException, IOException {
        Interleaver interleaver = new Interleaver();
        int seq_names_count = 0;
        int begin_at = tok.lineno(); // which line did this xreadBlock start at
        char missingChar = '?';
        char gapChar = '-'; // there is no standard definition of which characters
        // TNT uses for these, nor can I figure out how to
        // determine these values. So I'm just going with our
        // own defaults, to be tweaked as required.

        tok.wordChars(gapChar, gapChar);
        tok.wordChars(missingChar, missingChar);
        tok.wordChars('[', '['); // for [ACTG] -> N type stuff
        tok.wordChars(']', ']');

        Hashtable hash_names = new Hashtable(); // String name -> Sequence
        String name = null;

        // '.', '(' and ')' should be read as part of sequence names.
        tok.wordChars('.', '.');
        tok.wordChars('(', ')');
        tok.wordChars(')', ')');

        // okay, 'xread' has started.
        if (tok.ttype == StreamTokenizer.TT_WORD && tok.sval.equalsIgnoreCase("xread"))
            ; // we've already got xread on the stream, do nothing
        else tok.nextToken(); // this token IS 'xread'

        // now, move on to the token after 'xread'
        tok.nextToken();

        // first, we get the title
        StringBuffer title = null;
        if (tok.ttype == '\'') {
            title = new StringBuffer();

            while (true) {
                if (delay != null) delay.delay(tok.lineno(), count_lines);

                int type = tok.nextToken();

                if (type == '\'') break;

                // comment commands (our hacks, basically)
                if (type == StreamTokenizer.TT_WORD) {
                    if (tok.sval.length() > 0 && tok.sval.charAt(0) == '@') {
                        // special command!
                        if (tok.sval.equalsIgnoreCase("@xgroup")) {
                            groupCommand(GROUP_CHARSET, appendTo, tok, evt, delay, count_lines);
                        } else if (tok.sval.equalsIgnoreCase("@agroup")) {
                            groupCommand(GROUP_TAXONSET, appendTo, tok, evt, delay, count_lines);
                        } else {
                            // oops ... not a command! (that we recognize, anyway)
                        }
                    } else {
                        title.append(tok.sval);
                    }
                } else title.append(type);

                if (type == StreamTokenizer.TT_EOF)
                    throw formatException(
                            tok,
                            "The title doesn't seem to have been closed properly. Are you sure the"
                                    + " final quote character is present?");
            }
        } else {
            // err, no title?
            tok.pushBack();
        }

        // finished (or, err, not) the (optional) title
        // but we NEED two numbers now

        // number of characters
        int nChars = 0;
        tok.nextToken();
        if (tok.ttype != StreamTokenizer.TT_WORD)
            throw formatException(
                    tok,
                    "Couldn't find the number of characters. I found '"
                            + (char) tok.ttype
                            + "' instead!");
        try {
            nChars = Integer.parseInt(tok.sval);
        } catch (NumberFormatException e) {
            throw formatException(
                    tok,
                    "Couldn't convert this file's character count (which is \""
                            + tok.sval
                            + "\") into a number. Are you sure it's really a number?");
        }

        // number of taxa
        int nTax = 0;
        tok.nextToken();
        if (tok.ttype != StreamTokenizer.TT_WORD)
            throw formatException(
                    tok,
                    "Couldn't find the number of taxa. I found '"
                            + (char) tok.ttype
                            + "' instead!");
        try {
            nTax = Integer.parseInt(tok.sval);
        } catch (NumberFormatException e) {
            throw formatException(
                    tok,
                    "Couldn't convert this file's taxon count (which is \""
                            + tok.sval
                            + "\") into a number. Are you sure it's really a number?");
        }

        // okay, all done ... sigh.
        // now we can fingally go into the big loop

        // In the big loop, '.'s are part of th string.
        tok.wordChars('.', '.');

        int lineno = tok.lineno();

        while (true) {
            int type = tok.nextToken();

            if (delay != null) delay.delay(lineno, count_lines);

            if (type == StreamTokenizer.TT_EOF) {
                // wtf?!
                throw formatException(
                        tok,
                        "I've reached the end of the file, but the 'xread' beginning at line "
                                + begin_at
                                + " was never terminated.");
            }

            if (type == ';')
                // it's over. Go back home, etc.
                break;

            // okay, i'm commenting out this comment handling
            // code IN CASE it's ever needed again.
            /*
            if(type == '[' || type == ']') {
            	if(type == '[')
            		commentLevel++;

            	if(type == ']')
            		commentLevel--;

            	continue;
            }

            if(commentLevel > 0)
            	continue;
            */
            if (type == StreamTokenizer.TT_WORD) {
                // word!
                String word = tok.sval;

                // now, there are some 'special'
                // words: specifically, [dna], [num]
                // [prot] and [cont]. We just
                // ignore those: the BaseSequence
                // system will figure its own
                // thing out eventually.
                if (word.equalsIgnoreCase("[dna]")
                        || word.equalsIgnoreCase("[prot]")
                        || word.equalsIgnoreCase("[num]")) {
                    // ignore!
                    continue;
                }

                if (word.equalsIgnoreCase("[cont]")) {
                    throw formatException(
                            tok,
                            "This program can currently only load files which contain discrete"
                                + " sequences. This file does not (it contains continuous data, as"
                                + " indicated by '[cont]').");
                }

                if (word.matches("^\\[.*\\]$")) {
                    throw formatException(tok, "Unrecognized data type: " + word);
                }

                // get the sequence name
                String seq_name = new String(word); // otherwise, technically, both word and seq
                // would point to tok.sval.
                seq_name = seq_name.replace('_', ' ');

                // get the sequence itself
                int tmp_type = tok.nextToken();
                if (tmp_type != StreamTokenizer.TT_WORD) {

                    throw formatException(
                            tok,
                            "I recognize sequence name '"
                                    + seq_name
                                    + "', but instead of the sequence, I find '"
                                    + (char) tok.ttype
                                    + "'. What's going on?");
                }
                String sequence = tok.sval;
                seq_names_count++;

                // add the sequence to the interleaver
                try {
                    interleaver.appendSequence(seq_name, sequence);

                } catch (SequenceException e) {
                    throw formatException(
                            tok,
                            "Sequence '"
                                    + name
                                    + "' contains invalid characters. The exact error encountered"
                                    + " was: "
                                    + e);
                }

            } else if (type == '&') {
                // indicates TNT interleaving
                // ignore!
            } else {
                throw formatException(
                        tok,
                        "I found '"
                                + (char) type
                                + "' rather unexpectedly in the xread block! Are you sure it's"
                                + " supposed to be here?");
            }
        }

        // Okay, done with this. Back to ordinaryChar with you!
        tok.ordinaryChar('.');

        // now, let's 'unwind' the interleaver and
        // check that the numbers we get match up with
        // the numbers specified in the file itself.
        Iterator i = interleaver.getSequenceNamesIterator();
        int count = 0;
        while (i.hasNext()) {
            if (delay != null) delay.delay(count, seq_names_count);
            count++;

            String seqName = (String) i.next();
            Sequence seq = interleaver.getSequence(seqName);

            if (seq.getLength() != nChars) {
                throw new FormatException(
                        "The number of characters specified in the file ("
                                + nChars
                                + ") do not match with the number of characters is sequence '"
                                + seqName
                                + "' ("
                                + seq.getLength()
                                + ").");
            }

            appendTo.add(seq);
        }

        if (count != nTax)
            throw new FormatException(
                    "The number of sequences specified in the file ("
                            + nTax
                            + ") does not match the number of sequences present in the file ("
                            + count
                            + ").");

        // only important in the xread section
        tok.ordinaryChar(gapChar);
        tok.ordinaryChar(missingChar);

        tok.ordinaryChar('.');
        tok.ordinaryChar('(');
        tok.ordinaryChar(')');
    }

    int last_group_id_used = 10000;

    /**
     * Parses a 'group' command. Group commands are relatively easy to work with; they go like this:
     * (=\d+ (\(\w+\))* (\d+)*)* ^ ^ ^ | | \---------- one or more char/taxon numbers |
     * \------------------- the name of this group (if any) \-------------------------- the number
     * of this group (not important to us, except that /=\d+/ indicates a new taxongroup starting In
     * this function, I'll use ?group or _group to indicate, err, well, /[ax]group/.
     */
    public void groupCommand(
            int which_group,
            SequenceList appendTo,
            StreamTokenizer tok,
            FormatHandlerEvent evt,
            DelayCallback delay,
            int count_lines)
            throws FormatException, DelayAbortedException, IOException {
        int begin_at = tok.lineno(); // which line did this group start at

        String current_command_name = "";
        if (which_group == GROUP_CHARSET) current_command_name = "xgroup";
        else current_command_name = "agroup";

        System.err.println("Beginning: " + current_command_name);

        // okay, we're assuming that '?group' has already started.

        // since fullstops can be used in ranges, we NEED them to be wordChars,
        // so that they turn up as a word (i.e., '23.26' is returned as '23.26',
        // not '23' '.' '26')
        tok.wordChars('.', '.');

        // brackets are word chars in xread. We need them to be non-word chars
        // here.
        tok.ordinaryChar('(');
        tok.ordinaryChar(')');

        // okay, we pop into the loop. we're looking for:
        // 	';'	-> exit
        // 	'='	-> start new group (slurp group id)
        // 	'('	-> set title for last group (slurp until terminating ')')
        // 	word	-> ought to be either:
        // 			\d+	->	one single unit to be added to the group
        // 			\d+\.\d+ ->	a range to be added. note that some programs
        // 					like spewing ranges out as consecutive numbers.
        // 					we ought to track this, watch for a line, and
        // 					then ... oh, never MIND.
        //
        // this effectively means that you can pull crazy shit like:
        // 	xgroup (ha ha!) =1 34.24 (this is weird, innit?) 13 54 (multiple titles wtf?) 24.52 =2
        // 13
        // and so on. But hey, well formed files will work just fine.
        //
        Hashtable hash_group_ids = new Hashtable(); // String id -> Sequence
        String currentName = "";

        int sequence_begin = -1;
        int sequence_end = -1;

        while (true) {
            int type = tok.nextToken();

            if (delay != null) delay.delay(tok.lineno(), count_lines);

            if (type == StreamTokenizer.TT_EOF) {
                // wtf?!
                throw formatException(
                        tok,
                        "I've reached the end of the file, but the '"
                                + current_command_name
                                + "' beginning at line "
                                + begin_at
                                + " was never terminated.");
            }

            if (type == ';')
                // it's over. Go back home, etc.
                break;

            if (type == '=') {
                // we've got a new sequence
                // BUT, first, we need to ensure that the last sequence is terminated
                // fire the last
                if (sequence_begin == -1 || sequence_end == -1) {
                    // no sequences have begun yet. booyah.
                } else {
                    if (which_group == GROUP_CHARSET) {
                        fireEvent(
                                evt.makeCharacterSetFoundEvent(
                                        currentName, sequence_begin + 1, sequence_end + 1));
                        // System.err.println("New multi-character sequence [2]: " + currentName + "
                        // from " +
                        // sequence_begin + " to " + sequence_end);
                    }
                }

                // then set up the next one
                sequence_begin = -1;
                sequence_end = -1;

                // okay, the next token ought to be a unique group id
                String group_id;
                if (tok.nextToken() != StreamTokenizer.TT_WORD) {
                    tok.pushBack();

                    // throw formatException(tok, "Expecting the group id, but found '" +
                    // (char)tok.ttype + "'
                    // instead!");
                    group_id = new Integer(++last_group_id_used).toString();
                } else {
                    group_id = tok.sval;
                }

                if (hash_group_ids.get(group_id) != null)
                    throw formatException(tok, "Duplicate group id '" + group_id + "' found!");

                // okay, set the new group id!
                hash_group_ids.put(group_id, new Integer(0));
                currentName = "Group #" + group_id;

                continue;
            } else if (type == '(') {
                // okay, now we basically read until the closing ')'
                // and store it in currentName
                StringBuffer buff_name = new StringBuffer();
                int title_began = tok.lineno();

                while (tok.nextToken() != ')') {
                    if (delay != null) delay.delay(tok.lineno(), count_lines);

                    if (tok.ttype == StreamTokenizer.TT_EOF)
                        throw formatException(
                                tok,
                                "The title which began in "
                                        + current_command_name
                                        + " on line "
                                        + title_began
                                        + " is not terminated! (I can't find the ')' which would"
                                        + " end it).");
                    else if (tok.ttype == StreamTokenizer.TT_WORD) buff_name.append(tok.sval);
                    else buff_name.append((char) tok.ttype);
                }

                currentName = buff_name.toString();

                // But wait! Some names are extra-special.
                // Note that we discard the charset name
                // at this point: any positional data is
                // collected together and used while
                // resplitting the entire file.
                String originalName = currentName;

                if (currentName.matches("^.*_posN$") || currentName.equals("posN")) {
                    currentName = ":0";
                }

                if (currentName.matches("^.*_pos1$") || currentName.equals("pos1")) {
                    currentName = ":1";
                }

                if (currentName.matches("^.*_pos2$") || currentName.equals("pos2")) {
                    currentName = ":2";
                }

                if (currentName.matches("^.*_pos3$") || currentName.equals("pos3")) {
                    currentName = ":3";
                }

                // System.err.println("Converted '" + originalName + "' -> '" + currentName + "'");

                // Now the rest of this method will keep
                // adding these positions into the special
                // codonsets. HAHA BRILLIANT.

                continue;

            } else if (type == StreamTokenizer.TT_WORD) {
                // word!
                String word = tok.sval;

                // now, this is either:
                // 1.	\d+	->	a number! submit straightaway, get on with life
                // 2.	\d+.	->	a range, from number to LENGTH_OF_SET. submit, etc.
                // 3.	.\d+	->	a range, from 0 to number. submit, etc.
                // 4.	\d+.\d+	->	a range, from num1 to num2. submit, etc.
                //
                // the trick is figuring out which one is which.
                //

                if (word.indexOf('.') == -1) {
                    // We've got a single number.

                    int locus = atoi(word, tok);

                    if (sequence_begin == -1) {
                        sequence_begin = locus;
                    }

                    if (sequence_end == -1
                            || sequence_end
                                    == locus
                                            - 1) { // if sequence_end is pointing at the LAST locus,
                        sequence_end = locus; // move it to the current position

                        // the sequence continues, so we do NOT fire now
                    } else {
                        // the sequence ends here!
                        // fire the last
                        if (which_group == GROUP_CHARSET) {
                            if (currentName.charAt(0) == ':') {
                                for (int d = sequence_begin + 1; d <= (sequence_end + 1); d++) {
                                    fireEvent(evt.makeCharacterSetFoundEvent(currentName, d, d));
                                    // System.err.println("New multicharacter sequence [6]: " +
                                    // currentName + " from "
                                    // + d + " to " + d);
                                }
                            } else {
                                fireEvent(
                                        evt.makeCharacterSetFoundEvent(
                                                currentName, sequence_begin + 1, sequence_end + 1));
                                // System.err.println("New multicharacter sequence [3]: " +
                                // currentName + " from " +
                                // sequence_begin + " to " + sequence_end);

                            }
                        }

                        // then set up the next one
                        sequence_begin = locus;
                        sequence_end = locus;
                    }

                    continue;
                } else {
                    // okay, there's a fullstop here ...
                    // sigh.
                    //
                    // you have to wonder why you bother, sometimes.

                    // one question: do we have an unfired sequence?
                    // if we do, fire it first!
                    // fire the last
                    if (which_group == GROUP_CHARSET) {
                        fireEvent(
                                evt.makeCharacterSetFoundEvent(
                                        currentName, sequence_begin + 1, sequence_end + 1));
                        // System.err.println("New multicharacter sequence [1]: " + currentName + "
                        // from " +
                        // sequence_begin + " to " + sequence_end);
                    }

                    // then set up the next one
                    sequence_begin = -1;
                    sequence_end = -1;

                    // okay, now we can fire this next bugger
                    int from = 0;
                    int to = 0;
                    if (word.charAt(0) == '.') {
                        // it's a '.\d+' or a '.'

                        if (word.length() == 1) {
                            // it's a '.'
                            from = 0;
                            to = appendTo.getMaxLength();
                        } else {
                            // it's a '.\d+'
                            from = 0;
                            to = atoi(word.substring(1), tok);
                        }

                    } else if (word.charAt(word.length() - 1) == '.') {
                        // it's at the end

                        from = atoi(word.substring(0, word.length() - 1), tok);
                        to = appendTo.getMaxLength();
                    } else {
                        // it's in the middle
                        int indexOf = word.indexOf('.');

                        from = atoi(word.substring(0, indexOf - 1), tok);
                        to = atoi(word.substring(indexOf + 1), tok);
                    }

                    if (which_group == GROUP_CHARSET) {
                        from++; // convert from TNT loci (0-based) to normal loci (1-based)
                        to++;

                        // Multi-character blocks are POISON for situations where
                        // the sequences are supposed to move in thirds (i.e. ':1',
                        // ':2' and ':3'. This is because the algorithm for figuring
                        // out position information is designed to work with

                        fireEvent(evt.makeCharacterSetFoundEvent(currentName, from, to));
                        // System.err.println("New multi-character block [4]: " + currentName + "
                        // from " + from
                        // + " to " + to);
                    }

                    continue;
                }

            } else {
                throw formatException(
                        tok,
                        "I found '"
                                + (char) type
                                + "' rather unexpectedly in the "
                                + current_command_name
                                + " command beginning on line "
                                + begin_at
                                + "! Are you sure it's supposed to be here?");
            }
        }

        // do we have an incomplete sequence?
        if (sequence_begin != -1 && sequence_end != -1) {
            // fire the last
            if (which_group == GROUP_CHARSET) {
                if (currentName.charAt(0) == ':') {
                    for (int d = sequence_begin + 1; d <= sequence_end + 1; d++) {
                        fireEvent(evt.makeCharacterSetFoundEvent(currentName, d, d));
                        // System.err.println("New multicharacter sequence [7]: " + currentName + "
                        // from " +
                        // sequence_begin + " to " + sequence_end);
                    }
                } else {
                    fireEvent(
                            evt.makeCharacterSetFoundEvent(
                                    currentName, sequence_begin + 1, sequence_end + 1));
                    // System.err.println("New multicharacter sequence [5]: " + currentName + " from
                    // " +
                    // sequence_begin + " to " + sequence_end);
                }
            }
        }

        // Restore '.' to its usual position as a character.
        tok.ordinaryChar('.');

        // Restore brackets to their magical significance in the xread.
        tok.wordChars('(', ')');
        tok.wordChars(')', ')');
    }

    private int atoi(String word, StreamTokenizer tok) throws FormatException {
        try {
            return Integer.parseInt(word);
        } catch (NumberFormatException e) {
            throw formatException(tok, "Could not convert word '" + word + "' to a number: " + e);
        }
    }

    /**
     * Writes the content of this sequence list into a file. The file is overwritten. The order of
     * the sequences written into the file is guaranteed to be the same as in the list.
     *
     * @throws IOException if there was a problem creating/writing to the file.
     * @throws DelayAbortedException if the DelayCallback was aborted by the user.
     */
    public void writeFile(File file, SequenceList set, DelayCallback delay)
            throws IOException, DelayAbortedException {
        writeTNTFile(file, set, 0, "", delay);
    }

    /**
     * A species TNTFile-only method to have a bit more control over how the Nexus file gets
     * written.
     *
     * @param interleaveAt Specifies where you want to interleave. Note that TNTFile.INTERLEAVE_AT
     *     will be entirely ignored here, and that if the sequence is less than interleaveAt, it
     *     will not be interleaved at all. '-1' turns off all interleaving (flatline), although you
     *     can obviously use a huge value (999999) to get basically the same thing.
     * @param otherBlocks We put this into the file at the very bottom. It should be one or more
     *     proper 'BLOCK's, unless you really know what you're doing.
     */
    public void writeTNTFile(
            File file, SequenceList set, int interleaveAt, String otherBlocks, DelayCallback delay)
            throws IOException, DelayAbortedException {
        boolean interleaved = false;

        // Check if interleaveAt is valid.
        if (interleaveAt > 0 && interleaveAt < set.getMaxLength()) interleaved = true;

        // Time to start working.
        set.lock();
        if (delay != null) delay.begin();

        // Start writing to file.
        PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));

        // First, we write out the TNT preamble.
        writer.println("nstates 32;"); // give our data the best possible chance
        // TODO: Replace '32' with 'dna' once we properly support molecular data.

        writer.println("xread"); // begin sequence output
        writer.println(
                "'Written by TaxonDNA " + Versions.getTaxonDNA() + " on " + new Date() + "'");

        // Write out the maximum length and sequence count
        writer.println(set.getMaxLength() + " " + set.count());
        writer.println(""); // leave a blank line for the prettyness

        /*
         * Time to write out some sequences.
         */

        /*
         * First, we need to ensure that every sequence in this file is actually unique.
         * For now, we've gotten rid of the make-sure-sequence-names-are-less-than-X-chars
         * code, but this is the place to reintroduce them if necessary.
         */
        HashMap<String, Sequence> names = new HashMap<String, Sequence>();
        // HashMap(String name) -> Sequence
        //	hash of all the names currently in use
        Vector vec_names = new Vector(); // Vector(String)
        Iterator i = set.iterator();
        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();
            String name = getTNTName(seq.getFullName());
            name = name.replace(' ', '_'); // we do NOT support ' '. Pfft.
            String new_name = name;
            int name_index = 1;
            while (names.containsKey(new_name)) {
                // Uhoh, duplicate name. Put _1, _2, etc. at the end.
                new_name = name + "_" + name_index;
                name_index++;
            }
            names.put(new_name, seq);
            vec_names.add(new_name);
        }

        // TODO: On the subject of writing out sequences:
        // With nstates=32, we can use upto 32 states: 0-9 and A-V.
        // So we should probably check for W,X,Y,Z, and produce an
        // error.

        // Now that we have the names (and their order), time to write them out.
        if (!interleaved) {
            // In this case, we just need to write them out in the order of
            // vec_names.

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
        } else {
            // Ah, interleaved. We need to write out each segment.

            for (int x = 0; x < set.getMaxLength(); x += interleaveAt) {
                Iterator i_names = vec_names.iterator();

                // report the delay
                if (delay != null) {
                    try {
                        delay.delay(x, set.getMaxLength());
                    } catch (DelayAbortedException e) {
                        writer.close();
                        set.unlock();
                        throw e;
                    }
                }

                // the TNT standard (ha!) requires an '&' in between blocks.
                writer.println("&");

                // go over all the taxa
                while (i_names.hasNext()) {
                    String name = (String) i_names.next();
                    Sequence seq = (Sequence) names.get(name);
                    Sequence subseq = null;
                    int until = 0;
                    try {
                        until = x + interleaveAt;
                        // thanks to the loop, we *will* walk off the end of this
                        if (until > seq.getLength()) {
                            until = seq.getLength();
                        }
                        subseq = seq.getSubsequence(x + 1, until);

                    } catch (SequenceException e) {

                        delay.end();
                        throw new IOException(
                                "Could not get subsequence ("
                                        + (x + 1)
                                        + ", "
                                        + until
                                        + ") from sequence "
                                        + seq
                                        + ". This is most likely a programming error.");
                    }

                    writer.println(name + " " + subseq.getSequence());
                }
            }
        }

        writer.println(";"); // the semicolon ends the 'xread' command.

        /*
         * Writing of sequences completed! Now let's write out CODONPOSSETs.
         */

        // Here's the thing: each sequence has its own positional
        // information, which (we assume) is consistent within a
        // column (which is a pretty safe assumption from now,
        // as we only accept positional data from Nexus and TNT,
        // neither of which support per-sequence positional data).

        // Unfortunately, we'd like to produce only a single set
        // of positional data for the entire dataset. To simplify
        // things, we create three strings, one for each position;
        // concatenate each column's positional information, and
        // then combine them at the end.

        // Note (this being an important point): we only use the
        // FIRST taxon in the table to determine CODONPOSSET
        // information to be emitted.

        // This is very likely indeed to work!
        StringBuffer[] array_strbuff_positions = new StringBuffer[4];
        array_strbuff_positions[0] = new StringBuffer();
        array_strbuff_positions[1] = new StringBuffer();
        array_strbuff_positions[2] = new StringBuffer();
        array_strbuff_positions[3] = new StringBuffer();

        // get the first sequence
        Sequence seq = (Sequence) set.get(0);

        // Note: if you change this x = 0, you'll get 'N' as
        // well. Since we don't want this right now, we're
        // turning it off. Turn it on whenever.
        for (int x = 1; x <= 3; x++) {
            // For each position (1..3), create a vector of loci in
            // that position.
            Vector v = (Vector) seq.getProperty("position_" + x);

            if (v != null) {
                // Iterate over every FromToPair and add it to
                // array_strbuff_positions[x].

                Iterator i_v = v.iterator();
                while (i_v.hasNext()) {
                    FromToPair ftp = (FromToPair) i_v.next();
                    // buff_nexus_positions.append("[" + horzOffset + "] (" + ftp.from + ") - (" +
                    // ftp.to +
                    // ")" + str_end);

                    // How much do we increment by? '1' for N, '3' for 1,2,3.
                    int increment_by = 1;
                    if (x == 1 || x == 2 || x == 3) increment_by = 3;

                    // Note those -1s! They're to change our 1-index based calculations
                    // into 0-based TNT coordinates.

                    if (ftp.from == ftp.to) {
                        // array_strbuff_positions[x].append("single(" + ftp + ")->[");
                        // Single position loci. Write them out.
                        array_strbuff_positions[x].append((ftp.from - 1) + " ");
                        // array_strbuff_positions[x].append("]nosingle ");
                    } else {
                        // Multiloci. Count from 'from' to 'to', displaying each one.
                        // array_strbuff_positions[x].append("multi(" + ftp + ")->[");
                        for (int y = (ftp.from); y <= (ftp.to); y += increment_by) {
                            array_strbuff_positions[x].append((y - 1) + " ");
                        }
                        // array_strbuff_positions[x].append("]nomulti ");
                    }
                }
            }
        }

        // Now write them all out for later writing.
        StringBuffer buff_tnt_positions = new StringBuffer();

        // Change zero-length strings to null.
        for (int x = 0; x <= 3; x++) {
            if (array_strbuff_positions[x].length() == 0) array_strbuff_positions[x] = null;
        }

        String position_names[] = {"N", "1", "2", "3"};

        // Write out positional information as xgroups, skipping any position
        // without any information.
        int colid = 0;
        for (int x = 1; x <= 3; x++) {
            if (array_strbuff_positions[x] != null) {
                buff_tnt_positions.append(
                        "="
                                + colid
                                + " (pos"
                                + position_names[x]
                                + ") "
                                + array_strbuff_positions[x]
                                + "\n");
                colid++;
            }
        }

        // Write out buff_tnt_positions, but only if there is anything in it.
        if (buff_tnt_positions.length() > 0) {
            // buff_tnt_positions.append("'*** The following is positional information for this
            // dataset.
            // ***\n");
            writer.println("xgroup\n" + buff_tnt_positions.toString() + "\n;\n\n");
        }

        // Write out any other blocks.
        if (otherBlocks != null) writer.println(otherBlocks);

        writer.close();

        // Processing complete. Time to cleanup.
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
        try {
            BufferedReader buff = new BufferedReader(new FileReader(file));

            while (buff.ready()) {
                String str = buff.readLine().trim();

                if (str.equals("")) continue;

                if (str.toLowerCase().indexOf("xread") != -1) {
                    // we find xread!
                    // we don't know if its parseable, but ... well.
                    // *shrugs*
                    // </irresponsibility>
                    //
                    // TODO Make this actually work, etc.
                    return true;
                }
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Given a column name, this code will turn it into a valid CODONPOSSET (or filename).
     *
     * @param columnName The column name you'd like to fix.
     * @return A sanitized, cleaned-up version of this column name.
     */
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
