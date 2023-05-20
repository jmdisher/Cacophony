package com.jeffdisher.cacophony.commands;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * A container of resources which can be used by a command.
 * Instances of this type are expected to configured for sub-commands, etc, based on resources available at the
 * top-level.
 */
public class Context
{
	public final IEnvironment environment;
	public final ILogger logger;
	public final URL baseUrl;
	public final LocalRecordCache recordCache;
	public final LocalUserInfoCache userInfoCache;
	public final EntryCacheRegistry entryRegistry;
	public final String keyName;
	private final Map<String, IpfsKey> _keyNameMap;

	public Context(IEnvironment environment
			, ILogger logger
			, URL baseUrl
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
			, Map<String, IpfsKey> keyNameMap
			, String keyName
	)
	{
		this.environment = environment;
		this.logger = logger;
		this.baseUrl = baseUrl;
		this.recordCache = recordCache;
		this.userInfoCache = userInfoCache;
		this.entryRegistry = entryRegistry;
		_keyNameMap = keyNameMap;
		this.keyName = keyName;
	}

	public IpfsKey getSelectedKey()
	{
		return _keyNameMap.get(this.keyName);
	}

	public synchronized void addKey(String name, IpfsKey key)
	{
		// We currently require that the name be the selected one.
		Assert.assertTrue(this.keyName.equals(name));
		Assert.assertTrue(!_keyNameMap.containsKey(name));
		_keyNameMap.put(name, key);
	}

	public synchronized void removeKey(String name)
	{
		// We currently require that the name be the selected one.
		Assert.assertTrue(this.keyName.equals(name));
		Assert.assertTrue(_keyNameMap.containsKey(name));
		_keyNameMap.remove(name);
	}

	public synchronized Context cloneWithSelectedKey(String keyName)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		return new Context(this.environment
				, this.logger
				, this.baseUrl
				, this.recordCache
				, this.userInfoCache
				, this.entryRegistry
				, new HashMap<>(_keyNameMap)
				, keyName
		);
	}

	public synchronized Context cloneWithExtras(LocalRecordCache localRecordCache, LocalUserInfoCache userInfoCache, EntryCacheRegistry entryRegistry)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		return new Context(this.environment
				, this.logger
				, this.baseUrl
				, localRecordCache
				, userInfoCache
				, entryRegistry
				, new HashMap<>(_keyNameMap)
				, this.keyName
		);
	}

	/**
	 * Used as a reverse lookup of a given key to its home user key name.  This is used in some cases where the key must
	 * be selected by name, but the actual key is passed around as the generalized parameter.
	 * 
	 * @param key The key.
	 * @return The home user key name for this key, or null if it isn't found.
	 */
	public synchronized String findNameForKey(IpfsKey key)
	{
		String name = null;
		for (Map.Entry<String, IpfsKey> elt : _keyNameMap.entrySet())
		{
			if (elt.getValue().equals(key))
			{
				name = elt.getKey();
				break;
			}
		}
		return name;
	}
}
