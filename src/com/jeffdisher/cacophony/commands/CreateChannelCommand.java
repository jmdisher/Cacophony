package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;
import java.util.Map;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.data.global.AbstractDescription;
import com.jeffdisher.cacophony.data.global.AbstractIndex;
import com.jeffdisher.cacophony.data.global.AbstractRecommendations;
import com.jeffdisher.cacophony.data.global.AbstractRecords;
import com.jeffdisher.cacophony.types.IConnection;
import com.jeffdisher.cacophony.types.ILogger;
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
		// We are performing a very low-level operation so we need to reach down into the IConnection object.
		IConnection connection = context.basicConnection;
		Map<String, IpfsKey> existingKeys = connection.getLocalPublicKeys();
		IpfsKey publicKey = existingKeys.get(_keyName);
		if (null == publicKey)
		{
			publicKey = connection.generateLocalPublicKey(_keyName);
		}
		// This will fail with exception, never null.
		Assert.assertTrue(null != publicKey);
		setupLog.logFinish("Key setup done:  " + publicKey);
		
		ILogger log = context.logger.logStart("Creating initial channel state...");
		AbstractDescription description;
		IpfsFile newRoot;
		try (IWritingAccess access = StandardAccess.writeAccessWithKeyOverride(context.basicConnection, context.scheduler, context.logger, context.sharedDataModel, _keyName, publicKey))
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
			newRoot = _runCore(access, description);
		}
		
		context.cacheUpdater.addedHomeUser(publicKey, description);
		
		context.setSelectedKey(publicKey);
		log.logFinish("Initial state published to Cacophony!");
		return new ChangedRoot(newRoot);
	}


	private AbstractDescription _defaultDescription(IWritingAccess access) throws IpfsConnectionException
	{
		AbstractDescription description = AbstractDescription.createNew();
		description.setName("Unnamed");
		description.setDescription("Description forthcoming");
		return description;
	}

	private IpfsFile _runCore(IWritingAccess access, AbstractDescription description) throws IpfsConnectionException
	{
		AbstractRecommendations recommendations = AbstractRecommendations.createNew();
		
		AbstractRecords records = AbstractRecords.createNew();
		
		// Save these.
		byte[] rawDescription;
		try
		{
			rawDescription = description.serializeV2();
		}
		catch (SizeConstraintException e)
		{
			// This is default so it can't happen.
			throw Assert.unexpected(e);
		}
		
		byte[] rawRecommendations;
		try
		{
			rawRecommendations = recommendations.serializeV2();
		}
		catch (SizeConstraintException e)
		{
			// This is empty so it can't happen.
			throw Assert.unexpected(e);
		}
		
		byte[] rawRecords;
		try
		{
			rawRecords = records.serializeV2();
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
		AbstractIndex streamIndex = AbstractIndex.createNew();
		streamIndex.descriptionCid = hashDescription;
		streamIndex.recommendationsCid = hashRecommendations;
		streamIndex.recordsCid = hashRecords;
		
		return access.uploadIndexAndUpdateTracking(streamIndex);
	}
}
