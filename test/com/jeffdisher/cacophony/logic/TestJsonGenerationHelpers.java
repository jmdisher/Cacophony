package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.scheduler.SingleThreadedScheduler;
import com.jeffdisher.cacophony.testutils.MemoryConfigFileSystem;
import com.jeffdisher.cacophony.testutils.MockConnectionFactory;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.SizeLimits;


public class TestJsonGenerationHelpers
{
	public static final IpfsFile FILE1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
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
		LocalRecordCache cache = new LocalRecordCache(Map.of(FILE1, new LocalRecordCache.Element("string", "description", 1L, "discussionUrl", false, null, null)));
		JsonObject data = JsonGenerationHelpers.postStruct(cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"cached\":false}", data.toString());
	}

	@Test
	public void testPostStructCached() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache(Map.of(FILE1, new LocalRecordCache.Element("string", "description", 1L, "discussionUrl", true, "url1", "url2")));
		JsonObject data = JsonGenerationHelpers.postStruct(cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"cached\":true,\"thumbnailUrl\":\"url1\",\"videoUrl\":\"url2\"}", data.toString());
	}

	@Test
	public void testFolloweeKeys() throws Throwable
	{
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		JsonArray followeeKeys = JsonGenerationHelpers.followeeKeys(followIndex);
		Assert.assertEquals("[]", followeeKeys.toString());
	}

	@Test
	public void testPrefs() throws Throwable
	{
		GlobalPrefs prefs = GlobalPrefs.defaultPrefs();
		JsonObject data = JsonGenerationHelpers.prefs(prefs);
		Assert.assertEquals("{\"edgeSize\":1280,\"followerCacheBytes\":10000000000}", data.toString());
	}

	@Test
	public void testBuildFolloweeCacheEmpty() throws Throwable
	{
		MockSingleNode remoteConnection = new MockSingleNode();
		remoteConnection.addNewKey(KEY_NAME, PUBLIC_KEY1);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(remoteConnection), true);
		RemoteActions actions = RemoteActions.loadIpfsConfig(executor, remoteConnection, KEY_NAME);
		SingleThreadedScheduler scheduler = new SingleThreadedScheduler(actions);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		IpfsFile indexFile = _storeNewIndex(remoteConnection, pinCache, null, null);
		remoteConnection.publish(KEY_NAME, indexFile);
		LoadChecker loadChecker = new LoadChecker(scheduler, pinCache, remoteConnection);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		LocalRecordCache recordCache = JsonGenerationHelpers.buildFolloweeCache(scheduler, loadChecker, indexFile, followIndex);
		
		// This should have zero entries.
		Assert.assertTrue(recordCache.getKeys().isEmpty());
		// Make sure that we fail to look something up.
		JsonObject object = JsonGenerationHelpers.postStruct(recordCache, FILE1);
		Assert.assertNull(object);
	}

	@Test
	public void testBuildFolloweeCacheWithEntries() throws Throwable
	{
		MockSingleNode remoteConnection = new MockSingleNode();
		remoteConnection.addNewKey(KEY_NAME, PUBLIC_KEY1);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(remoteConnection), true);
		RemoteActions actions = RemoteActions.loadIpfsConfig(executor, remoteConnection, KEY_NAME);
		SingleThreadedScheduler scheduler = new SingleThreadedScheduler(actions);
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		IpfsFile recordFile = _storeEntry(remoteConnection, pinCache, "entry1", PUBLIC_KEY1);
		IpfsFile indexFile = _storeNewIndex(remoteConnection, pinCache, recordFile, null);
		remoteConnection.publish(KEY_NAME, indexFile);
		LoadChecker loadChecker = new LoadChecker(scheduler, pinCache, remoteConnection);
		FollowIndex followIndex = FollowIndex.emptyFollowIndex();
		
		IpfsFile followeeRecordFile = _storeEntry(remoteConnection, pinCache, "entry2", PUBLIC_KEY2);
		// We want to create an oversized record to make sure that it is not in cached list.
		IpfsFile oversizeRecordFile = _storeData(remoteConnection, pinCache, new byte[(int) (SizeLimits.MAX_RECORD_SIZE_BYTES + 1)]);
		IpfsFile followeeIndexFile = _storeNewIndex(remoteConnection, pinCache, followeeRecordFile, oversizeRecordFile);
		FollowRecord record = new FollowRecord(PUBLIC_KEY2, followeeIndexFile, 1L, new FollowingCacheElement[] {
				new FollowingCacheElement(followeeRecordFile, null, null, 0L)
		});
		followIndex.checkinRecord(record);
		LocalRecordCache recordCache = JsonGenerationHelpers.buildFolloweeCache(scheduler, loadChecker, indexFile, followIndex);
		
		// Make sure that we have both entries (not the oversized one - that will be ignored since we couldn't read it).
		Assert.assertEquals(2, recordCache.getKeys().size());
		JsonObject object = JsonGenerationHelpers.postStruct(recordCache, recordFile);
		Assert.assertEquals("entry1", object.get("name").asString());
		object = JsonGenerationHelpers.postStruct(recordCache, followeeRecordFile);
		Assert.assertEquals("entry2", object.get("name").asString());
	}


	private static IpfsFile _storeNewIndex(MockSingleNode connection, GlobalPinCache pinCache, IpfsFile record1, IpfsFile record2) throws IpfsConnectionException
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
		IpfsFile recordsFile = _storeData(connection, pinCache, data);
		StreamRecommendations recommendations = new StreamRecommendations();
		data = GlobalData.serializeRecommendations(recommendations);
		IpfsFile recommendationsFile = _storeData(connection, pinCache, data);
		IpfsFile picFile = _storeData(connection, pinCache, new byte[] { 1, 2, 3, 4, 5 });
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(picFile.toSafeString());
		data = GlobalData.serializeDescription(description);
		IpfsFile descriptionFile = _storeData(connection, pinCache, data);
		StreamIndex index = new StreamIndex();
		index.setDescription(descriptionFile.toSafeString());
		index.setRecommendations(recommendationsFile.toSafeString());
		index.setRecords(recordsFile.toSafeString());
		index.setVersion(1);
		data = GlobalData.serializeIndex(index);
		return _storeData(connection, pinCache, data);
	}

	private static IpfsFile _storeEntry(MockSingleNode connection, GlobalPinCache pinCache, String name, IpfsKey publisher) throws IpfsConnectionException
	{
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription("");
		record.setElements(new DataArray());
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(publisher.toPublicKey());
		byte[] data = GlobalData.serializeRecord(record);
		return _storeData(connection, pinCache, data);
	}

	private static IpfsFile _storeData(MockSingleNode connection, GlobalPinCache pinCache, byte[] data) throws IpfsConnectionException
	{
		// We are directly going to check the pinCache since we are force-feeding the data for the test, but MockConnection doesn't like that.
		IpfsFile file = null;
		IpfsFile hash = MockSingleNode.generateHash(data);
		if (pinCache.shouldPinAfterAdding(hash))
		{
			file = connection.storeData(new ByteArrayInputStream(data));
		}
		else
		{
			file = hash;
		}
		return file;
	}
}
