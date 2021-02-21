/**
 * A Testable class is one which can be tested. That wasn't too hard.
 * To specify in additional detail, all Testable objects have a
 * function called 'test()', which runs a series of tests. The
 * results are signalled back to the caller via a callback
 * mechanism (see Common.TestController). 
 *
 * @author Gaurav Vaidya gaurav@ggvaidya.com 
 */

/*
 * TaxonDNA
 * Copyright (C) 2005 Gaurav Vaidya
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

public interface Testable {
	/**
	 * The test which is used to test this object.
	 * Results are signalled to the TestController
	 * via the passed TestController interface.
	 *
	 * Since 'test' might take a while, a DelayCallback 
	 * can be used to let the user know.
	 *
	 * This really ought to be static, but this Cannot Be
	 * in the java world. So I'm going to rely on
	 * implementations of 'test' to not confuse themselves
	 * with the core class itself. 
	 */
	public void test(TestController controller, DelayCallback delay) throws DelayAbortedException;
}
