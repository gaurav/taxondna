package com.ggvaidya.TaxonDNA.DNA;

  public class FromToPair implements Comparable {

                     public int from;
                     public int to;
      
                     public FromToPair(int from, int to) {
                                 this.from = from;
                                 this.to = to;
                         }
          
                         public int compareTo(Object o) {
                                     FromToPair ftp = (FromToPair) o;
              
                                     return (this.from - ftp.from);
                             }
                     }

