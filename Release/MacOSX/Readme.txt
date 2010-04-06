CHANGING THE MEMORY LIMIT FOR SEQUENCEMATRIX
---------------------------------------------

Loading large datasets in SequenceMatrix can take a lot
of memory. Unfortunately, Java applications like
SequenceMatrix have strict memory limits and -- at the
moment -- the program is unable to determine when its 
memory is at risk of running out. If the application 
abruptly stops responding while loading a file, increase 
the memory limit as described in this file.

1. Right-click on the "Sequence Matrix" application in
   Finder, and select the "Show Package Contents" option. 
   A new folder called "Contents" will now open in Finder.
2. Open the "Contents" folder, and double-click on the
   Info.plist file. This will either open in Textedit or
   the Property List Editor.
3. Find the "Java" item, and open it by clicking on the 
   triangle beside it. Look for the "VMOptions" property. 
   It should have a value of -Xmx1000m. This indicates that 
   the memory limit is set at 1000M (1,000 Megabytes). To 
   edit this value, double-click on the "-Xmx1000m" text 
   and change the '1000' to a higher number. For instance, 
   -Xmx2000M would increase the limit to 2,000 Megabytes.
4. Once the value has changed, use the File -> Save option 
   to save your changes. Close the Property List Editor 
   and the Contents window in Finder.
5. The application can now be executed by double-clicking 
   on its icon as usual.

Note that if you set the memory limit too high (say, to
-Xmx2000M when the computer only has 2GB of memory),
Java might refuse to start at all. If this happens, set
the memory to a lower value (in this example, to perhaps
-Xmx1500M) and try again.
