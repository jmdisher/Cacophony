package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockLocalActions;
import com.jeffdisher.cacophony.testutils.MockPinMechanism;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class TestStartFollowingCommand
{
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final String REMOTE_KEY_NAME = "remoteKey";
	private static final IpfsKey REMOTE_PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14f");

	@Test
	public void testUsage() throws Throwable
	{
		IpfsFile originalRecordsCid = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK3");
		MockPinMechanism remotePin = new MockPinMechanism(null);
		MockConnection remoteConnection = new MockConnection(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, remotePin, null);
		remoteConnection.storeData(originalRecordsCid, GlobalData.serializeRecords(new StreamRecords()));
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		Executor executor = new Executor(System.out);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(remoteConnection);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism, remoteConnection);
		MockLocalActions localActions = new MockLocalActions(IPFS_HOST, KEY_NAME, null, sharedConnection, pinCache, pinMechanism, followIndex);
		
		IpfsFile originalRoot = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		StreamIndex originalRootData = new StreamIndex();
		originalRootData.setVersion(1);
		IpfsFile originalDescription = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK1");
		originalRootData.setDescription(originalDescription.toSafeString());
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK2");
		originalRootData.setRecommendations(originalRecommendations.toSafeString());
		originalRootData.setRecords(originalRecordsCid.toSafeString());
		remoteConnection.storeData(originalRoot, GlobalData.serializeIndex(originalRootData));
		
		StreamDescription originalDescriptionData = new StreamDescription();
		originalDescriptionData.setName("name");
		IpfsFile originalPicture = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeK4");
		originalDescriptionData.setDescription("Description");
		originalDescriptionData.setPicture(originalPicture.toSafeString());
		remoteConnection.storeData(originalDescription, GlobalData.serializeDescription(originalDescriptionData));
		
		remoteConnection.setRootForKey(REMOTE_PUBLIC_KEY, originalRoot);
		command.scheduleActions(executor, localActions);
		
		// Verify the states that should have changed.
		Assert.assertTrue(pinMechanism.isPinned(originalRoot));
		Assert.assertTrue(pinMechanism.isPinned(originalDescription));
		Assert.assertTrue(pinMechanism.isPinned(originalRecommendations));
		Assert.assertTrue(pinMechanism.isPinned(originalRecordsCid));
		Assert.assertTrue(pinMechanism.isPinned(originalPicture));
		
		// Make sure that the local index is correct.
		LocalIndex finalIndex = localActions.readIndex();
		Assert.assertEquals(IPFS_HOST, finalIndex.ipfsHost());
		Assert.assertEquals(KEY_NAME, finalIndex.keyName());
		// (since we started with a null published index (not normally something which can happen), and didn't publish a change, we expect it to still be null).
		Assert.assertNull(localActions.readIndex().lastPublishedIndex());
	}

	@Test
	public void testErrorSize() throws Throwable
	{
		MockPinMechanism remotePin = new MockPinMechanism(null);
		MockConnection remoteConnection = new MockConnection(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, remotePin, null);
		IpfsFile originalRoot = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		remoteConnection.storeData(originalRoot, new byte[(int) (SizeLimits.MAX_INDEX_SIZE_BYTES + 1)]);
		remoteConnection.setRootForKey(REMOTE_PUBLIC_KEY, originalRoot);
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		Executor executor = new Executor(System.out);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		MockPinMechanism pinMechanism = new MockPinMechanism(remoteConnection);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, pinMechanism, remoteConnection);
		MockLocalActions localActions = new MockLocalActions(IPFS_HOST, KEY_NAME, null, sharedConnection, pinCache, pinMechanism, followIndex);
		
		try {
			command.scheduleActions(executor, localActions);
			Assert.fail();
		} catch (SizeConstraintException e) {
			// Expected.
		}
	}
}
