/**
 * Basic implementation of a FormatHandler. Mostly here to simplify the
 * FormatListener implementation just a little :).
 */

/*
    TaxonDNA
    Copyright (C) 2005-07 Gaurav Vaidya
    
    his program is free software; you can redistribute it and/or modify
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

package com.ggvaidya.TaxonDNA.Model.formats;

import java.util.*;
import java.io.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.Model.*;

public class BaseFormatHandler implements FormatHandler {
	protected Vector formatListeners = new Vector();

	public  String getShortName() 	{ return "Base Format Handler"; }
	public 	String getExtension()	{ return "txt"; }
	public  String getFullName() 	{ return "Base for all Format Handlers in TaxonDNA"; }
	
	public SequenceList readFile(File file, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {
		return null;
	}

	public void appendFromFile(SequenceList appendTo, File fileFrom, DelayCallback delay) throws IOException, SequenceException, FormatException, DelayAbortedException {}

	public void writeFile(File file, SequenceList set, DelayCallback delay) throws IOException, DelayAbortedException
	{
	}
	
	public boolean mightBe(File file) {
		return false;
	}

	public void addFormatListener(FormatListener listener) {
		if(!formatListeners.contains(listener))
			formatListeners.add(listener);	
	}
	public void removeFormatListener(FormatListener listener) {
		formatListeners.remove(listener);
	}
	public void fireEvent(FormatHandlerEvent evt) throws FormatException {
		Iterator i = formatListeners.iterator();
		while(i.hasNext()) {
			FormatListener fl = (FormatListener) i.next();

			if(fl.eventOccured(evt))
				return; 	// event consumed! get out of here!
		}
	}
}

