package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveEntry;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == _elementCid)
		{
			throw new UsageException("Element CID must be provided");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = context.logger.logStart("Removing entry " + _elementCid + " from channel...");
			newRoot = RemoveEntry.run(access, context.recordCache, _elementCid);
			if (null == newRoot)
			{
				throw new UsageException("Unknown post");
			}
			log.logFinish("Entry removed: " + _elementCid);
		}
		return new ChangedRoot(newRoot);
	}
}
