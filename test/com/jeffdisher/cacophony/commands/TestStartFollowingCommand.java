package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockConnectionFactory;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
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
		MockSingleNode remoteConnection = new MockSingleNode();
		MockSingleNode sharedConnection = new MockSingleNode();
		_configureCluster(
				new MockSingleNode[] { remoteConnection, sharedConnection}
				, new String[] { REMOTE_KEY_NAME, KEY_NAME}
				, new IpfsKey[] { REMOTE_PUBLIC_KEY, PUBLIC_KEY}
		);
		IpfsFile originalRecordsCid = remoteConnection.storeData(new ByteArrayInputStream(GlobalData.serializeRecords(new StreamRecords())));
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(sharedConnection), true);
		// For this test, we want to just fake a default config.
		StandardAccess.createForWrite(executor, IPFS_HOST, KEY_NAME).close();
		
		StreamDescription originalDescriptionData = new StreamDescription();
		originalDescriptionData.setName("name");
		IpfsFile originalPicture = remoteConnection.storeData(new ByteArrayInputStream(new byte[] { 0, 1, 2, 3, 4, 5 }));
		originalDescriptionData.setDescription("Description");
		originalDescriptionData.setPicture(originalPicture.toSafeString());
		IpfsFile originalDescription = remoteConnection.storeData(new ByteArrayInputStream(GlobalData.serializeDescription(originalDescriptionData)));
		
		StreamRecommendations recommendations = new StreamRecommendations();
		IpfsFile originalRecommendations = remoteConnection.storeData(new ByteArrayInputStream(GlobalData.serializeRecommendations(recommendations)));
		StreamIndex originalRootData = new StreamIndex();
		originalRootData.setVersion(1);
		originalRootData.setDescription(originalDescription.toSafeString());
		originalRootData.setRecommendations(originalRecommendations.toSafeString());
		originalRootData.setRecords(originalRecordsCid.toSafeString());
		IpfsFile originalRoot = remoteConnection.storeData(new ByteArrayInputStream(GlobalData.serializeIndex(originalRootData)));
		
		remoteConnection.publish(REMOTE_KEY_NAME, originalRoot);
		command.runInEnvironment(executor);
		
		// Verify the states that should have changed.
		Assert.assertTrue(sharedConnection.isPinned(originalRoot));
		Assert.assertTrue(sharedConnection.isPinned(originalDescription));
		Assert.assertTrue(sharedConnection.isPinned(originalRecommendations));
		Assert.assertTrue(sharedConnection.isPinned(originalRecordsCid));
		Assert.assertTrue(sharedConnection.isPinned(originalPicture));
		
		// Make sure that the local index is correct.
		IReadingAccess reading = StandardAccess.readAccess(executor);
		IpfsFile lastPublishedIndex = reading.getLastRootElement();
		reading.close();
		// (since we started with a null published index (not normally something which can happen), and didn't publish a change, we expect it to still be null).
		Assert.assertNull(lastPublishedIndex);
		executor.shutdown();
	}

	@Test
	public void testErrorSize() throws Throwable
	{
		MockSingleNode remoteConnection = new MockSingleNode();
		MockSingleNode sharedConnection = new MockSingleNode();
		_configureCluster(
				new MockSingleNode[] { remoteConnection, sharedConnection}
				, new String[] { REMOTE_KEY_NAME, KEY_NAME}
				, new IpfsKey[] { REMOTE_PUBLIC_KEY, PUBLIC_KEY}
		);
		IpfsFile originalRoot = remoteConnection.storeData(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_INDEX_SIZE_BYTES + 1)]));
		remoteConnection.publish(REMOTE_KEY_NAME, originalRoot);
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(sharedConnection), true);
		// For this test, we want to just fake a default config.
		StandardAccess.createForWrite(executor, IPFS_HOST, KEY_NAME).close();
		
		try {
			command.runInEnvironment(executor);
			Assert.fail();
		} catch (SizeConstraintException e) {
			// Expected.
		}
		executor.shutdown();
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
		executor.shutdown();
	}


	private static void _configureCluster(MockSingleNode[] nodes, String[] keyNames, IpfsKey[] keys)
	{
		MockSingleNode.connectPeers(nodes[0], nodes[1]);
		nodes[0].addNewKey(keyNames[0], keys[0]);
		nodes[1].addNewKey(keyNames[1], keys[1]);
	}
}
