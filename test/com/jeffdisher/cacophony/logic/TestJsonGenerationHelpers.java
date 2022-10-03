package com.jeffdisher.cacophony.logic;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPrefs;
import com.jeffdisher.cacophony.data.local.v1.LocalRecordCache;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestJsonGenerationHelpers
{
	public static final IpfsFile FILE1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");

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
}
