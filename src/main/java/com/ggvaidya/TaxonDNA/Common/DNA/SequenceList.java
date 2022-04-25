/**
 * SequenceList is a class to organise sequences into a "list". It *is* a list (an ordered set of
 * elements).
 *
 * <p>SequenceLists are either created independently and filled (using the null constructor) or
 * loaded in from a file. Loading from a file can throw IOExceptions (in case of IO-issues) as well
 * as SequenceListExceptions. SequenceListException wraps most other exceptions. If you want the
 * full detailed error message, you are free to use the static readFile() function.
 *
 * <p>I'm obviously in several minds over this, and I'm not sure how this is going to end up. Assume
 * all mixed up until further notice.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2005, 2007	Gaurav Vaidya

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
import com.ggvaidya.TaxonDNA.Common.DNA.formats.*;
import java.io.*; // Input/output
import java.util.*; // Hashtables

public class SequenceList implements List, Testable {
    // variables essential to this class
    private LinkedList sequences =
            new LinkedList(); // specified solely by order (based on sortedBy)
    private SpeciesDetails details = null; // the species summary for this SequenceList
    private File file = null; // the file we came from
    private FormatHandler formatHandler = null; // the formathandler used to extract this file
    private int sortedBy = 0; // the order in which we are (currently) sorted
    private boolean modified = false; // has this sequencelist been modified?

    // Hashtable to store species name -> Integer(index)
    //
    private Hashtable ht_species = new Hashtable();

    // constants for the sorting mechanism (i.e. what can be stored in sortedBy)
    public static final int SORT_UNSORTED = 0;
    public static final int SORT_BYNAME = 1;
    public static final int SORT_BYSIZE = 2;
    public static final int SORT_BYGI = 3;
    public static final int SORT_BYFAMILY = 4;
    public static final int SORT_RANDOM = 5;
    public static final int SORT_RANDOM_WITHIN_SPECIES = 6;
    public static final int SORT_PROPORTION_AMBIGUOUS = 7;

    // DEBUG variable: when set, we'll print out a fairly
    // verbose stream of messages after every lock() and
    // unlock() call. Allows you to figure it out whether
    // your lock()/unlock()s are unmatched.
    //
    private static final boolean DEBUG_LOCK = false;

    //
    // 	1. 	STATIC FUNCTIONS. SequenceList maintains the code which allows for
    // 		multiple format input/output, since a SequenceList is tied into
    // 		a particular file and FormatHandler. Thus, the list of formatHandlers
    // 		(retrievable through 'getFormatHandlers()') can only be got out
    // 		of this class. initFormatHandlers() must be called to set up the
    // 		handlers.
    //
    private static Vector formatHandlers = new Vector();
    private static String formatsHandled = "";

    /** Returns a vector containing all format handlers known to us right now. */
    public static Vector getFormatHandlers() {
        if (formatHandlers.size() == 0) initFormatHandlers();
        return formatHandlers;
    }

    /** Initialises the whole format-handler mechanism. */
    private static void initFormatHandlers() {
        if (formatHandlers.size() == 0) {
            formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.FastaFile());
            // formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.MegaFile());
            formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.NexusFile());
            formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.TNTFile());
            //			formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.SequencesFile());
            //			formatHandlers.add(new com.ggvaidya.TaxonDNA.Common.DNA.formats.PhylipFile());

            formatsHandled = "Fasta, Mega, Nexus, TNT and Sequences";
        }
    }

    //
    //	2. 		CONSTRUCTORS. A SequenceList can be constructed empty,
    //			but it is prefered if you specify a file and formatHandler
    //			to load up from. All constructors MUST call the null
    //			constructor, which handles setting up the formatHandlers.
    //
    /** Creates an empty SequenceList. All we do is set up the formatHandlers. */
    public SequenceList() {
        initFormatHandlers();
    }

    /**
     * Creates a SequenceList from the specified file using the specified formatHandler. Was there
     * ever a more elegant way to create a SequenceList? No, there cannot be.
     *
     * @throws SequenceListException if any exception was thrown creating this SequenceList. It
     *     wraps the exception and provides a useful message at the same time.
     * @throws DelayAbortedException if the DelayCallback was aborted by the user.
     */
    public SequenceList(File file, FormatHandler handler, DelayCallback delay)
            throws SequenceListException, DelayAbortedException {
        initFormatHandlers(); // set up the formatHandlers

        String fullPath = file.getAbsolutePath();

        try {
            if (handler.mightBe(file)) handler.appendFromFile(this, file, delay);
            else
                throw new SequenceListException(
                        "The specified file '"
                                + fullPath
                                + "' does not seem to be in the "
                                + handler.getShortName()
                                + " format. Try to use another import method to load this file, use"
                                + " 'Open' to let this program try to guess the file format (it can"
                                + " understand "
                                + formatsHandled
                                + " formats), or convert it into one of these formats "
                                + "before loading them up again.");
        } catch (IOException e) {
            throw new SequenceListException(
                    "A system error occured while trying to read '"
                            + fullPath
                            + "'. Are you sure that the file exists, "
                            + "and you have the correct permissions to read it?\n"
                            + "Technical explanation: "
                            + e,
                    e);

        } catch (SequenceException e) {
            throw new SequenceListException(
                    "At least one of the sequences in the file (with the name '"
                            + e.getMalformedSequenceName()
                            + "') has a problem with it. "
                            + "Please check the sequence and ensure that it does not contain "
                            + "illegal characters (such as '_').\nThe error reported is: "
                            + e,
                    e);

        } catch (FormatException e) {
            throw new SequenceListException(
                    "There is an error in the format for file '"
                            + fullPath
                            + "'. Please check the format and ensure that the format is "
                            + "correct.\nThe error reported is: "
                            + e,
                    e);
        }

        modified = false; // we are being backed by a valid file, and Everything is Okay.
    }

    /**
     * Creates a SequenceList from a Collection of Sequences. Any collection will do (we just feed
     * it into the addAll anyway). Note that this will leave the file as 'null', so you'll have to
     * use changeFile(), etc. to make this SequenceList feel like it *belongs* somewhere.
     */
    public SequenceList(Collection c) {
        initFormatHandlers(); // set up the formatHandlers
        // i don't care,
        file = null; // 	who you are
        formatHandler = null; // 	where you're from
        //	what you did
        addAll(c); // as long as you add 'c'

        modified();
    }

    //
    //	3.	CLASSES. Our Comparator classes are here, allowing for various
    //		types of sorts. Resorting is carried out by calling 'resort(int)',
    //		specified further below.
    //
    /** A Comparator class which sorts Sequences by name. */
    private class SortByName implements Comparator {
        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            return seq1.compareByDisplayName(seq2);
        }
    }

    /** A Comparator class which sorts Sequences by size, largest sequence first. */
    private class SortBySize implements Comparator {
        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            return seq2.compareByActualLength(seq1);
        }
    }

    private class SortByGi implements Comparator {
        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            if (seq1.getGI().equals("")) // go with 2! whatever it is!
            return -1;

            if (seq2.getGI().equals("")) // go with 1! whatever it is!
            return +1;

            return Integer.valueOf(seq1.getGI()).compareTo(Integer.valueOf(seq2.getGI()));
        }
    }

    private class SortByFamily implements Comparator {
        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            if (seq1.getFamilyName().equals(""))
                if (seq2.getFamilyName().equals(""))
                    // if they're BOTH "", we don't care.
                    return 0;
                else // go with 2! whatever it is!
                return +1;

            if (seq2.getFamilyName().equals("")) // go with 1! whatever it is!
            return -1;

            return seq1.getFamilyName().compareTo(seq2.getFamilyName());
        }
    }

    private class SortByProportionAmbiguous implements Comparator {
        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            double prop1 = ((double) seq1.getAmbiguous()) / seq1.getActualLength();
            double prop2 = ((double) seq2.getAmbiguous()) / seq2.getActualLength();

            return (int) ((prop2 - prop1) * 1000000);
        }
    }

    private class SortRandom implements Comparator {
        private int type = SORT_RANDOM;

        /**
         * Assign a random number to each and every Sequence on the list.
         *
         * @param type Either SORT_RANDOM or SORT_RANDOM_WITHIN_SPECIES, depending on what you want.
         */
        public SortRandom(SequenceList list, int type) {
            Iterator i = list.iterator();
            Random random = new Random();
            Hashtable used = new Hashtable();

            this.type = type;

            while (i.hasNext()) {
                Sequence seq = (Sequence) i.next();

                int next_rand;
                do {
                    next_rand = random.nextInt();
                } while (used.get(new Integer(next_rand)) != null);

                seq.setProperty("SequenceList.SortRandom.random", new Integer(next_rand));
            }
        }

        /*
         * 	Is the extra code worth it?
        public void	cleanup(SequenceList list) {
        	Iterator i = list.iterator();

        	while(i.hasNext()) {
        		Sequence seq = (Sequence) i.next();

        		seq.setProperty("SequenceList.SortRandom.random", null);
        	}
        }
        */

        public int compare(Object o1, Object o2) {
            Sequence seq1 = (Sequence) o1;
            Sequence seq2 = (Sequence) o2;

            if (type == SORT_RANDOM_WITHIN_SPECIES) {
                // only randomize WITHIN species name
                int diff = seq1.getSpeciesName().compareTo(seq2.getSpeciesName());
                if (diff != 0) return diff;
            }

            // they're conspecific!
            int i1 = ((Integer) seq1.getProperty("SequenceList.SortRandom.random")).intValue();
            int i2 = ((Integer) seq2.getProperty("SequenceList.SortRandom.random")).intValue();

            // System.err.println("Comparing " + i1 + " and " + i2);

            return i2 - i1;
        }
    }

    //
    //	4.	MANIPULATORS. Classes which change the very nature of this class.
    //
    /**
     * Resorts this class using the specified sorting scheme. The sorting schemes are integer
     * constants of this class. There is no way to determine how this class is sorted, because I
     * can't think of why anyone would want to do this. You just call 'resort' to whatever you want;
     * we're smart enough not to resort if we're already sorted that way. Of course, this means
     * that: 1. Once sorted, we must maintain the sort in all additions/deletions. (oh dear, what a
     * pain! - but what rewards! Eh. No time. Really.) 2. Once UNsorted, we need to make sure that
     * sortMethod HAS been reset.
     *
     * @return The last sorting strategy used (before your resort call)
     */
    public int resort(int sortMethod) {
        int toReturn = sortedBy;

        lock();

        if (sortMethod == SORT_RANDOM
                || sortMethod == SORT_RANDOM_WITHIN_SPECIES
                || sortedBy != sortMethod) {
            // don't bother unless the sort method _changed_
            // or it's a SORT_RANDOM, which must ALWAYS
            // be resorted

            Comparator c = null;
            switch (sortMethod) {
                case SORT_BYSIZE:
                    c = new SortBySize();
                    break;
                case SORT_BYGI:
                    c = new SortByGi();
                    break;
                case SORT_BYFAMILY:
                    c = new SortByFamily();
                    break;
                case SORT_RANDOM:
                case SORT_RANDOM_WITHIN_SPECIES:
                    c = new SortRandom(this, sortMethod);
                    break;
                case SORT_PROPORTION_AMBIGUOUS:
                    c = new SortByProportionAmbiguous();
                    break;

                case SORT_BYNAME:
                default:
                    c = new SortByName();
                    break;
            }

            Collections.sort(sequences, c); // sort 'em!
            c = null; // try and trigger the gc

            sortedBy = sortMethod;
        }

        unlock();

        return toReturn;
    }

    //
    //	5.	SEQUENCE LOCKING/UNLOCKING. Since we have a set of classes, all of whom
    //		need to be able to use a SequenceList at roughly the same time, we need
    //		some way to avoid confusion. We're synchronizing on a special, static
    //		'lock' object: you must call lock() before you do anything to a string,
    //		and unlock() once you're done.
    //
    //		Bear in mind that TaxonDNA.lock/unlockSequenceList will act a proxy for
    //		you. The advantage of using that is:
    //			a) 	the sequence list will be locked anyways, and
    //			b) 	you can call unlockSequenceList(list) to CHANGE
    //			   	the list which TaxonDNA holds.
    //
    //		If you're only going to modify the *contents* of the SequenceList,
    //		feel free to call lock()/unlock() around where you do this to stop
    //		strange things from happening. It's probably also a good idea to lock
    //		on the SequenceList while modifying Sequences *in* the SequenceList.
    //
    private static Object sequenceListLock = new Object(); // The <Object> we synchronize on
    private Runnable threadLockingUs = null; // The <Thread> who currently holds the lock
    private int sequenceListLockCount = 0; // The number of locks issued (by the
    // threadLockingUs); we're only unlocked
    // once ALL of these have been 'unwound'
    // by unlock()

    /**
     * Locks this sequence list. There is no compulsion, but this is highly recommended in any
     * multithreaded environment. We're going to try and implement this ourself in all our internal
     * functions (e.g. resort), but you should too.
     */
    public void lock() {
        if (DEBUG_LOCK)
            System.err.println(
                    " ["
                            + sequenceListLockCount
                            + "] Locking sequence list: "
                            + Thread.currentThread()
                            + " called from "
                            + new Throwable().getStackTrace()[1]);

        synchronized (sequenceListLock) {
            // if tLSS = null:		go thru
            // if tLSS = otherThread:	loop indefinately until ssl = null
            // if tLSS = currentThread:	go thru
            while (threadLockingUs != Thread.currentThread() && threadLockingUs != null) {
                try {
                    sequenceListLock.wait();
                } catch (InterruptedException e) {
                }
            }
            threadLockingUs = Thread.currentThread();
            sequenceListLockCount++;

            sequenceListLock.notifyAll();
        }

        if (DEBUG_LOCK)
            System.err.println(
                    " ["
                            + sequenceListLockCount
                            + "] Sequence list locked: "
                            + Thread.currentThread()
                            + " called from "
                            + new Throwable().getStackTrace()[1]);
    }

    /** Unlock sequence list. We release the lock. Goodbye, lock! (Lock says: bye!) */
    public void unlock() {
        if (DEBUG_LOCK)
            System.err.println(
                    " ["
                            + sequenceListLockCount
                            + "] Unlocking sequence list: "
                            + Thread.currentThread()
                            + " called from "
                            + new Throwable().getStackTrace()[1]);

        synchronized (sequenceListLock) {
            sequenceListLockCount--;
            if (sequenceListLockCount != 0) return;

            // unlock
            threadLockingUs = null;
            sequenceListLock.notifyAll();
        }

        if (DEBUG_LOCK)
            System.err.println(
                    " ["
                            + sequenceListLockCount
                            + "] Sequence list unlocked: "
                            + Thread.currentThread()
                            + " called from "
                            + new Throwable().getStackTrace()[1]);
    }

    //
    //	6.	The following section reads SequenceLists out of files. We figure out which handler to
    // use,
    //		and pass them on to the SequenceList() constructors. It's all good. Since these are static
    //		methods, you can call them to create SequenceList directly - like easy-to-use constructors!
    //		Too bad constructors aren't any good for that ...
    //
    /**
     * Loads up a SequenceList from the specified file using the specified formatHandler and
     * providing feedback via the specified DelayCallback.
     */
    public static SequenceList readFile(File f, FormatHandler h, DelayCallback delay)
            throws SequenceListException, DelayAbortedException {
        initFormatHandlers();
        return new SequenceList(f, h, delay);
    }

    /**
     * Loads up a SequenceList from the specified file. We will "intelligently" (ha!) try to
     * determine the type of file, by running through the various FormatHandler.mightBe() functions.
     * There are many holes to this, but on the whole it works, and even if it doesn't, we'll have
     * prominent Export/Import systems.
     *
     * <p>Oh, and it makes a nice list of the format handlers it *does* support, so you have a
     * better idea about what's going on, and so on.
     */
    public static SequenceList readFile(File file, DelayCallback delay)
            throws SequenceListException, DelayAbortedException {
        return readFile(file, delay, null);
    }

    /**
     * Loads up a SequenceList from the specified file. We will "intelligently" (ha!) try to
     * determine the type of file, by running through the various FormatHandler.mightBe() functions.
     * There are many holes to this, but on the whole it works, and even if it doesn't, we'll have
     * prominent Export/Import systems.
     *
     * <p>Oh, and it makes a nice list of the format handlers it *does* support, so you have a
     * better idea about what's going on, and so on.
     */
    public static SequenceList readFile(File file, DelayCallback delay, FormatListener listener)
            throws SequenceListException, DelayAbortedException {
        initFormatHandlers();

        // First we figure out which formatHandler to use.
        Iterator i = formatHandlers.iterator();
        String fullPath = file.getAbsolutePath();
        StringBuffer validFormats = new StringBuffer();

        while (i.hasNext()) {
            FormatHandler handler = (FormatHandler) i.next();

            if (handler.mightBe(file)) {
                if (listener != null) handler.addFormatListener(listener);
                return new SequenceList(file, handler, delay);
            }

            if (i.hasNext()) validFormats.append(handler.getShortName() + ", ");
            else validFormats.append("or " + handler.getShortName());
        }

        // if we're here, we couldn't find a working handler
        throw new SequenceListException(
                "I could not understand the input file '"
                        + fullPath
                        + "'. It does not appear to be a "
                        + validFormats.toString()
                        + " file, which are the only types of file I can read. Please check the"
                        + " format of the file, or convert it into one of the formats I can read.");
    }

    //
    //	7.		RETRIEVALS. Gets values from this SequenceList.
    //
    /**
     * Has this SequenceList been modified? TODO: This mechanism needs writing up, if it's any use,
     * or to be got rid of, if it isn't.
     */
    public boolean isModified() {
        return modified;
    }

    /** Returns the number of Sequences in this sequence set. */
    public int count() {
        return sequences.size();
    }

    /** Ditto. Except some people are odd in their English ... */
    public int size() {
        return sequences.size();
    }

    /** Gets the file this sequence set is associated with. */
    public File getFile() {
        return file;
    }

    /** Gets the format handler this sequence set is associated with. */
    public com.ggvaidya.TaxonDNA.Common.DNA.formats.FormatHandler getFormatHandler() {
        return formatHandler;
    }

    /** Is this an empty set? */
    public boolean isEmpty() {
        return sequences.isEmpty();
    }

    /** Does this contain this Sequence?. TODO: Can be optimized. */
    public boolean contains(Object o) {
        Sequence seq = (Sequence) o;
        return sequences.contains(seq);
    }

    /**
     * Returns an iterator over all the sequences currently in our sequencesbase. This is a normal,
     * fail-fast iterator. I don't think it's guaranteed to be in order, but I think it is
     * guaranteed not to repeat elements. I think. Don't look at me, I'm just using
     * Vector.iterator() for now.
     */
    public Iterator iterator() {
        return sequences.iterator();
    }

    /**
     * Returns a conspecificIterator using the species name instead of a Sequence. It does this by
     * searching for the first matching sequence.
     *
     * <p>TODO: Optimize this if required.
     *
     * @return An iterator to all species named 'speciesName' in this list, or 'null' if there are
     *     no such species.
     */
    public Iterator conspecificIterator(String speciesName) {
        lock();
        resort(SORT_BYNAME);

        Sequence seq = null;

        int index = 0;

        if (ht_species.get(speciesName) != null) {
            index = ((Integer) ht_species.get(speciesName)).intValue();
            seq = (Sequence) this.get(index);
        } else {
            String last_spName = null;

            while (index < sequences.size()) {
                seq = (Sequence) sequences.get(index);
                if (seq != null) {
                    if (seq.getSpeciesName().equals(speciesName)) break;

                    if (last_spName == null || !last_spName.equals(seq.getSpeciesName())) {
                        // new species!
                        ht_species.put(seq.getSpeciesName(), new Integer(index));
                    }

                    last_spName = seq.getSpeciesName();
                }
                index++;
            }

            if (seq == null) {
                unlock();
                return null;
            }

            ht_species.put(speciesName, new Integer(index));
        }

        //	System.err.println("Freemem = " + Runtime.getRuntime().freeMemory());
        //	System.err.println("CI: " + seq + " at " + index + " to <" + this + ">");

        Iterator i = new ConspecificIterator(this, seq, index);

        unlock();
        return i;
    }

    /**
     * Returns an iterator which will only iterate over the conspecifics of the specified Sequence.
     * Note that this Sequence must actually be IN this sequence list. To search by species name,
     * use the slighly slower conspecificIterator(String speciesName).
     *
     * <p>PLEASE remember to lock the sequence before you play around with a conspecificIterator!
     * Otherwise, the sequence might get resorted under your thumb, which could cause you lots and
     * lots of angst. public Iterator conspecificIterator(Sequence seq) { lock();
     * resort(SORT_BYNAME); // so we're going by name
     *
     * <p>int index = indexOf(seq); if(index == -1) { // this sequence does not exist return null; }
     *
     * <p>ConspecificIterator i = ; unlock();
     *
     * <p>return i; }
     */
    /** Returns an Array version of this list */
    public Object[] toArray() {
        return sequences.toArray();
    }

    /** Returns an Array version of this list */
    public Object[] toArray(Object[] obj) {
        return sequences.toArray(obj);
    }

    /** Determine the length of the largest of these Sequences */
    public int getMaxLength() {
        Iterator i = iterator();
        int maxLength = 0;

        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();

            if (seq.getLength() > maxLength) maxLength = seq.getLength();
        }

        return maxLength;
    }

    /** Compares the two objects (sets?) for equality */
    public boolean equals(Object o) {
        if (!getClass().equals(o.getClass())) return false;

        SequenceList set = (SequenceList) o;

        if (set.size() != size()) return false;

        Iterator i = set.iterator();
        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();

            if (!contains(seq)) return false;
        }

        return true;
    }

    /** Returns the SpeciesDetails; if there isn't one, make one up first. */
    public SpeciesDetails getSpeciesDetails(DelayCallback delay) throws DelayAbortedException {
        lock();

        // An innoculous little optimization? Nope!
        // If you uncomment this next line, delay.end() doesn't
        // get called correctly, and the ProgressDialog stays
        // active. TODO, if anyone's interested.
        //
        // if(details == null)
        try {
            details = new SpeciesDetails(this, delay);
        } catch (DelayAbortedException e) {
            unlock();
            throw e;
        }

        unlock();

        return details;
    }

    /** Returns a (unique!) hashCode for this SequenceList */
    public int hashCode() {
        return sequences.hashCode();
    }

    public int lastIndexOf(Object o) {
        Sequence seq = (Sequence) o;

        return sequences.lastIndexOf(seq);
    }

    public int indexOf(Object o) {
        Sequence seq = (Sequence) o;

        return sequences.indexOf(seq);
    }

    public Object get(int x) {
        return sequences.get(x);
    }

    public List subList(int from, int to) {
        return sequences.subList(from, to);
    }

    /** Returns a string summary of this object. */
    public String toString() {
        return "SequenceList(contains "
                + sequences.size()
                + " sequences, sorted by method "
                + sortedBy
                + ")";
    }

    //
    //	8.	SETTERS. Set values in this file (not the same as *modifying* the list, mentioned above)
    //

    /**
     * Somebody modified either this dataset itself (add, remove, etc.), or one of the sequences
     * which comprise us.
     */
    public void modified() {
        modified = true;
        sortedBy = SORT_UNSORTED;
        details = null;
        ht_species = new Hashtable();
    }

    /** Sets the file this sequence list is associated with. */
    public void setFile(File file) {
        if (this.file != null && !file.equals(this.file)) { // if the two files are different
            modified();
        }
        this.file = file;
    }

    /** Sets the format handler this sequence set is associated with. */
    public void setFormatHandler(com.ggvaidya.TaxonDNA.Common.DNA.formats.FormatHandler handler) {
        this.formatHandler = handler;
    }

    /** Adds a Sequence to this SequenceList. */
    public boolean add(Sequence seq) {
        if (sequences.add(seq)) {
            sortedBy = SORT_UNSORTED;
            modified();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Adds a Sequence to this SequenceSet. This is only a stub to handle the conversion: we really
     * do prefer add(Sequence).
     */
    public boolean add(Object o) {
        Sequence seq = (Sequence) o;

        return add(seq);
    }

    /** Removes a Sequence from this SequenceSet. */
    public boolean remove(Object o) {
        Sequence seq = (Sequence) o;

        if (sequences.remove(seq)) {
            modified();
            return true;
        }
        return false;
    }

    public boolean addAll(int index, Collection c) {
        throw new UnsupportedOperationException();
    }

    public void add(int x, Object o) {
        sequences.add(x, o);
        sortedBy = SORT_UNSORTED;
        modified();
    }

    public Object remove(int x) {
        Object o = null;
        if ((o = sequences.remove(x)) != null) modified();

        return o;
    }

    /** Checks whether this SequenceSet contains all the elements in this collection */
    public boolean containsAll(Collection c) {
        Iterator i = c.iterator();
        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();

            if (!contains(seq)) return false;
        }
        return true;
    }

    /** Adds all Sequences in the collection to this SequenceList. */
    public boolean addAll(Collection c) {
        Iterator i = c.iterator();
        boolean changed = false;

        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();

            if (add(seq)) changed = true;
        }

        if (changed) {
            sortedBy = SORT_UNSORTED;
            modified();
        }

        return changed;
    }

    /**
     * Retains only the elements in this collection; i.e. intersection of this and Collection c.
     * Right now, this is unimplemented, until someone can figure out what it's good for.
     */
    public boolean retainAll(Collection c) {
        throw new UnsupportedOperationException(
                "If you need SequenceSet to do a retainAll(), you'll have to write it yourself!");
    }

    /** Removes all the elements in this set which are contained in the other set. */
    public boolean removeAll(Collection c) {
        Iterator i = c.iterator();
        boolean changed = false;

        while (i.hasNext()) {
            Sequence seq = (Sequence) i.next();

            if (remove(seq)) changed = true;
        }
        return changed;
    }

    /** Removes all the elements in this set */
    public void clear() {
        sequences.clear();
    }

    /** Sets a particular element in our List, by (zero-based) index */
    public Object set(int x, Object o) {
        Sequence seq = (Sequence) o;
        Sequence last = (Sequence) sequences.set(x, seq);
        sortedBy = SORT_UNSORTED;
        return last;
    }

    public ListIterator listIterator() {
        return sequences.listIterator();
    }

    public ListIterator listIterator(int index) {
        return sequences.listIterator(index);
    }

    //
    //	9.	ACTION FUNCTIONS. Functions which do things.
    //

    /** Writes this sequence set to its file. */
    public void writeToFile(DelayCallback delay) throws IOException, DelayAbortedException {
        formatHandler.writeFile(file, this, delay);
        modified = false;
    }

    /** Writes this sequence set to a file */
    public void writeToFile(File writeTo, DelayCallback delay)
            throws IOException, DelayAbortedException {
        formatHandler.writeFile(writeTo, this, delay);
        modified = false;
    }

    /** Tests the SequenceList class. */
    public void test(TestController testMaster, DelayCallback delay) throws DelayAbortedException {
        testMaster.begin("DNA.SequenceList");

        SequenceList sl = new SequenceList();
        Sequence seq = Sequence.makeEmptySequence("Test pretest", 32);
        sl.add(seq);
        sl.add(seq);
        Sequence seq1 = Sequence.makeEmptySequence("Test test", 32);
        sl.add(seq1);
        sl.add(seq1);
        sl.add(seq1);
        sl.add(seq1);
        sl.add(seq1);
        seq = Sequence.makeEmptySequence("Test posttest", 32);
        sl.add(seq);
        sl.add(seq);

        testMaster.beginTest("Is ConspecificIterator actually returning all the sequences?");
        Iterator i = sl.conspecificIterator(seq1.getSpeciesName());
        int x = 0;
        while (i.hasNext()) {
            Sequence s = (Sequence) i.next();
            i.remove();
            x++;
        }

        if (x == 5) testMaster.succeeded();
        else testMaster.failed("I 'found' " + x + " conspecific sequences instead of 5!");

        testMaster.beginTest("Does 'remove' work?");
        if (sl.count() == 4) testMaster.succeeded();
        else
            testMaster.failed(
                    "After removing, there should be 4 sequences; instead, there are "
                            + sl.count()
                            + "!");

        testMaster.done();
    }
}

class ConspecificIterator implements Iterator {
    private static SequenceList list;
    private static Sequence target;
    private static int x;
    private static boolean justDeleted = false;

    public ConspecificIterator(SequenceList list, Sequence target, int index) {
        ConspecificIterator.list = list;
        ConspecificIterator.target = target;
        ConspecificIterator.x = index;
    }

    public boolean hasNext() {
        if (x < 0 || x >= list.count()) return false;
        Sequence seq = (Sequence) list.get(x);
        if (seq.getSpeciesName() == null) // DEFINITELY not the same
        return false;
        if (seq.getSpeciesName().equals(target.getSpeciesName())) return true;
        return false;
    }

    public Object next() throws NoSuchElementException {
        justDeleted = false;

        if (!hasNext()) throw new NoSuchElementException();

        Sequence seq = (Sequence) list.get(x);
        x++;
        return seq;
    }

    public void remove() {
        if (justDeleted)
            throw new IllegalStateException(
                    "You can't call remove() twice after a single call to next()");

        // the trick is: we decrement 'x' (to move 'back' one) and remove whatever is at 'x'. x is
        // now
        // pointing at the 'new' x.
        x--;
        Sequence seq = (Sequence) list.get(x);
        if (seq != null && seq.getSpeciesName().equals(target.getSpeciesName())) {
            list.remove(list.get(x));
        }

        justDeleted = true;
    }
}
;
