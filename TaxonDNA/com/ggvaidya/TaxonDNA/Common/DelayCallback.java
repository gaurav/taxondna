/**
 * Interface for DelayCallbacks. Used to keep track of a long process.
 * A function taking a long time can call the DelayCallback object to 
 * let it know how things are going.
 *
 * There is now an additional bonus: the DelayCallback object can use 
 * the delay(done, total) function to (possibly) abort the process. If
 * the user indicates he wants the process aborted, delay(done, total)
 * will return false.
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

public interface DelayCallback {
	/**
	 * This function is called when the delay begins.
	 */
	public void begin(); 

	/**
	 * This function is called when the delay ends.
	 */
	public void end();

	/**
	 * This function is called during the delay.
	 * @param done	number of steps which have been carried out
	 * @param total	number of steps which have to be done 
	 * @throws DelayAbortedException if the delay was interrupted (presumably by the user)
	 */
	public void delay(int done, int total) throws DelayAbortedException;

	/**
	 * This func-err, method - can be called by the delaying component
	 * to indicate a *warning* which isn't serious enough to stop the
	 * process, but the user should be informed. The DelayCallback
	 * should inform the user after end() gets called.
	 */
	public void addWarning(String warning);
}
