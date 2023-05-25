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
import com.jeffdisher.cacophony.logic.LocalRecordCacheBuilder;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.SizeConstraintException;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;
import com.jeffdisher.cacophony.utils.KeyNameRules;


public record CreateChannelCommand(String _keyName) implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		KeyNameRules.validateKey(_keyName);
		
		// First, we want to verify that we can contact the server and configure our publication key.
		// Before we have a publication key, we can't really configure any of the other communication and data abstractions we need.
		ILogger setupLog = context.logger.logStart("Verifying IPFS and setting up public key called \"" + _keyName + "\"");
		IConnection connection = context.environment.getConnection();
		IpfsKey publicKey = connection.getOrCreatePublicKey(_keyName);
		// This will fail with exception, never null.
		Assert.assertTrue(null != publicKey);
		setupLog.logFinish("Key setup done:  " + publicKey);
		
		ILogger log = context.logger.logStart("Creating initial channel state...");
		StreamDescription description;
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccessWithKeyOverride(context, _keyName, publicKey))
		{
			// Make sure that this isn't something which already exists.
			for (IReadingAccess.HomeUserTuple tuple : access.readHomeUserData())
			{
				if (tuple.keyName().equals(_keyName))
				{
					throw new UsageException("Channel already exists for the IPFS key named: \"" + _keyName + "\"");
				}
			}
			// Create the empty description, recommendations, record stream, and index.
			description = _defaultDescription(access);
			newRoot = _runCore(context.environment, access, description);
		}
		
		// If the cache exists, populate it.
		if (null != context.userInfoCache)
		{
			LocalRecordCacheBuilder.populateUserInfoFromDescription(context.userInfoCache, publicKey, description);
		}
		if (null != context.entryRegistry)
		{
			context.entryRegistry.createHomeUser(publicKey);
		}
		
		context.setSelectedKey(publicKey);
		log.logFinish("Initial state published to Cacophony!");
		return new ChangedRoot(newRoot);
	}


	private StreamDescription _defaultDescription(IWritingAccess access) throws IpfsConnectionException
	{
		StreamDescription description = new StreamDescription();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		InputStream pictureStream = CreateChannelCommand.class.getResourceAsStream("/resources/unknown_user.png");
		Assert.assertTrue(null != pictureStream);
		IpfsFile pictureHash = access.uploadAndPin(pictureStream);
		description.setPicture(pictureHash.toSafeString());
		return description;
	}

	private IpfsFile _runCore(IEnvironment environment, IWritingAccess access, StreamDescription description) throws IpfsConnectionException
	{
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
