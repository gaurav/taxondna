/**
 * The LociDisplayMode displays locuses (loci), in the order they are present in the file. It's a
 * simple system so we can figure out how Trees work and so on, and get our interfaces in order
 * somewhat before moving onto more interesting things (like the long-mythical FeatureDisplayMode).
 */

/*
 *
 *  GenBankExplorer
 *  Copyright (C) 2007 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.GenBankExplorer;

import com.ggvaidya.TaxonDNA.Common.DNA.*;
import java.util.*;
import javax.swing.tree.*;

public class LociDisplayMode extends DisplayMode {
  public LociDisplayMode(ViewManager man) {
    super(man);
  }

  public void setGenBankFile(GenBankFile genBankFile) {
    super.setGenBankFile(genBankFile);

    if (genBankFile != null) {
      viewManager.setFileText(
          "Current file: "
              + genBankFile.getFile().getAbsolutePath()
              + "\nNumber of loci in file: "
              + genBankFile.getLocusCount());
    } else {
      viewManager.setFileText("No file loaded.");
    }
  }

  public Object getRoot() {
    if (genBankFile == null) return "No file loaded";

    return "Current file (" + genBankFile.getFile().getAbsolutePath() + ")";
  }

  protected java.util.List getSubnodes(Object node) {
    if (genBankFile == null) return null;

    if (node.equals(getRoot())) {
      return genBankFile.getLoci();
    }

    int index = getIndexOfChild(getRoot(), node);
    if (index == -1)
      // means that we're probably looking for a sub-sub-node
      ;
    else {
      GenBankFile.Locus l = genBankFile.getLocus(index);
      if (l != null) {
        return l.getSections();
      }
    }

    return null;
  }

  public void pathSelected(TreePath p) {
    Object obj = p.getLastPathComponent();
    Class cls = obj.getClass();

    if (cls.equals(String.class)) {
      viewManager.setSelectionText("");

    } else if (cls.equals(GenBankFile.Locus.class)) {
      GenBankFile.Locus l = (GenBankFile.Locus) obj;

      StringBuffer buff = new StringBuffer();
      Iterator i_sec = l.getSections().iterator();
      while (i_sec.hasNext()) {
        GenBankFile.Section sec = (GenBankFile.Section) i_sec.next();

        buff.append(sec.getName() + ": " + sec.entry() + "\n");
      }

      viewManager.setSelectionText("Currently selected: locus " + l.toString() + "\n" + buff);

    } else if (GenBankFile.Section.class.isAssignableFrom(cls)) {
      GenBankFile.Section sec = (GenBankFile.Section) obj;

      String additionalText = "";

      if (GenBankFile.OriginSection.class.isAssignableFrom(cls)) {
        GenBankFile.OriginSection ori = (GenBankFile.OriginSection) obj;
        Sequence seq = null;

        try {
          seq = ori.getSequence();
          additionalText = "Sequence:\n" + seq.getSequenceWrapped(70);

        } catch (SequenceException e) {
          additionalText =
              "Sequence: Could not be extracted.\nThe following error occured while parsing sequence: "
                  + e.getMessage();
        }
      }

      viewManager.setSelectionText(
          "Currently selected: section "
              + sec.getName()
              + " of locus "
              + sec.getLocus()
              + "\nValue: "
              + sec.entry()
              + "\n"
              + additionalText);
    }
  }
}
