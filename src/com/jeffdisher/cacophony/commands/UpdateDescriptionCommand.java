package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.actions.ActionHelpers;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.logic.ChannelModifier;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.MiscHelpers;
import com.jeffdisher.cacophony.utils.SizeLimits;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath, String _email, String _website) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return true;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
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
			IOperationLog log = environment.logOperation("Updating channel description...");
			_runCore(environment, access);
			log.finish("Update completed!");
		}
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws UsageException, IpfsConnectionException
	{
		ChannelModifier modifier = new ChannelModifier(access);
		
		// Read the existing description since we might be only partially updating it.
		StreamDescription description = ActionHelpers.readDescription(modifier);
		
		if (null != _name)
		{
			description.setName(_name);
		}
		if (null != _description)
		{
			description.setDescription(_description);
		}
		if (null != _picturePath)
		{
			// Upload the picture.
			byte[] rawData;
			try
			{
				rawData = Files.readAllBytes(_picturePath.toPath());
			}
			catch (IOException e)
			{
				throw new UsageException("Unable to load picture: " + _picturePath.toPath());
			}
			if (rawData.length > SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES)
			{
				throw new UsageException("Picture too big (is " + MiscHelpers.humanReadableBytes(rawData.length) + ", limit " + MiscHelpers.humanReadableBytes(SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES) + ")");
			}
			IpfsFile pictureHash = access.uploadAndPin(new ByteArrayInputStream(rawData));
			description.setPicture(pictureHash.toSafeString());
		}
		if (null != _email)
		{
			// Since email is optional, we will treat an empty string as "remove".
			if (0 == _email.length())
			{
				description.setEmail(null);
			}
			else
			{
				description.setEmail(_email);
			}
		}
		if (null != _website)
		{
			// Since website is optional, we will treat an empty string as "remove".
			if (0 == _website.length())
			{
				description.setWebsite(null);
			}
			else
			{
				description.setWebsite(_website);
			}
		}
		
		// Update and commit the structure.
		modifier.storeDescription(description);
		environment.logToConsole("Saving new index...");
		IpfsFile newRoot = ActionHelpers.commitNewRoot(modifier);
		
		environment.logToConsole("Publishing " + newRoot + "...");
		FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
	}
}
