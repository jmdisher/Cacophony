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

import com.jeffdisher.cacophony.data.local.v3.OpcodeContextV3;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SetPrefsInt;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SetPrefsLong;
import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * This test just verifies the behaviour of the V3->V4 migration and will be short-lived, removed when V3 support is
 * removed.
 */
public class TestV3V4
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});
	public static final IpfsFile F4 = MockSingleNode.generateHash(new byte[] {4});
	public static final IpfsFile F5 = MockSingleNode.generateHash(new byte[] {5});
	public static final IpfsFile F6 = MockSingleNode.generateHash(new byte[] {6});

	@Test
	public void explicitRecordsMatch() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		_addStreamRecord(start, F1, F2, null, null, 5L);
		
		ExplicitCacheData v3 = _codecV3(start);
		CachedRecordInfo recordInfo3 = v3.getRecordInfo(F1);
		Assert.assertEquals(F2, recordInfo3.thumbnailCid());
		Assert.assertNull(recordInfo3.videoCid());
		Assert.assertNull(recordInfo3.audioCid());
		
		ExplicitCacheData v4 = _codecV4(start);
		CachedRecordInfo recordInfo4 = v4.getRecordInfo(F1);
		Assert.assertEquals(F2, recordInfo4.thumbnailCid());
		Assert.assertNull(recordInfo4.videoCid());
		Assert.assertNull(recordInfo4.audioCid());
	}

	@Test
	public void explicitUsersChange() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		start.addUserInfo(MockKeys.K0, 1L, F5, F2, F6, F3, F4, 10L);
		
		ExplicitCacheData v3 = _codecV3(start);
		ExplicitCacheData.UserInfo user3 = v3.getUserInfo(MockKeys.K0);
		Assert.assertNull(user3);
		
		ExplicitCacheData v4 = _codecV4(start);
		ExplicitCacheData.UserInfo user4 = v4.getUserInfo(MockKeys.K0);
		Assert.assertEquals(MockKeys.K0, user4.publicKey());
		Assert.assertEquals(1L, user4.lastFetchAttemptMillis());
		Assert.assertEquals(1L, user4.lastFetchSuccessMillis());
		Assert.assertEquals(F5, user4.indexCid());
		Assert.assertEquals(F2, user4.recommendationsCid());
		Assert.assertEquals(F6, user4.recordsCid());
		Assert.assertEquals(F3, user4.descriptionCid());
		Assert.assertEquals(F4, user4.userPicCid());
		Assert.assertEquals(10L, user4.combinedSizeBytes());
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
		ExplicitCacheData explicitCache = _codecV4(start);
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
	public void pinCounts() throws Throwable
	{
		ExplicitCacheData start = new ExplicitCacheData();
		_addStreamRecord(start, F1, F2, null, null, 5L);
		_addStreamRecord(start, F2, F2, F3, null, 20L);
		_addStreamRecord(start, F3, null, null, null, 1L);
		_addStreamRecord(start, F4, F2, null, null, 5L);
		_addStreamRecord(start, F5, F2, F4, null, 25L);
		ExplicitCacheData explicitCache = _codecV4(start);
		
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
		ExplicitCacheData explicitCache = _codecV4(start);
		CachedRecordInfo recordInfo = explicitCache.getRecordInfo(F1);
		Assert.assertNotNull(recordInfo);
		ExplicitCacheData.UserInfo userInfo = explicitCache.getUserInfo(MockKeys.K0);
		Assert.assertNotNull(userInfo);
		
		// Verify that serializing always gives the same result.
		byte[] serial1 = _serializeV4(explicitCache);
		byte[] serial2 = _serializeV4(explicitCache);
		Assert.assertArrayEquals(serial1, serial2);
	}

	@Test
	public void unknownPrefs() throws Throwable
	{
		// While the ExplicitCacheData is the big change in this version, the prefs are also different in that they permit unknown keys.
		// Create some real opcodes and some unknown ones.
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			writer.writeOpcode(new Opcode_SetPrefsInt(PrefsData.INT_VIDEO_EDGE, 5));
			writer.writeOpcode(new Opcode_SetPrefsInt("BOGO_INT", 6));
			writer.writeOpcode(new Opcode_SetPrefsLong("BOGO_LONG", 7L));
			writer.writeOpcode(new Opcode_SetPrefsLong(PrefsData.LONG_FOLLOW_CACHE_BYTES, 8L));
		}
		byte[] bytes = out.toByteArray();
		
		PrefsData prefs = PrefsData.defaultPrefs();
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes))
		{
			OpcodeContext context = new OpcodeContext(null, prefs, null, null, null);
			OpcodeCodec.decodeWholeStream(input, context);
		}
		Assert.assertEquals(5, prefs.videoEdgePixelMax);
		Assert.assertEquals(8L, prefs.followeeCacheTargetBytes);
	}


	private static byte[] _serializeV3(ExplicitCacheData start) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			start.serializeToOpcodeWriterV3(writer);
		}
		return out.toByteArray();
	}

	private static ExplicitCacheData _codecV3(ExplicitCacheData start) throws IOException
	{
		byte[] bytes = _serializeV3(start);
		ExplicitCacheData explicitCache = new ExplicitCacheData();
		OpcodeContextV3 context = new OpcodeContextV3(null, null, null, null, explicitCache, new ArrayList<>());
		try (ByteArrayInputStream input = new ByteArrayInputStream(bytes))
		{
			OpcodeCodec.decodeWholeStreamV3(input, context);
		}
		return explicitCache;
	}

	private static byte[] _serializeV4(ExplicitCacheData start) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			start.serializeToOpcodeWriter(writer);
		}
		return out.toByteArray();
	}

	private static ExplicitCacheData _codecV4(ExplicitCacheData start) throws IOException
	{
		byte[] bytes = _serializeV4(start);
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
		CachedRecordInfo info = new CachedRecordInfo(streamCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
		data.addStreamRecord(streamCid, info);
	}
}
