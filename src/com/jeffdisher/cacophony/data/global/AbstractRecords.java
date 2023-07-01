package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.scheduler.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits;


/**
 * Meant to abstract the differences between StreamRecords (V1) and CacophonyRecords (V2).
 */
public class AbstractRecords
{
	public static final long SIZE_LIMIT_BYTES = SizeLimits.MAX_META_DATA_LIST_SIZE_BYTES;
	public static final DataDeserializer<AbstractRecords> DESERIALIZER = (byte[] data) -> new AbstractRecords(_convertList(GlobalData.deserializeRecords(data).getRecord()));

	/**
	 * @return A new empty record.
	 */
	public static AbstractRecords createNew()
	{
		return new AbstractRecords(Collections.emptyList());
	}


	private static List<IpfsFile> _convertList(List<String> raw) throws FailedDeserializationException
	{
		// We want to make sure that none of these fail to convert.
		List<IpfsFile> processed = new ArrayList<>();
		for (String one : raw)
		{
			IpfsFile file = IpfsFile.fromIpfsCid(one);
			if (null == file)
			{
				throw new FailedDeserializationException(StreamRecords.class);
			}
			processed.add(file);
		}
		return processed;
	}


	private final List<IpfsFile> _records;

	private AbstractRecords(List<IpfsFile> list)
	{
		_records = new ArrayList<>();
		_records.addAll(list);
	}

	/**
	 * @return An unmodifiable list of the records.
	 */
	public List<IpfsFile> getRecordList()
	{
		return Collections.unmodifiableList(_records);
	}

	public boolean removeRecord(IpfsFile victim)
	{
		return _records.remove(victim);
	}

	public void addRecord(IpfsFile cid)
	{
		_records.add(cid);
	}

	public byte[] serializeV1() throws SizeConstraintException
	{
		StreamRecords records = new StreamRecords();
		List<String> raw = records.getRecord();
		for (IpfsFile ref : _records)
		{
			raw.add(ref.toSafeString());
		}
		return GlobalData.serializeRecords(records);
	}
}
