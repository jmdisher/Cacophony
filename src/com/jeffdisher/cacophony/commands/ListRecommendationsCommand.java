package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ListRecommendationsCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		LoadChecker checker = new LoadChecker(remote, local);
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _publicKey)
		{
			publicKey = _publicKey;
			FollowIndex followIndex = local.loadFollowIndex();
			IpfsFile root = followIndex.getLastFetchedRoot(_publicKey);
			if (null != root)
			{
				executor.logToConsole("Following " + _publicKey);
				rootToLoad = root;
				isCached = true;
			}
			else
			{
				executor.logToConsole("NOT following " + _publicKey);
				rootToLoad = remote.resolvePublicKey(_publicKey);
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _publicKey);
				}
			}
		}
		else
		{
			// Just list our recommendations.
			// Read the existing StreamIndex.
			publicKey = remote.getPublicKey();
			LocalIndex localIndex = local.readIndex();
			rootToLoad = localIndex.lastPublishedIndex();
			Assert.assertTrue(null != rootToLoad);
			isCached = true;
		}
		StreamIndex index = GlobalData.deserializeIndex(isCached ? checker.loadCached(rootToLoad) : checker.loadNotCached(rootToLoad));
		
		// Read the existing recommendations list.
		byte[] rawRecommendations = isCached
				? checker.loadCached(IpfsFile.fromIpfsCid(index.getRecommendations()))
				: checker.loadNotCached(IpfsFile.fromIpfsCid(index.getRecommendations()))
		;
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(rawRecommendations);
		
		// Walk the recommendations and print their keys to the console.
		executor.logToConsole("Recommendations of " + publicKey.toPublicKey());
		for (String rawKey : recommendations.getUser())
		{
			executor.logToConsole("\t" + rawKey);
		}
	}
}
