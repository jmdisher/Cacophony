package com.jeffdisher.cacophony.projection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.OpcodeContext;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestFavouritesCacheData
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});
	public static final IpfsFile F4 = MockSingleNode.generateHash(new byte[] {4});
	public static final IpfsFile F5 = MockSingleNode.generateHash(new byte[] {5});

	@Test
	public void empty() throws Throwable
	{
		FavouritesCacheData start = new FavouritesCacheData();
		FavouritesCacheData favourites = _codec(start);
		Assert.assertNotNull(favourites);
	}

	@Test
	public void basicUsage() throws Throwable
	{
		FavouritesCacheData start = new FavouritesCacheData();
		// These are overlapping in ways we would never normally see but the cache doesn't care.
		// The only CID it cares about is the first one - the location of the element it is describing.
		addStreamRecord(start, F1, F2, null, null, 5L);
		FavouritesCacheData favourites = _codec(start);
		Assert.assertEquals(1, favourites.getRecordFiles().size());
		CachedRecordInfo recordInfo = favourites.getRecordInfo(F1);
		Assert.assertEquals(F2, recordInfo.thumbnailCid());
		Assert.assertNull(recordInfo.videoCid());
		Assert.assertNull(recordInfo.audioCid());
		CachedRecordInfo removed = favourites.removeStreamRecord(F1);
		Assert.assertEquals(F1, removed.streamCid());
		Assert.assertEquals(0, favourites.getRecordFiles().size());
		Assert.assertNull(favourites.removeStreamRecord(F1));
	}

	@Test
	public void pinCounts() throws Throwable
	{
		FavouritesCacheData start = new FavouritesCacheData();
		addStreamRecord(start, F1, F2, null, null, 5L);
		addStreamRecord(start, F2, F2, F3, null, 20L);
		addStreamRecord(start, F3, null, null, null, 1L);
		addStreamRecord(start, F4, F2, null, null, 5L);
		addStreamRecord(start, F5, F2, F4, null, 25L);
		FavouritesCacheData favourites = _codec(start);
		Assert.assertEquals(5, favourites.getRecordFiles().size());
		
		// Test the expected pin counts.
		Map<IpfsFile, Integer> pinCount = new HashMap<>();
		favourites.walkAllPins((IpfsFile pin) -> {
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
	public void walkInOrder() throws Throwable
	{
		FavouritesCacheData start = new FavouritesCacheData();
		addStreamRecord(start, F1, F2, null, null, 5L);
		addStreamRecord(start, F2, F2, F3, null, 20L);
		addStreamRecord(start, F3, null, null, null, 1L);
		addStreamRecord(start, F4, F2, null, null, 5L);
		addStreamRecord(start, F5, F2, F4, null, 25L);
		FavouritesCacheData favourites = _codec(start);
		favourites.removeStreamRecord(F3);
		
		Iterator<IpfsFile> walker = favourites.getRecordFiles().iterator();
		Assert.assertEquals(F1, walker.next());
		Assert.assertEquals(F2, walker.next());
		Assert.assertEquals(F4, walker.next());
		Assert.assertEquals(F5, walker.next());
		Assert.assertFalse(walker.hasNext());
	}

	@Test
	public void checkSize() throws Throwable
	{
		FavouritesCacheData start = new FavouritesCacheData();
		addStreamRecord(start, F1, F2, null, null, 5L);
		addStreamRecord(start, F2, F2, F3, null, 20L);
		addStreamRecord(start, F3, null, null, null, 1L);
		addStreamRecord(start, F4, F2, null, null, 5L);
		addStreamRecord(start, F5, F2, F4, null, 25L);
		Assert.assertEquals(5L + 20L + 1L + 5L + 25L, start.getFavouritesSizeBytes());
		FavouritesCacheData favourites = _codec(start);
		Assert.assertEquals(5L + 20L + 1L + 5L + 25L, favourites.getFavouritesSizeBytes());
		favourites.removeStreamRecord(F3);
		Assert.assertEquals(5L + 20L + 5L + 25L, favourites.getFavouritesSizeBytes());
	}


	private static FavouritesCacheData _codec(FavouritesCacheData start) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (OpcodeCodec.Writer writer = OpcodeCodec.createOutputWriter(out))
		{
			start.serializeToOpcodeWriter(writer);
		}
		
		FavouritesCacheData favourites = new FavouritesCacheData();
		OpcodeContext context = new OpcodeContext(null, null, null, favourites, null);
		try (ByteArrayInputStream input = new ByteArrayInputStream(out.toByteArray()))
		{
			OpcodeCodec.decodeWholeStream(input, context);
		}
		return favourites;
	}

	private static void addStreamRecord(FavouritesCacheData data, IpfsFile streamCid, IpfsFile thumbnailCid, IpfsFile videoCid, IpfsFile audioCid, long combinedSizeBytes)
	{
		CachedRecordInfo info = new CachedRecordInfo(streamCid, false, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
		data.addStreamRecord(streamCid, info);
	}
}
