package com.jeffdisher.cacophony.data.local.v4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The primitive serialization routines used when writing to an opcode stream.
 */
public class OpcodeSerializer
{
	private final ByteBuffer _data;

	/**
	 * Creates the serializer, writing to the given ByteBuffer (whose state it will modify).
	 * 
	 * @param data The buffer to populate.
	 */
	public OpcodeSerializer(ByteBuffer data)
	{
		_data = data;
	}

	/**
	 * @param value The boolean to write, as a byte.
	 */
	public void writeBoolean(boolean value)
	{
		// We store a boolean as a byte of either 0 or 1 (physically, 0 and non-0).
		_data.put(value ? (byte)1 : (byte)0);
	}

	/**
	 * @param value The signed integer to write, as 4 bytes.
	 */
	public void writeInt(int value)
	{
		_data.putInt(value);
	}

	/**
	 * @param value The signed long integer to write, as 8 bytes.
	 */
	public void writeLong(long value)
	{
		_data.putLong(value);
	}

	/**
	 * @param value The IpfsKey to write (could be null).
	 */
	public void writeKey(IpfsKey value)
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = (null != value)
				? value.toPublicKey()
				: null
		;
		_writeString(raw);
	}

	/**
	 * @param value The IpfsFile to write (could be null).
	 */
	public void writeCid(IpfsFile value)
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = (null != value)
				? value.toSafeString()
				: null
		;
		_writeString(raw);
	}

	/**
	 * @param value The String to write (could be null).
	 */
	public void writeString(String value)
	{
		_writeString(value);
	}


	private void _writeString(String value)
	{
		if (null != value)
		{
			byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
			_data.putInt(utf8.length);
			_data.put(utf8);
		}
		else
		{
			_data.putInt(-1);
		}
	}
}
