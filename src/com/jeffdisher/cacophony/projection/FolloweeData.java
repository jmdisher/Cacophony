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
import com.jeffdisher.cacophony.data.local.v4.Opcode_SkipFolloweeRecord;
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
		return new FolloweeData();
	}


	private final Map<IpfsKey, List<FollowingCacheElement>> _followeeElements;
	private final Map<IpfsKey, Set<IpfsFile>> _temporarilySkippedRecordsByFollowee;
	private final Map<IpfsKey, Set<IpfsFile>> _permanentlySkippedRecordsByFollowee;
	private final Map<IpfsKey, Map<IpfsFile, FollowingCacheElement>> _elementsForLookup;
	private final Map<IpfsKey, IpfsFile> _followeeLastIndices;
	private final Map<IpfsKey, Long> _followeeLastPollMillis;
	private final Map<IpfsKey, Long> _followeeLastSuccessMillis;
	// We keep track of the most recent fetch so that we can adjust the time of any updates to make tests more reliable.
	private long _mostRecentFetchMillis;

	// Only set in the cases of servers so it is bound late, but can only be bound once.
	private HandoffConnector<IpfsKey, TimePair> _followeeRefreshConnector;

	private FolloweeData()
	{
		_followeeElements = new HashMap<>();
		_temporarilySkippedRecordsByFollowee = new HashMap<>();
		_permanentlySkippedRecordsByFollowee = new HashMap<>();
		_elementsForLookup = new HashMap<>();
		_followeeLastIndices = new HashMap<>();
		_followeeLastPollMillis = new HashMap<>();
		_followeeLastSuccessMillis = new HashMap<>();
		_mostRecentFetchMillis = 0;
	}

	/**
	 * Serializes the data in V3 format.  This is only used for testing migration.
	 * 
	 * @param writer The opcode writer which will serialize the data.
	 * @throws IOException There was a problem while writing.
	 */
	public void serializeToOpcodeWriterV3(OpcodeCodec.Writer writer) throws IOException
	{
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> elt : _followeeElements.entrySet())
		{
			IpfsKey followee = elt.getKey();
			IpfsFile indexRoot = _followeeLastIndices.get(followee);
			Assert.assertTrue(null != indexRoot);
			long lastPollMillis = _followeeLastPollMillis.get(followee);
			writer.writeOpcode(new Opcode_SetFolloweeStateV3(followee, indexRoot, lastPollMillis));
			for (FollowingCacheElement record : elt.getValue())
			{
				writer.writeOpcode(new Opcode_AddFolloweeElementV3(followee, record.elementHash(), record.imageHash(), record.leafHash(), record.combinedSizeBytes()));
			}
		}
	}

	/**
	 * Serializes the data in the receiver into opcodes.
	 * 
	 * @param writer The writer which will serialize the produced opcodes.
	 * @throws IOException There was a problem while writing.
	 */
	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		// NOTE:  V4 uses stateful assumptions in the data stream.  It assumes that the initial followee state will be
		// set before any of the elements within it.
		for (Map.Entry<IpfsKey, List<FollowingCacheElement>> elt : _followeeElements.entrySet())
		{
			IpfsKey followee = elt.getKey();
			IpfsFile indexRoot = _followeeLastIndices.get(followee);
			Assert.assertTrue(null != indexRoot);
			long lastPollMillis = _followeeLastPollMillis.get(followee);
			long lastSuccessMillis = _followeeLastSuccessMillis.get(followee);
			// Write the followee state, first, to put us in the state where we can describe this followee.
			writer.writeOpcode(new Opcode_SetFolloweeState(followee, indexRoot, lastPollMillis, lastSuccessMillis));
			// Write any cached elements for this followee.
			for (FollowingCacheElement record : elt.getValue())
			{
				writer.writeOpcode(new Opcode_AddFolloweeElement(record.elementHash(), record.imageHash(), record.leafHash(), record.combinedSizeBytes()));
			}
			// Write any of the skipped records which would have been in this followee's list.
			for (IpfsFile recordCid : _temporarilySkippedRecordsByFollowee.get(followee))
			{
				writer.writeOpcode(new Opcode_SkipFolloweeRecord(recordCid, false));
			}
			for (IpfsFile recordCid : _permanentlySkippedRecordsByFollowee.get(followee))
			{
				writer.writeOpcode(new Opcode_SkipFolloweeRecord(recordCid, true));
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
		return _followeeLastPollMillis.get(publicKey);
	}

	@Override
	public IpfsKey getNextFolloweeToPoll()
	{
		// For this query, we won't worry about having a specialized projection but will just walk a list.
		IpfsKey followee = null;
		long oldestTime = Long.MAX_VALUE;
		for (Map.Entry<IpfsKey, Long> elt : _followeeLastPollMillis.entrySet())
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
		// Make sure that the element hasn't been added before.
		List<FollowingCacheElement> list = _followeeElements.get(followeeKey);
		for (FollowingCacheElement elt : list)
		{
			Assert.assertTrue(!element.elementHash().equals(elt.elementHash()));
		}
		
		// Make sure that this isn't being skipped (since we shouldn't have found it, in that case).
		Assert.assertTrue(!_temporarilySkippedRecordsByFollowee.get(followeeKey).contains(element.elementHash()));
		Assert.assertTrue(!_permanentlySkippedRecordsByFollowee.get(followeeKey).contains(element.elementHash()));
		
		// Add this to the relevant collections.
		list.add(element);
		_elementsForLookup.get(followeeKey).put(element.elementHash(), element);
	}

	/**
	 * Removes the element from the tracking for this followee.
	 * If the followee isn't already tracking this element, this method does nothing.
	 * Note that this can be used by elements which are cached, elements which were skipped (temporary or permanent), or
	 * elements with no special cache state.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param elementCid The CID of the StreamRecord to drop from the cache.
	 */
	public void removeElement(IpfsKey followeeKey, IpfsFile elementCid)
	{
		boolean didRemove = _followeeElements.get(followeeKey).removeIf((FollowingCacheElement elt) -> (elementCid.equals(elt.elementHash())));
		// Note that it is possible that we were asked to remove something which was never in the list.
		// (this is because we only record the elements which have images or leaves)
		if (didRemove)
		{
			FollowingCacheElement match = _elementsForLookup.get(followeeKey).remove(elementCid);
			Assert.assertTrue(null != match);
		}
		else
		{
			// This might have been a skipped element (permanent or temporary).
			if (_temporarilySkippedRecordsByFollowee.get(followeeKey).contains(elementCid))
			{
				boolean didRemoveSkipped = _temporarilySkippedRecordsByFollowee.get(followeeKey).remove(elementCid);
				Assert.assertTrue(didRemoveSkipped);
			}
			else if (_permanentlySkippedRecordsByFollowee.get(followeeKey).contains(elementCid))
			{
				boolean didRemoveSkipped = _permanentlySkippedRecordsByFollowee.get(followeeKey).remove(elementCid);
				Assert.assertTrue(didRemoveSkipped);
			}
			else
			{
				// Note that it may not be in any collection, yet still valid to remove, if the element was removed
				// before we tried to cache it or was removed while we were retrying a temporary failure.
			}
		}
	}

	/**
	 * Records that the given recordCid was skipped when synchronizing followeeKey.  The same helper is used for
	 * permanent and temporary skips.
	 * Note that a given recordCid can only be added once (the caller should know what has been skipped when
	 * synchronizing).
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param recordCid The CID of the record to skip.
	 * @param isPermanent True if this should be permanently skipped or false if it should be temporary so it can be
	 * retried, later.
	 */
	public void addSkippedRecord(IpfsKey followeeKey, IpfsFile recordCid, boolean isPermanent)
	{
		// This shouldn't already be in the collection.
		Assert.assertTrue(!_temporarilySkippedRecordsByFollowee.get(followeeKey).contains(recordCid));
		Assert.assertTrue(!_permanentlySkippedRecordsByFollowee.get(followeeKey).contains(recordCid));
		
		// Add it to the skipped records.
		if (isPermanent)
		{
			_permanentlySkippedRecordsByFollowee.get(followeeKey).add(recordCid);
		}
		else
		{
			_temporarilySkippedRecordsByFollowee.get(followeeKey).add(recordCid);
		}
	}

	/**
	 * Removes the given recordCID from the list of previously-skipped elements for followeeKey.  This is expected to
	 * only be called on the temporarily skipped records (permanently skipped records are removed with the normal
	 * removeElement() call).
	 * This call is specifically to be used in cases where a previously temporarily skipped element has been
	 * successfully fetched and caching decisions are being made.
	 * Note that the recordCid MUST be in the list of temporarily skipped elements.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param recordCid The CID of the record to remove from the temporarily skipped list.
	 */
	public void removeTemporarilySkippedRecord(IpfsKey followeeKey, IpfsFile recordCid)
	{
		Assert.assertTrue(_temporarilySkippedRecordsByFollowee.get(followeeKey).contains(recordCid));
		boolean didRemove = _temporarilySkippedRecordsByFollowee.get(followeeKey).remove(recordCid);
		Assert.assertTrue(didRemove);
	}

	@Override
	public Set<IpfsFile> getSkippedRecords(IpfsKey followeeKey, boolean temporaryOnly)
	{
		Set<IpfsFile> copy = new HashSet<>(_temporarilySkippedRecordsByFollowee.get(followeeKey));
		if (!temporaryOnly)
		{
			copy.addAll(_permanentlySkippedRecordsByFollowee.get(followeeKey));
		}
		return copy;
	}

	/**
	 * Creates a new followee record, internally.  This must be called before this followeeKey can be used in any other
	 * calls in this interface.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The initial StreamIndex CID.
	 * @param lastPollMillis The initial poll time (must be >= 0L).
	 * @param lastSuccessMillis The initial success time (must be >= 0L).
	 */
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis, long lastSuccessMillis)
	{
		List<FollowingCacheElement> match0 = _followeeElements.put(followeeKey, new ArrayList<>());
		Assert.assertTrue(null == match0);
		Set<IpfsFile> skipped0 = _temporarilySkippedRecordsByFollowee.put(followeeKey, new HashSet<>());
		Assert.assertTrue(null == skipped0);
		Set<IpfsFile> skipped1 = _permanentlySkippedRecordsByFollowee.put(followeeKey, new HashSet<>());
		Assert.assertTrue(null == skipped1);
		Map<IpfsFile, FollowingCacheElement> match1 = _elementsForLookup.put(followeeKey, new HashMap<>());
		Assert.assertTrue(null == match1);
		IpfsFile match2 = _followeeLastIndices.put(followeeKey, indexRoot);
		Assert.assertTrue(null == match2);
		Long match3 = _followeeLastPollMillis.put(followeeKey, lastPollMillis);
		Assert.assertTrue(null == match3);
		Long match4 = _followeeLastSuccessMillis.put(followeeKey, lastSuccessMillis);
		Assert.assertTrue(null == match4);
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.create(followeeKey, new TimePair(lastPollMillis, lastSuccessMillis));
		}
	}

	/**
	 * Updates an existing followee's record.  Assumes that the followee already exists.
	 * 
	 * @param followeeKey The public key of the followee.
	 * @param indexRoot The StreamIndex CID of the most recent refresh of the followee.
	 * @param lastPollMillis The current time.
	 * @param isSuccess True if the poll was successful in reading the followee data.
	 */
	public void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis, boolean isSuccess)
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
		Long match1 = _followeeLastPollMillis.put(followeeKey, pollMillisToSave);
		Assert.assertTrue(null != match1);
		if (isSuccess)
		{
			Long match2 = _followeeLastSuccessMillis.put(followeeKey, pollMillisToSave);
			Assert.assertTrue(null != match2);
		}
		
		if (null != _followeeRefreshConnector)
		{
			_followeeRefreshConnector.update(followeeKey, new TimePair(pollMillisToSave, _followeeLastSuccessMillis.get(followeeKey)));
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
		Set<IpfsFile> skipped0 = _temporarilySkippedRecordsByFollowee.remove(followeeKey);
		Assert.assertTrue(null != skipped0);
		Assert.assertTrue(skipped0.isEmpty());
		Set<IpfsFile> skipped1 = _permanentlySkippedRecordsByFollowee.remove(followeeKey);
		Assert.assertTrue(null != skipped1);
		Assert.assertTrue(skipped1.isEmpty());
		Map<IpfsFile, FollowingCacheElement> match1 = _elementsForLookup.remove(followeeKey);
		Assert.assertTrue(null != match1);
		Assert.assertTrue(match1.isEmpty());
		IpfsFile match2 = _followeeLastIndices.remove(followeeKey);
		Assert.assertTrue(null != match2);
		Long match3 = _followeeLastPollMillis.remove(followeeKey);
		Assert.assertTrue(null != match3);
		Long match4 = _followeeLastSuccessMillis.remove(followeeKey);
		Assert.assertTrue(null != match4);
		
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
	public void attachRefreshConnector(HandoffConnector<IpfsKey, TimePair> followeeRefreshConnector)
	{
		Assert.assertTrue(null == _followeeRefreshConnector);
		_followeeRefreshConnector = followeeRefreshConnector;
		
		for(Map.Entry<IpfsKey, Long> elt : _followeeLastPollMillis.entrySet())
		{
			IpfsKey key = elt.getKey();
			TimePair pair = new TimePair(elt.getValue(), _followeeLastSuccessMillis.get(key));
			_followeeRefreshConnector.create(key, pair);
		}
	}


	/**
	 * A simple tuple to describe polling times, as we want to communicate the last attempt and last success times.
	 */
	public static record TimePair(long pollMillis, long successMillis) {}
}
