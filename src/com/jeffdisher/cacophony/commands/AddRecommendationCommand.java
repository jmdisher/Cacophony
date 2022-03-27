package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.HighLevelCache;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record AddRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		IOperationLog log = environment.logOperation("Adding recommendation " + _channelPublicKey + "...");
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		LoadChecker checker = new LoadChecker(remote, local.loadGlobalPinCache(), local.getSharedConnection());
		HighLevelCache cache = new HighLevelCache(local.loadGlobalPinCache(), local.getSharedConnection());
		
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
		IpfsFile hashDescription = HighLevelIdioms.saveData(remote, rawRecommendations);
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setRecommendations(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = HighLevelIdioms.saveAndPublishIndex(remote, local, index);
		cache.uploadedToThisCache(indexHash);
		
		// Remove the previous index and recommendations from cache.
		cache.removeFromThisCache(originalRecommendations);
		cache.removeFromThisCache(oldRootHash);
		local.writeBackConfig();
		log.finish("Now recommending: " + _channelPublicKey);
	}
}
