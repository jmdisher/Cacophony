package com.jeffdisher.cacophony.data.local.v1;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The index of what data is pinned in the local node and the corresponding ref count for each entries.
 * This includes data added for any reason, be it locally uploaded, fetched for following, or fetched explicitly.
 * NOTE:  There isn't currently any special durability on the pin/unpin operations, although that will eventually be required if we don't want to become inconsistent on ctrl-c.
 */
public class GlobalPinCache
{
	public static GlobalPinCache newCache()
	{
		return new GlobalPinCache(new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	public static GlobalPinCache fromStream(InputStream inputStream)
	{
		Map<IpfsFile, Integer> map = null;
		try (ObjectInputStream stream = new ObjectInputStream(inputStream))
		{
			map = (HashMap<IpfsFile, Integer>)stream.readObject();
		}
		catch (ClassNotFoundException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		catch (EOFException e)
		{
			// This just means we are done reading.
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
		return new GlobalPinCache(map);
	}

	private final Map<IpfsFile, Integer> _map;

	private GlobalPinCache(Map<IpfsFile, Integer> map)
	{
		Assert.assertTrue(null != map);
		_map = map;
	}

	public void writeToStream(OutputStream outputStream)
	{
		try (ObjectOutputStream stream = new ObjectOutputStream(outputStream))
		{
			stream.writeObject(_map);
		}
		catch (IOException e)
		{
			// We don't expect this.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Increments the refcount for this hash.
	 * 
	 * @param hash The hash we want to add.
	 * @return True if the data isn't already present and needs to be pinned.
	 */
	public boolean shouldPinAfterAdding(IpfsFile hash)
	{
		return _isNewAfterIncrement(hash);
	}

	/**
	 * Called when a hash has been explicitly uploaded to increment its refcount.
	 * NOTE:  It is possible that this was already added so we will just increment the count, in that case.
	 * 
	 * @param hash The hash of the data uploaded.
	 */
	public void hashWasAdded(IpfsFile hash)
	{
		_isNewAfterIncrement(hash);
	}

	/**
	 * Decrements the refcount for this hash.
	 * 
	 * @param hash The hash we want to remove.
	 * @return True if this should be unpinned as its refcount is now zero.
	 */
	public boolean shouldUnpinAfterRemoving(IpfsFile hash)
	{
		Integer original = _map.remove(hash);
		// We currently assume that something we would want to unpin is always here.
		Assert.assertTrue(null != original);
		int count = original - 1;
		boolean isPresent = (count > 0);
		if (isPresent)
		{
			_map.put(hash, count);
		}
		return !isPresent;
	}

	/**
	 * Returns true if this hash is in the cache at all, no matter how many references it has.
	 * 
	 * @param hash The hash to check.
	 * @return True if this hash is cached.
	 */
	public boolean isCached(IpfsFile hash)
	{
		return _map.containsKey(hash);
	}


	private boolean _isNewAfterIncrement(IpfsFile hash)
	{
		int count = _map.getOrDefault(hash, 0);
		boolean isNew = (0 == count);
		count += 1;
		_map.put(hash, count);
		return isNew;
	}
}
