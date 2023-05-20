package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.scheduler.ICommonFutureRead;
import com.jeffdisher.cacophony.scheduler.SyntheticRead;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}


	private void _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, KeyException, ProtocolDataException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			// Make sure that they are a followee.
			rootToLoad = followees.getLastFetchedRootForFollowee(_channelPublicKey);
			if (null != rootToLoad)
			{
				logger.logVerbose("Following " + _channelPublicKey);
				isCached = true;
			}
			else
			{
				logger.logVerbose("NOT following " + _channelPublicKey);
				rootToLoad = access.resolvePublicKey(_channelPublicKey).get();
				// Throws KeyException on failure.
				Assert.assertTrue(null != rootToLoad);
				isCached = false;
			}
		}
		else
		{
			// This is us.
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		ForeignChannelReader reader = new ForeignChannelReader(access, rootToLoad, isCached);
		StreamRecords records = reader.loadRecords();
		
		// Start the async StreamRecord loads.
		List<AsyncRecord> asyncRecords = new ArrayList<>();
		for (String rawCid : records.getRecord())
		{
			IpfsFile recordCid = IpfsFile.fromIpfsCid(rawCid);
			ICommonFutureRead<StreamRecord> future = (isCached
					? new SyntheticRead<>(access.loadCached(recordCid, (byte[] data) -> GlobalData.deserializeRecord(data)))
					: access.loadNotCached(recordCid, "record", SizeLimits.MAX_RECORD_SIZE_BYTES, (byte[] data) -> GlobalData.deserializeRecord(data))
			);
			asyncRecords.add(new AsyncRecord(rawCid, future));
		}
		
		// Walk the elements, reading each element.
		ILogger log = logger.logStart("Found " + asyncRecords.size() + " records:");
		for (AsyncRecord asyncRecord : asyncRecords)
		{
			StreamRecord record = asyncRecord.future.get();
			ILogger log2 = log.logStart("element " + asyncRecord.recordCid + ": " + record.getName());
			DataArray array = record.getElements();
			for (DataElement element : array.getElement())
			{
				log2.logOperation("\t" + element.getCid() + " - " + element.getMime());
			}
			log2.logFinish("");
		}
		log.logFinish("");
	}


	private static record AsyncRecord(String recordCid, ICommonFutureRead<StreamRecord> future)
	{
	}
}
