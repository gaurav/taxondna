/**
 * The WebHandler allows application users to access features on the internet.
 * This first feature (and the reason why this code was written at all) was to
 * come up with a way of doing NCBI BLAST directly from the application.
 *
 * Some URLs of note:
 *	- http://www.ncbi.nlm.nih.gov/blast/Doc/urlapi.html (BLAST's URL API)
 *
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2010 Gaurav Vaidya
 *  
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *  
 */

package com.ggvaidya.TaxonDNA.SequenceMatrix;

import java.net.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class WebHandler {
    private SequenceMatrix      matrix 	= null;     // the SequenceMatrix object

    /** 
        The constructor does nothing but set up matrix. this is 
        because WebHandler will probably not be used as often as
        you think, so we might as well do as little as possible
        until explicitly commanded to.
    */

    public WebHandler(SequenceMatrix matrix) {
        this.matrix = matrix;
    }

	/**
	 * A sequence to BLAST! We should Do Things on the UI to tell the
	 * user that this is happening. And then we must actually do things.
	 *
	 * We're going to use the URL API detailed here:
	 *		http://www.ncbi.nlm.nih.gov/blast/Doc/urlapi.html
	 * 
	 */
	public void BLASTSequence(Sequence seq, String taxonName, String geneName) {
		if(!seq.isDNA()) {
			if(MessageBox.messageBox(
					matrix.getFrame(),
					"This sequence does not appear to be DNA",
					"Currently, only sequences identified as DNA may be BLASTed. Would " +
					"you like to BLAST it on NCBI BLAST anyway?",
					MessageBox.MB_YESNO
			) == MessageBox.MB_NO)
				return;
		}

		// BLAST the sequence.
		// Some common settings we can tweak later.
		String database = "nr";			// GenBank+EMBL+DDBJ+PDB sequences (but no EST, STS, GSS, or phase 0, 1 or 2 HTGS sequences). No longer "non-redundant".
		String genetic_code = "1";		// Which genetic code do we use (1..16,21,22)
		String program_name = "blastn";	// Search blast nucleotides.

		// Generate a defline.
		String defLine = ">" + taxonName + " (gene " + geneName + ")";
		String sequence = defLine + "\n" + seq.getSequence();
		String encodedSequence;

		try {
			encodedSequence = URLEncoder.encode(sequence, "UTF-8");
		} catch(UnsupportedEncodingException e) {
			MessageBox.messageBox(
					matrix.getFrame(),
					"Your Java's URLEncoder does not support UTF-8!",
					"Please report this problem to the developers."
			);
			return;
		}

		// Prepare the query.
		String query = "CMD=Put&DATABASE=" + database + 
				"&GENETIC_CODE=" + genetic_code +
				"&PROGRAM=" + program_name +
				"&QUERY_BELIEVE_DEFLINE=1" +
				"&QUERY=" + encodedSequence;

		// Prepare the POST.
		URL ncbi_blast;

		try {
			ncbi_blast = new URL("http://www.ncbi.nlm.nih.gov/blast/Blast.cgi");
		} catch(MalformedURLException e) {
			MessageBox.messageBox(
				matrix.getFrame(),
				"Invalid URL",
				"The URL stored internally for BLAST searches is invalid. Please " +
				"report this bug to the developers."
			);
			return;
		}

		String response;
		boolean failed = false;
		try {
			HttpURLConnection conn = (HttpURLConnection) ncbi_blast.openConnection();

			conn.setInstanceFollowRedirects(true);
			conn.setRequestMethod("POST");
			conn.addRequestProperty("Content-type", "application/x-www-form-urlencoded");
			conn.addRequestProperty("Content-length", new Integer(query.length()).toString());

			conn.setDoOutput(true);
			conn.connect();
			OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
			writer.write(query);
			writer.flush();

			if(conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				response = conn.getResponseCode() + " " + conn.getResponseMessage();
				failed = true;
			} else {
				// I give up.
				BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String line;
				StringBuffer buff = new StringBuffer();
				while ((line = reader.readLine()) != null) {
					buff.append(line);
					buff.append("\n");	// Sigh.
				}
				reader.close();

				response = buff.toString();
			}
			
		} catch(IOException e) {
			MessageBox.messageBox(
				matrix.getFrame(),
				"Could not submit request to NCBI BLAST",
				"An error occured while submitting this request to NCBI BLAST. Please " +
				"try again later. If this continues to fail, please ensure that NCBI BLAST " +
				"is online, then report this bug to the developers.\n\n" +
				e.getMessage()
			);
			return;
		}

		if(!failed) {
			Pattern p = Pattern.compile("<!--QBlastInfoBegin\\s*RID = (.*)\\s*RTOE = (\\p{Digit}+)\\s*QBlastInfoEnd\\s*-->");
			Matcher m = p.matcher(response);

			if(m.find()) {
				String RID = m.group(1);
				String RTOE = m.group(2);

				try {
					MessageBox.messageBox(
						matrix.getFrame(),
						"Success!",
						"Gene " + geneName + " of " + taxonName + " has been sent to NCBI BLAST for analysis. " +
						"Results are expected in " + RTOE + " seconds. To view these results, please visit:\n\t" +
						"http://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Get&RID=" + URLEncoder.encode(RID, "UTF-8")
					);
				} catch(UnsupportedEncodingException e) {
					MessageBox.messageBox(
						matrix.getFrame(),
						"Success!",
						"Gene " + geneName + " of " + taxonName + " has been sent to NCBI BLAST for analysis. " +
						"Results are expected in " + RTOE + " seconds. To view these results, please visit:\n\t" +
						"http://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Get&RID=" + RID + "\n\n" +
						"WARNING: Your Java does not support UTF-8! The above URL might not work. " +
						"Please report this to the developers."
					);
				}
			} else {
				failed = true;
				response = "Could not find RID in string " + response;
			}
		}

		if(failed) {
			MessageBox.messageBox(matrix.getFrame(),
				"NCBI refused this request",
				"This request was successfully transmitted to NCBI, but the request " +
				"was refused. Please try again. If this fails, please report this bug " +
				"to the developers of this application. Please do not report it to NCBI " +
				"unless you're sure the fault is with them.\n\n" +
				response
			);
			return;
		}

	}
}
