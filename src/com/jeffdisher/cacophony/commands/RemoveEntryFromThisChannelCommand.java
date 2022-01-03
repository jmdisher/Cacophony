package com.jeffdisher.cacophony.commands;

import java.io.IOException;

import com.jeffdisher.cacophony.logic.Executor;
import com.jeffdisher.cacophony.logic.LocalActions;

import io.ipfs.multihash.Multihash;


public record RemoveEntryFromThisChannelCommand(Multihash elementCid) implements ICommand
{
	@Override
	public void scheduleActions(Executor executor, LocalActions local) throws IOException
	{
		// TODO Auto-generated method stub
		
	}
}
