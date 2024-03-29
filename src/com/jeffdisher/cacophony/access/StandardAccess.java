package com.jeffdisher.cacophony.access;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.LocalDataModel;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.projection.ChannelData;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FolloweeData;
import com.jeffdisher.cacophony.projection.IExplicitCacheReading;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureResolve;
import com.jeffdisher.cacophony.scheduler.FutureSave;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.scheduler.FutureVoid;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;


/**
 * The standard factories and implementation of the access design.
 * The general idea is that higher-level components of the system should not be concerned with the individual parts of
 * the underlying implementation.
 * A thread needs to request the access type it wants, closing it when finished.  Those access types have different
 * interfaces which implement the underlying functional primitives of the system.
 * Note that the point of this design is that it doesn't distinguish between local storage versus network access
 * primitives, allowing the accessing code to interact in as high-level a way as possible without needing to manage
 * those individual parts of the system which would otherwise expose more specific pieces of primitive functionality.
 */
public class StandardAccess implements IWritingAccess
{
	/**
	 * Requests read access.
	 * Note that this assumes that the config directory has already been created and is valid (see
	 * "StandardAccess.createNewChannelConfig" and "LocalDataModel.verifyStorageConsistency").
	 * 
	 * @param connection The low-level IPFS connection.
	 * @param scheduler The network scheduler.
	 * @param logger The logger.
	 * @param dataModel The shared local data model.
	 * @param publicKey The public key of the current home user.
	 * @return The read access interface.
	 */
	public static IReadingAccess readAccess(IConnection connection, INetworkScheduler scheduler, ILogger logger, LocalDataModel dataModel, IpfsKey publicKey)
	{
		IReadOnlyLocalData reading = dataModel.openForRead();
		String keyName = _findKeyName(reading, publicKey);
		return new StandardAccess(connection, scheduler, logger, reading, null, keyName, publicKey);
	}

	/**
	 * Requests write access.
	 * Note that this assumes that the config directory has already been created and is valid (see
	 * "StandardAccess.createNewChannelConfig" and "LocalDataModel.verifyStorageConsistency").
	 * 
	 * @param connection The low-level IPFS connection.
	 * @param scheduler The network scheduler.
	 * @param logger The logger.
	 * @param dataModel The shared local data model.
	 * @param publicKey The public key of the current home user.
	 * @return The write access interface.
	 */
	public static IWritingAccess writeAccess(IConnection connection, INetworkScheduler scheduler, ILogger logger, LocalDataModel dataModel, IpfsKey publicKey)
	{
		IReadWriteLocalData writing = dataModel.openForWrite();
		String keyName = _findKeyName(writing, publicKey);
		return new StandardAccess(connection, scheduler, logger, writing, writing, keyName, publicKey);
	}

	/**
	 * Requests write access with key overrides.
	 * This is a special-case of writeAccess() used for initial channel creation.
	 * 
	 * @param connection The low-level IPFS connection.
	 * @param scheduler The network scheduler.
	 * @param logger The logger.
	 * @param dataModel The shared local data model.
	 * @param keyName The name of the publicKey, on this IPFS node
	 * @param publicKey The public key of the current home user.
	 * @return The write access interface.
	 */
	public static IWritingAccess writeAccessWithKeyOverride(IConnection connection, INetworkScheduler scheduler, ILogger logger, LocalDataModel dataModel, String keyName, IpfsKey publicKey)
	{
		IReadWriteLocalData writing = dataModel.openForWrite();
		return new StandardAccess(connection, scheduler, logger, writing, writing, keyName, publicKey);
	}


	private static String _findKeyName(IReadOnlyLocalData data, IpfsKey publicKey)
	{
		// We want to verify that this publicKey _IS_ a home key (or else we will throw).
		String keyName = null;
		if (null != publicKey)
		{
			ChannelData localIndex = data.readLocalIndex();
			for (String homeKeyName : localIndex.getKeyNames())
			{
				if (localIndex.getPublicKey(homeKeyName).equals(publicKey))
				{
					keyName = homeKeyName;
					break;
				}
			}
			// This would just be a static error to get this far and be missing a key name (where did we get the original key?)
			Assert.assertTrue(null != keyName);
		}
		return keyName;
	}


	private final ILogger _logger;
	private final IConnection _sharedConnection;
	private final String _keyName;
	private final IpfsKey _publicKey;
	private final INetworkScheduler _scheduler;
	private final IReadOnlyLocalData _readOnly;
	private final IReadWriteLocalData _readWrite;
	
	private final PinCacheData _pinCache;
	private final FolloweeData _followeeData;
	private boolean _writeFollowIndex;
	private final ChannelData _channelData;
	private boolean _writeChannelData;
	private final FavouritesCacheData _favouritesCache;
	private boolean _writeFavouritesCache;
	private final ExplicitCacheData _explicitCache;
	private boolean _didAccessExplicitCache;

	private StandardAccess(IConnection connection, INetworkScheduler scheduler, ILogger logger, IReadOnlyLocalData readOnly, IReadWriteLocalData readWrite, String keyName, IpfsKey publicKey)
	{
		PinCacheData pinCache = readOnly.readGlobalPinCache();
		Assert.assertTrue(null != pinCache);
		FolloweeData followeeData = readOnly.readFollowIndex();
		Assert.assertTrue(null != followeeData);
		ChannelData localIndex = readOnly.readLocalIndex();
		Assert.assertTrue(null != localIndex);
		FavouritesCacheData favouritesCache = readOnly.readFavouritesCache();
		Assert.assertTrue(null != favouritesCache);
		
		Assert.assertTrue(null != connection);
		if (null != keyName)
		{
			// This should have been checked before we got here.
			Assert.assertTrue(KeyNameRules.isValidKey(keyName));
		}
		Assert.assertTrue(null != scheduler);
		
		_logger = logger;
		_sharedConnection = connection;
		_keyName = keyName;
		_publicKey = publicKey;
		_scheduler = scheduler;
		_readOnly = readOnly;
		_readWrite = readWrite;
		
		_pinCache = pinCache;
		_followeeData = followeeData;
		_channelData = localIndex;
		_favouritesCache = favouritesCache;
		_explicitCache = readOnly.readExplicitCache();
	}

	@Override
	public IFolloweeReading readableFolloweeData()
	{
		return _followeeData;
	}

	@Override
	public boolean isInPinCached(IpfsFile file)
	{
		return _pinCache.isPinned(file);
	}

	@Override
	public PrefsData readPrefs()
	{
		return _readOnly.readGlobalPrefs();
	}

	@Override
	public void requestIpfsGc() throws IpfsConnectionException
	{
		_sharedConnection.requestStorageGc();
	}

	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(_pinCache.isPinned(file));
		return _scheduler.readData(file, decoder);
	}

	@Override
	public <R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
	{
		Assert.assertTrue(null != file);
		Assert.assertTrue(null != context);
		Assert.assertTrue(maxSizeInBytes > 0L);
		Assert.assertTrue(null != decoder);
		
		if (_pinCache.isPinned(file))
		{
			_logger.logVerbose("WARNING!  Not expected in cache:  " + file);
		}
		return _scheduler.readDataWithSizeCheck(file, context, maxSizeInBytes, decoder);
	}

	@Override
	public IpfsFile getLastRootElement()
	{
		return _channelData.getLastPublishedIndex(_keyName);
	}

	@Override
	public FutureResolve resolvePublicKey(IpfsKey keyToResolve)
	{
		Assert.assertTrue(null != keyToResolve);
		return _scheduler.resolvePublicKey(keyToResolve);
	}

	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _scheduler.getSizeInBytes(cid);
	}

	@Override
	public ConcurrentTransaction openConcurrentTransaction()
	{
		return new ConcurrentTransaction(_scheduler, _pinCache.snapshotPinnedSet());
	}

	@Override
	public IFavouritesReading readableFavouritesCache()
	{
		Assert.assertTrue(null != _favouritesCache);
		return _favouritesCache;
	}

	@Override
	public List<IReadingAccess.HomeUserTuple> readHomeUserData()
	{
		return _channelData.getKeyNames().stream()
				.map((String keyName) -> new IReadingAccess.HomeUserTuple(keyName, _channelData.getPublicKey(keyName), _channelData.getLastPublishedIndex(keyName)))
				.collect(Collectors.toList())
		;
	}

	// ----- Writing methods -----
	@Override
	public FolloweeData writableFolloweeData()
	{
		Assert.assertTrue(null != _readWrite);
		// We will want to write this back.
		_writeFollowIndex = true;
		return _followeeData;
	}

	@Override
	public void writePrefs(PrefsData prefs)
	{
		Assert.assertTrue(null != _readWrite);
		_readWrite.writeGlobalPrefs(prefs);
	}

	@Override
	public IpfsFile uploadAndPin(InputStream dataToSave) throws IpfsConnectionException
	{
		FutureSave save = _scheduler.saveStream(dataToSave);
		IpfsFile hash = save.get();
		_pinCache.addRef(hash);
		return hash;
	}

	@Override
	public IpfsFile uploadIndexAndUpdateTracking(AbstractIndex streamIndex) throws IpfsConnectionException
	{
		Assert.assertTrue(null != _readWrite);
		byte[] serializedIndex;
		try
		{
			serializedIndex = streamIndex.serializeV2();
		}
		catch (SizeConstraintException e)
		{
			// We created this as well-formed so it can't be this large.
			throw Assert.unexpected(e);
		}
		FutureSave save = _scheduler.saveStream(new ByteArrayInputStream(serializedIndex));
		IpfsFile hash = save.get();
		_pinCache.addRef(hash);
		_channelData.setLastPublishedIndex(_keyName, _publicKey, hash);
		_writeChannelData = true;
		return hash;
	}

	@Override
	public FuturePin pin(IpfsFile cid)
	{
		boolean shouldPin = !_pinCache.isPinned(cid);
		_pinCache.addRef(cid);
		FuturePin pin = null;
		if (shouldPin)
		{
			pin = _scheduler.pin(cid);
		}
		else
		{
			// If we decided that this was already pinned, just return an already completed pin.
			pin = new FuturePin(cid);
			pin.success();
		}
		return pin;
	}

	@Override
	public void unpin(IpfsFile cid) throws IpfsConnectionException
	{
		_pinCache.delRef(cid);
		boolean shouldUnpin = !_pinCache.isPinned(cid);
		if (shouldUnpin)
		{
			_scheduler.unpin(cid).get();
		}
	}

	@Override
	public FuturePublish beginIndexPublish(IpfsFile indexRoot)
	{
		return _scheduler.publishIndex(_keyName, _publicKey, indexRoot);
	}

	@Override
	public IExplicitCacheReading readableExplicitCache()
	{
		Assert.assertTrue(null != _explicitCache);
		return _explicitCache;
	}

	@Override
	public boolean isIpfsOnline()
	{
		boolean isOnline;
		try
		{
			// We just want to hit a cheap end-point so use the key list, since that is local-only and requires no special knowledge.
			_sharedConnection.getLocalPublicKeys();
			isOnline = true;
		}
		catch (IpfsConnectionException e)
		{
			// This means that the IPFS daemon is offline.
			isOnline = false;
		}
		return isOnline;
	}

	@Override
	public void close()
	{
		// We always assume the pin cache is being written.
		if (null != _readWrite)
		{
			_readWrite.writeGlobalPinCache(_pinCache);
		}
		if (_writeFollowIndex)
		{
			_readWrite.writeFollowIndex(_followeeData);
		}
		if (_writeChannelData)
		{
			_readWrite.writeLocalIndex(_channelData);
		}
		if (_writeFavouritesCache)
		{
			_readWrite.writeFavouritesCache(_favouritesCache);
		}
		if (_didAccessExplicitCache)
		{
			_readWrite.writeExplicitCache(_explicitCache);
		}
		// The read/write references are the same, when both present, but read-only is always present so close it.
		_readOnly.close();
	}

	@Override
	public void commitTransactionPinCanges(Map<IpfsFile, Integer> changedPinCounts, Set<IpfsFile> falsePins)
	{
		// During stop follow, this can involve lots of unpin operations so we want to complete them asynchronously.
		List<FutureUnpin> unpins = new ArrayList<>();
		// First, walk the map of changed reference counts, adding and removing references where listed.
		for (Map.Entry<IpfsFile, Integer> entry: changedPinCounts.entrySet())
		{
			IpfsFile cid = entry.getKey();
			int countToChange = entry.getValue();
			while (countToChange > 0)
			{
				countToChange -= 1;
				_pinCache.addRef(cid);
			}
			while (countToChange < 0)
			{
				countToChange += 1;
				_pinCache.delRef(cid);
			}
			Assert.assertTrue(0 == countToChange);
			_rationalizeUnpin(unpins, cid);
		}
		// Now, walk the set of false pins, requesting that the network unpin them if they aren't referenced.
		for (IpfsFile pin : falsePins)
		{
			_rationalizeUnpin(unpins, pin);
		}
		// Wait for all the unpins to complete.
		for (FutureUnpin unpin : unpins)
		{
			try
			{
				unpin.future.get();
			}
			catch (IpfsConnectionException e)
			{
				// This is non-fatal but a concern.
				_logger.logError("Failed to unpin " + unpin.cid + ": " + e.getLocalizedMessage());
			}
		}
	}

	@Override
	public ExplicitCacheData writableExplicitCache()
	{
		Assert.assertTrue(null != _explicitCache);
		// Almost all access to the explicit cache will change its state.
		_didAccessExplicitCache = true;
		return _explicitCache;
	}

	@Override
	public void deleteChannelData() throws IpfsConnectionException
	{
		// First, remove these from local tracking.
		_channelData.removeChannel(_keyName);
		_writeChannelData = true;
		
		// Now, remove the key from the node.
		_scheduler.deletePublicKey(_keyName).get();
	}

	@Override
	public FavouritesCacheData writableFavouritesCache()
	{
		Assert.assertTrue(null != _favouritesCache);
		// If we are requesting write access, we will just assume it is written.
		_writeFavouritesCache = true;
		return _favouritesCache;
	}


	private void _rationalizeUnpin(List<FutureUnpin> out_unpins, IpfsFile cid)
	{
		if (!_pinCache.isPinned(cid))
		{
			FutureUnpin unpin = new FutureUnpin(cid, _scheduler.unpin(cid));
			out_unpins.add(unpin);
		}
	}


	private static record FutureUnpin(IpfsFile cid, FutureVoid future) {}
}
