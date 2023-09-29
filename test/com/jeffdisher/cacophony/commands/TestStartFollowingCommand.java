package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StandardLogger;


public class TestStartFollowingCommand
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	private static final String KEY_NAME = "keyName";
	private static final String REMOTE_KEY_NAME = "remoteKey";

	@Test
	public void testUsage() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		MockSingleNode sharedConnection = new MockSingleNode(swarm);
		_configureCluster(
				new MockSingleNode[] { remoteConnection, sharedConnection}
				, new String[] { REMOTE_KEY_NAME, KEY_NAME}
				, new IpfsKey[] { MockKeys.K2, MockKeys.K1}
		);
		IpfsFile originalRecordsCid = remoteConnection.storeData(new ByteArrayInputStream(AbstractRecords.createNew().serializeV1()));
		
		StartFollowingCommand command = new StartFollowingCommand(MockKeys.K2);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(sharedConnection, 1);
		LocalDataModel localDataModel = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, fileSystem, scheduler);
		SilentLogger logger = new SilentLogger();
		
		AbstractDescription originalDescriptionData = AbstractDescription.createNew();
		originalDescriptionData.setName("name");
		IpfsFile originalPicture = remoteConnection.storeData(new ByteArrayInputStream(new byte[] { 0, 1, 2, 3, 4, 5 }));
		originalDescriptionData.setDescription("Description");
		originalDescriptionData.setUserPic("image/jpeg", originalPicture);
		IpfsFile originalDescription = remoteConnection.storeData(new ByteArrayInputStream(originalDescriptionData.serializeV1()));
		
		AbstractRecommendations recommendations = AbstractRecommendations.createNew();
		IpfsFile originalRecommendations = remoteConnection.storeData(new ByteArrayInputStream(recommendations.serializeV1()));
		AbstractIndex originalRootData = AbstractIndex.createNew();
		originalRootData.descriptionCid = originalDescription;
		originalRootData.recommendationsCid = originalRecommendations;
		originalRootData.recordsCid = originalRecordsCid;
		IpfsFile originalRoot = remoteConnection.storeData(new ByteArrayInputStream(originalRootData.serializeV1()));
		
		remoteConnection.publish(REMOTE_KEY_NAME, MockKeys.K2, originalRoot);
		Context context = new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
				, new Context.AccessTuple(localDataModel, sharedConnection, scheduler)
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, new CacheUpdater(null, null, null, null, null)
				, null
				, null
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
		try (IReadingAccess reading = Context.readAccess(context))
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
				, new IpfsKey[] { MockKeys.K2, MockKeys.K1}
		);
		IpfsFile originalRoot = remoteConnection.storeData(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_INDEX_SIZE_BYTES + 1)]));
		remoteConnection.publish(REMOTE_KEY_NAME, MockKeys.K2, originalRoot);
		
		StartFollowingCommand command = new StartFollowingCommand(MockKeys.K2);
		// We are expecting the error to be logged so we want to capture the output to make sure we see it.
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(sharedConnection, 1);
		LocalDataModel localDataModel = LocalDataModel.verifiedAndLoadedModel(LocalDataModel.NONE, fileSystem, scheduler);
		StandardLogger logger = StandardLogger.topLogger(new PrintStream(outputStream), false);
		
		boolean didFail = false;
		try
		{
			command.runInContext(new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
					, new Context.AccessTuple(localDataModel, sharedConnection, scheduler)
					, () -> System.currentTimeMillis()
					, logger
					, DataDomain.FAKE_BASE_URL
					, null
					, null
					, null
					, new CacheUpdater(null, null, null, null, null)
					, null
					, null
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
