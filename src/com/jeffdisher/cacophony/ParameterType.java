package com.jeffdisher.cacophony;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import com.jeffdisher.cacophony.data.global.record.ElementSpecialType;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public enum ParameterType
{
	INT("int"
			, (String arg) -> {
				try
				{
					int result = Integer.parseInt(arg);
					if (result < 0L)
					{
						throw new UsageException("Value cannot be negative: \"" + result + "\"");
					}
					return result;
				}
				catch (NumberFormatException e)
				{
					throw new UsageException("Not a number: \"" + arg + "\"");
				}
			}
	),
	LONG_BYTES("long"
			, (String arg) -> {
				/*
				 * Parses the given number as a long, but also allows for suffix of "k"/"K" (thousands), "m"/"M" (millions),
				 * "g"/"G" (billions).
				 */
				long result;
				// First, see if there is trailing magnitude suffix.
				long magnitude = 1L;
				char lastChar = arg.charAt(arg.length() - 1);
				switch (lastChar)
				{
					case 'k':
					case 'K':
						magnitude = 1_000L;
						break;
					case 'm':
					case 'M':
						magnitude = 1_000_000L;
						break;
					case 'g':
					case 'G':
						magnitude = 1_000_000_000L;
						break;
				}
				String toParse = (1L == magnitude)
						? arg
						: arg.substring(0, arg.length() - 1)
				;
				try
				{
					long value = Long.parseLong(toParse);
					result = value * magnitude;
					if (result < 0L)
					{
						throw new UsageException("Value cannot be negative: \"" + result + "\"");
					}
				}
				catch (NumberFormatException e)
				{
					throw new UsageException("Not a number: \"" + arg + "\"");
				}
				return result;
			}
	),
	LONG_MILLIS("long"
			, (String arg) -> {
				try
				{
					long result = Long.parseLong(arg);
					if (result < 0L)
					{
						throw new UsageException("Value cannot be negative: \"" + result + "\"");
					}
					return result;
				}
				catch (NumberFormatException e)
				{
					throw new UsageException("Not a number: \"" + arg + "\"");
				}
			}
	),
	MIME("mime_type"
			, (String arg) -> {
				// We just expect the mime type to have a "/" in it.
				if (!arg.contains("/"))
				{
					throw new UsageException("MIME type invalid: \"" + arg + "\"");
				}
				return arg;
			}
	),
	STRING("string"
			, (String arg) -> arg
	),
	SPECIAL("\"image\""
			, (String arg) -> {
				if (!ElementSpecialType.IMAGE.value().equals(arg))
				{
					throw new UsageException("Unknown special file type: \"" + arg + "\"");
				}
				return true;
			}
	),
	FILE("file_path"
			, (String arg) -> {
				File file = new File(arg);
				if (!file.exists())
				{
					throw new UsageException("File does not exist: \"" + arg + "\"");
				}
				if (!file.isFile())
				{
					throw new UsageException("File exists but is not a regular file: \"" + arg + "\"");
				}
				return file;
			}
	),
	PUBLIC_KEY("public_key"
			, (String arg) -> {
				IpfsKey key = IpfsKey.fromPublicKey(arg);
				if (null == key)
				{
					throw new UsageException("Not a valid IPFS public key: \"" + arg + "\"");
				}
				return key;
			}
	),
	CID("cid"
			, (String arg) -> {
				IpfsFile cid = IpfsFile.fromIpfsCid(arg);
				if (null == cid)
				{
					throw new UsageException("Not a valid IPFS CID: \"" + arg + "\"");
				}
				return cid;
			}
	),
	URL("url"
			, (String arg) -> {
				URL url;
				try
				{
					url = new URL(arg);
				}
				catch (MalformedURLException e)
				{
					throw new UsageException("Malformed URL: \"" + arg + "\"");
				}
				// We still return a string for this case since URL objects are annoying to use and we are usually just passing this through.
				return url.toString();
			}
	),
	;

	public final String shortDescription;
	private final Parser<?> _parser;

	private ParameterType(String shortDescription, Parser<?> parser)
	{
		this.shortDescription = shortDescription;
		_parser = parser;
	}

	public <T> T parse(Class<T> clazz, String arg) throws UsageException
	{
		return clazz.cast(_parser.parse(arg));
	}


	private interface Parser<R>
	{
		R parse(String arg) throws UsageException;
	}
}
