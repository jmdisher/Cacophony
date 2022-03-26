package com.jeffdisher.cacophony.commands;

import java.io.IOException;
import java.io.InputStream;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.logic.Executor.IOperationLog;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String ipfs, String keyName) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		IOperationLog log = executor.logOperation("Creating new channel...");
		// Make sure that there is no local index in this location.
		LocalIndex index = local.readIndex();
		if (null != index)
		{
			executor.fatalError(new Exception("Index already exists"));
		}
		Assert.assertTrue(null == index);
		
		// Save the local config.
		index = new LocalIndex(ipfs, keyName, null);
		local.storeIndex(index);
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local.getSharedConnection(), index.keyName());
		HighLevelCache cache = new HighLevelCache(local.loadGlobalPinCache(), local.getSharedPinMechanism());
		
		// Create the empty description, recommendations, record stream, and index.
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		InputStream pictureStream = CreateChannelCommand.class.getResourceAsStream("/resources/unknown_user.png");
		Assert.assertTrue(null != pictureStream);
		IpfsFile pictureHash = remote.saveStream(pictureStream);
		cache.uploadedToThisCache(pictureHash);
		description.setPicture(pictureHash.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		
		// Save these.
		byte[] rawDescription = GlobalData.serializeDescription(description);
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		byte[] rawRecords = GlobalData.serializeRecords(records);
		
		IpfsFile hashDescription = HighLevelIdioms.saveData(executor, remote, rawDescription);
		cache.uploadedToThisCache(hashDescription);
		IpfsFile hashRecommendations = HighLevelIdioms.saveData(executor, remote, rawRecommendations);
		cache.uploadedToThisCache(hashRecommendations);
		IpfsFile hashRecords = HighLevelIdioms.saveData(executor, remote, rawRecords);
		cache.uploadedToThisCache(hashRecords);
		
		// Create the new local index.
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishNewIndex(executor, remote, local, hashDescription, hashRecommendations, hashRecords);
		cache.uploadedToThisCache(indexHash);
		log.finish("Channel created and published to Cacophony!");
	}
}
