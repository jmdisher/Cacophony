package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.v1.GlobalPinCache;
import com.jeffdisher.cacophony.data.local.v1.HighLevelCache;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IConnection;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		IOperationLog log = environment.logOperation("Adding recommendation " + _channelPublicKey + "...");
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		HighLevelCache cache = new HighLevelCache(pinCache, connection);
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, pinCache, connection);
		
		// Read our existing root key.
		IpfsFile oldRootHash = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != oldRootHash);
		StreamIndex index = GlobalData.deserializeIndex(checker.loadCached(oldRootHash));
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid(index.getRecommendations());
		
		// Read the existing recommendations list.
		byte[] rawRecommendations = checker.loadCached(originalRecommendations);
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(rawRecommendations);
		
		// Verify that we didn't already add them.
		Assert.assertTrue(!recommendations.getUser().contains(_channelPublicKey.toPublicKey()));
		
		// Add the new channel.
		recommendations.getUser().add(_channelPublicKey.toPublicKey());
		
		// Serialize and upload the description.
		rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		IpfsFile hashDescription = remote.saveData(rawRecommendations);
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setRecommendations(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(remote, index);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), indexHash));
		cache.uploadedToThisCache(indexHash);
		
		// Remove the previous index and recommendations from cache.
		cache.removeFromThisCache(originalRecommendations);
		cache.removeFromThisCache(oldRootHash);
		local.writeBackConfig();
		log.finish("Now recommending: " + _channelPublicKey);
	}
}
