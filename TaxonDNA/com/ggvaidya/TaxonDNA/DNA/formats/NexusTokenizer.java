/**
 * NexusTokenizer is like StreamTokenizer, except that it is specifically
 * keyed in to Nexus's oddball attitudes. The idea is, you should be
 * able to (mostly) use this as a normal StreamTokenizer, except that:
 * 	- 's can be used to demarcate 'words'.
 * 	- _s are automatically converted into spaces.
 * 	- [s and ]s can be used to open and close comments.
 * 
 * We still need to come up with a smart solution for Nexus sequence
 * blocks with wholes in them, but whatever.
 *
 * @author Gaurav Vaidya gaurav@ggvaidya.com
 */

/*
 * TaxonDNA
 * Copyright (C) 2007 Gaurav Vaidya
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 */

package com.ggvaidya.TaxonDNA.DNA.formats;

import java.io.*;
import java.util.*;

public class NexusTokenizer extends StreamTokenizer {
	public NexusTokenizer(Reader r) {
		super(r);

		initTokenizer();
	}

	/** 
	 * Sets up Nexus-specific transformations for this
	 * tokenizer.
	 */
	public void initTokenizer() {
		ordinaryChar('/');	// this is the default StringTokenizer comment char, so we need to set it back to nothingness

		// turn off parseNumbers()
		ordinaryChar('.');
		ordinaryChar('-');
		ordinaryChars('0','9');
		wordChars('.','.');
		wordChars('-','-');
		wordChars('0','9');

		wordChars('|','|');		// this is officially allowed in Nexus, and pretty convenient for us
		wordChars('_', '_');
						// we need to replace this manually. they form one 'word' in NEXUS.
		wordChars('(','(');		// sequences which use '(ACTG)' = 'N' will now be read in in one shot.
		wordChars(')',')');
		ordinaryChar('[');	// We need this to be an 'ordinary' so that we can use it to discriminate comments
		ordinaryChar(']');	// We need this to be a 'word' so that we can use it to discriminate comments
		ordinaryChar(';');	// We need this to be a 'word' so that we can use it to distinguish commands 

		// let's see if plain old fashioned quoteChars() works
		ordinaryChar('\'');
	}

	/**
	 * Returns the nextToken() (with a little preprocessing from your friends)
	 */
	public int nextToken() throws IOException {
		int tok = super.nextToken();

		return tok;
	}
}
