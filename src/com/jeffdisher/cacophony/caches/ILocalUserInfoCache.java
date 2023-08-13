package com.jeffdisher.cacophony.caches;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The read-only part of the LocalUserInfoCache interface.
 */
public interface ILocalUserInfoCache
{
	/**
	 * Looks up the user info for the given user key, returning null if not found.
	 * 
	 * @param userKey The key to look up.
	 * @return The most recently written cache element or null, if not found.
	 */
	public Element getUserInfo(IpfsKey userKey);


	/**
	 * The cache element.
	 */
	public static record Element(String name
			, String description
			, IpfsFile userPicCid
			, String emailOrNull
			, String websiteOrNull
			, IpfsFile featureOrNull
	)
	{
	}
}
