package com.jeffdisher.cacophony.projection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jeffdisher.cacophony.data.local.v4.OpcodeCodec;
import com.jeffdisher.cacophony.data.local.v4.Opcode_FavouriteStreamRecord;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * The favourites cache is similar to the explicit cache in that it stores data the same way and is consulted in the
 * same read path.
 * However, it differs in a few key ways:
 * -entries are explicitly added/removed from the favourites cache while the explicit cache is an automatic read-through
 * -there is no limit to the size of the favourites cache while the explicit cache is bounded
 * -the favourites cache is only associated with StreamRecord elements, not user info
 */
public class FavouritesCacheData implements IFavouritesReading
{
	private final Map<IpfsFile, CachedRecordInfo> _recordInfo;
	private final List<IpfsFile> _additionOrder;

	/**
	 * Creates the empty cache.
	 */
	public FavouritesCacheData()
	{
		_recordInfo = new HashMap<>();
		_additionOrder = new ArrayList<>();
	}

	/**
	 * Serializes the contents of the receiver into the given writer.
	 * 
	 * @param writer The writer which will consume the opcodes.
	 * @throws IOException The writer encountered an error.
	 */
	public void serializeToOpcodeWriter(OpcodeCodec.Writer writer) throws IOException
	{
		for (IpfsFile key : _additionOrder)
		{
			CachedRecordInfo info = _recordInfo.get(key);
			writer.writeOpcode(new Opcode_FavouriteStreamRecord(key, info.thumbnailCid(), info.videoCid(), info.audioCid(), info.combinedSizeBytes()));
		}
	}

	/**
	 * Calls out to the given pin Consumer for each pinned element from the explicit cache.  Note that this will call
	 * the consumer each time a given element is pinned, even if it is pinned multiple times.
	 * 
	 * @param pin The consumer to be called for each pinned element, each time they are pinned.
	 */
	public void walkAllPins(Consumer<IpfsFile> pin)
	{
		for(CachedRecordInfo info : _recordInfo.values())
		{
			pin.accept(info.streamCid());
			if (null != info.thumbnailCid())
			{
				pin.accept(info.thumbnailCid());
			}
			if (null != info.videoCid())
			{
				pin.accept(info.videoCid());
			}
			if (null != info.audioCid())
			{
				pin.accept(info.audioCid());
			}
		}
	}

	/**
	 * Adds a new StreamRecord to the cache and marks it as most recently used.  Note that thumbnail, video, and audio
	 * are all optional but at most one of video and audio can be present.
	 * NOTE:  A record with the given streamCid cannot already be in the cache.
	 * 
	 * @param streamCid The CID of the StreamRecord.
	 * @param thumbnailCid The CID of the thumbnail selected from this StreamRecord.
	 * @param videoCid The CID of the video element selected from this StreamRecord.
	 * @param audioCid The CID of the audio element selected from this StreamRecord.
	 * @param combinedSizeBytes The combined size, in bytes, of all of the above elements.
	 */
	public void addStreamRecord(IpfsFile streamCid, CachedRecordInfo info)
	{
		Assert.assertTrue(!_recordInfo.containsKey(streamCid));
		_recordInfo.put(streamCid, info);
		_additionOrder.add(streamCid);
	}

	/**
	 * Removes a given StreamRecord from the cache, returning the associated cached data.
	 * 
	 * @param streamCid The CID of the StreamRecord.
	 * @return The cached info or null if it wasn't in the cache.
	 */
	public CachedRecordInfo removeStreamRecord(IpfsFile streamCid)
	{
		boolean didRemove = _additionOrder.remove(streamCid);
		CachedRecordInfo removed = _recordInfo.remove(streamCid);
		if (didRemove)
		{
			Assert.assertTrue(null != removed);
		}
		else
		{
			Assert.assertTrue(null == removed);
		}
		return removed;
	}

	@Override
	public CachedRecordInfo getRecordInfo(IpfsFile recordCid)
	{
		return _recordInfo.get(recordCid);
	}

	@Override
	public List<IpfsFile> getRecordFiles()
	{
		return List.copyOf(_additionOrder);
	}

	@Override
	public long getFavouritesSizeBytes()
	{
		return _recordInfo.values().stream()
				.mapToLong((CachedRecordInfo elt) -> elt.combinedSizeBytes())
				.sum()
		;
	}
}
