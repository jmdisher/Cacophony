package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeWriting;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


/**
 * The implementation of IRefreshSupport to be used in the common system.
 */
public class StandardRefreshSupport implements FolloweeRefreshLogic.IRefreshSupport
{
	private final IEnvironment _environment;
	private final IWritingAccess _access;
	private final IFolloweeWriting _followees;
	private final IpfsKey _followeeKey;
	
	private final List<IpfsFile> _deferredMetaDataUnpins;
	private final List<IpfsFile> _deferredFileUnpins;
	private final Set<IpfsFile> _elementsToRemoveFromCache;
	private final List<FollowingCacheElement> _elementsToAddToCache;

	public StandardRefreshSupport(IEnvironment environment, IWritingAccess access, IFolloweeWriting followees, IpfsKey followeeKey)
	{
		_environment = environment;
		_access = access;
		_followees = followees;
		_followeeKey = followeeKey;
		
		_deferredMetaDataUnpins = new ArrayList<>();
		_deferredFileUnpins = new ArrayList<>();
		_elementsToRemoveFromCache = new HashSet<>();
		_elementsToAddToCache = new ArrayList<>();
	}

	public void commitChanges()
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
		
		// Now, write-back our changes to the actual cache.
		for (IpfsFile cid : _elementsToRemoveFromCache)
		{
			_followees.removeElement(_followeeKey, cid);
		}
		for (FollowingCacheElement elt : _elementsToAddToCache)
		{
			_followees.addElement(_followeeKey, elt);
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
		FollowingCacheElement elt = _followees.getElementForFollowee(_followeeKey, elementHash);
		return (null != elt)
				? elt.imageHash()
				: null
		;
	}
	@Override
	public IpfsFile getLeafForCachedElement(IpfsFile elementHash)
	{
		FollowingCacheElement elt = _followees.getElementForFollowee(_followeeKey, elementHash);
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
