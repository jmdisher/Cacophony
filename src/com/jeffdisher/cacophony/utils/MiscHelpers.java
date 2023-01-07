package com.jeffdisher.cacophony.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Basic utilities for miscellaneous uses.
 */
public class MiscHelpers
{
	/**
	 * Converts a given number of bytes into a human-readable string.
	 * 
	 * @param bytes The raw number of bytes.
	 * @return A human-readable string.
	 */
	public static String humanReadableBytes(long bytes)
	{
		String firstPart = null;
		if (bytes >= 1_000_000_000L)
		{
			firstPart = _getMagnitudeString(bytes, 1_000_000_000d, "GB");
		}
		else if (bytes >= 1_000_000L)
		{
			firstPart = _getMagnitudeString(bytes, 1_000_000d, "MB");
		}
		else if (bytes >= 1_000L)
		{
			firstPart = _getMagnitudeString(bytes, 1_000d, "kB");
		}
		return (null != firstPart)
				? (firstPart + " (" + bytes + " bytes)")
				: (bytes + " bytes")
		;
	}

	/**
	 * Copies all the data from the input stream to the output stream, stopping once input reaches end of file.
	 * 
	 * @param input The input stream to read.
	 * @param output The output stream to write.
	 * @return The number of bytes copied.
	 * @throws IOException If there is an error interacting with the streams.
	 */
	public static long copyToEndOfFile(InputStream input, OutputStream output) throws IOException
	{
		long totalCopied = 0L;
		boolean reading = true;
		byte[] data = new byte[4096];
		while (reading)
		{
			int read = input.read(data);
			if (read > 0)
			{
				output.write(data, 0, read);
				totalCopied += (long)read;
			}
			else
			{
				reading = false;
			}
		}
		return totalCopied;
	}


	private static String _getMagnitudeString(long bytes, double magnitude, String suffix)
	{
		double direct = (double)bytes / magnitude;
		return String.format("%.2f %s", direct, suffix);
	}
}
