package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class InitReducer extends Reducer<Text, Text, Text, Text> {

    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        /*
		 * TODO: Output key: node+rank, value: adjacency list
		 */
        for (Text v : values) {
            // actually only executed once
            String[] outLinks = v.toString().split(" ");
            double len = outLinks.length;
            double weight = 1 / len;
            String outKey = key.toString() + " " + String.format("%.4f", weight);
            String outputValue = "";
            for (String l : outLinks) {
                outputValue += key.toString() + "-" + l + " ";
            }

            context.write( new Text(outKey), new Text(outputValue));
        }
    }
}
