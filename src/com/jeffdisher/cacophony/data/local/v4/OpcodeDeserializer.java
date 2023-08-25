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

	public OpcodeDeserializer(ByteBuffer data)
	{
		_data = data;
	}

	public int readInt()
	{
		return _data.getInt();
	}

	public long readLong()
	{
		return _data.getLong();
	}

	public IpfsKey readKey()
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = _readString();
		return (null != raw)
				? IpfsKey.fromPublicKey(raw)
				: null
		;
	}

	public IpfsFile readCid()
	{
		// We will serialize these as strings, just since the helpers to use the binary encoding are unclear.
		String raw = _readString();
		return (null != raw)
				? IpfsFile.fromIpfsCid(raw)
				: null
		;
	}

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
