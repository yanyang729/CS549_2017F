package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class IterMapper extends Mapper<LongWritable, Text, Text, Text> {

    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException,
            IllegalArgumentException {
        String line = value.toString(); // Converts Line to a String
        String[] sections = line.split("\t"); // Splits it into two parts. Part 1: node+rank | Part 2: adj list
        // 5 0.5000	5-1 5-4

        if (sections.length > 2) // Checks if the data is in the incorrect format
        {
            throw new IOException("Incorrect data format");
        }

        if (sections.length != 2) {
            return;
        }

		/* 
         * TODO: emit key: adj vertex, value: computed weight.
		 * 
		 * Remember to also emit the input adjacency list for this node!
		 * Put a marker on the string value to indicate it is an adjacency list.
		 */

        String weight = sections[0].split(" ")[1];


        String[] adjList = sections[1].trim().split(" ");

        double weight_avg = Double.parseDouble(weight) / adjList.length;

        for (String adj : adjList) {

            String outKey = adj.split("-")[1];
            context.write(new Text(outKey), new Text(String.format("%.4f",weight_avg)));
        }

        String outKey_adj = sections[0].split(" ")[0];
        String outValue_adj = "##" + sections[1].trim();
        context.write(new Text(outKey_adj), new Text(outValue_adj));


    }


}


