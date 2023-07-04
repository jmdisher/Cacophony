package com.jeffdisher.cacophony.commands;

import java.net.URL;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.logic.DraftManager;
import com.jeffdisher.cacophony.logic.EntryCacheRegistry;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.LocalRecordCache;
import com.jeffdisher.cacophony.logic.LocalUserInfoCache;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * A container of resources which can be used by a command.
 * Instances of this type are expected to configured for sub-commands, etc, based on resources available at the
 * top-level.
 */
public class Context
{
	public final DraftManager sharedDraftManager;
	public final LocalDataModel sharedDataModel;
	public final IConnection basicConnection;
	public final INetworkScheduler scheduler;
	public final LongSupplier currentTimeMillisGenerator;
	public final ILogger logger;
	public final URL baseUrl;
	public final LocalRecordCache recordCache;
	public final LocalUserInfoCache userInfoCache;
	public final EntryCacheRegistry entryRegistry;
	public final boolean enableVersion2Data;
	private IpfsKey _selectedKey;

	public Context(DraftManager sharedDraftManager
			, LocalDataModel sharedDataModel
			, IConnection basicConnection
			, INetworkScheduler scheduler
			, LongSupplier currentTimeMillisGenerator
			, ILogger logger
			, URL baseUrl
			, LocalRecordCache recordCache
			, LocalUserInfoCache userInfoCache
			, EntryCacheRegistry entryRegistry
			, boolean enableVersion2Data
			, IpfsKey selectedKey
	)
	{
		this.sharedDraftManager = sharedDraftManager;
		this.sharedDataModel = sharedDataModel;
		this.basicConnection = basicConnection;
		this.scheduler = scheduler;
		this.currentTimeMillisGenerator = currentTimeMillisGenerator;
		this.logger = logger;
		this.baseUrl = baseUrl;
		this.recordCache = recordCache;
		this.userInfoCache = userInfoCache;
		this.entryRegistry = entryRegistry;
		this.enableVersion2Data = enableVersion2Data;
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
		return new Context(this.sharedDraftManager
				, this.sharedDataModel
				, this.basicConnection
				, this.scheduler
				, this.currentTimeMillisGenerator
				, this.logger
				, this.baseUrl
				, this.recordCache
				, this.userInfoCache
				, this.entryRegistry
				, this.enableVersion2Data
				, selectedKey
		);
	}

	public synchronized Context cloneWithExtras(LocalRecordCache localRecordCache, LocalUserInfoCache userInfoCache, EntryCacheRegistry entryRegistry)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		return new Context(this.sharedDraftManager
				, this.sharedDataModel
				, this.basicConnection
				, this.scheduler
				, this.currentTimeMillisGenerator
				, this.logger
				, this.baseUrl
				, localRecordCache
				, userInfoCache
				, entryRegistry
				, this.enableVersion2Data
				, _selectedKey
		);
	}
}
