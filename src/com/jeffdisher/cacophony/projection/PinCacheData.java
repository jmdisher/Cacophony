package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v2.Opcode_SetPinnedCount;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public class PinCacheData
{
	public static PinCacheData buildOnCache(GlobalPinCache pinCache)
	{
		Map<IpfsFile, Integer> map = pinCache.readInternalCopy();
		return new PinCacheData(map);
	}

	public static PinCacheData createEmpty()
	{
		return new PinCacheData(new HashMap<>());
	}


	private final Map<IpfsFile, Integer> _map;

	private PinCacheData(Map<IpfsFile, Integer> map)
	{
		_map = map;
	}

	public GlobalPinCache serializeToPinCache()
	{
		GlobalPinCache pinCache = GlobalPinCache.newCache();
		for (Map.Entry<IpfsFile, Integer> elt : _map.entrySet())
		{
			IpfsFile cid = elt.getKey();
			int count = elt.getValue();
			for (int i = 0; i < count; ++i)
			{
				pinCache.hashWasAdded(cid);
			}
		}
		return pinCache;
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
}
