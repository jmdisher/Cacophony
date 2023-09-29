package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record DeleteChannelCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		IpfsKey userToDelete = context.getSelectedKey();
		if (null == userToDelete)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		try (IWritingAccess access = Context.writeAccess(context))
		{
			IpfsFile indexCid = access.getLastRootElement();
			AbstractIndex index = access.loadCached(indexCid, AbstractIndex.DESERIALIZER).get();
			
			IpfsFile recommendationsCid = index.recommendationsCid;
			// Recommendations has no other info so we don't need to load it.
			access.unpin(recommendationsCid);
			
			IpfsFile descriptionCid = index.descriptionCid;
			AbstractDescription description = access.loadCached(descriptionCid, AbstractDescription.DESERIALIZER).get();
			_handleDescription(access, description);
			access.unpin(descriptionCid);
			
			IpfsFile recordsCid = index.recordsCid;
			AbstractRecords records = access.loadCached(recordsCid, AbstractRecords.DESERIALIZER).get();
			_handleRecords(access, userToDelete, context.cacheUpdater, records);
			access.unpin(recordsCid);
			
			access.unpin(indexCid);
			access.deleteChannelData();
		}
		catch (FailedDeserializationException e)
		{
			// This is our own well-formed data so we don't expect this error.
			throw Assert.unexpected(e);
		}
		
		context.cacheUpdater.removedHomeUser(userToDelete);
		
		// We also want to clean up the context.
		context.setSelectedKey(null);
		return None.NONE;
	}


	private void _handleDescription(IWritingAccess access, AbstractDescription description) throws IpfsConnectionException
	{
		// We just need the user pic.
		IpfsFile pic = description.getPicCid();
		// In V2, this can be missing.
		if (null != pic)
		{
			access.unpin(pic);
		}
	}

	private void _handleRecords(IWritingAccess access, IpfsKey publicKey, CacheUpdater cacheUpdater, AbstractRecords records) throws FailedDeserializationException, IpfsConnectionException
	{
		// We need to walk all the records and then walk every leaf in each one.
		for (IpfsFile recordCid : records.getRecordList())
		{
			// Read the record.
			AbstractRecord record = access.loadCached(recordCid, AbstractRecord.DESERIALIZER).get();
			// Unpin all the leaves in the record.
			if (null != record.getThumbnailCid())
			{
				access.unpin(record.getThumbnailCid());
			}
			if (null != record.getVideoExtension())
			{
				for (AbstractRecord.Leaf leaf : record.getVideoExtension())
				{
					IpfsFile leafCid = leaf.cid();
					access.unpin(leafCid);
				}
			}
			// Unpin the record.
			access.unpin(recordCid);
			
			// Now that this has been removed, notify the cache.
			cacheUpdater.removedHomeUserPost(publicKey, recordCid, record);
		}
	}
}
