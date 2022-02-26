package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestRepublishCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testRepublishAfterNew() throws Throwable
	{
		RepublishCommand command = new RepublishCommand();
		
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// Verify initial update.
		LocalIndex index1 = user.getLocalStoredIndex();
		Assert.assertEquals(IPFS_HOST, index1.ipfsHost());
		Assert.assertEquals(KEY_NAME, index1.keyName());
		Assert.assertNotNull(index1.lastPublishedIndex());
		
		// Now, run the refresh command.
		user.runCommand(null, command);
		
		// Verify nothing changed.
		LocalIndex index2 = user.getLocalStoredIndex();
		Assert.assertEquals(index1, index2);
	}
}
