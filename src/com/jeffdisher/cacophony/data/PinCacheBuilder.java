package com.jeffdisher.cacophony.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.projection.ExplicitCacheData;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Builds the pin cache by scanning the network roots and followee cache data.
 */
public class PinCacheBuilder
{
	private final INetworkScheduler _scheduler;
	private final PinCacheData _cache;

	/**
	 * Creates a new builder.
	 * 
	 * @param scheduler The scheduler used for requesting network resources.
	 */
	public PinCacheBuilder(INetworkScheduler scheduler)
	{
		_scheduler = scheduler;
		_cache = PinCacheData.createEmpty();
	}

	/**
	 * Pins the data reachable from the given root, interpreting it as the home user.
	 * 
	 * @param root The root element of the home user.
	 */
	public void addHomeUser(IpfsFile root)
	{
		try
		{
			_addHomeUser(root);
		}
		catch (FailedDeserializationException | SizeConstraintException | IpfsConnectionException e)
		{
			// Not expected since this is already pinned.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Pins the data reachable from the given lastFetchedRootForFollowee, using snapshotAllElementsForFollowee to
	 * interpret the data as a followee.
	 * 
	 * @param lastFetchedRootForFollowee The last known good root of data for this followee.
	 * @param snapshotAllElementsForFollowee A description of the data cached for this followee.
	 */
	public void addFollowee(IpfsFile lastFetchedRootForFollowee, Map<IpfsFile, FollowingCacheElement> snapshotAllElementsForFollowee)
	{
		try
		{
			_addFollowee(lastFetchedRootForFollowee, snapshotAllElementsForFollowee);
		}
		catch (FailedDeserializationException | SizeConstraintException | IpfsConnectionException e)
		{
			// Not expected since this is already pinned.
			throw Assert.unexpected(e);
		}
	}

	/**
	 * Called to add favourites to the pin cache.
	 * 
	 * @param explicitCache The explicit cache.
	 */
	public void addFavourites(FavouritesCacheData favourites)
	{
		favourites.walkAllPins((IpfsFile elt) -> _pin(elt));
	}

	/**
	 * Called to add anything explicitly cached to the pin cache.
	 * 
	 * @param explicitCache The explicit cache.
	 */
	public void addExplicitCache(ExplicitCacheData explicitCache)
	{
		explicitCache.walkAllPins((IpfsFile elt) -> _pin(elt));
	}

	/**
	 * Called when the relevant data has been fed into the builder in order to extract the resultant cache.
	 * 
	 * @return The cache which has been built.
	 */
	public PinCacheData finish()
	{
		return _cache;
	}


	private void _addHomeUser(IpfsFile lastRootElement) throws FailedDeserializationException, SizeConstraintException, IpfsConnectionException
	{
		IpfsFile recordsFile = _pinMetaDataAndDescription(lastRootElement);
		AbstractRecords records = _scheduler.readData(recordsFile, AbstractRecords.DESERIALIZER).get();
		List<FutureRead<AbstractRecord>> futures = new ArrayList<>();
		for (IpfsFile recordFile : records.getRecordList())
		{
			_pin(recordFile);
			futures.add(_scheduler.readData(recordFile, AbstractRecord.DESERIALIZER));
		}
		
		// For the home user, we want to just pin all the leaf elements, since we host them all.
		for (FutureRead<AbstractRecord> future : futures)
		{
			AbstractRecord record = future.get();
			if (null != record.getThumbnailCid())
			{
				_pin(record.getThumbnailCid());
			}
			List<AbstractRecord.Leaf> leaves = record.getVideoExtension();
			if (null != leaves)
			{
				for (AbstractRecord.Leaf leaf : leaves)
				{
					_pin(leaf.cid());
				}
			}
		}
	}

	private void _addFollowee(IpfsFile lastFetchedRootForFollowee, Map<IpfsFile, FollowingCacheElement> snapshotAllElementsForFollowee) throws FailedDeserializationException, SizeConstraintException, IpfsConnectionException
	{
		_pinMetaDataAndDescription(lastFetchedRootForFollowee);
		
		// For the followee, we only see the filtered set and only pin what we recorded in the element (meta-data is always pinned, of course).
		for (FollowingCacheElement elt : snapshotAllElementsForFollowee.values())
		{
			// Every element has a meta-data entry but image and leaf are optional.
			_pin(elt.elementHash());
			IpfsFile image = elt.imageHash();
			if (null != image)
			{
				_pin(image);
			}
			IpfsFile leaf = elt.leafHash();
			if (null != leaf)
			{
				_pin(leaf);
			}
		}
	}

	// Returns records CID.
	private IpfsFile _pinMetaDataAndDescription(IpfsFile indexFile) throws FailedDeserializationException, IpfsConnectionException
	{
		_pin(indexFile);
		// We know that everything reachable from a root is valid, here, since it is either our own data or a followee root which we already validated.
		AbstractIndex index = _scheduler.readData(indexFile, AbstractIndex.DESERIALIZER).get();
		IpfsFile descriptionFile = index.descriptionCid;
		_pin(descriptionFile);
		IpfsFile recommendationsFile = index.recommendationsCid;
		_pin(recommendationsFile);
		IpfsFile recordsFile = index.recordsCid;
		_pin(recordsFile);
		
		AbstractDescription description = _scheduler.readData(descriptionFile, AbstractDescription.DESERIALIZER).get();
		if (null != description.getPicCid())
		{
			_pin(description.getPicCid());
		}
		return recordsFile;
	}

	private void _pin(IpfsFile cid)
	{
		Assert.assertTrue(null != cid);
		_cache.addRef(cid);
	}
}
