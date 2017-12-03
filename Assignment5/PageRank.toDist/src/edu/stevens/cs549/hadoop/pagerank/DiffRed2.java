package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public class DiffRed2 extends Reducer<Text, Text, Text, Text> {

	public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
		double diff_max = 0.0; // sets diff_max to a default value
		/* 
		 * TODO: Compute and emit the maximum of the differences
		 */

		for ( Text t : values) {
			double diff = Double.parseDouble(t.toString());
			if( diff > diff_max) diff_max = diff;
		}

		context.write(new Text("diff_max"), new Text(String.format("%.4f", diff_max)));
	}
}
