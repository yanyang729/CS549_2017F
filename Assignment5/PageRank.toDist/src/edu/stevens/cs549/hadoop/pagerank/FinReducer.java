package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class FinReducer extends Reducer<DoubleWritable, Text, Text, Text> {

	public void reduce(DoubleWritable key, Iterable<Text> values, Context context) throws IOException,
			InterruptedException {
		/* 
		 * TODO: For each value, emit: key:node, value:-rank
		 */
		for (Text t : values) {
			context.write(t, new Text(key.toString()));
		}
	}
}
