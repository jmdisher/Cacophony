package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record ListRecommendationsCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		StreamIndex index = null;
		if (null != _publicKey)
		{
			publicKey = _publicKey;
			FollowIndex followIndex = local.loadFollowIndex();
			IpfsFile root = followIndex.getLastFetchedRoot(_publicKey);
			if (null == root)
			{
				throw new UsageException("Given public key (" + _publicKey.toPublicKey() + ") is not being followed");
			}
			index = GlobalData.deserializeIndex(remote.readData(root));
		}
		else
		{
			// Just list our recommendations.
			// Read the existing StreamIndex.
			publicKey = remote.getPublicKey();
			index = HighLevelIdioms.readIndexForKey(remote, publicKey, null);
		}
		
		// Read the existing recommendations list.
		byte[] rawRecommendations = remote.readData(IpfsFile.fromIpfsCid(index.getRecommendations()));
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(rawRecommendations);
		
		// Walk the recommendations and print their keys to the console.
		executor.logToConsole("Recommendations of " + publicKey.toPublicKey());
		for (String rawKey : recommendations.getUser())
		{
			executor.logToConsole("\t" + rawKey);
		}
	}
}
