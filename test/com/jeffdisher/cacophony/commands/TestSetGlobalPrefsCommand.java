package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
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
		MockUserNode user = new MockUserNode(KEY_NAME, PUBLIC_KEY, new MockSingleNode(new MockSwarm()));
		
		// We need to create the channel first so we will just use the command to do that.
		user.createEmptyConfig(KEY_NAME);
		user.runCommand(null, new CreateChannelCommand(IPFS_HOST, KEY_NAME));
		
		// Verify initial update.
		Assert.assertNotNull(user.getLastRootElement());
		
		PrefsData original = user.readPrefs();
		// Shutdown the environment so that it drops all caches - otherwise, we will end up re-reading the same instance of the prefs object.
		user.shutdown();
		SetGlobalPrefsCommand command = new SetGlobalPrefsCommand(original.videoEdgePixelMax + 1, original.followCacheTargetBytes + 1, 2000L, 3000L);
		
		// Now, run the refresh command.
		user.runCommand(null, command);
		
		// Verify the update.
		PrefsData updated = user.readPrefs();
		Assert.assertEquals(original.videoEdgePixelMax + 1, updated.videoEdgePixelMax);
		Assert.assertEquals(original.followCacheTargetBytes + 1, updated.followCacheTargetBytes);
		Assert.assertEquals(2000L, updated.republishIntervalMillis);
		Assert.assertEquals(3000L, updated.followeeRefreshMillis);
		user.shutdown();
	}
}
