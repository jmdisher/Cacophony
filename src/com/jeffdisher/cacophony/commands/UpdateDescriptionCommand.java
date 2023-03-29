package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.UpdateDescription;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath, String _email, String _website) implements ICommand<None>
{
	@Override
	public None runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue((null != _name) || (null != _description) || (null != _picturePath) || (null != _email) || (null != _website));
		if (null != _picturePath)
		{
			Assert.assertTrue(_picturePath.isFile());
		}
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			if (null == access.getLastRootElement())
			{
				throw new UsageException("Channel must first be created with --createNewChannel");
			}
			IEnvironment.IOperationLog log = environment.logStart("Updating channel description...");
			_runCore(environment, access);
			log.logFinish("Update completed!");
		}
		return None.NONE;
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws UsageException, IpfsConnectionException
	{
		FileInputStream pictureStream;
		try
		{
			pictureStream = (null != _picturePath)
					? new FileInputStream(_picturePath)
					: null;
		}
		catch (FileNotFoundException e)
		{
			throw new UsageException("Unable to load picture: " + _picturePath.toPath());
		}
		UpdateDescription.Result result = UpdateDescription.run(access, _name, _description, pictureStream, _email, _website);
		IpfsFile newRoot = result.newRoot();
		
		environment.logVerbose("Publishing " + newRoot + "...");
		FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
	}
}
