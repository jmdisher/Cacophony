package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.EditEntry;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


public record EditPostCommand(IpfsFile _postToEdit, String _name, String _description, String _discussionUrl) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException, UsageException
	{
		if ((null == _name) && (null == _description) && (null == _discussionUrl))
		{
			throw new UsageException("At least one field must be being changed");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			EditEntry.Result result = EditEntry.run(access, _postToEdit, _name, _description, _discussionUrl);
			if (null != result)
			{
				newRoot = result.newRoot();
			}
			else
			{
				throw new UsageException("Entry is not in our stream: " + _postToEdit);
			}
		}
		return new ChangedRoot(newRoot);
	}
}
