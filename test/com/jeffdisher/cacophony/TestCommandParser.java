package com.jeffdisher.cacophony;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.PublishCommand;


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
		String[] foo = {"--publishToThisChannel", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL", "--element", "--mime", "mime type", "--file", "/path"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
	}

	@Test
	public void testFullPublish()
	{
		String[] foo = {"--publishToThisChannel", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL"
				, "--element", "--mime", "mime type", "--file", "/path", "--special", "image"
				, "--element", "--mime", "mime type", "--file", "/path", "--width", "640", "--height", "480"
		};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
		Assert.assertTrue(command instanceof PublishCommand);
		PublishCommand publishCommand = (PublishCommand) command;
		ElementSubCommand thumbnailCommand = publishCommand._elements()[0];
		Assert.assertTrue(thumbnailCommand.isSpecialImage());
	}

	@Test
	public void testBrokenArgs()
	{
		String[] foo = {"--publishToThisChannel", "--name"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testCreateNewChannel()
	{
		String[] foo = {"--createNewChannel", "--ipfs", "/ip4/127.0.0.1/tcp/5001",  "--keyName",  "cacophony"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionLocal()
	{
		String[] foo = {"--readDescription"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionArg()
	{
		String[] foo = {"--readDescription", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testUpdateDescription()
	{
		String[] foo = {"--updateDescription", "--name", "name", "--description", "description", "--pictureFile", "/tmp/fake"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListChannel()
	{
		String[] foo = {"--listChannel", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRemoveFromThisChannel()
	{
		String[] foo = {"--removeFromThisChannel", "--elementCid", "QmRntQodp7qHb3PwS8GkcaKXfeELJymB4H5D8rBfoEwq8J"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testSetGlobalPrefs()
	{
		String[] foo = {"--setGlobalPrefs", "--edgeMaxPixels", "7", "--followCacheTargetBytes", "5000000000"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testPublishSpecialOnly() throws IOException
	{
		String[] foo = {"--publishToThisChannel", "--name", "name", "--mime", "image/jpeg", "--file", "/tmp/fake", "--special", "image"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testStartFollowing() throws IOException
	{
		String[] foo = {"--startFollowing", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testStopFollowing() throws IOException
	{
		String[] foo = {"--stopFollowing", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListFollowees() throws IOException
	{
		String[] foo = {"--listFollowees"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListFollowee() throws IOException
	{
		String[] foo = {"--listFollowee", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListRecommendationsLocal() throws IOException
	{
		String[] foo = {"--listRecommendations"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListRecommendationsRemove() throws IOException
	{
		String[] foo = {"--listRecommendations", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testAddRecommendation() throws IOException
	{
		String[] foo = {"--addRecommendation", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRemoveRecommendation() throws IOException
	{
		String[] foo = {"--removeRecommendation", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRepublish() throws IOException
	{
		String[] foo = {"--republish"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testHtmlOutput() throws IOException
	{
		String[] foo = {"--htmlOutput", "--directory", "/tmp"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testSingleVideo()
	{
		String[] foo = {"--publishSingleVideo", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL"
				, "--thumbnailJpeg", "/thumbnail"
				, "--videoFile", "/video", "--videoMime", "video/webm", "--videoHeight", "640", "--videoWidth", "480"
		};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
		Assert.assertTrue(command instanceof PublishCommand);
		PublishCommand publishCommand = (PublishCommand) command;
		Assert.assertEquals(2, publishCommand._elements().length);
		ElementSubCommand thumbnailCommand = publishCommand._elements()[0];
		Assert.assertTrue(thumbnailCommand.isSpecialImage());
		ElementSubCommand videoCommand = publishCommand._elements()[1];
		Assert.assertEquals("video/webm", videoCommand.mime());
	}

	@Test
	public void testGetPublicKey() throws IOException
	{
		String[] foo = {"--getPublicKey"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testNonsenseArgs()
	{
		String[] foo = {"no-command", "no-arg"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertEquals(0, outStream.size());
	}
}
