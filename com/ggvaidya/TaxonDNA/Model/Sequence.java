/* 
 * Sequences are the core data object of TaxonDNA. They contain
 * common code for parsing species names, handle basic Sequence-level
 * stuff, and acts as a parent class for all the forms of
 * Sequence-information we support in TaxonDNA.
 *
 * @seealso DNASequence, DataSequence
 * @author Gaurav Vaidya
 */
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

package com.ggvaidya.TaxonDNA.Model;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Others.*;
import java.util.Properties;
import java.util.regex.*;

public abstract class Sequence implements Comparable, Testable {
	/*
	 * Some variables used by all sequences.
	 */
	protected UUID id = new UUID();

	// The actual name of this sequence.
	protected String	name;

	// From which we parse:
	protected String	gi = "";
	protected String	family = "";
	protected String	genus = "";
	protected String	species = "";
	protected String	subspecies = "";

	/**
	 * I'm not sure where this code is being used, but the
	 * documentation suggests that it's a flag set when
	 * conversion of name -> genus species fails, or when
	 * (for whatever reason) the software isn't sure if it
	 * did it correctly. So I'll just tiptoe around it for
	 * now.
	 */
	protected boolean	flag_isSpeciesNameAvailable = false;

	/**
	 * Properties can be associated or unassociated with this
	 * list freely.
	 */
	protected Properties	properties = new Properties();

	/**
	 * Change the name associated with this sequence. This 
	 * activates the species name parsing code.
	 * @param name
	 */
	public void changeName(String name) {
		Pattern p;
		Matcher m;

		// but no newlines allowed! silently convert into spaces ...
		name = name.replace('\n', ' ');

		// now, we can trim out the spaces on either end
		name = name.trim();

		// actually change the name, and reset the other variables
		this.name = name;
		genus = "";
		species = "";
		subspecies = "";
		gi = "";
		family = "";
		flag_isSpeciesNameAvailable = false;
		
		// guess genus/species and subspecies (first three words in the sequence name)
		// the '\b' you see all over is the 'word boundary', whatever that might be.
		// Who knows. It WORKS.
		p = Pattern.compile("(\\p{Upper}\\p{Lower}+) (\\p{Lower}+) (\\p{Lower}+)\\b");
		m = p.matcher(name);
		if (m.find()) {
			genus = m.group(1);
			species = m.group(2);
			subspecies = m.group(3);
		} else {
			// try two?
			p = Pattern.compile("(\\p{Upper}\\p{Lower}+) (\\p{Lower}+)\\b");
			m = p.matcher(name);
			if (m.find()) {
				genus = m.group(1);
				species = m.group(2);
			} else {
				// pick out the sp.-type
				p = Pattern.compile("(\\p{Upper}\\p{Lower}+) (\\p{Lower}+)\\.\\b");
				m = p.matcher(name);
				if (m.find()) {
					genus = m.group(1);
					species = m.group(2);
					flag_isSpeciesNameAvailable = true;
				}
			}
		}
		if (genus == "") {
			flag_isSpeciesNameAvailable = true;
		}

		p = Pattern.compile("gi\\|(\\d+)[\\|:]"); // ends with either '|' (for normal GIs) or
		// ':' (for GIs which refer to a part of an
		// entire sequence).
		m = p.matcher(name);
		if (m.find()) {
			gi = m.group(1);
		}

		p = Pattern.compile("\\(family:\\s*(\\p{Alpha}+)\\s*\\)", Pattern.UNICODE_CASE);
		m = p.matcher(name);
		if (m.find()) {
			family = m.group(1);
		}

		// Now, we have changed name ... but the sequence remains the same.
		// Hence, I won't call 'resetAllDistances'; even with its different
		// name, the sequence will remain the same, and the distance caching
		// should be using UUIDs now.
	}

	/**
	 * Compare this Sequence against another sequence.
	 * 
	 * @param o The object to compare this object again. May only be a
	 *	DNASequence.
	 * @return -1, 0, or +1 depending on which sequence is "lower" in the
	 *	sort.
	 */
	public int compareTo(Object o) {
		return compareByDisplayName((Sequence) o);
	}

	/**
	 * Compares two sequences by display name (ascending). Error flagged 
	 * sequences are always sorted after non-error flagged sequences.
	 *
	 * @param seq The Sequence to compare this sequence against
	 * @return -1, 0 or +1 depending on which sequence is "lower" in the
	 *	sort.
	 */
	public int compareByDisplayName(Sequence seq) {
		if(compareByWarningFlag(seq) != 0)
			return compareByWarningFlag(seq);

		return getDisplayName().compareToIgnoreCase(seq.getDisplayName());
	}

	/**
	 * Compares two sequences by error flag. Error-flagged sequences are 
	 * ordered after non-error flagged sequences in compareByDisplayName()
	 * sorts.
	 *
	 * @param seq The Sequence to compare this sequence against.
	 * @return -1, 0, or +1 depending on which sequence is "lower" in the
	 *	sort.
	 */
	public int compareByWarningFlag(Sequence seq) {
		boolean hisWarningFlag = seq.isSpeciesNameAvailable();
		boolean myWarningFlag = isSpeciesNameAvailable();

		if(hisWarningFlag && !myWarningFlag) {
			// he's on warning! i should be before him.
			return -1;
		} else if(!hisWarningFlag && myWarningFlag) {
			// i'm on warning! i should be after him.
			return 1;
		}

		return 0;
	}

	/**
	 * Check which this object is identical to another
	 * object. At the moment, we only decide two
	 * sequences to be identical if they have the
	 * same GUID (at which point, 'seq == obj' should
	 * also work).
	 *
	 * @param obj The object to compare against.
	 * @return True if they are equal, false otherwise.
	 */
	public boolean equals(Object obj) {
		Sequence seq;
		if (obj.getClass().equals(this.getClass())) {
			seq = (Sequence) obj;
			if (seq.getId().equals(id)) {
				return true;
			}
			return false;
		}
		return false;
	}

	/**
	 * Get the "display name", the shortest possible name which
	 * can be used to refer to this sequence. 
	 *
	 * @return The display name
	 */
	public String getDisplayName() {
		if (flag_isSpeciesNameAvailable) {
			// warning, but not a "Something sp." warning
			// display the first 40 letters
			int len = name.length();
			if (len >= 80) {
				len = 80;
			}
			return "{" + name.substring(0, len) + "}";
		}

		String display_name = "";
		// if the GI is not specified, we *can't* fall back
		// to Species Name, as this is Not Enough Information.
		if (getGI() == null) {
			return getFullName();
		}
		if (getSpeciesName() != null) {
			display_name = getSpeciesName();
			if (!subspecies.equals("")) {
				display_name += " (" + subspecies + ")";
			}
			if (!family.equals("")) {
				display_name += " (Family " + family + ")";
			}
			if (getGI() != null) {
				display_name += " (gi:" + getGI() + ")";
			}
			return name;
		}
		return getFullName();
	}

	/**
	 * @return The family associated with this sequence.
	 */
	public String getFamilyName() {
		return family;
	}

	/**
	 * @return The complete, unparsed (but slightly cleaned up) name string.
	 */
	public String getFullName() {
		return name;
	}

	/**
	 *
	 * @param max_size
	 * @return
	 */
	public String getFullName(int max_size) {
		/* Get some obvious edge cases */
		if (max_size == -1) {
			return getSpeciesName();
		}
		if (getSpeciesName() == null) {
			if (getFullName() == null) {
				return null;
			} else {
				// all we have is fullName
				String display_name = getFullName();
				if (getGI() != null) {
					display_name = "gi|" + getGI() + "| " + name;
				}
				if (display_name.length() > max_size) {
					return display_name.substring(0, max_size);
				}
				return display_name;
			}
		}
		if (max_size == 0) {
			return "";
		}
		if (max_size == 1) {
			return getGenusName().substring(0, 1);
		}
		if (max_size == 2) {
			return getGenusName().substring(0, 1) + getSpeciesEpithet().substring(0, 1);
		}
		if (max_size == 3) {
			return getGenusName().substring(0, 1) + " " + getSpeciesEpithet().substring(0, 1);
		}
		// try full name
		String display_name = getFullName();
		if (display_name.length() <= max_size) {
			return display_name;
		}
		if (getGI() != null) {
			display_name = "gi|" + getGI() + "| " + getSpeciesName();
		} else {
			display_name = getSpeciesName();
		}
		if (display_name.length() <= max_size) {
			return display_name;
		}
		
		String potential = name; // potential name
		if (potential.length() > max_size) {
			// we'll have to shrink it
			if (max_size >= (1 + 1 + species.length())) {
				// we're okay if we just shrink the genus name
				int diff = max_size - (0 + 1 + species.length());
				if (diff > genus.length()) {
					diff = genus.length();
				}
				genus = genus.substring(0, diff);
				potential = genus + " " + species;
			} else if (max_size >= (1 + 1 + 1)) {
				// we're okay if we shrink species name AND genus name
				genus = genus.substring(0, 1);
				int diff = max_size - (1 + 1 + 0);
				if (diff > species.length()) {
					diff = species.length();
				}
				species = species.substring(0, diff);
				potential = genus + " " + species;
			}
			if (potential.length() > max_size) {
				throw new RuntimeException("I shrunk the name from " + name.length() + " to " + potential.length() + " but it\'s still not " + max_size + "!");
			}
		}
		return potential;
	}

	/**
	 * @return The GI number associated with this sequence.
	 */
	public String getGI() {
		if (gi.equals("")) {
			return null;
		}
		return gi;
	}

	/**
	 * @return The genus name associated with this sequence.
	 */
	public String getGenusName() {
		return genus;
	}

	/**
	 * Every sequence has a length. This method must be
	 * overloaded to display that length to anybody who's
	 * interested.
	 * 
	 * @return The length of this sequence.
	 */
	public abstract int getLength();

	/**
	 * Gets the "default" name of this sequence. Normally,
	 * this is the DisplayName.
	 * @return getDisplayName(), unless overloaded.
	 */
	public String getName() {
		return getDisplayName();
	}

	/**
	 * @return The species name, if parsed. If not, it
	 *	will return "" (NOT the full name).
	 */
	public String getSpeciesName() {
		if (species.equals("") || genus.equals("")) {
			return null;
		}
		return genus + " " + species;
	}

	/**
	 * @return The species epithet.
	 */
	public String getSpeciesEpithet() {
		return species;
	}

	/**
	 * @return The subspecies name (or "" if there isn't one).
	 */
	public String getSubspeciesName() {
		return subspecies;
	}

	/**
	 * @return The unique identifier used to identify this
	 *	sequence. At present, just returns the UUID.
	 */
	public String getId() {
		return getUUID().toString();
	}

	/**
	 * @return The UUID associated with this sequence.
	 */
	public UUID getUUID() {
		return id;
	}
	
	/**
	 * Because there isn't enough flexibility as is: you can get
	 * and set properties on this sequence.
	 * @param name The name of the property. Please use reverse-DNS
	 *	naming (e.g. "com.ggvaidya.SequenceMatrix.a.b.c").
	 * @return The value stored in that property, or null.
	 */
	public Object getProperty(String name) {
		if (properties == null) {
			return null;
		}
		return properties.get(name);
	}

	/**
	 * Sets a property on this sequence.
	 * @param name The name of the property. Please use reverse-DNS
	 *	naming (e.g. "com.ggvaidya.SequenceMatrix.a.b.c").
	 * @param value The value you'd like to set this property.
	 */
	public void setProperty(String name, Object value) {
		if (properties == null) {
			properties = new Properties();
		}
		if (value == null) {
			properties.remove(name);
		} else {
			properties.put(name, value);
		}
	}

	/**
	 * Returns the state of the is-species-name-available flag.
	 */
	public boolean isSpeciesNameAvailable() {
		return flag_isSpeciesNameAvailable;
	}
	
	/**
	 * Changes the state of the is-species-name-available flag.
	 */
	public void setSpeciesNameAvailable(boolean flag) {
		flag_isSpeciesNameAvailable = flag;
	}

	/**
	 * Converts this Sequence object into a string for easy debugging.
	 * @return A string containing the display name, class name and
	 *	sequence length.
	 */
	public String toString() {
		return "Model.Sequence(" + getName() + ", length: " + getLength() + ")";
	}

}
