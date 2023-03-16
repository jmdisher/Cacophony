package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException, FailedDeserializationException, SizeConstraintException
	{
		Assert.assertTrue(null != _name);
		Assert.assertTrue(null != _description);
		
		IOperationLog log = environment.logOperation("Publish: " + this);
		PublishHelpers.PublishElement[] openElements = openElementFiles(environment, _elements);
		FuturePublish asyncPublish;
		IpfsFile[] newElementContainer = new IpfsFile[1];
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IpfsFile newRoot = PublishHelpers.uploadFileAndUpdateTracking(environment, access, _name, _description, _discussionUrl, openElements, newElementContainer);
			asyncPublish = access.beginIndexPublish(newRoot);
		}
		finally
		{
			closeElementFiles(environment, openElements);
		}
		
		// We can now wait for the publish to complete, now that we have closed all the local state.
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
		
		environment.logToConsole("New element: " + newElementContainer[0]);
		log.finish("Publish completed!");
	}


	private static PublishHelpers.PublishElement[] openElementFiles(IEnvironment environment, ElementSubCommand[] commands)
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
				environment.logError("File not found:  " + file.getAbsolutePath());
				error = true;
			}
		}
		if (error)
		{
			closeElementFiles(environment, elements);
			elements = null;
		}
		return elements;
	}

	private static void closeElementFiles(IEnvironment environment, PublishHelpers.PublishElement[] elements)
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
