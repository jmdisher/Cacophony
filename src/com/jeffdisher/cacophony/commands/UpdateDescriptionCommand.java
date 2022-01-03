package com.jeffdisher.cacophony.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.utils.Types;

import io.ipfs.multihash.Multihash;


public record UpdateDescriptionCommand(String _name, String _description, File _picturePath) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// Read the existing StreamIndex.
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, remote.getPublicKey());
		
		// Read the existing description since we might be only partially updating it.
		byte[] rawDescription = remote.readData(Types.fromIpfsCid(index.getDescription()));
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
			Multihash pictureHash = HighLevelIdioms.saveData(executor, remote, rawData);
			description.setPicture(pictureHash.toBase58());
		}
		
		// Serialize and upload the description.
		rawDescription = GlobalData.serializeDescription(description);
		Multihash hashDescription = HighLevelIdioms.saveData(executor, remote, rawDescription);
		
		// Update, save, and publish the new index.
		index.setDescription(hashDescription.toBase58());
		HighLevelIdioms.saveAndPublishIndex(executor, remote, index);
	}
}
