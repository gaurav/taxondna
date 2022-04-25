/**
 * LargeTextArea
 *
 * <p>Sometimes, TextAreas just aren't large enough. On some systems, TextArea.setText() is silently
 * ignored if the text is too big. On other systems, somebody throws an OutOfMemoryError. It's all
 * pretty random, so I'm just going to create a class to deal with it ALL.
 *
 * <p>Basically, it'll: 1. Use 'try' to make sure that nothing goes wrong with the setText. 2. Check
 * it see if the text got written successfully (by using getText() to get a back: a memory load, if
 * ever there was one, but what else can we do?) 3. If it DIDN'T, it warns you and asks if you'd
 * like to dump the output to a file. Pretty simple interface: MessageBox to ask, FileDialog to
 * indicate location, and - hell - ProgressDialog to save.
 *
 * <p>It's a neat enough solution; have no idea if it will actually WORK. Lesse.
 */

/*
    TaxonDNA
    Copyright (C) 2006	Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Common.UI;

import java.awt.*;
import java.io.*;

public class LargeTextArea extends TextArea {
    private Frame frame = null;
    private String ourString = new String("");

    public void setFrame(Frame frame) {
        this.frame = frame;
    }

    public synchronized String getText() {
        return ourString;
    }

    public synchronized void append(String str) {
        setText(getText() + str);
    }

    public synchronized void appendString(String str) {
        setText(getText() + str);
    }

    public synchronized void setText(String str) {
        ourString = str;

        try {
            super.setText(str);

            if (super.getText().equals(str)) {
                // don't change this!
                // we can't use OUR getText
                // since that's overloaded
                // to return the 'correct'
                // text.

                // if we're here, the text
                // changed successfully.
                // Good show.
                return;
            }
        } catch (Throwable e) {
            // fall through
        }

        // okay, so the text:
        // 1.	didn't change (silently), or
        // 2.	threw some kind of exception while changing

        // solution: tell the user!
        // how can we signal to the user that something catastrophic happened?
        // haha! easy!
        super.setText(
                "Results should be displayed here now, but we are out of memory.\n"
                    + "There is a chance that other buttons on this page MIGHT work. Give them a"
                    + " shot.\n"
                    + "Allocating more memory for this program to run might help, too (see the"
                    + " README file).");

        if (frame != null) {
            MessageBox mb =
                    new MessageBox(
                            frame,
                            "The results cannot be displayed! Write to file?",
                            "Results have been calculated, but cannot be displayed, because of"
                                + " limitations imposed by the operating system. Allocating more"
                                + " memory for this program might help (see the README file).\n\n"
                                + "Would you like me to write the results to a file instead?",
                            MessageBox.MB_YESNO);
            if (mb.showMessageBox() == MessageBox.MB_YES) {
                FileDialog fd =
                        new FileDialog(
                                frame,
                                "Where would you like to save the results?",
                                FileDialog.SAVE);

                fd.setVisible(true); // go!

                while (true) {
                    String filename = "";
                    if (fd.getFile() != null) {
                        if (fd.getDirectory() != null) filename = fd.getDirectory() + fd.getFile();
                        else filename = fd.getFile();
                    } else {
                        // he 'cancelled' the file dialog
                        break;
                    }

                    // So now we have a FileDialog
                    try {
                        PrintWriter writer = new PrintWriter(new FileWriter(filename));

                        writer.print(str);
                        writer.close();

                        return;
                    } catch (IOException e) {
                        MessageBox mbError =
                                new MessageBox(
                                        frame,
                                        "Something went wrong!",
                                        "There was an error writing '"
                                                + filename
                                                + "'. The exact technical description is: "
                                                + e
                                                + "\n\nWould you like to try again?",
                                        MessageBox.MB_YESNO);
                        if (mbError.showMessageBox() == MessageBox.MB_YES) {
                            continue;
                        } else {
                            break;
                        }
                    }
                }
            }
        }
    }
}
