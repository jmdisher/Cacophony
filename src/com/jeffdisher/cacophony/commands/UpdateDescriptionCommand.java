package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		IpfsFile[] previousIndexFile = new IpfsFile[1];
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, publicKey, previousIndexFile);
		cache.removeFromThisCache(previousIndexFile[0]);
		
		// Read the existing description since we might be only partially updating it.
		byte[] rawDescription = remote.readData(IpfsFile.fromIpfsCid(index.getDescription()));
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
			IpfsFile pictureHash = HighLevelIdioms.saveData(executor, remote, rawData);
			cache.uploadedToThisCache(pictureHash);
			description.setPicture(pictureHash.cid().toString());
		}
		
		// Serialize and upload the description.
		rawDescription = GlobalData.serializeDescription(description);
		IpfsFile hashDescription = HighLevelIdioms.saveData(executor, remote, rawDescription);
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setDescription(hashDescription.cid().toString());
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(executor, remote, index);
		cache.uploadedToThisCache(indexHash);
	}
}
