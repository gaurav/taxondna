/**
 * A FormatException is thrown if any of the formats here don't like something. FormatException is a
 * generic error meaning "something went wrong trying to figure this format out"; it could be an
 * IOException, SequenceSetException, etc. We wrap it all up, but you can get the underlying
 * exception by using getCause().
 */

/*
    TaxonDNA
    Copyright (C) 2005 Gaurav Vaidya

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

package com.ggvaidya.TaxonDNA.Common.DNA.formats;

public class FormatException extends Exception {
    private static final long serialVersionUID = -8796431763478134968L;

    public FormatException() {
        super();
    }

    public FormatException(String message) {
        super(message);
    }

    public FormatException(String message, Throwable cause) {
        super(message, cause);
    }

    public FormatException(Throwable cause) {
        super(cause);
    }
}
