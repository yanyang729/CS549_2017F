package edu.stevens.cs549.hadoop.pagerank;

import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class FinMapper extends Mapper<LongWritable, Text, DoubleWritable, Text> {

	public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException, IllegalArgumentException {
		String line = value.toString(); // Converts Line to a String
		/*
		 * TODO output key:-rank, value: node
		 * See IterMapper for hints on parsing the output of IterReducer.
		 */

		String[] sections = line.split("\t");
		String node = sections[0].split(" ")[0];
		double rank = Double.parseDouble(sections[0].split(" ")[1]);

		context.write(new DoubleWritable(rank), new Text(node));

	}

}
