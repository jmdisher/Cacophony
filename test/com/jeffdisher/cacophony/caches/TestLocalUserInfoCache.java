package com.jeffdisher.cacophony.caches;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.testutils.MockSingleNode;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestLocalUserInfoCache
{
	public static final IpfsFile F1 = MockSingleNode.generateHash(new byte[] {1});
	public static final IpfsFile F2 = MockSingleNode.generateHash(new byte[] {2});

	@Test
	public void testEmpty() throws Throwable
	{
		LocalUserInfoCache cache = new LocalUserInfoCache();
		ILocalUserInfoCache.Element elt = cache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
	}

	@Test
	public void testBasicRead() throws Throwable
	{
		LocalUserInfoCache cache = new LocalUserInfoCache();
		cache.setUserInfo(MockKeys.K1, "name", "description", F1, null, null, null);
		ILocalUserInfoCache.Element elt = cache.getUserInfo(MockKeys.K1);
		Assert.assertEquals("name", elt.name());
		Assert.assertEquals("description", elt.description());
		Assert.assertEquals(F1, elt.userPicCid());
		Assert.assertNull(elt.emailOrNull());
		Assert.assertNull(elt.websiteOrNull());
	}

	@Test
	public void testReadAfterUpdate() throws Throwable
	{
		LocalUserInfoCache cache = new LocalUserInfoCache();
		cache.setUserInfo(MockKeys.K1, "name", "description", F1, null, null, null);
		ILocalUserInfoCache.Element elt1 = cache.getUserInfo(MockKeys.K1);
		cache.setUserInfo(MockKeys.K1, "name2", "description2", F2, "email", "site", null);
		ILocalUserInfoCache.Element elt2 = cache.getUserInfo(MockKeys.K1);
		
		Assert.assertEquals("name", elt1.name());
		Assert.assertEquals("description", elt1.description());
		Assert.assertEquals(F1, elt1.userPicCid());
		Assert.assertNull(elt1.emailOrNull());
		Assert.assertNull(elt1.websiteOrNull());
		
		Assert.assertEquals("name2", elt2.name());
		Assert.assertEquals("description2", elt2.description());
		Assert.assertEquals(F2, elt2.userPicCid());
		Assert.assertEquals("email", elt2.emailOrNull());
		Assert.assertEquals("site", elt2.websiteOrNull());
	}
}
