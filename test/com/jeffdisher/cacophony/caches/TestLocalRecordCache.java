package com.jeffdisher.cacophony.caches;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestLocalRecordCache
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});
	public static final IpfsFile F3 = MockSingleNode.generateHash(new byte[] {3});

	@Test
	public void testEmpty() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		Assert.assertEquals(0, cache.getKeys().size());
		CachedRecordInfo elt = cache.get(F1);
		Assert.assertNull(elt);
	}

	@Test
	public void testMultiRef() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(F1, 0);
		cache.recordMetaDataPinned(F1, 0);
		Assert.assertEquals(1, cache.getKeys().size());
		CachedRecordInfo elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertNull(elt.videoCid());
		Assert.assertNull(elt.audioCid());
		cache.recordMetaDataReleased(F1);
		Assert.assertEquals(1, cache.getKeys().size());
		cache.recordMetaDataReleased(F1);
		Assert.assertEquals(0, cache.getKeys().size());
		elt = cache.get(F1);
		Assert.assertNull(elt);
	}

	@Test
	public void testLeaves() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(F1, 1);
		cache.recordAudioPinned(F1, F2);
		Assert.assertEquals(1, cache.getKeys().size());
		CachedRecordInfo elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertNull(elt.videoCid());
		Assert.assertNotNull(elt.audioCid());
		Assert.assertEquals(F2, elt.audioCid());
		cache.recordAudioReleased(F1, F2);
		elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertNull(elt.videoCid());
		Assert.assertNull(elt.audioCid());
		cache.recordMetaDataReleased(F1);
		elt = cache.get(F1);
		Assert.assertNull(elt);
	}

	@Test
	public void testMultipleVideos() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(F1, 2);
		cache.recordVideoPinned(F1, F2, 100);
		cache.recordVideoPinned(F1, F3, 200);
		Assert.assertEquals(1, cache.getKeys().size());
		CachedRecordInfo elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertEquals(F3, elt.videoCid());
		Assert.assertNull(elt.audioCid());
		cache.recordVideoReleased(F1, F3, 200);
		elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertEquals(F2, elt.videoCid());
		Assert.assertNull(elt.audioCid());
		cache.recordVideoReleased(F1, F2, 100);
		elt = cache.get(F1);
		Assert.assertNull(elt.thumbnailCid());
		Assert.assertNull(elt.videoCid());
		Assert.assertNull(elt.audioCid());
		cache.recordMetaDataReleased(F1);
		elt = cache.get(F1);
		Assert.assertNull(elt);
	}
}
