/**
 * A TestController carries out Tests by calling the 'test()' method
 * of Testable objects. The test signals results back to TestController
 * via four methods: testInformation(title, description), testBegin(title),
 * testSuccess(title), testFailure(title, description).
 *
 * The Testable objects are responsible for their own issues - the
 * TestController will only report test status back to the user.
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

import java.io.*;

public interface TestController {
	public void begin(String title);
	public void done();

	public void beginTest(String testName);
	public void failed(String description);
	public void succeeded();
	public void information(String information);
	
	public File file(String name);
	public File tempfile();
	public boolean isIdentical(File x, File y);
}
