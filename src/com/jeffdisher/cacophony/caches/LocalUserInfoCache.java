package com.jeffdisher.cacophony.caches;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This contains the basic user info (StreamDescription, roughly) of users known to the system.  It is a dynamically-
 * built ephemeral cache, typically containing the local user and followees, but could contain other data, in the
 * future.
 * The cache is incrementally updated as the system runs and all access is synchronized, since reads and writes can come
 * from different threads in the system, at any time.
 */
public class LocalUserInfoCache implements ILocalUserInfoCache
{
	private final Map<IpfsKey, Element> _cache;

	/**
	 * Creates an empty cache.
	 */
	public LocalUserInfoCache()
	{
		_cache = new HashMap<>();
	}

	@Override
	public synchronized Element getUserInfo(IpfsKey userKey)
	{
		return _cache.get(userKey);
	}

	/**
	 * Replaces the value in the cache (whether or not it exists) with a new entry with this data.
	 * 
	 * @param userKey The user to update/add.
	 * @param description The user's description.
	 */
	public synchronized void setUserInfo(IpfsKey userKey
			, AbstractDescription description
	)
	{
		Assert.assertTrue(null != userKey);
		Assert.assertTrue(null != description);
		
		_cache.put(userKey, new Element(description.getName()
				, description.getDescription()
				, description.getPicCid()
				, description.getEmail()
				, description.getWebsite()
				, description.getFeature()
		));
	}

	/**
	 * Removes the user.  Has no effect if the user is not in the cache.
	 * 
	 * @param userKey The user to remove from the cache.
	 */
	public synchronized void removeUser(IpfsKey userKey)
	{
		_cache.remove(userKey);
	}
}
