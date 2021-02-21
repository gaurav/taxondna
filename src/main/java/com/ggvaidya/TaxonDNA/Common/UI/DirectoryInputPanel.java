/**
 * DirectoryInputPanel
 *
 * <p>As the name suggests, this is a Panel containing a TextField and a "Browse ..." button; you
 * pick a file, or specify one yourself.
 *
 * <p>Special features: 1. The programmer decides whether you are 'saving' or 'loading', like with a
 * FileDialog. We *are* smart enough to figure out what you mean, and why you mean it. 2. Partial
 * names will be resolved into full names. 3. (the reason why this was written) If the user typed in
 * a filename, we will VERIFY that it makes sense and DOES exist (if it's to be LOADED) or DOES NOT
 * exist (if it's to be SAVED). In case a getFile() request is made of us, we will test this, etc.
 *
 * <p>Issues: 1. Can't do directories. This is because the underlying FileDialog doesn't do
 * directories.
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
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

public class DirectoryInputPanel extends Panel implements ActionListener, TextListener {
  Window frame = null;
  int mode = 0;
  String label = null;
  TextField tf = new TextField();
  Button browse = new Button("Browse ...");

  boolean fileLoadedThroughDialog = false;

  public static final int MODE_DIR_CREATE = 0x01;
  public static final int MODE_DIR_SELECT = 0x02;

  /**
   * Set us up a very simple file input panel. This is just a big TextField, with a "Browse ..."
   * button on one side.
   *
   * <p>If a 'label' is specified, we will add that as well. It will be added to the left of the
   * 'file input' part of the dialog.
   */
  public DirectoryInputPanel(String label, int mode, Window frame) {
    this.frame = frame;
    this.mode = mode;
    this.label = label;

    if (label == null) {
      setLayout(new BorderLayout());
    } else {
      setLayout(new BorderLayout(5, 0));
      add(new Label(label), BorderLayout.WEST);
    }

    tf.addTextListener(this);
    add(tf);

    browse.addActionListener(this);
    add(browse, BorderLayout.EAST);
  }

  /** We should atleast _try_ and look presentable */
  public Insets getInsets() {
    if (label == null) return new Insets(0, 0, 0, 0);
    return new Insets(3, 3, 3, 3);
  }

  /** Somebody hit the "Browse" button! */
  public void actionPerformed(ActionEvent e) {
    if (e.getSource().equals(browse)) {
      String message = "";
      if (mode == MODE_DIR_CREATE) message = "Please specify a new directory to create ...";
      else if (mode == MODE_DIR_SELECT) message = "Please select a directory to use ...";
      else message = "Please select a directory ...";

      File f = getFileFromFileDialog(message);

      if (f != null) {
        tf.setText(f.getAbsolutePath());
        fileLoadedThroughDialog = true;
      }
    }
  }

  public File getFileFromFileDialog(String message) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(message);
    chooser.setDialogType(
        (mode == MODE_DIR_SELECT ? JFileChooser.OPEN_DIALOG : JFileChooser.SAVE_DIALOG));
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    if (chooser.showDialog(frame, "Use this directory") == JFileChooser.APPROVE_OPTION) {
      File f = new File(chooser.getSelectedFile().getAbsolutePath());
      chooser = null;
      System.gc();
      return f;
    }

    chooser = null;
    System.gc();

    return null;
  }

  /** Returns the current text content as a File */
  public File getFile() {

    // if the file is empty, you get NOTHING!
    if (tf.getText().trim().equals("")) return null;

    // if the file is NOT empty, things get very tricky very quickly:
    File f = new File(tf.getText());
    tf.setText(f.getAbsolutePath());
    f = new File(f.getAbsolutePath()); // ha-ha!

    // 1.	If we are a SAVEFILE:
    // 	(a)	Already existing files will require a prompt, unless
    // 		they've already been prompted.
    // 	(b)
    if (mode == MODE_DIR_CREATE) {
      if (f.exists()) {
        // oh no! the file exists!
        if (fileLoadedThroughDialog) {
          // the dialog should already have warned the user, don't do anything
        } else {
          // warn the user!
          MessageBox mb =
              MessageBox.createFromWindow(
                  frame,
                  "Warning: the specified directory exists!",
                  "Are you sure you want to overwrite '" + f + "'?",
                  MessageBox.MB_YESNO);
          if (mb.showMessageBox() != MessageBox.MB_YES) {
            f = getFileFromFileDialog("Please choose another directory to use instead ...");
            if (f == null) return null;

            tf.setText(f.getAbsolutePath());
            return f;
          }
        }
      }
    } else if (mode == MODE_DIR_SELECT) {
      if (f.exists()) {
        // yay!
        return f;
      } else {
        // err .. wops!
        MessageBox mb =
            MessageBox.createFromWindow(
                frame,
                "Warning: the specified directory doesn't exist!",
                "There is no directory named '"
                    + f
                    + "'!\n\nWould you like to specify a directory to select now?",
                MessageBox.MB_YESNO);

        if (mb.showMessageBox() == MessageBox.MB_YES) {
          f = getFileFromFileDialog("Please choose a directory to use instead ...");

          if (f == null) return null;

          tf.setText(f.getAbsolutePath());
          return f;
        } else {
          return null;
        }
      }
    }

    // Okay, let the user have 'f'
    return f;
  }

  public void setFile(File f) {
    if (f == null) tf.setText("");
    else tf.setText(f.getAbsolutePath());
  }

  /**
   * Listens for changes to the textfield. This way, we KNOW if somebody modified the field outside
   * of the browse dialog.
   */
  public void textValueChanged(TextEvent e) {
    fileLoadedThroughDialog = false;
  }
}
