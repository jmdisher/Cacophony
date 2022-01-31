package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestCreateChannelCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testUsage() throws IOException
	{
		CreateChannelCommand command = new CreateChannelCommand(IPFS_HOST, KEY_NAME);
		Executor executor = new Executor();
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(null);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism, null);
		MockLocalActions localActions = new MockLocalActions(null, null, sharedConnection, pinCache, pinMechanism, followIndex);
		
		command.scheduleActions(executor, localActions);
		
		// Verify the states that should have changed.
		LocalIndex storedIndex = localActions.getStoredIndex();
		Assert.assertEquals(IPFS_HOST, storedIndex.ipfsHost());
		Assert.assertEquals(KEY_NAME, storedIndex.keyName());
		IpfsFile root = sharedConnection.resolve(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(sharedConnection.loadData(root));
		Assert.assertEquals(1, index.getVersion());
		IpfsFile descriptionCid = IpfsFile.fromIpfsCid(index.getDescription());
		StreamDescription description = GlobalData.deserializeDescription(sharedConnection.loadData(descriptionCid));
		Assert.assertEquals("Unnamed", description.getName());
		IpfsFile recommendationsCid = IpfsFile.fromIpfsCid(index.getRecommendations());
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(sharedConnection.loadData(recommendationsCid));
		Assert.assertEquals(0, recommendations.getUser().size());
		IpfsFile recordsCid = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(sharedConnection.loadData(recordsCid));
		Assert.assertEquals(0, records.getRecord().size());
		
		Assert.assertTrue(pinMechanism.isPinned(root));
		Assert.assertTrue(pinMechanism.isPinned(descriptionCid));
		Assert.assertTrue(pinMechanism.isPinned(recommendationsCid));
		Assert.assertTrue(pinMechanism.isPinned(recommendationsCid));
	}
}
