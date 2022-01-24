package com.jeffdisher.cacophony.data.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Stores and manages basic access to the keys we are following, what data we have from each, and our polling schedule.
 */
public class FollowIndex
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
			FollowRecord record = new FollowRecord(publicKey.key().toString(), fetchRootIndex.cid().toString(), 0L, new FollowingCacheElement[0]);
			_sortedFollowList.add(0, record);
		}
	}

	/**
	 * Removes the given public key from the list we are following.
	 * If we aren't following them, this does nothing.
	 * 
	 * @param publicKey The key to stop following.
	 */
	public void removeFollowing(IpfsKey publicKey)
	{
		_removeRecordFromList(publicKey);
	}

	public IpfsKey nextKeyToPoll()
	{
		return (_sortedFollowList.isEmpty())
				? null
				: IpfsKey.fromPublicKey(_sortedFollowList.get(0).publicKey())
		;
	}

	public IpfsFile getLastFetchedRoot(IpfsKey publicKey)
	{
		String publicKeyString = publicKey.key().toString();
		List<FollowRecord> list = _sortedFollowList.stream().filter((r) -> publicKeyString.equals(r.publicKey())).collect(Collectors.toList());
		Assert.assertTrue(list.size() <= 1);
		return (list.isEmpty())
				? null
				: IpfsFile.fromIpfsCid(list.get(0).lastFetchedRoot())
		;
	}

	public void updatePollTimerNoChange(IpfsKey publicKey, long currentTimeMillis)
	{
		FollowRecord record = _removeRecordFromList(publicKey);
		FollowRecord newRecord = new FollowRecord(publicKey.toString(), record.lastFetchedRoot(), currentTimeMillis, record.elements());
		_sortedFollowList.add(newRecord);
	}

	public void addNewElementToFollower(IpfsKey publicKey, IpfsFile fetchedRoot, IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long currentTimeMillis)
	{
		FollowRecord record = _removeRecordFromList(publicKey);
		String imageHashString = (null != imageHash)
				? imageHash.cid().toString()
				: null
		;
		FollowingCacheElement element = new FollowingCacheElement(elementHash.cid().toString(), imageHashString, leafHash.cid().toString());
		FollowingCacheElement[] oldElements = record.elements();
		FollowingCacheElement[] newElements = new FollowingCacheElement[oldElements.length + 1];
		System.arraycopy(oldElements, 0, newElements, 0, oldElements.length);
		newElements[oldElements.length] = element;

		FollowRecord newRecord = new FollowRecord(publicKey.key().toString(), fetchedRoot.cid().toString(), currentTimeMillis, newElements);
		_sortedFollowList.add(newRecord);
	}


	private FollowRecord _removeRecordFromList(IpfsKey publicKey)
	{
		String publicKeyString = publicKey.key().toString();
		Iterator<FollowRecord> iter = _sortedFollowList.iterator();
		FollowRecord found = null;
		while ((null == found) && iter.hasNext())
		{
			FollowRecord one = iter.next();
			if (publicKeyString.equals(one.publicKey()))
			{
				iter.remove();
				found = one;
			}
		}
		Assert.assertTrue(null != found);
		return found;
	}
}