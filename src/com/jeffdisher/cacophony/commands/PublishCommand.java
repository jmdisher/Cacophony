package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.PublishHelpers;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record PublishCommand(String _name, String _description, String _discussionUrl, ElementSubCommand[] _elements) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _name);
		Assert.assertTrue(null != _description);
		
		IOperationLog log = environment.logOperation("Publish: " + this);
		LocalConfig local = environment.loadExistingConfig();
		IReadWriteLocalData data = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = data.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = data.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		
		IpfsFile previousRootElement = localIndex.lastPublishedIndex();
		PublishHelpers.PublishElement[] openElements = openElementFiles(environment, _elements);
		FuturePublish asyncPublish;
		try
		{
			asyncPublish = PublishHelpers.uploadFileAndStartPublish(environment, scheduler, connection, previousRootElement, pinCache, cache, _name, _description, _discussionUrl, openElements);
		}
		finally
		{
			closeElementFiles(environment, openElements);
		}
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		PublishHelpers.updateLocalStorageAndWaitForPublish(environment, localIndex, cache, previousRootElement, asyncPublish, data);
		
		// Save back other parts of the data store.
		data.writeGlobalPinCache(pinCache);
		data.close();
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
