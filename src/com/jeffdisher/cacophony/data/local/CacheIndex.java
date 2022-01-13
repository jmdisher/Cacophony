package com.jeffdisher.cacophony.data.local;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.utils.Assert;

import io.ipfs.multihash.Multihash;


/**
 * The index of what data is pinned in the local node and the corresponding ref count for each entries.
 * This includes data added for any reason, be it locally uploaded, fetched for following, or fetched explicitly.
 * NOTE:  There isn't currently any special durability on the pin/unpin operations, although that will eventually be required if we don't want to leak.
 */
public class CacheIndex
{
	public static CacheIndex newCache()
	{
		return new CacheIndex(new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	public static CacheIndex fromStream(InputStream inputStream)
	{
		Map<String, Integer> map = null;
		try (ObjectInputStream stream = new ObjectInputStream(inputStream))
		{
			map = (HashMap<String, Integer>)stream.readObject();
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
		return new CacheIndex(map);
	}

	// The key is the Multihash but we store it as a string to serialize it.
	private final Map<String, Integer> _map;

	private CacheIndex(Map<String, Integer> map)
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
	public boolean shouldPinAfterAdding(Multihash hash)
	{
		return _isNewAfterIncrement(hash);
	}

	/**
	 * Called when a hash has been explicitly uploaded to increment its refcount.
	 * NOTE:  It is possible that this was already added so we will just increment the count, in that case.
	 * 
	 * @param hash The hash of the data uploaded.
	 */
	public void hashWasAdded(Multihash hash)
	{
		_isNewAfterIncrement(hash);
	}

	/**
	 * Decrements the refcount for this hash.
	 * 
	 * @param hash The hash we want to remove.
	 * @return True if this should be unpinned as its refcount is now zero.
	 */
	public boolean shouldUnpinAfterRemoving(Multihash hash)
	{
		String raw = hash.toBase58();
		Integer original = _map.remove(raw);
		// We currently assume that something we would want to unpin is always here.
		Assert.assertTrue(null != original);
		int count = original - 1;
		boolean isPresent = (count > 0);
		if (isPresent)
		{
			_map.put(raw, count);
		}
		return !isPresent;
	}


	private boolean _isNewAfterIncrement(Multihash hash)
	{
		String raw = hash.toBase58();
		int count = _map.getOrDefault(raw, 0);
		boolean isNew = (0 == count);
		count += 1;
		_map.put(raw, count);
		return isNew;
	}
}
