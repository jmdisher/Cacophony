package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.global.recommendations.StreamRecommendations;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ListRecommendationsCommand() implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// Read the existing StreamIndex.
		IpfsKey publicKey = remote.getPublicKey();
		IpfsFile[] previousIndexFile = new IpfsFile[1];
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, publicKey, previousIndexFile);
		
		// Read the existing recommendations list.
		byte[] rawRecommendations = remote.readData(IpfsFile.fromIpfsCid(index.getRecommendations()));
		StreamRecommendations recommendations = GlobalData.deserializeRecommendations(rawRecommendations);
		
		// Walk the recommendations and print their keys to the console.
		for (String rawKey : recommendations.getUser())
		{
			executor.logToConsole("Recommending: " + rawKey);
		}
	}
}
