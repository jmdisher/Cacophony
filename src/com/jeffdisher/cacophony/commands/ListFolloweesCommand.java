package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.ILocalActions;
import com.jeffdisher.cacophony.types.CacophonyException;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment, ILocalActions local) throws IOException, CacophonyException
	{
		FollowIndex followIndex = local.loadFollowIndex();
		for(FollowRecord record : followIndex)
		{
			environment.logToConsole("Following: " + record.publicKey().toPublicKey());
		}
	}
}
