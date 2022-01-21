package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.global.GlobalData;
import com.jeffdisher.cacophony.data.global.description.StreamDescription;
import com.jeffdisher.cacophony.data.global.index.StreamIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.HighLevelIdioms;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.IpfsKey;


public record ReadDescriptionCommand(IpfsKey _channelPublicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		RemoteActions remote = RemoteActions.loadIpfsConfig(local);
		IpfsKey keyToResolve = (null != _channelPublicKey)
				? _channelPublicKey
				: remote.getPublicKey()
		;
		StreamIndex index = HighLevelIdioms.readIndexForKey(remote, keyToResolve);
		byte[] rawDescription = remote.readData(IpfsFile.fromIpfsCid(index.getDescription()));
		StreamDescription description = GlobalData.deserializeDescription(rawDescription);
		System.out.println("Channel public key: " + keyToResolve);
		System.out.println("-name: " + description.getName());
		System.out.println("-description: " + description.getDescription());
		System.out.println("-picture: " + description.getPicture());
	}
}
