package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.ActionHelpers;
import com.jeffdisher.cacophony.commands.results.OnePost;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.record.StreamRecord;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record EditPostCommand(IpfsFile _postToEdit, String _name, String _description, String _discussionUrl) implements ICommand<OnePost>
{
	@Override
	public OnePost runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if ((null == _name) && (null == _description) && (null == _discussionUrl))
		{
			throw new UsageException("At least one field must be being changed");
		}
		
		Result result;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			result = _run(access, _postToEdit, _name, _description, _discussionUrl);
			if (null == result)
			{
				throw new UsageException("Entry is not in our stream: " + _postToEdit);
			}
		}
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
		ChannelModifier modifier = new ChannelModifier(access);
		StreamRecords records = ActionHelpers.readRecords(modifier);
		List<String> recordList = records.getRecord();
		IpfsFile newRoot = null;
		IpfsFile newEltCid = null;
		StreamRecord record = null;
		if (recordList.contains(postToEdit.toSafeString()))
		{
			// Found this, so replace it.
			record = ActionHelpers.unwrap(access.loadCached(postToEdit, (byte[] data) -> GlobalData.deserializeRecord(data)));
			if (null != name)
			{
				record.setName(name);
			}
			if (null != description)
			{
				record.setDescription(description);
			}
			if (null != discussionUrl)
			{
				if (discussionUrl.isEmpty())
				{
					record.setDiscussion(null);
				}
				else
				{
					record.setDiscussion(discussionUrl);
				}
			}
			newEltCid = access.uploadAndPin(new ByteArrayInputStream(ActionHelpers.serializeRecord(record)));
			
			int index = recordList.indexOf(postToEdit.toSafeString());
			recordList.remove(index);
			recordList.add(index, newEltCid.toSafeString());
			
			modifier.storeRecords(records);
			newRoot = ActionHelpers.commitNewRoot(modifier);
			access.unpin(postToEdit);
		}
		return (null != newRoot)
				? new Result(newRoot, newEltCid, record)
				: null
		;
	}


	public static record Result(IpfsFile newRoot, IpfsFile newRecordCid, StreamRecord newRecord)
	{
	}
}
