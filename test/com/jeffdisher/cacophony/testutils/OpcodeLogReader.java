package com.jeffdisher.cacophony.testutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.data.local.v4.IDataOpcode;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;


/**
 * Given the path to an opcode log file (from the local data model), will do a read-only pass over it, writing all
 * decoded data opcodes to stdout.
 */
public class OpcodeLogReader
{
	public static void main(String[] args)
	{
		File file = (1 == args.length) ? new File(args[0]) : null;
		if ((null != file) && file.isFile())
		{
			try (FileInputStream stream = new FileInputStream(file))
			{
				OpcodeCodec.decodeStreamCustom(stream, (IDataOpcode opcode) -> {
					System.out.println("OPCODE: " + opcode);
				});
			}
			catch (FileNotFoundException e)
			{
				// We already checked this, so it shouldn't happen.
				throw Assert.unexpected(e);
			}
			catch (IOException e)
			{
				// While this could happen, we shouldn't see it on close(), especially not on local files.
				throw Assert.unexpected(e);
			}
		}
		else
		{
			System.err.println("Usage:  OpcodeLogReader path_to_log_file");
			System.err.println("The given path_to_log_file must be a regular V4 log file");
			System.exit(1);
		}
	}
}
