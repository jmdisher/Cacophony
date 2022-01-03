package com.jeffdisher.cacophony.utils;

public class Assert
{
	public static RuntimeException unexpected(Exception e)
	{
		throw new RuntimeException("Unexpected exception", e);
	}

	public static void assertTrue(boolean flag)
	{
		if (!flag)
		{
			throw new RuntimeException("Expected true");
		}
	}
}
