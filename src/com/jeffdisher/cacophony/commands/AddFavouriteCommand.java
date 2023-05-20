package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.CommonRecordPinning;
import com.jeffdisher.cacophony.projection.CachedRecordInfo;
import com.jeffdisher.cacophony.projection.FavouritesCacheData;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.ProtocolDataException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record AddFavouriteCommand(IpfsFile _elementCid) implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		// Look this up and add it to the cache or throw UsageException if it isn't a StreamRecord.
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			FavouritesCacheData favourites = access.writableFavouritesCache();
			if (null == favourites.getRecordInfo(_elementCid))
			{
				// We can add this so use the common logic.
				CachedRecordInfo info;
				try
				{
					info = CommonRecordPinning.loadAndPinRecord(access, access.readPrefs().videoEdgePixelMax, _elementCid);
				}
				catch (ProtocolDataException e)
				{
					throw new UsageException("Record could not be parsed");
				}
				catch (IpfsConnectionException e)
				{
					throw e;
				}
				// The helper never returns null.
				Assert.assertTrue(null != info);
				favourites.addStreamRecord(_elementCid, info);
			}
		}
		return None.NONE;
	}
}
