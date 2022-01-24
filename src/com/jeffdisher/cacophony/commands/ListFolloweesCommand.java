package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.data.local.FollowIndex;
import com.jeffdisher.cacophony.data.local.FollowRecord;
import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;


public record ListFolloweesCommand() implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		FollowIndex followIndex = local.loadFollowIndex();
		for(FollowRecord record : followIndex)
		{
			System.out.println("Following: " + record.publicKey());
		}
	}
}
