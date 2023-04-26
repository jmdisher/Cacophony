package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.StandardEnvironment;
import com.jeffdisher.cacophony.logic.StandardLogger;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class TestStartFollowingCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String IPFS_HOST = "ipfsHost";
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final String REMOTE_KEY_NAME = "remoteKey";
	private static final IpfsKey REMOTE_PUBLIC_KEY = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14f");

	@Test
	public void testUsage() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		MockSingleNode sharedConnection = new MockSingleNode(swarm);
		_configureCluster(
				new MockSingleNode[] { remoteConnection, sharedConnection}
				, new String[] { REMOTE_KEY_NAME, KEY_NAME}
				, new IpfsKey[] { REMOTE_PUBLIC_KEY, PUBLIC_KEY}
		);
		IpfsFile originalRecordsCid = remoteConnection.storeData(new ByteArrayInputStream(GlobalData.serializeRecords(new StreamRecords())));
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(sharedConnection, 1);
		LocalDataModel localDataModel = LocalDataModel.verifiedAndLoadedModel(fileSystem, scheduler, IPFS_HOST, KEY_NAME, true);
		StandardEnvironment executor = new StandardEnvironment(fileSystem.getDraftsTopLevelDirectory()
				, localDataModel
				, sharedConnection
				, scheduler
		);
		SilentLogger logger = new SilentLogger();
		
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
		
		remoteConnection.publish(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, originalRoot);
		ICommand.Context context = new ICommand.Context(executor
				, logger
				, null
				, null
				, null
				, KEY_NAME
				, PUBLIC_KEY
		);
		command.runInContext(context);
		
		// Verify the states that should have changed.
		Assert.assertTrue(sharedConnection.isPinned(originalRoot));
		Assert.assertTrue(sharedConnection.isPinned(originalDescription));
		Assert.assertTrue(sharedConnection.isPinned(originalRecommendations));
		Assert.assertTrue(sharedConnection.isPinned(originalRecordsCid));
		Assert.assertTrue(sharedConnection.isPinned(originalPicture));
		
		// Make sure that the local index is correct.
		IpfsFile lastPublishedIndex;
		try (IReadingAccess reading = StandardAccess.readAccess(context))
		{
			lastPublishedIndex = reading.getLastRootElement();
		}
		// (since we started with a null published index (not normally something which can happen), and didn't publish a change, we expect it to still be null).
		Assert.assertNull(lastPublishedIndex);
		scheduler.shutdown();
	}

	@Test
	public void testErrorSize() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		MockSingleNode sharedConnection = new MockSingleNode(swarm);
		_configureCluster(
				new MockSingleNode[] { remoteConnection, sharedConnection}
				, new String[] { REMOTE_KEY_NAME, KEY_NAME}
				, new IpfsKey[] { REMOTE_PUBLIC_KEY, PUBLIC_KEY}
		);
		IpfsFile originalRoot = remoteConnection.storeData(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_INDEX_SIZE_BYTES + 1)]));
		remoteConnection.publish(REMOTE_KEY_NAME, REMOTE_PUBLIC_KEY, originalRoot);
		
		StartFollowingCommand command = new StartFollowingCommand(REMOTE_PUBLIC_KEY);
		// We are expecting the error to be logged so we want to capture the output to make sure we see it.
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(sharedConnection, 1);
		LocalDataModel localDataModel = LocalDataModel.verifiedAndLoadedModel(fileSystem, scheduler, IPFS_HOST, KEY_NAME, true);
		StandardEnvironment executor = new StandardEnvironment(fileSystem.getDraftsTopLevelDirectory()
				, localDataModel
				, sharedConnection
				, scheduler
		);
		StandardLogger logger = StandardLogger.topLogger(new PrintStream(outputStream));
		
		boolean didFail = false;
		try
		{
			command.runInContext(new ICommand.Context(executor
					, logger
					, null
					, null
					, null
					, KEY_NAME
					, PUBLIC_KEY
			));
		}
		catch (ProtocolDataException e)
		{
			didFail = true;
		}
		Assert.assertTrue(didFail);
		scheduler.shutdown();
		
		// Check for the error message.
		Assert.assertTrue(new String(outputStream.toByteArray()).contains("Followee meta-data element too big (probably wrong file published):  Size limit broken: index was 1025 but is limited to 1024"));
		
		// Check that the data shows nobody being followed.
		try (IReadOnlyLocalData access = localDataModel.openForRead())
		{
			Assert.assertTrue(access.readFollowIndex().getAllKnownFollowees().isEmpty());
		}
	}


	private static void _configureCluster(MockSingleNode[] nodes, String[] keyNames, IpfsKey[] keys)
	{
		nodes[0].addNewKey(keyNames[0], keys[0]);
		nodes[1].addNewKey(keyNames[1], keys[1]);
	}
}
