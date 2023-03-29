package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String keyName) implements ICommand<None>
{
	@Override
	public None runInEnvironment(IEnvironment environment) throws IpfsConnectionException, UsageException
	{
		Assert.assertTrue(null != keyName);
		
		// Make sure that we aren't going to over-write an existing structure.
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			if (null != access.getLastRootElement())
			{
				throw new UsageException("Channel already exists for the IPFS key named: \"" + keyName + "\"");
			}
		}
		
		// First, we want to verify that we can contact the server and configure our publication key.
		// Before we have a publication key, we can't really configure any of the other communication and data abstractions we need.
		IEnvironment.IOperationLog setupLog = environment.logStart("Verifying IPFS and setting up public key called \"" + keyName + "\"");
		_setupKey(environment);
		setupLog.logFinish("Key setup done!");
		
		IEnvironment.IOperationLog log = environment.logStart("Creating initial channel state...");
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			_runCore(environment, access);
		}
		log.logFinish("Initial state published to Cacophony!");
		return None.NONE;
	}


	private void _setupKey(IEnvironment environment) throws IpfsConnectionException
	{
		IConnection connection = environment.getConnection();
		
		// Check to see if this key exists.
		List<IConnection.Key> keys = connection.getKeys();
		boolean keyExists = keys.stream().anyMatch((k) -> k.name().equals(keyName));
		// The key now ALWAYS exists since we create it in the pre-command phase.
		Assert.assertTrue(keyExists);
		environment.logVerbose("Using existing key: \"" + keyName + "\"");
	}

	private void _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException
	{
		// Create the empty description, recommendations, record stream, and index.
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		InputStream pictureStream = CreateChannelCommand.class.getResourceAsStream("/resources/unknown_user.png");
		Assert.assertTrue(null != pictureStream);
		IpfsFile pictureHash = access.uploadAndPin(pictureStream);
		description.setPicture(pictureHash.toSafeString());
		
		StreamRecommendations recommendations = new StreamRecommendations();
		
		StreamRecords records = new StreamRecords();
		
		// Save these.
		byte[] rawDescription;
		try
		{
			rawDescription = GlobalData.serializeDescription(description);
		}
		catch (SizeConstraintException e)
		{
			// This is default so it can't happen.
			throw Assert.unexpected(e);
		}
		
		byte[] rawRecommendations;
		try
		{
			rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		}
		catch (SizeConstraintException e)
		{
			// This is empty so it can't happen.
			throw Assert.unexpected(e);
		}
		
		byte[] rawRecords;
		try
		{
			rawRecords = GlobalData.serializeRecords(records);
		}
		catch (SizeConstraintException e)
		{
			// This is empty so it can't happen.
			throw Assert.unexpected(e);
		}
		
		IpfsFile hashDescription = access.uploadAndPin(new ByteArrayInputStream(rawDescription));
		IpfsFile hashRecommendations = access.uploadAndPin(new ByteArrayInputStream(rawRecommendations));
		IpfsFile hashRecords = access.uploadAndPin(new ByteArrayInputStream(rawRecords));
		
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
