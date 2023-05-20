package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.logic.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;


public record GetPublicKeyCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException
	{
		ILogger log = context.logger.logStart("Public Key:");
		log.logOperation("Public Key (other users can follow you with this): " + context.getSelectedKey().toPublicKey());
		log.logFinish("");
		return None.NONE;
	}
}
