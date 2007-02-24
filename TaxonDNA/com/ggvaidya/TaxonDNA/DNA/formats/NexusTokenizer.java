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

public class NexusTokenizer {
	public static final int		TT_NULL =	-1;	// not set
	public static final int		TT_EOF	=	-2;	// end of file
	public static final int		TT_WORD =	-3;	// a word (it's in this.sval)
	public static final int		TT_EOL =	-4;	// end of line

	private int	current_status 	= TT_NULL;
	public 	String 	sval		= null;		// string value
	private int	lineno		= 1;
	private Reader	r		= null;

	public NexusTokenizer(Reader r) {
		this.r = r;
	}

	public int lineno() {
		return lineno;
	}

	private boolean reportNewlines = false;
	public void reportNewlines(boolean x) {
		reportNewlines = true;
	}

	private int commentingLevel = 0;
	/**
	 * @return TT_NULL, TT_EOF, TT_WORD or (if reportNewlines is on) TT_EOL
	 * @throws IOException if something went wrong while reading the file (note that EOFException will NEVER be thrown)
	 */ 
	public int eatNextToken() throws IOException, FormatException {
		StringBuffer token = new StringBuffer();

		boolean inAWord = true;
		int noOfConseqNewlines = 0;
		char lastChar = '@';		// our WTF character
		char ch = '@';
		while(true) {
			lastChar = ch;

			int c = -1;
			try {
				c = r.read();	// next character
				if(c == -1)
					throw new EOFException();
			} catch(EOFException e) {
				// if there's any words left, we need
				// to send that first!
				return reportWord(token, TT_EOF);
			}

			ch = (char) c;
			int type = Character.getType(ch);
			token.append(ch);

			// newlines
			if(
				(type == Character.PARAGRAPH_SEPARATOR) ||
				(type == Character.LINE_SEPARATOR) ||
				(ch == 0x000A || ch == 0x000D)
			) {
				noOfConseqNewlines++;

				if(noOfConseqNewlines%2 == 0) {	// skip alternate contiguous newlines
					lineno++;

					if(reportNewlines) {
						return reportWord(token, TT_EOL);
					} else {
						if(token.size() > 0)
							return reportWord(token, TT_WORD);
					}
				}

				continue;
			}
			noOfConseqNewlines = 0;
			
			// comments
			if(ch == '[') {
				commentingLevel++;
				continue;
			}

			if(ch == ']') {
				commentingLevel--;
				continue;
			}

			if(commentingLevel > 0)
				continue;

			if(commentingLevel < 0)
				throw new FormatException("Closing ']' without an opening '[' on line " + lineno);

			// words (but NOT sequences)
			// 1. look for 's; they're special.
			if(ch == '\'') {
				if(lastChar == '\'') {
					// two 's; actually insert one into the buffer (as a normal character)
					token.append(ch);
				}
				// one, probably single, 's.
				// 
				// are we in a word? if not, a word just ended!
				if(inAWord) {
					inAWord = false;
				} else
					inAWord = true;
			}

			// 2. Now that we'll know if the user *really* means
			// to put a space in somewhere, let's squeeze up
			// whitespace!
			//
			// Also: whitespace separates 'words'. If we see
			// whitespace, and we're not inAWord, we need to
			// pass it back to the user.
			if(Character.isWhitespace(ch)) {
				if(Character.isWhitespace(lastChar)) {
					if(inAWord)
						token.append(ch);
					// skip it!
					continue;
				} else {
					if(inAWord) {
						// whitespace significant, get on with life.
						token.append(ch);
						continue;
					}

					// not inAWord! End of word!
					return reportWord(token);
				}
			}

			// 3. BUT - punctuation also ends words!
			if(!Character.isLetterOrDigit(ch)) {
				return ch;
			}

			// it's a letter or digit
			token.append(ch);
		}
	}

	public int nextToken() throws IOException, FormatException {
		return eatNextToken();
	}

	public void pushBack() {	// tmep, DLEETE THIS!
		throw new RuntimeException("Oh no you don't ... stupid irrigating ... pushback? pushback?!");
	}

	// Functions which need to work/we need to have:
	// 	tok.nextToken() -> returns either a character (cast to int),
	// 		TT_EOF, TT_EOL, or TT_WORD
	//	tok.sval 	-> the string value (if nextToken() returns TT_WORD
	//	tok.lineno()	-> the current line number
	//		

	/*
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
	*/

	/**
	 * Returns the nextToken() (with a little preprocessing from your friends)
	public int nextToken() throws IOException {
		int tok = super.nextToken();

		return tok;
	}
	*/
}
