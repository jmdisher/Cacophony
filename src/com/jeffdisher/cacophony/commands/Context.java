package com.jeffdisher.cacophony.commands;

import java.net.URL;
import java.util.function.LongSupplier;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.caches.IEntryCacheRegistry;
import com.jeffdisher.cacophony.caches.ILocalRecordCache;
import com.jeffdisher.cacophony.caches.ILocalUserInfoCache;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.local.v4.DraftManager;
import com.jeffdisher.cacophony.logic.ExplicitCacheManager;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * A container of resources which can be used by a command.
 * Instances of this type are expected to configured for sub-commands, etc, based on resources available at the
 * top-level.
 */
public class Context
{
	/**
	 * Requests read access.
	 * This helper is static since Context shouldn't be acting as though it has behaviour.
	 * 
	 * @param context The current command context.
	 * @return The read access interface.
	 */
	public static IReadingAccess readAccess(Context context)
	{
		return StandardAccess.readAccess(context.basicConnection, context.scheduler, context.logger, context.sharedDataModel, context.getSelectedKey());
	}

	/**
	 * Requests write access.
	 * This helper is static since Context shouldn't be acting as though it has behaviour.
	 * 
	 * @param context The current command context.
	 * @return The write access interface.
	 */
	public static IWritingAccess writeAccess(Context context)
	{
		return StandardAccess.writeAccess(context.basicConnection, context.scheduler, context.logger, context.sharedDataModel, context.getSelectedKey());
	}


	public final DraftManager sharedDraftManager;
	public final LocalDataModel sharedDataModel;
	public final IConnection basicConnection;
	public final INetworkScheduler scheduler;
	public final LongSupplier currentTimeMillisGenerator;
	public final ILogger logger;
	public final URL baseUrl;
	public final ILocalRecordCache recordCache;
	public final ILocalUserInfoCache userInfoCache;
	public final IEntryCacheRegistry entryRegistry;
	public final CacheUpdater cacheUpdater;
	public final ExplicitCacheManager explicitCacheManager;
	private IpfsKey _selectedKey;

	public Context(DraftManager sharedDraftManager
			, LocalDataModel sharedDataModel
			, IConnection basicConnection
			, INetworkScheduler scheduler
			, LongSupplier currentTimeMillisGenerator
			, ILogger logger
			, URL baseUrl
			, ILocalRecordCache recordCache
			, ILocalUserInfoCache userInfoCache
			, IEntryCacheRegistry entryRegistry
			, CacheUpdater cacheUpdater
			, ExplicitCacheManager explicitCacheManager
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
		this.cacheUpdater = cacheUpdater;
		this.explicitCacheManager = explicitCacheManager;
		_selectedKey = selectedKey;
	}

	/**
	 * @return The selected home user key (could be null).
	 */
	public IpfsKey getSelectedKey()
	{
		return _selectedKey;
	}

	/**
	 * Changes the selected home user key.
	 * 
	 * @param key The new user key (could be null).
	 */
	public void setSelectedKey(IpfsKey key)
	{
		_selectedKey = key;
	}

	/**
	 * Creates a clone of the receiver, but with the given home user selected.  This is often used in sub-commands where
	 * the home user is specified but shouldn't change the existing context.
	 * 
	 * @param selectedKey The home user to select.
	 * @return The new Context object.
	 */
	public Context cloneWithSelectedKey(IpfsKey selectedKey)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		Context context = new Context(this.sharedDraftManager
				, this.sharedDataModel
				, this.basicConnection
				, this.scheduler
				, this.currentTimeMillisGenerator
				, this.logger
				, this.baseUrl
				, this.recordCache
				, this.userInfoCache
				, this.entryRegistry
				, this.cacheUpdater
				, this.explicitCacheManager
				, selectedKey
		);
		return context;
	}

	/**
	 * Creates a clone of the receiver, but populating it with the given caches.  This is typically only used during
	 * server start-up, when it needs to override the non-interactive Context created when the system started.
	 * 
	 * @param localRecordCache The local record cache.
	 * @param userInfoCache The user info cache.
	 * @param entryRegistry The entry registry.
	 * @param cacheUpdater The cache updater object.
	 * @param explicitCacheManager The manager of the explicit cache.
	 * @return The new Context object.
	 */
	public Context cloneWithExtras(ILocalRecordCache localRecordCache
			, ILocalUserInfoCache userInfoCache
			, IEntryCacheRegistry entryRegistry
			, CacheUpdater cacheUpdater
			, ExplicitCacheManager explicitCacheManager
	)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		Context context = new Context(this.sharedDraftManager
				, this.sharedDataModel
				, this.basicConnection
				, this.scheduler
				, this.currentTimeMillisGenerator
				, this.logger
				, this.baseUrl
				, localRecordCache
				, userInfoCache
				, entryRegistry
				, cacheUpdater
				, explicitCacheManager
				, _selectedKey
		);
		return context;
	}
}