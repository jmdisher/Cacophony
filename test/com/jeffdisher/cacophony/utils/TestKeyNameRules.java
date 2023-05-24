package com.jeffdisher.cacophony.utils;

import org.junit.Test;

import com.jeffdisher.cacophony.types.UsageException;


public class TestKeyNameRules
{
	@Test(expected = UsageException.class)
	public void empty() throws Throwable
	{
		KeyNameRules.validateKey("");
	}

	@Test(expected = UsageException.class)
	public void tooLong() throws Throwable
	{
		KeyNameRules.validateKey("01234567890123456789012345678901");
	}

	@Test(expected = UsageException.class)
	public void invalidChars() throws Throwable
	{
		KeyNameRules.validateKey("key name");
	}

	@Test(expected = UsageException.class)
	public void encoding() throws Throwable
	{
		KeyNameRules.validateKey("Æ");
	}

	@Test
	public void sane() throws Throwable
	{
		KeyNameRules.validateKey("long_key-name.withAZthings12340");
	}
}
