/** 	
 * 	A SequenceListException is thrown if there is something
 * 	funny about a SequenceListException. Mostly, this is about mistakes
 * 	on the user's part (duplicate sequences).
 *
 * 	Unlike the SequenceSetException (which is serious deprecated anyway),
 * 	we roll SequenceExceptions INTO ourself. A SequenceListException is
 * 	ONLY thrown by a SequenceList constructor; i.e. if there's an error
 * 	loading up a SequenceList from a file.
 */

/*
    TaxonDNA
    Copyright (C) 2005	Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.DNA;

public class SequenceListException extends Exception {
	private static final long serialVersionUID = 485570581694822010L;

	public SequenceListException() {
		super();
	}

	public SequenceListException(String message) {
		super(message);
	}

	public SequenceListException(String message, Throwable cause) {
		super(message, cause);
	}

	public SequenceListException(Throwable cause) {
		super(cause);
	}
}
