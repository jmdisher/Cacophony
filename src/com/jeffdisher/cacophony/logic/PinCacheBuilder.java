package com.jeffdisher.cacophony.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jeffdisher.breakwater.utilities.Assert;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.PinCacheData;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;


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
		List<FutureRead<StreamRecord>> futures = _commonStart(lastRootElement, true);
		
		// For the home user, we want to just pin all the leaf elements, since we host them all.
		for (FutureRead<StreamRecord> future : futures)
		{
			StreamRecord record = future.get();
			for (DataElement elt : record.getElements().getElement())
			{
				_pin(IpfsFile.fromIpfsCid(elt.getCid()));
			}
		}
	}

	private void _addFollowee(IpfsFile lastFetchedRootForFollowee, Map<IpfsFile, FollowingCacheElement> snapshotAllElementsForFollowee) throws FailedDeserializationException, SizeConstraintException, IpfsConnectionException
	{
		_commonStart(lastFetchedRootForFollowee, false);
		
		// For the followee, we only see the filtered set and only pin what we recorded in the element (meta-data is always pinned, of course).
		for (FollowingCacheElement elt : snapshotAllElementsForFollowee.values())
		{
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

	private List<FutureRead<StreamRecord>> _commonStart(IpfsFile indexFile, boolean fetchRecords) throws FailedDeserializationException, IpfsConnectionException
	{
		_pin(indexFile);
		// We know that everything reachable from a root is valid, here, since it is either our own data or a followee root which we already validated.
		StreamIndex index = _scheduler.readData(indexFile, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		IpfsFile descriptionFile = IpfsFile.fromIpfsCid(index.getDescription());
		_pin(descriptionFile);
		IpfsFile recommendationsFile = IpfsFile.fromIpfsCid(index.getRecommendations());
		_pin(recommendationsFile);
		IpfsFile recordsFile = IpfsFile.fromIpfsCid(index.getRecords());
		_pin(recordsFile);
		
		StreamDescription description = _scheduler.readData(descriptionFile, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		_pin(IpfsFile.fromIpfsCid(description.getPicture()));
		StreamRecords records = _scheduler.readData(recordsFile, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		
		List<FutureRead<StreamRecord>> futures = fetchRecords ? new ArrayList<>() : null;
		for (String rawRecord : records.getRecord())
		{
			IpfsFile recordFile = IpfsFile.fromIpfsCid(rawRecord);
			_pin(recordFile);
			if (fetchRecords)
			{
				futures.add(_scheduler.readData(recordFile, (byte[] data) -> GlobalData.deserializeRecord(data)));
			}
		}
		return futures;
	}

	private void _pin(IpfsFile cid)
	{
		_cache.addRef(cid);
	}
}
