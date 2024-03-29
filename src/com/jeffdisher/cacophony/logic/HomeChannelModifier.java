package com.jeffdisher.cacophony.logic;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Contains the common logic for navigating the home channel's on-IPFS data tree and saving it back, post-modification.
 * Note that this should ONLY be used for the home channel.
 * This keeps track of things which need to be unpinned when committed and manages updating the intermediate data
 * structures in the tree and writing back the new root for the channel.
 * Note that, despite the objects being returned by the "load*" calls being mutable, they will only be written-back as
 * changed if they are passed back in via the associated "store*" call.  This can be done with a new instance or
 * modified original instance.
 * NOTE:  Only the directly-updated meta-data is written-back when changed, not anything underneath it (StreamRecord
 * instances, for example).
 */
public class HomeChannelModifier
{
	private final IWritingAccess _access;
	private Tuple<AbstractIndex> _index;
	private Tuple<AbstractRecommendations> _recommendations;
	private Tuple<AbstractRecords> _records;
	private Tuple<AbstractDescription> _description;

	/**
	 * Creates the modifier on top of the given access, using its previously-writing root as its starting-point.
	 * 
	 * @param access Write-access.
	 */
	public HomeChannelModifier(IWritingAccess access)
	{
		_access = access;
	}

	/**
	 * Lazily loads the recommendations.
	 * 
	 * @return The recommendations object.
	 * @throws IpfsConnectionException There was a problem loading the channel data.
	 */
	public AbstractRecommendations loadRecommendations() throws IpfsConnectionException
	{
		if (null == _recommendations)
		{
			IpfsFile cid = _getIndex().recommendationsCid;
			AbstractRecommendations elt;
			try
			{
				elt = _access.loadCached(cid, AbstractRecommendations.DESERIALIZER).get();
			}
			catch (FailedDeserializationException e)
			{
				// This is the home channel so we wrote this.
				throw Assert.unexpected(e);
			}
			_recommendations = new Tuple<>(elt, cid, false);
		}
		return _recommendations.element;
	}

	/**
	 * Updates the recommendations to the new object, marking it for write-back on commit.
	 * 
	 * @param elt The new object.
	 */
	public void storeRecommendations(AbstractRecommendations elt)
	{
		Assert.assertTrue(null != _recommendations);
		_recommendations = new Tuple<>(elt, _recommendations.originalCid, true);
	}

	/**
	 * Lazily loads the records.
	 * 
	 * @return The records object.
	 * @throws IpfsConnectionException There was a problem loading the channel data.
	 */
	public AbstractRecords loadRecords() throws IpfsConnectionException
	{
		if (null == _records)
		{
			IpfsFile cid = _getIndex().recordsCid;
			AbstractRecords elt;
			try
			{
				elt = _access.loadCached(cid, AbstractRecords.DESERIALIZER).get();
			}
			catch (FailedDeserializationException e)
			{
				// This is the home channel so we wrote this.
				throw Assert.unexpected(e);
			}
			_records = new Tuple<>(elt, cid, false);
		}
		return _records.element;
	}

	/**
	 * Updates the records to the new object, marking it for write-back on commit.
	 * 
	 * @param elt The new object.
	 */
	public void storeRecords(AbstractRecords elt)
	{
		Assert.assertTrue(null != _records);
		_records = new Tuple<>(elt, _records.originalCid, true);
	}

	/**
	 * Lazily loads the description.
	 * 
	 * @return The description object.
	 * @throws IpfsConnectionException There was a problem loading the channel data.
	 */
	public AbstractDescription loadDescription() throws IpfsConnectionException
	{
		if (null == _description)
		{
			IpfsFile cid = _getIndex().descriptionCid;
			AbstractDescription elt;
			try
			{
				elt = _access.loadCached(cid, AbstractDescription.DESERIALIZER).get();
			}
			catch (FailedDeserializationException e)
			{
				// This is the home channel so we wrote this.
				throw Assert.unexpected(e);
			}
			_description = new Tuple<>(elt, cid, false);
		}
		return _description.element;
	}

	/**
	 * Updates the description to the new object, marking it for write-back on commit.
	 * 
	 * @param elt The new object.
	 */
	public void storeDescription(AbstractDescription elt)
	{
		Assert.assertTrue(null != _description);
		_description = new Tuple<>(elt, _description.originalCid, true);
	}

	/**
	 * Commits the entire data structure, serializing and storing any modified objects (as denoted by calling their
	 * "store*" method).
	 * After storing all the updates, unpins any of the now-stale resources.
	 * Also updates the channel tracking to point to this new root, then returns it.
	 * Note that the caller is responsible for publishing the root.
	 * 
	 * @return The new root of the channel structure (CID of the StreamIndex).
	 * @throws IpfsConnectionException If there was a problem contacting the server.
	 */
	public IpfsFile commitNewRoot() throws IpfsConnectionException
	{
		// For us to get this far, we must have at least loaded something.
		Assert.assertTrue(null != _index);
		AbstractIndex index = _index.element;
		boolean mustWrite = _index.needsUpdate;
		
		// Note that even if these changes are not actually changing the CID, we still want to balance uploadAndPin with unpin.
		List<IpfsFile> toUnpin = new ArrayList<>();
		IpfsFile recommendations;
		IpfsFile records;
		IpfsFile description;
		try
		{
			recommendations = _writeUpdatedRecommendations(toUnpin);
			records = _writeUpdatedRecords(toUnpin);
			description = _writeUpdatedDescription(toUnpin);
		}
		catch (SizeConstraintException e)
		{
			// If we hit this failure, there is either something seriously corrupt or the spec needs to be updated.
			throw Assert.unexpected(e);
		}
		
		if (null != recommendations)
		{
			index.recommendationsCid = recommendations;
			mustWrite = true;
		}
		if (null != records)
		{
			index.recordsCid = records;
			mustWrite = true;
		}
		if (null != description)
		{
			index.descriptionCid = description;
			mustWrite = true;
		}
		
		IpfsFile cid = _index.originalCid;
		if (mustWrite)
		{
			cid = _access.uploadIndexAndUpdateTracking(index);
			toUnpin.add(_index.originalCid);
			_index = null;
		}
		else
		{
			Assert.assertTrue(toUnpin.isEmpty());
		}
		
		// Clean up before returning.
		for (IpfsFile old : toUnpin)
		{
			_access.unpin(old);
		}
		return cid;
	}


	private IpfsFile _writeUpdatedRecommendations(List<IpfsFile> toUnpin) throws IpfsConnectionException, SizeConstraintException
	{
		IpfsFile cid = null;
		if ((null != _recommendations) && _recommendations.needsUpdate)
		{
			byte[] data = _recommendations.element.serializeV2();
			cid = _access.uploadAndPin(new ByteArrayInputStream(data));
			toUnpin.add(_recommendations.originalCid);
			_recommendations = null;
		}
		return cid;
	}

	private IpfsFile _writeUpdatedRecords(List<IpfsFile> toUnpin) throws IpfsConnectionException, SizeConstraintException
	{
		IpfsFile cid = null;
		if ((null != _records) && _records.needsUpdate)
		{
			byte[] data = _records.element.serializeV2();
			cid = _access.uploadAndPin(new ByteArrayInputStream(data));
			toUnpin.add(_records.originalCid);
			_records = null;
		}
		return cid;
	}

	private IpfsFile _writeUpdatedDescription(List<IpfsFile> toUnpin) throws IpfsConnectionException, SizeConstraintException
	{
		IpfsFile cid = null;
		if ((null != _description) && _description.needsUpdate)
		{
			byte[] data = _description.element.serializeV2();
			cid = _access.uploadAndPin(new ByteArrayInputStream(data));
			toUnpin.add(_description.originalCid);
			_description = null;
		}
		return cid;
	}

	private AbstractIndex _getIndex() throws IpfsConnectionException
	{
		if (null == _index)
		{
			IpfsFile root = _access.getLastRootElement();
			AbstractIndex index;
			try
			{
				index = _access.loadCached(root, AbstractIndex.DESERIALIZER).get();
			}
			catch (FailedDeserializationException e)
			{
				// This is the home channel so we wrote this.
				throw Assert.unexpected(e);
			}
			_index = new Tuple<>(index, root, false);
		}
		return _index.element;
	}


	private static record Tuple<T>(T element, IpfsFile originalCid, boolean needsUpdate)
	{}
}
