package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestSetGlobalPrefsCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testSetPrefs() throws Throwable
	{
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		
		// We need to create the channel first so we will just use the command to do that.
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// Verify initial update.
		LocalIndex index1 = user.getLocalStoredIndex();
		Assert.assertEquals(IPFS_HOST, index1.ipfsHost());
		Assert.assertEquals(KEY_NAME, index1.keyName());
		Assert.assertNotNull(index1.lastPublishedIndex());
		
		GlobalPrefs original = user.readPrefs();
		SetGlobalPrefsCommand command = new SetGlobalPrefsCommand(original.videoEdgePixelMax() + 1, original.followCacheTargetBytes() + 1);
		
		// Now, run the refresh command.
		user.runCommand(null, command);
		
		// Verify the update.
		GlobalPrefs updated = user.readPrefs();
		Assert.assertEquals(original.videoEdgePixelMax() + 1, updated.videoEdgePixelMax());
		Assert.assertEquals(original.followCacheTargetBytes() + 1, updated.followCacheTargetBytes());
		user.shutdown();
	}
}
