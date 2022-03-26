package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.LocalIndex;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.logic.RemoteActions;
import com.jeffdisher.cacophony.types.CacophonyException;


public record GetPublicKeyCommand() implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, ILocalActions local) throws IOException, CacophonyException
	{
		LocalIndex localIndex = ValidationHelpers.requireIndex(local);
		RemoteActions remote = RemoteActions.loadIpfsConfig(executor, local.getSharedConnection(), localIndex.keyName());
		executor.logToConsole("Public Key (other users can follow you with this): " + remote.getPublicKey().toPublicKey());
	}
}
