package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.data.local.v3.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v3.Opcode_AddFolloweeElement;
import com.jeffdisher.cacophony.data.local.v3.Opcode_SetFolloweeState;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A high-level projection of the followee data which also exposes a mutation interface.
 */
public class FolloweeData implements IFolloweeWriting
{
	public static FolloweeData createEmpty()
	{
		Map<IpfsKey, List<FollowingCacheElement>> followeeElements = new HashMap<>();
		Map<IpfsKey, IpfsFile> followeeLastIndices = new HashMap<>();
		Map<IpfsKey, Long> followeeLastFetchMillis = new HashMap<>();
		return new FolloweeData(followeeElements, followeeLastIndices, followeeLastFetchMillis);
	}


	private final Map<IpfsKey, List<FollowingCacheElement>> _followeeElements;
	private final Map<IpfsKey, Map<IpfsFile, FollowingCacheElement>> _elementsForLookup;
	private final Map<IpfsKey, IpfsFile> _followeeLastIndices;
	private final Map<IpfsKey, Long> _followeeLastFetchMillis;
	// We keep track of the most recent fetch so that we can adjust the time of any updates to make tests more reliable.
	private long _mostRecentFetchMillis;

	// Only set in the cases of servers so it is bound late, but can only be bound once.
	private HandoffConnector<IpfsKey, Long> _followeeRefreshConnector;

	private FolloweeData(Map<IpfsKey, List<FollowingCacheElement>> followeeElements, Map<IpfsKey, IpfsFile> followeeLastIndices, Map<IpfsKey, Long> followeeLastFetchMillis)
	{
		_followeeElements = new HashMap<>();
		_elementsForLookup = new HashMap<>();
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> entry : followeeElements.entrySet())
		{
			IpfsKey key = entry.getKey();
			List<FollowingCacheElement> list = new ArrayList<>();
			Map<IpfsFile, FollowingCacheElement> map = new HashMap<>();
			for (FollowingCacheElement elt : entry.getValue())
			{
				list.add(elt);
				map.put(elt.elementHash(), elt);
			}
			_followeeElements.put(key, list);
			_elementsForLookup.put(key, map);
		}
		_followeeLastIndices = new HashMap<>(followeeLastIndices);
		_followeeLastFetchMillis = new HashMap<>(followeeLastFetchMillis);
		_mostRecentFetchMillis = 0;
	}

	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> elt : _followeeElements.entrySet())
		{
			IpfsKey followee = elt.getKey();
			IpfsFile indexRoot = _followeeLastIndices.get(followee);
			Assert.assertTrue(null != indexRoot);
			long lastPollMillis = _followeeLastFetchMillis.get(followee);
			writer.writeOpcode(new Opcode_SetFolloweeState(followee, indexRoot, lastPollMillis));
			for (FollowingCacheElement record : elt.getValue())
			{
				writer.writeOpcode(new Opcode_AddFolloweeElement(followee, record.elementHash(), record.imageHash(), record.leafHash(), record.combinedSizeBytes()));
			}
		}
	}

	@Override
	public Set<IpfsKey> getAllKnownFollowees()
	{
		return new HashSet<>(_followeeElements.keySet());
	}

	@Override
	public Map<IpfsFile, FollowingCacheElement> snapshotAllElementsForFollowee(IpfsKey publicKey)
	{
		Map<IpfsFile, FollowingCacheElement> map = _elementsForLookup.get(publicKey);
		return (null != map)
				? Map.copyOf(map)
				: null
		;
	}

	@Override
	public IpfsFile getLastFetchedRootForFollowee(IpfsKey publicKey)
	{
		return _followeeLastIndices.get(publicKey);
	}

	@Override
	public long getLastPollMillisForFollowee(IpfsKey publicKey)
	{
		return _followeeLastFetchMillis.get(publicKey);
	}

	@Override
	public IpfsKey getNextFolloweeToPoll()
	{
		// For this query, we won't worry about having a specialized projection but will just walk a list.
		IpfsKey followee = null;
		long oldestTime = Long.MAX_VALUE;
		for (Map.Entry<IpfsKey, Long> elt : _followeeLastFetchMillis.entrySet())
		{
			long thisTime = elt.getValue();
			if (thisTime < oldestTime)
			{
				followee = elt.getKey();
				oldestTime = thisTime;
			}
		}
		return followee;
	}

	@Override
	public void addElement(IpfsKey followeeKey, FollowingCacheElement element)
	{
		// First, make sure that the element hasn't been added before.
		List<FollowingCacheElement> list = _followeeElements.get(followeeKey);
		for (FollowingCacheElement elt : list)
		{
			Assert.assertTrue(!element.elementHash().equals(elt.elementHash()));
		}
		list.add(element);
		_elementsForLookup.get(followeeKey).put(element.elementHash(), element);
	}

	@Override
	public void removeElement(IpfsKey followeeKey, IpfsFile elementCid)
	{
		boolean didRemove = _followeeElements.get(followeeKey).removeIf((FollowingCacheElement elt) -> (elementCid.equals(elt.elementHash())));
		// Note that it is possible that we were asked to remove something which was never in the list.
		if (didRemove)
		{
			FollowingCacheElement match = _elementsForLookup.get(followeeKey).remove(elementCid);
			Assert.assertTrue(null != match);
		}
	}

	@Override
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		List<FollowingCacheElement> match0 = _followeeElements.put(followeeKey, new ArrayList<>());
		Assert.assertTrue(null == match0);
		Map<IpfsFile, FollowingCacheElement> match1 = _elementsForLookup.put(followeeKey, new HashMap<>());
		Assert.assertTrue(null == match1);
		IpfsFile match2 = _followeeLastIndices.put(followeeKey, indexRoot);
		Assert.assertTrue(null == match2);
		Long match3 = _followeeLastFetchMillis.put(followeeKey, lastPollMillis);
		Assert.assertTrue(null == match3);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.create(followeeKey, lastPollMillis);
		}
	}

	@Override
	public void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		// We expect that any actual update uses a non-zero time (since that is effectively the "never updated" value).
		Assert.assertTrue(lastPollMillis > 0L);
		// Note that some tests run quickly enough that the lastPollMillis can overlap.  This is harmless but does make tests flaky so we ensure this is always added at the end.
		long pollMillisToSave = lastPollMillis;
		if (_mostRecentFetchMillis >= pollMillisToSave)
		{
			pollMillisToSave = _mostRecentFetchMillis + 1L;
		}
		_mostRecentFetchMillis = pollMillisToSave;
		IpfsFile match0 = _followeeLastIndices.put(followeeKey, indexRoot);
		Assert.assertTrue(null != match0);
		Long match1 = _followeeLastFetchMillis.put(followeeKey, pollMillisToSave);
		Assert.assertTrue(null != match1);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.update(followeeKey, pollMillisToSave);
		}
	}

	@Override
	public void removeFollowee(IpfsKey followeeKey)
	{
		List<FollowingCacheElement> match0 = _followeeElements.remove(followeeKey);
		Assert.assertTrue(null != match0);
		Assert.assertTrue(match0.isEmpty());
		Map<IpfsFile, FollowingCacheElement> match1 = _elementsForLookup.remove(followeeKey);
		Assert.assertTrue(null != match1);
		Assert.assertTrue(match1.isEmpty());
		IpfsFile match2 = _followeeLastIndices.remove(followeeKey);
		Assert.assertTrue(null != match2);
		Long match3 = _followeeLastFetchMillis.remove(followeeKey);
		Assert.assertTrue(null != match3);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.destroy(followeeKey);
		}
	}

	@Override
	public void attachRefreshConnector(HandoffConnector<IpfsKey, Long> followeeRefreshConnector)
	{
		Assert.assertTrue(null == _followeeRefreshConnector);
		_followeeRefreshConnector = followeeRefreshConnector;
		
		for(Map.Entry<IpfsKey, Long> elt : _followeeLastFetchMillis.entrySet())
		{
			_followeeRefreshConnector.create(elt.getKey(), elt.getValue());
		}
	}
}
