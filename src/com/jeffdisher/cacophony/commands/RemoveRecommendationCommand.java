package com.jeffdisher.cacophony.commands;

import java.io.ByteArrayInputStream;

import com.jeffdisher.cacophony.access.IWritingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.IEnvironment.IOperationLog;
import com.jeffdisher.cacophony.scheduler.FuturePublish;
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
		
		try (IWritingAccess access = StandardAccess.writeAccess(environment))
		{
			IOperationLog log = environment.logOperation("Removing recommendation " + _channelPublicKey + "...");
			CleanupData cleanup = _runCore(environment, access);
			
			// By this point, we have completed the essential network operations (everything else is local state and network clean-up).
			_runFinish(environment, access, cleanup);
			log.finish("No longer recommending: " + _channelPublicKey);
		}
	}


	private CleanupData _runCore(IEnvironment environment, IWritingAccess access) throws IpfsConnectionException
	{
		LocalIndex localIndex = access.readOnlyLocalIndex();
		
		// Read the existing StreamIndex.
		IpfsFile rootToLoad = localIndex.lastPublishedIndex();
		Assert.assertTrue(null != rootToLoad);
		StreamIndex index = access.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data)).get();
		IpfsFile originalRecommendations = IpfsFile.fromIpfsCid(index.getRecommendations());
		
		// Read the existing recommendations list.
		StreamRecommendations recommendations = access.loadCached(originalRecommendations, (byte[] data) -> GlobalData.deserializeRecommendations(data)).get();
		
		// Verify that they are already in the list.
		Assert.assertTrue(recommendations.getUser().contains(_channelPublicKey.toPublicKey()));
		
		// Remove the channel.
		recommendations.getUser().remove(_channelPublicKey.toPublicKey());
		
		// Serialize and upload the description.
		byte[] rawRecommendations = GlobalData.serializeRecommendations(recommendations);
		IpfsFile hashDescription = access.uploadAndPin(new ByteArrayInputStream(rawRecommendations), true);
		
		// Update, save, and publish the new index.
		index.setRecommendations(hashDescription.toSafeString());
		environment.logToConsole("Saving and publishing new index");
		FuturePublish asyncPublish = access.uploadStoreAndPublishIndex(index);
		return new CleanupData(asyncPublish, rootToLoad, originalRecommendations);
	}

	private void _runFinish(IEnvironment environment, IWritingAccess access, CleanupData data) throws IpfsConnectionException
	{
		// Remove the previous recommendations from cache (index handled below).
		access.unpin(data.originalRecommendations);
		
		// Unpin the previous index.
		access.unpin(data.oldRootHash);
		
		// See if the publish actually succeeded (we still want to update our local state, even if it failed).
		CommandHelpers.commonWaitForPublish(environment, data.asyncPublish);
	}


	private static record CleanupData(FuturePublish asyncPublish, IpfsFile oldRootHash, IpfsFile originalRecommendations) {}
}
