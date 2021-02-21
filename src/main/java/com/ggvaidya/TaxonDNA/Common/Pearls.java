/**
 * Pearls. Because some of us wish we were coding in Perl.
 *
 * (Note: this file was hacked together as coding continued,
 * basically any time I pined for Perl. The functions do not
 * attempt to fully, or even partially, emulate their 
 * corresponding Perl functions or actions. They only do as
 * much as my program needs them to do.)
 *
 * @author Gaurav Vaidya gaurav@ggvaidya.com 
 */

/*
 * TaxonDNA
 * Copyright (C) 2007 Gaurav Vaidya
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 */

package com.ggvaidya.TaxonDNA.Common;

import java.util.*;

public class Pearls {
	/**
	 * 'Joins' a Collection into a String.
	 *
	 * Analagous to join()
	 */
	public static String join(String joiner, Collection coll) {
		StringBuffer buff = new StringBuffer();

		Iterator i = coll.iterator();
		while(i.hasNext()) {
			String str = i.next().toString();
			buff.append(str);
			if(i.hasNext())
				buff.append(joiner);
		}

		return buff.toString();
	}

	/**
	 * 'Joins' a Collection into a String. This function allowes 
	 * you to denote both a 'before' and an after'. For instance,
	 * 	repeat('A', @list, 'B')
	 * will give you 
	 * 	A$list[0]BA@list[1]B...
	 *
	 * It's the best I can do without continuations, I guess =/
	 */
	public static String repeat(String before, Collection coll, String after) {
		StringBuffer buff = new StringBuffer();

		Iterator i = coll.iterator();
		while(i.hasNext()) {
			String str = i.next().toString();

			buff.append(before);
			buff.append(str);
			buff.append(after);
		}

		return buff.toString();
	}
}
