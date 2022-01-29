package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestStartFollowingCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testUsage() throws IOException
	{
		StartFollowingCommand command = new StartFollowingCommand(PUBLIC_KEY);
		Executor executor = new Executor();
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism();
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY);
		MockLocalActions localActions = new MockLocalActions(IPFS_HOST, KEY_NAME, sharedConnection, pinCache, pinMechanism, followIndex);
		
		IpfsFile originalRoot = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		StreamIndex originalRootData = new StreamIndex();
		originalRootData.setVersion(1);
		IpfsFile originalDescription = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK1");
		originalRootData.setDescription(originalDescription.cid().toString());
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK2");
		originalRootData.setRecommendations(originalRecommendations.cid().toString());
		IpfsFile originalRecords = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK3");
		originalRootData.setRecords(originalRecords.cid().toString());
		sharedConnection.storeData(originalRoot, GlobalData.serializeIndex(originalRootData));
		
		StreamDescription originalDescriptionData = new StreamDescription();
		originalDescriptionData.setName("name");
		IpfsFile originalPicture = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK4");
		originalDescriptionData.setDescription("Description");
		originalDescriptionData.setPicture(originalPicture.cid().toString());
		sharedConnection.storeData(originalDescription, GlobalData.serializeDescription(originalDescriptionData));
		
		sharedConnection.setRootForKey(PUBLIC_KEY, originalRoot);
		command.scheduleActions(executor, localActions);
		
		// Verify the states that should have changed.
		Assert.assertTrue(pinMechanism.isPinned(originalRoot));
		Assert.assertTrue(pinMechanism.isPinned(originalDescription));
		Assert.assertTrue(pinMechanism.isPinned(originalRecommendations));
		Assert.assertTrue(pinMechanism.isPinned(originalRecords));
		Assert.assertTrue(pinMechanism.isPinned(originalPicture));
	}
}
