package com.jeffdisher.cacophony.testutils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Allows the collection of MockSingleNode elements to find each other.
 * NOTE:  This implementation assumes that the swarm is read-only once setup.  Additional locking would be required if
 * this were to be modified while running.
 */
public class MockSwarm
{
	private List<MockSingleNode> _nodes = new ArrayList<>();

	public void registerInSwarm(MockSingleNode node)
	{
		_nodes.add(node);
	}

	public List<MockSingleNode> getNodes()
	{
		return Collections.unmodifiableList(_nodes);
	}
}
