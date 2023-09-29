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
		return StandardAccess.readAccess(context.accessTuple.basicConnection, context.accessTuple.scheduler, context.logger, context.accessTuple.sharedDataModel(), context.getSelectedKey());
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
		return StandardAccess.writeAccess(context.accessTuple.basicConnection, context.accessTuple.scheduler, context.logger, context.accessTuple.sharedDataModel(), context.getSelectedKey());
	}

	/**
	 * Requests read access using the minimal access tuple and no selected key.
	 * This helper is static since Context shouldn't be acting as though it has behaviour.
	 * 
	 * @param accessTuple The minimal access tuple.
	 * @param logger The logger.
	 * @return The read access interface.
	 */
	public static IReadingAccess readAccessBasic(Context.AccessTuple accessTuple, ILogger logger)
	{
		return StandardAccess.readAccess(accessTuple.basicConnection, accessTuple.scheduler, logger, accessTuple.sharedDataModel(), null);
	}

	/**
	 * Requests write access using the minimal access tuple and no selected key.
	 * This helper is static since Context shouldn't be acting as though it has behaviour.
	 * 
	 * @param accessTuple The minimal access tuple.
	 * @param logger The logger.
	 * @return The write access interface.
	 */
	public static IWritingAccess writeAccessBasic(Context.AccessTuple accessTuple, ILogger logger)
	{
		return StandardAccess.writeAccess(accessTuple.basicConnection, accessTuple.scheduler, logger, accessTuple.sharedDataModel(), null);
	}


	public final DraftManager sharedDraftManager;
	public final AccessTuple accessTuple;
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
			, AccessTuple accessTuple
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
		this.accessTuple = accessTuple;
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

	public IpfsKey getSelectedKey()
	{
		return _selectedKey;
	}

	public void setSelectedKey(IpfsKey key)
	{
		// Note that this could be null.
		_selectedKey = key;
	}

	public Context cloneWithSelectedKey(IpfsKey selectedKey)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		Context context = new Context(this.sharedDraftManager
				, this.accessTuple
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

	public Context cloneWithExtras(ILocalRecordCache localRecordCache
			, ILocalUserInfoCache userInfoCache
			, IEntryCacheRegistry entryRegistry
			, CacheUpdater cacheUpdater
			, ExplicitCacheManager explicitCacheManager
	)
	{
		// We reference everything as a shared structure except for the key-name map, which is a duplicate.
		Context context = new Context(this.sharedDraftManager
				, this.accessTuple
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


	/**
	 * Components required to use StandardAccess (since these are almost exclusively for those cases and never change
	 * during a run - some other parts of the context change or are replaced in clones).
	 */
	public static record AccessTuple(LocalDataModel sharedDataModel, IConnection basicConnection, INetworkScheduler scheduler) {}
}