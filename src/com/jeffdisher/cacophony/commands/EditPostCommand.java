package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.caches.CacheUpdater;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.global.AbstractRecord;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.logic.HomeChannelModifier;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record EditPostCommand(IpfsFile _postToEdit, String _name, String _description, String _discussionUrl) implements ICommand<OnePost>
{
	@Override
	public OnePost runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if ((null == _name) && (null == _description) && (null == _discussionUrl))
		{
			throw new UsageException("At least one field must be being changed");
		}
		// Check the parameters, if provided.
		if ((null != _name) && !AbstractRecord.validateName(_name))
		{
			throw new UsageException("Name must have a length must be between [1, 255]");
		}
		if ((null != _description) && (_description.length() > 32768))
		{
			// We allow the empty string to mean "remove".
			throw new UsageException("Description length cannot be more than 32768");
		}
		IpfsKey publicKey = context.getSelectedKey();
		if (null == publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		
		Result result;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			result = _run(access, _postToEdit, _name, _description, _discussionUrl);
			if (null == result)
			{
				throw new UsageException("Entry is not in our stream: " + _postToEdit);
			}
		}
		_handleCacheUpdates(context.cacheUpdater, publicKey, _postToEdit, result.newRecordCid(), result.newRecord());
		return new OnePost(result.newRoot(), result.newRecordCid(), result.newRecord());
	}


	/**
	 * Removes the entry CID from the local user's stream, replacing it with a new one with the same leaves but modified
	 * other meta-data.
	 * 
	 * @param access Write access.
	 * @param postToEdit The CID of the record to replace.
	 * @param name The new name for the entry (can be null to not modify).
	 * @param description The new description for the entry (can be null to not modify).
	 * @param discussionUrl The new discussion URL for the entry (can be null to not modify and empty string to remove).
	 * @return The new local root element and StreamRecord or null, if the entry wasn't found.
	 * @throws IpfsConnectionException There was a network error.
	 */
	private static Result _run(IWritingAccess access, IpfsFile postToEdit, String name, String description, String discussionUrl) throws IpfsConnectionException
	{
		// The edit only changes the StreamRecord element, itself.  We first want to make sure that we see this
		// element in the stream list and that it has our key as the publisher (this just avoids doing confusing
		// things like editing someone else's content - nothing prevents this, but the standard UI shouldn't
		// make such a mistake easy).
		// Then, we will create a replacement StreamRecord element and replace the old one, in-place.
		HomeChannelModifier modifier = new HomeChannelModifier(access);
		AbstractRecords records = modifier.loadRecords();
		IpfsFile newRoot = null;
		IpfsFile newEltCid = null;
		AbstractRecord record = null;
		if (records.removeRecord(postToEdit))
		{
			// Found this, so replace it.
			try
			{
				record = access.loadCached(postToEdit, AbstractRecord.DESERIALIZER).get();
			}
			catch (FailedDeserializationException e)
			{
				// This is for deserializing the local channel so the error isn't expected.
				throw Assert.unexpected(e);
			}
			if (null != name)
			{
				record.setName(name);
			}
			if (null != description)
			{
				if (description.isEmpty())
				{
					record.setDescription(null);
				}
				else
				{
					record.setDescription(description);
				}
			}
			if (null != discussionUrl)
			{
				if (discussionUrl.isEmpty())
				{
					record.setDiscussionUrl(null);
				}
				else
				{
					record.setDiscussionUrl(discussionUrl);
				}
			}
			try
			{
				byte[] data = record.serializeV2();
				newEltCid = access.uploadAndPin(new ByteArrayInputStream(data));
			}
			catch (SizeConstraintException e)
			{
				// This would be a static error we should be handling on a higher level so ending up here would be a bug.
				throw Assert.unexpected(e);
			}
			
			records.addRecord(newEltCid);
			
			modifier.storeRecords(records);
			newRoot = modifier.commitNewRoot();
			access.unpin(postToEdit);
		}
		return (null != newRoot)
				? new Result(newRoot, newEltCid, record)
				: null
		;
	}

	private static void _handleCacheUpdates(CacheUpdater cacheUpdater, IpfsKey publicKey, IpfsFile oldCid, IpfsFile newCid, AbstractRecord newStreamRecord)
	{
		// NOTE:  This assumes that the leaves are the same between the old/new records.
		
		// Notify the cache that we removed the old entry.
		// NOTE:  We pass in newStreamRecord since it has all the same out-of-line data, due to the above assumption.
		cacheUpdater.removedHomeUserPost(publicKey, oldCid, newStreamRecord);
		
		// Notify the cache that we added the new entry.
		cacheUpdater.addedHomeUserPost(publicKey, newCid, newStreamRecord);
	}


	public static record Result(IpfsFile newRoot, IpfsFile newRecordCid, AbstractRecord newRecord)
	{
	}
}
