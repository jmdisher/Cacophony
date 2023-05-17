package com.jeffdisher.cacophony.commands;

import java.util.Set;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.UsageException;


public record ListFavouritesCommand() implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		// Look this up and add it to the cache or throw UsageException if it isn't a StreamRecord.
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			IFavouritesReading favourites = access.readableFavouritesCache();
			Set<CachedRecordInfo> records = favourites.getRecords();
			ILogger logger = context.logger.logStart("Found " + records.size() + " favourites:");
			for (CachedRecordInfo record : records)
			{
				ILogger elt = logger.logStart("Record: " + record.streamCid());
				if (null != record.thumbnailCid())
				{
					elt.logOperation("Thumbnail: " + record.thumbnailCid());
				}
				if (null != record.videoCid())
				{
					elt.logOperation("Video: " + record.videoCid());
				}
				if (null != record.audioCid())
				{
					elt.logOperation("Audio: " + record.audioCid());
				}
				elt.logFinish("");
			}
			logger.logFinish("");
		}
		return None.NONE;
	}
}
