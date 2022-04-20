package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.types.UsageException;


public record RefreshNextFolloweeCommand() implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		LocalConfig local = environment.loadExistingConfig();
		FollowIndex followIndex = local.loadFollowIndex();
		IpfsKey publicKey = followIndex.nextKeyToPoll();
		if (null == publicKey)
		{
			throw new UsageException("Not following any users");
		}
		CommandHelpers.refreshFollowee(environment, local, followIndex, publicKey);
	}
}
