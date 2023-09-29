package com.jeffdisher.cacophony.commands;

import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.IFavouritesReading;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record ListFavouritesCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		// Look this up and add it to the cache or throw UsageException if it isn't a StreamRecord.
		try (IReadingAccess access = Context.readAccess(context))
		{
			IFavouritesReading favourites = access.readableFavouritesCache();
			List<IpfsFile> keys = favourites.getRecordFiles();
			ILogger logger = context.logger.logStart("Found " + keys.size() + " favourites:");
			for (IpfsFile key : keys)
			{
				CachedRecordInfo record = favourites.getRecordInfo(key);
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
