package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.FutureSize;
import com.jeffdisher.cacophony.scheduler.FutureSizedRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Common helper to "start" following a new followee.
 * This is a single entry-point, executed synchronously, which resolves and fetches the data of a given followee key,
 * other than the actual stream records.
 * Instead, it synthesizes a new root with an empty records element so that the followee can be described, now, but must
 * be synchronized, later.
 */
public class SimpleFolloweeStarter
{
	/**
	 * Attempts to start following the user with the given followeeKey.  Takes care of resolving the key, fetching the
	 * core meta-data (except for StreamRecords), synthesizing and uploading new StreamRecords and StreamIndex
	 * structures, and then returning the new "fake" root pointing at this "fake" StreamIndex.
	 * Note that the caller is responsible for actually recording this in the followee data structure.
	 * 
	 * @param logger A consumer which logs the messages it is given.
	 * @param access The writing access to the network.
	 * @param cacheUpdater For updating caches.
	 * @param followeeKey The public key of the followee to look up.
	 * @return The root of the new "fake" meta-data tree for this user (never null).
	 * @throws IpfsConnectionException There was a network error when reading/writing the data.
	 * @throws ProtocolDataException The target user's data was malformed or broke protocol.
	 * @throws KeyException The user couldn't be found.
	 */
	public static IpfsFile startFollowingWithEmptyRecords(Consumer<String> logger, IWritingAccess access, CacheUpdater cacheUpdater, IpfsKey followeeKey) throws IpfsConnectionException, ProtocolDataException, KeyException
	{
		StartSupport support = new StartSupport(logger, access, cacheUpdater, followeeKey);
		
		// Run the operation, bearing in mind that we need to handle errors, internally.
		IpfsFile hackedRoot;
		try
		{
			IpfsFile actualRoot = access.resolvePublicKey(followeeKey).get();
			// Throws KeyException on failure.
			Assert.assertTrue(null != actualRoot);
			hackedRoot = FolloweeRefreshLogic.startFollowing(support, actualRoot);
			// This should only fail with an exception.
			Assert.assertTrue(null != hackedRoot);
		}
		catch (IpfsConnectionException e)
		{
			logger.accept("Network error contacting IPFS node:  " + e.getLocalizedMessage());
			throw e;
		}
		catch (KeyException e)
		{
			logger.accept("Key resolution failure in start following:  " + e.getLocalizedMessage());
			throw e;
		}
		catch (SizeConstraintException e)
		{
			logger.accept("Followee meta-data element too big (probably wrong file published):  " + e.getLocalizedMessage());
			throw e;
		}
		catch (FailedDeserializationException e)
		{
			logger.accept("Followee data appears to be corrupt:  " + e.getLocalizedMessage());
			throw e;
		}
		return hackedRoot;
	}


	private static class StartSupport implements FolloweeRefreshLogic.IStartSupport
	{
		private final Consumer<String> _logger;
		private final IWritingAccess _access;
		private final CacheUpdater _cacheUpdater;
		private final IpfsKey _followeeKey;
		
		public StartSupport(Consumer<String> logger, IWritingAccess access, CacheUpdater cacheUpdater, IpfsKey followeeKey)
		{
			_logger = logger;
			_access = access;
			_cacheUpdater = cacheUpdater;
			_followeeKey = followeeKey;
		}
		
		@Override
		public void logMessage(String message)
		{
			_logger.accept(message);
		}
		@Override
		public FutureSize getSizeInBytes(IpfsFile cid)
		{
			return _access.getSizeInBytes(cid);
		}
		@Override
		public void followeeDescriptionNewOrUpdated(AbstractDescription description)
		{
			_cacheUpdater.addedFollowee(_followeeKey, description);
		}
		@Override
		public FuturePin addMetaDataToFollowCache(IpfsFile cid)
		{
			return _access.pin(cid);
		}
		@Override
		public <R> FutureRead<R> loadCached(IpfsFile file, DataDeserializer<R> decoder)
		{
			return _access.loadCached(file, decoder);
		}
		@Override
		public <R> FutureSizedRead<R> loadNotCached(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<R> decoder)
		{
			return _access.loadNotCached(file, context, maxSizeInBytes, decoder);
		}
		@Override
		public IpfsFile uploadNewData(byte[] data) throws IpfsConnectionException
		{
			return _access.uploadAndPin(new ByteArrayInputStream(data));
		}
	}
}
