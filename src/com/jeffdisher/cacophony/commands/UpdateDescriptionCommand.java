package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath, String _email, String _website) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		Assert.assertTrue((null != _name) || (null != _description) || (null != _picturePath) || (null != _email) || (null != _website));
		if (null != _picturePath)
		{
			Assert.assertTrue(_picturePath.isFile());
		}
		
		IOperationLog log = environment.logOperation("Updating channel description...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local.loadGlobalPinCache(), local.getSharedConnection());
		HighLevelCache cache = new HighLevelCache(local.loadGlobalPinCache(), local.getSharedConnection());
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(rootToLoad));
		
		// Read the existing description since we might be only partially updating it.
		byte[] rawDescription = checker.loadCached(IpfsFile.fromIpfsCid(index.getDescription()));
		StreamDescription description = GlobalData.deserializeDescription(rawDescription);
		
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
			byte[] rawData = Files.readAllBytes(_picturePath.toPath());
			IpfsFile pictureHash = HighLevelIdioms.saveData(remote, rawData);
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
		rawDescription = GlobalData.serializeDescription(description);
		IpfsFile hashDescription = HighLevelIdioms.saveData(remote, rawDescription);
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setDescription(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(remote, local, index);
		cache.uploadedToThisCache(indexHash);
		
		// Remove old root.
		cache.removeFromThisCache(rootToLoad);
		local.writeBackConfig();
		log.finish("Update completed!");
	}
}
