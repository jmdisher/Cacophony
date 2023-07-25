package com.jeffdisher.cacophony.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * This cache stores information about the posts observed from followees which are replies to home user posts.
 * All the methods are synchronized since the calls can come in from different threads in the system.
 */
public class HomeUserReplyCache
{
	private final HandoffConnector<IpfsFile, IpfsFile> _connector;
	private final Map<IpfsFile, Integer> _homeUserPosts;
	private final Map<IpfsFile, Integer> _followeeUserPosts;
	private final Set<IpfsFile> _followeePostsObserved;

	/**
	 * Creates the cache such that it notifies the given connector when new relationships are discovered or deleted.
	 * 
	 * @param connector The connector to notify of changes.
	 */
	public HomeUserReplyCache(HandoffConnector<IpfsFile, IpfsFile> connector)
	{
		_connector = connector;
		_homeUserPosts = new HashMap<>();
		_followeeUserPosts = new HashMap<>();
		_followeePostsObserved = new HashSet<>();
	}

	/**
	 * Records that a home user has created a post with the given CID.
	 * 
	 * @param cid The CID of a home user post.
	 */
	public synchronized void addHomePost(IpfsFile cid)
	{
		int refCount = _homeUserPosts.getOrDefault(cid, 0);
		_homeUserPosts.put(cid, refCount + 1);
	}

	/**
	 * Removes a home user post previously added.
	 * Note that this will not implicitly clear any followee replies already observed as that information is still
	 * potentially useful.
	 * 
	 * @param cid The CID of the home user post.
	 */
	public synchronized void removeHomePost(IpfsFile cid)
	{
		int refCount = _homeUserPosts.remove(cid);
		if (refCount > 1)
		{
			_homeUserPosts.put(cid, refCount - 1);
			// Note that we don't bother clearing all the replies to this post, since that observed context could still be useful.
		}
	}

	/**
	 * Records that a followee post replying to another post has been observed.  The replyTo MUST not be null but the
	 * reply may not be to a home user.  If it is to someone else, this post is recorded for balance with the removal
	 * but it will not result in any update to the connector.
	 * 
	 * @param cid The CID of the followee post.
	 * @param replyTo The CID of the post to which this post is a reply.
	 */
	public synchronized void addFolloweePost(IpfsFile cid, IpfsFile replyTo)
	{
		Assert.assertTrue(null != replyTo);
		int refCount = _followeeUserPosts.getOrDefault(cid, 0);
		_followeeUserPosts.put(cid, refCount + 1);
		if (0 == refCount)
		{
			// This is the first time we added this so see if this is a reply to something we are hosting and notify the connector.
			if (_homeUserPosts.containsKey(replyTo))
			{
				_followeePostsObserved.add(cid);
				_connector.create(cid, replyTo);
			}
		}
	}

	/**
	 * Removes a followee post previously added.
	 * 
	 * @param cid The CID of the followee post.
	 */
	public synchronized void removeFolloweePost(IpfsFile cid)
	{
		int refCount = _followeeUserPosts.remove(cid);
		if (refCount > 1)
		{
			_followeeUserPosts.put(cid, refCount - 1);
		}
		else
		{
			// There are no remaining posts so delete it if this is one we had previously communicated.
			if (_followeePostsObserved.contains(cid))
			{
				_followeePostsObserved.remove(cid);
				_connector.destroy(cid);
			}
		}
	}
}
