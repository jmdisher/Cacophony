package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.record.DataArray;
import com.jeffdisher.cacophony.data.global.record.DataElement;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.scheduler.FutureRead;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListChannelEntriesCommand(IpfsKey _channelPublicKey) implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, KeyException, FailedDeserializationException
	{
		try (IReadingAccess access = StandardAccess.readAccess(context.environment, context.logger))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}


	private void _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, KeyException, FailedDeserializationException
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
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _channelPublicKey);
				}
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
		StreamIndex index = access.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		StreamRecords records = (isCached
				? access.loadCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
				: access.loadNotCached(IpfsFile.fromIpfsCid(index.getRecords()), (byte[] data) -> GlobalData.deserializeRecords(data))
		).get();
		
		// Start the async StreamRecord loads.
		List<AsyncRecord> asyncRecords = new ArrayList<>();
		for (String recordCid : records.getRecord())
		{
			FutureRead<StreamRecord> future = (isCached
					? access.loadCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
					: access.loadNotCached(IpfsFile.fromIpfsCid(recordCid), (byte[] data) -> GlobalData.deserializeRecord(data))
			);
			asyncRecords.add(new AsyncRecord(recordCid, future));
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


	private static record AsyncRecord(String recordCid, FutureRead<StreamRecord> future)
	{
	}
}
