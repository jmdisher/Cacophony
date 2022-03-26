package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;


public record GetPublicKeyCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment, ILocalConfig local) throws IOException, CacophonyException
	{
		LocalIndex localIndex = local.readExistingSharedIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		environment.logToConsole("Public Key (other users can follow you with this): " + remote.getPublicKey().toPublicKey());
	}
}
