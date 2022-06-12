package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;


public class TestListChannelEntriesCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testWithAndWithoutKey() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY1, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user1.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
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
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY1, null);
		MockUserNode user2 = new MockUserNode(KEY_NAME, PUBLIC_KEY2, user1);
		
		// Create the channels.
		user1.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		user2.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// Check that we can ask about someone we aren't following who does exist.
		user2.runCommand(null, new ListChannelEntriesCommand(PUBLIC_KEY1));
		user1.shutdown();
		user2.shutdown();
	}
}
