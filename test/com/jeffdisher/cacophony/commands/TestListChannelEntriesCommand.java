package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.KeyException;


public class TestListChannelEntriesCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";

	@Test
	public void testWithAndWithoutKey() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(new MockSwarm()), FOLDER.newFolder());
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Check that we can list entries with null key.
		user1.runCommand(null, new ListChannelEntriesCommand(null));
		
		// Test what happens when we ask about someone who doesn't exist.
		try
		{
			user1.runCommand(null, new ListChannelEntriesCommand(MockKeys.K2));
			Assert.fail();
		}
		catch (KeyException e)
		{
			// Expected.
		}
		user1.shutdown();
	}

	@Test
	public void testCheckingOtherUser() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Check that we can ask about someone we aren't following who does exist.
		user2.runCommand(null, new ListChannelEntriesCommand(MockKeys.K1));
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testNotCachedEntry() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Make an entry with no leaves and one with a big leaf.
		user1.runCommand(null, new PublishCommand("name", "description", null, null, null, null, new ElementSubCommand[0]));
		File video = FOLDER.newFile();
		Files.write(video.toPath(), new byte[] { 1,2,3,4,5 });
		user1.runCommand(null, new PublishCommand("big name", "leaf description", null, null, null, null, new ElementSubCommand[] { new ElementSubCommand("video/webm", video, 720, 1280) } ));
		
		// Reduce the cache size and start following the user.
		user2.runCommand(null, new SetGlobalPrefsCommand(1280, 0L, 0L, 0L, 2L, 0L, 0L, 0L, 0L));
		user2.runCommand(null, new StartFollowingCommand(MockKeys.K1));
		
		// Check that the output from the listing makes sense.
		user2.runCommand(null, new ListCachedElementsForFolloweeCommand(MockKeys.K1));
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testWithReplyTo() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, MockKeys.K1, new MockSingleNode(swarm), FOLDER.newFolder());
		MockUserNode user2 = new MockUserNode(KEY_NAME, MockKeys.K2, new MockSingleNode(swarm), FOLDER.newFolder());
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Make a basic entry.
		OnePost post = user1.runCommand(null, new PublishCommand("name", "description", null, null, null, null, new ElementSubCommand[0]));
		
		// Make a reply to that entry.
		IpfsFile replyTo = post.recordCid;
		Assert.assertNotNull(replyTo);
		OnePost theReply = user1.runCommand(null, new PublishCommand("follow-up name", "follow-up description", null, replyTo, null, null, new ElementSubCommand[0]));
		Assert.assertEquals(replyTo, theReply.streamRecord.getReplyTo());
		
		// Start following them and list the entries.
		user2.runCommand(null, new StartFollowingCommand(MockKeys.K1));
		user2.runCommand(null, new RefreshFolloweeCommand(MockKeys.K1));
		ByteArrayOutputStream captureStream = new ByteArrayOutputStream();
		user2.runCommand(captureStream, new ListCachedElementsForFolloweeCommand(MockKeys.K1));
		user1.shutdown();
		user2.shutdown();
		
		String fullOutput = new String(captureStream.toByteArray());
		Assert.assertTrue(fullOutput.contains("Element CID: " + replyTo.toSafeString() + " (image: (none), leaf: (none))"));
		Assert.assertTrue(fullOutput.contains("Element CID: " + theReply.recordCid.toSafeString() + " (image: (none), leaf: (none)) is a reply to: " + replyTo.toSafeString()));
	}
}
