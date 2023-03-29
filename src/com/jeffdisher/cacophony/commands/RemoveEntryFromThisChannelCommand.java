package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.RemoveEntry;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveEntryFromThisChannelCommand(IpfsFile _elementCid) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != _elementCid);
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IEnvironment.IOperationLog log = environment.logStart("Removing entry " + _elementCid + " from channel...");
			IpfsFile newRoot = RemoveEntry.run(access, null, _elementCid);
			FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
			CommandHelpers.commonWaitForPublish(environment, asyncPublish);
			log.logFinish("Entry removed: " + _elementCid);
		}
	}
}
