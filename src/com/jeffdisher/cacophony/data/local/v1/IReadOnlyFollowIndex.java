package com.jeffdisher.cacophony.data.local.v1;

import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * This is the minimal read-only interface to the FollowIndex, provided in order to formalize access to it, somewhat, as
 * there are many callers which only want basic read-only functionality.
 */
public interface IReadOnlyFollowIndex extends Iterable<FollowRecord>
{
	/**
	 * Looks up the FollowRecord for the given public key and returns a reference to it WITHOUT removing it from the index.
	 * If we aren't following them, this returns null.
	 * 
	 * @param publicKey The key to stop following.
	 * @return The last state of the cache, null if not being followed.
	 */
	FollowRecord peekRecord(IpfsKey publicKey);
}
