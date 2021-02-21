/**
 * Stores 'Messages' (strings which can be retrieved, and hence modified,
 * from a central source). We're trying to be simple: there'll be a set
 * of codes, you call 
 * 	Messages.getMessage(Messages.FILE_NOT_FOUND) 
 * to get a standard FILE_NOT_FOUND error string. Some messages need an
 * arguments (cast via Object), such as 
 * 	Messages.getMessage(Messages.FILE_NOT_FOUND, file)
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */
/*
    TaxonDNA
    Copyright (C) Gaurav Vaidya, 2005

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


package com.ggvaidya.TaxonDNA.SpeciesIdentifier;

import java.io.*;

public class Messages {
	public static int	SUCCESS		=	0;
	public static int	FILE_NOT_FOUND	=	1;
	public static int	CANT_READ_FILE	=	2;
	public static int	SAVE_FILE_FORMAT =	3;
	public static int	READING_FILE	=	4;
	public static int	IOEXCEPTION_READING =	5;
	public static int	IOEXCEPTION_WRITING =	6;
	public static int	IOEXCEPTION_WRITING_NO_FILENAME =	7;
	public static int	COPY_TO_CLIPBOARD_FAILED =	8;
	public static int	NEED_SPECIES_SUMMARY =	9;

	private static String[] errorMessages = {
		// SUCCESS:		No arguments
		"This operation was completely successfully.",
		// FILE_NOT_FOUND:	The file which was not found
		"The file '$1$' was not found. Please ensure that the file exists.",
		// CANT_READ_FILE:	The file which could not be read
		"The file '$1$' could not be read. Please check whether the file exists, and that you have sufficient permissions to read this file.",
		// SAVE_FILE_FORMAT
		"Saving file '$1$' in the $2$ format. To save export this file to another format, you may use the 'Export' menu.",
		// READING_FILE:
		"Reading sequences from '$1$' into SpeciesIdentifier. This might take some time.",
		// IOEXCEPTION_READING:		file, exception
		"There was an error while reading from '$1$'. Please ensure that the file exists, that you have permission to read it, and that there are no problems with the drive on which the file is being read from.\n\nThe technical description of this error is: $2$",
		// IOEXCEPTION_WRITING:		file, exception
		"There was an error while writing to '$1$'. Please ensure that you have permission to create or modify this file, that the drive on which the file is being written is not full or set 'write-only'.\n\nThe technical description of this error is: $2$",
		// IOEXCEPTION_WRITING_NO_FILENAME:	exception
		"There was an error while trying to write a file. Please ensure that the drive on which the file is being written is not full or set 'write-only'.\n\nThe technical description of this error is: $1$",
		// COPY_TO_CLIPBOARD_FAILED:	exception	
		"There was an error copying to the clipboard. The text was probably not copied. Please try again; if this doesn't work, please report it as a bug.\n\nTechnical explanation: $1$",
		// NEED_SPECIES_SUMMARY
		"To carry out this operation, I need the Species Summary module, which was not built into your SpeciesIdentifier. You can try downloading the most recent one, or download the \"definitive\" copy from http://taxondna.sf.net/"
	};

	public static String getMessage(int code) {
		if(code >= 0 && code < errorMessages.length) {
			return errorMessages[code];
		}

		return "An illegal error code occured (error code " + code + "). This is a programming error. Please contact the developer(s) at http://taxondna.sf.net/.";
	}

	public static String getMessage(int code, Object arg) {
		String message = getMessage(code);
		String textArg = makeText(arg);

		if(textArg != null)
			message = message.replaceAll("\\$1\\$", textArg.replaceAll("\\\\", "\\\\\\\\"));

		return message;
	}

	public static String getMessage(int code, Object arg, Object arg2) {
		String message = getMessage(code);
		String textArg = makeText(arg);
		String textArg2 = makeText(arg2);

		if(textArg != null)
			message = message.replaceAll("\\$1\\$", textArg.replaceAll("\\\\", "\\\\\\\\"));

		if(textArg2 != null)
			message = message.replaceAll("\\$2\\$", textArg2.replaceAll("\\\\", "\\\\\\\\"));
		
		return message;

	}

	private static String makeText(Object obj) {
		// handle null objects
		if(obj == null)
			return "(null)";
		
		// see if it's a class we recognize
		if(File.class.isAssignableFrom(obj.getClass())) {
			// it's a file!
			return ((File)obj).getAbsolutePath();
		}

		if(String.class.isAssignableFrom(obj.getClass())) {
			// it's a string!
			return (String)obj;
		}

		return obj.toString();
	}
}
