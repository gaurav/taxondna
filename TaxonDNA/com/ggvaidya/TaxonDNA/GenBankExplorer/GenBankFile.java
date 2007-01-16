/**
 * A GenBankFile abstracts a file of the GenBank format, allowing you to access its contents,
 * as well as write it back. A GenBankFile will encapsulate the file entirely; creating a
 * GenBankFile, then writing it back out again should create AN EXACTLY IDENTICAL FILE. We
 * do this by:
 * 1.	Reducing the file format to it's basic structure.
 * 2.	Perfecting code which can 'open' the file, then save it back as itself (without
 * 	ANY changes!)
 * 3.	Perfecting code which 'reads' bits.
 * 4.	Perfecting code which iterates over readable bits.
 *
 * This is pretty easy for GB files, which have a very simple, logical structure:
 * 1.	Each file is made up of zero or more loci, separated by '//'
 * 2.	Each locus is made up of zero or more sections. 
 * 	Each section is identified by a name starting in column 0. 
 * 	A section may span multiple lines if column 0 is left blank
 * 	(i.e. the first character on the line is a space).
 * 3.	Some sections contain entire values we can use: 
 * 	1.	DEFINITION:	The 'sequence name'
 * 	2.	
 * 4.	Some sections require special processing; namely:
 * 	1.	LOCUS:		Date?
 * 	2.	VERSION:	Extract the record GI number.
 * 	3.	ORGANISM:	We'll need to extract the species name (first line)
 * 	4.	FEATURES:	A complete subproblem unto itself :)
 * 	5.	ORIGIN:		Sequence can be extracted from here.
 *
 * That's where we go for now. More interprocessing fun to follow.
 *
 * FOR FUTURE REFERENCE: What we call 'sections', GenBank calls 'keywords'
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

import java.io.*;
import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class GenBankFile {
	private File 		file =		null;
	private Vector		v_locus = 	new Vector();

	/**
	 * A locus
	 */
	public class Locus {
		private Vector v_sections = new Vector();

		public Locus() {
		}

		public void addSection(Section s) {
			if(s == null)
				return;

			v_sections.add(s);
		}

		public Section getSection(int x) {
			return (Section) v_sections.get(x);
		}

		public List getSections(String s) {
			Vector v = new Vector();

			Iterator i = getSections().iterator();
			while(i.hasNext()) {
				Section sec = (Section) i.next();

				if(sec.name().equals(s))
					v.add(sec);
			}

			return v;
		}

		public Section getSection(String s) {
			List v = getSections(s);

			if(v.size() == 0)
				return null;
			if(v.size() == 1)
				return (Section) v.get(0);

			throw new RuntimeException("There are multiple sections named '" + s + "' in Locus " + this + "!");
		}

		public String getSectionsAsString(String s) {
			StringBuffer buff = new StringBuffer();
			List v = getSections(s);
			
			Iterator i = v.iterator();
			while(i.hasNext()) {
				Section sec = (Section) i.next();

				buff.append(sec.value() + "\n");
			}

			return buff.toString().trim();
		}

		public List getSections() {
			return (List) v_sections;
		}

		public String getName() {
			if(getSection("DEFINITION") != null) {
				return getSection("DEFINITION").entry();
			} else if(getSection("ACCESSION") != null) {
				return getSection("ACCESSION").entry();
			} else {
				return "Unnamed GenBank locus";
			}
		}	

		public String toString() {
			return getName();
		}
	}

	/**
	 * A section 
	 */
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

		public void setValue(String val)  {
			buff = new StringBuffer(val);	
		}

		public void append(String val) {
			if(buff == null)
				buff = new StringBuffer();
			buff.append("\n" + val);
		}

		public void parseSection() throws FormatException {
			// *we* don't do any parsing, humpf
		}

		public String name() { return name; }

		/*
		 * Okay, listen carefully, this is important.
		 */
		/**
		 * The value() of an entry is the EXACT STRING which needs to be
		 * placed into the GenBank file after the appropriate space for
		 * the name(). For instance, for REFERENCE:
		 * 	value() = "2  (bases 1 to 5028)
				  AUTHORS   Roemer,T., Madden,K., Chang,J. and Snyder,M.
				  TITLE     Selection of axial growth sites in yeast requires Axl2p, a novel
				            plasma membrane glycoprotein
				  JOURNAL   Genes Dev. 10 (7), 777-793 (1996)
				  PUBMED    8846915"
		 * The section will be written back to file as new String(name() + appropriate_spaces + value()),
		 * so the format had better be <em>exactly</em> right.
		 */
		public String value()  { 
			return buff.toString().trim(); 
		}

		/**
		 * The entry() of a Section is the value(), reformatted to be 'human-readable'. This is
		 * ignored when writing back to the file, but is the ONLY value used in the program itself
		 * and in all user-facing surfaces (unless it's some sort of what-will-this-output question).
		 * The individual Section classes are responsible for ensuring that the entry() and value()
		 * methods are suitably synchronized. Section itself will return a spaceless version of the
		 * value().
		 *
		 */
		public String entry() { 
//			String no_newlines = buff.toString().replace('\n', ' '); -- REDUNDANT
			String compress_spaces = value().replaceAll("\\s+", " ");
			return compress_spaces;
		}

		// TIMTOWTDI
		public String getName() { return name(); }
		public String getValue() { return value(); }
		public String getEntry() { return entry(); }

		public String toString() {
			return name();
		}
	}

	/**
	 * The OriginSection allows you to read origins off-a loci.
	 */
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
	 * A class for a Feature.
	 */
	public class Feature {
		private String name = null;
		private Hashtable ht_keys = new Hashtable();	// String(key_name) => Vector[String(value)]

		public Feature(String name) {
			this.name = name;
		}

		/*
		 * Note that 'additional' are NOT actually keys - each
		 * key is not guaranteed to yield a unique value.
		 */
		public void addKey(String key, String value) {
			if(ht_keys.get(key) != null)
				((Vector)ht_keys.get(key)).add(value);
			else {
				Vector v = new Vector();
				v.add(value);
				ht_keys.put(key, v);
			}
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
	}

	/**
	 * FeaturesSection. We 'parse' the features. At the moment, we are read-only; this
	 * allows us to just write quick parse-and-remember code, keeping the original in
	 * place to write back out to the file.
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

		public void parseSection() throws FormatException {
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
			for(int x = 0; x < tokens.length; x++) {
				String tok = tokens[x];	

				switch(state) {
					case 0:	// waiting for Location/Qualifiers
					       if(tok.equalsIgnoreCase("Location/Qualifiers"))	       
						       state = 1;
					       break;
					case 1:	// waiting for id
					       // so this must be the ID then!
					       f = new Feature(tok);	// tok == name of feature
					       state = 2;
					       break;
					case 2: // waiting for location
					       // so this must be the location!
					       f.addKey("@location", tok);
					       state = 3;
					       break;
					case 3: // waiting for key-value pair OR id 
					       if(tok.charAt(0) == '/') {
							int index_eq = tok.indexOf('=');
							if(index_eq == -1)
								throw new FormatException("In sequence " + name() + ": feature '" + tok + "' is invalid or incomplete (it ought to be /something=something else, but I can't find an '=')");

							current_key = tok.substring(0, index_eq);
							current_value = tok.substring(index_eq + 1);

							if(countChar(tok, '"') % 2 != 0) {
								state = 4;
							} else {
								f.addKey(current_key, current_value);
								current_key = null;	// consumed
								current_value = null;
							}

					       } else {
						       addFeature(f);
						       f = new Feature(tok);	// tok == name of feature
						       state = 2;
					       }
					       break;
					     
					case 4:	// waiting for the end of the " 
					       current_value += " " + tok;	// sorry, world.
					       if(countChar(tok, '"') % 2 != 0) {
						       // okay, we're done here
						       f.addKey(current_key, current_value);
						       current_key = null;
						       current_value = null;
						       state = 3;
					       }
					       break;
				}
			}
		}

		public String entry() {
			StringBuffer buff = new StringBuffer();
			Iterator i = features.iterator();
			
			while(i.hasNext()) {
				Feature f = (Feature) i.next();	

				buff.append("Feature " + f.getName() + " has the following information:\n");
				Iterator i_keys = f.getKeys().iterator();
				while(i_keys.hasNext()) {
					String key = (String) i_keys.next();

					buff.append("\t" + key + ":\t");

					Iterator i_values = f.getValues(key).iterator();
					while(i_values.hasNext()) {
						String val = (String) i_values.next();

						buff.append(val + "\t");
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

		for(int x = 0; x < str.length(); x++) {
			if(str.charAt(x) == ch)
				count++;
		}

		return count;
	}

	/**
	 * LocusSection. The 'Locus' entry is generally right at the top of the list.
	 * TO BE WRITTEN (TBD)(TODO)(FIXME)
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
	 * Returns a Section object of the right type (i.e, if section name is 'LOCUS',
	 * it'll return a LocusSection instead of a Section.
	 */
	public Section sectionFactory(Locus l, String name) {
		if(name.equalsIgnoreCase("FEATURES"))
			return new GenBankFile.FeaturesSection(l, name);
		else if(name.equalsIgnoreCase("ORIGIN"))
			return new GenBankFile.OriginSection(l, name);
//		else if(name.equalsIgnoreCase("LOCUS"))
//			return new GenBankFile.LocusSection(l, name);
		else
			return new GenBankFile.Section(l, name);
	}

	/** You can't do that! */
	private GenBankFile() {
	}

	/** 
	 * Create a GenBankFile encapsulated around a File. We'll save
	 * the file as well; we'll need this to write back to it. We
	 * load up the file, using a DelayCallback to report how 
	 * that's going.
	 *
	 * Oh, and bear in mind that since we HOLD the file, on some
	 * operating systems this actually LOCKS the file in place,
	 * so it can't be copied/moved/etc.
	 *
	 * Heaven alone knows why, though.
	 */
	public GenBankFile(File f, DelayCallback d) throws IOException, FormatException, DelayAbortedException {
		file = f;	

		if(d != null)
			d.begin();

		// count lines
		int count_lines = 0;

		try {
			BufferedReader r = new BufferedReader(new FileReader(f));
			while(true) {
				if(r.readLine() == null)
					break;
				if(d != null)
					d.delay(count_lines, Integer.MAX_VALUE);	// reasonable amount of possible file lines
				count_lines++;
			}
		} catch(Exception e) {
			// ignore; we'll catch it propery in a second
		}

		LineNumberReader r = null;
		try {
			r = new LineNumberReader(new FileReader(f));
			r.setLineNumber(0);	// 1-based indexes, not 0

			Locus l = new Locus();
			Section s = null;

			String line = null;
			while(true) {
				line = r.readLine();

				if(d != null)
					d.delay(r.getLineNumber() - 1, count_lines);

				if(line == null) {
					// EOF!
					if(s != null) {
						s.parseSection();
						l.addSection(s);
					}

					if(l != null && l.getSections().size() > 0)
						addLocus(l);

					break;
				}

				// whitespace counts
				int leadingWhitespace = 0;
				if(line.length() == 0)	// blank line?
					continue;

				for(int x = 0; x < line.length(); x++) {
					if(!Character.isWhitespace(line.charAt(x)))
						break;
					leadingWhitespace++;
				}

				switch(leadingWhitespace) {
					case 0:
						// SECTION or '//'
						if(line.charAt(0) == '/' && line.charAt(1) == '/') {
							// '//'
							if(s != null) {
								s.parseSection();
								l.addSection(s);
							}
							addLocus(l);
							l = new Locus();
							s = null;
						} else {
							// SECTION
							// save old section
							if(s != null) {
								s.parseSection();
								l.addSection(s);
							}

							// start new section
							int until = line.indexOf(' ');
							if(until == -1)
								throw new FormatException("Error on line " + r.getLineNumber() + ": Expecting keyword, found '" + line + "'");

							String keyword = line.substring(0, until).trim();
							s = sectionFactory(l, keyword);

							String x = line.substring(until).trim();
								// we pick up the ' ', then trim it out, so in case the string ends at (until + 1), we won't throw an exception.
							if(x.length() == 0)
								x = " ";
							s.append(x);
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
						if(line.length() >= 8 && Character.isDigit(line.charAt(8))) {
							// we're in 'ORIGIN', and this is a number
							s.append(line);	
						} else {
							// continuation?
							if(line.length() >= 10 && line.substring(0, 10).trim().equals("")) {
								// yes!
								s.append(line);
							} else 
								throw new FormatException("Error on line " + r.getLineNumber() + ": Unexpected line '" + line + "'.");

						}
				}
			}
			

		} catch(IOException e) {
			if(d != null)
				d.end();
			throw e;

		} finally {
			if(r != null)
				r.close();
		
			if(d != null)
				d.end();
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

		if(delay != null)
			delay.begin();

		int count = 0;
		int loci_count = getLocusCount();
		Iterator i_loci = getLoci().iterator();
		while(i_loci.hasNext()) {
			Locus l = (Locus) i_loci.next();

			if(delay != null)
				delay.delay(count, loci_count);
			count++;

			Iterator i_sections = l.getSections().iterator();
			while(i_sections.hasNext()) {
				Section s = (Section) i_sections.next();

				// now, we need to reformat the lines correctly
				// 
				// for origin, we put in 12 spaces, so the first
				// line will be on the same level as the others
				//
				// for everything else, we fill up the keyword to
				// 12 spaces.
				if(s.name().equals("ORIGIN")) {
					pw.print("ORIGIN      \n        " + s.value() + "\n");
				} else if(s.name().equals("FEATURES")) {
					pw.print("FEATURES             " + s.value() + "\n");
				} else {
					StringBuffer tmp = new StringBuffer();
					for(int x = 0; x < 12 - (s.name().length()); x++)
						tmp.append(' ');

					pw.print(s.name() + tmp.toString() + s.value() + "\n");
				}
			}

			pw.print("//\n\n");
		}

		if(delay != null)
			delay.end();
	}

}
