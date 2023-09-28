package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.commands.results.None;
import com.jeffdisher.cacophony.types.ILogger;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record GetPublicKeyCommand() implements ICommand<None>
{
	@Override
	public None runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		IpfsKey publicKey = context.getSelectedKey();
		if (null == publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		ILogger log = context.logger.logStart("Public Key:");
		log.logOperation("Public Key (other users can follow you with this): " + publicKey.toPublicKey());
		log.logFinish("");
		return None.NONE;
	}
}
