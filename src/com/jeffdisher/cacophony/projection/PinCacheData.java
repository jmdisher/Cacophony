package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v2.Opcode_SetPinnedCount;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public class PinCacheData
{
	public static PinCacheData createEmpty()
	{
		return new PinCacheData(new HashMap<>());
	}


	private final Map<IpfsFile, Integer> _map;

	private PinCacheData(Map<IpfsFile, Integer> map)
	{
		_map = map;
	}

	public void serializeToOpcodeStream(ObjectOutputStream stream) throws IOException
	{
		for (Map.Entry<IpfsFile, Integer> elt : _map.entrySet())
		{
			stream.writeObject(new Opcode_SetPinnedCount(elt.getKey(), elt.getValue()));
		}
	}

	public boolean isPinned(IpfsFile file)
	{
		return _map.containsKey(file);
	}

	public void addRef(IpfsFile file)
	{
		Integer value = _map.get(file);
		int newValue = (null != value)
				? value + 1
				: 1
		;
		_map.put(file, newValue);
	}

	public void delRef(IpfsFile file)
	{
		Integer value = _map.get(file);
		int newValue = value - 1;
		Assert.assertTrue(newValue >= 0);
		if (newValue > 0)
		{
			_map.put(file, newValue);
		}
		else
		{
			_map.remove(file);
		}
	}

	/**
	 * Used when starting new transactions.
	 * 
	 * @return A copy of the set of resources we have explicitly pinned.
	 */
	public Set<IpfsFile> snapshotPinnedSet()
	{
		return Set.copyOf(_map.keySet());
	}

	/**
	 * Compares the receiver to the given instance, returning true if they have the same elements and the same count for
	 * each one.
	 * This is used instead of a .equals() override since it doesn't need that kind of generalizability.
	 * The implementation assumes that the receiver is the "canonical" (from disk) version of the data while the
	 * parameter is the "derived" version (from more fundamental data elements).
	 * 
	 * @param derived The other instance.
	 * @return True if both instances have the same elements and element values.
	 */
	public boolean verifyMatch(PinCacheData derived)
	{
		boolean doesMatch = false;
		if (_map.size() == derived._map.size())
		{
			doesMatch = true;
			for (IpfsFile elt : _map.keySet())
			{
				if (_map.get(elt).intValue() != derived._map.get(elt).intValue())
				{
					doesMatch = false;
					break;
				}
			}
		}
		
		// For now, we want to log information describing this mismatch, just to observe inconsistencies.
		if (!doesMatch)
		{
			System.err.println("MISMATCH in PinCache:  Canonical size is " + _map.size() + " while derived is " + derived._map.size());
			for (IpfsFile key : _map.keySet())
			{
				int canonicalCount = _map.get(key).intValue();
				if (derived._map.containsKey(key))
				{
					int derivedCount = derived._map.get(key).intValue();
					if (canonicalCount != derivedCount)
					{
						System.err.println("BOTH(" + key + "), canonical value " + canonicalCount + " while derived is " + derivedCount);
					}
				}
				else
				{
					System.err.println("CANONICAL(" + key + ") value " + canonicalCount);
				}
			}
			for (IpfsFile key : derived._map.keySet())
			{
				if (!_map.containsKey(key))
				{
					System.err.println("DERIVED(" + key + ") value " + derived._map.get(key));
				}
			}
		}
		return doesMatch;
	}
}
