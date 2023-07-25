package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.DataDomain;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.testutils.MockSwarm;
import com.jeffdisher.cacophony.testutils.SilentLogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class TestJsonGenerationHelpers
{
	@ClassRule
	public static TemporaryFolder FOLDER = new TemporaryFolder();
	public static final IpfsFile FILE1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile FILE2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile FILE3 = MockSingleNode.generateHash(new byte[] {3});
	private static final String KEY_NAME = "keyName";

	@Test
	public void testDataVersion() throws Throwable
	{
		JsonObject data = JsonGenerationHelpers.dataVersion();
		Assert.assertTrue(data.toString().startsWith("{\"hash\":\""));
	}

	@Test
	public void testPrefs() throws Throwable
	{
		PrefsData prefs = PrefsData.defaultPrefs();
		JsonObject data = JsonGenerationHelpers.prefs(prefs);
		Assert.assertEquals("{\"videoEdgePixelMax\":1280,\"followCacheTargetBytes\":10000000000,\"republishIntervalMillis\":43200000,\"followeeRefreshMillis\":3600000,\"explicitCacheTargetBytes\":1000000000,\"followeeRecordThumbnailMaxBytes\":10000000,\"followeeRecordAudioMaxBytes\":200000000,\"followeeRecordVideoMaxBytes\":2000000000}", data.toString());
	}

	@Test
	public void testBuildFolloweeCacheEmpty() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		remoteConnection.addNewKey(KEY_NAME, MockKeys.K1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(remoteConnection, 1);
		SilentLogger logger = new SilentLogger();
		
		IpfsFile indexFile = null;
		Context context = new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
				, model
				, remoteConnection
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, null
				, MockKeys.K1
		);
		try (IWritingAccess access = StandardAccess.writeAccessWithKeyOverride(context, KEY_NAME, MockKeys.K1))
		{
			indexFile = _storeNewIndex(access, null, null, true);
		}
		
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IFolloweeReading followIndex = access.readableFolloweeData();
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, recordCache, userInfoCache, null, context.getSelectedKey(), indexFile);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, recordCache, userInfoCache, null, followIndex);
		}
		
		// This should have zero entries.
		Assert.assertTrue(recordCache.getKeys().isEmpty());
		// Make sure that we fail to look something up.
		Assert.assertNull(recordCache.get(FILE1));
		scheduler.shutdown();
	}

	@Test
	public void testBuildFolloweeCacheWithEntries() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		remoteConnection.addNewKey(KEY_NAME, MockKeys.K1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(remoteConnection, 1);
		SilentLogger logger = new SilentLogger();
		
		IpfsFile recordFile = null;
		IpfsFile indexFile = null;
		IpfsFile followeeRecordFile = null;
		Context context = new Context(new DraftManager(fileSystem.getDraftsTopLevelDirectory())
				, model
				, remoteConnection
				, scheduler
				, () -> System.currentTimeMillis()
				, logger
				, DataDomain.FAKE_BASE_URL
				, null
				, null
				, null
				, null
				, MockKeys.K1
		);
		try (IWritingAccess access = StandardAccess.writeAccessWithKeyOverride(context, KEY_NAME, MockKeys.K1))
		{
			recordFile = _storeEntry(access, "entry1", MockKeys.K1);
			indexFile = _storeNewIndex(access, recordFile, null, true);
			
			followeeRecordFile = _storeEntry(access, "entry2", MockKeys.K2);
			// We want to create an oversized record to make sure that it is not in cached list.
			IpfsFile oversizeRecordFile = access.uploadAndPin(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_RECORD_SIZE_BYTES + 1)]));
			
			IFolloweeWriting followIndex = access.writableFolloweeData();
			IpfsFile followeeIndexFile = _storeNewIndex(access, followeeRecordFile, oversizeRecordFile, false);
			followIndex.createNewFollowee(MockKeys.K2, followeeIndexFile, 1L);
			followIndex.addElement(MockKeys.K2, new FollowingCacheElement(followeeRecordFile, null, null, 0L));
		}
		
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		HomeUserReplyCache replyCache = new HomeUserReplyCache(new HandoffConnector<IpfsFile, IpfsFile>((Runnable run) -> run.run()));
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile publishedIndex = access.getLastRootElement();
			Assert.assertEquals(indexFile, publishedIndex);
			IFolloweeReading followIndex = access.readableFolloweeData();
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, recordCache, userInfoCache, replyCache, context.getSelectedKey(), indexFile);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, recordCache, userInfoCache, replyCache, followIndex);
		}
		
		// Make sure that we have both entries (not the oversized one - that will be ignored since we couldn't read it).
		Assert.assertEquals(2, recordCache.getKeys().size());
		Assert.assertEquals("entry1", recordCache.get(recordFile).name());
		Assert.assertEquals("entry2", recordCache.get(followeeRecordFile).name());
		scheduler.shutdown();
	}


	private static IpfsFile _storeNewIndex(IWritingAccess access, IpfsFile record1, IpfsFile record2, boolean shouldStoreAsIndex) throws IpfsConnectionException, SizeConstraintException
	{
		StreamRecords records = new StreamRecords();
		if (null != record1)
		{
			records.getRecord().add(record1.toSafeString());
		}
		if (null != record2)
		{
			records.getRecord().add(record2.toSafeString());
		}
		byte[] data = GlobalData.serializeRecords(records);
		IpfsFile recordsFile = access.uploadAndPin(new ByteArrayInputStream(data));
		StreamRecommendations recommendations = new StreamRecommendations();
		data = GlobalData.serializeRecommendations(recommendations);
		IpfsFile recommendationsFile = access.uploadAndPin(new ByteArrayInputStream(data));
		IpfsFile picFile = access.uploadAndPin(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5 }));
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(picFile.toSafeString());
		data = GlobalData.serializeDescription(description);
		IpfsFile descriptionFile = access.uploadAndPin(new ByteArrayInputStream(data));
		AbstractIndex index = AbstractIndex.createNew();
		index.descriptionCid = descriptionFile;
		index.recommendationsCid = recommendationsFile;
		index.recordsCid = recordsFile;
		
		IpfsFile indexHash = null;
		// Note that we only want to store this _as_ the index if this is the owner of the storage, since this helper is sometimes used to simulate followee refresh.
		if (shouldStoreAsIndex)
		{
			indexHash = access.uploadIndexAndUpdateTracking(index);
			FuturePublish publish = access.beginIndexPublish(indexHash);
			publish.get();
			indexHash = publish.getIndexHash();
		}
		else
		{
			indexHash = access.uploadAndPin(new ByteArrayInputStream(index.serializeV1()));
		}
		return indexHash;
	}

	private static IpfsFile _storeEntry(IWritingAccess access, String name, IpfsKey publisher) throws IpfsConnectionException, SizeConstraintException
	{
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription("");
		record.setElements(new DataArray());
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(publisher.toPublicKey());
		byte[] data = GlobalData.serializeRecord(record);
		return access.uploadAndPin(new ByteArrayInputStream(data));
	}
}
