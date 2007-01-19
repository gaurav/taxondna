/**
 * A GenBankFile.FeatureBin stores a whole set of GenBankFile.Features, then returns vectors of them
 * on the basis of certain criteria. Since GenBankFile.Features contain all relevant
 * information - including the locus they belong to - they can be shifted
 * around without hurting anything, and anyways we're only making lists
 * of pointers anyway. 
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

import java.io.*;
import java.util.*;
import java.util.prefs.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class FeatureBin {
	private List features = 	new LinkedList();

	public class FeatureList extends LinkedList implements SequenceContainer {
		String name = null;

		public FeatureList(String name) {
			this.name = name;
		}

		public void addFeature(GenBankFile.Feature f) {
			add(f);
		}

		public void addFeatures(Collection c) {
			addAll(c);
		}

		public String getName() {
			return name;
		}

		public SequenceList getAsSequenceList() {
			return null;
		}

		public List alsoContains() {
			return this;	// we contain ourselves
		}

		public String toString() {
			return getName();
		}
	}

	public FeatureBin() {
	}

	public FeatureBin(Collection features) {
		addAll(features);
	}

	public FeatureBin(GenBankFile gbf) {
		Iterator i = gbf.getLoci().iterator();
		while(i.hasNext()) {
			GenBankFile.Locus l = (GenBankFile.Locus) i.next();

			Object o = l.getSection("FEATURES");
			if(o == null)	// no 'FEATURES' section
				continue;
			if(GenBankFile.FeaturesSection.class.isAssignableFrom(o.getClass())) {
				// valid!
				GenBankFile.FeaturesSection sec = (GenBankFile.FeaturesSection) o;

				Iterator i2 = sec.getFeatures().iterator();
				while(i2.hasNext()) {
					GenBankFile.Feature f = (GenBankFile.Feature) i2.next();

					add(f);
				}
			}
		}	
	}

	public void clear() {
		features = new LinkedList();
	}

	public void addAll(Collection c) {
		features.addAll(c);
	}
	
	public void add(GenBankFile.Feature f) {
		features.add(f);
	}

	public List getGenes() {
		Hashtable ht_genes = new Hashtable();
		boolean misc_genes = false;

		Iterator i = features.iterator();
		while(i.hasNext()) {
			GenBankFile.Feature f = (GenBankFile.Feature) i.next();

			List s = f.getValues("/gene");
			if(s != null) {
				Iterator i2 = s.iterator();
				while(i2.hasNext()) {
					ht_genes.put(i2.next(), new Object());
				}
			} else {
				misc_genes = true;
			}
		}

		Vector v = new Vector(ht_genes.keySet());
		Collections.sort(v);
		if(misc_genes)
			v.add("No gene specified");

		// TODO: Stop the insanity
		LinkedList ll = new LinkedList();
		i = v.iterator();
		while(i.hasNext()) {
			String gene = (String) i.next();	

			ll.add(getByGene(gene));
		}

		return ll;
	}

	public FeatureList getByGene(String gene) {
		boolean nullSearch = false;
		FeatureList fs = new FeatureList("Features containing gene " + gene);

		if(gene.equals("No gene specified")) {
			nullSearch = true;
			fs = new FeatureList("No gene specified");
		}

		Iterator i = features.iterator();
		while(i.hasNext()) {
			GenBankFile.Feature f = (GenBankFile.Feature) i.next();

			List s = f.getValues("/gene");
			if(s != null) {
				if(s.contains(gene))
					fs.addFeature(f);
			} else if(nullSearch) {
				fs.addFeature(f);
			}
		}

		Collections.sort(fs);
		return fs;
	}
}
