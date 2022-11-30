package com.jeffdisher.cacophony.utils;


/**
 * Basic utilities for string manipulation.
 */
public class StringHelpers
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


	private static String _getMagnitudeString(long bytes, double magnitude, String suffix)
	{
		double direct = (double)bytes / magnitude;
		return String.format("%.2f %s", direct, suffix);
	}
}
