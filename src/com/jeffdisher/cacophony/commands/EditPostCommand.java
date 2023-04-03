package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.EditEntry;
import com.jeffdisher.cacophony.commands.results.OnePost;
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
		
		EditEntry.Result result;
		try (IWritingAccess access = StandardAccess.writeAccess(context.environment, context.logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			result = EditEntry.run(access, _postToEdit, _name, _description, _discussionUrl);
			if (null == result)
			{
				throw new UsageException("Entry is not in our stream: " + _postToEdit);
			}
		}
		return new OnePost(result.newRoot(), result.newRecordCid(), result.newRecord());
	}
}
