package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String ipfs, String keyName) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != ipfs);
		Assert.assertTrue(null != keyName);
		
		IOperationLog log = environment.logOperation("Creating new channel...");
		LocalConfig local = environment.createNewConfig(ipfs, keyName);
		IConnection connection = local.getSharedConnection();
		// Check to see if this key exists.
		List<IConnection.Key> keys = connection.getKeys();
		boolean keyExists = keys.stream().anyMatch((k) -> k.name().equals(keyName));
		if (keyExists)
		{
			environment.logToConsole("Using existing key: \"" + keyName + "\"");
		}
		else
		{
			IOperationLog keyLog = environment.logOperation("Key \"" + keyName + "\" not found.  Generating...");
			IConnection.Key key = connection.generateKey(keyName);
			keyLog.finish("Public key \"" + key.key() + "\" generated with name: \"" + key.name() + "\"");
		}
		// Make sure that there is no local index in this location.
		LocalIndex index = local.readLocalIndex();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, index.keyName());
		HighLevelCache cache = new HighLevelCache(local.loadGlobalPinCache(), scheduler);
		
		// Create the empty description, recommendations, record stream, and index.
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		InputStream pictureStream = CreateChannelCommand.class.getResourceAsStream("/resources/unknown_user.png");
		Assert.assertTrue(null != pictureStream);
		IpfsFile pictureHash = scheduler.saveStream(pictureStream, true).get();
		cache.uploadedToThisCache(pictureHash);
		description.setPicture(pictureHash.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		
		// Save these.
		byte[] rawDescription = GlobalData.serializeDescription(description);
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		byte[] rawRecords = GlobalData.serializeRecords(records);
		
		IpfsFile hashDescription = scheduler.saveStream(new ByteArrayInputStream(rawDescription), true).get();
		cache.uploadedToThisCache(hashDescription);
		IpfsFile hashRecommendations = scheduler.saveStream(new ByteArrayInputStream(rawRecommendations), true).get();
		cache.uploadedToThisCache(hashRecommendations);
		IpfsFile hashRecords = scheduler.saveStream(new ByteArrayInputStream(rawRecords), true).get();
		cache.uploadedToThisCache(hashRecords);
		
		// Create the new local index.
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(hashDescription.toSafeString());
		streamIndex.setRecommendations(hashRecommendations.toSafeString());
		streamIndex.setRecords(hashRecords.toSafeString());
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, streamIndex);
		// Update the local index.
		LocalIndex localIndex = local.readLocalIndex();
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), indexHash));
		cache.uploadedToThisCache(indexHash);
		local.writeBackConfig();
		log.finish("Channel created and published to Cacophony!");
	}
}
