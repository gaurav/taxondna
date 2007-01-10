/**
 * The LociDisplayMode displays locuses (loci), in the order they are present in the file. It's a simple system
 * so we can figure out how Trees work and so on, and get our interfaces in order somewhat before moving onto
 * more interesting things (like the long-mythical FeatureDisplayMode). 
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

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.prefs.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.tree.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class LociDisplayMode extends DisplayMode {
	public LociDisplayMode(ViewManager man) {
		super(man);
	}

	public Object getRoot() {
		if(genBankFile == null)
			return "No file loaded";

		return "Current file (" + genBankFile.getFile().getAbsolutePath() + ")";
	}

	protected java.util.List getSubnodes(Object node) {
		if(genBankFile == null)
			return null;

		if(node.equals(getRoot())) {
			return genBankFile.getLoci();
		}

		int index = getIndexOfChild(getRoot(), node);
		if(index == -1)
			throw new RuntimeException("node not found: " + node);

		GenBankFile.Locus l = genBankFile.getLocus(index);
		if(l != null) {
			return l.getSections();
		}

		return null;
	}
}
