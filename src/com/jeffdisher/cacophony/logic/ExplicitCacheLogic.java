package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
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
 */
public class ExplicitCacheLogic
{
	/**
	 * We will use 1 GB as the size of the explicit cache, since we usually satisfy requests from the local user or
	 * followee cache so this is typically just used for one-offs.
	 */
	private static long MAX_EXPLICIT_CACHE_BYTES = 1_000_000_000L;

	/**
	 * Loads user info for the user with the given public key, reading through to the network to find the info if it
	 * isn't already in the cache.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * NOTE:  The user is still resolved before checking the cache to avoid cases where an old version of the user info
	 * would never be dropped from the cache since it keeps being "found".
	 * 
	 * @param access Write-access to local storage and the network.
	 * @param publicKey The public key of the user.
	 * @return The info for this user (never null).
	 * @throws KeyException The key could not be resolved.
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static ExplicitCacheData.UserInfo loadUserInfo(IWritingAccess access, IpfsKey publicKey) throws KeyException, ProtocolDataException, IpfsConnectionException
	{
		IpfsFile root = access.resolvePublicKey(publicKey).get();
		// This will fail instead of returning null.
		Assert.assertTrue(null != root);
		ExplicitCacheData data = access.writableExplicitCache();
		ExplicitCacheData.UserInfo info = data.getUserInfo(root);
		if (null == info)
		{
			// Find and populate the cache.
			// First, read all of the data to make sure that it is valid.
			ForeignChannelReader reader = new ForeignChannelReader(access, root, false);
			StreamIndex index = reader.loadIndex();
			StreamDescription description = reader.loadDescription();
			// (recommendations is something we don't use but will pin later so we want to know it is valid)
			reader.loadRecommendations();
			// We need to check the user pic, explicitly.
			IpfsFile userPicCid = IpfsFile.fromIpfsCid(description.getPicture());
			long picSize = access.getSizeInBytes(userPicCid).get();
			if (picSize > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
			{
				throw new SizeConstraintException("explicit user pic", picSize, SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES);
			}
			// Now, pin everything and update the cache.
			FuturePin pinIndex = access.pin(root);
			FuturePin pinRecommendations = access.pin(IpfsFile.fromIpfsCid(index.getRecommendations()));
			FuturePin pinDescription = access.pin(IpfsFile.fromIpfsCid(index.getDescription()));
			FuturePin pinUserPic = access.pin(userPicCid);
			pinIndex.get();
			pinRecommendations.get();
			pinDescription.get();
			pinUserPic.get();
			long combinedSizeBytes = access.getSizeInBytes(pinIndex.cid).get()
					+ access.getSizeInBytes(pinRecommendations.cid).get()
					+ access.getSizeInBytes(pinDescription.cid).get()
					+ picSize
			;
			info = data.addUserInfo(pinIndex.cid, pinRecommendations.cid, pinDescription.cid, userPicCid, combinedSizeBytes);
			
			// Purge any overflow.
			_purgeExcess(access, data);
		}
		return info;
	}

	/**
	 * Loads the info describing the StreamRecord with the given recordCid.
	 * If the info was already in the cache, or the network read was a success, this call will mark that entry as most
	 * recently used.
	 * 
	 * @param access Write-access to local storage and the network.
	 * @param recordCid The CID of the record.
	 * @return The info for this StreamRecord (never null).
	 * @throws ProtocolDataException The data found was corrupt.
	 * @throws IpfsConnectionException There was a problem accessing the network (could be a timeout due to not finding
	 * the data).
	 */
	public static ExplicitCacheData.RecordInfo loadRecordInfo(IWritingAccess access, IpfsFile recordCid) throws ProtocolDataException, IpfsConnectionException
	{
		ExplicitCacheData data = access.writableExplicitCache();
		ExplicitCacheData.RecordInfo info = data.getRecordInfo(recordCid);
		if (null == info)
		{
			// Find and populate the cache.
			StreamRecord record = access.loadNotCached(recordCid, "explicit record", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] bytes) -> GlobalData.deserializeRecord(bytes)).get();
			LeafFinder leafFinder = LeafFinder.parseRecord(record);
			IpfsFile thumbnailCid = leafFinder.thumbnail;
			PrefsData prefs = access.readPrefs();
			LeafFinder.VideoLeaf videoLeaf = leafFinder.largestVideoWithLimit(prefs.videoEdgePixelMax);
			IpfsFile videoCid = (null != videoLeaf) ? videoLeaf.cid() : null;
			IpfsFile audioCid = (null != videoLeaf) ? null : leafFinder.audio;
			
			// Now, pin everything and update the cache.
			FuturePin pinRecord = access.pin(recordCid);
			FuturePin pinThumbnail = (null != thumbnailCid)
					? access.pin(thumbnailCid)
					: null
			;
			FuturePin pinVideo = (null != videoCid)
					? access.pin(videoCid)
					: null
			;
			FuturePin pinAudio = (null != audioCid)
					? access.pin(audioCid)
					: null
			;
			// We want to revert all the pins if any of these fail.
			try
			{
				pinRecord.get();
				long combinedSizeBytes = access.getSizeInBytes(recordCid).get();
				if (null != pinThumbnail)
				{
					pinThumbnail.get();
					combinedSizeBytes += access.getSizeInBytes(thumbnailCid).get();
				}
				if (null != pinVideo)
				{
					pinVideo.get();
					combinedSizeBytes += access.getSizeInBytes(videoCid).get();
				}
				if (null != pinAudio)
				{
					pinAudio.get();
					combinedSizeBytes += access.getSizeInBytes(audioCid).get();
				}
				info = data.addStreamRecord(recordCid, thumbnailCid, videoCid, audioCid, combinedSizeBytes);
				
				// Purge any overflow.
				_purgeExcess(access, data);
			}
			catch (IpfsConnectionException e)
			{
				access.unpin(recordCid);
				if (null != thumbnailCid)
				{
					access.unpin(thumbnailCid);
				}
				if (null != videoCid)
				{
					access.unpin(videoCid);
				}
				if (null != audioCid)
				{
					access.unpin(audioCid);
				}
				throw e;
			}
		}
		return info;
	}


	private static void _purgeExcess(IWritingAccess access, ExplicitCacheData data)
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
		}, MAX_EXPLICIT_CACHE_BYTES);
	}
}
