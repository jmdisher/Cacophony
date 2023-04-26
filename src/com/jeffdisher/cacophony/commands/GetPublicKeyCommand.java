package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


public record GetPublicKeyCommand() implements ICommand<None>
{
	@Override
	public None runInContext(ICommand.Context context) throws IpfsConnectionException
	{
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			ILogger log = context.logger.logStart("Public Key:");
			log.logOperation("Public Key (other users can follow you with this): " + access.getPublicKey().toPublicKey());
			log.logFinish("");
		}
		return None.NONE;
	}
}
