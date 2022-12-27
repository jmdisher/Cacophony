package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
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
		LocalRecordCache cache = new LocalRecordCache(Map.of(FILE1, new LocalRecordCache.Element("string", "description", 1L, "discussionUrl", false, null, null, null)));
		JsonObject data = JsonGenerationHelpers.postStruct(cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"cached\":false}", data.toString());
	}

	@Test
	public void testPostStructCached() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache(Map.of(FILE1, new LocalRecordCache.Element("string", "description", 1L, "discussionUrl", true, "url1", "url2", null)));
		JsonObject data = JsonGenerationHelpers.postStruct(cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"cached\":true,\"thumbnailUrl\":\"url1\",\"videoUrl\":\"url2\",\"audioUrl\":null}", data.toString());
	}

	@Test
	public void testPostStructCachedAudio() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache(Map.of(FILE1, new LocalRecordCache.Element("string", "description", 1L, "discussionUrl", true, "url1", null, "audio URL")));
		JsonObject data = JsonGenerationHelpers.postStruct(cache, FILE1);
		Assert.assertEquals("{\"name\":\"string\",\"description\":\"description\",\"publishedSecondsUtc\":1,\"discussionUrl\":\"discussionUrl\",\"cached\":true,\"thumbnailUrl\":\"url1\",\"videoUrl\":null,\"audioUrl\":\"audio URL\"}", data.toString());
	}

	@Test
	public void testFolloweeKeys() throws Throwable
	{
		FolloweeData followIndex = FolloweeData.buildOnIndex(FollowIndex.emptyFollowIndex());
		JsonArray followeeKeys = JsonGenerationHelpers.followeeKeys(followIndex);
		Assert.assertEquals("[]", followeeKeys.toString());
	}

	@Test
	public void testPrefs() throws Throwable
	{
		PrefsData prefs = PrefsData.defaultPrefs();
		JsonObject data = JsonGenerationHelpers.prefs(prefs);
		Assert.assertEquals("{\"edgeSize\":1280,\"followerCacheBytes\":10000000000}", data.toString());
	}

	@Test
	public void testBuildFolloweeCacheEmpty() throws Throwable
	{
		MockSingleNode remoteConnection = new MockSingleNode();
		remoteConnection.addNewKey(KEY_NAME, PUBLIC_KEY1);
		StandardEnvironment executor = new StandardEnvironment(System.out, new MemoryConfigFileSystem(), new MockConnectionFactory(remoteConnection), true);
		
		IpfsFile indexFile = null;
		StandardAccess.createNewChannelConfig(executor, "ipfs", KEY_NAME);
		try (IWritingAccess access = StandardAccess.writeAccess(executor))
		{
			indexFile = _storeNewIndex(access, null, null, true);
		}
		
		LocalRecordCache recordCache = null;
		try (IReadingAccess access = StandardAccess.readAccess(executor))
		{
			IFolloweeReading followIndex = access.readableFolloweeData();
			recordCache = JsonGenerationHelpers.buildFolloweeCache(access, indexFile, followIndex);
		}
		
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
		
		IpfsFile recordFile = null;
		IpfsFile indexFile = null;
		IpfsFile followeeRecordFile = null;
		StandardAccess.createNewChannelConfig(executor, "ipfs", KEY_NAME);
		try (IWritingAccess access = StandardAccess.writeAccess(executor))
		{
			recordFile = _storeEntry(access, "entry1", PUBLIC_KEY1);
			indexFile = _storeNewIndex(access, recordFile, null, true);
			
			followeeRecordFile = _storeEntry(access, "entry2", PUBLIC_KEY2);
			// We want to create an oversized record to make sure that it is not in cached list.
			IpfsFile oversizeRecordFile = access.uploadAndPin(new ByteArrayInputStream(new byte[(int) (SizeLimits.MAX_RECORD_SIZE_BYTES + 1)]), true);
			
			IFolloweeWriting followIndex = access.writableFolloweeData();
			IpfsFile followeeIndexFile = _storeNewIndex(access, followeeRecordFile, oversizeRecordFile, false);
			followIndex.createNewFollowee(PUBLIC_KEY2, followeeIndexFile, 1L);
			followIndex.addElement(PUBLIC_KEY2, new FollowingCacheElement(followeeRecordFile, null, null, 0L));
		}
		
		LocalRecordCache recordCache = null;
		try (IReadingAccess access = StandardAccess.readAccess(executor))
		{
			IpfsFile publishedIndex = access.getLastRootElement();
			Assert.assertEquals(indexFile, publishedIndex);
			IFolloweeReading followIndex = access.readableFolloweeData();
			recordCache = JsonGenerationHelpers.buildFolloweeCache(access, publishedIndex, followIndex);
		}
		
		// Make sure that we have both entries (not the oversized one - that will be ignored since we couldn't read it).
		Assert.assertEquals(2, recordCache.getKeys().size());
		JsonObject object = JsonGenerationHelpers.postStruct(recordCache, recordFile);
		Assert.assertEquals("entry1", object.get("name").asString());
		object = JsonGenerationHelpers.postStruct(recordCache, followeeRecordFile);
		Assert.assertEquals("entry2", object.get("name").asString());
	}


	private static IpfsFile _storeNewIndex(IWritingAccess access, IpfsFile record1, IpfsFile record2, boolean shouldStoreAsIndex) throws IpfsConnectionException
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
		IpfsFile recordsFile = access.uploadAndPin(new ByteArrayInputStream(data), true);
		StreamRecommendations recommendations = new StreamRecommendations();
		data = GlobalData.serializeRecommendations(recommendations);
		IpfsFile recommendationsFile = access.uploadAndPin(new ByteArrayInputStream(data), true);
		IpfsFile picFile = access.uploadAndPin(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5 }), true);
		StreamDescription description = new StreamDescription();
		description.setName("name");
		description.setDescription("description");
		description.setPicture(picFile.toSafeString());
		data = GlobalData.serializeDescription(description);
		IpfsFile descriptionFile = access.uploadAndPin(new ByteArrayInputStream(data), true);
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
			indexHash = access.uploadAndPin(new ByteArrayInputStream(GlobalData.serializeIndex(index)), true);
		}
		return indexHash;
	}

	private static IpfsFile _storeEntry(IWritingAccess access, String name, IpfsKey publisher) throws IpfsConnectionException
	{
		StreamRecord record = new StreamRecord();
		record.setName(name);
		record.setDescription("");
		record.setElements(new DataArray());
		record.setPublishedSecondsUtc(1L);
		record.setPublisherKey(publisher.toPublicKey());
		byte[] data = GlobalData.serializeRecord(record);
		return access.uploadAndPin(new ByteArrayInputStream(data), true);
	}
}
