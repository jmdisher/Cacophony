package com.jeffdisher.cacophony.data.local.v1;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public class TestLocalRecordCache
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");
	public static final IpfsFile F3 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCC");
	private static final IpfsKey K1 = IpfsKey.fromPublicKey("z5AanNVJCxnSSsLjo4tuHNWSmYs3TXBgKWxVqdyNFgwb1br5PBWo14F");

	@Test
	public void testEmpty() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		Assert.assertEquals(0, cache.getKeys().size());
		LocalRecordCache.Element elt = cache.get(F1);
		Assert.assertNull(elt);
	}

	@Test
	public void testMultiRef() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(F1, "name", "description", 1L, null, K1.toPublicKey(), 0);
		cache.recordMetaDataPinned(F1, "name", "description", 1L, null, K1.toPublicKey(), 0);
		Assert.assertEquals(1, cache.getKeys().size());
		LocalRecordCache.Element elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertTrue(elt.isCached());
		Assert.assertNull(elt.videoCid());
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
		cache.recordMetaDataPinned(F1, "name", "description", 1L, null, K1.toPublicKey(), 1);
		cache.recordAudioPinned(F1, F2);
		Assert.assertEquals(1, cache.getKeys().size());
		LocalRecordCache.Element elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertTrue(elt.isCached());
		Assert.assertNull(elt.videoCid());
		Assert.assertEquals(F2, elt.audioCid());
		cache.recordAudioReleased(F1, F2);
		elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertFalse(elt.isCached());
		Assert.assertNull(elt.audioCid());
		cache.recordMetaDataReleased(F1);
		elt = cache.get(F1);
		Assert.assertNull(elt);
	}

	@Test
	public void testMultipleVideos() throws Throwable
	{
		LocalRecordCache cache = new LocalRecordCache();
		cache.recordMetaDataPinned(F1, "name", "description", 1L, null, K1.toPublicKey(), 2);
		cache.recordVideoPinned(F1, F2, 100);
		cache.recordVideoPinned(F1, F3, 200);
		Assert.assertEquals(1, cache.getKeys().size());
		LocalRecordCache.Element elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertTrue(elt.isCached());
		Assert.assertEquals(F3, elt.videoCid());
		cache.recordVideoReleased(F1, F3, 200);
		elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertTrue(elt.isCached());
		Assert.assertEquals(F2, elt.videoCid());
		cache.recordVideoReleased(F1, F2, 100);
		elt = cache.get(F1);
		Assert.assertEquals("name", elt.name());
		Assert.assertFalse(elt.isCached());
		Assert.assertNull(elt.videoCid());
		cache.recordMetaDataReleased(F1);
		elt = cache.get(F1);
		Assert.assertNull(elt);
	}
}
