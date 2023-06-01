package com.jeffdisher.cacophony.logic;

import org.junit.Assert;
import org.junit.Test;

import com.jeffdisher.cacophony.testutils.MockKeys;
import com.jeffdisher.cacophony.types.IpfsFile;


public class TestLocalUserInfoCache
{
	public static final IpfsFile F1 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeKG");
	public static final IpfsFile F2 = IpfsFile.fromIpfsCid("QmTaodmZ3CBozbB9ikaQNQFGhxp9YWze8Q8N8XnryCCeCG");

	@Test
	public void testEmpty() throws Throwable
	{
		LocalUserInfoCache cache = new LocalUserInfoCache();
		LocalUserInfoCache.Element elt = cache.getUserInfo(MockKeys.K1);
		Assert.assertNull(elt);
	}

	@Test
	public void testBasicRead() throws Throwable
	{
		LocalUserInfoCache cache = new LocalUserInfoCache();
		cache.setUserInfo(MockKeys.K1, "name", "description", F1, null, null);
		LocalUserInfoCache.Element elt = cache.getUserInfo(MockKeys.K1);
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
		cache.setUserInfo(MockKeys.K1, "name", "description", F1, null, null);
		LocalUserInfoCache.Element elt1 = cache.getUserInfo(MockKeys.K1);
		cache.setUserInfo(MockKeys.K1, "name2", "description2", F2, "email", "site");
		LocalUserInfoCache.Element elt2 = cache.getUserInfo(MockKeys.K1);
		
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
