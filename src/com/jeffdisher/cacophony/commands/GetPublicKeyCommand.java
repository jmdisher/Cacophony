package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


public record GetPublicKeyCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			environment.logToConsole("Public Key (other users can follow you with this): " + access.getPublicKey().toPublicKey());
		}
	}
}
