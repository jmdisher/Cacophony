package com.jeffdisher.cacophony.commands;

import java.util.List;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.ForeignChannelReader;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.FollowingCacheElement;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListCachedElementsForFolloweeCommand(IpfsKey _followeeKey) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _followeeKey)
		{
			throw new UsageException("Public key must be provided");
		}
		
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			_runCore(context.logger, access);
		}
		return None.NONE;
	}

	private void _runCore(ILogger logger, IReadingAccess access) throws IpfsConnectionException, UsageException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		Map<IpfsFile, FollowingCacheElement> cachedElements = followees.snapshotAllElementsForFollowee(_followeeKey);
		if (null != cachedElements)
		{
			// We know that all the meta-data reachable from this root is cached locally, but not all the leaf data elements, so we will check the FollowRecord.
			IpfsFile root = followees.getLastFetchedRootForFollowee(_followeeKey);
			
			ForeignChannelReader reader = new ForeignChannelReader(access, root, true);
			AbstractRecords records;
			try
			{
				records = reader.loadRecords();
			}
			catch (ProtocolDataException e)
			{
				// We should not have already cached this if it was corrupt.
				throw Assert.unexpected(e);
			}
			List<IpfsFile> recordList = records.getRecordList();
			ILogger log = logger.logStart("Followee has " + recordList.size() + " elements:");
			for(IpfsFile elementCid : recordList)
			{
				FollowingCacheElement element = cachedElements.get(elementCid);
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
				// We want to mention that this is a reply, if it is one (this since we only list people we are
				// following, this should be quick since we always fetch the meta-data, even for "non-cached" cases).
				String replyPart = "";
				try
				{
					AbstractRecord record = access.loadCached(elementCid, AbstractRecord.DESERIALIZER).get();
					if (null != record.getReplyTo())
					{
						replyPart = " is a reply to: " + record.getReplyTo().toSafeString();
					}
				}
				catch (FailedDeserializationException e)
				{
					// This can't happen since we already decided to fetch this, before.
					throw Assert.unexpected(e);
				}
				log.logOperation("Element CID: " + elementCid.toSafeString() + " " + suffix + replyPart);
			}
			log.logFinish("");
		}
		else
		{
			throw new UsageException("Not following " + _followeeKey.toPublicKey());
		}
	}
}
