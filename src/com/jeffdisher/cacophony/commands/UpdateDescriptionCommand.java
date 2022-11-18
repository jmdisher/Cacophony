package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.SizeLimits;
import com.jeffdisher.cacophony.utils.StringHelpers;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath, String _email, String _website) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue((null != _name) || (null != _description) || (null != _picturePath) || (null != _email) || (null != _website));
		if (null != _picturePath)
		{
			Assert.assertTrue(_picturePath.isFile());
		}
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IOperationLog log = environment.logOperation("Updating channel description...");
			CleanupData cleanup = _runCore(environment, access);
			
			// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
			_runFinish(environment, access, cleanup);
			log.finish("Update completed!");
		}
	}


	private CleanupData _runCore(IEnvironment environment, IWritingAccess access) throws UsageException, IpfsConnectionException
	{
		LocalIndex localIndex = access.readOnlyLocalIndex();
		INetworkScheduler scheduler = access.scheduler();
		HighLevelCache cache = access.loadCacheReadWrite();
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = cache.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing description since we might be only partially updating it.
		StreamDescription description = cache.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		
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
				throw new UsageException("Picture too big (is " + StringHelpers.humanReadableBytes(rawData.length) + ", limit " + StringHelpers.humanReadableBytes(SizeLimits.MAX_DESCRIPTION_IMAGE_SIZE_BYTES) + ")");
			}
			IpfsFile pictureHash = scheduler.saveStream(new ByteArrayInputStream(rawData), true).get();
			cache.uploadedToThisCache(pictureHash);
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
		
		// Serialize and upload the description.
		byte[] rawDescription = GlobalData.serializeDescription(description);
		IpfsFile hashDescription = scheduler.saveStream(new ByteArrayInputStream(rawDescription), true).get();
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setDescription(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncPublish = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		return new CleanupData(asyncPublish, rootToLoad);
	}

	private void _runFinish(IEnvironment environment, IWritingAccess access, CleanupData data) throws IpfsConnectionException
	{
		LocalIndex localIndex = access.readOnlyLocalIndex();
		HighLevelCache cache = access.loadCacheReadWrite();
		
		// Do the local storage update while the publish continues in the background (even if it fails, we still want to update local storage).
		CommandHelpers.commonUpdateIndex(environment, access, localIndex, cache, data.oldRootHash, data.asyncPublish.getIndexHash());
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, data.asyncPublish);
	}


	private static record CleanupData(FuturePublish asyncPublish, IpfsFile oldRootHash) {}
}
