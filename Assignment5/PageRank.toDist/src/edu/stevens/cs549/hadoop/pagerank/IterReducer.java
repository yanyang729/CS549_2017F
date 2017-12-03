package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class IterReducer extends Reducer<Text, Text, Text, Text> {

    public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        double d = PageRankDriver.DECAY; // Decay factor
        /*
		 * TODO: emit key:node+rank, value: adjacency list
		 * Use PageRank algorithm to compute rank from weights contributed by incoming edges.
		 * Remember that one of the values will be marked as the adjacency list for the node.
		 */

        String adjList = "";
        double weight = 0;


        for (Text v : values){
            String vString = v.toString();
            if( vString.startsWith("##")) {
                adjList = vString.substring(2);
            } else {
                weight += Double.parseDouble(v.toString());
            }
        }

        String outKey = key.toString() + " " + String.format("%.4f", (1-d) + d*weight);
        String outValue = adjList;
        context.write(new Text(outKey) ,new Text(outValue));

    }
}
