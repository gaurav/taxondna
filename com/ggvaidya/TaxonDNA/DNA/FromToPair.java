package com.ggvaidya.TaxonDNA.DNA;

public class FromToPair implements Comparable {
    public int from;
    public int to;

    public FromToPair(int from, int to) {
        if(from < 0 || to < 0)
            throw new RuntimeException("FromTo of " + from + " to " + to + " invalid: less than zero!");

        if(to < from) 
            throw new RuntimeException("FromTo of " + from + " to " + to + " invalid: incorrect order!");

        this.from = from;
        this.to = to;
    }

    public int compareTo(Object o) {
        FromToPair ftp = (FromToPair) o;

        return (this.from - ftp.from);
    }
}

