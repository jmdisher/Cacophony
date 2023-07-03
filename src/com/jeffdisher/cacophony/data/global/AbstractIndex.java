package com.jeffdisher.cacophony.data.global;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Meant to abstract the differences between StreamIndex (V1) and CacophonyIndex (V2) while also providing a
 * higher-level interface.
 */
public class AbstractIndex
{
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_INDEX_SIZE_BYTES;
	public static final DataDeserializer<AbstractIndex> DESERIALIZER = (byte[] data) -> _convertV1(GlobalData.deserializeIndex(data));

	/**
	 * @return A new empty index.
	 */
	public static AbstractIndex createNew()
	{
		return new AbstractIndex(1
				, null
				, null
				, null
		);
	}


	private static AbstractIndex _convertV1(StreamIndex index) throws FailedDeserializationException
	{
		return new AbstractIndex(index.getVersion()
				, IpfsFile.fromIpfsCid(index.getDescription())
				, IpfsFile.fromIpfsCid(index.getRecommendations())
				, IpfsFile.fromIpfsCid(index.getRecords())
		);
	}


	// Since there is nothing special about these fields and they are all independent, we will just leave them public.
	public int version;
	public IpfsFile descriptionCid;
	public IpfsFile recommendationsCid;
	public IpfsFile recordsCid;

	private AbstractIndex(int version
			, IpfsFile descriptionCid
			, IpfsFile recommendationsCid
			, IpfsFile recordsCid
	)
	{
		this.version = version;
		this.descriptionCid = descriptionCid;
		this.recommendationsCid = recommendationsCid;
		this.recordsCid = recordsCid;
	}

	public byte[] serializeV1() throws SizeConstraintException
	{
		StreamIndex index = new StreamIndex();
		index.setVersion(this.version);
		index.setDescription(this.descriptionCid.toSafeString());
		index.setRecommendations(this.recommendationsCid.toSafeString());
		index.setRecords(this.recordsCid.toSafeString());
		return GlobalData.serializeIndex(index);
	}
}
