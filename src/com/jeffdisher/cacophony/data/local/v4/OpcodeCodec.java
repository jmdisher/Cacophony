package com.jeffdisher.cacophony.data.local.v4;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.data.local.v3.Opcode_ExplicitUserInfoV3;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The top-level class responsible for reading and writing the V3 opcodes.
 * The serialization format is as follows (all big-endian):
 * -[0-3] - ordinal of opcode type.
 * -[4-7] - the length of the opcode data.
 * -[8-(length + 8)] - "length" bytes of opcode data, specific to each opcode.
 * This data stream is also passed through GZIP before hitting the output stream.
 */
public class OpcodeCodec
{
	@SuppressWarnings("unchecked")
	private static Function<OpcodeDeserializer, IDataOpcode>[] _OPCODE_TABLE = new Function[OpcodeType.END_OF_LIST.ordinal()];
	private static final int HEADER_SIZE = Integer.BYTES + Integer.BYTES;

	// For now, we just use a 4 KiB scratch buffer so our opcodes are all very small.
	private static final int SERIALIZATION_BUFFER_SIZE_BYTES = 4096;

	// We request that each opcode register itself into the table for decoding, here.
	// We tried doing this as a "push" in static{} for each opcode but they aren't eagerly initialized (not surprising).
	static
	{
		Opcode_DefineChannel.register(_OPCODE_TABLE);
		Opcode_SetFolloweeState.register(_OPCODE_TABLE);
		Opcode_AddFolloweeElement.register(_OPCODE_TABLE);
		Opcode_SetPrefsInt.register(_OPCODE_TABLE);
		Opcode_SetPrefsLong.register(_OPCODE_TABLE);
		Opcode_ExplicitUserInfoV3.register(_OPCODE_TABLE);
		Opcode_ExplicitStreamRecord.register(_OPCODE_TABLE);
		Opcode_FavouriteStreamRecord.register(_OPCODE_TABLE);
		Opcode_ExplicitUserInfo.register(_OPCODE_TABLE);
		
		// Verify that the table is fully-built (0 is always empty as an error state).
		for (int i = 1; i < _OPCODE_TABLE.length; ++i)
		{
			Assert.assertTrue(null != _OPCODE_TABLE[i]);
		}
	}

	/**
	 * Creates the writer to serialize opcodes to the given output.
	 * 
	 * @param output The stream to use for output.
	 * @return The writer which the caller can use to serialize opcodes (they must close it when done).
	 * @throws IOException Something went wrong starting to use the output.
	 */
	public static OpcodeCodec.Writer createOutputWriter(OutputStream output) throws IOException
	{
		return new OpcodeCodec.Writer(new GZIPOutputStream(output));
	}

	/**
	 * Decodes the previously-written V3 stream from input, applying the opcodes found to the given V3 context.
	 * 
	 * @param input The stream used for input.
	 * @param context Where the V3 opcodes will be applied as they are read.
	 * @throws IOException Something went wrong reading the stream.
	 */
	public static void decodeWholeStreamV3(InputStream input, OpcodeContextV3 context) throws IOException
	{
		try (GZIPInputStream stream = new GZIPInputStream(input))
		{
			// We loop until EOF.
			while (true)
			{
				byte[] header = stream.readNBytes(HEADER_SIZE);
				ByteBuffer wrap = ByteBuffer.wrap(header);
				int ord = wrap.getInt();
				int length = wrap.getInt();
				byte[] frame = stream.readNBytes(length);
				IDataOpcode opcode = _OPCODE_TABLE[ord].apply(new OpcodeDeserializer(ByteBuffer.wrap(frame)));
				opcode.applyV3(context);
			}
		}
		catch (BufferUnderflowException e)
		{
			// This is just a normal exit.
		}
		catch (IOException e)
		{
			throw e;
		}
	}

	/**
	 * Decodes the previously-written stream from input, applying the opcodes found to the given context.
	 * 
	 * @param input The stream used for input.
	 * @param context Where the opcodes will be applied as they are read.
	 * @throws IOException Something went wrong reading the stream.
	 */
	public static void decodeWholeStream(InputStream input, OpcodeContext context) throws IOException
	{
		try (GZIPInputStream stream = new GZIPInputStream(input))
		{
			// We loop until EOF.
			while (true)
			{
				byte[] header = stream.readNBytes(HEADER_SIZE);
				ByteBuffer wrap = ByteBuffer.wrap(header);
				int ord = wrap.getInt();
				int length = wrap.getInt();
				byte[] frame = stream.readNBytes(length);
				IDataOpcode opcode = _OPCODE_TABLE[ord].apply(new OpcodeDeserializer(ByteBuffer.wrap(frame)));
				opcode.apply(context);
			}
		}
		catch (BufferUnderflowException e)
		{
			// This is just a normal exit.
		}
		catch (IOException e)
		{
			throw e;
		}
	}


	/**
	 * The writer encapsulates the logic for writing the binary frames for each opcode to a stream and closing it, when
	 * done.
	 */
	public static class Writer implements Closeable
	{
		private final OutputStream _out;
		private final byte[] _scratch;
		
		public Writer(OutputStream out)
		{
			_out = out;
			_scratch = new byte[SERIALIZATION_BUFFER_SIZE_BYTES];
		}
		
		public void writeOpcode(IDataOpcode opcode) throws IOException
		{
			ByteBuffer headerWrap = ByteBuffer.wrap(_scratch, 0, HEADER_SIZE);
			ByteBuffer encodeWrap = ByteBuffer.wrap(_scratch, HEADER_SIZE, _scratch.length - HEADER_SIZE);
			opcode.write(new OpcodeSerializer(encodeWrap));
			
			int ord = opcode.type().ordinal();
			int length = encodeWrap.position();
			headerWrap.putInt(ord);
			headerWrap.putInt(length);
			_out.write(_scratch, 0, HEADER_SIZE + length);
		}
		@Override
		public void close() throws IOException
		{
			_out.close();
		}
	}
}
