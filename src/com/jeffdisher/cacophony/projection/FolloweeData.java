package com.jeffdisher.cacophony.projection;

import java.util.HashSet;
import java.util.List;
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
		return new FolloweeData(followIndex);
	}


	// For now, we have only the FollowIndex backing store but an event stream will be added later.
	private final FollowIndex _backingStore;

	private FolloweeData(FollowIndex followIndex)
	{
		_backingStore = followIndex;
	}

	@Override
	public Set<IpfsKey> getAllKnownFollowees()
	{
		Set<IpfsKey> keys = new HashSet<>();
		for (FollowRecord record : _backingStore)
		{
			keys.add(record.publicKey());
		}
		return keys;
	}

	@Override
	public FollowingCacheElement getElementForFollowee(IpfsKey publicKey, IpfsFile cid)
	{
		FollowingCacheElement match = null;
		for (FollowingCacheElement elt : _backingStore.peekRecord(publicKey).elements())
		{
			if (cid.equals(elt.elementHash()))
			{
				// We will verify only one match here, for now.
				Assert.assertTrue(null == match);
				match = elt;
			}
		}
		return match;
	}

	@Override
	public IpfsFile getLastFetchedRootForFollowee(IpfsKey publicKey)
	{
		FollowRecord record = _backingStore.peekRecord(publicKey);
		return (null != record)
				? record.lastFetchedRoot()
				: null
		;
	}

	@Override
	public long getLastPollMillisForFollowee(IpfsKey publicKey)
	{
		FollowRecord record = _backingStore.peekRecord(publicKey);
		// This call should never be made when there is no match.
		Assert.assertTrue(null != record);
		return record.lastPollMillis();
	}

	@Override
	public List<IpfsFile> getElementsForFollowee(IpfsKey publicKey)
	{
		return Stream.of(_backingStore.peekRecord(publicKey).elements())
				.map((FollowingCacheElement elt) -> elt.elementHash())
				.collect(Collectors.toList())
		;
	}

	@Override
	public IpfsKey getNextFolloweeToPoll()
	{
		return _backingStore.nextKeyToPoll();
	}

	@Override
	public void addElement(IpfsKey followeeKey, FollowingCacheElement element)
	{
		FollowRecord record = _backingStore.checkoutRecord(followeeKey);
		Assert.assertTrue(null != record);
		FollowingCacheElement[] oldElements = record.elements();
		FollowingCacheElement[] newElements = new FollowingCacheElement[oldElements.length + 1];
		System.arraycopy(oldElements, 0, newElements, 0, oldElements.length);
		newElements[oldElements.length] = element;
		FollowRecord newRecord = new FollowRecord(record.publicKey(), record.lastFetchedRoot(), record.lastPollMillis(), newElements);
		_backingStore.checkinRecord(newRecord);
	}

	@Override
	public void removeElement(IpfsKey followeeKey, IpfsFile elementCid)
	{
		FollowRecord record = _backingStore.checkoutRecord(followeeKey);
		Assert.assertTrue(null != record);
		FollowingCacheElement[] oldElements = record.elements();
		FollowingCacheElement[] newElements = Stream.of(oldElements)
				.filter((FollowingCacheElement elt) -> !elementCid.equals(elt.elementHash()))
				.collect(Collectors.toList())
				.toArray((int size) -> new FollowingCacheElement[size])
		;
		FollowRecord newRecord = new FollowRecord(record.publicKey(), record.lastFetchedRoot(), record.lastPollMillis(), newElements);
		_backingStore.checkinRecord(newRecord);
		
	}

	@Override
	public void createNewFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		FollowRecord record = _backingStore.checkoutRecord(followeeKey);
		Assert.assertTrue(null == record);
		FollowRecord newRecord = new FollowRecord(followeeKey, indexRoot, lastPollMillis, new FollowingCacheElement[0]);
		_backingStore.checkinRecord(newRecord);
	}

	@Override
	public void updateExistingFollowee(IpfsKey followeeKey, IpfsFile indexRoot, long lastPollMillis)
	{
		FollowRecord record = _backingStore.checkoutRecord(followeeKey);
		Assert.assertTrue(null != record);
		FollowRecord newRecord = new FollowRecord(record.publicKey(), indexRoot, lastPollMillis, record.elements());
		_backingStore.checkinRecord(newRecord);
	}

	@Override
	public void removeFollowee(IpfsKey followeeKey)
	{
		FollowRecord record = _backingStore.checkoutRecord(followeeKey);
		Assert.assertTrue(null != record);
		// We also want to make sure that this is empty so we should clean up any cached elements before removing.
		Assert.assertTrue(0 == record.elements().length);
	}
}
