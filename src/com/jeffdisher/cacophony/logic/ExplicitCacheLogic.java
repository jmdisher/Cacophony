package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.Context;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.IExplicitCacheReading;
import com.jeffdisher.cacophony.projection.PrefsData;
import com.jeffdisher.cacophony.scheduler.FuturePin;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * The logic associated with the "explicit cache" used by the system.  "Explicit", in this case means data which needed
 * to be cached because it was explicitly requested, not cached for reasons of availability and load balancing, like
 * followee-related caches.
 * It is structured as a least-recently-used read-through cache under the write lock provided by IWritingAccess.  It
 * tracks data within the local IPFS node which was pinned due to these explicit lookups.  This means that, despite
 * being highly versatile, it is very heavy-weight and has an on-disk representation.
 * Relationship with PinCache:  The explicit cache is used when building the PinCache since it adds references to pinned
 * data on the local node.
 * Relationship with LocalRecordCache and LocalUserInfoCache:  This exists as a peer to them, in the stack.  While they
 * represent fast-path look-ups for data known for structural reasons (home user data or followee data), this represents
 * a slower path for actual management of data cached for other reasons.  This means that users of the cache should
 * check those other caches first, as they are fast ephemeral projections.  Accessing this cache is potentially much
 * slower (read-through, so it may do network access, plus a read updates the LRU state meaning it always needs to
 * write-back to disk).
 * Since those other ephemeral caches should be preferentially used, there will be relatively little overlap between
 * this and those other caches.  An example where they may overlap is if this cache is used to view a post which results
 * in a decision to follow the user, thus putting the cached entry in the followee cache.  In that case, later calls to
 * find it will resolve it in those fast-path caches hitting before this cache is checked, allowing it to age out and be
 * purged.
 * Note that this implementation will internally request whatever kind of data locking access is required for what it
 * needs to accomplish and will avoid doing heavy network operations under lock, relying on transactions for those cases
 * where it would need to write-back storage due to changes it has made to what is pinned on the local node.
 */
public class ExplicitCacheLogic
{
	/**
	 * Loads user info for the user with the given public key, reading through to the network to find the info if it
	 * isn't already in the cache.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * NOTE:  The user is still resolved before checking the cache to avoid cases where an old version of the user info
	 * would never be dropped from the cache since it keeps being "found".
	 * 
	 * @param context The context.
	 * @param publicKey The public key of the user.
	 * @return The info for this user (never null).
	 * @throws KeyException The key could not be resolved.
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static ExplicitCacheData.UserInfo loadUserInfo(Context context, IpfsKey publicKey) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		Assert.assertTrue(null != context);
		Assert.assertTrue(null != publicKey);
		
		IpfsFile root;
		ExplicitCacheData.UserInfo info;
		ConcurrentTransaction transaction = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			root = access.resolvePublicKey(publicKey).get();
			// This will fail instead of returning null.
			Assert.assertTrue(null != root);
			IExplicitCacheReading data = access.readableExplicitCache();
			info = data.getUserInfo(root);
			if (null == info)
			{
				transaction = access.openConcurrentTransaction();
			}
		}
		
		if (null != transaction)
		{
			Assert.assertTrue(null == info);
			ExplicitCacheData.UserInfo potential;
			try
			{
				potential = _loadUserInfo(transaction, root);
			}
			catch (ProtocolDataException | IpfsConnectionException e)
			{
				try (IWritingAccess access = StandardAccess.writeAccess(context))
				{
					ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
					transaction.rollback(resolver);
				}
				throw e;
			}
			try (IWritingAccess access = StandardAccess.writeAccess(context))
			{
				ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
				ExplicitCacheData data = access.writableExplicitCache();
				info = data.getUserInfo(root);
				if (null == info)
				{
					// Add this to the structure, creating the official result.
					info = data.addUserInfo(potential.indexCid(), potential.recommendationsCid(), potential.descriptionCid(), potential.userPicCid(), potential.combinedSizeBytes());
					
					// Commit the transaction.
					transaction.commit(resolver);
					
					// Purge any overflow.
					PrefsData prefs = access.readPrefs();
					_purgeExcess(access, data, prefs);
				}
				else
				{
					// We will just use this one so rollback the transaction as its network operations will be redundant.
					transaction.rollback(resolver);
				}
			}
		}
		Assert.assertTrue(null != info);
		return info;
	}

	/**
	 * Loads the info describing the StreamRecord with the given recordCid.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * 
	 * @param context The context.
	 * @param recordCid The CID of the record.
	 * @return The info for this StreamRecord (never null).
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static CachedRecordInfo loadRecordInfo(Context context, IpfsFile recordCid) throws ProtocolDataException, IpfsConnectionException
	{
		Assert.assertTrue(null != context);
		Assert.assertTrue(null != recordCid);
		
		CachedRecordInfo info;
		int videoEdgePixelMax = 0;
		ConcurrentTransaction transaction = null;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			info = data.getRecordInfo(recordCid);
			if (null == info)
			{
				transaction = access.openConcurrentTransaction();
				videoEdgePixelMax = access.readPrefs().videoEdgePixelMax;
			}
		}
		
		if (null != transaction)
		{
			Assert.assertTrue(null == info);
			Assert.assertTrue(videoEdgePixelMax > 0);
			CachedRecordInfo potential;
			try
			{
				potential = _loadRecordInfo(transaction, videoEdgePixelMax, recordCid);
			}
			catch (ProtocolDataException | IpfsConnectionException e)
			{
				try (IWritingAccess access = StandardAccess.writeAccess(context))
				{
					ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
					transaction.rollback(resolver);
				}
				throw e;
			}
			try (IWritingAccess access = StandardAccess.writeAccess(context))
			{
				ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
				ExplicitCacheData data = access.writableExplicitCache();
				info = data.getRecordInfo(recordCid);
				if (null == info)
				{
					// Add this to the structure.
					data.addStreamRecord(recordCid, potential);
					info = potential;
					
					// Commit the transaction.
					transaction.commit(resolver);
					
					// Purge any overflow.
					PrefsData prefs = access.readPrefs();
					_purgeExcess(access, data, prefs);
				}
				else
				{
					// We will just use this one so rollback the transaction as its network operations will be redundant.
					transaction.rollback(resolver);
				}
			}
		}
		Assert.assertTrue(null != info);
		return info;
	}

	/**
	 * Just a helper to read the total size from ExplicitCacheData.
	 * 
	 * @param context The context.
	 * @return The total size of the explicitly cached data, in bytes.
	 */
	public static long getExplicitCacheSize(Context context)
	{
		Assert.assertTrue(null != context);
		
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			return data.getCacheSizeBytes();
		}
	}

	/**
	 * Returns the record with the given recordCid, returning null if the explicit cache doesn't have information about
	 * it.
	 * NOTE:  Will NOT load from the network.
	 * 
	 * @param context The context.
	 * @param recordCid The CID of the record.
	 * @return The info for this StreamRecord (null if unknown).
	 */
	public static CachedRecordInfo getExistingRecordInfo(Context context, IpfsFile recordCid)
	{
		Assert.assertTrue(null != context);
		
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IExplicitCacheReading data = access.readableExplicitCache();
			return data.getRecordInfo(recordCid);
		}
	}


	private static void _purgeExcess(IWritingAccess access, ExplicitCacheData data, PrefsData prefs)
	{
		data.purgeCacheToSize((IpfsFile evict) -> {
			try
			{
				access.unpin(evict);
			}
			catch (IpfsConnectionException e)
			{
				// This is just a local contact problem so just log it.
				System.err.println("WARNING:  Failure in unpin, will need to be removed manually: " + evict);
			}
		}, prefs.explicitCacheTargetBytes);
	}

	private static ExplicitCacheData.UserInfo _loadUserInfo(ConcurrentTransaction transaction, IpfsFile root) throws ProtocolDataException, IpfsConnectionException
	{
		// First, read all of the data to make sure that it is valid.
		ForeignChannelReader reader = new ForeignChannelReader(transaction, root, false);
		AbstractIndex index = reader.loadIndex();
		AbstractDescription description = reader.loadDescription();
		// (recommendations is something we don't use but will pin later so we want to know it is valid)
		reader.loadRecommendations();
		// We need to check the user pic, explicitly.
		IpfsFile userPicCid = description.getPicCid();
		// In V2, the user pic is optional.
		long picSize = (null != userPicCid)
				? transaction.getSizeInBytes(userPicCid).get()
				: 0L
		;
		if (picSize > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
		{
			throw new SizeConstraintException("explicit user pic", picSize, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
		}
		// Now, pin everything and update the cache.
		FuturePin pinIndex = transaction.pin(root);
		FuturePin pinRecommendations = transaction.pin(index.recommendationsCid);
		FuturePin pinDescription = transaction.pin(index.descriptionCid);
		if (null != userPicCid)
		{
			transaction.pin(userPicCid).get();
		}
		pinIndex.get();
		pinRecommendations.get();
		pinDescription.get();
		long combinedSizeBytes = transaction.getSizeInBytes(pinIndex.cid).get()
				+ transaction.getSizeInBytes(pinRecommendations.cid).get()
				+ transaction.getSizeInBytes(pinDescription.cid).get()
				+ picSize
		;
		return new ExplicitCacheData.UserInfo(pinIndex.cid, pinRecommendations.cid, pinDescription.cid, userPicCid, combinedSizeBytes);
	}

	private static CachedRecordInfo _loadRecordInfo(ConcurrentTransaction transaction, int videoEdgePixelMax, IpfsFile recordCid) throws ProtocolDataException, IpfsConnectionException
	{
		CachedRecordInfo info = CommonRecordPinning.loadAndPinRecord(transaction, videoEdgePixelMax, recordCid);
		// This is never null - throws on error.
		Assert.assertTrue(null != info);
		return info;
	}
}
