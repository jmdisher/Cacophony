package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException, SizeConstraintException
	{
		if (null == _name)
		{
			throw new UsageException("Name must be provided");
		}
		if (null == _description)
		{
			throw new UsageException("Description must be provided");
		}
		// We will expect that any entry-point will allocate this, so it isn't a user-facing error.
		Assert.assertTrue(null != _elements);
		
		ILogger log = context.logger.logStart("Publish: " + this);
		PublishHelpers.PublishElement[] openElements = openElementFiles(context.logger, _elements);
		IpfsFile newRoot;
		IpfsFile newElement;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			PublishHelpers.PublishResult result = PublishHelpers.uploadFileAndUpdateTracking(log, access, _name, _description, _discussionUrl, openElements);
			newRoot = result.newIndexRoot();
			newElement = result.newRecordCid();
		}
		finally
		{
			closeElementFiles(openElements);
		}
		
		log.logOperation("New element: " + newElement);
		log.logFinish("Publish completed!");
		return new ChangedRoot(newRoot);
	}


	private static PublishHelpers.PublishElement[] openElementFiles(ILogger logger, ElementSubCommand[] commands)
	{
		boolean error = false;
		PublishHelpers.PublishElement[] elements = new PublishHelpers.PublishElement[commands.length];
		for (int i = 0; !error && (i < commands.length); ++i)
		{
			ElementSubCommand command = commands[i];
			File file = command.filePath();
			try
			{
				FileInputStream stream = new FileInputStream(file);
				elements[i] = new PublishHelpers.PublishElement(command.mime(), stream, command.height(), command.width(), command.isSpecialImage());
			}
			catch (FileNotFoundException e)
			{
				logger.logError("File not found:  " + file.getAbsolutePath());
				error = true;
			}
		}
		if (error)
		{
			closeElementFiles(elements);
			elements = null;
		}
		return elements;
	}

	private static void closeElementFiles(PublishHelpers.PublishElement[] elements)
	{
		for (PublishHelpers.PublishElement element : elements)
		{
			if (null != element)
			{
				InputStream file = element.fileData();
				try
				{
					file.close();
				}
				catch (IOException e)
				{
					// We don't know how this fails on close.
					throw Assert.unexpected(e);
				}
			}
		}
	}
}
