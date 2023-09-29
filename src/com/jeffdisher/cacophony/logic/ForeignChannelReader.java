package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IBasicNetworkOps;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.scheduler.ICommonFutureRead;
import com.jeffdisher.cacophony.scheduler.SyntheticRead;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Contains the logic for reading channel meta-data, given an initial root CID and whether or not it is expected to be
 * cached.
 * This helper class only exists since this is a pretty common idiom and the boiler-plate to implement it is a complete
 * duplicate.
 * Note that the size exceptions can only be thrown if this is a non-cached case.
 */
public class ForeignChannelReader
{
	private final IBasicNetworkOps _access;
	private final IpfsFile _root;
	private final boolean _isCached;

	private AbstractIndex _index;
	private AbstractRecommendations _recommendations;
	private AbstractRecords _records;
	private AbstractDescription _description;

	/**
	 * Creates the new reader.
	 * 
	 * @param access The access object (must remain valid while this is being used).
	 * @param root The CID of the root StreamIndex element.
	 * @param isCached True if the cache-checking helper should be used, false if we assume it isn't in local cache.
	 */
	public ForeignChannelReader(IBasicNetworkOps access, IpfsFile root, boolean isCached)
	{
		_access = access;
		_root = root;
		_isCached = isCached;
	}

	/**
	 * Lazily loads the StreamIndex and returns it.
	 * 
	 * @return The StreamIndex (never null).
	 * @throws ProtocolDataException The data was too big couldn't be parsed.
	 * @throws IpfsConnectionException There was an error (or timeout) contacting the server.
	 */
	public AbstractIndex loadIndex() throws ProtocolDataException, IpfsConnectionException 
	{
		return _getIndex();
	}

	/**
	 * Lazily loads the StreamRecommendations and returns it, loading the StreamIndex to do so if not already loaded.
	 * 
	 * @return The StreamRecommendations (never null).
	 * @throws ProtocolDataException The data was too big couldn't be parsed.
	 * @throws IpfsConnectionException There was an error (or timeout) contacting the server.
	 */
	public AbstractRecommendations loadRecommendations() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _recommendations)
		{
			IpfsFile cid = _getIndex().recommendationsCid;
			_recommendations = _loadHelper(cid, "recommendations", AbstractRecommendations.SIZE_LIMIT_BYTES, AbstractRecommendations.DESERIALIZER).get();
		}
		return _recommendations;
	}

	/**
	 * Lazily loads the AbstractRecords and returns it, loading the StreamIndex to do so if not already loaded.
	 * 
	 * @return The AbstractRecords (never null).
	 * @throws ProtocolDataException The data was too big couldn't be parsed.
	 * @throws IpfsConnectionException There was an error (or timeout) contacting the server.
	 */
	public AbstractRecords loadRecords() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _records)
		{
			IpfsFile cid = _getIndex().recordsCid;
			_records = _loadHelper(cid, "records", AbstractRecords.SIZE_LIMIT_BYTES, AbstractRecords.DESERIALIZER).get();
		}
		return _records;
	}

	/**
	 * Lazily loads the StreamDescription and returns it, loading the StreamIndex to do so if not already loaded.
	 * 
	 * @return The StreamDescription (never null).
	 * @throws ProtocolDataException The data was too big couldn't be parsed.
	 * @throws IpfsConnectionException There was an error (or timeout) contacting the server.
	 */
	public AbstractDescription loadDescription() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _description)
		{
			IpfsFile cid = _getIndex().descriptionCid;
			_description = _loadHelper(cid, "description", SizeLimits.MAX_DESCRIPTION_SIZE_BYTES, AbstractDescription.DESERIALIZER).get();
		}
		return _description;
	}


	private AbstractIndex _getIndex() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _index)
		{
			_index = _loadHelper(_root, "index", AbstractIndex.SIZE_LIMIT_BYTES, AbstractIndex.DESERIALIZER).get();
		}
		return _index;
	}

	private <T> ICommonFutureRead<T> _loadHelper(IpfsFile file, String context, long maxSizeInBytes, DataDeserializer<T> decoder)
	{
		return _isCached
				? new SyntheticRead<>(_access.loadCached(file, decoder))
				: _access.loadNotCached(file, context, maxSizeInBytes, decoder)
		;
	}
}
