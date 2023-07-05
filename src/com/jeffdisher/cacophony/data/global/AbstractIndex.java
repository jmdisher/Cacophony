package com.jeffdisher.cacophony.data.global;

import java.util.Map;
import java.util.stream.Collectors;

import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.v2.root.CacophonyRoot;
import com.jeffdisher.cacophony.data.global.v2.root.DataReference;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Meant to abstract the differences between StreamIndex (V1) and CacophonyRoot (V2) while also providing a
 * higher-level interface.
 */
public class AbstractIndex
{
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_INDEX_SIZE_BYTES;
	public static final DataDeserializer<AbstractIndex> DESERIALIZER = (byte[] data) -> _commonMultiVersionLoad(data);

	/**
	 * @return A new empty index.
	 */
	public static AbstractIndex createNew()
	{
		return new AbstractIndex(null
				, null
				, null
		);
	}


	private static AbstractIndex _commonMultiVersionLoad(byte[] data) throws FailedDeserializationException
	{
		AbstractIndex converted;
		try
		{
			// We check for version 2, first.
			CacophonyRoot root = GlobalData2.deserializeRoot(data);
			converted = _convertV2(root);
		}
		catch (FailedDeserializationException e)
		{
			// We will try version 1.
			StreamIndex index = GlobalData.deserializeIndex(data);
			converted = _convertV1(index);
		}
		
		// We would have loaded one of them or thrown.
		return converted;
	}

	private static AbstractIndex _convertV2(CacophonyRoot root) throws FailedDeserializationException
	{
		Map<String, String> data = root.getData().stream().collect(Collectors.toMap(
				DataReference::getType, DataReference::getValue
		));
		String descriptionCid = data.get(GlobalData2.ROOT_DATA_TYPE_DESCRIPTION);
		String recommendationsCid = data.get(GlobalData2.ROOT_DATA_TYPE_RECOMMENDATIONS);
		String recordsCid = data.get(GlobalData2.ROOT_DATA_TYPE_RECORDS);
		return new AbstractIndex((null != descriptionCid) ? IpfsFile.fromIpfsCid(descriptionCid) : null
				, (null != recommendationsCid) ? IpfsFile.fromIpfsCid(recommendationsCid) : null
				, (null != recordsCid) ? IpfsFile.fromIpfsCid(recordsCid) : null
		);
	}

	private static AbstractIndex _convertV1(StreamIndex index) throws FailedDeserializationException
	{
		return new AbstractIndex(IpfsFile.fromIpfsCid(index.getDescription())
				, IpfsFile.fromIpfsCid(index.getRecommendations())
				, IpfsFile.fromIpfsCid(index.getRecords())
		);
	}


	// Since there is nothing special about these fields and they are all independent, we will just leave them public.
	public IpfsFile descriptionCid;
	public IpfsFile recommendationsCid;
	public IpfsFile recordsCid;

	private AbstractIndex(IpfsFile descriptionCid
			, IpfsFile recommendationsCid
			, IpfsFile recordsCid
	)
	{
		this.descriptionCid = descriptionCid;
		this.recommendationsCid = recommendationsCid;
		this.recordsCid = recordsCid;
	}

	/**
	 * Serializes the instance as a V1 StreamIndex, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV1() throws SizeConstraintException
	{
		StreamIndex index = new StreamIndex();
		index.setVersion(1);
		index.setDescription(this.descriptionCid.toSafeString());
		index.setRecommendations(this.recommendationsCid.toSafeString());
		index.setRecords(this.recordsCid.toSafeString());
		return GlobalData.serializeIndex(index);
	}

	/**
	 * Serializes the instance as a V2 CacophonyRoot, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV2() throws SizeConstraintException
	{
		CacophonyRoot index = new CacophonyRoot();
		index.setVersion(2);
		if (null != this.descriptionCid)
		{
			DataReference data_description = new DataReference();
			data_description.setType(GlobalData2.ROOT_DATA_TYPE_DESCRIPTION);
			data_description.setValue(this.descriptionCid.toSafeString());
			index.getData().add(data_description);
		}
		if (null != this.recommendationsCid)
		{
			DataReference data_recommendations = new DataReference();
			data_recommendations.setType(GlobalData2.ROOT_DATA_TYPE_RECOMMENDATIONS);
			data_recommendations.setValue(this.recommendationsCid.toSafeString());
			index.getData().add(data_recommendations);
		}
		if (null != this.recordsCid)
		{
			DataReference data_records = new DataReference();
			data_records.setType(GlobalData2.ROOT_DATA_TYPE_RECORDS);
			data_records.setValue(this.recordsCid.toSafeString());
			index.getData().add(data_records);
		}
		return GlobalData2.serializeRoot(index);
	}
}
