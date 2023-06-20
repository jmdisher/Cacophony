package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IBasicNetworkOps;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.ICommonFutureRead;
import com.jeffdisher.cacophony.scheduler.SyntheticRead;
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

	private StreamIndex _index;
	private StreamRecommendations _recommendations;
	private StreamRecords _records;
	private StreamDescription _description;

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
	public StreamIndex loadIndex() throws ProtocolDataException, IpfsConnectionException 
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
	public StreamRecommendations loadRecommendations() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _recommendations)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getRecommendations());
			_recommendations = _loadHelper(cid, "recommendations", SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		}
		return _recommendations;
	}

	/**
	 * Lazily loads the StreamRecords and returns it, loading the StreamIndex to do so if not already loaded.
	 * 
	 * @return The StreamRecords (never null).
	 * @throws ProtocolDataException The data was too big couldn't be parsed.
	 * @throws IpfsConnectionException There was an error (or timeout) contacting the server.
	 */
	public StreamRecords loadRecords() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _records)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getRecords());
			_records = _loadHelper(cid, "records", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
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
	public StreamDescription loadDescription() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _description)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getDescription());
			_description = _loadHelper(cid, "description", SizeLimits.MAX_DESCRIPTION_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		}
		return _description;
	}


	private StreamIndex _getIndex() throws ProtocolDataException, IpfsConnectionException
	{
		if (null == _index)
		{
			_index = _loadHelper(_root, "index", SizeLimits.MAX_INDEX_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
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
