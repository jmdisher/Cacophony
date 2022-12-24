package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final IEnvironment _environment;
	private final ConcurrentTransaction _transaction;
	private final IpfsKey _followeeKey;
	private final Map<IpfsFile, FollowingCacheElement> _cachedEntriesForFollowee;
	
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;

	public StandardRefreshSupport(IEnvironment environment, ConcurrentTransaction transaction, IpfsKey followeeKey, Map<IpfsFile, FollowingCacheElement> cachedEntriesForFollowee)
	{
		_environment = environment;
		_transaction = transaction;
		_followeeKey = followeeKey;
		_cachedEntriesForFollowee = cachedEntriesForFollowee;
		
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
	}

	public void commitFolloweeChanges(IFolloweeWriting followees)
	{
		for (IpfsFile cid : _elementsToRemoveFromCache)
		{
			followees.removeElement(_followeeKey, cid);
		}
		for (FollowingCacheElement elt : _elementsToAddToCache)
		{
			followees.addElement(_followeeKey, elt);
		}
	}

	@Override
	public void logMessage(String message)
	{
		_environment.logToConsole(message);
	}
	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _transaction.getSizeInBytes(cid);
	}
	@Override
	public FuturePin addMetaDataToFollowCache(IpfsFile cid)
	{
		return _transaction.pin(cid);
	}
	@Override
	public void deferredRemoveMetaDataFromFollowCache(IpfsFile cid)
	{
		_transaction.unpin(cid);
	}
	@Override
	public FuturePin addFileToFollowCache(IpfsFile cid)
	{
		return _transaction.pin(cid);
	}
	@Override
	public void deferredRemoveFileFromFollowCache(IpfsFile cid)
	{
		_transaction.unpin(cid);
	}
	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		return _transaction.loadCached(file, decoder);
	}
	@Override
	public IpfsFile getImageForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _cachedEntriesForFollowee.get(elementHash);
		return (null != elt)
				? elt.imageHash()
				: null
		;
	}
	@Override
	public IpfsFile getLeafForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _cachedEntriesForFollowee.get(elementHash);
		return (null != elt)
				? elt.leafHash()
				: null
		;
	}
	@Override
	public void addElementToCache(IpfsFile elementHash, IpfsFile imageHash, IpfsFile leafHash, long combinedSizeBytes)
	{
		_elementsToAddToCache.add(new FollowingCacheElement(elementHash, imageHash, leafHash, combinedSizeBytes));
	}
	@Override
	public void removeElementFromCache(IpfsFile elementHash)
	{
		_elementsToRemoveFromCache.add(elementHash);
	}
}
