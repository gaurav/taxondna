/**
 * A GenBankFile abstracts a file of the GenBank format, allowing you to access its contents, as
 * well as write it back. A GenBankFile will encapsulate the file entirely; creating a GenBankFile,
 * then writing it back out again should create AN EXACTLY IDENTICAL FILE. We do this by: 1.
 * Reducing the file format to it's basic structure. 2. Perfecting code which can 'open' the file,
 * then save it back as itself (without ANY changes!) 3. Perfecting code which 'reads' bits. 4.
 * Perfecting code which iterates over readable bits.
 *
 * <p>This is pretty easy for GB files, which have a very simple, logical structure: 1. Each file is
 * made up of zero or more loci, separated by '//' 2. Each locus is made up of zero or more
 * sections. Each section is identified by a name starting in column 0. A section may span multiple
 * lines if column 0 is left blank (i.e. the first character on the line is a space). 3. Some
 * sections contain entire values we can use: 1. DEFINITION: The 'sequence name' 2. 4. Some sections
 * require special processing; namely: 1. LOCUS: Date? 2. VERSION: Extract the record GI number. 3.
 * ORGANISM: We'll need to extract the species name (first line) 4. FEATURES: A complete subproblem
 * unto itself :) 5. ORIGIN: Sequence can be extracted from here.
 *
 * <p>That's where we go for now. More interprocessing fun to follow.
 *
 * <p>FOR FUTURE REFERENCE: What we call 'sections', GenBank calls 'keywords'
 */

/*
 *
 *  GenBankExplorer
 *  Copyright (C) 2007 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.GenBankExplorer;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Common.DNA.*;
import com.ggvaidya.TaxonDNA.Common.DNA.formats.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

public class GenBankFile {
  private File file = null;
  private Vector v_locus = new Vector();

  /** A locus */
  public class Locus {
    private Vector v_sections = new Vector();

    public Locus() {}

    public void addSection(Section s) {
      if (s == null) return;

      v_sections.add(s);
    }

    public Section getSection(int x) {
      return (Section) v_sections.get(x);
    }

    public List getSections(String s) {
      Vector v = new Vector();

      Iterator i = getSections().iterator();
      while (i.hasNext()) {
        Section sec = (Section) i.next();

        if (sec.name().equals(s)) v.add(sec);
      }

      return v;
    }

    public Section getSection(String s) {
      List v = getSections(s);

      if (v.size() == 0) return null;
      if (v.size() == 1) return (Section) v.get(0);

      throw new RuntimeException(
          "There are multiple sections named '" + s + "' in Locus " + this + "!");
    }

    public String getSectionsAsString(String s) {
      StringBuffer buff = new StringBuffer();
      List v = getSections(s);

      Iterator i = v.iterator();
      while (i.hasNext()) {
        Section sec = (Section) i.next();

        buff.append(sec.value() + "\n");
      }

      return buff.toString().trim();
    }

    public List getSections() {
      return (List) v_sections;
    }

    public String getName() {
      if (getSection("DEFINITION") != null) {
        return getSection("DEFINITION").entry();
      } else if (getSection("ACCESSION") != null) {
        return getSection("ACCESSION").entry();
      } else {
        return "Unnamed GenBank locus";
      }
    }

    public String getGI() {
      if (getSection("VERSION") != null) {
        // TODO: Move this code into, err, VersionSection - when there is one.
        Pattern p = Pattern.compile("GI:(\\d+)\\s*$");
        Matcher m = p.matcher(getSection("VERSION").value());

        if (m.find()) {
          return m.group(1);
        }
      }

      return "";
    }

    public String toString() {
      return getName();
    }
  }

  /** A section */
  public class Section {
    protected Locus locus = null;
    protected String name = "";
    protected StringBuffer buff = null;

    public Section(Locus l, String secName) {
      locus = l;
      name = secName;
    }

    public Locus getLocus() {
      return locus;
    }

    public void setValue(String val) {
      buff = new StringBuffer(val);
    }

    public void append(String val) {
      if (buff == null) buff = new StringBuffer();
      buff.append("\n" + val);
    }

    public void parseSection(DelayCallback d) throws FormatException {
      // *we* don't do any parsing, humpf
    }

    public String name() {
      return name;
    }

    /*
     * Okay, listen carefully, this is important.
     */
    /**
     * The value() of an entry is the EXACT STRING which needs to be placed into the GenBank file
     * after the appropriate space for the name(). For instance, for REFERENCE: value() = "2 (bases
     * 1 to 5028) AUTHORS Roemer,T., Madden,K., Chang,J. and Snyder,M. TITLE Selection of axial
     * growth sites in yeast requires Axl2p, a novel plasma membrane glycoprotein JOURNAL Genes Dev.
     * 10 (7), 777-793 (1996) PUBMED 8846915" The section will be written back to file as new
     * String(name() + appropriate_spaces + value()), so the format had better be <em>exactly</em>
     * right.
     */
    public String value() {
      return buff.toString().trim();
    }

    /**
     * The entry() of a Section is the value(), reformatted to be 'human-readable'. This is ignored
     * when writing back to the file, but is the ONLY value used in the program itself and in all
     * user-facing surfaces (unless it's some sort of what-will-this-output question). The
     * individual Section classes are responsible for ensuring that the entry() and value() methods
     * are suitably synchronized. Section itself will return a spaceless version of the value().
     */
    public String entry() {
      //			String no_newlines = buff.toString().replace('\n', ' '); -- REDUNDANT
      String compress_spaces = value().replaceAll("\\s+", " ");
      return compress_spaces;
    }

    // TIMTOWTDI
    public String getName() {
      return name();
    }

    public String getValue() {
      return value();
    }

    public String getEntry() {
      return entry();
    }

    public String toString() {
      return name();
    }
  }

  /** The OriginSection allows you to read origins off-a loci. */
  public class OriginSection extends Section {
    public OriginSection(Locus l, String name) {
      super(l, name);
    }

    public String entry() {
      return value().replaceAll("\\d+", "").replaceAll("\\s+", "");
    }

    public Sequence getSequence() throws SequenceException {
      return new Sequence(getLocus().getName(), entry());
    }
  }

  /**
   * A class for a Location. These are locations as defined in section 3.4.12.2 of release 157.0 of
   * NCBI-GenBank. To simplify, there are seven types of valid locations in a GenBank file.
   */

  /*
   * TYPE 1.1 Site between two residues: 				[Z:]X^Y
   * * NOT SUPPORTED
   * TYPE 1.2 Single residue chosen from a range of residues: 	[Z:][<]X.[>]Y
   * * NOT SUPPORTED
   * TYPE 2.1 Contiguous span of bases:				[Z:][<]X..[>]Y
   * 	Z above indicates an accession number, to refer to another sequence
   * * supported
   *
   * TYPE 3.1 Operator complement():				complement(location)
   * * supported
   * TYPE 3.2 Operator join():					join(location, location, .. location)
   * * supported
   * TYPE 3.3 Operator order():					order(location, location, .. location)
   *	Note that join() implies that the locations must be joined end-to-end, but
   *	order() doesn't.
   * * supported
   */
  public class Location {
    public class SingleLocation extends Location {
      private boolean complemented = false; // complement?
      private boolean from_extended = false; // did the user use '<'?
      private int from = -1;
      private int to = -1;
      private boolean to_extended = false; // did the user use '>'?

      private SingleLocation() {}

      public SingleLocation(String str) throws FormatException {
        if (str.indexOf("join") != -1) {
          // we do NOT support joins inside SingleLocations. Sorry.
          throw new FormatException(
              "Currently, 'join's inside other operators (such as 'complement') are not supported at present. Sorry!");
        }

        if (str.regionMatches(true, 0, "complement", 0, 10)) {
          complemented = true;
          str = str.substring(str.indexOf('(') + 1, str.indexOf(')'));
        }

        if (str.charAt(0) == '<') {
          from_extended = true;
          str = str.substring(1);
        }

        int index_to_extender = str.indexOf('>');
        if (index_to_extender != -1) {
          to_extended = true;
          str = str.substring(0, index_to_extender) + str.substring(index_to_extender + 1);
        }

        if (str.matches("^\\d+\\.\\.\\d+$")) {
          // simple and easy
          String[] tokens = str.split("\\.+");

          try {
            from = Integer.parseInt(tokens[0]);
            to = Integer.parseInt(tokens[1]);
          } catch (NumberFormatException e) {
            throw new FormatException(
                "Could not interpret location '" + str + "': " + e.getMessage());
          }
        } else if (str.matches("^\\d+$")) {
          // single number
          try {
            from = Integer.parseInt(str);
            to = from;
          } catch (NumberFormatException e) {
            throw new FormatException(
                "Could not interpret location '" + str + "': " + e.getMessage());
          }
        } else {
          throw new FormatException(
              "Could not interpret location '"
                  + str
                  + "': not a format I can handle at the moment (Sorry!)");
        }
      }

      public String toString() {
        StringBuffer buff = new StringBuffer();

        if (complemented) buff.append("complement of ");

        buff.append("from " + from + " to " + to + " ");

        if (from_extended) buff.append("extending from ");
        if (to_extended) buff.append("extending to ");

        return buff.toString();
      }

      /** Cut the current location (in biological units, natch) out of Sequence. */
      public Sequence getSubsequence(Sequence seq) throws SequenceException {
        // now, since we're a SingleLocation, we've got it *easy*!
        int my_from = from;
        int my_to = to;

        if (complemented) {
          int temp = my_to;
          my_to = my_from;
          my_from = temp;
        }

        // TODO: this drops 'extended' information. are we sure we
        // want to do this? On the other hand, there's not an awful
        // lot you *can* do with that information.
        return seq.getSubsequence(my_from, my_to);
      }

      /** returns the 'true' from, the lower of the two location values */
      public int getFrom() {
        if (complemented) return to;
        return from;
      }

      /** return the 'true' to, the lower of the two location values */
      public int getTo() {
        if (complemented) return from;
        return to;
      }

      /** Does this SingleLocation intersect another SingleLocation? */
      public boolean doesIntersect(SingleLocation s) {
        if (getFrom() <= s.getFrom() && getTo() >= s.getFrom()) { // intersects atleast at s.from
          return true;
        } else if (getTo() >= s.getTo() && getFrom() <= s.getTo()) { // insersects atleast at s.to
          return true;
        }

        return false;
      }

      /**
       * Does this SingleLocation intersect with a Location? Now, a location has a full powered way
       * of doing this, and we should have the SingleLocation.doesIntersect(SingleLocation) case
       * above, so hopefully this won't just sit around looping infinitely.
       */
      public boolean doesIntersect(Location l) {
        return l.doesIntersect(this);
      }
    }

    private Vector locations = new Vector();
    private boolean join = false;
    private boolean order = false;

    private Location() {}

    public Location(String loc) throws FormatException {
      String[] tokens = loc.split("[\\(\\),]+");
      if (tokens[0].regionMatches(true, 0, "join", 0, 4)) {
        join = true;
      } else if (tokens[0].regionMatches(true, 0, "order", 0, 4)) {
        order = true;
      } else {
        // no preamble? hmm. maybe we've only got one location
        locations.add(new SingleLocation(loc));
        return;
      }

      for (int x = 1; x < tokens.length; x++) {
        if (tokens[x].regionMatches(true, 0, "complement", 0, 10)) {
          locations.add(new SingleLocation("complement(" + tokens[x + 1] + ")"));
          x++;
          continue;
        }
        locations.add(new SingleLocation(tokens[x]));
      }
    }

    public String toString() {
      StringBuffer buff = new StringBuffer();

      if (join) buff.append("Join of (");
      if (order) buff.append("Order of (");

      Iterator i = locations.iterator();
      while (i.hasNext()) {
        buff.append(i.next().toString());
        if (i.hasNext()) buff.append(", ");
      }

      if (join || order) buff.append(")");

      return buff.toString();
    }

    /** Cut the current location (in biological units, natch) out of Sequence. */
    public Sequence getSubsequence(Sequence seq) throws SequenceException {
      Sequence sequence = Sequence.makeEmptySequence(toString(), 0);
      // are we a join or order?
      for (int x = 0; x < locations.size(); x++) {
        sequence = sequence.concatSequence(((Location) locations.get(x)).getSubsequence(seq));
      }

      return sequence;
    }

    public List getLocations() {
      return locations;
    }

    /** Does this intersect with a SingleLocation? */
    public boolean doesIntersect(SingleLocation loc) {
      // if ANY of our Locations intersect with this SL, we're through

      for (int x = 0; x < locations.size(); x++) {
        Location l = (Location) locations.get(x);

        if (l.doesIntersect(loc)) return true;
      }

      return false;
    }

    /** Does this intersect with a Location? Ditto as above, except that we repeat for n x n. */
    public boolean doesIntersect(Location loc) {
      Iterator i_me = locations.iterator();

      while (i_me.hasNext()) {
        Location l_me = (Location) i_me.next();

        Iterator i_you = loc.getLocations().iterator();
        while (i_you.hasNext()) {
          Location l_you = (Location) i_you.next();

          if (l_me.doesIntersect(l_you)) return true;
        }
      }

      return false;
    }
  }

  /** A class for a Feature. */
  public class Feature implements Comparable, SequenceContainer {
    private FeaturesSection section = null;
    private String name = null;
    private Hashtable ht_keys = new Hashtable(); // String(key_name) => Vector[String(value)]

    public Feature(FeaturesSection sec, String name) {
      section = sec;
      this.name = name;
    }

    /*
     * Note that 'additional' are NOT actually keys - each
     * key is not guaranteed to yield a unique value.
     */
    public void addKey(String key, String value) {
      // trim starting/ending '"'s
      if (value.length() > 0) {
        if (value.charAt(0) == '"') value = value.substring(1);

        if (value.charAt(value.length() - 1) == '"') value = value.substring(0, value.length() - 1);
      }

      addKey(key, (Object) value);
    }

    public void addKey(String key, Object value) {
      // get on with it
      if (ht_keys.get(key) != null) ((Vector) ht_keys.get(key)).add(value);
      else {
        Vector v = new Vector();
        v.add(value);
        ht_keys.put(key, v);
      }
    }

    public Location getLocation() {
      List l = getValues("@location");
      if (l == null || l.size() == 0) return null;
      return (Location) l.get(0);
    }

    public Locus getLocus() {
      return section.getLocus();
    }

    public Set getKeys() {
      return ht_keys.keySet();
    }

    public List getValues(String key) {
      return (List) ht_keys.get(key);
    }

    public String getName() {
      return name;
    }

    public String getGeneName() {
      List list = getValues("/gene");
      if (list != null) {
        if (list.size() > 1) {
          StringBuffer buff = new StringBuffer();
          Iterator i = list.iterator();
          while (i.hasNext()) {
            buff.append(i.next());
            if (i.hasNext()) buff.append(" | ");
          }
          return "( " + buff.toString() + " )";

        } else if (list.size() == 1) {
          return (String) list.get(0);

        } else return "";
      } else return "";
    }

    public String toString() {
      List list = getValues("/gene");
      if (list != null && list.size() > 0) {
        return getName() + " (" + list.get(0) + ", " + getLocus() + ")";
      }

      return getName() + " (" + getLocus() + ")";
    }

    public int compareTo(Object o) {
      return toString().compareTo(((Feature) o).toString());
    }

    public SequenceList getAsSequenceList() throws SequenceException {
      // do we have a origin in our enclosing locus?
      Locus locus = getLocus();

      List list = locus.getSections("ORIGIN");
      if (list == null || list.size() == 0) return new SequenceList(); // no origin

      // okay, we have *ATLEAST* one origin
      // what if we have more than one?
      //
      // ... loop?
      SequenceList sl = new SequenceList();
      for (int x = 0; x < list.size(); x++) {
        OriginSection origin = (OriginSection) list.get(x);

        // do we have a @location?
        List list_values = getValues("@location");

        if (list_values != null && list_values.size() > 0) {
          for (int y = 0; y < list_values.size(); y++) {
            Location location = (Location) list_values.get(y);

            String gene_name = getGeneName();
            if (gene_name.length() != 0) gene_name += ", ";

            Sequence seq = location.getSubsequence(origin.getSequence());
            seq.changeName(
                getLocus().getName()
                    + " gi|"
                    + getLocus().getGI()
                    + "| "
                    + gene_name
                    + location.toString());

            // DAMBE hack
            if (seq.getSpeciesName() != null) {
              String seqName = seq.getSpeciesName();

              if (seq.getSubspeciesName() != null) seqName += " " + seq.getSubspeciesName();

              if (seq.getGI() != null) seqName += " " + seq.getGI();

              seq.changeName(seqName.replace(' ', '_') + " " + seq.getFullName());
            }
            if (seq != null) sl.add(seq);
          }
        }
      }

      return sl;
    }

    public List alsoContains() {
      return new LinkedList();
    }
  }

  /**
   * FeaturesSection. We 'parse' the features. At the moment, we are read-only; this allows us to
   * just write quick parse-and-remember code, keeping the original in place to write back out to
   * the file.
   */
  public class FeaturesSection extends Section {
    public FeaturesSection(Locus l, String name) {
      super(l, name);
    }

    // Now, we just let <strong>everything work just like it does already</strong>,
    // except for some minor modification we slip in when nobody's looking.
    private Vector features = new Vector();

    public void addFeature(Feature f) {
      features.add(f);
    }

    public List getFeatures() {
      return features;
    }

    public void parseSection(DelayCallback delay) throws FormatException {
      // okay, here's what a Feature section looks like;
      // spaces are insignificant (INCLUDING between '"'es),
      // and
      // FEATURES Location/Qualifiers (<id> <location> (/<key>=<value>)*)*
      //
      // I'd use a StreamTokenizer if it wasn't such a pain to use :(
      String[] tokens = value().split("\\s+"); // split by whitespace

      String current_key = null;
      String current_value = null;
      Feature f = null;
      int state = 0;
      // 0: 	waiting for Location/Qualifiers
      // 1:	waiting for id
      // 2:	waiting for location
      // 3:	waiting for key-value pair OR id
      for (int x = 0; x < tokens.length; x++) {
        String tok = tokens[x];

        switch (state) {
          case 0: // waiting for Location/Qualifiers
            if (tok.equalsIgnoreCase("Location/Qualifiers")) state = 1;
            break;
          case 1: // waiting for id
            // so this must be the ID then!
            f = new Feature(this, tok); // tok == name of feature
            state = 2;
            break;
          case 2: // waiting for location
            // so this must be the location!
            try {
              f.addKey("@location", new Location(tok));
            } catch (FormatException e) {
              // invalid exceptions are, eh, okayish right now
              delay.addWarning(e.getMessage());
            }

            state = 3;
            break;
          case 3: // waiting for key-value pair OR id
            if (tok.charAt(0) == '/') {
              int index_eq = tok.indexOf('=');
              if (index_eq == -1) {
                // 								throw new FormatException("In locus " + getLocus().getName() + ": feature
                // '" + tok + "' is invalid or incomplete (it ought to be /something=something else,
                // but I can't find an '=')");
                current_key = tok;
                current_value = "";
              } else {
                current_key = tok.substring(0, index_eq);
                current_value = tok.substring(index_eq + 1);
              }

              if (countChar(tok, '"') % 2 != 0) {
                state = 4;
              } else {
                f.addKey(current_key, current_value);
                current_key = null; // consumed
                current_value = null;
              }

            } else {
              addFeature(f);
              f = new Feature(this, tok); // tok == name of feature
              state = 2;
            }
            break;

          case 4: // waiting for the end of the "
            current_value += " " + tok; // sorry, world.
            if (countChar(tok, '"') % 2 != 0) {
              // okay, we're done here
              f.addKey(current_key, current_value);
              current_key = null;
              current_value = null;
              state = 3;
            }
            break;
        }
      }

      if (f != null) addFeature(f);
      f = null;
    }

    public String entry() {
      StringBuffer buff = new StringBuffer();
      Iterator i = features.iterator();

      while (i.hasNext()) {
        Feature f = (Feature) i.next();

        buff.append("Feature " + f.getName() + " has the following information:\n");
        Iterator i_keys = f.getKeys().iterator();
        while (i_keys.hasNext()) {
          String key = (String) i_keys.next();

          buff.append("\t" + key + ":\t");

          Iterator i_values = f.getValues(key).iterator();
          while (i_values.hasNext()) {
            buff.append(i_values.next().toString() + "\t");
          }
          buff.append("\n");
        }
      }

      return buff.toString();
    }
  }

  // TODO: Optimize (or replace, then bury underground)
  private int countChar(String str, char ch) {
    int count = 0;

    for (int x = 0; x < str.length(); x++) {
      if (str.charAt(x) == ch) count++;
    }

    return count;
  }

  /**
   * LocusSection. The 'Locus' entry is generally right at the top of the list. TO BE WRITTEN
   * (TBD)(TODO)(FIXME)
   */
  /*
  public class LocusSection extends Section {
  	public LocusSection(Locus l, String name) {
  		super(l, name);
  	}

  	// Comments for the following fields are from GenBank release 157.0, Dec 2006
  	// Note that they are LINE offsets (the 'LOCUS' is at positions 01-05). We will
  	// need to subtract 12 to get coordinates relative to 'name'
  	//
  	private String 	locusName = "";		// 13-28
  	private int 	seqLength = 0;		// 30-40, right justified
  	private String 	type = "";		// 45-47 'ss-', 'ds-' or 'ms-' (mixed stranded)
  						// 48-53 'NA', 'DNA', 'tRNA', etc.
  	private String	linear = "";		// 56-63 'linear' or 'circular'
  	private String	divisionCode = "";	// 65-67 division code, see http://www.ncbi.nlm.nih.gov/Sitemap/samplerecord.html#GenBankDivisionB or Section 3.3
  	private String	date = "";		// 69-79

  	private boolean appendedAlready = false;

  	// NCBI recommends we do NOT use the variable types to get our job done, but it's just
  	// easier this way. I'm sorry. We'll re-write if the format changes, I guess.
  	public void append(String x) {
  		if(appendedAlready)
  			throw new RuntimeException("LocusSection " + this + " in locus " + locus + " has a duplicate append!");
  		appendedAlready = true;
  		setValue(x);
  	}

  	public void setValue(String x) {
  		// parse 'x' and store its value in our fields
  		if(x.getLength() < 79) {

  		}
  	}

  	public String entry() {

  	}

  	public String value() {

  	}
  }
  */

  /**
   * Returns a Section object of the right type (i.e, if section name is 'LOCUS', it'll return a
   * LocusSection instead of a Section.
   */
  public Section sectionFactory(Locus l, String name) {
    if (name.equalsIgnoreCase("FEATURES")) return new GenBankFile.FeaturesSection(l, name);
    else if (name.equalsIgnoreCase("ORIGIN")) return new GenBankFile.OriginSection(l, name);
    //		else if(name.equalsIgnoreCase("LOCUS"))
    //			return new GenBankFile.LocusSection(l, name);
    else return new GenBankFile.Section(l, name);
  }

  /** You can't do that! */
  private GenBankFile() {}

  /**
   * Create a GenBankFile encapsulated around a File. We'll save the file as well; we'll need this
   * to write back to it. We load up the file, using a DelayCallback to report how that's going.
   *
   * <p>Oh, and bear in mind that since we HOLD the file, on some operating systems this actually
   * LOCKS the file in place, so it can't be copied/moved/etc.
   *
   * <p>Heaven alone knows why, though.
   */
  public GenBankFile(File f, DelayCallback d)
      throws IOException, FormatException, DelayAbortedException {
    file = f;

    if (d != null) d.begin();

    // count lines
    int count_lines = 0;

    try {
      BufferedReader r = new BufferedReader(new FileReader(f));
      while (true) {
        if (r.readLine() == null) break;
        if (d != null)
          d.delay(count_lines, Integer.MAX_VALUE); // reasonable amount of possible file lines
        count_lines++;
      }
    } catch (Exception e) {
      // ignore; we'll catch it propery in a second
    }

    LineNumberReader r = null;
    try {
      r = new LineNumberReader(new FileReader(f));
      r.setLineNumber(0); // 1-based indexes, not 0

      Locus l = new Locus();
      Section s = null;

      String line = null;
      while (true) {
        line = r.readLine();

        if (d != null) d.delay(r.getLineNumber() - 1, count_lines);

        if (line == null) {
          // EOF!
          if (s != null) {
            s.parseSection(d);
            l.addSection(s);
          }

          if (l != null && l.getSections().size() > 0) addLocus(l);

          break;
        }

        // whitespace counts
        int leadingWhitespace = 0;
        if (line.length() == 0) // blank line?
        continue;

        for (int x = 0; x < line.length(); x++) {
          if (!Character.isWhitespace(line.charAt(x))) break;
          leadingWhitespace++;
        }

        switch (leadingWhitespace) {
          case 0:
            // SECTION or '//'
            if (line.charAt(0) == '/' && line.charAt(1) == '/') {
              // '//'
              if (s != null) {
                s.parseSection(d);
                l.addSection(s);
              }
              addLocus(l);
              l = new Locus();
              s = null;
            } else {
              // SECTION
              // save old section
              if (s != null) {
                s.parseSection(d);
                l.addSection(s);
              }

              // start new section
              int until = line.indexOf(' ');
              if (until == -1)
                throw new FormatException(
                    "Error on line "
                        + r.getLineNumber()
                        + ": Expecting keyword, found '"
                        + line
                        + "'");

              String keyword = line.substring(0, until).trim();
              s = sectionFactory(l, keyword);

              String x = line.substring(until).trim();
              // we pick up the ' ', then trim it out, so in case the string ends at (until + 1), we
              // won't throw an exception.
              if (x.length() == 0) x = " ";

              // tricky, dicky: do NOT appent the end of ORIGIN, since this is crap and useless to
              // us (unless we understand it, but that's another story)
              if (!keyword.equalsIgnoreCase("ORIGIN")) s.append(x);
            }

            break;
          case 2:
            // SUBSECTION
            s.append(line);

            break;

          case 3:
            // SUBSUBSECTION
            s.append(line);

            break;

          case 5:
            // Feature entry
            s.append(line);

            break;

          default:
            if (line.length() >= 8 && Character.isDigit(line.charAt(8))) {
              // we're in 'ORIGIN', and this is a number
              s.append(line);
            } else {
              // continuation?
              if (line.length() >= 10 && line.substring(0, 10).trim().equals("")) {
                // yes!
                s.append(line);
              } else
                throw new FormatException(
                    "Error on line " + r.getLineNumber() + ": Unexpected line '" + line + "'.");
            }
        }
      }

    } catch (IOException e) {
      if (d != null) d.end();
      throw e;

    } finally {
      if (r != null) r.close();

      if (d != null) d.end();
    }
  }

  public File getFile() {
    return file;
  }

  public void setFile(File f) {
    file = f;
  }

  public void addLocus(Locus l) {
    v_locus.add(l);
  }

  public Locus getLocus(int x) {
    return (Locus) v_locus.get(x);
  }

  public List getLoci() {
    return (List) v_locus;
  }

  public int getLocusCount() {
    return v_locus.size();
  }

  public String toString() {
    return super.toString() + ": contains " + getLocusCount() + " loci";
  }

  public String getAsGenBank(DelayCallback delay) throws DelayAbortedException {
    StringWriter writer = new StringWriter();
    writeAsGenBank(writer, delay);
    return writer.toString();
  }

  public void writeAsGenBank(Writer writer, DelayCallback delay) throws DelayAbortedException {
    PrintWriter pw = new PrintWriter(writer);

    if (delay != null) delay.begin();

    int count = 0;
    int loci_count = getLocusCount();
    Iterator i_loci = getLoci().iterator();
    while (i_loci.hasNext()) {
      Locus l = (Locus) i_loci.next();

      if (delay != null) delay.delay(count, loci_count);
      count++;

      Iterator i_sections = l.getSections().iterator();
      while (i_sections.hasNext()) {
        Section s = (Section) i_sections.next();

        // now, we need to reformat the lines correctly
        //
        // for origin, we put in 12 spaces, so the first
        // line will be on the same level as the others
        //
        // for everything else, we fill up the keyword to
        // 12 spaces.
        if (s.name().equals("ORIGIN")) {
          pw.print("ORIGIN      \n        " + s.value() + "\n");
        } else if (s.name().equals("FEATURES")) {
          pw.print("FEATURES             " + s.value() + "\n");
        } else {
          StringBuffer tmp = new StringBuffer();
          for (int x = 0; x < 12 - (s.name().length()); x++) tmp.append(' ');

          pw.print(s.name() + tmp.toString() + s.value() + "\n");
        }
      }

      pw.print("//\n\n");
    }

    if (delay != null) delay.end();
  }
}
