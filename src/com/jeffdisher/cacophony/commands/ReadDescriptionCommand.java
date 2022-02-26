package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		IpfsFile rootToLoad = null;
		if (null != _channelPublicKey)
		{
			publicKey = _channelPublicKey;
			FollowIndex followIndex = local.loadFollowIndex();
			rootToLoad = followIndex.getLastFetchedRoot(_channelPublicKey);
			if (null == rootToLoad)
			{
				throw new UsageException("Given public key (" + _channelPublicKey.toPublicKey() + ") is not being followed");
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
		}
		StreamIndex index = GlobalData.deserializeIndex(remote.readData(rootToLoad));
		byte[] rawDescription = remote.readData(IpfsFile.fromIpfsCid(index.getDescription()));
		StreamDescription description = GlobalData.deserializeDescription(rawDescription);
		executor.logToConsole("Channel public key: " + publicKey);
		executor.logToConsole("-name: " + description.getName());
		executor.logToConsole("-description: " + description.getDescription());
		executor.logToConsole("-picture: " + description.getPicture());
	}
}
