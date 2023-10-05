package com.jeffdisher.cacophony.data.local.v4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The primitive deserialization routines used when reading from an opcode stream.
 */
public class OpcodeDeserializer
{
	private final ByteBuffer _data;

	/**
	 * Creates the deserializer, reading from the given ByteBuffer (whose state it will modify).
	 * 
	 * @param data The buffer to read.
	 */
	public OpcodeDeserializer(ByteBuffer data)
	{
		_data = data;
	}

	/**
	 * @return The next byte in stream, as a boolean.
	 */
	public boolean readBoolean()
	{
		// We store a boolean as a byte of either 0 or 1 (physically, 0 and non-0).
		return (0 != _data.get());
	}

	/**
	 * @return The next 4 bytes in stream, as a signed integer.
	 */
	public int readInt()
	{
		return _data.getInt();
	}

	/**
	 * @return The next 8 bytes in stream, as a signed long integer.
	 */
	public long readLong()
	{
		return _data.getLong();
	}

	/**
	 * @return The next IpfsKey in the stream (could be null).
	 */
	public IpfsKey readKey()
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = _readString();
		return (null != raw)
				? IpfsKey.fromPublicKey(raw)
				: null
		;
	}

	/**
	 * @return The next IpfsFile in the stream (could be null).
	 */
	public IpfsFile readCid()
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = _readString();
		return (null != raw)
				? IpfsFile.fromIpfsCid(raw)
				: null
		;
	}

	/**
	 * @return The next String in the stream (could be null).
	 */
	public String readString()
	{
		return _readString();
	}


	private String _readString()
	{
		int length = _data.getInt();
		String value;
		if (length >= 0)
		{
			byte[] utf8 = new byte[length];
			_data.get(utf8);
			value = new String(utf8, StandardCharsets.UTF_8);
		}
		else
		{
			Assert.assertTrue(-1 == length);
			value = null;
		}
		return value;
	}
}
