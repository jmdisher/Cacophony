package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.IReadOnlyLocalData;
import com.jeffdisher.cacophony.data.local.v1.LocalIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.scheduler.INetworkScheduler;
import com.jeffdisher.cacophony.types.CacophonyException;


public record GetPublicKeyCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		LocalIndex localIndex = null;
		try (IReadOnlyLocalData localData = local.getSharedLocalData().openForRead())
		{
			localIndex = localData.readLocalIndex();
		}
		INetworkScheduler scheduler = environment.getSharedScheduler(local.getSharedConnection(), localIndex.keyName());
		environment.logToConsole("Public Key (other users can follow you with this): " + scheduler.getPublicKey().toPublicKey());
	}
}
