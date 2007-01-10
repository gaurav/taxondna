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

		public Vector getSections(String s) {
			Vector v = new Vector();

			Iterator i = getSections();
			while(i.hasNext()) {
				Section sec = (Section) i.next();

				if(sec.name().equals(s))
					v.add(sec);
			}

			return v;
		}

		public String getSection(String s) {
			StringBuffer buff = new StringBuffer();
			Vector v = getSections(s);
			
			Iterator i = v.iterator();
			while(i.hasNext()) {
				Section sec = (Section) i.next();

				buff.append(sec.value() + "\n");
			}

			return buff.toString();
		}

		public List getSections() {
			return (List) v_sections;
		}

		public String toString() {
			if(getSection("DEFINITION") != null) {
				return getSection("DEFINITION");
			} else if(getSection("ACCESSION") != null) {
				return getSection("ACCESSION");
			} else {
				return "Unnamed GenBank locus";
			}
		}
	}

	/**
	 * A section 
	 */
	public class Section {
		private String name = "";
		private StringBuffer buff = null;

		public Section(String secName) {
			name = secName;
		}

		public void setValue(String val) {
			buff = new StringBuffer(val);	
		}

		public void append(String val) {
			if(buff == null)
				buff = new StringBuffer();
			buff.append("\n" + val);
		}

		public String name() { return name; }
		public String value() { return buff.toString().trim(); }
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

		LineNumberReader r = null;
		try {
			r = new LineNumberReader(new FileReader(f));
			r.setLineNumber(0);	// 1-based indexes, not 0

			Locus l = new Locus();
			Section s = null;

			String line = null;
			while(true) {
				line = r.readLine();

				if(line == null) {
					// EOF!
					if(s != null)
						l.addSection(s);

					if(l != null)
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
							l.addSection(s);
							addLocus(l);
							l = new Locus();
							s = null;
						} else {
							// SECTION
							// save old section
							l.addSection(s);

							// start new section
							int until = line.indexOf(' ');
							if(until == -1)
								throw new FormatException("Error on line " + r.getLineNumber() + ": Expecting keyword, found '" + line + "'");

							String keyword = line.substring(0, until).trim();
							s = new Section(keyword);

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
		}
				

		if(d != null)
			d.end();
	}

	public File getFile() {
		return file;
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

	public String getAsGenBank() {
		StringBuffer buff = new StringBuffer();
		
		Iterator i_loci = getLoci().iterator();
		while(i_loci.hasNext()) {
			Locus l = (Locus) i_loci.next();

			Iterator i_sections = l.getSections();
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
					buff.append("ORIGIN      \n        " + s.value() + "\n");
				} else if(s.name().equals("FEATURES")) {
					buff.append("FEATURES             " + s.value() + "\n");
				} else {
					StringBuffer tmp = new StringBuffer();
					for(int x = 0; x < 12 - (s.name().length()); x++)
						tmp.append(' ');

					buff.append(s.name() + tmp.toString() + s.value() + "\n");
				}

			}

			buff.append("//\n\n");
		}

		return buff.toString();
	}

}
