package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record RemoveFavouriteCommand(IpfsFile _elementCid) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		// Look this up and add it to the cache or throw UsageException if it isn't a StreamRecord.
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			FavouritesCacheData favourites = access.writableFavouritesCache();
			CachedRecordInfo removed = favourites.removeStreamRecord(_elementCid);
			if (null != removed)
			{
				// Unpin these elements.
				ILogger logger = context.logger.logStart("Unpinnning elements under favourite: " + _elementCid);
				_unpinWithLog(logger, access, _elementCid);
				_unpinWithLog(logger, access, removed.thumbnailCid());
				_unpinWithLog(logger, access, removed.videoCid());
				_unpinWithLog(logger, access, removed.audioCid());
				logger.logFinish("Favourite unpinned");
			}
			else
			{
				throw new UsageException("Record was NOT a favourite");
			}
		}
		return None.NONE;
	}


	private void _unpinWithLog(ILogger logger, IWritingAccess access, IpfsFile elt)
	{
		if (null != elt)
		{
			try
			{
				access.unpin(elt);
			}
			catch (IpfsConnectionException e)
			{
				logger.logError("Failed to unpin element (will need to be manually unpinned): " + elt);
			}
		}
	}
}
