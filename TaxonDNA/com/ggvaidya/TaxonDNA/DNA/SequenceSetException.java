/** 	
 * 	A SequenceSetException is thrown if there is something
 * 	funny about a SequenceSet. Mostly, this is about mistakes
 * 	on the user's part (duplicate sequences).
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

public class SequenceSetException extends Exception {
	private static final long serialVersionUID = -8512258323870315592L;

	public SequenceSetException() {
		super();
	}

	public SequenceSetException(String message) {
		super(message);
	}

	public SequenceSetException(String message, Throwable cause) {
		super(message, cause);
	}

	public SequenceSetException(Throwable cause) {
		super(cause);
	}
}
