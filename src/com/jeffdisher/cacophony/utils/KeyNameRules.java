package com.jeffdisher.cacophony.utils;

import java.nio.charset.StandardCharsets;

import com.jeffdisher.cacophony.types.UsageException;


/**
 * Testing with "ipfs key gen", it seems like IPFS has very few restrictions on what can be in a name.  However, the
 * IPFS library, sitting on their JSON-RPC interface, does seem to have some restrictions.
 * We don't bother with exhaustively probing all of these limits and will instead just apply some pretty basic
 * restrictions on the names we will use:
 * -MUST be ASCII
 * -at least 1 character
 * -at most 31 characters [1-31]
 * -allowed characters will be alphanumeric and "_", "-", "." [a-zA-Z0-9_-.]
 */
public class KeyNameRules
{
	public static void validateKey(String key) throws UsageException
	{
		_validateKey(key);
	}

	public static boolean isValidKey(String key)
	{
		boolean isValid;
		try
		{
			_validateKey(key);
			isValid = true;
		}
		catch (UsageException e)
		{
			isValid = false;
		}
		return isValid;
	}


	private static void _validateKey(String key) throws UsageException
	{
		if (null == key)
		{
			throw new UsageException("Key name cannot be null");
		}
		if (!StandardCharsets.US_ASCII.newEncoder().canEncode(key))
		{
			throw new UsageException("Key name must be ASCII");
		}
		byte[] ascii = key.getBytes(StandardCharsets.US_ASCII);
		if (0 == ascii.length)
		{
			throw new UsageException("Key name must be at least 1 character long");
		}
		if (ascii.length > 31)
		{
			throw new UsageException("Key name must be at most 31 characters long");
		}
		if (!key.matches("[a-zA-Z0-9_\\-\\.]*"))
		{
			throw new UsageException("Key name only include a-zA-Z0-9_-.");
		}
	}
}
