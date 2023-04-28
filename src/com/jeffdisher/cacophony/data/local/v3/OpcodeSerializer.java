package com.jeffdisher.cacophony.data.local.v3;

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

	public OpcodeSerializer(ByteBuffer data)
	{
		_data = data;
	}

	public void writeInt(int value)
	{
		_data.putInt(value);
	}

	public void writeLong(long value)
	{
		_data.putLong(value);
	}

	public void writeKey(IpfsKey value)
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = (null != value)
				? value.toPublicKey()
				: null
		;
		_writeString(raw);
	}

	public void writeCid(IpfsFile value)
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = (null != value)
				? value.toSafeString()
				: null
		;
		_writeString(raw);
	}

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
