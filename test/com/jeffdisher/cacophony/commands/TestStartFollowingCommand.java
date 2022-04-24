package com.jeffdisher.cacophony.commands;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockConnection;
import com.jeffdisher.cacophony.testutils.MockConnectionFactory;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
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
		MockConnection remoteConnection = new MockConnection(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, null);
		remoteConnection.storeData(originalRecordsCid, GlobalData.serializeRecords(new StreamRecords()));
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, remoteConnection);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(sharedConnection), true);
		// For this test, we want to just fake a default config.
		executor.createNewConfig(IPFS_HOST, KEY_NAME);
		
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
		command.runInEnvironment(executor);
		
		// Verify the states that should have changed.
		Assert.assertTrue(sharedConnection.isPinned(originalRoot));
		Assert.assertTrue(sharedConnection.isPinned(originalDescription));
		Assert.assertTrue(sharedConnection.isPinned(originalRecommendations));
		Assert.assertTrue(sharedConnection.isPinned(originalRecordsCid));
		Assert.assertTrue(sharedConnection.isPinned(originalPicture));
		
		// Make sure that the local index is correct.
		LocalIndex finalIndex = executor.loadExistingConfig().readLocalIndex();
		Assert.assertEquals(IPFS_HOST, finalIndex.ipfsHost());
		Assert.assertEquals(KEY_NAME, finalIndex.keyName());
		// (since we started with a null published index (not normally something which can happen), and didn't publish a change, we expect it to still be null).
		Assert.assertNull(finalIndex.lastPublishedIndex());
	}

	@Test
	public void testErrorSize() throws Throwable
	{
		MockConnection remoteConnection = new MockConnection(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, null);
		IpfsFile originalRoot = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
		remoteConnection.storeData(originalRoot, new byte[(int) (SizeLimits.MAX_INDEX_SIZE_BYTES + 1)]);
		remoteConnection.setRootForKey(REMOTE_PUBLIC_KEY, originalRoot);
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		MockConnection sharedConnection = new MockConnection(KEY_NAME, PUBLIC_KEY, remoteConnection);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(sharedConnection), true);
		// For this test, we want to just fake a default config.
		executor.createNewConfig(IPFS_HOST, KEY_NAME);
		
		try {
			command.runInEnvironment(executor);
			Assert.fail();
		} catch (SizeConstraintException e) {
			// Expected.
		}
	}

	@Test
	public void testMissingConfig() throws Throwable
	{
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(null), true);
		
		// We expect this to fail since there is no LocalIndex.
		try {
			command.runInEnvironment(executor);
			Assert.fail();
		} catch (UsageException e) {
			// Expected.
		}
	}
}
