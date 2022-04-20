package com.jeffdisher.cacophony.commands;

import com.jeffdisher.cacophony.data.local.v1.FollowIndex;
import com.jeffdisher.cacophony.logic.CommandHelpers;
import com.jeffdisher.cacophony.logic.IEnvironment;
import com.jeffdisher.cacophony.logic.LocalConfig;
import com.jeffdisher.cacophony.types.CacophonyException;
import com.jeffdisher.cacophony.types.IpfsKey;
import com.jeffdisher.cacophony.utils.Assert;


public record RefreshFolloweeCommand(IpfsKey _publicKey) implements ICommand
{
	@Override
	public void runInEnvironment(IEnvironment environment) throws CacophonyException
	{
		Assert.assertTrue(null != _publicKey);
		
		LocalConfig local = environment.loadExistingConfig();
		FollowIndex followIndex = local.loadFollowIndex();
		CommandHelpers.refreshFollowee(environment, local, followIndex, _publicKey);
	}
}
