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
import com.jeffdisher.cacophony.scheduler.SingleThreadedScheduler;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
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
		LocalIndex localIndex = local.readLocalIndex();
		IConnection connection = local.getSharedConnection();
		GlobalPinCache pinCache = local.loadGlobalPinCache();
		HighLevelCache cache = new HighLevelCache(pinCache, connection);
		CleanupData cleanup = _runCore(environment, connection, localIndex, pinCache, cache);
		
		// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
		_runFinish(environment, local, localIndex, cache, cleanup);
		log.finish("No longer recommending: " + _channelPublicKey);
	}


	private CleanupData _runCore(IEnvironment environment, IConnection connection, LocalIndex localIndex, GlobalPinCache pinCache, HighLevelCache cache) throws IpfsConnectionException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, connection, localIndex.keyName());
		LoadChecker checker = new LoadChecker(new SingleThreadedScheduler(remote), pinCache, connection);
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = checker.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid(index.getRecommendations());
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = checker.loadCached(originalRecommendations, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		
		// Verify that they are already in the list.
		Assert.assertTrue(recommendations.getUser().contains(_channelPublicKey.toPublicKey()));
		
		// Remove the channel.
		recommendations.getUser().remove(_channelPublicKey.toPublicKey());
		
		// Serialize and upload the description.
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		IpfsFile hashDescription = remote.saveData(rawRecommendations);
		cache.uploadedToThisCache(hashDescription);
		
		// Update, save, and publish the new index.
		index.setRecommendations(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		IpfsFile indexHash = CommandHelpers.serializeSaveAndPublishIndex(environment, remote, index);
		return new CleanupData(indexHash, rootToLoad, originalRecommendations);
	}

	private void _runFinish(IEnvironment environment, LocalConfig local, LocalIndex localIndex, HighLevelCache cache, CleanupData data)
	{
		// Update the local index.
		local.storeSharedIndex(new LocalIndex(localIndex.ipfsHost(), localIndex.keyName(), data.indexHash));
		cache.uploadedToThisCache(data.indexHash);
		
		// Remove the previous file from cache.
		_safeRemove(environment, cache, data.originalRecommendations);
		_safeRemove(environment, cache, data.oldRootHash);
		local.writeBackConfig();
	}

	private static void _safeRemove(IEnvironment environment, HighLevelCache cache, IpfsFile file)
	{
		try
		{
			cache.removeFromThisCache(file);
		}
		catch (IpfsConnectionException e)
		{
			environment.logError("WARNING: Error unpinning " + file + ".  This will need to be done manually.");
		}
	}


	private static record CleanupData(IpfsFile indexHash, IpfsFile oldRootHash, IpfsFile originalRecommendations) {}
}
