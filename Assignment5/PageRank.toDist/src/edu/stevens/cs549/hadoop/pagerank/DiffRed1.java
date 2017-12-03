package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class DiffRed1 extends Reducer<Text, Text, Text, Text> {

    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        double[] ranks = new double[2];
        /*
		 * TODO: The list of values should contain two ranks.  Compute and output their difference.
		 */


        int i = 0;
        for (Text t : values) {
            // loop 2 times, find pairs and calculate diff
           ranks[i] =  Double.parseDouble(t.toString());
           i ++;
        }

        double diff = Math.abs(ranks[0] - ranks[1]);

        context.write(key, new Text(Double.toString(diff)));

    }
}
