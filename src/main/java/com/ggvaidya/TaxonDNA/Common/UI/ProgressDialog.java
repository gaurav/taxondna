/**
 * Displays a progress bar Dialog in the screen using DelayCallback. The only important point to
 * note is that you should not create a ProgressDialog while in EventQueue. Instead, fork a new
 * thread and use that to run this. And make sure that exception-catching code calls
 * ProgressDialog.end()! Otherwise, nasty, nasty things might happen.
 *
 * <p>The way this is supposed to work is: 1. foo() is a function which needs to do something which
 * will take some time. So it instantiates ProgressDialog, sends it to another function,
 * bar(DelayCallback), which (as you can see) expects a delay callback. 2. bar() treats this exactly
 * like a DelayCallback. The only thing to remember is: 1. We can throw DelayAbortedExceptions if
 * somebody hits the "abort" key, so this'll have to be watched out for. 2. bar() MUST call
 * delay.end() before leaving. foo() can assume the ProgressDialog has been completely closed by the
 * time bar() returns.
 *
 * <p>The justification for this is, since bar() does the potentially delaying work, bar() might
 * have a case where it does NOT display the dialog at all, for instance, if it realises that it's
 * work is already done, etc. Also, it propagates the idea of the DelayCallback as a pretty throw
 * away sort of thing.
 *
 * @author Gaurav Vaidya, 2005
 */

/*
    TaxonDNA
    Copyright (C) 2005, 2006, 2010	Gaurav Vaidya

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

import com.ggvaidya.TaxonDNA.Common.*;
import java.awt.*;
import java.awt.event.*;

public class ProgressDialog extends Dialog
        implements DelayCallback, ActionListener, Runnable, WindowListener {
    public static final int FLAG_NOMODAL = 1;
    public static final int FLAG_NOCANCEL = 2;
    private TextArea textarea;
    private String message;
    private StringBuffer buff_warnings = new StringBuffer();
    private boolean warnings_added = false;

    private Frame frame = null;
    private String title = null;

    private boolean abort = false;

    private ProgressBar pi;

    private static ProgressDialog singleton;

    /**
     * @param f
     * @param title
     * @param message
     * @param flags (currently ignored) Can be FLAG_NOMODAL or FLAG_NOCANCEL or both OR'd together.
     * @return
     */
    public static ProgressDialog create(Frame f, String title, String message, int flags) {
        if (singleton == null) singleton = new ProgressDialog(f, title, message, 0);
        else {
            singleton.setTitle(title);
            singleton.setMessage(message);

            singleton.reset();

            singleton.pack();
        }

        return singleton;
    }

    public static ProgressDialog create(Frame f, String title, String message) {
        return ProgressDialog.create(f, title, message, 0);
    }

    /**
     * Creates a ProgressDialog, which, like any dialog, doesn't actually do anything magical until
     * you call begin().
     */
    private ProgressDialog(Frame f, String title, String message, int flags) {
        super(f, title, ((flags & FLAG_NOMODAL) == 0));

        frame = f;
        this.title = title;

        setSize(220, 190);
        setResizable(false);
        setLayout(new BorderLayout());

        this.message = message;

        textarea = new TextArea(message, 5, 50, TextArea.SCROLLBARS_VERTICAL_ONLY);
        textarea.setEditable(false);
        add(textarea, BorderLayout.CENTER);

        Panel south = new Panel();
        south.setLayout(new BorderLayout());

        pi = new ProgressBar("Please wait ...");
        south.add(pi);

        /*
        if((flags & FLAG_NOCANCEL) == 0) {
        	Button btnAbort = new Button("Cancel");
        	btnAbort.setActionCommand("Cancel");
        	btnAbort.addActionListener(this);
        	south.add(btnAbort, BorderLayout.EAST);
        }*/

        addWindowListener(this);

        add(south, BorderLayout.SOUTH);

        pack();
    }

    public void setTitle(String title) {
        super.setTitle(title);
        this.title = title;
    }

    public void setMessage(String message) {
        textarea.setText(message);
        this.message = message;
    }

    public void reset() {
        pi.changeIndicator(0, 0);
        lastPercentage = 0;
    }

    /** Add a warning. All warnings are concatenated and displayed just after end(). */
    public void addWarning(String x) {
        warnings_added = true;
        buff_warnings.append(" - " + x + "\n");
    }

    /** Our begin() creates a new thread for the ProgressDialog to run in. */
    public void begin() {
        // System.err.println("begin() called");
        new Thread(this, "Progress Dialog").start();
    }

    /**
     * Our run() is in a new thread; this allows setVisible to happily block us and the program will
     * continue to run, while still being modal (and blocking input to the mainFrame).
     */
    public void run() {
        // System.err.println("run() began");
        setVisible(true);
        // System.err.println("run() ended");
    }

    /**
     * This function causes the window to close and dispose itself. This will cause the new thread
     * we forked to keep the setVisible in to die. This function MUST be called once the delay is
     * "done", because leaving a modal dialog in front of your main frame is no way to run a
     * program.
     */
    public void end() {
        while (!isVisible())
            ;
        setVisible(false);
        // dispose(); -- TODO: Don't dispose of it! Leave it in 'singleton' for us to find.

        if (warnings_added) {
            MessageBox mb =
                    new MessageBox(
                            frame,
                            "Warnings: " + title,
                            "The following warnings were generated:\n" + buff_warnings.toString(),
                            MessageBox.MB_OK | MessageBox.MB_TITLE_IS_UNIQUE);
            mb.go();

            warnings_added = false;
            buff_warnings = new StringBuffer();
        }
    }

    private float lastPercentage = 0;

    /**
     * The delay function. Right now, we just change our text to report the percentage done, and
     * inform our ProgressBar about the change.
     *
     * <p>Big change: now, WE will handle the reporting, i.e. unless the new fraction is
     * significantly (0.1%) larger than the last one, we will ignore it. This means EVERYBODY -
     * effective immediately - must use delay.delay() without going through that interval shit.
     */
    public void delay(int done, int total) throws DelayAbortedException {
        if (!isVisible()) return;

        // System.err.println("delay(" + done + ", " + total + " called");

        float percentage = (float) done / total * 100;
        if (percentage == 0 || Math.abs(percentage - lastPercentage) > 0.5) {
            textarea.setText(message + "\n\nPercentage done: " + percentage + "%");
            pi.changeIndicator(done, total);
            lastPercentage = percentage;
        }

        if (abort) {
            end();
            throw new DelayAbortedException();
        }
    }

    /**
     * actionPerformed, used to set up a "Cancel" button. Once we actually have a cancel button,
     * it'll be really cool. We promise.
     */
    public void actionPerformed(ActionEvent e) {
        abort = true;
    }

    public void windowClosing(WindowEvent e) {
        abort = true;
    }

    public void windowActivated(WindowEvent e) {}

    public void windowClosed(WindowEvent e) {}

    public void windowDeactivated(WindowEvent e) {}

    public void windowDeiconified(WindowEvent e) {}

    public void windowIconified(WindowEvent e) {}

    public void windowOpened(WindowEvent e) {}

    /*
    public static void main(String[] args) throws DelayAbortedException {
    	Frame f = new Frame("Take a good look around you");
    	f.setSize(150, 180);
    	f.addWindowListener(new WindowAdapter() {
    		public void windowClosing(WindowEvent e) {
    			System.exit(0);
    		}
    	});

    	f.setVisible(true);

    	ProgressDialog pb = new ProgressDialog(f, "Please wait while I twiddle my fingers", "Please wait ...", 0);

    	int max = 10000000;
    	pb.begin();
    	for(int x = 0; x < max; x++) {
    		if(x % 1000 == 0)
    			pb.delay(x, max);
    	}
    	pb.end();

    }
    */
}
