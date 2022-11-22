package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.access.IReadingAccess;
import com.jeffdisher.cacophony.access.StandardAccess;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.data.local.v1.IReadOnlyFollowIndex;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.types.CacophonyException;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		try (IReadingAccess access = StandardAccess.readAccess(environment))
		{
			IReadOnlyFollowIndex followIndex = access.readOnlyFollowIndex();
			for(FollowRecord record : followIndex)
			{
				environment.logToConsole("Following: " + record.publicKey().toPublicKey());
			}
		}
	}
}
