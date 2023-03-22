package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.EditEntry;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record EditPostCommand(IpfsFile _postToEdit, String _name, String _description, String _discussionUrl) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue((null != _name) || (null != _description) || (null != _discussionUrl));
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IOperationLog log = environment.logOperation("Editing post: " + _postToEdit);
			EditEntry.Result result = EditEntry.run(access, _postToEdit, _name, _description, _discussionUrl);
			if (null != result)
			{
				environment.logToConsole("Publishing " + result.newRoot() + "...");
				FuturePublish asyncPublish = access.beginIndexPublish(result.newRoot());
				CommandHelpers.commonWaitForPublish(environment, asyncPublish);
				log.finish("Update completed!");
			}
			else
			{
				environment.logError("Entry is not in our stream: " + _postToEdit);
			}
		}
	}
}
