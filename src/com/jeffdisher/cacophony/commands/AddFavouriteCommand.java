package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.ConcurrentTransaction;
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
				ConcurrentTransaction transaction = access.openConcurrentTransaction();
				ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
				try
				{
					info = CommonRecordPinning.loadAndPinRecord(transaction, access.readPrefs().videoEdgePixelMax, _elementCid);
					transaction.commit(resolver);
				}
				catch (ProtocolDataException e)
				{
					transaction.rollback(resolver);
					throw new UsageException("Record could not be parsed");
				}
				catch (IpfsConnectionException e)
				{
					transaction.rollback(resolver);
					throw e;
				}
				// The helper never returns null.
				Assert.assertTrue(null != info);
				favourites.addStreamRecord(_elementCid, info);
			}
			else
			{
				throw new UsageException("Post is already favourite");
			}
		}
		return None.NONE;
	}
}
