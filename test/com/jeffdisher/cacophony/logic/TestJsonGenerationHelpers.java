package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.ICommand;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.MultiThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
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
	public static final IpfsFile FILE1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile FILE2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile FILE3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");
	private static final String KEY_NAME = "keyName";
	private static final IpfsKey PUBLIC_KEY1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");
	private static final IpfsKey PUBLIC_KEY2 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo141");

	@Test
	public void testDataVersion() throws Throwable
	{
		JsonObject data = JsonGenerationHelpers.dataVersion();
		Assert.assertTrue(data.toString().startsWith("{\"hash\":\""));
	}

	@Test
	public void testPostStructNotCached() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(FILE1, "string", "description", 1L, "discussionUrl", PUBLIC_KEY1.toPublicKey(), 1);
		JsonObject data = JsonGenerationHelpers.postStruct(null, cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"publisherKey\":\"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F\",\"cached\":false}", data.toString());
	}

	@Test
	public void testPostStructImplicitCached() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(FILE1, "string", "description", 1L, "discussionUrl", PUBLIC_KEY1.toPublicKey(), 0);
		JsonObject data = JsonGenerationHelpers.postStruct(null, cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"publisherKey\":\"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F\",\"cached\":true,\"thumbnailUrl\":null,\"videoUrl\":null,\"audioUrl\":null}", data.toString());
	}

	@Test
	public void testPostStructCached() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(FILE1, "string", "description", 1L, "discussionUrl", PUBLIC_KEY1.toPublicKey(), 2);
		cache.recordThumbnailPinned(FILE1, FILE2);
		cache.recordVideoPinned(FILE1, FILE3, 100);
		JsonObject data = JsonGenerationHelpers.postStruct("url/", cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"publisherKey\":\"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F\",\"cached\":true,\"thumbnailUrl\":\"url/QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG\",\"videoUrl\":\"url/QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC\",\"audioUrl\":null}", data.toString());
	}

	@Test
	public void testPostStructCachedAudio() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(FILE1, "string", "description", 1L, "discussionUrl", PUBLIC_KEY1.toPublicKey(), 2);
		cache.recordThumbnailPinned(FILE1, FILE2);
		cache.recordAudioPinned(FILE1, FILE3);
		JsonObject data = JsonGenerationHelpers.postStruct("url/", cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"publisherKey\":\"z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F\",\"cached\":true,\"thumbnailUrl\":\"url/QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG\",\"videoUrl\":null,\"audioUrl\":\"url/QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC\"}", data.toString());
	}

	@Test
	public void testPrefs() throws Throwable
	{
		PrefsData prefs = PrefsData.defaultPrefs();
		JsonObject data = JsonGenerationHelpers.prefs(prefs);
		Assert.assertEquals("{\"edgeSize\":1280,\"followerCacheBytes\":10000000000,\"republishIntervalMillis\":43200000,\"followeeRefreshMillis\":3600000}", data.toString());
	}

	@Test
	public void testBuildFolloweeCacheEmpty() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		remoteConnection.addNewKey(KEY_NAME, PUBLIC_KEY1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null, "ipfs", KEY_NAME, true);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(remoteConnection, 1);
		StandardEnvironment executor = new StandardEnvironment(fileSystem.getDraftsTopLevelDirectory()
				, model
				, remoteConnection
				, scheduler
		);
		SilentLogger logger = new SilentLogger();
		
		IpfsFile indexFile = null;
		ICommand.Context context = new ICommand.Context(executor
				, logger
				, null
				, null
				, null
				, KEY_NAME
				, PUBLIC_KEY1
		);
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			indexFile = _storeNewIndex(access, null, null, true);
		}
		
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IFolloweeReading followIndex = access.readableFolloweeData();
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, recordCache, userInfoCache, context.publicKey, indexFile);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, recordCache, userInfoCache, followIndex);
		}
		
		// This should have zero entries.
		Assert.assertTrue(recordCache.getKeys().isEmpty());
		// Make sure that we fail to look something up.
		JsonObject object = JsonGenerationHelpers.postStruct(null, recordCache, FILE1);
		Assert.assertNull(object);
		scheduler.shutdown();
	}

	@Test
	public void testBuildFolloweeCacheWithEntries() throws Throwable
	{
		MockSwarm swarm = new MockSwarm();
		MockSingleNode remoteConnection = new MockSingleNode(swarm);
		remoteConnection.addNewKey(KEY_NAME, PUBLIC_KEY1);
		MemoryConfigFileSystem fileSystem = new MemoryConfigFileSystem(FOLDER.newFolder());
		LocalDataModel model = LocalDataModel.verifiedAndLoadedModel(fileSystem, null, "ipfs", KEY_NAME, true);
		MultiThreadedScheduler scheduler = new MultiThreadedScheduler(remoteConnection, 1);
		StandardEnvironment executor = new StandardEnvironment(fileSystem.getDraftsTopLevelDirectory()
				, model
				, remoteConnection
				, scheduler
		);
		SilentLogger logger = new SilentLogger();
		
		IpfsFile recordFile = null;
		IpfsFile indexFile = null;
		IpfsFile followeeRecordFile = null;
		ICommand.Context context = new ICommand.Context(executor
				, logger
				, null
				, null
				, null
				, KEY_NAME
				, PUBLIC_KEY1
		);
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			recordFile = _storeEntry(access, "entry1", PUBLIC_KEY1);
			indexFile = _storeNewIndex(access, recordFile, null, true);
			
			followeeRecordFile = _storeEntry(access, "entry2", PUBLIC_KEY2);
			// We want to create an oversized record to make sure that it is not in cached list.
			IpfsFile oversizeRecordFile = access.uploadAndPin(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_RECORD_SIZE_BYTES + 1)]));
			
			IFolloweeWriting followIndex = access.writableFolloweeData();
			IpfsFile followeeIndexFile = _storeNewIndex(access, followeeRecordFile, oversizeRecordFile, false);
			followIndex.createNewFollowee(PUBLIC_KEY2, followeeIndexFile);
			followIndex.addElement(PUBLIC_KEY2, new FollowingCacheElement(followeeRecordFile, null, null, 0L));
			followIndex.updateExistingFollowee(PUBLIC_KEY2, followeeIndexFile, 1L);
		}
		
		LocalRecordCache recordCache = new LocalRecordCache();
		LocalUserInfoCache userInfoCache = new LocalUserInfoCache();
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IpfsFile publishedIndex = access.getLastRootElement();
			Assert.assertEquals(indexFile, publishedIndex);
			IFolloweeReading followIndex = access.readableFolloweeData();
			LocalRecordCacheBuilder.populateInitialCacheForLocalUser(access, recordCache, userInfoCache, context.publicKey, indexFile);
			LocalRecordCacheBuilder.populateInitialCacheForFollowees(access, recordCache, userInfoCache, followIndex);
		}
		
		// Make sure that we have both entries (not the oversized one - that will be ignored since we couldn't read it).
		Assert.assertEquals(2, recordCache.getKeys().size());
		JsonObject object = JsonGenerationHelpers.postStruct("url/", recordCache, recordFile);
		Assert.assertEquals("entry1", object.get("name").asString());
		object = JsonGenerationHelpers.postStruct("url/", recordCache, followeeRecordFile);
		Assert.assertEquals("entry2", object.get("name").asString());
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
		StreamIndex index = new StreamIndex();
		index.setDescription(descriptionFile.toSafeString());
		index.setRecommendations(recommendationsFile.toSafeString());
		index.setRecords(recordsFile.toSafeString());
		index.setVersion(1);
		
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
			indexHash = access.uploadAndPin(new ByteArrayInputStream(GlobalData.serializeIndex(index)));
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
