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
	private static final String REMOTE_KEY_NAME = "remoteKey";
	private static final IpfsKey REMOTE_PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14f");

	@Test
	public void testUsage() throws IOException
	{
		MockPinMechanism remotePin = new MockPinMechanism(null);
		MockConnection remoteConnection = new MockConnection(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, remotePin);
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		Executor executor = new Executor();
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(remoteConnection);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism);
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
		remoteConnection.storeData(originalRoot, GlobalData.serializeIndex(originalRootData));
		
		StreamDescription originalDescriptionData = new StreamDescription();
		originalDescriptionData.setName("name");
		IpfsFile originalPicture = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK4");
		originalDescriptionData.setDescription("Description");
		originalDescriptionData.setPicture(originalPicture.cid().toString());
		remoteConnection.storeData(originalDescription, GlobalData.serializeDescription(originalDescriptionData));
		
		remoteConnection.setRootForKey(REMOTE_PUBLIC_KEY, originalRoot);
		command.scheduleActions(executor, localActions);
		
		// Verify the states that should have changed.
		Assert.assertTrue(pinMechanism.isPinned(originalRoot));
		Assert.assertTrue(pinMechanism.isPinned(originalDescription));
		Assert.assertTrue(pinMechanism.isPinned(originalRecommendations));
		Assert.assertTrue(pinMechanism.isPinned(originalRecords));
		Assert.assertTrue(pinMechanism.isPinned(originalPicture));
	}
}
