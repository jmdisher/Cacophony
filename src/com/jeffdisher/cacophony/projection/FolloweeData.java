package com.jeffdisher.cacophony.projection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A high-level projection of the followee data which also exposes a mutation interface.
 */
public class FolloweeData implements IFolloweeWriting
{
	/**
	 * Creates the data on top of the given followIndex, writing back its changes there.
	 * 
	 * @param followIndex The FollowIndex we will use as the backing store.
	 * @return The new instance.
	 */
	public static FolloweeData buildOnIndex(FollowIndex followIndex)
	{
		// We want to tear apart this data structure and pass the relevant parts into the new instance.
		Map<IpfsKey, List<FollowingCacheElement>> followeeElements = new HashMap<>();
		Map<IpfsKey, IpfsFile> followeeLastIndices = new HashMap<>();
		Map<IpfsKey, Long> followeeLastFetchMillis = new HashMap<>();
		for (FollowRecord record : followIndex)
		{
			IpfsKey publicKey = record.publicKey();
			List<FollowingCacheElement> elements = Stream.of(record.elements())
					.collect(Collectors.toList())
			;
			followeeElements.put(publicKey, elements);
			followeeLastIndices.put(publicKey, record.lastFetchedRoot());
			followeeLastFetchMillis.put(publicKey, record.lastPollMillis());
		}
		
		return new FolloweeData(followeeElements, followeeLastIndices, followeeLastFetchMillis);
	}


	private final Map<IpfsKey, List<FollowingCacheElement>> _followeeElements;
	private final Map<IpfsKey, Map<IpfsFile, FollowingCacheElement>> _elementsForLookup;
	private final Map<IpfsKey, IpfsFile> _followeeLastIndices;
	private final Map<IpfsKey, Long> _followeeLastFetchMillis;

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
	}

	public FollowIndex serializeToIndex()
	{
		// Note that the index entries are technically supposed to be sorted such that the next followee to poll is first.
		List<IpfsKey> sortedKeys = _followeeLastFetchMillis.entrySet().stream()
			.sorted((Map.Entry<IpfsKey, Long> elt1, Map.Entry<IpfsKey, Long> elt2) -> {
				long one = elt1.getValue();
				long two = elt2.getValue();
				return (one > two)
						? 1
						: (one < two)
							? -1
							: 0
				;
			})
			.map((Map.Entry<IpfsKey, Long> elt) -> elt.getKey())
			.collect(Collectors.toList())
		;
		
		FollowIndex index = FollowIndex.emptyFollowIndex();
		for (IpfsKey oneKey : sortedKeys)
		{
			FollowingCacheElement[] elements = _followeeElements.get(oneKey).toArray((int size) -> new FollowingCacheElement[size]);
			FollowRecord record = new FollowRecord(oneKey, _followeeLastIndices.get(oneKey), _followeeLastFetchMillis.get(oneKey), elements);
			index.checkinRecord(record);
		}
		return index;
	}

	@Override
	public Set<IpfsKey> getAllKnownFollowees()
	{
		return new HashSet<>(_followeeElements.keySet());
	}

	@Override
	public FollowingCacheElement getElementForFollowee(IpfsKey publicKey, IpfsFile cid)
	{
		return _elementsForLookup.get(publicKey).get(cid);
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
	public List<IpfsFile> getElementsForFollowee(IpfsKey publicKey)
	{
		return _followeeElements.get(publicKey).stream()
				.map((FollowingCacheElement elt) -> elt.elementHash())
				.collect(Collectors.toList())
		;
	}

	@Override
	public IpfsKey getNextFolloweeToPoll()
	{
		// For this query, we won't worry about having a specialized projection but will just walk a list.
		IpfsKey followee = null;
		long oldestTime = Long.MAX_VALUE;
		for (Map.Entry<IpfsKey, Long> elt : _followeeLastFetchMillis.entrySet())
		{
			if (elt.getValue() < oldestTime)
			{
				followee = elt.getKey();
				oldestTime = elt.getValue();
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
	}

	@Override
	public void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		IpfsFile match0 = _followeeLastIndices.put(followeeKey, indexRoot);
		Assert.assertTrue(null != match0);
		Long match1 = _followeeLastFetchMillis.put(followeeKey, lastPollMillis);
		Assert.assertTrue(null != match1);
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
	}
}
