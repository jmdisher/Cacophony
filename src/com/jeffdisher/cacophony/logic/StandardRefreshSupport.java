package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final IEnvironment _environment;
	private final IWritingAccess _access;
	private final List<FollowingCacheElement> _initialElements;
	private final Map<IpfsFile, FollowingCacheElement> _initialElementsForLookup;
	
	private final List<IpfsFile> _deferredMetaDataUnpins;
	private final List<IpfsFile> _deferredFileUnpins;
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;

	public StandardRefreshSupport(IEnvironment environment, IWritingAccess access, FollowingCacheElement[] initialElements)
	{
		_environment = environment;
		_access = access;
		_initialElements = List.of(initialElements);
		_initialElementsForLookup = _initialElements.stream().collect(Collectors.toMap((FollowingCacheElement elt) -> elt.elementHash(), (FollowingCacheElement elt) -> elt));
		
		_deferredMetaDataUnpins = new ArrayList<>();
		_deferredFileUnpins = new ArrayList<>();
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
	}

	public FollowingCacheElement[] applyAndReturnElements()
	{
		// We can now commit to unpinning anything which we wanted to drop.
		for (IpfsFile cid : _deferredMetaDataUnpins)
		{
			try
			{
				_access.unpin(cid);
			}
			catch (IpfsConnectionException e)
			{
				_environment.logError("Failed to unpin meta-data " + cid + ": " + e.getLocalizedMessage());
			}
		}
		for (IpfsFile cid : _deferredFileUnpins)
		{
			try
			{
				_access.unpin(cid);
			}
			catch (IpfsConnectionException e)
			{
				_environment.logError("Failed to unpin file " + cid + ": " + e.getLocalizedMessage());
			}
		}
		
		// Now, compose the list of elements we are left with (we want to keep these in order and only append to the end, though).
		List<FollowingCacheElement> list = new ArrayList<>();
		for (FollowingCacheElement elt : _initialElements)
		{
			if (!_elementsToRemoveFromCache.contains(elt.elementHash()))
			{
				// We are not removing it, so add it to the list, in-order.
				list.add(elt);
			}
		}
		for (FollowingCacheElement elt : _elementsToAddToCache)
		{
			// This is new, so add it to the end.
			list.add(elt);
		}
		return list.toArray((int size) -> new FollowingCacheElement[size]);
	}

	@Override
	public void logMessage(String message)
	{
		_environment.logToConsole(message);
	}
	@Override
	public FutureSize getSizeInBytes(IpfsFile cid)
	{
		return _access.getSizeInBytes(cid);
	}
	@Override
	public FuturePin addMetaDataToFollowCache(IpfsFile cid)
	{
		return _access.pin(cid);
	}
	@Override
	public void deferredRemoveMetaDataFromFollowCache(IpfsFile cid)
	{
		_deferredMetaDataUnpins.add(cid);
	}
	@Override
	public FuturePin addFileToFollowCache(IpfsFile cid)
	{
		return _access.pin(cid);
	}
	@Override
	public void deferredRemoveFileFromFollowCache(IpfsFile cid)
	{
		_deferredFileUnpins.add(cid);
	}
	@Override
	public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
	{
		return _access.loadCached(file, decoder);
	}
	@Override
	public IpfsFile getImageForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _initialElementsForLookup.get(elementHash);
		return (null != elt)
				? elt.imageHash()
				: null
		;
	}
	@Override
	public IpfsFile getLeafForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _initialElementsForLookup.get(elementHash);
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
