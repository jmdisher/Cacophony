package com.jeffdisher.cacophony.logic;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
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
	private final IReadingAccess _access;
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
	public ForeignChannelReader(IReadingAccess access, IpfsFile root, boolean isCached)
	{
		_access = access;
		_root = root;
		_isCached = isCached;
	}

	public StreamRecommendations loadRecommendations() throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		if (null == _recommendations)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getRecommendations());
			_checkSize(cid, "recommendations", SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES);
			_recommendations = _loadHelper(cid, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		}
		return _recommendations;
	}

	public StreamRecords loadRecords() throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		if (null == _records)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getRecords());
			_checkSize(cid, "records", SizeLimits.MAX_RECORD_SIZE_BYTES);
			_records = _loadHelper(cid, (byte[] data) -> GlobalData.deserializeRecords(data)).get();
		}
		return _records;
	}

	public StreamDescription loadDescription() throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		if (null == _description)
		{
			IpfsFile cid = IpfsFile.fromIpfsCid(_getIndex().getDescription());
			_checkSize(cid, "description", SizeLimits.MAX_DESCRIPTION_SIZE_BYTES);
			_description = _loadHelper(cid, (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		}
		return _description;
	}


	private StreamIndex _getIndex() throws IpfsConnectionException, FailedDeserializationException, SizeConstraintException
	{
		if (null == _index)
		{
			_checkSize(_root, "index", SizeLimits.MAX_INDEX_SIZE_BYTES);
			_index = _loadHelper(_root, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		}
		return _index;
	}

	private <T> FutureRead<T> _loadHelper(IpfsFile file, DataDeserializer<T> decoder)
	{
		return _isCached
				? _access.loadCached(file, decoder)
				: _access.loadNotCached(file, decoder)
		;
	}

	private void _checkSize(IpfsFile cid, String type, long sizeLimit) throws IpfsConnectionException, SizeConstraintException
	{
		// We assume these are correctly-sized if they are already cached.
		if (!_isCached)
		{
			long size = _access.getSizeInBytes(cid).get();
			if (size > sizeLimit)
			{
				throw new SizeConstraintException(type, size, sizeLimit);
			}
		}
	}
}
