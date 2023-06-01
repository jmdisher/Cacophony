package com.jeffdisher.cacophony.projection;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestPinCacheData
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});

	@Test
	public void checkBasics() throws Throwable
	{
		PinCacheData pinCache = PinCacheData.createEmpty();
		Assert.assertFalse(pinCache.isPinned(F1));
		pinCache.addRef(F1);
		Assert.assertTrue(pinCache.isPinned(F1));
		Assert.assertFalse(pinCache.isPinned(F2));
		
		// We used to serialize/deserialize here but PinCacheData is no longer written to disk.
		
		Assert.assertTrue(pinCache.isPinned(F1));
		Assert.assertFalse(pinCache.isPinned(F2));
		pinCache.addRef(F2);
		pinCache.addRef(F2);
		pinCache.delRef(F1);
		pinCache.delRef(F2);
		Assert.assertFalse(pinCache.isPinned(F1));
		Assert.assertTrue(pinCache.isPinned(F2));
	}

	@Test
	public void comparison() throws Throwable
	{
		PinCacheData pinCache1 = PinCacheData.createEmpty();
		PinCacheData pinCache2 = PinCacheData.createEmpty();
		Assert.assertNull(pinCache1.verifyMatch(pinCache2));
		pinCache1.addRef(F1);
		Assert.assertNotNull(pinCache1.verifyMatch(pinCache2));
		pinCache2.addRef(F1);
		Assert.assertNull(pinCache1.verifyMatch(pinCache2));
		pinCache1.addRef(F1);
		Assert.assertNotNull(pinCache1.verifyMatch(pinCache2));
	}
}
