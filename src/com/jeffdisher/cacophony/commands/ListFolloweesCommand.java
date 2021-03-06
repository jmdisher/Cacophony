package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.data.local.v1.FollowRecord;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		FollowIndex followIndex = local.loadFollowIndex();
		for(FollowRecord record : followIndex)
		{
			environment.logToConsole("Following: " + record.publicKey().toPublicKey());
		}
	}
}
