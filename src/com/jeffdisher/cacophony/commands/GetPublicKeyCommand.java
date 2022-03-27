package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;


public record GetPublicKeyCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IOException, CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = local.readLocalIndex();
		RemoteActions remote = RemoteActions.loadIpfsConfig(environment, local.getSharedConnection(), localIndex.keyName());
		local.writeBackConfig();
		environment.logToConsole("Public Key (other users can follow you with this): " + remote.getPublicKey().toPublicKey());
	}
}
