package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.global.records.StreamRecords;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record CreateChannelCommand(String keyName) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(ICommand.Context context) throws IpfsConnectionException, UsageException
	{
		if (null == keyName)
		{
			throw new UsageException("Key name must be provided");
		}
		
		// Make sure that we aren't going to over-write an existing structure.
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			if (null != access.getLastRootElement())
			{
				throw new UsageException("Channel already exists for the IPFS key named: \"" + keyName + "\"");
			}
		}
		
		// First, we want to verify that we can contact the server and configure our publication key.
		// Before we have a publication key, we can't really configure any of the other communication and data abstractions we need.
		ILogger setupLog = context.logger.logStart("Verifying IPFS and setting up public key called \"" + keyName + "\"");
		_setupKey(context.environment, setupLog);
		setupLog.logFinish("Key setup done!");
		
		ILogger log = context.logger.logStart("Creating initial channel state...");
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccess(context))
		{
			newRoot = _runCore(context.environment, access);
		}
		log.logFinish("Initial state published to Cacophony!");
		return new ChangedRoot(newRoot);
	}


	private void _setupKey(IEnvironment environment, ILogger logger) throws IpfsConnectionException
	{
		IConnection connection = environment.getConnection();
		
		IpfsKey publicKey = connection.getOrCreatePublicKey(this.keyName);
		logger.logVerbose("Using existing key for \"" + keyName + "\": " + publicKey);
	}

	private IpfsFile _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException
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
		
		return access.uploadIndexAndUpdateTracking(streamIndex);
	}
}
