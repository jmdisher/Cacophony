package com.jeffdisher.cacophony.projection;

import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
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
}
