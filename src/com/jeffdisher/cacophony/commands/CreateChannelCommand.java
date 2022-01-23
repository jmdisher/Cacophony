package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String ipfs, String keyName) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		// Make sure that there is no local index in this location.
		LocalIndex index = local.readIndex();
		if (null != index)
		{
			executor.fatalError(new Exception("Index already exists"));
		}
		Assert.assertTrue(null == index);
		
		// Save the local config.
		index = new LocalIndex(ipfs, keyName);
		local.storeIndex(index);
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		HighLevelCache cache = HighLevelCache.fromLocal(local);
		
		// Create the empty description, recommendations, record stream, and index.
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		// TODO:  Make this into a question mark icon, or something.
		// (for now, this is just a 0-byte file).
		description.setPicture("QmbFMke1KXqnYyBBWxB74N4c5SBnJMVAiMNRcGu6x1AwQH");
		
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
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishNewIndex(executor, remote, hashDescription, hashRecommendations, hashRecords);
		cache.uploadedToThisCache(indexHash);
	}
}
