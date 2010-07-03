/** 	
 * 	A SequenceException is thrown if a sequence is wrong for some
 * 	reason. `Wrong' includes having non-valid nucleotides,
 * 	length/sequence disparity, and anything else the Sequence class
 * 	might need to communicate with the user.	
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

package com.ggvaidya.TaxonDNA.Model;

public class SequenceException extends Exception {
	/**
	 * A generated serialVersionUID
	 */
	private static final long serialVersionUID = -2662237531930486828L;
	private String fullName = "";

	public String getMalformedSequenceName() {
		if(fullName.equals(""))
			return "(I can't understand the name either!)";

		return fullName;
	}
	
	public SequenceException(String name, String message) {
		super(message);

		fullName = name;
	}

	public SequenceException(String name, String message, Throwable cause) {
		super(message, cause);

		fullName = name;
	}

	public SequenceException(String name, Throwable cause) {
		super(cause);

		fullName = name;
	}

	public String getMessage() {
		return "While processing the sequence named '" + fullName + "', the following occured: " + super.getMessage();
	}
}
