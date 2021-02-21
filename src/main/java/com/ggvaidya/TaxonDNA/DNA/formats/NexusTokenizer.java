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

	private Results reportChar(StringBuffer s, char x) {
		//System.err.println("Reporting string " + s + ", character " + x + " (also known as " + (int)x + ")");
		Results r = reportWord(s, (int) x);
		//System.err.println("Resulting in: " + r);
		return r;
	}

	private Results reportWord(StringBuffer x) {
		return reportWord(x, TT_WORD);
	}

	private Results reportWord(StringBuffer x, int val) {
		try {
			if(x == null || x.length() == 0) {
				if(val == TT_WORD)
					return null;
				return new Results(this, val);
			}
			return new Results(this, x.toString(), val);
		} finally {
			if(x != null)
				x.delete(0, x.length());
		}
	}

	private class Results {
		private NexusTokenizer tok = null;
		public int status = 0;
		public String sval = null;

		public Results(Results r) {
			tok = r.tok;
			status = r.status;
			sval = r.sval;
		}

		public Results(NexusTokenizer nt, String x) {
			status = TT_WORD;
			sval = x;
			tok = nt;
		}

		public Results(NexusTokenizer nt, int x) {
			status = x;
			sval = null;
			tok = nt;
		}

		public Results(NexusTokenizer nt, String x, int status) {
			sval = x;
			this.status = status;
			tok = nt;
		}

		// we can only be deleted IFF:
		// 1.	the status is TT_WORD and sval == null
		// 2.	the status is TT_NULL
		public boolean canDelete() {
			return (
					(status == TT_NULL) ||
					(status == TT_WORD && sval == null)
				);
		}

		public int go() {
			if(status == TT_WORD) {
				tok.sval = sval;
				tok.current_status = status;
				sval = null;
				status = TT_NULL;	// the TT_WORD has been consumed.
			} else {
				if(sval != null) {
					// generate the TT_WORD *first*
					tok.sval = sval;
					tok.current_status = TT_WORD;
					sval = null;
					// but 'status' still has the alternate status message
				} else {
					// sval == null
					// NOW we consume the final status
					tok.current_status = status;
					status = TT_NULL;
				}
			}

			return tok.current_status;
		}

		public String toString() {
			StringBuffer str = new StringBuffer();
			boolean hasString = false;

			if(sval != null) {
				if(sval.length() > 20)
					str.append("status = TT_WORD, word = '" + sval.substring(0, 20) + " ...'");
				else		
					str.append("status = TT_WORD, word = '" + sval + "'");
				hasString = true;
			}
			
			if(status == TT_WORD) {
				// nothing else to report
			} else {
				if(hasString)
					str.append(" | also hiding: ");

				if(status > 0)
					str.append("status = '" + (char) status + "'");
				else
					str.append("status = " + status);
			}

			return str.toString();
		}
	}

	public NexusTokenizer(Reader r) {
		this.r = r;
	}

	public int lineno() {
		return lineno;
	}

	private boolean reportNewlines = false;
	public void reportNewlines(boolean x) {
		reportNewlines = x;
	}

	private char gapChar = 0;
	public void setGapChar(char ch) {	
		gapChar = ch;
	}
	private char missingChar = 0;
	public void setMissingChar(char ch) {
		missingChar = ch;
	}
	public boolean isValidCharacter(char ch) {
		if(ch == '_')		// _ is ALWAYS alphanumeric
			return true;

		if(ch == '.')		// a LOT of Nexus files seem to think '.' is alphanumeric. What to do. Go with flow, etc.
			return true;

		if(ch == '|')		// SOME nexus files seem to think '|' is alphanumeric. shrugs.
			return true;

		if(ch == gapChar)
			return true;
		
		if(ch == missingChar)
			return true;
	
		return false;
	}

	// 'static' variables for eatNextToken();
	private int commentingLevel = 0;
	private StringBuffer token = new StringBuffer();
	private boolean inSingleQuotes = false;
	private int noOfConseqNewlines = 0;

	private char lastChar = '@';
	private char ch = '@';
	/**
	 * @return TT_NULL, TT_EOF, TT_WORD or (if reportNewlines is on) TT_EOL
	 * @throws IOException if something went wrong while reading the file (note that EOFException will NEVER be thrown)
	 */ 
	public Results eatNextToken() throws IOException, FormatException {
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
			// newlines
			if(
				(type == Character.PARAGRAPH_SEPARATOR) ||
				(type == Character.LINE_SEPARATOR) ||
				(ch == 0x000A || ch == 0x000D)
			) {
				noOfConseqNewlines++;
				if(noOfConseqNewlines%2 == 1) {	// skip alternate contiguous newlines
					lineno++;
					if(reportNewlines) {
						return reportWord(token, TT_EOL);	// yes, report BOTH
					} else {
						return reportWord(token);
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
			if(ch == '\'' || ch == '"') {
				if(lastChar == ch) {
					// two 's; actually insert one into the buffer (as a normal character)
					token.append(ch);
				}
				// one, probably single, 's.
				// 
				// are we in a word? if not, a word just ended!
				if(inSingleQuotes) {
					inSingleQuotes = false;
					return reportWord(token);
				} else
					inSingleQuotes = true;

				continue;
			}

			//System.err.println("character = '" + ch + "', isWhitespace: " + Character.isWhitespace(ch) + ", lastChar.isWhitespace: " + Character.isWhitespace(lastChar) + ", isLetterOrDigit: " + Character.isLetterOrDigit(ch) + ", heck; token = '" + token + "'");
	
			// 2. Now that we'll know if the user *really* means
			// to put a space in somewhere, let's squeeze up
			// whitespace!
			//
			// Also: whitespace separates 'words'. If we see
			// whitespace, and we're not inSingleQuotes, we need to
			// pass it back to the user.
			if(Character.isWhitespace(ch)) {
				if(Character.isWhitespace(lastChar)) {
					if(inSingleQuotes)
						token.append(ch);
					// skip it!
					continue;
				} else {
					if(inSingleQuotes) {
						// whitespace significant, get on with life.
						token.append(ch);
						continue;
					}
	
					// not inSingleQuotes! End of word!
					return reportWord(token);
				}
			}

			// 3. BUT - punctuation also ends words! (unless we're inSingleQuotes)
			if(!inSingleQuotes && !Character.isLetterOrDigit(ch) && !isValidCharacter(ch)) {	// TODO are hyphens always okay?
				return reportChar(token, ch);
			}

			// it's a letter or digit
			token.append(ch);
		}
	}

	private Stack previousResults = new Stack();	// Stack of Results
	private Results lastResult = null;
	int v = 0;
	public int nextToken() throws IOException, FormatException {
		while(lastResult == null) {
			lastResult = eatNextToken();
		}
		
		//System.err.println(lastResult);

		// now we have a 'lastResult'.
		// There are three kinds of lastResults
		//
		// luckily, there *is* one this OOP is good at
		int retVal = lastResult.go();

		// now, we CREATE a Results FROM what we're about to RETURN
		// and we KEEP a copy in the STACK
		//
		// so we can POP it if we HAVE to
		if(retVal == TT_WORD) {
			if(lastResult.status != TT_WORD) {				
				previousResults.push(new Results(this, sval, lastResult.status));
//				previousResults.push(new Results(this, null, lastResult.status));
//				previousResults.push(new Results(this, sval, TT_WORD));
			} else
				previousResults.push(new Results(this, sval, retVal));					
		} else
			previousResults.push(new Results(this, null, retVal));

		// Okay, NOW we can delete the current 'lastResult'
		if(lastResult.canDelete()) {
			lastResult = null;
		}

		return retVal;
	}

	public void pushBack() {
		lastResult = (Results) previousResults.pop();
		//System.err.println("Popping: " + lastResult);
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
