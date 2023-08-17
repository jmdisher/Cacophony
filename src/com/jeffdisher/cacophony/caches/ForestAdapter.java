package com.jeffdisher.cacophony.caches;

import java.util.HashSet;
import java.util.Set;

import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * Listeners attached to the ReplyForest need to listen on the set of posts reachable from whatever root is selected so
 * this adapter filters that information.
 * None of the methods here should be called from outside of the ReplyForest implementation.  To any other code, this
 * should be treated like an opaque token.
 * The intention is that this will be called for all updates in the ReplyForest, since each adapter could be monitoring
 * a large number of nodes in a sub-tree, but it will ignore anything updates which aren't related to that sub-tree.
 * Note that this implementation is NOT thread safe.
 */
public class ForestAdapter implements ReplyForest.IAdapterToken
{
	// The key is the child, the value is the parent (since deletes just reference the child).
	private final HandoffConnector<IpfsFile, IpfsFile> _connector;
	private final Set<IpfsFile> _parents;

	/**
	 * Creates a new adapter, starting with startingRoot as a known parent node.
	 * 
	 * @param connector The connector to notify when relevant relationships are added/removed.
	 * @param startingRoot The initial root of the tree to be monitored.
	 */
	public ForestAdapter(HandoffConnector<IpfsFile, IpfsFile> connector, IpfsFile startingRoot)
	{
		_connector = connector;
		_parents = new HashSet<>();
		_parents.add(startingRoot);
	}

	/**
	 * Adds a new parent-child relationship to the adapter.  Note that the update is only added, and notification sent
	 * to the connector, if the parent is already known to the adapter.  Otherwise, does nothing.
	 * 
	 * @param parent The parent.
	 * @param child The child.
	 */
	public void addChild(IpfsFile parent, IpfsFile child)
	{
		// If this parent is known to us, add the child as a new parent and notify the connector.
		if (_parents.contains(parent))
		{
			_parents.add(child);
			_connector.create(child, parent);
		}
	}

	/**
	 * Removes a parent-child relationship in the adapter.  Note that we only pass this on to the connector if the
	 * parent is already known to the adapter.  Otherwise, does nothing.
	 * 
	 * @param parent The parent.
	 * @param child The child.
	 */
	public void removeChild(IpfsFile parent, IpfsFile child)
	{
		// If this parent is known to us, notify the connector but leave the parent set untouched (as it may have children).
		if (_parents.contains(parent))
		{
			_connector.destroy(child);
		}
	}
}
