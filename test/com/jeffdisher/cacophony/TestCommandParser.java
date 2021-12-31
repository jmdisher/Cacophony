package com.jeffdisher.cacophony;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import com.jeffdisher.cacophony.commands.ICommand;


class TestCommandParser
{
	@Test
	void testUsage()
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		CommandParser.printUsage(capture);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	void testPublish()
	{
		String[] foo = {"/ip4/127.0.0.1/tcp/5001", "--publishToThisChannel", "--name", "entry name", "--discussionUrl", "URL", "--element", "--mime", "mime type", "--file", "/path"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
	}
}
