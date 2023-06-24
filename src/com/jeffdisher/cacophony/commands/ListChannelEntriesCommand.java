package com.jeffdisher.cacophony.commands;

import java.util.ArrayList;
import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
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
			ICommonFutureRead<AbstractRecord> future = (isCached
					? new SyntheticRead<>(access.loadCached(recordCid, AbstractRecord.DESERIALIZER))
					: access.loadNotCached(recordCid, "record", AbstractRecord.SIZE_LIMIT_BYTES, AbstractRecord.DESERIALIZER)
			);
			asyncRecords.add(new AsyncRecord(rawCid, future));
		}
		
		// Walk the elements, reading each element.
		ILogger log = logger.logStart("Found " + asyncRecords.size() + " records:");
		for (AsyncRecord asyncRecord : asyncRecords)
		{
			AbstractRecord record = asyncRecord.future.get();
			ILogger log2 = log.logStart("element " + asyncRecord.recordCid + ": " + record.getName());
			if (null != record.getThumbnailCid())
			{
				log2.logOperation("\tThumbnail: " + record.getThumbnailCid().toSafeString());
			}
			List<AbstractRecord.Leaf> leaves = record.getVideoExtension();
			if (null != leaves)
			{
				for (AbstractRecord.Leaf leaf : leaves)
				{
					log2.logOperation("\t" + leaf.cid().toSafeString() + " - " + leaf.mime());
				}
			}
			log2.logFinish("");
		}
		log.logFinish("");
	}


	private static record AsyncRecord(String recordCid, ICommonFutureRead<AbstractRecord> future)
	{
	}
}
