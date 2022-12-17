package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String ipfs, String keyName) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != ipfs);
		Assert.assertTrue(null != keyName);
		
		IOperationLog log = environment.logOperation("Creating new channel configuration...");
		StandardAccess.createNewChannelConfig(environment, ipfs, keyName);
		log.finish("Config ready!");
		
		log = environment.logOperation("Creating initial channel state...");
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
		log.finish("Initial state published to Cacophony!");
	}


	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException, SizeConstraintException, UsageException
	{
		IConnection connection = access.connection();
		
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
		
		// Create the empty description, recommendations, record stream, and index.
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		InputStream pictureStream = CreateChannelCommand.class.getResourceAsStream("/resources/unknown_user.png");
		Assert.assertTrue(null != pictureStream);
		IpfsFile pictureHash = access.uploadAndPin(pictureStream, true);
		description.setPicture(pictureHash.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		
		// Save these.
		byte[] rawDescription = GlobalData.serializeDescription(description);
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		byte[] rawRecords = GlobalData.serializeRecords(records);
		
		IpfsFile hashDescription = access.uploadAndPin(new ByteArrayInputStream(rawDescription), true);
		IpfsFile hashRecommendations = access.uploadAndPin(new ByteArrayInputStream(rawRecommendations), true);
		IpfsFile hashRecords = access.uploadAndPin(new ByteArrayInputStream(rawRecords), true);
		
		// Create the new local index.
		StreamIndex streamIndex = new StreamIndex();
		streamIndex.setVersion(1);
		streamIndex.setDescription(hashDescription.toSafeString());
		streamIndex.setRecommendations(hashRecommendations.toSafeString());
		streamIndex.setRecords(hashRecords.toSafeString());
		
		IpfsFile newRoot = access.uploadIndexAndUpdateTracking(streamIndex);
		FuturePublish asyncPublish = access.beginIndexPublish(newRoot);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, asyncPublish);
	}
}
