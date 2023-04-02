package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.UpdateDescription;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;


/**
 * The command which updates the description elements of the local user's channel.
 * NOTE:  The _pictureStream, if not null, will be closed by this command, during runInEnvironment, so the caller can
 * relinguish ownership of it.
 */
public record UpdateDescriptionCommand(String _name, String _description, InputStream _pictureStream, String _email, String _website) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInEnvironment(IEnvironment environment, ILogger logger) throws IpfsConnectionException, UsageException
	{
		// All of the parameters are optional but at least one of them must be provided (null means "unchanged field").
		if ((null == _name) && (null == _description) && (null == _pictureStream) && (null == _email) && (null == _website))
		{
			throw new UsageException("At least one field must be being changed");
		}
		// These elements must also be non-empty.
		if ((null != _name) && _name.isEmpty())
		{
			throw new UsageException("Name must be non-empty.");
		}
		if ((null != _description) && _description.isEmpty())
		{
			throw new UsageException("Description must be non-empty.");
		}
		
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(environment, logger))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			ILogger log = logger.logStart("Updating channel description...");
			UpdateDescription.Result result = UpdateDescription.run(access, _name, _description, _pictureStream, _email, _website);
			newRoot = result.newRoot();
			log.logFinish("Update completed!");
		}
		finally
		{
			// We took ownership of the stream so close it.
			if (null != _pictureStream)
			{
				try
				{
					_pictureStream.close();
				}
				catch (IOException e)
				{
					// We will just log and ignore this since it shouldn't happen but we would want to know about it.
					logger.logError(e.getLocalizedMessage());
				}
			}
		}
		return new ChangedRoot(newRoot);
	}
}
