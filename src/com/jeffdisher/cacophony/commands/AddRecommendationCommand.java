package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;
import com.jeffdisher.cacophony.types.IpfsKey;


public record AddRecommendationCommand(IpfsKey channelPublicKey) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		// TODO Auto-generated method stub
		
	}
}
