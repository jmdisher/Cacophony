package com.jeffdisher.cacophony;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.ICommand;


public class TestCommandParser
{
	@Test
	public void testUsage()
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		CommandParser.printUsage(capture);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testPublish()
	{
		String[] foo = {"/ip4/127.0.0.1/tcp/5001", "--publishToThisChannel", "--name", "entry name", "--discussionUrl", "URL", "--element", "--mime", "mime type", "--file", "/path"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
	}

	@Test
	public void testMissingCommand()
	{
		String[] foo = {"/ip4/127.0.0.1/tcp/5001"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testBrokenArgs()
	{
		String[] foo = {"/ip4/127.0.0.1/tcp/5001", "--publishToThisChannel", "--name"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testCreateNewChannel()
	{
		String[] foo = {"/tmp/test", "--createNewChannel", "--ipfs", "/ip4/127.0.0.1/tcp/5001",  "--keyName",  "cacophony"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionLocal()
	{
		String[] foo = {"/tmp/test", "--readDescription"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionArg()
	{
		String[] foo = {"/tmp/test", "--readDescription", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testUpdateDescription()
	{
		String[] foo = {"/tmp/test", "--updateDescription", "--name", "name", "--description", "description", "--pictureFile", "/tmp/fake"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListChannel()
	{
		String[] foo = {"/tmp/test", "--listChannel", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRemoveFromThisChannel()
	{
		String[] foo = {"/tmp/test", "--removeFromThisChannel", "--elementCid", "QmRntQodp7qHb3PwS8GkcaKXfeELJymB4H5D8rBfoEwq8J"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testSetGlobalPrefs()
	{
		String[] foo = {"/tmp/test", "--setGlobalPrefs", "--cacheWidth", "5", "--cacheHeight", "7", "--cacheGlobalBytes", "5000000000"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, 1, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}
}
