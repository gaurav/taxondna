/**
 * A DelayAbortedException means that a DelayCallback needs to
 * let the Delay know to stop, stop now, probably because the
 * user wants it to.
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 *
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
 * 
 */

package com.ggvaidya.TaxonDNA.Common;

public class DelayAbortedException extends Exception {
	/**
	 * A generated serialVersionUID
	 */
	private static final long serialVersionUID = -470511092703466979L;

	public DelayAbortedException() {
		super();
	}

	public DelayAbortedException(String message) {
		super(message);
	}

	public DelayAbortedException(String message, Throwable cause) {
		super(message, cause);
	}

	public DelayAbortedException(Throwable cause) {
		super(cause);
	}	
}
