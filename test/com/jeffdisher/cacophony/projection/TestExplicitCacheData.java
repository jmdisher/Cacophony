package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestExplicitCacheData
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});
	public static final IpfsFile F4 = MockSingleNode.generateHash(new byte[] {4});
	public static final IpfsFile F5 = MockSingleNode.generateHash(new byte[] {5});
	public static final IpfsFile F6 = MockSingleNode.generateHash(new byte[] {6});

	@Test
	public void empty() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		ExplicitCacheData explicitCache = _codec(start);
		Assert.assertNotNull(explicitCache);
	}

	@Test
	public void basicUsage() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		// These are overlapping in ways we would never normally see but the cache doesn't care.
		// The only CID it cares about is the first one - the location of the element it is describing.
		_addStreamRecord(start, F1, F2, null, null, 5L);
		start.addUserInfo(MockKeys.K0, 1L, F5, F2, F6, F3, F4, 10L);
		ExplicitCacheData explicitCache = _codec(start);
		CachedRecordInfo recordInfo = explicitCache.getRecordInfo(F1);
		ExplicitCacheData.UserInfo userInfo = explicitCache.getUserInfo(MockKeys.K0);
		Assert.assertEquals(F2, recordInfo.thumbnailCid());
		Assert.assertNull(recordInfo.videoCid());
		Assert.assertNull(recordInfo.audioCid());
		Assert.assertEquals(F4, userInfo.userPicCid());
		Assert.assertEquals(F6, userInfo.recordsCid());
		Assert.assertEquals(10L, userInfo.combinedSizeBytes());
		int unpinCount[] = new int[1];
		explicitCache.purgeCacheToSize((IpfsFile unpin) -> unpinCount[0] += 1, 4L);
		Assert.assertEquals(6, unpinCount[0]);
	}

	@Test
	public void basicLru() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		_addStreamRecord(start, F1, F2, null, null, 5L);
		_addStreamRecord(start, F2, F2, F3, null, 20L);
		_addStreamRecord(start, F3, null, null, null, 1L);
		_addStreamRecord(start, F4, F2, null, null, 5L);
		_addStreamRecord(start, F5, F2, F4, null, 25L);
		ExplicitCacheData explicitCache = _codec(start);
		List<IpfsFile> unpins = new ArrayList<>();
		explicitCache.purgeCacheToSize((IpfsFile unpin) -> unpins.add(unpin), 30L);
		// We should be left with only the F4 and F5 elements, seeing everything else unpinned in the order we entered it.
		Assert.assertEquals(6, unpins.size());
		Assert.assertEquals(F1, unpins.get(0));
		Assert.assertEquals(F2, unpins.get(1));
		Assert.assertEquals(F2, unpins.get(2));
		Assert.assertEquals(F2, unpins.get(3));
		Assert.assertEquals(F3, unpins.get(4));
		Assert.assertEquals(F3, unpins.get(5));
	}

	@Test
	public void readingLru() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		_addStreamRecord(start, F1, F2, null, null, 5L);
		_addStreamRecord(start, F2, F2, F3, null, 20L);
		_addStreamRecord(start, F3, null, null, null, 1L);
		_addStreamRecord(start, F4, F2, null, null, 5L);
		_addStreamRecord(start, F5, F2, F4, null, 25L);
		ExplicitCacheData explicitCache = _codec(start);
		// Read some of these elements to make sure that we see the unpins in the expected LRU order.
		explicitCache.getRecordInfo(F1);
		explicitCache.getRecordInfo(F3);
		explicitCache.getRecordInfo(F4);
		List<IpfsFile> unpins = new ArrayList<>();
		explicitCache.purgeCacheToSize((IpfsFile unpin) -> unpins.add(unpin), 11L);
		// We should see F2 nd F5 released first.
		Assert.assertEquals(6, unpins.size());
		Assert.assertEquals(F2, unpins.get(0));
		Assert.assertEquals(F2, unpins.get(1));
		Assert.assertEquals(F3, unpins.get(2));
		Assert.assertEquals(F5, unpins.get(3));
		Assert.assertEquals(F2, unpins.get(4));
		Assert.assertEquals(F4, unpins.get(5));
	}

	@Test
	public void pinCounts() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		_addStreamRecord(start, F1, F2, null, null, 5L);
		_addStreamRecord(start, F2, F2, F3, null, 20L);
		_addStreamRecord(start, F3, null, null, null, 1L);
		_addStreamRecord(start, F4, F2, null, null, 5L);
		_addStreamRecord(start, F5, F2, F4, null, 25L);
		ExplicitCacheData explicitCache = _codec(start);
		
		// Test the expected pin counts.
		Map<IpfsFile, Integer> pinCount = new HashMap<>();
		explicitCache.walkAllPins((IpfsFile pin) -> {
			int count = pinCount.getOrDefault(pin, 0);
			count += 1;
			pinCount.put(pin, count);
		});
		Assert.assertEquals(5, pinCount.size());
		Assert.assertEquals(1, pinCount.get(F1).intValue());
		Assert.assertEquals(5, pinCount.get(F2).intValue());
		Assert.assertEquals(2, pinCount.get(F3).intValue());
		Assert.assertEquals(2, pinCount.get(F4).intValue());
		Assert.assertEquals(1, pinCount.get(F5).intValue());
	}

	@Test
	public void verifySuccessiveSerialization() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		// These are overlapping in ways we would never normally see but the cache doesn't care.
		// The only CID it cares about is the first one - the location of the element it is describing.
		_addStreamRecord(start, F1, F2, null, null, 5L);
		start.addUserInfo(MockKeys.K0, 1L, F5, F2, F6, F3, F4, 10L);
		ExplicitCacheData explicitCache = _codec(start);
		CachedRecordInfo recordInfo = explicitCache.getRecordInfo(F1);
		ExplicitCacheData.UserInfo userInfo = explicitCache.getUserInfo(MockKeys.K0);
		
		// Verify that serializing always gives the same result.
		byte[] serial1 = _serialize(explicitCache);
		byte[] serial2 = _serialize(explicitCache);
		Assert.assertArrayEquals(serial1, serial2);
		
		// Verify that serialization doesn't break anything.
		Assert.assertEquals(F2, recordInfo.thumbnailCid());
		Assert.assertNull(recordInfo.videoCid());
		Assert.assertNull(recordInfo.audioCid());
		Assert.assertEquals(F4, userInfo.userPicCid());
		Assert.assertEquals(F6, userInfo.recordsCid());
		Assert.assertEquals(10L, userInfo.combinedSizeBytes());
		int unpinCount[] = new int[1];
		explicitCache.purgeCacheToSize((IpfsFile unpin) -> unpinCount[0] += 1, 4L);
		Assert.assertEquals(6, unpinCount[0]);
	}


	private static byte[] _serialize(ExplicitCacheData start) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			start.serializeToOpcodeWriter(writer);
		}
		return out.toByteArray();
	}

	private static ExplicitCacheData _codec(ExplicitCacheData start) throws IOException
	{
		byte[] bytes = _serialize(start);
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContext context = new OpcodeContext(null, null, null, null, explicitCache);
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes))
		{
			OpcodeCodec.decodeWholeStream(input, context);
		}
		return explicitCache;
	}

	private static void _addStreamRecord(ExplicitCacheData data, IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes)
	{
		CachedRecordInfo info = new CachedRecordInfo(streamCid, false, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
		data.addStreamRecord(streamCid, info);
	}
}
