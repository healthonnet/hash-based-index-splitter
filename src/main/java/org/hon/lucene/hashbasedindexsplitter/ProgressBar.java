package org.hon.lucene.hashbasedindexsplitter;

/**
 * Simple command line progress bar
 * 
 * @see http://nakkaya.com/2009/11/08/command-line-progress-bar/
 * 
 * @author nolan
 *
 */
public class ProgressBar {
    
    public static void printProgBar(int percent){
        StringBuilder bar = new StringBuilder("[");

        for(int i = 0; i < 50; i++){
            if( i < (percent/2)){
                bar.append("=");
            }else if( i == (percent/2)){
                bar.append(">");
            }else{
                bar.append(" ");
            }
        }

        bar.append("]   " + percent + "%     ");
        System.out.print("\r" + bar.toString());
    }
    
}
