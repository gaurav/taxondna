/**
 * An UndoStack encapsulated a Stack which keeps a list of 'tasks' executed. Tasks are defined by
 * UI.UndoTask.
 *
 * <p>One important thing which UndoStack needs to be capable of doing is to be as memory efficient
 * as possible. It's likely that once an object is deleted, we will be the only people to refer to
 * it; thus, it is our responsibility to free it as soon as is feasible.
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

package com.ggvaidya.TaxonDNA.Common.UI;

public class UndoStack {
  private UndoTask[] tasks;

  public UndoStack(int size) {
    tasks = new UndoTask[size];
  }

  public int getSize() {
    return tasks.length;
  }

  public UndoTask getTask(int x) {
    if (x < tasks.length) return tasks[x];
    return null;
  }

  public void push(UndoTask push) {
    tasks[0] = null; // die, old task, die!
    for (int x = 1; x < tasks.length - 1; x++) {
      tasks[x - 1] = tasks[x]; // shift up
    }
    tasks[tasks.length - 1] = push; // last one is shifted into [$#tasks], in perl terms
  }

  public UndoTask pop() {
    for (int x = 1; x < tasks.length - 1; x++) {
      // ***************
    }
    return null;
  }
}
