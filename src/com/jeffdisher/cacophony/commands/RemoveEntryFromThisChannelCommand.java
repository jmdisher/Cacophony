package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveEntry;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _elementCid);
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = logger.logStart("Removing entry " + _elementCid + " from channel...");
			newRoot = RemoveEntry.run(access, null, _elementCid);
			if (null == newRoot)
			{
				throw new UsageException("Unknown post");
			}
			log.logFinish("Entry removed: " + _elementCid);
		}
		return new ChangedRoot(newRoot);
	}
}
