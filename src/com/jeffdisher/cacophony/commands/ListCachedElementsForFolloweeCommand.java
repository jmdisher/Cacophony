package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.FollowingCacheElement;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException, FailedDeserializationException
	{
		if (null == _followeeKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		try (IReadingAccess access = StandardAccess.readAccess(context.environment, context.logger))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}

	private void _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, UsageException, FailedDeserializationException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		Map<IpfsFile, FollowingCacheElement> cachedElements = followees.snapshotAllElementsForFollowee(_followeeKey);
		if (null != cachedElements)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			IpfsFile root = followees.getLastFetchedRootForFollowee(_followeeKey);
			
			ForeignChannelReader reader = new ForeignChannelReader(access, root, true);
			StreamRecords records = reader.loadRecords();
			List<String> recordList = records.getRecord();
			ILogger log = logger.logStart("Followee has " + recordList.size() + " elements:");
			for(String elementCid : recordList)
			{
				FollowingCacheElement element = cachedElements.get(IpfsFile.fromIpfsCid(elementCid));
				String suffix = null;
				if (null != element)
				{
					String imageString = (null != element.imageHash())
							? element.imageHash().toSafeString()
							: "(none)"
					;
					String leafString = (null != element.leafHash())
							? element.leafHash().toSafeString()
							: "(none)"
					;
					suffix = "(image: " + imageString + ", leaf: " + leafString + ")";
				}
				else
				{
					suffix = "(not cached)";
				}
				log.logOperation("Element CID: " + elementCid + " " + suffix);
			}
			log.logFinish("");
		}
		else
		{
			throw new UsageException("Not following " + _followeeKey.toPublicKey());
		}
	}
}
