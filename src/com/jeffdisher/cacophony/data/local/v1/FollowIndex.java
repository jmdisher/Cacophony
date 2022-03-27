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

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Stores and manages basic access to the keys we are following, what data we have from each, and our polling schedule.
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
	 * Adds the given follower to the front of the list of following keys to be fetched.
	 * If the key is already being followed, this method does nothing.
	 * 
	 * @param publicKey The key to follow.
	 * @param fetchRootIndex The initial root index.
	 */
	public void addFollowingWithInitialState(IpfsKey publicKey, IpfsFile fetchRootIndex)
	{
		boolean isNew = !_sortedFollowList.stream().anyMatch((r) -> publicKey.equals(r.publicKey()));
		if (isNew)
		{
			FollowRecord record = new FollowRecord(publicKey, fetchRootIndex, 0L, new FollowingCacheElement[0]);
			_sortedFollowList.add(0, record);
		}
	}

	/**
	 * Removes the given public key from the list we are following and returns the last state of the cached data.
	 * If we aren't following them, this does nothing and returns null.
	 * 
	 * @param publicKey The key to stop following.
	 * @return The last state of the cache, null if not being followed.
	 */
	public FollowRecord removeFollowing(IpfsKey publicKey)
	{
		return _removeRecordFromList(publicKey);
	}

	public IpfsKey nextKeyToPoll()
	{
		return (_sortedFollowList.isEmpty())
				? null
				: _sortedFollowList.get(0).publicKey()
		;
	}

	public IpfsFile getLastFetchedRoot(IpfsKey publicKey)
	{
		FollowRecord record = _getFollowerRecord(publicKey);
		return (null == record)
				? null
				: record.lastFetchedRoot()
		;
	}

	public FollowRecord getFollowerRecord(IpfsKey publicKey)
	{
		return _getFollowerRecord(publicKey);
	}

	public void updateFollowee(IpfsKey publicKey, IpfsFile fetchRootIndex, long currentTimeMillis)
	{
		FollowRecord record = _removeRecordFromList(publicKey);
		Assert.assertTrue(null != record);
		FollowRecord newRecord = new FollowRecord(publicKey, fetchRootIndex, currentTimeMillis, record.elements());
		_sortedFollowList.add(newRecord);
	}

	public void addNewElementToFollower(IpfsKey publicKey, IpfsFile fetchedRoot, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long currentTimeMillis, long combinedSizeBytes)
	{
		FollowRecord record = _removeRecordFromList(publicKey);
		Assert.assertTrue(null != record);
		FollowingCacheElement element = new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes);
		FollowingCacheElement[] oldElements = record.elements();
		FollowingCacheElement[] newElements = new FollowingCacheElement[oldElements.length + 1];
		System.arraycopy(oldElements, 0, newElements, 0, oldElements.length);
		newElements[oldElements.length] = element;

		FollowRecord newRecord = new FollowRecord(publicKey, fetchedRoot, currentTimeMillis, newElements);
		_sortedFollowList.add(newRecord);
	}

	@Override
	public Iterator<FollowRecord> iterator()
	{
		return Collections.unmodifiableList(_sortedFollowList).iterator();
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
