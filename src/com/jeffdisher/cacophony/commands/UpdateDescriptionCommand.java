package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


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
		
		IOperationLog log = environment.logOperation("Updating channel description...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler);
		CleanupData cleanup = _runCore(environment, scheduler, connection, localIndex, pinCache, cache);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		_runFinish(environment, local, localIndex, cache, cleanup);
		log.finish("Update completed!");
	}


	private CleanupData _runCore(IEnvironment environment, INetworkScheduler scheduler, IConnection connection, LocalIndex localIndex, GlobalPinCache pinCache, HighLevelCache cache) throws UsageException, IpfsConnectionException
	{
		LoadChecker checker = new LoadChecker(scheduler, pinCache, connection);
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = checker.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		
		// Read the existing description since we might be only partially updating it.
		StreamDescription description = checker.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data)).get();
		
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
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		return new CleanupData(indexHash, rootToLoad);
	}

	private void _runFinish(IEnvironment environment, LocalConfig local, LocalIndex localIndex, HighLevelCache cache, CleanupData data)
	{
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), data.indexHash));
		cache.uploadedToThisCache(data.indexHash);
		
		// Remove old root.
		_safeRemove(environment, cache, data.oldRootHash);
		local.writeBackConfig();
	}

	private static void _safeRemove(IEnvironment environment, HighLevelCache cache, IpfsFile file)
	{
		try
		{
			cache.removeFromThisCache(file).get();
		}
		catch (IpfsConnectionException e)
		{
			environment.logError("WARNING: Error unpinning " + file + ".  This will need to be done manually.");
		}
	}


	private static record CleanupData(IpfsFile indexHash, IpfsFile oldRootHash) {}
}
