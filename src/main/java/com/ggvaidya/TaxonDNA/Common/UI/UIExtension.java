/**
 * An interface to allow extension of the SpeciesIdentifier UI. It lists out the basic structure
 * required of any object which wants to extend the UI. The getShortName() and getPanel() are the
 * only ones which actually need to *do* anything, although of course the others must be
 * implemented, atleast as stubs. They are intended for Future Use (if any can be dreamed up).
 *
 * @author Gaurav Vaidya, gaurav@ggvaidya.com
 */

/*
    TaxonDNA
    Copyright (C) 2005	Gaurav Vaidya

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
package com.ggvaidya.TaxonDNA.Common.UI;

import java.awt.*;

public interface UIExtension {
  /**
   * Returns the short name of this extension. This name will be used to identify the extension in
   * menus, forms and the like.
   */
  public String getShortName();

  /**
   * Returns a one-paragraph description of this extension. This will be used to let the user know
   * what this extension is supposed to do.
   */
  public String getDescription();

  /**
   * Used to notify the extension that the data has changed. Check back with the owner to see what's
   * going on.
   */
  public void dataChanged();

  /**
   * If the application has a menu for "commands", and you would like to add anything to this menu,
   * just add it to the commandMenu. Don't end with a separator, we'll handle that. If you have a
   * lot of commands, you're welcome to add a PopupMenu instead.
   *
   * @return true, if a separator needs to be added after whatever you've done. SpeciesIdentifier
   *     won't add a separator if you're the last item in the menu.
   */
  public boolean addCommandsToMenu(Menu commandMenu);

  /**
   * Returns the 'Panel' of this extension. If you return null, there will be no Panel - i.e., your
   * extension will be present but not visible.
   */
  public Panel getPanel();
}
