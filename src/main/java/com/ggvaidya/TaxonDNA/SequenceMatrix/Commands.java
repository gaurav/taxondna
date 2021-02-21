/**
 * Commands have commands to access SequenceMatrix functionality
 * through commands. For now, this is entirely for the command line
 * command system, but maybe an Undo/Redo system could be built up
 * about this.
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2009 Gaurav Vaidya
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

import java.io.*;
import java.util.*;

import com.ggvaidya.TaxonDNA.Common.*;

public class Commands { 
    private static SequenceMatrix  matrix 	= null;			// the SequenceMatrix object

    /** Executes a set of commands coming off the command line */
    public static void executeCmdLine(SequenceMatrix input_matrix, List commands) {
        matrix = input_matrix;      // Set this up.

        Vector cmds = new Vector(commands);        

        // Now, we should split this into words, and "recognize" commands
        // by the leading '--'. These commands are presented to an
        // execute(List) method, which takes the command and all necessary
        // arguments, and then returns control.
        while(cmds.size() > 0) {
            String cmd = (String) cmds.remove(0);

            if(cmd.substring(0, 2).equals("--")) {
                // A command!
                cmd = cmd.substring(2);

                execute(cmd, cmds);
            } else {
                output(cmd + " is not a valid command.");
            }
        }
    }

    /** Executes a simple command, possibly getting other arguments off a
      * queue.
      */
    public static void execute(String cmd, List arguments) {
        cmd = cmd.toLowerCase();

        if(cmd.equals("version")) {
            output("This is SequenceMatrix v" + Versions.getTaxonDNA());
        } else if(cmd.equals("add")) {
            String filename = (String) arguments.remove(0);

            matrix.getFileManager().addFile(new File(filename));
        } else if(cmd.equals("quit") || cmd.equals("exit")) {
            System.exit(0);
        } else {
            output("Unable to understand command: " + cmd);
        }
    }

    /** How to provide output. */
    public static void output(String s) {
        System.err.println(s);
    }
}
