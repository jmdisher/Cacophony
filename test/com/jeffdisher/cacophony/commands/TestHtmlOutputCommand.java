package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public class TestHtmlOutputCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();

	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testWithoutChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		File parentDirectory = FOLDER.newFolder();
		HtmlOutputCommand command = new HtmlOutputCommand(new File(parentDirectory, "output"));
		try {
			user1.runCommand(null, command);
			Assert.fail();
		} catch (UsageException e) {
			// Expected.
		}
	}

	@Test
	public void testWithBasicChannel() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		CreateChannelCommand createCommand = new CreateChannelCommand(IPFS_HOST, KEY_NAME);
		user1.runCommand(null, createCommand);
		
		File parentDirectory = FOLDER.newFolder();
		HtmlOutputCommand command = new HtmlOutputCommand(new File(parentDirectory, "output"));
		user1.runCommand(null, command);
		
		// Verify the presence of on-disk structures once implemented.
		File outputDirectory = new File(parentDirectory, "output");
		Assert.assertTrue(outputDirectory.isDirectory());
		Assert.assertTrue(new File(outputDirectory, "index.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "prefs.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "utils.js").isFile());
		Assert.assertTrue(new File(outputDirectory, "user.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "play.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "recommending.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "following.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "generated_db.js").isFile());
	}

	@Test
	public void testWithLocalData() throws Throwable
	{
		// Create the single user.
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		CreateChannelCommand createCommand = new CreateChannelCommand(IPFS_HOST, KEY_NAME);
		user1.runCommand(null, createCommand);
		
		// Populate its stream with a single, well-formed video post.
		File thumbnail = FOLDER.newFile();
		File smallVideo = FOLDER.newFile();
		File largeVideo = FOLDER.newFile();
		Files.write(thumbnail.toPath(), "thumbnail file".getBytes());
		Files.write(smallVideo.toPath(), "small video".getBytes());
		Files.write(largeVideo.toPath(), "large video".getBytes());
		
		PublishCommand publishCommand = new PublishCommand("post", "", null, new ElementSubCommand[] {
				new ElementSubCommand("image/jpeg", thumbnail, 0, 0, true)
				, new ElementSubCommand("video/webm", smallVideo, 480, 640, false)
				, new ElementSubCommand("video/webm", largeVideo, 720, 1280, false)
		});
		user1.runCommand(null, publishCommand);
		
		File parentDirectory = FOLDER.newFolder();
		HtmlOutputCommand command = new HtmlOutputCommand(new File(parentDirectory, "output"));
		user1.runCommand(null, command);
		
		// Verify the presence of on-disk structures once implemented.
		File outputDirectory = new File(parentDirectory, "output");
		Assert.assertTrue(outputDirectory.isDirectory());
		Assert.assertTrue(new File(outputDirectory, "index.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "prefs.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "utils.js").isFile());
		Assert.assertTrue(new File(outputDirectory, "user.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "play.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "recommending.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "following.html").isFile());
		Assert.assertTrue(new File(outputDirectory, "generated_db.js").isFile());
	}
}
