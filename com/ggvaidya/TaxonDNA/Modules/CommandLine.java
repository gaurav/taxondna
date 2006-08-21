/**
 * TaxonDNA's command line interface. Handles *every* straight off the
 * command line. The ultimate dream is to allow TaxonDNA to be used off
 * the command line for certain quick operations (conversion). It would
 * also be a very fast way of implementing a testing harness around the
 * currently existing code.
 *
 * How it works: if TaxonDNA detects a 'command line' (of any sort), it
 * hands control over to CommandLine.processCommandLine(String[] args).
 * CommandLine can then call TaxonDNA to set up a normal TaxonDNA object,
 * create multiple TaxonDNA objects, or directly interface with the 
 * TaxonDNA.* objects to carry out the analyses.
 *
 * When eventually I get to finishing up this module, it
 * might help to look up Java's guidelines on POSIX-compliant
 * command line arguments at:
 * 	http://java.sun.com/docs/books/tutorial/essential/attributes/_posix.html
 * They're short and succinct, etc.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2006

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

package com.ggvaidya.TaxonDNA.Modules;

import java.util.*;
import java.io.*;

public class CommandLine {
	LinkedList files	=	new LinkedList();	// files specified to be opened (List of Files)
	LinkedList unknown 	=	new LinkedList();	// unknown arguments (List of Strings)
	
	/**
	 * Creates a CommandLine object, by parsing the command line.
	 * The execute function then 'makes sense of it all'
	 */
	public CommandLine(String[] args) {
		for(int x = 0; x < args.length; x++) {
			String arg = args[x];
			
			// if we don't know what it is, assume it's a file
			File f = new File(arg);
			if(f.exists()) {
				files.add(f);
				continue;
			}

			// no? then it's unknown!
			unknown.add(arg);
		}	       
	}

	/**
	 * Uses the information gathered from parsing the
	 * command line to do stuff.
	 */
	public void execute() {
		boolean inGuiMode = true;
		
		// Are we in 'command line' mode or 'GUI' mode?
		// For now, there's only GUI mode
		
		if(inGuiMode) {
			// warn the user about unknown options
			
			// are there any files specified?
			if(files.size() == 0)
				new TaxonDNA();
			else {
				Iterator i = files.iterator();

				while(i.hasNext()) {
					TaxonDNA td = new TaxonDNA();
					td.loadFile((File)i.next(), null);
				}
			}
		}		
	}
	
	public static void processCommandLine(String[] args) {
		CommandLine cl = new CommandLine(args);
		cl.execute();
	}
}
