/**
 * This is the final keystone in the FormatHandler system - it was
 * cheap, easy and flexible, but now it gets to be cheaply 
 * extensible too. In a nutshell, this is just an ordinary 'listener'
 * for FormatHandlers: you can 'listen' to a variety of events,
 * using a simple constant system to differentiate potentially subtle
 * and complicated events.
 *
 * The really huge question: should listeners get to push content
 * directly into the output file? How should this work? I'll just
 * ignore it until a more appropriate time :).
 */

/*
    TaxonDNA
    Copyright (C) 2005, 2006 Gaurav Vaidya
    
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

package com.ggvaidya.TaxonDNA.DNA.formats;

import java.io.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;

public interface FormatListener {
	/**
	 * An 'event' occured while reading a file.
	 * This is going to vary *dramatically* between different formats:
	 * I imagine Fasta is not going to report ANYTHING (except maybe
	 * SEQUENCE_ADDEDs), while Nexus is probably going to go ballistic
	 * with SET_INFORMATION or whatever.
	 *
	 * @throws FormatException if we see something we disapprove of; the FormatException will be communicated back to the user.
	 * @return true, if the event was 'consumed' (we shouldn't let anybody else know about this)
	 */
	public boolean eventOccured(FormatHandlerEvent evt)
		throws FormatException;
}

