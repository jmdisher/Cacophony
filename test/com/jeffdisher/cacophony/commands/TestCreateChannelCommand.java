package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.testutils.MockUserNode;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestCreateChannelCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testUsage() throws Throwable
	{
		MockUserNode user1 = new MockUserNode(KEY_NAME, PUBLIC_KEY, null);
		CreateChannelCommand command = new CreateChannelCommand(IPFS_HOST, KEY_NAME);
		user1.runCommand(null, command);
		
		// Verify the states that should have changed.
		LocalIndex storedIndex = user1.getLocalStoredIndex();
		Assert.assertEquals(IPFS_HOST, storedIndex.ipfsHost());
		Assert.assertEquals(KEY_NAME, storedIndex.keyName());
		IpfsFile root = user1.resolveKeyOnNode(PUBLIC_KEY);
		StreamIndex index = GlobalData.deserializeIndex(user1.loadDataFromNode(root));
		Assert.assertEquals(1, index.getVersion());
		IpfsFile descriptionCid = IpfsFile.fromIpfsCid(index.getDescription());
		StreamDescription description = GlobalData.deserializeDescription(user1.loadDataFromNode(descriptionCid));
		Assert.assertEquals("Unnamed", description.getName());
		IpfsFile recommendationsCid = IpfsFile.fromIpfsCid(index.getRecommendations());
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(user1.loadDataFromNode(recommendationsCid));
		Assert.assertEquals(0, recommendations.getUser().size());
		IpfsFile recordsCid = IpfsFile.fromIpfsCid(index.getRecords());
		StreamRecords records = GlobalData.deserializeRecords(user1.loadDataFromNode(recordsCid));
		Assert.assertEquals(0, records.getRecord().size());
		
		Assert.assertTrue(user1.isPinnedLocally(root));
		Assert.assertTrue(user1.isPinnedLocally(descriptionCid));
		Assert.assertTrue(user1.isPinnedLocally(recommendationsCid));
	}
}
