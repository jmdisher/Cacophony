package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;


public class TestListChannelEntriesCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testWithAndWithoutKey() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY1, new MockSingleNode(new MockSwarm()));
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Check that we can list entries with null key.
		user1.runCommand(null, new ListChannelEntriesCommand(null));
		
		// Test what happens when we ask about someone who doesn't exist.
		try
		{
			user1.runCommand(null, new ListChannelEntriesCommand(PUBLIC_KEY2));
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
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY1, new MockSingleNode(swarm));
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm));
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Check that we can ask about someone we aren't following who does exist.
		user2.runCommand(null, new ListChannelEntriesCommand(PUBLIC_KEY1));
		user1.shutdown();
		user2.shutdown();
	}

	@Test
	public void testNotCachedEntry() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY1, new MockSingleNode(swarm));
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, new MockSingleNode(swarm));
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(KEY_NAME));
		
		// Make an entry with no leaves and one with a big leaf.
		user1.runCommand(null, new PublishCommand("name", "description", null, new ElementSubCommand[0]));
		File video = FOLDER.newFile();
		Files.write(video.toPath(), new byte[] { 1,2,3,4,5 });
		user1.runCommand(null, new PublishCommand("big name", "leaf description", null, new ElementSubCommand[] { new ElementSubCommand("video/webm", video, 720, 1280, false) } ));
		
		// Reduce the cache size and start following the user.
		user2.runCommand(null, new SetGlobalPrefsCommand(1280, 2L, 0L, 0L));
		user2.runCommand(null, new StartFollowingCommand(PUBLIC_KEY1));
		
		// Check that the output from the listing makes sense.
		user2.runCommand(null, new ListCachedElementsForFolloweeCommand(PUBLIC_KEY1));
		user1.shutdown();
		user2.shutdown();
	}
}
