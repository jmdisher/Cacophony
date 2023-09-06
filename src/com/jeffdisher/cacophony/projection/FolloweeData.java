package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v3.Opcode_AddFolloweeElementV3;
import com.jeffdisher.cacophony.data.local.v3.Opcode_SetFolloweeStateV3;
import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_AddFolloweeElement;
import com.jeffdisher.cacophony.data.local.v4.Opcode_SetFolloweeState;
import com.jeffdisher.cacophony.logic.HandoffConnector;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A high-level projection of the followee data which also exposes a mutation interface.
 */
public class FolloweeData implements IFolloweeReading
{
	public static FolloweeData createEmpty()
	{
		Map<IpfsKey, List<FollowingCacheElement>> followeeElements = new HashMap<>();
		Map<IpfsKey, IpfsFile> followeeLastIndices = new HashMap<>();
		Map<IpfsKey, IpfsFile> followeeNextBackwardRecord = new HashMap<>();
		Map<IpfsKey, Long> followeeLastFetchMillis = new HashMap<>();
		return new FolloweeData(followeeElements, followeeLastIndices, followeeNextBackwardRecord, followeeLastFetchMillis);
	}


	private final Map<IpfsKey, List<FollowingCacheElement>> _followeeElements;
	private final Map<IpfsKey, Map<IpfsFile, FollowingCacheElement>> _elementsForLookup;
	private final Map<IpfsKey, IpfsFile> _followeeLastIndices;
	// We only store the entry in _followeeNextBackwardRecord if not null.
	private final Map<IpfsKey, IpfsFile> _followeeNextBackwardRecord;
	private final Map<IpfsKey, Long> _followeeLastFetchMillis;
	// We keep track of the most recent fetch so that we can adjust the time of any updates to make tests more reliable.
	private long _mostRecentFetchMillis;

	// Only set in the cases of servers so it is bound late, but can only be bound once.
	private HandoffConnector<IpfsKey, Long> _followeeRefreshConnector;

	private FolloweeData(Map<IpfsKey, List<FollowingCacheElement>> followeeElements, Map<IpfsKey, IpfsFile> followeeLastIndices, Map<IpfsKey, IpfsFile> followeeNextBackwardRecord, Map<IpfsKey, Long> followeeLastFetchMillis)
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
		_followeeNextBackwardRecord = new HashMap<>(followeeNextBackwardRecord);
		_followeeLastFetchMillis = new HashMap<>(followeeLastFetchMillis);
		_mostRecentFetchMillis = 0;
	}

	public void serializeToOpcodeWriterV3(OpcodeCodec.Writer writer) throws IOException
	{
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> elt : _followeeElements.entrySet())
		{
			IpfsKey followee = elt.getKey();
			IpfsFile indexRoot = _followeeLastIndices.get(followee);
			Assert.assertTrue(null != indexRoot);
			// V3 data cannot describe partially-loaded followees.
			Assert.assertTrue(!_followeeNextBackwardRecord.containsKey(followee));
			long lastPollMillis = _followeeLastFetchMillis.get(followee);
			writer.writeOpcode(new Opcode_SetFolloweeStateV3(followee, indexRoot, lastPollMillis));
			for (FollowingCacheElement record : elt.getValue())
			{
				writer.writeOpcode(new Opcode_AddFolloweeElementV3(followee, record.elementHash(), record.imageHash(), record.leafHash(), record.combinedSizeBytes()));
			}
		}
	}

	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		// NOTE:  V4 uses stateful assumptions in the data stream.  It assumes that the initial followee state will be
		// set before any of the elements within it.
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> elt : _followeeElements.entrySet())
		{
			IpfsKey followee = elt.getKey();
			IpfsFile indexRoot = _followeeLastIndices.get(followee);
			Assert.assertTrue(null != indexRoot);
			IpfsFile nextBackwardRecord = _followeeNextBackwardRecord.get(followee);
			long lastPollMillis = _followeeLastFetchMillis.get(followee);
			writer.writeOpcode(new Opcode_SetFolloweeState(followee, indexRoot, nextBackwardRecord, lastPollMillis));
			for (FollowingCacheElement record : elt.getValue())
			{
				writer.writeOpcode(new Opcode_AddFolloweeElement(record.elementHash(), record.imageHash(), record.leafHash(), record.combinedSizeBytes()));
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

	/**
	 * Adds a new element for the given followee.
	 * Asserts that an element with the same elementHash is not already in the cache for this followee.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param element The new element to track.
	 */
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

	/**
	 * Removes the element from the tracking for this followee.
	 * If the followee isn't already tracking this element, this method does nothing.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param elementCid The CID of the StreamRecord to drop from the cache.
	 */
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

	/**
	 * Creates a new followee record, internally.  This must be called before this followeeKey can be used in any other
	 * calls in this interface.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The initial StreamIndex CID.
	 * @param nextBackwardRecord The next record CID we need to fetch when loading backward (null if the stream is fully
	 * loaded).
	 * @param lastPollMillis The initial poll time (must be >= 0L).
	 */
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, IpfsFile nextBackwardRecord, long lastPollMillis)
	{
		List<FollowingCacheElement> match0 = _followeeElements.put(followeeKey, new ArrayList<>());
		Assert.assertTrue(null == match0);
		Map<IpfsFile, FollowingCacheElement> match1 = _elementsForLookup.put(followeeKey, new HashMap<>());
		Assert.assertTrue(null == match1);
		IpfsFile match2 = _followeeLastIndices.put(followeeKey, indexRoot);
		Assert.assertTrue(null == match2);
		// We don't have support for incremental synchronization, yet.
		Assert.assertTrue(null == nextBackwardRecord);
		Long match3 = _followeeLastFetchMillis.put(followeeKey, lastPollMillis);
		Assert.assertTrue(null == match3);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.create(followeeKey, lastPollMillis);
		}
	}

	/**
	 * Updates an existing followee's record.  Assumes that the followee already exists.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The StreamIndex CID of the most recent refresh of the followee.
	 * @param nextBackwardRecord The next record CID we need to fetch when loading backward (null if the stream is fully
	 * loaded).
	 * @param lastPollMillis The current time.
	 */
	public void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, IpfsFile nextBackwardRecord, long lastPollMillis)
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
		// We don't have support for incremental synchronization, yet.
		Assert.assertTrue(null == nextBackwardRecord);
		Long match1 = _followeeLastFetchMillis.put(followeeKey, pollMillisToSave);
		Assert.assertTrue(null != match1);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.update(followeeKey, pollMillisToSave);
		}
	}

	/**
	 * Removes a given followee entirely from tracking.  Note that this call assumes there are no elements associated
	 * with this followee and that the record does already exist.
	 * 
	 * @param followeeKey The public key of the followee.
	 */
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

	/**
	 * Attaches the listener for followee refresh data updates.  This can be called at most once for any given instance.
	 * Upon being called, the given followeeRefreshConnector will be populated with the current state of followee data
	 * and will then be notified of refresh times whenever a followee is added/removed/refreshed.
	 * 
	 * @param followeeRefreshConnector The connector to notify of followee refreshes.
	 */
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
