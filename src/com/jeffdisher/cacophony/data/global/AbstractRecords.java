package com.jeffdisher.cacophony.data.global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.global.v2.records.CacophonyRecords;
import com.jeffdisher.cacophony.types.DataDeserializer;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.utils.SizeLimits2;


/**
 * Meant to abstract the differences between StreamRecords (V1) and CacophonyRecords (V2).
 */
public class AbstractRecords
{
	/**
	 * The maximum size, in bytes, of a records file.
	 */
	public static final long SIZE_LIMIT_BYTES = SizeLimits2.MAX_RECORDS_SIZE_BYTES;
	/**
	 * The shared deserializer for reading an instance from raw data.
	 */
	public static final DataDeserializer<AbstractRecords> DESERIALIZER = (byte[] data) -> _commonMultiVersionLoad(data);

	/**
	 * @return A new empty record.
	 */
	public static AbstractRecords createNew()
	{
		return new AbstractRecords(Collections.emptyList());
	}


	private static AbstractRecords _commonMultiVersionLoad(byte[] data) throws FailedDeserializationException
	{
		AbstractRecords converted;
		try
		{
			// We check for version 2, first.
			CacophonyRecords recordsV2 = GlobalData2.deserializeRecords(data);
			converted = new AbstractRecords(_convertList(recordsV2.getRecord()));
		}
		catch (FailedDeserializationException e)
		{
			// We will try version 1.
			StreamRecords recordsV1 = GlobalData.deserializeRecords(data);
			converted = new AbstractRecords(_convertList(recordsV1.getRecord()));
		}
		
		// We would have loaded one of them or thrown.
		return converted;
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
				throw new FailedDeserializationException(CacophonyRecords.class);
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

	/**
	 * Removes the given victim from the list, returning true if they were removed or false for not found.
	 * 
	 * @param victim The record CID to remove.
	 * @return True if the record was removed or false if it was not found.
	 */
	public boolean removeRecord(IpfsFile victim)
	{
		return _records.remove(victim);
	}

	/**
	 * Adds the given record CID to the list of posted records.
	 * 
	 * @param key The CID of the record to add.
	 */
	public void addRecord(IpfsFile cid)
	{
		_records.add(cid);
	}

	/**
	 * Serializes the instance as a V1 StreamRecords, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
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

	/**
	 * Serializes the instance as a V2 CacophonyRecords, returning the resulting byte array.
	 * 
	 * @return The byte array of the serialized instance
	 * @throws SizeConstraintException The instance was too big to fit within limits, once serialized.
	 */
	public byte[] serializeV2() throws SizeConstraintException
	{
		CacophonyRecords records = new CacophonyRecords();
		List<String> raw = records.getRecord();
		for (IpfsFile ref : _records)
		{
			raw.add(ref.toSafeString());
		}
		return GlobalData2.serializeRecords(records);
	}
}
