/**
 * Preferences is a common store for preferences. 'Preferences' preferences are stored
 * into the java.util.prefs store, and are thus persistant across sessions (heh heh -
 * ain't I the software engineer!). Everybody can check with us through a generic
 * preferences system (getPreference/setPreference), as well as to check some of the
 * variables we ourselves maintain.
 *
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

import com.ggvaidya.TaxonDNA.Common.UI.*;

public class Preferences {
	private GenBankExplorer 		matrix 	= null;			// the GenBankExplorer object

	/**
	 * Constructor. Sets up the UI (on the dialog object, which isn't madeVisible just yet)
	 * and 
	 */
	public Preferences(GenBankExplorer matrix) {
		// set up the GenBankExplorer
		this.matrix = matrix;
	}

	// 
	// the general Preference functions
	//
	/**
	 * Sets the preference specified 
	 */
	public void setPreference(String key, String value) {
		java.util.prefs.Preferences.userNodeForPackage(getClass()).put(key, value);
	}

	/**
	 * Returns the preference specified
	 * @param def default value for this key
	 */
	public String getPreference(String key, String def) {
		return java.util.prefs.Preferences.userNodeForPackage(getClass()).get(key, def);
	}

	/**
	 * Sets the preference specified (as int)
	 */
	public void setPreference(String key, int value) {
		java.util.prefs.Preferences.userNodeForPackage(getClass()).putInt(key, value);
	}

	/**
	 * Returns the preference specified (as int)
	 * @param def default value for this key
	 */
	public int getPreference(String key, int def) {
		return java.util.prefs.Preferences.userNodeForPackage(getClass()).getInt(key, def);
	}

	public void beginNewSession() {
		// clear all session-based variables
		// right now, this is only PREF_NOT_SET_YET.
		MessageBox.resetSession();		// reset all MB_YESNOTOALL
	}
}
