package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.projection.IFolloweeReading;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.FailedDeserializationException;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.KeyException;
import com.jeffdisher.cacophony.utils.Assert;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public boolean requiresKey()
	{
		return false;
	}

	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			_runCore(environment, access);
		}
	}


	private void _runCore(IEnvironment environment, IReadingAccess access) throws IpfsConnectionException, KeyException, FailedDeserializationException
	{
		IFolloweeReading followees = access.readableFolloweeData();
		
		// See if this is our key or one we are following (we can only do this list for channels we are following since
		// we only want to read data we already pinned).
		IpfsKey publicKey = null;
		IpfsFile rootToLoad = null;
		boolean isCached = false;
		if (null != _channelPublicKey)
		{
			publicKey = _channelPublicKey;
			IpfsFile lastRoot = followees.getLastFetchedRootForFollowee(_channelPublicKey);
			if (null != lastRoot)
			{
				environment.logToConsole("Following " + _channelPublicKey);
				rootToLoad = lastRoot;
				isCached = true;
			}
			else
			{
				environment.logToConsole("NOT following " + _channelPublicKey);
				rootToLoad = access.resolvePublicKey(_channelPublicKey).get();
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
			publicKey = access.getPublicKey();
			rootToLoad = access.getLastRootElement();
			Assert.assertTrue(null != rootToLoad);
		}
		StreamIndex index = (isCached
				? access.loadCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
				: access.loadNotCached(rootToLoad, (byte[] data) -> GlobalData.deserializeIndex(data))
		).get();
		StreamDescription description = (isCached
				? access.loadCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data))
				: access.loadNotCached(IpfsFile.fromIpfsCid(index.getDescription()), (byte[] data) -> GlobalData.deserializeDescription(data))
		).get();
		environment.logToConsole("Channel public key: " + publicKey);
		environment.logToConsole("-name: " + description.getName());
		environment.logToConsole("-description: " + description.getDescription());
		environment.logToConsole("-picture: " + description.getPicture());
	}
}
