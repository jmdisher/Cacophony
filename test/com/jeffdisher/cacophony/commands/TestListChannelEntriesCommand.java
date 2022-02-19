package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestListChannelEntriesCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testWithAndWithoutKey() throws IOException
	{
		Executor executor = new Executor(System.out);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(null);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism, null);
		MockLocalActions localActions = new MockLocalActions(null, null, sharedConnection, pinCache, pinMechanism, followIndex);
		
		// We need to create the channel first so we will just use the command to do that.
		new CreateChannelCommand(IPFS_HOST, KEY_NAME).scheduleActions(executor, localActions);
		
		// Check that we can list entries with null key.
		new ListChannelEntriesCommand(null).scheduleActions(executor, localActions);
		
		// Check that we can list entries with an explicit key.
		new ListChannelEntriesCommand(PUBLIC_KEY).scheduleActions(executor, localActions);
	}
}
