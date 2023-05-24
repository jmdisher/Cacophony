package com.jeffdisher.cacophony.commands;

import java.net.URL;

import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.types.IpfsKey;


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
	private IpfsKey _selectedKey;

	public Context(IEnvironment environment
			, ILogger logger
			, URL baseUrl
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
			, IpfsKey selectedKey
	)
	{
		this.environment = environment;
		this.logger = logger;
		this.baseUrl = baseUrl;
		this.recordCache = recordCache;
		this.userInfoCache = userInfoCache;
		this.entryRegistry = entryRegistry;
		_selectedKey = selectedKey;
	}

	public IpfsKey getSelectedKey()
	{
		return _selectedKey;
	}

	public void setSelectedKey(IpfsKey key)
	{
		// Note that this could be null.
		_selectedKey = key;
	}

	public synchronized Context cloneWithSelectedKey(IpfsKey selectedKey)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		return new Context(this.environment
				, this.logger
				, this.baseUrl
				, this.recordCache
				, this.userInfoCache
				, this.entryRegistry
				, selectedKey
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
				, _selectedKey
		);
	}
}
