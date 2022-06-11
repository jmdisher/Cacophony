package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LoadChecker;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.scheduler.SingleThreadedScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		INetworkScheduler scheduler = new SingleThreadedScheduler(remote);
		LoadChecker checker = new LoadChecker(scheduler, local.loadGlobalPinCache(), local.getSharedConnection());
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			publicKey = _channelPublicKey;
			FollowIndex followIndex = local.loadFollowIndex();
			rootToLoad = followIndex.getLastFetchedRoot(_channelPublicKey);
			if (null != rootToLoad)
			{
				environment.logToConsole("Following " + _channelPublicKey);
				isCached = true;
			}
			else
			{
				environment.logToConsole("NOT following " + _channelPublicKey);
				rootToLoad = scheduler.resolvePublicKey(_channelPublicKey).get();
				// If this failed to resolve, through a key exception.
				if (null == rootToLoad)
				{
					throw new KeyException("Failed to resolve key: " + _channelPublicKey);
				}
				isCached = false;
			}
		}
		else
		{
			// Just list our recommendations.
			// Read the existing StreamIndex.
			publicKey = remote.getPublicKey();
			rootToLoad = localIndex.lastPublishedIndex();
			Assert.assertTrue(null != rootToLoad);
		}
		StreamIndex index = (isCached
				? checker.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
				: checker.loadNotCached(environment, rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
		).get();
		StreamDescription description = (isCached
				? checker.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data))
				:checker.loadNotCached(environment, IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data))
		).get();
		environment.logToConsole("Channel public key: " + publicKey);
		environment.logToConsole("-name: " + description.getName());
		environment.logToConsole("-description: " + description.getDescription());
		environment.logToConsole("-picture: " + description.getPicture());
	}
}
