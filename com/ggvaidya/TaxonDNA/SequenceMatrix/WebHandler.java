/**
 * The WebHandler allows application users to access features on the internet.
 * This first feature (and the reason why this code was written at all) was to
 * come up with a way of doing NCBI BLAST directly from the application.
 */

/*
 *
 *  SequenceMatrix
 *  Copyright (C) 2010 Gaurav Vaidya
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

package com.ggvaidya.TaxonDNA.SequenceMatrix;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

import javax.swing.*;		// "Come, thou Tortoise, when?"
import javax.swing.event.*;
import javax.swing.table.*;

import com.ggvaidya.TaxonDNA.Common.*;
import com.ggvaidya.TaxonDNA.DNA.*;
import com.ggvaidya.TaxonDNA.DNA.formats.*;
import com.ggvaidya.TaxonDNA.UI.*;

public class WebHandler {
    private SequenceMatrix      matrix 	= null;     // the SequenceMatrix object

    /** 
        The constructor does nothing but set up matrix. this is 
        because WebHandler will probably not be used as often as
        you think, so we might as well do as little as possible
        until explicitly commanded to.
    */

    public WebHandler(SequenceMatrix matrix) {
        this.matrix = matrix;
    }
}
