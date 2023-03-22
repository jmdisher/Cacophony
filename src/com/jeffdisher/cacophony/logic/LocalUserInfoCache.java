package com.jeffdisher.cacophony.logic;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This contains the basic user info (StreamDescription, roughly) of users known to the system.  It is a dynamically-
 * built ephemeral cache, typically containing the local user and followees, but could contain other data, in the
 * future.
 * The cache is incrementally updated as the system runs and all access is synchronized, since reads and writes can come
 * from different threads in the system, at any time.
 */
public class LocalUserInfoCache
{
	private final Map<IpfsKey, Element> _cache;

	/**
	 * Creates an empty cache.
	 */
	public LocalUserInfoCache()
	{
		_cache = new HashMap<>();
	}

	/**
	 * Looks up the user info for the given user key, returning null if not found.
	 * 
	 * @param userKey The key to look up.
	 * @return The most recently written cache element or null, if not found.
	 */
	public synchronized Element getUserInfo(IpfsKey userKey)
	{
		return _cache.get(userKey);
	}

	/**
	 * Replaces the value in the cache (whether or not it exists) with a new entry with this data.
	 * 
	 * @param userKey The user to update/add.
	 * @param name The user's name.
	 * @param description The user's description.
	 * @param userPicCid The user's picture CID.
	 * @param emailOrNull The user's E-Mail address (could be null).
	 * @param websiteOrNull The user's website (could be null).
	 */
	public synchronized void setUserInfo(IpfsKey userKey
			, String name
			, String description
			, IpfsFile userPicCid
			, String emailOrNull
			, String websiteOrNull
	)
	{
		Assert.assertTrue(null != userKey);
		Assert.assertTrue(null != name);
		Assert.assertTrue(null != description);
		Assert.assertTrue(null != userPicCid);
		
		_cache.put(userKey, new Element(name
				, description
				, userPicCid
				, emailOrNull
				, websiteOrNull
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


	/**
	 * The cache element.
	 */
	public static record Element(String name
			, String description
			, IpfsFile userPicCid
			, String emailOrNull
			, String websiteOrNull
	)
	{
	}
}