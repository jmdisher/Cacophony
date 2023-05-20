package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.commands.results.ChangedRoot;
import com.jeffdisher.cacophony.types.IpfsConnectionException;
import com.jeffdisher.cacophony.types.IpfsFile;
import com.jeffdisher.cacophony.types.UsageException;
import com.jeffdisher.cacophony.utils.Assert;


/**
 * Since the IPNS publications are temporary (typically only living up to 24 hours), the index hash needs to be
 * periodically republished to the network.
 */
public record RepublishCommand() implements ICommand<ChangedRoot>
{
	@Override
	public ChangedRoot runInContext(Context context) throws IpfsConnectionException, UsageException
	{
		if (null == context.publicKey)
		{
			throw new UsageException("Channel must first be created with --createNewChannel");
		}
		IpfsFile indexHash;
		try (IReadingAccess access = StandardAccess.readAccess(context))
		{
			Assert.assertTrue(null != access.getLastRootElement());
			// Get the previously posted index hash.
			indexHash = access.getLastRootElement();
			// We must have previously published something.
			Assert.assertTrue(null != indexHash);
		}
		return new ChangedRoot(indexHash);
	}
}
