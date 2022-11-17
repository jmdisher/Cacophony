package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;

import com.jeffdisher.cacophony.data.IReadWriteLocalData;
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
import com.jeffdisher.cacophony.scheduler.FuturePublish;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record RemoveRecommendationCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException, IpfsConnectionException
	{
		Assert.assertTrue(null != _channelPublicKey);
		
		IOperationLog log = environment.logOperation("Removing recommendation " + _channelPublicKey + "...");
		LocalConfig local = environment.loadExistingConfig();
		IReadWriteLocalData data = local.getSharedLocalData().openForWrite();
		LocalIndex localIndex = data.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = data.readGlobalPinCache();
		INetworkScheduler scheduler = environment.getSharedScheduler(connection, localIndex.keyName());
		HighLevelCache cache = new HighLevelCache(pinCache, scheduler, connection);
		CleanupData cleanup = _runCore(environment, scheduler, localIndex, cache);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		_runFinish(environment, local, localIndex, cache, cleanup, data);
		data.writeGlobalPinCache(pinCache);
		data.close();
		log.finish("No longer recommending: " + _channelPublicKey);
	}


	private CleanupData _runCore(IEnvironment environment, INetworkScheduler scheduler, LocalIndex localIndex, HighLevelCache cache) throws IpfsConnectionException
	{
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = cache.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid(index.getRecommendations());
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = cache.loadCached(originalRecommendations, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		
		// Verify that they are already in the list.
		Assert.assertTrue(recommendations.getUser().contains(_channelPublicKey.toPublicKey()));
		
		// Remove the channel.
		recommendations.getUser().remove(_channelPublicKey.toPublicKey());
		
		// Serialize and upload the description.
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		IpfsFile hashDescription = scheduler.saveStream(new ByteArrayInputStream(rawRecommendations), true).get();
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setRecommendations(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncPublish = CommandHelpers.serializeSaveAndPublishIndex(environment, scheduler, index);
		return new CleanupData(asyncPublish, rootToLoad, originalRecommendations);
	}

	private void _runFinish(IEnvironment environment, LocalConfig local, LocalIndex localIndex, HighLevelCache cache, CleanupData data, IReadWriteLocalData localData)
	{
		// Remove the previous recommendations from cache (index handled below).
		CommandHelpers.safeRemoveFromLocalNode(environment, cache, data.originalRecommendations);
		
		// Do the local storage update while the publish continues in the background (even if it fails, we still want to update local storage).
		CommandHelpers.commonUpdateIndex(environment, localData, localIndex, cache, data.oldRootHash, data.asyncPublish.getIndexHash());
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, data.asyncPublish);
	}


	private static record CleanupData(FuturePublish asyncPublish, IpfsFile oldRootHash, IpfsFile originalRecommendations) {}
}
