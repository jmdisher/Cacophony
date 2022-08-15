package com.jeffdisher.cacophony;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.ElementSubCommand;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.commands.PublishCommand;
import com.jeffdisher.cacophony.commands.SetGlobalPrefsCommand;
import com.jeffdisher.cacophony.types.UsageException;


public class TestCommandParser
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	@Test
	public void testUsage()
	{
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		CommandParser.printUsage(capture);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testPublish() throws Throwable
	{
		File tempFile = FOLDER.newFile();
		String[] foo = {"--publishToThisChannel", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL", "--element", "--mime", "mime type", "--file", tempFile.getAbsolutePath()};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertEquals(0, outStream.size());
	}

	@Test
	public void testFullPublish() throws Throwable
	{
		File tempFile = FOLDER.newFile();
		String[] foo = {"--publishToThisChannel", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL"
				, "--element", "--mime", "mime type", "--file", tempFile.getAbsolutePath(), "--special", "image"
				, "--element", "--mime", "mime type", "--file", tempFile.getAbsolutePath(), "--width", "640", "--height", "480"
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
	public void testBrokenArgs() throws Throwable
	{
		String[] foo = {"--publishToThisChannel", "--name"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testCreateNewChannel() throws Throwable
	{
		String[] foo = {"--createNewChannel", "--ipfs", "/ip4/127.0.0.1/tcp/5001"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionLocal() throws Throwable
	{
		String[] foo = {"--readDescription"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testReadDescriptionArg() throws Throwable
	{
		String[] foo = {"--readDescription", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testUpdateDescription() throws Throwable
	{
		File tempFile = FOLDER.newFile();
		String[] foo = {"--updateDescription", "--name", "name", "--description", "description", "--pictureFile", tempFile.getAbsolutePath()};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListChannel() throws Throwable
	{
		String[] foo = {"--listChannel", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRemoveFromThisChannel() throws Throwable
	{
		String[] foo = {"--removeFromThisChannel", "--elementCid", "QmRntQodp7qHb3PwS8GkcaKXfeELJymB4H5D8rBfoEwq8J"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testSetGlobalPrefs() throws Throwable
	{
		String[] foo = {"--setGlobalPrefs", "--edgeMaxPixels", "7", "--followCacheTargetBytes", "5000000000"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test(expected = UsageException.class)
	public void testSetGlobalPrefsInvalid() throws Throwable
	{
		String[] foo = {"--setGlobalPrefs", "--edgeMaxPixels", "7", "--followCacheTargetBytes", "5000000000h"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		// We expect this to throw the UsageException.
		CommandParser.parseArgs(foo, capture);
	}

	@Test
	public void testSetGlobalPrefsGigs() throws Throwable
	{
		String[] foo = {"--setGlobalPrefs", "--edgeMaxPixels", "7", "--followCacheTargetBytes", "5g"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
		SetGlobalPrefsCommand safe = (SetGlobalPrefsCommand) command;
		Assert.assertEquals(5_000_000_000L, safe._followCacheTargetBytes());
	}

	@Test
	public void testPublishSpecialOnly() throws Throwable
	{
		String[] foo = {"--publishToThisChannel", "--name", "name", "--mime", "image/jpeg", "--file", "/tmp/fake", "--special", "image"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertTrue(outStream.size() > 0);
	}

	@Test
	public void testStartFollowing() throws Throwable
	{
		String[] foo = {"--startFollowing", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testStopFollowing() throws Throwable
	{
		String[] foo = {"--stopFollowing", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListFollowees() throws Throwable
	{
		String[] foo = {"--listFollowees"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListFollowee() throws Throwable
	{
		String[] foo = {"--listFollowee", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListRecommendationsLocal() throws Throwable
	{
		String[] foo = {"--listRecommendations"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testListRecommendationsRemove() throws Throwable
	{
		String[] foo = {"--listRecommendations", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testAddRecommendation() throws Throwable
	{
		String[] foo = {"--addRecommendation", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRemoveRecommendation() throws Throwable
	{
		String[] foo = {"--removeRecommendation", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRepublish() throws Throwable
	{
		String[] foo = {"--republish"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testHtmlOutput() throws Throwable
	{
		String[] foo = {"--htmlOutput", "--directory", "/tmp"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testSingleVideo() throws Throwable
	{
		File tempFile = FOLDER.newFile();
		String[] foo = {"--publishSingleVideo", "--name", "entry name", "--description", "entry description", "--discussionUrl", "URL"
				, "--thumbnailJpeg", tempFile.getAbsolutePath()
				, "--videoFile", tempFile.getAbsolutePath(), "--videoMime", "video/webm", "--videoHeight", "640", "--videoWidth", "480"
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
	public void testGetPublicKey() throws Throwable
	{
		String[] foo = {"--getPublicKey"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testNonsenseArgs() throws Throwable
	{
		String[] foo = {"no-command", "no-arg"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNull(command);
		Assert.assertEquals(0, outStream.size());
	}

	@Test
	public void testRefreshOne() throws Throwable
	{
		String[] foo = {"--refreshFollowee", "--publicKey", "z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRefreshNext() throws Throwable
	{
		String[] foo = {"--refreshNextFollowee"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testGetPrefs() throws Throwable
	{
		String[] foo = {"--getGlobalPrefs"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command = CommandParser.parseArgs(foo, capture);
		Assert.assertNotNull(command);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test
	public void testRun() throws Throwable
	{
		String[] args1 = {"--run"};
		String[] args2 = {"--run", "--overrideCommand", "command", "--commandSelection", "STRICT"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		ICommand command1 = CommandParser.parseArgs(args1, capture);
		Assert.assertNotNull(command1);
		ICommand command2 = CommandParser.parseArgs(args2, capture);
		Assert.assertNotNull(command2);
		Assert.assertTrue(0 == outStream.size());
	}

	@Test(expected = UsageException.class)
	public void testRunFailure() throws Throwable
	{
		String[] args = {"--run", "--overrideCommand", "command", "--commandSelection", "BOGUS"};
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		PrintStream capture = new PrintStream(outStream);
		// We expect this to throw the UsageException.
		CommandParser.parseArgs(args, capture);
	}
}
