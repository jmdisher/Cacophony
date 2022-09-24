package com.jeffdisher.cacophony.data.local.v1;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Stores and manages basic access to the keys we are following, what data we have from each, and our polling schedule.
 * While some basic helpers exist for reading the index in more complicated ways, individual FollowRecord instances
 * are modified via a checkout-checking system.
 * This allows for a narrow interface while also allowing new/update special-cases to be checked by callers.
 * It does, however, mean that care must be taken to either entirely drop the index or re-checkin an element, on
 * failure.
 */
public class FollowIndex implements Iterable<FollowRecord>
{
	public static FollowIndex emptyFollowIndex()
	{
		return new FollowIndex(new ArrayList<>());
	}

	@SuppressWarnings("unchecked")
	public static FollowIndex fromStream(InputStream inputStream)
	{
		List<FollowRecord> list = null;
		try (ObjectInputStream stream = new ObjectInputStream(inputStream))
		{
			list = (List<FollowRecord>)stream.readObject();
		}
		catch (ClassNotFoundException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		return new FollowIndex(list);
	}


	// We sort these by when they were last fetched:  head element is next to fetch.
	// NOTE:  This isn't a strict sorting since parallel requests in the future could allow slightly out-of-order elements.
	private final List<FollowRecord> _sortedFollowList;

	private FollowIndex(List<FollowRecord> sortedFollowList)
	{
		_sortedFollowList = sortedFollowList;
	}

	public void writeToStream(OutputStream outputStream)
	{
		try (ObjectOutputStream stream = new ObjectOutputStream(outputStream))
		{
			stream.writeObject(_sortedFollowList);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Removes the given public key from the list we are following and returns the last state of the cached data.
	 * If we aren't following them, this does nothing and returns null.
	 * 
	 * @param publicKey The key to stop following.
	 * @return The last state of the cache, null if not being followed.
	 */
	public FollowRecord checkoutRecord(IpfsKey publicKey)
	{
		return _removeRecordFromList(publicKey);
	}

	/**
	 * Looks up the FollowRecord for the given public key and returns a reference to it WITHOUT removing it from the index.
	 * If we aren't following them, this returns null.
	 * 
	 * @param publicKey The key to stop following.
	 * @return The last state of the cache, null if not being followed.
	 */
	public FollowRecord peekRecord(IpfsKey publicKey)
	{
		return _getFollowerRecord(publicKey);
	}

	/**
	 * Peeks at the next followee which needs to be polled for updates (has no side-effects).
	 * 
	 * @return The next followee to poll, null if there aren't any.
	 */
	public IpfsKey nextKeyToPoll()
	{
		return (_sortedFollowList.isEmpty())
				? null
				: _sortedFollowList.get(0).publicKey()
		;
	}

	/**
	 * Checks the given record back into the index, at the END of the list (meaning it will be the last to poll, next).
	 * 
	 * @param record The record to check in.
	 */
	public void checkinRecord(FollowRecord record)
	{
		// The record must exist.
		Assert.assertTrue(null != record);
		Assert.assertTrue(null != record.publicKey());
		Assert.assertTrue(null != record.lastFetchedRoot());
		Assert.assertTrue(null != record.elements());
		
		// Make sure a record for this user isn't already here.
		Assert.assertTrue(null == _getFollowerRecord(record.publicKey()));
		
		// Add it to the end of the list.
		_sortedFollowList.add(record);
	}

	@Override
	public Iterator<FollowRecord> iterator()
	{
		return Collections.unmodifiableList(_sortedFollowList).iterator();
	}

	/**
	 * Creates a clone of the receiver, starting with the same state but allowing independent divergence of the state
	 * of the 2 objects.
	 * @return The new cloned instance.
	 */
	public FollowIndex mutableClone()
	{
		return new FollowIndex(new ArrayList<>(_sortedFollowList));
	}

	public void replaceCached(IpfsKey publicKey, FollowingCacheElement[] newElements)
	{
		int index = -1;
		for (int i = 0; i < _sortedFollowList.size(); ++i)
		{
			if (publicKey.equals(_sortedFollowList.get(i).publicKey()))
			{
				index = i;
				break;
			}
		}
		Assert.assertTrue(-1 != index);
		FollowRecord oldRecord = _sortedFollowList.remove(index);
		FollowRecord newRecord = new FollowRecord(publicKey, oldRecord.lastFetchedRoot(), oldRecord.lastPollMillis(), newElements);
		_sortedFollowList.add(index, newRecord);
	}


	private FollowRecord _removeRecordFromList(IpfsKey publicKey)
	{
		Iterator<FollowRecord> iter = _sortedFollowList.iterator();
		FollowRecord found = null;
		while ((null == found) && iter.hasNext())
		{
			FollowRecord one = iter.next();
			if (publicKey.equals(one.publicKey()))
			{
				iter.remove();
				found = one;
			}
		}
		return found;
	}

	private FollowRecord _getFollowerRecord(IpfsKey publicKey)
	{
		List<FollowRecord> list = _sortedFollowList.stream().filter((r) -> publicKey.equals(r.publicKey())).collect(Collectors.toList());
		Assert.assertTrue(list.size() <= 1);
		return (list.isEmpty())
				? null
				: list.get(0)
		;
	}
}
