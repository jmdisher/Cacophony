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
		ConcurrentTransaction transaction;
		int videoEdgePixelMax;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			FavouritesCacheData favourites = access.writableFavouritesCache();
			if (null == favourites.getRecordInfo(_elementCid))
			{
				// We can add this so use the common logic.
				transaction = access.openConcurrentTransaction();
				videoEdgePixelMax = access.readPrefs().videoEdgePixelMax;
			}
			else
			{
				throw new UsageException("Post is already favourite");
			}
		}
		// We will throw or open the transaction.
		Assert.assertTrue(null != transaction);
		
		CachedRecordInfo info;
		try
		{
			info = CommonRecordPinning.loadAndPinRecord(transaction, videoEdgePixelMax, _elementCid);
		}
		catch (ProtocolDataException e)
		{
			_rollback(context, transaction);
			throw new UsageException("Record could not be parsed");
		}
		catch (IpfsConnectionException e)
		{
			_rollback(context, transaction);
			throw e;
		}
		// The helper never returns null.
		Assert.assertTrue(null != info);
		_commit(context, transaction, info);
		return None.NONE;
	}


	private void _commit(Context context, ConcurrentTransaction transaction, CachedRecordInfo info) throws UsageException
	{
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
			FavouritesCacheData favourites = access.writableFavouritesCache();
			if (null == favourites.getRecordInfo(_elementCid))
			{
				// Write this back.
				transaction.commit(resolver);
				// Update the favourites.
				favourites.addStreamRecord(_elementCid, info);
			}
			else
			{
				// Failed in a race so just revert and throw.
				transaction.rollback(resolver);
				throw new UsageException("Post concurrently added to favourites");
			}
		}
	}

	private void _rollback(Context context, ConcurrentTransaction transaction)
	{
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			ConcurrentTransaction.IStateResolver resolver = ConcurrentTransaction.buildCommonResolver(access);
			transaction.rollback(resolver);
		}
	}
}
