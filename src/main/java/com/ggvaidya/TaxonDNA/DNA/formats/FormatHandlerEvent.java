/**
 * A FormatHandlerEvent stores information about the state of the FormatHandler when
 * the event was fired - which file it was processing, which line it was on, which
 * formatHandler object was being used - as well as what actually happened.
 *
 * This could get complicated, and I desperately wish there was an easier way to
 * handle this madness. Any ideas? Anybody?
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

public class FormatHandlerEvent {
	/** There was no event */
	public static final int		NO_EVENT =		0;

	/** A file-wide character set was detected; the FormatHandler would like to inform you of the same. */
	public static final int		CHARACTER_SET_FOUND =	1;

	// environment info
	private File			f = null;	
	public File getFile() 		{	return f; }
	private FormatHandler		fh = null;
	public FormatHandler getFormatHandler() 
					{	return fh; }
	private SequenceList		sl = null;
	public SequenceList getSequenceList() 
					{	return sl; }
	private int			id = NO_EVENT;
	public int getId()		{ 	return id; }

	// event specific info
	public String	name = null;
	public Sequence	sequence = null;
	public int	from = 	0;
	public int 	to =	0;

	/**
	 * Standard constructor. You can specify 'null' to any of these.
	 */
	public FormatHandlerEvent(File f, FormatHandler fh, SequenceList sl) {
		this.f = f;
		this.fh = fh;
		this.sl = sl;
	}

	/**
	 * Specify a CHARACTER_SET_FOUND event.
	 * Note the following about CHARACTER_SET_FOUND events:
	 * 1.	There may be multiple character sets.
	 * 2.	Each character set might cover multiple areas.
	 * 	Each area will be called in ONCE and SEPARATELY!
	 * 	Hence, YOU (and you alone) are responsible for
	 * 	figuring out:
	 * 	1.	which areas are redundant (used in two
	 * 		or more features already)
	 * 	2.	which areas are called out multiple times.
	 * 	3.	any other issue you might have with this
	 * 		system.
	 *
	 * It's quite new and quite rocky yet. Apologies.
	 */
	public FormatHandlerEvent makeCharacterSetFoundEvent(String called, int from, int to) {
		FormatHandlerEvent evt = new FormatHandlerEvent(f, fh, sl);
		evt.id = CHARACTER_SET_FOUND;
		evt.name = called;
		evt.from = from;
		evt.to = to;

		return evt;
	}
}

