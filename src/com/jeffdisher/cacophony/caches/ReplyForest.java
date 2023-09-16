package com.jeffdisher.cacophony.caches;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Represents the collection of tree relationships between posts and their replies.
 * Note that this data structure is inherently inconsistent, as posts can be deleted or discovered in the wrong order.
 * This means that all relationships which are described by the data are assumed to be correct, from some point of view,
 * so they can never be fully destroyed.  That is, if a post is deleted, it can be disconnected from its parent, but
 * none of its replies can be disconnected from it.
 * This means that rendering the data is a little tricky in that such a broken link will not be observable from the
 * ultimate root from which all the replies descend, as those links can be destroyed or not found.
 * Hence, any rendering of the data is a sort of "best efforts, based on current knowledge", and it may look very
 * different at different times.
 * This gives rise to a key point about this class:  None of the mutative calls (adding or removing) are for actual data
 * roots, but only for posts which are relies to others.
 * The public interface is synchronized since it is a cache which could be populated from any part of the system.
 * Note that it is possible for a post to be observed multiple times (rebroadcasts, etc) so this structure must be
 * internally reference-counted such that it only notifies listeners on the first add or last remove.
 */
public class ReplyForest
{
	private final Map<IpfsFile, Set<IpfsFile>> _roots = new HashMap<>();
	private final Map<IpfsFile, Integer> _refCounts = new HashMap<>();
	private final IdentityHashMap<ForestAdapter, Void> _listeners = new IdentityHashMap<>();

	/**
	 * Adds a new relationship to the forest.  Neither parameter can be null.
	 * 
	 * @param post The new post discovered (the "child").
	 * @param parent The post to which this is a reply (the "parent").
	 */
	public synchronized void addPost(IpfsFile post, IpfsFile parent)
	{
		Assert.assertTrue(null != post);
		Assert.assertTrue(null != parent);
		
		// We should only change state if this is new.
		int refCount = 0;
		if (!_refCounts.containsKey(post))
		{
			Set<IpfsFile> children = _roots.get(parent);
			// This might not yet be known.
			if (null == children)
			{
				children = new HashSet<>();
				_roots.put(parent, children);
			}
			children.add(post);
			
			// Notify the listeners of this new post.
			for (ForestAdapter adapter : _listeners.keySet())
			{
				adapter.addChild(parent, post);
			}
		}
		else
		{
			refCount = _refCounts.get(post);
		}
		_refCounts.put(post, refCount + 1);
	}

	/**
	 * Removes an existing relationship from the forest.  Neither parameter can be null.
	 * 
	 * @param post The post being removed (the "child").
	 * @param parent The post to which this is a reply (the "parent").
	 */
	public synchronized void removePost(IpfsFile post, IpfsFile parent)
	{
		Assert.assertTrue(null != post);
		Assert.assertTrue(null != parent);
		
		// We should only change state if this was the last reference
		int refCount = _refCounts.get(post);
		if (1 == refCount)
		{
			Set<IpfsFile> children = _roots.get(parent);
			// We MUST see this since it was added, at some point.
			Assert.assertTrue(null != children);
			Assert.assertTrue(children.contains(post));
			children.remove(post);
			
			// Notify the listeners of this removed post.
			for (ForestAdapter adapter : _listeners.keySet())
			{
				adapter.removeChild(parent, post);
			}
			_refCounts.remove(post);
		}
		else
		{
			_refCounts.put(post, refCount - 1);
		}
	}

	/**
	 * Adds a connector for listening to updates to the structure rooted at the given root, returning an opaque token
	 * which is used to remove the listener.
	 * 
	 * @param connector The connector to notify of add/remove operations (the key is the "child" and the value is the
	 * "parent").
	 * @param root The root of the tree to monitor.
	 * @return An opaque token to pass back to removeListener().
	 */
	public synchronized IAdapterToken addListener(HandoffConnector<IpfsFile, IpfsFile> connector, IpfsFile root)
	{
		Assert.assertTrue(null != root);
		
		ForestAdapter adapter = new ForestAdapter(connector, root);
		_listeners.put(adapter, null);
		if (_roots.containsKey(root))
		{
			// This is the common case:  We know about the post so we know that this is a valid call.
			// Walk all the children of this root, recursively, and populate the adapter.
			_fillNewAdapter(adapter, root);
		}
		else
		{
			// This case happens when the user tries to listen to a post which we don't know about.  While this could
			// just be a bogus call, it is typically what happens when they view a post which is valid but doesn't yet
			// have any children.
			// In this case, we don't walk the roots but otherwise leave the listener installed.
		}
		return adapter;
	}

	/**
	 * Removes a connector previously added for listening to updates in the forest.
	 * 
	 * @param token The opaque token previously returned by addListener().
	 */
	public synchronized void removeListener(IAdapterToken token)
	{
		ForestAdapter adapter = (ForestAdapter)token;
		Assert.assertTrue(null != adapter);
		Assert.assertTrue(_listeners.containsKey(adapter));
		_listeners.remove(adapter);
	}


	private void _fillNewAdapter(ForestAdapter adapter, IpfsFile root)
	{
		// We will assume that the caller verified that this is a known root.
		Set<IpfsFile> children = _roots.get(root);
		for (IpfsFile child : children)
		{
			adapter.addChild(root, child);
			if (_roots.containsKey(child))
			{
				// We just fill this via a basic depth-first call as we are unlikely to blow our stack.
				// (We could make this breadth-first, in the future, if this becomes a problem but that requires an extra queue).
				_fillNewAdapter(adapter, child);
			}
		}
	}


	/**
	 * This is just so that we can return the ForestAdapter instance without callers assuming that they can use its
	 * interface.
	 */
	public interface IAdapterToken
	{
	}
}
