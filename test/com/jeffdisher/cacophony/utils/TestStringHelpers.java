package com.jeffdisher.cacophony.utils;

import org.junit.Test;


public class TestStringHelpers
{
	@Test
	public void testSmall()
	{
		long bytes = 657l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("657 bytes", readable);
	}

	@Test
	public void testKEven()
	{
		long bytes = 2_000l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("2.00 kB (2000 bytes)", readable);
	}

	@Test
	public void testKOdd()
	{
		long bytes = 3_567l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("3.57 kB (3567 bytes)", readable);
	}

	@Test
	public void testMEven()
	{
		long bytes = 2_000_000l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("2.00 MB (2000000 bytes)", readable);
	}

	@Test
	public void testMOdd()
	{
		long bytes = 3_987_567l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("3.99 MB (3987567 bytes)", readable);
	}

	@Test
	public void testGEven()
	{
		long bytes = 2_000_000_000l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("2.00 GB (2000000000 bytes)", readable);
	}

	@Test
	public void testGOdd()
	{
		long bytes = 3_123_987_567l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("3.12 GB (3123987567 bytes)", readable);
	}

	@Test
	public void testIndirectOverflow()
	{
		long bytes = 3_999_987_567l;
		String readable = StringHelpers.humanReadableBytes(bytes);
		org.junit.Assert.assertEquals("4.00 GB (3999987567 bytes)", readable);
	}
}
