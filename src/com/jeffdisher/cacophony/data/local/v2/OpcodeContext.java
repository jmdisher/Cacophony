package com.jeffdisher.cacophony.data.local.v2;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jeffdisher.cacophony.utils.Assert;


/**
 * The class type passed into the IDataOpcode method so that the decoded opcode can be applied to the data model.
 * This class is meant to contain the interfaces which provide more specific data mutation primitive functionality.
 * It is also the common place where we manage the streams:  We use an object stream built upon a GZIP stream.
 */
public record OpcodeContext(IMiscUses misc, IFolloweeDecoding followee)
{
	public static ObjectOutputStream createOutputStream(OutputStream output) throws IOException
	{
		return new ObjectOutputStream(new GZIPOutputStream(output));
	}

	public void decodeWholeStream(InputStream input) throws IOException
	{
		try (ObjectInputStream stream = new ObjectInputStream(new GZIPInputStream(input)))
		{
			// We loop until EOF.
			while (true)
			{
				IDataOpcode opcode = (IDataOpcode) stream.readObject();
				opcode.apply(this);
			}
		}
		catch (ClassNotFoundException e)
		{
			// This would imply a bogus object ended up in the data stream - we should only have the opcodes and our basic types.
			throw Assert.unexpected(e);
		}
		catch (EOFException e)
		{
			// This is just a normal exit.
		}
		catch (IOException e)
		{
			throw e;
		}
	}
}
